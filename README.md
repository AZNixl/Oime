# Oime（○输入法）

Android RIME 输入法 —— librime + Jetpack Compose

> 由 AZime 更名而来。内置 RIME 引擎（librime_jni.so，arm64-v8a），
> 支持方案导入、预设置 Lua（preset_keys）、可视化键盘布局编辑、剪贴板收藏与更多候选面板。

## 特性

- ⌨️ **多键盘**：26 键 / 九宫格数字 / 符号分类网格 / emoji 网格，均支持左右滑动分类与等高布局
- 👆 **手势**：上/下滑直出符号 · 长按符号气泡 · 退格左滑选择删除 · 红摇杆光标/快捷指针
- 🔄 **RIME 引擎**：简体拼音等内置方案，候选横滚 + 数字前缀选词 + 更多候选网格面板（◀▶ 翻页）
- 📋 **剪贴板中心**（jqb 风格）：剪贴板历史 / 收藏短语双选项卡，卡片列表，︙ 菜单（收藏/置顶/分词/删除）
- 🧩 **方案管理**：ZIP / 文件夹导入，导入后重命名，外置目录 Documents/Oime
- 🛠 **键盘编辑器**：可视化编辑键位、宽度、高度系数、滑动手势
- 🎨 **外观**：键高/增高行可调，自定义字体（键帽/候选分字体），设置页配色跟随主题回车键
- 💬 **打字振动**：总开关 / 按下 / 抬起 / 系统或自定义时长
- 🧭 **首次启动向导**：读取本地文件权限 → 启用输入法 → 选择输入法 → 进入设置

## 外部目录

```
/storage/emulated/0/Documents/Oime/
├── schema/        # 输入方案（子文件夹，可重命名）
├── fonts/         # 字体文件
└── lua/           # preset_keys.lua 预设置
```

## 构建

GitHub Actions 自动构建 debug APK；vendored RimeEngine（com.kingzcheung.xime.rime）提供 JNI 绑定。

## 许可

GPL-3.0
