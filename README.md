# ServerCasino

面向 Paper/Purpur 26.2、Java 25 的 Casino 插件。源码许可为 GPL-3.0，见 [LICENSE](LICENSE)。主命令 `/casino`；默认实体机器是免费练习，不扣款、不发放金币。不提供菜单新开金币局；已有金币记录仍可继续处理和核对。

## 构建与安装

在本项目目录运行 `mvn clean package`，使用 Java 25 与 Maven 3.9+。首次构建需要联网下载 `pom.xml` 中公开依赖；离线构建只适用于缓存已完整的环境。安装 `target/server-casino-*.jar`，不要安装 `original-*.jar`。

停止服务器并备份 `plugins/ServerCasino` 后替换插件，同一插件仅保留一个 JAR。按需安装 Vault 与兼容经济插件；没有经济提供者时只提供练习。菜单采用 Paper 原生 Dialog；菜单可通过 `menu-enabled` 开关控制。AuthMe 接入用于已有认证保护。默认模型可通过 CraftEngine 合并，或者由管理员自己的资源包系统分发。

运行 `python tools/package-resources.py` 得到 `target/casino-craftengine.zip`。将其中 `resources/casino` 安装至 `plugins/CraftEngine/resources/casino`，重新生成并分发服务器合并资源包。不要覆盖其他内容包。ZIP 是 CraftEngine 内容包，不能直接作为客户端资源包 URL；管理员须使用合并后的有效资源包。

## 命令与配置

- `/casino`：机器管理菜单入口。Mines 仅通过实体机器游玩。
- `/casino create <game> [skin-id]`：创建免费机器。
- `/casino bet <game> <amount>`：修改自己的对应机器的练习下注，金额为 1～100 的整数；需要靠近机器，并等待当前对局或动画结束。
- `/casino remove [game]`：删除自己对应类型的机器；不填游戏类型则删除自己的全部机器。
- `/casino reload-models`：校验并重载机器定义。
- `/casino-demo` 同样支持以上机器管理子命令。
- `casino.use`、`casino.machine`：使用菜单与管理机器的权限。

`config.yml` 中 `menu-enabled: true` 默认开启原生菜单。设置为 `false` 并重启服务器后，`/casino` 和 Shift＋右键只显示指令帮助，不打开 Dialog。机器创建、下注设置、删除、模型重载与实体按钮仍可使用；已有对局的处理界面随菜单关闭。结算服务和控制台经济核对功能独立运行，不受菜单开关影响。

例如 `/casino create mines`、`/casino bet mines 25`、`/casino remove mines`。实体机器始终为免费练习。

模型配置、动作与坐标见 [自定义模型](docs/custom-models.md)。版本要求见 [安装说明](docs/installation.md)，资源生成与复现见 [资源工具](docs/resources.md)。

## 永久机器

每位玩家可同时放置 12 种游戏，每种一台，不再按游戏组互斥。机器不会因离线或放置超过 20 分钟被删除。

`plugins/ServerCasino/placements.json` 保存所有者、世界 UUID、位置、朝向、完整模型定义快照和练习下注。创建、改下注、删除时立即原子写入；重启后在世界与所在区块加载时恢复。卸载区块只清理显示实体，保留布置记录；未加载世界的记录也保留。备份插件时同时备份此文件和对应世界。

当前一局与动画不跨重启或区块卸载恢复，恢复后为新的一局。显式删除才移除布置记录；`/casino remove [game]` 也能删除未加载的自有机器。现有权限与菜单开关继续生效。

## 经济与存档

金额使用整数 cents。练习不调用经济扣款或发奖。金币局的 `DEBIT_PENDING` / `CREDIT_PENDING` 表示经济操作需要核对；提供者不可用时不能伪造成功。备份并保护 `rounds` 数据，不删除记录以绕过待核对状态。

Mines 菜单移除后，已有资金记录仍保留控制台核对功能。管理员核对经济插件证据后，使用 `casino mines resolve <玩家UUID> <对局UUID> applied` 或 `not-applied` 标记实际结果。该命令自身不转账，不能猜测结果。其他菜单游戏使用 `casino resolve <玩家UUID> <对局UUID> applied` 或 `not-applied` 核对。

## 验证

`mvn test` 运行 Java 回归测试。安装 Pillow 后运行 `python -m unittest discover -s tools -p "test_*.py"` 校验资源、几何、当前资源引用与确定性打包。自动检查不替代 Java/Bedrock 客户端默认外观、点击区域、按压和动画验收。

本项目不包含正式服配置、第三方插件 JAR 或私有字体；第三方来源与素材归属边界见 [THIRD_PARTY.md](THIRD_PARTY.md)。
