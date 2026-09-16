# 从 ServerMines / 0.3.3-preview 升级

1. 停服并备份原 JAR、`plugins/ServerCasino`、旧 `plugins/ServerMines`（若存在）及 CraftEngine 的 `resources/rimuri_mines`。保留原资源包与对应校验值以便回滚。
2. 安装 ServerCasino JAR，每个插件只留一个版本。目录已从源码 `server-mines` 改为 `server-casino`；入口包为 `dev.server.casino`。数据目录仍是 `ServerCasino`，不要改写旧对局 JSON。
3. 安装新的 CE 内容包 `resources/casino`。备份后将旧的 `resources/rimuri_mines` 移出 CE 扫描目录，避免重复注册旧 ID；不要删除其他内容包。
4. 重新生成并分发合并资源包，检查实际客户端成功加载。新主资源为 `casino:*`；新包同时包含旧 `rimuri_mines:*` 模型、item_model、字体及 CE 物品注册兼容引用。
5. 检查菜单、进行中的旧对局与待核对记录。测试新旧命令、权限显式拒绝、无经济提供者的免费练习，再在隔离环境验证金币流程。

旧 ServerMines 数据迁移应只补充缺失目标，不覆盖已有 ServerCasino 数据。资金待核对状态必须保留，不能通过重新安装或删除数据修复。

升级不会自动给实体机器启用金币扣款，不改变原有赔率。新增模型配置只影响创建后的机器；已有机器保留创建时定义快照。外部资源命名空间无需改成 casino。

回滚时同时恢复旧 JAR、数据备份和旧资源包。不要仅回退 JAR 后混用未经确认的数据和资源。

菜单已迁移到 Paper 原生 Dialog，不再依赖 KaMenu。首次迁移把旧 `KaMenu/menus/mines/main.yml` 复制到 `ServerCasino/menu.yml`，不覆盖已有目标编辑。新字形使用 `casino:ui` / `casino:advanced`，旧字体 ID 保留兼容；实际客户端菜单视觉仍须验收。
