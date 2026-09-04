# AZime 输入法

基于 RIME（librime）框架的 Android 输入法 - v0.2.0-preview

> RIME 引擎已集成（librime_jni.so，arm64-v8a），26 键键盘 + 候选栏 + 手势可日常输入。
> 详细构建状态见 [BUILD_STATUS.md](BUILD_STATUS.md)。

## 特性

- 🎨 **Material Design 3** - Compose 实现，浅色/深色主题跟随系统
- ⌨️ **26 键键盘** - Q-P / A-L / shift+Z-M+退格 / 符号,空格,句号,回车，附增高行候选栏
- 👆 **三层手势** - 平点打字 · 上/下滑直出符号（带键面预览与触觉反馈）· 长按弹符号气泡
- 🔄 **RIME 引擎** - 内置五笔86（拼音混输）/ 五笔86 / 简体拼音方案，候选栏一键切换
- 📝 **候选栏** - preedit 回显、编号选候选、拼音注释、中/英切换、翻页
- 🔧 **方案管理** - 支持 ZIP 导入输入方案
- 🌟 **Lua 脚本** - 通过 Lua 脚本自定义按键功能
- 📂 **外部存储** - 方案、字体、脚本统一管理在 `/storage/emulated/0/Documents/AZime`

## 项目结构

```
AZime/
├── app/src/main/
│   ├── java/
│   │   ├── com/azime/input/
│   │   │   ├── AZimeApplication.kt          # 应用入口
│   │   │   ├── ime/
│   │   │   │   ├── AZimeService.kt          # 输入法服务（全链路接线）
│   │   │   │   └── ImeLifecycle.kt          # Compose 在 IME 中的生命周期桥
│   │   │   ├── ui/
│   │   │   │   ├── main/MainActivity.kt     # 主界面
│   │   │   │   ├── settings/SettingsActivity.kt  # 设置界面
│   │   │   │   ├── editor/KeyboardEditorActivity.kt  # 键盘编辑器（占位）
│   │   │   │   └── keyboard/KeyboardScreen.kt    # Compose 键盘 + 候选栏
│   │   │   ├── core/
│   │   │   │   ├── rime/RimeManager.kt      # RIME 封装（资产部署/按键处理）
│   │   │   │   ├── lua/LuaScriptManager.kt  # Lua 脚本管理
│   │   │   │   ├── keyboard/KeyboardManager.kt  # 布局管理（待接入持久化）
│   │   │   │   └── storage/StorageManager.kt    # 外部存储管理
│   │   │   ├── utils/SchemaImporter.kt      # ZIP 方案导入
│   │   │   └── data/
│   │   │       ├── keyboard/KeyboardPages.kt    # 内置 26 键/符号页布局
│   │   │       └── model/KeyboardLayout.kt      # 数据模型
│   │   └── com/kingzcheung/xime/rime/       # ⚠️ 包名不可改：JNI 符号绑定
│   │       └── RimeEngine.kt                # vendored 自 ximeiorg/Xime (GPL-3.0)
│   ├── jniLibs/arm64-v8a/librime_jni.so     # 静态链接的 librime（提取自 Xime 2.6.2）
│   └── assets/rime/                         # 内置 RIME 数据（词库/方案/lua）
└── README.md
```

## RIME 方案

内置方案（首次启动自动部署，词典编译需数十秒）：

| 方案 | 说明 |
|---|---|
| `wubi86_pinyin` | 五笔86·拼音混输（默认） |
| `wubi86` | 五笔86 |
| `pinyin_simp` | 简体拼音 |

候选栏空态点击方案名可弹出菜单切换。已知限制：拼音反查（`` ` ``）依赖的 stroke 词典未内置。

## 依赖库

- **Android Jetpack**: Compose / Material 3 / Lifecycle / Preferences
- **第三方**: Kotlin Coroutines / LuaJ 3.0.1 / Zip4j 2.11.5 / Gson
- **RIME**: librime（静态链接于 `librime_jni.so`，52 个 JNI 符号）

## 构建要求

- JDK 17 / Android SDK 34（build-tools 34+）
- Gradle 8.4（wrapper 已固定，**AGP 8.3 不能搭配 Gradle 9**）
- minSdk 24 (Android 7.0+)，**仅 arm64-v8a**

> ⚠️ 项目路径含非 ASCII 字符（如中文目录）时 AGP 会拒绝构建；当前以
> `gradle.properties` 的 `android.overridePathCheck=true` 绕过，最稳妥是放在纯英文路径。

## 构建与安装

```bash
# 构建 Debug APK（需 ANDROID_HOME 指向已安装 SDK 的目录）
./gradlew assembleDebug

# 或直接推送到 GitHub 由 Actions 构建（产物 app-debug artifact）
```

真机一键验证（安装 + 启用输入法 + 日志监控）：

```bash
python ../verify_device.py        # 在仓库上级目录
```

## 使用方法

1. 安装并启用输入法：系统设置 → 虚拟键盘 → 启用 AZime，然后切换为当前输入法
2. 首次使用等待词典部署（候选栏有进度提示）
3. 中文模式敲编码出候选；候选栏点选或按数字键上屏；空格选首候选
4. 字母键：平点打字 / 上滑直出数字、下滑直出符号 / 长按弹符号气泡
5. 退格：平点删一个，按住连删
6. 中/英切换在候选栏左端；回车无编码时为换行

## 参考项目

- [fcitx5-android](https://github.com/fxliang/fcitx5-android/) - 小企鹅输入法.fx（键盘交互参考）
- [Xime](https://github.com/ximeiorg/Xime) - 曦码输入法（RimeEngine 与 librime_jni.so 来源，GPL-3.0）
- [trime](https://github.com/osfans/trime) - 同文输入法
- [trime2](https://github.com/nirenr/trime2) - 同文输入法 v2

## 开发状态

- [x] 基础项目结构 / Material Design 3 UI / 存储管理 / 方案导入 / Lua 支持
- [x] RIME 引擎集成（librime + 内置词库 + 部署管理）
- [x] 26 键键盘 + 候选栏（preedit / 编号候选 / 中英切换 / 翻页）
- [x] 手势（上/下滑直出符号、长按符号气泡、退格连删）
- [x] 深色主题跟随系统 / 候选栏内方案切换
- [ ] 键盘布局编辑器（占位页）
- [ ] 字体管理界面
- [ ] Lua 脚本编辑界面
- [ ] KeyboardManager 布局持久化
- [ ] 更多 ABI 支持（armeabi-v7a / x86_64 的 librime）

## 许可证

RimeEngine.kt 与 librime 衍生自 GPL-3.0 项目（Xime/Trime/librime），本项目需以 GPL-3.0 发布。TBD
