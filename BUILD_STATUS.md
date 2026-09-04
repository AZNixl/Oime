# AZime 项目状态报告

> 最后更新：2026-09-04（首构建验证通过后重写，旧的 AAPT2 阻塞问题已解决）

## 项目信息

- **项目名称**: AZime（Android RIME 输入法）
- **版本**: v0.1.0-preview
- **包名**: `com.azime.input`
- **仓库**: https://github.com/AZNixl/AZime （私有，main 分支）
- **技术栈**: Kotlin 1.9.22 / AGP 8.3.0 / Gradle 8.4 / Compose BOM 2024.02.00
- **构建目标**: compileSdk 34 / minSdk 24 / targetSdk 34 / JDK 17

## 构建状态：✅ 已跑通

首构建验证已完成，本地与 CI 两侧均通过。

| 环境 | 结果 | 耗时 |
|---|---|---|
| Windows 本机（`./gradlew assembleDebug`） | BUILD SUCCESSFUL | 首次 7m02s，增量 44s |
| GitHub Actions（ubuntu-latest） | 3 次运行全部 success | 首次 ~4m，后续 ~1m40s |

产物：`app/build/outputs/apk/debug/app-debug.apk`，约 12.8 MB。

### 旧的阻塞问题已解决

此前卡住的 **AAPT2 架构不兼容**（`Cannot run program aapt2: No such file or directory`）是纯构建环境问题：
AAPT2 只有 x86_64 版本，ARM64 机器上跑不了。改用 x86_64 环境（本机或 GitHub Actions）即可，
输出始终是 APK，与"是否构建成 PC 应用"无关。

### 本机构建方式

```bash
ANDROID_HOME="C:/Users/HinYoung/AppData/Local/Android/Sdk" \
ANDROID_SDK_ROOT="C:/Users/HinYoung/AppData/Local/Android/Sdk" \
JAVA_HOME="C:/Program Files/Eclipse Adoptium/jdk-17.0.20.8-hotspot" \
./gradlew assembleDebug
```

两个注意事项：

1. **项目路径含中文（如 `Desktop\搞机\...`）会被 AGP 拒绝**
   （`Your project path contains non-ASCII characters`）。当前用
   `gradle.properties` 里的 `android.overridePathCheck=true` 绕过；
   若遇到资源处理异常，把项目移到纯英文路径才是根治办法。
2. **Gradle 版本不可随意升级**：AGP 8.3 不能搭配 Gradle 9，`gradle-wrapper.properties`
   固定为 Gradle 8.4。

## 功能完成度（实事求是）

代码总量 744 行。目前是**可安装、可启用的应用骨架**，尚不能真正打字。

| 模块 | 文件 | 状态 | 说明 |
|---|---|---|---|
| 应用入口 | `AZimeApplication.kt` | 可用 | 初始化存储目录 |
| 主界面 | `MainActivity.kt` | 可用 | 启用/切换输入法、进入设置 |
| 设置界面 | `SettingsActivity.kt` | 基本可用 | Compose + Material 3，部分入口还是 `TODO` |
| 存储管理 | `StorageManager.kt` | 可用 | 创建 `Documents/AZime/{schema,fonts,lua}` 并生成默认 Lua |
| 方案导入 | `SchemaImporter.kt` | 可用 | 基于 Zip4j 导入 ZIP 方案 |
| Lua 脚本 | `LuaScriptManager.kt` | 基础可用 | LuaJ 执行脚本 |
| 数据模型 | `KeyboardLayout.kt` | 可用 | KeyboardLayout / Row / Key / KeyType |
| **输入法服务** | `AZimeService.kt` | 骨架 | 生命周期回调已接，但按键→候选→上屏链路未建立 |
| **键盘视图** | `KeyboardView.kt` | **占位** | 仅 `canvas.drawText("AZime Keyboard")`，没有真实按键 |
| **键盘管理** | `KeyboardManager.kt` | **占位** | 布局加载/保存均为 `TODO` |
| **RIME 引擎** | `RimeManager.kt` | **空壳** | 7 个方法全是 `TODO`，返回空值 |
| 键盘编辑器 | `KeyboardEditorActivity.kt` | 占位页面 | 无编辑功能 |

未闭合的关键缺口（`TODO` 共 12 处，分布见下）：

- `RimeManager`：初始化、`processKey`、取候选、选候选、上屏、清空 —— 全部未实现
- `KeyboardManager`：布局加载与保存
- `SettingsActivity`：键盘编辑器、字体管理、Lua 脚本编辑三个入口未接线

## 后续开发优先级

打通"能真正打出一个字"的最短路径，顺序上建议 **键盘在前、引擎在后**——
先把按键事件和上屏链路做通，用假数据也能验证交互，再替换成真实 RIME 结果：

1. **真实键盘视图（前置依赖）**
   - 用 Compose 重写 `KeyboardView`，按 `KeyboardLayout` 数据渲染按键
   - 触摸/长按/滑动事件 → `AZimeService` → `commitText` 上屏
   - 先用内置 QWERTY 布局验证链路，不依赖 RIME

2. **候选栏**
   - 候选词横向列表、翻页、点击上屏
   - 数据先 mock，接口按 RIME 的形状设计

3. **RIME 引擎集成（最大工作量）**
   - 编译 `librime.so`（arm64-v8a / armeabi-v7a / x86_64），或复用现有预编译产物
   - JNI 绑定 `RimeManager`，打通 initialize / processKey / getCandidates / selectCandidate
   - 部署默认输入方案到 `Documents/AZime/schema/`
   - 注意：`app/build.gradle.kts` 已声明 `ndk.abiFilters`，接入 so 后即可打包

4. **补齐设置页入口**：键盘编辑器、字体管理、Lua 脚本编辑

5. **打磨**：按键反馈（振动/声音）、主题系统、字体回退验证

## 已知技术债

- `KeyboardView` 是传统 `View` + `onDraw` 绘制，其余 UI 均为 Compose，后续应统一到 Compose
- `SettingsActivity` 使用了已废弃的 `Icons.Filled.ArrowBack`（应换 `AutoMirrored` 版本）
- 多处 `TODO` 参数未使用（如 `RimeManager.initialize` 的 `context`），编译期有警告
- 无测试代码，已有 `junit` / `espresso` 依赖但零用例

## 环境备忘（本机）

| 组件 | 路径 |
|---|---|
| Android SDK | `C:\Users\HinYoung\AppData\Local\Android\Sdk`（build-tools 34/35/36，platforms 34/35/36） |
| JDK 17 | `C:\Program Files\Eclipse Adoptium\jdk-17.0.20.8-hotspot` |
| Gradle 分发 | 已缓存于 `~/.gradle/wrapper/dists/gradle-8.4-bin/` |

**网络提示**：本机代理只放行 `api.github.com`，`git push` 到 github.com 会 502。
代码更新需通过 GitHub API 推送，或在可直连的网络环境下操作。
