# ○输入法 · Oime

**Android RIME 输入法** —— librime 引擎 + Jetpack Compose 键盘，方案组零拷贝架构，支持本地语音识别。

> 由 AZime 更名而来（包名保持 `com.azime.input`）。
> 当前版本：**0.9.27-oime（vc37）** · 最低 Android 7.0（minSdk 24）· 仅 arm64-v8a

---

## ○ 圆环：整条工具栏的中心，也是这颗输入法的「指纹」

工具栏正中的那颗 **○ 圆环**，灵感来自 **BlackBerry 的轨迹球（Trackball）与光学触摸板（Optical Trackpad）**。

那是手机史上最有辨识度的拇指交互之一：一颗小球（后来的光学小板）嵌在机身里，
拇指在上面搓动，光标就跟着走；**按一下 = 确认，长按 = 呼出菜单**——
BlackBerry 的经典交互逻辑几乎都是从这颗小球展开的。

○ 输入法把它做成屏幕上的**一颗环**：球体在动、手指的落点不动（像拇指搓轨迹球那样"拨"），
于是一个可点击、可长按、可四向拨动、还能上下滑的控件就成型了。

| 手势 | 动作 | 对应轨迹球的什么习惯 |
|---|---|---|
| 点击 | 呼出 ○ 菜单 | 长按呼出菜单（BlackBerry 传统） |
| 长按 | 语音输入 | 长按启动"另一件事" |
| 左右拖动 | **移动光标**（呼啦圈：环自身跟着手指平移，锚点不动） | 搓动轨迹球移动光标 |
| 下滑 | 收起键盘 | —— |
| 上滑 | 快捷应用悬浮栏（5 个应用，滑动选择、松手启动；下滑可取消） | —— |

圆环本身有三种形态（设置 → O 圆环）：

- **圆形环**：白色呼吸环（默认）
- **圆角方形环**：方形描边，硬朗
- **眼睛**：环内一双眼睛，会**随机**眨眼 / 连眨两下 / 左右看 / 上下看 / 眯眼，间隔 2~9 秒

---

## 功能

### ⌨️ 键盘与布局

- **五种键盘页**：26 键主键盘 / 九宫格数字 / 26 键符号 / 全部符号（分类网格）/ emoji
- **横屏自适应**：横屏沿用同一套布局，仅把键高限低 25%（竖屏键高 × 0.75），
  避免横屏时键盘占掉大半个屏幕
- **键盘编辑器**：可视化网格编辑——标签 / code / 宽度 / 高度系数 / 长按 / 上·下·左·右四向滑动 / 角标提示，可增删键与行
- **工具栏工具**（默认不显示，自行勾选）：剪贴板 / 方案 / 数字 / emoji / 符号 / 设置 / 语音 / 收起 / 中英 / 撤回 / 重做
- **四向动作自由定义**：任意字母键都能挂 4 个滑动动作（支持 `[]` 这类"上屏后光标居中"写法）

### 👆 手势与键面

- 四向滑动直出符号、长按符号气泡（可滑动选择，多行气泡支持上滑换行）
- 退格左滑选择删除、上滑全清、下滑撤回（操作栈：插入→删除、删除→恢复）
- 键面四向提示按字面方向显示（上=正上、下=正下、左=正左、右=正右），长按提示在右上角
- 括号类符号上屏后**光标自动停在括号中间**（`{text}{Left}` 语法，用 `setSelection` 实现）

### 🔄 RIME 引擎

- librime（`librime_jni.so`）+ **方案组架构**：组目录即 librime 的 user 数据目录，零拷贝加载
- 方案组切换、ZIP / SAF 文件夹导入、方案重命名、导入后全量部署
- 候选横滚 + 数字前缀选词 + 更多候选网格面板（◀▶ 翻页）
- 方案切换：工具栏「方案」按钮 → 键盘下方中间的横向悬浮栏，点一下即切

### 💬 语音输入（三引擎可选）

| 引擎 | 说明 |
|---|---|
| SenseVoice（离线） | 本地 int8 模型，中/英/日/韩/粤，松手出全文 |
| 流式 zipformer（离线） | 边说边出字 |
| 联网 API | 任意 OpenAI 兼容的 `/v1/audio/transcriptions`（自填地址/密钥/模型） |

模型侧载在 `Documents/Oime/models/`，缺模型时按键给出提示而不是崩溃。

### 📋 剪贴板中心

剪贴板历史 / 收藏短语双选项卡、卡片列表、︙ 菜单（收藏 · 置顶 · 分词 · 删除 · 全清）；
复制后工具栏常驻一条"复制条"：**点击 = 上屏**，**左右划动 = 消亡**（不用先上屏就能清掉强制复制的无效内容）；
打字不会让它消失。可在设置里整体关闭（关闭后不再监听剪贴板）。

### 🪟 悬浮窗（编码预览）

跟随光标显示（`requestCursorUpdates` + `CursorAnchorInfo`），
**输入码前三码 + 前 6 个候选**显示在悬浮窗，余下的码仍在工具栏；
字号与工具栏候选字号同步，位置 / 透明度 / 样式可自定义。

### 🎨 外观

- 自绘图标集 **OimeIcons**（32 枚，24 网格手绘，回车是纸飞机），跟随主题 tint
- 键高 / 增高行 / 工具栏高度 / 按键圆角 / 行距列距 / 键盘与工具栏字号分别可调
- 多字体回退链（键帽字体与候选字体分开），主题配色 + 强调色，暗色主题
- 设置主页：状态卡（引擎状态、当前方案）+ 独立卡片式大项；版本号动态读取

### 🧭 其他

- **首次向导 5 页**：存储权限 → 启用输入法 → 选择输入法 → 进入设置 → O 圆环快捷应用
- **O 圆环上滑快捷启动**：5 个应用槽位自定义（需要 `QUERY_ALL_PACKAGES`）
- **外置 Lua 预设置**：`preset_keys.lua`（trime2 兼容表格式），Lua 编辑器带语法校验
- **省电**：热路径零 IO（方案显示名 / 方案列表全部缓存或按需取）、动画降帧（≈8fps 步进）、
  键盘收起即关面板、退出时释放 librime 与语音 ONNX 会话

---

## 借鉴与引用

○输入法站在这些项目的肩膀上，感谢作者们：

| 项目 | 借鉴 / 引用点 |
|---|---|
| [rime/librime](https://github.com/rime/librime) | RIME 引擎本体 |
| [osfans/trime](https://github.com/osfans/trime) | 整体形态、悬浮窗（编码预览）、`preset_keys` Lua 约定 |
| [nirenr/trime2](https://github.com/nirenr/trime2) | **方案组架构**（组目录即 librime user 目录、零拷贝）、RimeLifecycle 生命周期、窗口隐藏时释放语音对象 |
| [ximeiorg/Xime](https://github.com/ximeiorg/Xime) | 内置的 librime JNI 绑定（`com.kingzcheung.xime.rime`）、按键路由与键盘页思路、光标监听与资源释放策略 |
| [k2-fsa/sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx) | 本地语音识别（SenseVoice / 流式 zipformer，官方 release AAR） |
| [tiann/KernelSU](https://github.com/tiann/KernelSU) | 设置主页的卡片式布局风格 |
| [PiliPlus](https://github.com/bggRGjQaUbCoE/PiliPlus) | 底部悬浮栏（面板与 chip 条）样式参考 |
| jqb.lua · 复制自动添加到候选.lua · 悬浮窗显示优化.lua | 剪贴板中心形态、复制入候选、悬浮窗行为参考 |
| [rime/weasel](https://github.com/rime/weasel) | 方案与词库的组织方式参考 |
| AZNixl/trime2.az · AZNixl/Xime.az | 本人的 trime2 / Xime fork，早期 UI 风格与 Lua 脚本生态（振动 / 动效 / 悬浮窗） |
| BlackBerry Trackball / Optical Trackpad | **○ 圆环的设计灵感**（交互隐喻，非代码） |

---

## 外部目录

```
/storage/emulated/0/Documents/Oime/
├── schema/        # 输入方案组（每个子目录 = 一个完整方案包）
├── fonts/         # 字体文件（键帽 / 候选可分别选用）
├── lua/           # preset_keys.lua 预设置
└── models/        # 语音模型（SenseVoice / zipformer）
```

---

## 构建

```bash
# 本机只做语法 / 类型校验（不出包）
./gradlew compileDebugKotlin

# 出包由 GitHub Actions 完成（仓库 Actions → 最新 run → app-debug）
# 语音依赖 sherpa-onnx-1.13.5.aar（约 49MB）不入库，CI 构建前从官方 release 下载到 app/libs/
```

- 技术栈：Kotlin 1.9.22 / AGP 8.3.0 / Gradle 8.4 / Compose BOM 2024.02.00 / JDK 17
- 引擎：`librime_jni.so`（arm64-v8a）；Lua 用 luaj-jse 3.0.1；方案导入用 zip4j
- 项目路径含非 ASCII 字符时需 `gradle.properties` 里的 `android.overridePathCheck=true`

---

## 许可

**GPL-3.0**。本项目为 RIME 生态的衍生作品，继承上游 GPL 许可；
引用到的第三方项目版权归各自作者所有（见上表）。

---

## 更新说明

这份 README 会**随功能改造同步更新**——新功能、新的借鉴来源、以及 ○ 圆环的新玩法
都会在落地后补进来。

- 逐轮开发记录：[DEVLOG.md](DEVLOG.md)
- 当前状态：[BUILD_STATUS.md](BUILD_STATUS.md)
- 阶段总结（耗电优化 / 交互定稿）：[PHASE_REPORT.md](PHASE_REPORT.md)
