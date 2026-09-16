# 自定义机器外观

模型定义只为现有游戏配置外观和布局，不执行脚本，也不定义新规则。资源可来自普通资源包或 CraftEngine，默认通过原版 `item_model` 渲染。外部命名空间不必为 casino。

内置默认定义由 `src/main/java/dev/server/casino/model/BuiltinLayouts.java` 和 `MachineDefinition.builtin(game)` 提供。外部定义放在服务器 `plugins/ServerCasino/machines/*.yml`，每文件一台皮肤。缺省字段按 `game` 继承内置定义，因此只修改需要覆盖的项目。

```yaml
schema-version: 1
id: emerald-blackjack
game: blackjack
models:
  cabinet_blackjack: material:EMERALD_BLOCK
anchors:
  body:
    position: [0, 0, 0]
    rotation: [0, 0, 0]
    scale: 1
  playfield:
    position: [0, 0, 0]
    rotation: [0, 0, 0]
    scale: 1
parts:
  - model: material:EMERALD_BLOCK
    position: [0, 2.5, 0]
    rotation: [0, 0, 0]
    scale: 0.2
```

可直接复制 [emerald-blackjack.yml](emerald-blackjack.yml) 到上述目录。该示例用原版材料验证替换，无需额外贴图。自定义资源可将 `material:EMERALD_BLOCK` 改为 `my_pack:my_blackjack`，并提供相应 `assets/my_pack/items/my_blackjack.json` 及引用模型/贴图。模型配置不负责上传资源，必须先让客户端加载资源包。

执行 `/casino-demo reload-models`，然后 `/casino-demo create blackjack emerald-blackjack`。加载先校验全部文件再替换注册表；失败保留之前有效定义。已经创建的机器保留原定义快照；删除后重新创建才使用新外观。

## 字段

| 字段 | 用途 |
| --- | --- |
| `schema-version` | 当前为整数 1 |
| `id` / `game` | 唯一皮肤 ID / 已有游戏 ID |
| `models` | 逻辑模型名到资源 ID 的映射；未覆盖时沿用 `casino:<逻辑名>` |
| `anchors.body` | 机壳的局部变换 |
| `anchors.playfield` | 动态牌、球、轮盘等玩法局部坐标的共同变换 |
| `buttons` | 以该游戏支持的 action 为键的按钮覆盖 |
| `parts` | 额外静态部件，各含 `model, position, rotation, scale` |
| `settings-bounds` | 设置菜单射线区域 `[minX,minY,minZ,maxX,maxY,maxZ]` |

按钮值可以包含 `position: [x,y,z]`、`rotation: [pitch,yaw,roll]`、`width`、`height`、`depth`、`size` 与 `press`。前三个尺寸用于点击区域，`size` 用于视觉大小，`press` 为沿按钮面法线的按压距离。具体 action 和默认尺寸直接复制对应游戏的内置条目，不能随意添加玩法不支持的动作。按钮与机壳、动态挂点分别变换；修改 body 不会自动迁移按钮。

位置使用机器局部坐标与方块单位；整体朝向随机器放置 yaw。内置机壳模型底部为原点，正 Z 是前方。模型 JSON 使用 `8 + 物理坐标 × 4`，配合 FIXED ItemDisplay 的 scale 4；外来模型需按自身尺寸调整。rotation 数组为 pitch/yaw/roll，单位为度；四元数为 Z × Y × X，对点依次绕 X（pitch）、Y（yaw）、Z（roll）旋转，再做统一缩放和平移。不要把模型 JSON 的 display 旋转与机器配置旋转混为一谈。

所有坐标须为有限数；局部 position 每轴范围为 [-64, 64]，rotation 每轴范围为 [-360, 360] 度，统一缩放范围为 [0.001, 32]，按钮 width/height/depth/size 范围为 [0.001, 8]，press 范围为 [0, 1]。资源 ID 要满足 NamespacedKey 语法；`material:` 后为大写 Bukkit Material，且必须为非空气、可作为物品的材料。重复 ID、未知游戏/动作或非法值会报告对应文件和字段。创建后须检查四向摆放、卡牌/球与面板位置、按钮命中及按压方向。自动测试不能替代这些客户端验收。
