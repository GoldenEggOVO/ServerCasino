package dev.server.casino;

import com.google.gson.Gson;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.SecureRandom;
import java.util.*;
import java.util.function.LongSupplier;
import java.util.random.RandomGenerator;

/** Main-thread service. Pending external transactions always require explicit reconciliation. */
final class CasinoService {
    private final Path dir;
    private final MinesService.Wallet wallet;
    private final LongSupplier clock;
    private final RandomGenerator random;
    private final Gson gson = new Gson();
    private final Map<UUID, CasinoRound> rounds = new HashMap<>();
    private boolean storageFailed;

    CasinoService(Path dir, MinesService.Wallet wallet) throws IOException {
        this(dir, wallet, System::currentTimeMillis, new SecureRandom());
    }

    CasinoService(Path dir, MinesService.Wallet wallet, LongSupplier clock, RandomGenerator random)
            throws IOException {
        this.dir = dir;
        this.wallet = wallet;
        this.clock = clock;
        this.random = random;
        Files.createDirectories(dir);
        try (var files = Files.list(dir)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".json")).toList()) {
                CasinoRound r;
                try {
                    r = gson.fromJson(Files.readString(file), CasinoRound.class);
                } catch (RuntimeException ex) {
                    throw new IOException("游戏存档无法读取: " + file, ex);
                }
                if (r == null
                        || r.id == null
                        || r.player == null
                        || r.game == null
                        || r.phase == null
                        || r.stake < 100
                        || r.stake > 10000
                        || !file.getFileName().toString().equals(r.player + ".json")
                        || r.deck == null
                        || r.hand == null
                        || r.dealer == null) throw new IOException("无效游戏存档: " + file);
                if (r.game == CasinoRound.Game.BLACKJACK
                        && (r.deck.size() != 52
                                || new HashSet<>(r.deck).size() != 52
                                || r.deck.stream().anyMatch(c -> c == null || c < 0 || c >= 52)
                                || r.cursor < 4
                                || r.cursor > 52)) throw new IOException("无效牌堆存档: " + file);
                rounds.put(r.player, r);
            }
        }
    }

    CasinoRound get(UUID p) {
        var r = rounds.get(p);
        return r == null ? null : r.copy();
    }

    Set<UUID> players() {
        return Set.copyOf(rounds.keySet());
    }

    private void save(CasinoRound r) throws IOException {
        if (storageFailed) throw new IOException("游戏存储已暂停，请联系管理员");
        Path tmp = dir.resolve(r.player + ".tmp"), target = dir.resolve(r.player + ".json");
        try {
            byte[] bytes = gson.toJson(r).getBytes(StandardCharsets.UTF_8);
            try (var channel =
                    FileChannel.open(
                            tmp,
                            StandardOpenOption.CREATE,
                            StandardOpenOption.TRUNCATE_EXISTING,
                            StandardOpenOption.WRITE)) {
                var buffer = ByteBuffer.wrap(bytes);
                while (buffer.hasRemaining()) channel.write(buffer);
                channel.force(true);
            }
            Files.move(
                    tmp,
                    target,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
            if (!System.getProperty("os.name").startsWith("Windows"))
                try (var channel = FileChannel.open(dir, StandardOpenOption.READ)) {
                    channel.force(true);
                }
            rounds.put(r.player, r.copy());
        } catch (IOException ex) {
            storageFailed = true;
            throw ex;
        }
    }

    CasinoRound start(
            UUID p,
            UUID expectedPrevious,
            CasinoRound.Game game,
            long stake,
            int parameter,
            boolean practice)
            throws IOException {
        if (!practice && !wallet.available()) throw new IllegalStateException("经济服务不可用，练习仍可使用");
        var old = rounds.get(p);
        if ((old != null && !old.finished())
                || !Objects.equals(old == null ? null : old.id, expectedPrevious))
            throw new IllegalStateException("页面已过期或已有未结束对局");
        if (game == null || stake < 100 || stake > 10000 || stake % 100 != 0)
            throw new IllegalArgumentException("投注须为 1～100 整数金币");
        if (game == CasinoRound.Game.DICE && (parameter < 5 || parameter > 95))
            throw new IllegalArgumentException("胜率须为 5～95%");
        if ((game == CasinoRound.Game.LIMBO || game == CasinoRound.Game.CRASH)
                && (parameter < 101 || parameter > 10000))
            throw new IllegalArgumentException("目标倍率须为 1.01～100.00");
        var r = new CasinoRound();
        r.id = UUID.randomUUID();
        r.player = p;
        r.game = game;
        r.stake = stake;
        r.parameter = parameter;
        r.practice = practice;
        r.phase = CasinoRound.Phase.DEBIT_PENDING;
        r.point = CasinoRules.point(random.nextLong(CasinoRules.RANDOM_SPACE));
        r.roll = random.nextInt(10000);
        r.path = random.nextInt(4096);
        if (game == CasinoRound.Game.BLACKJACK) {
            for (int i = 0; i < 52; i++) r.deck.add(i);
            for (int i = 51; i > 0; i--) {
                int j = random.nextInt(i + 1);
                Collections.swap(r.deck, i, j);
            }
            r.hand.add(draw(r));
            r.dealer.add(draw(r));
            r.hand.add(draw(r));
            r.dealer.add(draw(r));
        }
        save(r);
        boolean paid = practice || wallet.take(p, stake);
        r.phase = paid ? CasinoRound.Phase.ACTIVE : CasinoRound.Phase.CANCELLED;
        r.started = clock.getAsLong();
        r.revision++;
        save(r);
        if (!paid) throw new IllegalStateException("余额不足或扣款被拒绝");
        return advance(p);
    }

    CasinoRound advance(UUID p) throws IOException {
        var r = get(p);
        if (r != null && r.phase == CasinoRound.Phase.DOUBLE_APPLIED) return completeDouble(r);
        if (r == null || r.phase != CasinoRound.Phase.ACTIVE) return r;
        switch (r.game) {
            case DICE -> {
                return settle(r, CasinoRules.dicePayout(r.stake, r.parameter, r.roll));
            }
            case LIMBO -> {
                return settle(r, CasinoRules.targetPayout(r.stake, r.parameter, r.point));
            }
            case PLINKO -> {
                return settle(r, CasinoRules.plinkoPayout(r.stake, Integer.bitCount(r.path)));
            }
            case BLACKJACK -> {
                if (CasinoRules.natural(r.hand)
                        || CasinoRules.natural(r.dealer)
                        || CasinoRules.total(r.hand) > 21
                        || r.standing) return finishBlackjack(r);
            }
            case CRASH -> {
                int current = CasinoRules.crashMultiplier(r.started, clock.getAsLong());
                // Reaching the configured target wins even when the sampled point equals it.
                if (r.point >= r.parameter && current >= r.parameter)
                    return settle(r, r.stake * r.parameter / 100);
                if (r.point < r.parameter && current >= r.point) return settle(r, 0);
            }
        }
        return r;
    }

    CasinoRound action(UUID p, UUID id, int revision, String action) throws IOException {
        var r = get(p);
        if (r != null
                && r.id.equals(id)
                && r.revision == revision
                && r.phase == CasinoRound.Phase.CREDIT_READY
                && action.equals("retry")) return settle(r, r.payout);
        if (r == null
                || !r.id.equals(id)
                || r.revision != revision
                || r.phase != CasinoRound.Phase.ACTIVE)
            throw new IllegalStateException("操作已处理或页面已过期");
        if (r.game == CasinoRound.Game.CRASH && action.equals("cash")) {
            var after = advance(p);
            if (after.phase != CasinoRound.Phase.ACTIVE) return after;
            int current = CasinoRules.crashMultiplier(r.started, clock.getAsLong());
            // Read time again only after advance; protect the crash boundary if time moved.
            if (current >= r.parameter && r.point >= r.parameter)
                return settle(r, r.stake * r.parameter / 100);
            if (current >= r.point || r.point < 100) return settle(r, 0);
            return settle(r, r.stake * Math.min(current, r.parameter) / 100);
        }
        if (r.game != CasinoRound.Game.BLACKJACK || r.standing)
            throw new IllegalStateException("该操作不适用于当前对局");
        switch (action) {
            case "hit" -> {
                r.hand.add(draw(r));
                r.revision++;
                if (CasinoRules.total(r.hand) >= 21) r.standing = true;
                save(r);
                return advance(p);
            }
            case "stand" -> {
                r.standing = true;
                r.revision++;
                save(r);
                return advance(p);
            }
            case "double" -> {
                if (r.hand.size() != 2 || r.stake > 5000)
                    throw new IllegalStateException("仅首轮可加倍，且加倍后总投注不得超过100金币");
                r.phase = CasinoRound.Phase.DOUBLE_PENDING;
                r.revision++;
                save(r);
                boolean paid = r.practice || wallet.take(p, r.stake);
                if (!paid) {
                    r.phase = CasinoRound.Phase.ACTIVE;
                    r.revision++;
                    save(r);
                    throw new IllegalStateException("加倍扣款被拒绝");
                }
                return completeDouble(r);
            }
            default -> throw new IllegalArgumentException("未知操作");
        }
    }

    private static int draw(CasinoRound r) {
        return r.deck.get(r.cursor++);
    }

    private CasinoRound completeDouble(CasinoRound r) throws IOException {
        r.stake *= 2;
        r.hand.add(draw(r));
        r.standing = true;
        r.phase = CasinoRound.Phase.ACTIVE;
        r.revision++;
        save(r);
        return advance(r.player);
    }

    private CasinoRound finishBlackjack(CasinoRound r) throws IOException {
        if (!CasinoRules.natural(r.hand)
                && !CasinoRules.natural(r.dealer)
                && CasinoRules.total(r.hand) <= 21)
            while (CasinoRules.total(r.dealer) < 17) r.dealer.add(draw(r));
        return settle(r, CasinoRules.blackjackPayout(r.stake, r.hand, r.dealer));
    }

    private CasinoRound settle(CasinoRound r, long payout) throws IOException {
        r.payout = payout;
        r.phase = payout == 0 ? CasinoRound.Phase.PAID : CasinoRound.Phase.CREDIT_PENDING;
        r.revision++;
        save(r);
        if (payout == 0) return r.copy();
        boolean paid = r.practice || wallet.give(r.player, payout);
        if (!paid) throw new IllegalStateException("发奖被拒绝，本局已暂停，请联系管理员核对");
        r.phase = CasinoRound.Phase.PAID;
        save(r);
        return r.copy();
    }

    void resolve(UUID p, UUID id, boolean applied) throws IOException {
        var r = get(p);
        if (r == null || !r.id.equals(id)) throw new IllegalArgumentException("对局不匹配");
        switch (r.phase) {
            case DEBIT_PENDING -> {
                r.phase = applied ? CasinoRound.Phase.ACTIVE : CasinoRound.Phase.CANCELLED;
                r.started = clock.getAsLong();
            }
            case DOUBLE_PENDING ->
                    r.phase = applied ? CasinoRound.Phase.DOUBLE_APPLIED : CasinoRound.Phase.ACTIVE;
            case CREDIT_PENDING -> {
                r.phase = applied ? CasinoRound.Phase.PAID : CasinoRound.Phase.CREDIT_READY;
            }
            default -> throw new IllegalStateException("该局不需要核对");
        }
        r.revision++;
        save(r);
        // Debit reconciliation only records state. Normal tick/open resumes game settlement.
    }
}
