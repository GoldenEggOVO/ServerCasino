# 开发结构与扩展接口

插件入口为 `dev.server.casino.CasinoPlugin`。`game/<game>/` 内的 Round 只负责规则和结果，Machine 控制器只负责对应游戏的实体与动画。`machine/` 统一管理创建、射线点击、按钮按压、菜单入口和清理；`model/` 负责不可变外观定义、校验和重载。

旧金币菜单的持久化服务仍保留原 JSON 字段和交易恢复语义。不要用练习机器 Round 替换持久化交易状态，也不要绕过金币确认回调调用经济提供者。

## 经济接口

其他插件可以使用 Bukkit `ServicesManager` 注册 `dev.server.casino.api.EconomyProvider`。接口以 UUID 和整数最小金额单位操作，返回值区分成功、失败、不可用和结果不确定。具体方法及返回值契约见接口 Javadoc。Vault 适配器使用最低优先级，允许更高优先级的明确提供者覆盖。

练习流程不调用经济服务。经济不可用时禁止金币开局；转账不确定时保留待核对记录。Vault 与本地文件不能实现跨系统原子事务，不可通过自动重试来假装恰好一次转账。

## 模型解析接口

注册 `dev.server.casino.api.MachineModelResolver`，实现 `ItemStack resolve(String namespacedModel)`。返回 `null` 时使用默认解析；返回的物品会复制，插件不修改提供者的缓存实例。默认解析使用原版 `item_model`，CraftEngine 不属于规则层依赖。

模型定义详见 [custom-models.md](custom-models.md)。注册服务和机器操作应在服务器主线程进行；不要在解析方法里阻塞网络或磁盘。API 目前是 preview，修改公共接口时需说明兼容影响。

## 回归与维护

测试中的 `Frozen*Round` 是冻结的 0.3.3 行为基线，只用于与新规则逐动作对比，不会进入插件 JAR。修改玩法应同时更新明确的行为测试；不能悄悄修改冻结基线使差分测试通过。

默认美术资源迁移保持原几何与像素。运行资源生成器前阅读 [resources.md](resources.md)，重绘会受字体版本影响。内置按钮位置及尺寸以 `BuiltinLayouts` 为准，测试需要覆盖实际定义而非重复写一套期望实现。

`machine/PlacementStore` 独立保存机器布置和模型快照，使用临时文件同步后原子替换；对局状态不写入布置文件。`MachineManager` 按所有者与游戏类型索引，区分保存记录与已加载实体。区块或世界卸载只卸载实体，显式删除才写入记录变更。
