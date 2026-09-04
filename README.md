# AZime 输入法

基于 RIME 框架的 Android 输入法应用 - 预览版 (v0.1.0-preview)

## 特性

- 🎨 **Material Design 3** - 现代化的界面设计
- 📝 **灵活的键盘布局** - 可视化编辑键盘布局，无需直接编写代码
- 🔧 **方案管理** - 支持导入 ZIP 格式的输入方案
- 🎯 **字体支持** - 支持主字体和多个备用字体，完美显示生僻字
- 🌟 **Lua 脚本** - 通过 Lua 脚本自定义 preset_keys 按键功能
- 📂 **外部存储** - 方案、字体、脚本统一管理在 `/storage/emulated/0/Documents/AZime`

## 项目结构

```
AZime/
├── app/
│   └── src/main/
│       ├── java/com/azime/input/
│       │   ├── AZimeApplication.kt          # 应用入口
│       │   ├── ime/
│       │   │   └── AZimeService.kt          # 输入法服务
│       │   ├── ui/
│       │   │   ├── main/MainActivity.kt     # 主界面
│       │   │   ├── settings/SettingsActivity.kt  # 设置界面
│       │   │   ├── editor/KeyboardEditorActivity.kt  # 键盘编辑器
│       │   │   └── keyboard/KeyboardView.kt # 键盘视图
│       │   ├── core/
│       │   │   ├── rime/RimeManager.kt      # RIME 引擎管理
│       │   │   ├── lua/LuaScriptManager.kt  # Lua 脚本管理
│       │   │   ├── keyboard/KeyboardManager.kt  # 键盘布局管理
│       │   │   └── storage/StorageManager.kt    # 存储管理
│       │   ├── utils/
│       │   │   └── SchemaImporter.kt        # 方案导入工具
│       │   └── data/model/
│       │       └── KeyboardLayout.kt        # 数据模型
│       └── res/
└── README.md
```

## 外部存储目录

位置: `/storage/emulated/0/Documents/AZime/`

```
AZime/
├── schema/              # 输入方案目录
│   ├── pinyin/         # 拼音方案示例
│   └── wubi/           # 五笔方案示例
├── fonts/              # 字体文件目录 (.ttf, .otf, .ttc)
│   ├── main.ttf
│   └── fallback.ttf
└── lua/                # Lua 脚本目录
    └── preset_keys.lua # 按键配置脚本
```

## 依赖库

- **Android Jetpack**
  - Compose (UI 框架)
  - Material 3 (设计系统)
  - ViewModel & Lifecycle
  - Preferences

- **第三方库**
  - Kotlin Coroutines (异步处理)
  - LuaJ (Lua 脚本支持)
  - Zip4j (ZIP 文件处理)
  - Gson (JSON 处理)

## 构建要求

- Android Studio Hedgehog | 2023.1.1+
- Gradle 8.4+
- JDK 17
- Android SDK 34
- minSdk 24 (Android 7.0+)

## 构建步骤

```bash
# 克隆项目
git clone <repository-url>
cd AZime

# 构建 Debug APK
./gradlew assembleDebug

# 构建 Release APK
./gradlew assembleRelease

# 安装到设备
./gradlew installDebug
```

## 使用方法

1. **安装应用**
2. **启用输入法**: 设置 → 系统 → 语言和输入法 → 虚拟键盘 → 管理虚拟键盘 → 启用 AZime
3. **选择输入法**: 在文本框中切换到 AZime 输入法
4. **导入方案**: 打开 AZime 应用 → 设置 → 导入方案 → 选择 ZIP 文件
5. **自定义字体**: 将字体文件放入 `/storage/emulated/0/Documents/AZime/fonts/`
6. **编辑 Lua 脚本**: 编辑 `/storage/emulated/0/Documents/AZime/lua/preset_keys.lua`

## 参考项目

- [fcitx5-android](https://github.com/fxliang/fcitx5-android/) - 小企鹅输入法.fx
- [Xime](https://github.com/ximeiorg/Xime) - 曦码输入法
- [trime](https://github.com/osfans/trime) - 同文输入法
- [trime2](https://github.com/nirenr/trime2) - 同文输入法 v2

## 开发状态

- [x] 基础项目结构
- [x] Material Design 3 UI
- [x] 存储管理
- [x] 方案导入功能
- [x] Lua 脚本支持
- [ ] RIME 引擎集成 (待完成)
- [ ] 键盘布局编辑器 (待完成)
- [ ] 字体管理界面 (待完成)
- [ ] 候选词显示 (待完成)
- [ ] 主题系统 (待完成)

## 许可证

TBD

## 贡献

欢迎提交 Issue 和 Pull Request！
