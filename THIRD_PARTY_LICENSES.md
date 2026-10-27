# 第三方许可证

本应用自研，没有复制任何同类开源项目的源码、资源或界面文案。打包进安装包的第三方库如下，均为宽松许可证，未修改源码。

| 库 | 版本 | 许可证 | 用途 |
|---|---|---|---|
| Kotlin 标准库 | 2.0.21 | Apache-2.0 | 语言运行时 |
| kotlinx.coroutines | 1.9.0 | Apache-2.0 | 协程 |
| kotlinx.serialization (json) | 1.7.3 | Apache-2.0 | 保存规则与设置、解析大模型返回 |
| AndroidX Core / Activity / Lifecycle | 1.13.1 / 1.9.3 / 2.8.7 | Apache-2.0 | 安卓基础组件 |
| Jetpack Compose（BOM 2024.10.01）：UI、Material 3、Material Icons Core | 见 BOM | Apache-2.0 | 界面 |

仅用于构建和测试、不进入安装包：Android Gradle Plugin、Gradle（Apache-2.0）；JUnit 4.13.2（EPL-1.0）。

Apache-2.0 全文见 https://www.apache.org/licenses/LICENSE-2.0 。
