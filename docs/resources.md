# 资源与生成工具

已提交的 JSON/PNG 是本次批准外观的发布输入；迁移只替换命名空间，不重新绘制。`tools/resource-baseline.json` 保存 0.3.3 基线 ZIP 中 918 个资源文件经命名替换后的 SHA-256。测试逐文件验证 PNG 字节、几何、UV、显示变换、字体和 CE 配置未发生其他修改。

## 确定性发布

在任意当前目录运行项目内 `tools/package-resources.py`，路径始终相对于项目根。无需字体、Pillow 或旁边项目；文件排序、时间戳、权限和无压缩 ZIP 编码固定。重复运行产生相同字节。旧命名空间只保留 JSON 和 CE 注册别名，复用 casino PNG，避免两份贴图漂移。

源码交付使用 `python tools/package-source.py`，输出 `target/server-casino-source.zip`。仅包含源码、资源、文档、生成工具和固定测试基线；排除 target、reports、artwork 原始绘图、本地字体、字体配置及 Python 缓存。打包前先完成代码更改和测试，再重新运行以纳入最终文件。

## 可选重绘

项目 tools 包含原机器、弹珠、牌面和菜单生成器。它们是修改外观的开发工具，不是普通构建必经步骤。需要 Python 3 与 Pillow；改动前先备份资源。调用顺序通常为 `build-mines-ui.py`、`build-casino-artwork.py`、`build-casino-panels.py`、`build-machine-ball.py`、`build-casino-cabinets.py`、`build-showcase-machines.py`。公开源码包不包含 `artwork` 原始绘图；`build-casino-artwork.py` 与 `build-casino-panels.py` 的可选重绘需要自行提供有权使用的 `artwork/casino-icons-source.png` 与 `artwork/casino-panels-source.png` 输入。默认资源打包不需要这些文件。不同生成器覆盖各自对应资源，旧 UI 生成器可能恢复已经不再使用的字形，修改后必须审阅差异。

需要文字的生成器使用 `asset_fonts.py`。复制 `tools/fonts.example.json` 为 `tools/fonts.local.json`，指定自己有权使用的 bold、symbols、cjk 字体。配置内相对路径以配置文件目录为根；环境变量 `CASINO_FONT_CONFIG` 可以选择另一配置，其相对路径以项目根为根。没有字体配置时明确失败，不搜索 Windows 字体、不静默换字体。不要把字体文件或本地配置放进发布包。

历史批准纹理使用过 Arial Bold、Segoe UI Symbol、Microsoft YaHei；其字体程序不随项目分发。使用其他字体会改变字形及像素，不能宣称与历史 PNG 一致。对重绘结果要求可重复时，固定 Python/Pillow 版本和字体文件 SHA-256，并记录工具、输入和顺序。通常直接打包现有批准 PNG 即可精确复现发布包。

重绘后运行 `python tools/sync-legacy-aliases.py` 同步旧 JSON 与 CE 注册别名，再运行资产测试并实际检查客户端效果。只有有意批准新的资源基线时才能更新基线摘要；不能为了测试通过而重写基线。
