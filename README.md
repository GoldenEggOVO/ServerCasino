# ServerCasino

面向 Paper/Purpur 26.2、Java 25 的 Casino 插件。源码许可为 GPL-3.0，见 [LICENSE](LICENSE)。主命令 `/casino`；默认实体机器是免费练习，不扣款、不发放金币。菜单金币局需要可用的经济提供者与玩家确认。

## 构建与安装

在本项目目录运行 `mvn clean package`，使用 Java 25 与 Maven 3.9+。首次构建需要联网下载 `pom.xml` 中公开依赖；离线构建只适用于缓存已完整的环境。安装 `target/server-casino-*.jar`，不要安装 `original-*.jar`。

停止服务器并备份 `plugins/ServerCasino` 后替换插件，同一插件仅保留一个 JAR。按需安装 Vault 与兼容经济插件；没有经济提供者时只提供练习。菜单采用 Paper 原生 Dialog，不再要求 KaMenu；新安装默认 `money-enabled: false`。AuthMe 接入用于已有认证保护。默认模型可通过 CraftEngine 合并，或者由管理员自己的资源包系统分发。

运行 `python tools/package-resources.py` 得到 `target/casino-craftengine.zip`。将其中 `resources/casino` 安装至 `plugins/CraftEngine/resources/casino`，重新生成并分发服务器合并资源包。不要覆盖其他内容包。ZIP 是 CraftEngine 内容包，不能直接作为客户端资源包 URL；管理员须使用合并后的有效资源包。

## 命令与配置

- `/casino`、`/mines`：菜单和旧对局入口。
- `/casino-demo create <game> [skin-id]`：创建免费机器；`remove` 清除机器。
- `/casino-demo reload-models`：校验并重载机器定义。
- `/plinko-demo`：兼容旧弹珠机入口。
- `casino.use`、`casino.machine`：新权限；保留 `servermines.use`、`servermines.machine` 兼容检查，旧显式拒绝仍有效。

模型配置、动作与坐标见 [自定义模型](docs/custom-models.md)。升级步骤见 [迁移说明](docs/migration.md)，资源生成与复现见 [资源工具](docs/resources.md)。

## 经济与存档

金额使用整数 cents。练习不调用经济扣款或发奖。金币局的 `DEBIT_PENDING` / `CREDIT_PENDING` 表示经济操作需要核对；提供者不可用时不能伪造成功。备份并保护 `rounds` 数据，不删除记录以绕过待核对状态。

管理员核对经济插件证据后，使用 `mines resolve <玩家UUID> <对局UUID> applied` 或 `not-applied` 标记实际结果。该命令自身不转账，不能猜测结果。旧对局字段及 ServerCasino 数据目录保留。

## 验证

`mvn test` 运行 Java 回归测试。安装 Pillow 后运行 `python -m unittest discover -s tools -p "test_*.py"` 校验资源、几何、旧 ID 与确定性打包。自动检查不替代 Java/Bedrock 客户端默认外观、点击区域、按压和动画验收。

本项目不包含正式服配置、第三方插件 JAR 或私有字体；第三方来源与素材归属边界见 [THIRD_PARTY.md](THIRD_PARTY.md)。
