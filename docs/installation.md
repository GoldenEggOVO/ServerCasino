# 当前版本安装

仅支持 Paper/Purpur 26.2 与 Java 25。0.4.1-preview 不提供旧版本兼容层，也不会自动迁移旧插件目录或导入其他菜单插件配置。

1. 停服并备份插件 JAR、数据和资源包；安装当前 ServerCasino JAR，同一插件仅保留一个版本。
2. 安装当前 CraftEngine 内容包 `resources/casino`，重新生成并分发合并资源包。仅注册 `casino:*`；旧内容包应备份后移出扫描目录。
3. 使用 `casino.use` 与 `casino.machine` 配置权限，旧权限不再生效。菜单入口为 `/casino` 和 `/casino mines`；机器管理使用 `/casino-demo`。
4. 首次安装从内置模板创建 `plugins/ServerCasino/menu.yml`。已有当前配置不覆盖；不会读取其他插件的菜单。
5. 在隔离环境验证资源加载、菜单和机器交互，再用于正式服。

当前 ServerCasino 存档与资金待核对保护保留。不要删除 `rounds` 数据绕过待核对状态；先按 README 核对实际经济操作。旧版数据不保证兼容，应保留原环境备份并在原版本完成对局核对。

实体机器仍为免费练习。模型定义只影响新创建的机器；已有机器保留创建时定义快照。自定义模型允许使用资源包自己的命名空间。
