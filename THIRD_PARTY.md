# 许可与第三方来源

项目保留原 GPL-3.0 许可文件，不擅自改为宽松许可。

- Paper API：`io.papermc.paper:paper-api`，来源为项目 POM 指定的 PaperMC 官方 Maven 仓库。服务器 API 在运行时提供，不随源码包附带服务器 JAR。
- Gson、JUnit 及 Maven 构建插件：以 POM 坐标为准确版本依据，由公开仓库获取，遵循各自上游许可。
- Vault、AuthMe、CraftEngine：可选服务器集成；本项目不分发其插件 JAR。Vault 的服务由管理员安装的插件提供。
- Python Pillow：可选资源开发/测试依赖，来源 https://python-pillow.org/ ，适用其上游许可证。普通资源打包只使用 Python 标准库。
- 历史 Arial Bold、Segoe UI Symbol、Microsoft YaHei 字体：来自原开发环境，字体程序不包含在本项目中。原纹理中已有栅格文字保留；不能据此宣称获得字体再分发许可。
- 默认资源中的图像切片和机器素材：继承原项目的生成图与程序化模型。原始 `artwork/casino-icons-source.png`、`casino-panels-source.png` 仅保留在本地，不包含在公开源码包；原 README 记载界面自制且未采用参考网站标志。现有文件缺少完整生成工具/服务版本与权属记录，因此公开平台发布前仍需由维护者核实来源及可再分发权，不能把技术打包验证视为素材权属确认。

本次交付不包含私有 KaMenu JAR、其他第三方插件二进制、字体文件或生产服务器配置。
