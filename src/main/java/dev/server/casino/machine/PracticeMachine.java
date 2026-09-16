package dev.server.casino.machine;

import dev.server.casino.CasinoPlugin;
import dev.server.casino.MachineGeometry;
import dev.server.casino.game.DemoRound;
import dev.server.casino.game.PracticeRound;
import dev.server.casino.model.*;

import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.*;

/** Shared entity lifetime and input plumbing; gameplay belongs to the concrete controller. */
public abstract class PracticeMachine<R extends PracticeRound> {
    protected static final double PITCH = -Math.toRadians(35);
    protected final CasinoPlugin plugin;
    protected final MachineManager manager;
    protected final UUID owner;
    protected final Location origin;
    protected final MachineDefinition definition;
    protected final R round;
    protected final List<Entity> parts = new ArrayList<>();
    private final List<Target> targets = new ArrayList<>();
    private final Map<ItemDisplay, ButtonDefinition> buttons = new LinkedHashMap<>();
    private final Map<ItemDisplay, String> buttonActions = new LinkedHashMap<>();
    private final Map<ItemDisplay, Integer> pressed = new HashMap<>();
    private ItemDisplay highlighted;
    protected int age;
    private long lastClick;

    protected PracticeMachine(
            MachineManager manager,
            UUID owner,
            Location origin,
            MachineDefinition definition,
            R round) {
        this.manager = manager;
        this.plugin = manager.plugin();
        this.owner = owner;
        this.origin = origin.clone();
        this.definition = definition;
        this.round = round;
    }

    public final UUID owner() {
        return owner;
    }

    public final String game() {
        return definition.game();
    }

    public final Location origin() {
        return origin.clone();
    }

    protected double scale() {
        return game().equals("plinko") ? .75 : MachineGeometry.machineScale(game());
    }

    public final void build() {
        buildGame();
        for (var part : definition.parts()) {
            var transform = part.transform();
            var display =
                    origin.getWorld()
                            .spawn(
                                    worldAt(transform.x(), transform.y(), transform.z()),
                                    ItemDisplay.class,
                                    this::common);
            display.setItemStack(ModelItems.resolve(part.model()));
            display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            display.setTransformation(
                    new Transformation(
                            new Vector3f(),
                            transform.rotation(),
                            new Vector3f((float) (4 * transform.scale() * scale())),
                            new Quaternionf().rotateY((float) Math.PI)));
        }
        var b = definition.settingsBounds();
        double s = scale();
        plugin.machineSettings()
                .register(
                        this,
                        owner,
                        origin,
                        new BoundingBox(
                                b.get(0) * s,
                                b.get(1) * s,
                                b.get(2) * s,
                                b.get(3) * s,
                                b.get(4) * s,
                                b.get(5) * s),
                        this::settings);
        refresh();
    }

    protected abstract void buildGame();

    protected abstract void action(String action);

    protected abstract boolean available(String action);

    protected abstract void refresh();

    protected void animate() {}

    protected boolean busy() {
        return false;
    }

    protected boolean canEditStake() {
        return !round.active() && !busy();
    }

    protected boolean rowAvailable(int row) {
        return true;
    }

    protected void confirmInput() {}

    public final boolean expired() {
        return parts.stream().anyMatch(e -> !e.isValid());
    }

    public final boolean touchesChunk(Chunk chunk) {
        return parts.stream()
                .anyMatch(
                        e ->
                                e.getWorld().equals(chunk.getWorld())
                                        && (e.getLocation().getBlockX() >> 4) == chunk.getX()
                                        && (e.getLocation().getBlockZ() >> 4) == chunk.getZ());
    }

    public final void tick() {
        age++;
        for (var iterator = pressed.entrySet().iterator(); iterator.hasNext(); ) {
            var entry = iterator.next();
            boolean done = age >= entry.getValue();
            entry.getKey()
                    .setTransformation(
                            buttonPose(
                                    buttons.get(entry.getKey()),
                                    done ? 0 : buttons.get(entry.getKey()).press()));
            if (done) iterator.remove();
        }
        animate();
        if (age % 4 == 0) hover();
    }

    protected final Location worldAt(double x, double y, double z) {
        var p = MachineGeometry.rotate(x * scale(), y * scale(), z * scale(), origin.getYaw());
        return origin.clone().add(p.x(), p.y(), p.z());
    }

    protected final Location at(double x, double y, double z) {
        var p = definition.anchor("playfield").apply(x, y, z);
        return worldAt(p.x(), p.y(), p.z());
    }

    protected final void common(Entity entity) {
        entity.setPersistent(false);
        entity.setGravity(false);
        entity.setInvulnerable(true);
        parts.add(entity);
        if (entity instanceof Display display) {
            display.setBrightness(
                    new Display.Brightness(
                            game().equals("plinko") ? 15 : 12, game().equals("plinko") ? 15 : 12));
            display.setViewRange(.6f);
            display.setTeleportDuration(1);
        }
    }

    protected final ItemStack model(String name) {
        String fallback =
                switch (name) {
                    case "mine_hidden" -> "material:POLISHED_DEEPSLATE";
                    case "mine_gem" -> "material:EMERALD_BLOCK";
                    case "mine_bomb" -> "material:TNT";
                    default -> "casino:" + name;
                };
        return ModelItems.resolve(definition.models().getOrDefault(name, fallback));
    }

    protected final ItemDisplay item(
            ItemStack stack, double x, double y, double z, double size, double pitch) {
        return origin.getWorld()
                .spawn(
                        at(x, y, z),
                        ItemDisplay.class,
                        display -> {
                            common(display);
                            display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
                            display.setItemStack(stack);
                            pose(display, MachineGeometry.itemPose(size, pitch, 0));
                            display.setInterpolationDuration(1);
                        });
    }

    protected final ItemDisplay model(String name, double x, double y, double z, double size) {
        return item(model(name), x, y, z, size, 0);
    }

    protected final void body(String name) {
        var anchor = definition.anchor("body");
        var point = anchor.apply(0, 0, 0);
        origin.getWorld()
                .spawn(
                        worldAt(point.x(), point.y(), point.z()),
                        ItemDisplay.class,
                        display -> {
                            common(display);
                            display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
                            display.setItemStack(model(name));
                            display.setTransformation(
                                    new Transformation(
                                            new Vector3f(),
                                            anchor.rotation(),
                                            new Vector3f((float) (4 * scale() * anchor.scale())),
                                            new Quaternionf().rotateY((float) Math.PI)));
                        });
    }

    /** Apply the same playfield transform to dynamic poses as to their positions. */
    protected final void pose(Display display, Transformation local) {
        var anchor = definition.anchor("playfield");
        float factor = (float) (scale() * anchor.scale());
        var rotation = anchor.rotation();
        var translation = new Vector3f(local.getTranslation()).mul(factor);
        rotation.transform(translation);
        display.setTransformation(
                new Transformation(
                        translation,
                        new Quaternionf(rotation).mul(local.getLeftRotation()),
                        new Vector3f(local.getScale()).mul(factor),
                        new Quaternionf(local.getRightRotation())));
    }

    protected final TextDisplay text(double x, double y, double z, double size) {
        return origin.getWorld()
                .spawn(
                        at(x, y, z),
                        TextDisplay.class,
                        display -> {
                            common(display);
                            display.setBillboard(Display.Billboard.FIXED);
                            display.setBackgroundColor(Color.fromARGB(0));
                            display.setDefaultBackground(false);
                            display.setLineWidth(1100);
                            pose(
                                    display,
                                    new Transformation(
                                            new Vector3f(),
                                            new Quaternionf(),
                                            new Vector3f((float) size),
                                            new Quaternionf()));
                        });
    }

    protected final void hit(
            String action,
            ItemDisplay visual,
            double x,
            double y,
            double z,
            double width,
            double height,
            int row) {
        targets.add(
                new Target(
                        action,
                        visual,
                        definition.anchor("playfield"),
                        new BoundingBox(
                                x - width / 2,
                                y,
                                z - width / 2,
                                x + width / 2,
                                y + height,
                                z + width / 2),
                        row));
    }

    protected final void button(String action, String modelName) {
        var button = definition.button(action);
        var transform = button.transform();
        var visual =
                origin.getWorld()
                        .spawn(
                                worldAt(transform.x(), transform.y(), transform.z()),
                                ItemDisplay.class,
                                display -> {
                                    common(display);
                                    display.setItemDisplayTransform(
                                            ItemDisplay.ItemDisplayTransform.NONE);
                                    display.setItemStack(model(modelName));
                                    display.setTransformation(buttonPose(button, 0));
                                    display.setInterpolationDuration(1);
                                });
        buttons.put(visual, button);
        buttonActions.put(visual, action);
        targets.add(
                new Target(
                        action,
                        visual,
                        transform,
                        new BoundingBox(
                                -button.width() / 2,
                                0,
                                -button.press(),
                                button.width() / 2,
                                button.height(),
                                button.depth()),
                        -1));
    }

    private Transformation buttonPose(ButtonDefinition button, double press) {
        var rotation = button.transform().rotation();
        double factor = scale() * button.transform().scale();
        var translation = new Vector3f(0, 0, (float) (-press * factor));
        rotation.transform(translation);
        return new Transformation(
                translation,
                rotation,
                new Vector3f(
                        (float) (4 * button.width() * factor / .6),
                        (float) (4 * button.size() * factor),
                        (float) (4 * button.size() * factor)),
                new Quaternionf().rotateY((float) Math.PI));
    }

    protected final void selectedButtons(java.util.function.Predicate<String> selected) {
        buttonActions.forEach(
                (display, action) -> {
                    display.setGlowing(selected.test(action) || display == highlighted);
                    display.setGlowColorOverride(Color.YELLOW);
                });
    }

    final TargetHit ray(Player player, double maximum) {
        var eye = player.getEyeLocation();
        var direction = eye.getDirection();
        var start =
                MachineGeometry.rotate(
                        eye.getX() - origin.getX(),
                        eye.getY() - origin.getY(),
                        eye.getZ() - origin.getZ(),
                        -origin.getYaw());
        var end =
                MachineGeometry.rotate(
                        eye.getX() + direction.getX() - origin.getX(),
                        eye.getY() + direction.getY() - origin.getY(),
                        eye.getZ() + direction.getZ() - origin.getZ(),
                        -origin.getYaw());
        TargetHit nearest = null;
        for (var target : targets) {
            var a =
                    target.transform.inverse(
                            start.x() / scale(), start.y() / scale(), start.z() / scale());
            var b =
                    target.transform.inverse(
                            end.x() / scale(), end.y() / scale(), end.z() / scale());
            var vector = new Vector(b.x() - a.x(), b.y() - a.y(), b.z() - a.z()).normalize();
            var hit =
                    target.bounds.rayTrace(
                            new Vector(a.x(), a.y(), a.z()),
                            vector,
                            maximum / (scale() * target.transform.scale()));
            if (hit == null) continue;
            double distance =
                    hit.getHitPosition().distance(new Vector(a.x(), a.y(), a.z()))
                            * scale()
                            * target.transform.scale();
            if (nearest == null || distance < nearest.distance)
                nearest = new TargetHit(target, distance);
        }
        return nearest;
    }

    final void click(TargetHit hit) {
        long now = System.currentTimeMillis();
        if (now - lastClick < 150
                || busy()
                || !rowAvailable(hit.target.row)
                || !available(hit.target.action)) return;
        lastClick = now;
        if (buttons.containsKey(hit.target.visual)) pressed.put(hit.target.visual, age + 4);
        action(hit.target.action);
        origin.getWorld().playSound(origin, Sound.BLOCK_STONE_BUTTON_CLICK_ON, .35f, 1.1f);
    }

    private void hover() {
        var player = Bukkit.getPlayer(owner);
        ItemDisplay next = null;
        if (player != null && manager.canUse(player, this)) {
            var hit = ray(player, manager.blockDistance(player));
            if (hit != null
                    && !busy()
                    && rowAvailable(hit.target.row)
                    && available(hit.target.action)) next = hit.target.visual;
        }
        if (next != highlighted) {
            if (highlighted != null) highlighted.setGlowing(false);
            highlighted = next;
            if (!busy()) refresh();
            if (next != null) {
                next.setGlowing(true);
                next.setGlowColorOverride(Color.YELLOW);
            }
        }
    }

    final void setStake(long amount) {
        if (amount < 100 || amount > 10000 || amount % 100 != 0) {
            throw new IllegalArgumentException("下注金额必须是 1～100 的整数。");
        }
        if (!canEditStake()) throw new IllegalArgumentException("请等当前对局或动画结束后修改金额。");
        manager.saveStake(this, amount);
        restoreStake(amount);
        refresh();
    }

    final void restoreStake(long amount) {
        if (round instanceof DemoRound demo) demo.setConfiguredStake(amount);
        else round.setStake(amount);
    }

    public final void settings(Player player) {
        confirmInput();
        plugin.openMachineSettings(
                player,
                game(),
                () -> round instanceof DemoRound demo ? demo.configuredStake() : round.stake(),
                this::setStake,
                this::canEditStake,
                () -> manager.contains(this) && manager.canManage(player, this),
                () -> manager.remove(this));
    }

    final boolean nearSettings(Player player) {
        var eye = player.getEyeLocation();
        var local =
                MachineGeometry.rotate(
                        eye.getX() - origin.getX(),
                        eye.getY() - origin.getY(),
                        eye.getZ() - origin.getZ(),
                        -origin.getYaw());
        var bounds = definition.settingsBounds();
        double[] point = {local.x() / scale(), local.y() / scale(), local.z() / scale()};
        double distanceSquared = 0;
        for (int axis = 0; axis < 3; axis++) {
            double difference =
                    point[axis] - Math.clamp(point[axis], bounds.get(axis), bounds.get(axis + 3));
            distanceSquared += difference * difference * scale() * scale();
        }
        return distanceSquared <= 100;
    }

    public final void clear() {
        plugin.machineSettings().unregister(this);
        for (Entity entity : parts) entity.remove();
        parts.clear();
        targets.clear();
        buttons.clear();
        pressed.clear();
        buttonActions.clear();
        highlighted = null;
    }

    private record Target(
            String action,
            ItemDisplay visual,
            ModelTransform transform,
            BoundingBox bounds,
            int row) {}

    record TargetHit(Target target, double distance) {}
}
