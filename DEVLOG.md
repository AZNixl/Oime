# ○输入法 开发记录（2026-09-05）

本轮按 11 项需求完成大改造。App 更名「○输入法」（包名保持 `com.azime.input`），整体框架向小企鹅（fcitx5）+ trime2 看齐，平台 RIME。

## 1. Lua 外置配置（trime2 兼容）

- 外置脚本目录：`Documents/AZime/lua/preset_keys.lua`（StorageManager 既有约定）。
- `LuaScriptManager` 升级：LuaJ 解析 trime2 表格式条目（`{ label, send, commit/text }`）与字符串条目；支持脚本 `return` 表或定义全局 `preset_keys` 表两种写法。
- 动作解析 `resolveAction()` 三级优先：
  1. preset_keys 条目名引用；
  2. 内置命令 identifier（`select_all` `cut` `copy` `paste` `toggle_ascii` `newline` `caps_lock` `delete_all` `undo` `page:symbols` `page:numpad` `page:emoji` 等）；
  3. 字面文本，兼容 trime2 `{text}{Left}` 光标后缀语法。

## 2. 方案管理

- **切换**：设置页方案卡内直接列出已部署方案（单选切换），候选栏空态点方案名弹出菜单同样可切。
- **导入**：ZIP（zip4j，自动探测 UTF-8/GBK 文件名编码，修复中文乱码）与 SAF 文件夹两种入口。
- **部署链**：导入 → `Documents/AZime/schema/<名>/` → 下次引擎初始化（或导入后立即）同步 yaml 进 `rime/shared`，自动解析 `schema_id` 追加生成 `default.custom.yaml` 的 schema_list → 触发全量维护。内置 `default.custom.yaml` 仅保留 pinyin_simp。

## 3. 键盘布局编辑器 v2（可视化）

- 网格等比渲染布局，点击任意按键弹属性对话框：标签 / code / 宽度 / 长按 / 上·下·左·右滑动 / 右上角提示；支持增删键、增删行；保存即持久化并可设为当前主键盘。
- 动作字段存于 `Key`（`longClick/swipeUp/swipeDown/swipeLeft/swipeRight/hint`），与 preset_keys 解析器共用一套取值约定。

## 4. 字体管理（外置 + 多字体）

- 放弃 SAF 导入，直接扫描 `Documents/AZime/fonts/` 下的 ttf/otf/ttc。
- 两个独立角色：**键帽字体** 与 **候选字体**，各字体卡片实时预览，可分别选用/删除。

## 5. UI 参考 KernelSU

设置主页重写：顶部状态卡（○ logo + 引擎状态灯 + 当前方案）+ 分组卡片（输入方案 / 键盘 / 外观 / 高级 / 关于），KSU 风格图标 + 副标题 + 右箭头条目。

## 6. 命名

`app_name` → **○输入法**；包名不变 `com.azime.input`；候选栏/空格键展示同步更换。

## 7-8. 工具栏 + 红摇杆

- 工具栏（候选栏上方 40dp）：最左 **○ 菜单键**（弹出：设置 / 剪贴板 / 26键 / 数字 / emoji / 符号）；中间剪贴板条显示最近复制内容（Android 10+ IME 剪贴板监听，图片剪贴板落盘 `filesDir/clipboard/`）；最右 **红摇杆**（ThinkPad 小红点风格，红色圆点）。
- 剪贴板面板：分词（中英混合切分）、提取英文词、提取网址，点 chip 直接上屏。
- 红摇杆两种模式：**光标移动**（拖动横移光标）/ **快捷指针**（拖动扩展选区）；按住不动弹气泡选择，选中后记忆，下次拖动直接生效。

## 9. 手势与键盘页

| 键 | 手势 | 行为 |
|---|---|---|
| 退格 | 左滑 | 逐字符扩展选区（持续滑动），松手删除选中 |
| 退格 | 上滑 | 全删（光标前后全部文本） |
| 退格 | 下滑 | 撤回（本地 undo 栈，最近 50 条上屏记录） |
| 退格 | 长按 | 连删 |
| 回车 | 长按 | 换行 |
| 空格 | 长按 | 切换中英文 |
| Shift | 长按 | 锁定大写（capsOn） |
| 符号键 | 长按 | 气泡选择默认键盘（26键符号 / 九宫格数字 / emoji），记忆为下次默认 |

- 新增 **九宫格数字键盘**、**emoji 键盘**（7 分类 tab，点选直出）。
- 字母键长按气泡符号与上下滑直出保留（fcitx5 风格映射，可被 preset_keys 覆盖）。

## 10. 内置方案裁剪

assets/rime 仅保留 pinyin_simp（+symbols/default 配置与 lua）；资产清单变化时自动清理 shared 目录旧文件（wubi86 系列等）并重新部署。

## 构建与验证

- CI：GitHub Actions 自动构建 arm64-v8a debug APK。
- 真机待验证项：导入方案中文 zip 乱码修复效果、方案切换、可视化编辑器全链路、emoji/九宫格、摇杆手感、滑动选删阈值。

## 已知限制

- undo 栈仅记录本输入法会话内的上屏内容，不覆盖 App 外部输入。
- 图片剪贴板仅落盘保存，暂未在面板内缩略预览。
- trime2 键盘 lua 布局文件（keyboards/*.lua）暂未直接渲染，仅 preset_keys.lua 动作约定兼容。

---

# 补充轮（2026-09-05 晚）：真机反馈 9 条 + native 崩溃修复

## Native SIGSEGV 修复（重要）

真机 tombstone：`refreshState → isReady → getAvailableSchemas`（DefaultDispatcher）
与维护线程 `ConfigData::LoadFromFile` 并发 → 「trying to execute non-executable memory」。
vendored RimeEngine 中 `getAvailableSchemas / getSchemaString / getSchemaList`
三个查询是裸调 native，没走 rimeLock。已统一加 `tryLocked`（拿不到锁返回空默认，
维护期间不进 native）。

## 9 条反馈落地方案

| # | 反馈 | 实现 |
|---|---|---|
| 1 | A-L 行键宽与第一行一致 | 每行按 10 份计宽，不足的行尾部留白 |
| 2 | 剪贴板条不消失/挡候选 | 条目只显示 10 秒；从面板上屏后条目清除+面板收起 |
| 3 | 工具栏自定义（参考 xime） | 长按 ○ 勾选工具（剪贴板/方案/数字/emoji/符号/设置），○ 与红摇杆固定 |
| 4 | 摇杆短距远点 | 快捷指针模式 10 字/步远距跳转；光标模式 1 字/步 |
| 5 | ○ 菜单面板化 | 键盘内嵌面板（剪贴板/页面/设置/方案切换），非浮窗 |
| 6 | 中文大写/英文小写键帽 | 显示层切换；中文输入逻辑不变（仍送小写编码） |
| 7 | 字体支持/增高行调整/方案二级菜单 | 设置页键高+增高行滑杆（下次键盘弹出热生效）；方案管理收二级页 |
| 8 | 退格滑动删除不正常 | 按 trime2「退格键滑动删除.lua v4」锚点模型重写：24px/字符、10px 进入阈值、横向占优判定、组合中禁用、右滑回退到锚点 |
| 9 | 开发记录 | 本文 |

## 收尾轮：CI 修复 + LICENSE + ○ 图标 + lua 键盘布局导入

### CI 修复（run 33963483159 → 33963940735 ✅）
- `KeyboardScreen.kt` 两类编译错误：
  1. 工具栏自定义对话框缺 material3 导入（AlertDialog/Checkbox/TextButton）；
  2. 剪贴板条文本用了未定义的 `NL` 常量 → `"\n"`。

### 新增
- **GPL-3.0 LICENSE**：gnu.org 官方文本入库。
- **○ 启动图标**：深色圆盘（#17181C）+ 白色圆环 + 红摇杆红点（#E5484D，环右缘）；
  自适应图标（anydpi-v26 vector）+ API 24/25 五密度 PNG 回退（此前 mipmap 为空，
  Android 7.x 会 Resources.NotFoundException）。
- **trime2 lua 键盘布局直接渲染**（前轮遗留项落地）：
  - 外置目录 `Documents/AZime/lua/keyboards/*.lua`，首次运行生成 example.lua 模板；
  - `LuaScriptManager.parseKeyboardLayout()`：LuaJ 解析 `return { name=..., rows={ {keys={…}} } }`
    （行支持无 keys 包装的直接键数组），键字段 label/click/long_click/swipe_*/width/hint；
  - KeyType 按 click 推断：BackSpace→DELETE、Return→ENTER、space→SPACE、shift→MODIFIER、
    单字符→CHARACTER、其余→FUNCTION；
  - `onKeyAction` FUNCTION 分支接入 resolveAction：命令/preset 引用/文本上屏（trime2 语义：
    非命令 click 按文本 commit），仅当解析结果为 null 时回退中英切换；
  - 键盘编辑器列表页新增「导入 lua 键盘布局」卡片：批量解析导入为自定义布局，
    逐文件反馈成功/失败，导入后可激活/继续可视化编辑。

### 暂缓
- 多 ABI（armeabi-v7a/x86_64）：需要与 vendored RimeEngine JNI 符号匹配的官方
  librime_jni.so 构建，风险大于收益（目标设备 arm64），待有可靠构建源再补。

## Oime 轮：7 条新需求

| # | 需求 | 实现 |
|---|---|---|
| 1 | 打字振动系统 | 新增 HapticsManager（core/haptic）：总开关、按下震动、抬起震动、系统默认/自定义模式（5-60ms 滑杆）；Vibrator VibrationEffect（API<26 走旧 API）；KeyboardScreen 按键 down/release 钩子；设置页新增「打字振动」卡片 |
| 2 | 按键编辑界面增强 | Key 模型新增 height 系数（0.5-2.0）；KeyEditDialog 加高度字段 + 取消按钮；编辑器网格与真实键盘行高均按行内最大 height 系数渲染 |
| 3 | LUA 脚本改名「预设置」 | 设置入口更名；LuaEditorActivity 顶栏加「说明」按钮：用途 / 条目定义 / 动作取值优先级 / 内置命令表 / 完整示例，可滚动 |
| 4 | 字体管理读不到 | 根因：Android 13+ File API 读 Documents 受限（READ_EXTERNAL_STORAGE 不覆盖）。修复：①外置扫描改递归（支持子文件夹）；②新增 SAF「从文件夹导入」把字体复制进应用私有 filesDir/fonts（一定可读）；③列表双源合并、同名私有优先 |
| 5 | 包名/文件夹改 Oime | applicationId → com.oime.input（与旧版并存，需重新选输入法）；外置目录 → Documents/Oime；vendored RimeEngine 包名不动（JNI 符号绑定） |
| 6 | 导入后重命名 | 导入方案成功后弹 Material 对话框改写 Documents/Oime/schema 子文件夹名（非法字符校验、目标存在校验），重命名后自动重新部署；内部 schema_id 不变 |
| 7 | 开发记录/上传/APK | 本文；push 后 CI 构建 |

### 顺带修复
- 上一轮遗留编译错：KeyboardScreen FUNCTION 键误传 ResolvedAction 对象给
  KeyAction.Resolved(String)（1219 行）→ 改传 key.code 字符串，由 Service 端 resolveAction 解析。

## 反馈轮 2：8 条体验修正

| # | 反馈 | 实现 |
|---|---|---|
| 1 | 工具栏不显示候选字 / ○ 菜单参考 xime | 根因：CandidateBar 组件写了但从未挂载。改为候选词直接占工具栏中部（打字时显示，点选上屏，横向滚动 + ▶ 翻页；空闲时恢复工具+剪贴板条）。○ 菜单参考 xime MenuBar 重做：顶部 ↑ 关闭 + ⚙ 设置圆钮，图标网格（剪贴板/26键/数字/表情/符号/定制工具栏，4 列 icon+label 磁贴），底部方案 chips |
| 2 | 九宫格无返回键 | numpad 第 3 行末改 ⌫，第 4 行首加「26」返回主键盘；工具栏「123」在 numpad 页变「26」亦可返回 |
| 3 | 符号键盘理解纠正 | 新增分类网格符号页 symgrid（常用/引号/数学/箭头/货币/序号/特殊 7 类，SymbolData），工具栏「符」与菜单「符号」指向它；26 键符号页保留 |
| 4 | emoji 横滑 + 高度压缩 | emoji 与符号页共用 CategoryGridPane：HorizontalPager 左右滑动切分类（点标签同步翻页），格子高度跟随主键盘键高 |
| 5 | 编辑器去 lua 导入 / numpad 打不开 | 移除「导入 lua 键盘布局」入口与整套 lua 键盘解析（parseKeyboardLayout/importLuaLayouts/example.lua）；builtinByName 补 numpad 修复内置九宫格无法进入编辑 |
| 6 | 增高行开关 | KeyboardManager.barEnabled 持久化；关闭时工具栏/候选栏回落 38dp 紧凑高度；键盘二级页开关 + 高度滑杆联动显隐 |
| 7 | 设置一级菜单 | 主页改纯入口（输入方案/键盘/外观/预设置/关于），滑杆、振动开关等全部收进对应二级页（键盘页含布局编辑器+键高+增高行+振动；外观页字体管理；关于页版本/GitHub） |
| 8 | 设置主题跟随回车键颜色 | MaterialTheme colorScheme 按 system dark 选择基础方案，primary=键盘 accentActive、primaryContainer=回车键底色 accentKeyBg（keyboardAccentActiveColor/keyboardAccentKeyColor 公开） |

### 真机验证（20:31-20:37, b72e0041）
- 新包 com.oime.input 装机、ime enable、切默认输入法 ✅；旧外置资产（方案/fonts/lua）已复制到 Documents/Oime。
- 键盘渲染 ✅（大写键帽/○ 工具栏/红摇杆/方案名）；○ 面板磁贴 ✅；设置页：运行正常·简体拼音（迁移方案自动部署）✅、打字振动卡片 ✅。
- 无崩溃（logcat 无 FATAL）。期间屏幕跳变系人机同时操作，自动化盲测暂停，交由用户手测。

## 反馈轮 3：7 条（jqb 剪贴板 / 更多候选 / KSU 设置）

| # | 反馈 | 实现 |
|---|---|---|
| 1 | ○ 菜单「定制工具栏」「剪贴板」无效 | 双根因：①定制工具栏用 Compose AlertDialog——对话框窗口 z-order 低于 IME 窗口被键盘挡住（隐形）；②剪贴板面板在 clipText 为空时直接 return（不可见），工具栏 clipboard 工具也无分发分支。修复：定制工具栏改内联面板（ToolbarCustomizePanel，勾选+保存/取消）；剪贴板面板改为永远可见（空态提示）；工具栏补 clipboard 分支 |
| 2 | 九宫格缩在左边大片空白 | numpad 每行 4 键 × 键宽 1f = 4/10 份，行尾补白 6 份。改键宽 2.5f（底行 5 键 × 2f），铺满整行 |
| 3 | 增高行语义纠正 | 增高行 = 键盘最后一行下方多一个**无按键空行**（高度滑杆控制），不再是工具栏变高；工具栏固定 38dp 紧凑高度 |
| 4 | 剪贴板分词等功能收进菜单（参考 jqb.lua） | 剪贴板面板整体重做：剪贴板/收藏双选项卡（滑块式高亮）、卡片列表（序号+文本）、每条 ︙ 菜单（收藏/置顶/分词/删除），分词在卡片内展开 token chips 点击上屏；Service 端剪贴板历史自动记录（去重插首、上限 100 条）+ 收藏短语，分别持久化 filesDir/clipboard.json / phrase.json（org.json） |
| 5 | 更多候选面板 | 新增 CandidatePanel：工具栏候选行尾「▾」入口，5 列网格展示当前页全部候选（带数字前缀），◀▶ 翻页（PageUp/PageDown keysym 0xFF54/0xFF55），点选上屏；候选清空自动收起 |
| 6 | 设置主页参考 KSU | 大方块状态卡（○ logo+引擎状态）+ 两个小方块（版本 0.5.0-oime / 项目 AZNixl/AZime）+ 下方设置项列表；增高行文案更新 |
| 7 | 记录/上传 | 本文；push 后 CI 构建，拉 APK 装机验证 |

### 顺带
- versionCode 3 / versionName 0.5.0-oime；KeyboardUiState 新增 clipHistory/phraseItems/clipTab/showCandidatePanel；
- KeyAction 新增 ToggleCandidatePanel/PageUp/SetClipTab/CommitClipText/ClipFav/ClipDelete/ClipTop/ClipClear；
- 教训沉淀：**IME 内的 Compose 一律不要用 AlertDialog**（对话框窗口在键盘下层不可见），弹层用内联面板或 Popup。

## 反馈轮 4：6 条（首启向导 / 仓库改名 / 面板等高）

| # | 反馈 | 实现 |
|---|---|---|
| 1 | 首次启动界面优化（原 AZime 名称） | MainActivity 重写为 4 页 HorizontalPager 向导：①读取本地文件权限（Android 11+ 跳「所有文件访问」开关，≤Q 走运行时 READ_EXTERNAL_STORAGE；每页实时状态徽标 ✓）②启用输入法（检测 ENABLED_INPUT_METHODS）③选择输入法（showInputMethodPicker，检测 DEFAULT_INPUT_METHOD）④进入设置；页码圆点 + 上一步/下一步/完成，ON_RESUME 自动刷新状态；Manifest 增补 MANAGE_EXTERNAL_STORAGE |
| 2 | GitHub 项目改名 Oime | API PATCH 仓库 name=Oime + 新描述（https://github.com/AZNixl/Oime）；README 整体重写为 Oime 品牌；设置页 4 处 GitHub 链接改 AZNixl/Oime；push 脚本默认仓库名改 Oime |
| 3 | 更多候选面板跟主键盘同高度 | 统一高度公式 stdH=4×keyH+5×间距（含增高行则再加）；CandidatePanel 固定 totalHeight，网格区 weight(1f)+verticalScroll |
| 4 | emoji 面板跟随主键盘高度 | CategoryGridPane 固定 totalHeight：标签行+Pager(weight 1f)+底行，单页内容可竖向滚动 |
| 5 | ○ 菜单/剪贴板覆盖主键盘 | MenuPanel / ClipboardPanel 从工具栏上方移入内容区：与键盘互斥替换显示（固定 totalHeight），不再把键盘往下顶 |
| 6 | 记录/上传 | 本文；改名后首个提交推送 AZNixl/Oime |

- versionCode 4 / versionName 0.6.0-oime。

### 热修复（真机崩溃）：SecurityException on ENABLED_INPUT_METHODS
- 现象：0.6.0 装机后打开向导即崩（targetSdk 34 读 Settings.Secure.ENABLED_INPUT_METHODS 抛
  SecurityException，Android 14 限制该 key 仅 targetSdk ≤ 33 可读；读取又在 ON_RESUME 观察器中，启动即崩）。
- 修复：启用检测改走 InputMethodManager.enabledInputMethodList（公开 API 无权限）；
  DEFAULT_INPUT_METHOD 读取包 runCatching，受限时视为未完成而非崩溃。

## 反馈轮 5：14 条（部署键 / 向导持久化 / 主题配色 / 九宫格五列 / 长按规范 / 备份）

| # | 反馈 | 实现 |
|---|---|---|
| 1 | 方案设置和 ○ 菜单添加部署键 | 方案页新增「重新部署」卡（调 deployImportedSchemas）；○ 菜单新增「部署」磁贴 → KeyAction.Deploy 由 Service 执行 |
| 2 | 跳过向导无效，每次都开向导 | wizard_prefs.wizard_done 标志：跳过/完成时写入；onCreate 检测标志或「三步全部达成」直接进设置；关于页加「重新运行向导」入口（清标志） |
| 3 | 字体设置支持多个候选字体 | FontManager 新增第三个角色「面板字体」（更多候选/emoji/符号网格用），与键帽/候选栏互相独立；字体卡三按钮：键帽/候选/面板 |
| 4 | 增高行 1-72dp | setBarHeightDp coerceIn(1,72)，滑杆 valueRange 1..72 |
| 5 | 设置增加主题和配色（参考小企鹅.fx） | 新建 KeyboardTheme（theme_prefs：mode system/light/dark + 亮/暗强调色）；二级页「主题与配色」：色彩模式三 chip + 7 个预设色板（默认蓝/中国红/森绿/暗紫/橙光/青碧/樱粉）+ 自定义 RGB 滑杆；KeyboardColors 全部由主题动态构建（accent 派生 accentActive/accentKeyBg/compositeOver 对比文字色），设置页主色同步跟随 |
| 6 | 剪贴板限高 + 标签（jqb） | 卡片文本 maxLines=3；词条下方常驻标签行：网址/电话号码/英文单词正则提取，横向滑动点选上屏；删除原「分词」开关菜单项 |
| 7 | emoji/符号面板去底行 + 左上返回 | CategoryGridPane 删除底行 ABC/空格/⌫；返回键「←」固定在顶行左上角，分类标签其右横向滑动 |
| 8 | 打字时输入码+候选覆盖工具栏；剪贴板条参考复制自动添加到候选.lua | 组合中（preedit 或候选非空）整个工具栏替换为「输入码（强调色）+ 候选横滚 + ◀▶ 翻页 + ▾」组合行；剪贴板条常驻（不再 10s 过期）、点击直接上屏（lua 同款点选上屏），打字（进入组合）或新复制时消亡（Service 在 preedit 非空时清 clipText） |
| 9 | ○菜单输入方案做成按键 | 「输入方案」磁贴 → 居中浮窗列表选择方案（选中高亮，空态「引擎部署中…」）；删除原底部方案 chips |
| 10 | 九宫格五列 + 26键对齐 | NumpadPane 专用布局：左列 = 3 行高滑动选符号键（上下滑动在 15 符号带上移动、松手上屏）+ 返回键；中间三列 = 1-0 + "." ","；右列 = ⌫/中英/空格/⏎。26 键第二行左右各加 0.5 键宽 spacer（G 对齐 V），行4 本就 10 份对齐；新增 code="spacer" 占位键（不渲染不响应） |
| 11 | 长按符号按规范重配 | Q-P→1-0；A-L→全选/-/@/#//——/+/括号气泡/=；K 长按 = 常用括号气泡（{}〈〉()《》[]【】，{Left} 光标入括号，参考 26键.lua）；Z-M→`/剪切/复制/粘贴/"/'/：；逗号→！句号→？。单符号长按松手直接上屏（不必再点气泡）；select_all/cut/copy/paste 走命令分发 |
| 12 | 备份设置到 Download | 「备份设置」：keyboard/font/haptic/theme/wizard 五组 prefs 打包 JSON，MediaStore 写入 Download/Oime_backup_时间戳.json（API<29 走公共目录） |
| 13 | 符号显示开关 | 设置→键盘→「符号显示」卡：长按提示/上滑/下滑/左滑/右滑 五个独立开关；关闭时键面角标（longPressHint）与滑动预览不显示，动作照常执行；hint 开关纳入 sizeSignature 热重建 |
| 14 | 记录/上传/暂停 | 本文；推送 CI 后暂停，等用户手机连接指令 |

- versionCode 5 / versionName 0.7.0-oime。
- 注意：九宫格页由 NumpadPane 专用渲染，键盘编辑器里对 numpad 的自定义修改不影响实际九宫格页（内置布局为数据基准）。

### 修复 2：NumpadSliderSymbols 作用域
- 首推 CI 失败发现 push_via_api.py 按 git 索引（ls-files -s）取文件，需先 git add -A；
- 二推失败：NumpadSliderSymbols 误放在 object KeyboardPages 内部，KeyboardScreen 顶层 import 无法解析 → 改 KeyboardPages.NumpadSliderSymbols 引用。

### 真机自动化验证（0.7.0-oime，commit 0ea23a7e，设备 b72e0041，Android 14）
装机 18:16:50 Success。逐项验证：
- 跳过向导持久化 ✅：跳过后进设置；HOME 重新打开 App 直达设置不再出向导；关于页有「重新运行向导」。
- 主题与配色 ✅：色彩模式三 chip、7 预设色板、自定义 RGB；点「中国红」→ 键帽勾选迁移、RGB 滑杆同步、键盘 preedit/选项卡/收起按钮全部变红（强调色即时生效）。
- 备份设置 ⚠️ 入口存在（Download 文件未在自动化点击中确认，留人工验证）。
- 26 键 ✅：A-L 偏移对齐（G 对 V），键帽角标全套新规范（Q¹-P⁰/全选/-/@/#//——/+/括号/=、Z`X剪切C复制V粘贴"B'N：M、，！。？）。
- 长按 ✅：长按 Q 松手直接上屏「1」（单符号免二次点击）；括号气泡在 K 键。
- 输入码+候选覆盖工具栏 ✅：组合中整条工具栏替换为 preedit（红色）+ 候选横滚 + ◀▶ + ▾；剪贴板条打字即消亡。
- 更多候选面板 ✅：「▾」展开 5 列网格等高面板，◀▶ 翻页、收起可用。
- ○ 菜单 ✅：8 磁贴（剪贴板/26键/数字/表情/符号/输入方案/部署/定制工具栏）；「输入方案」浮窗列表 pinyin_simp 高亮当前。
- 九宫格 ✅：五列（3 行高滑选符号键 + 26 返回 | 1-0+.`, | ⌫/中英/空格/⏎），铺满无空白；滑键上滑一位松手直出「～」。
- 剪贴板面板 ✅：双选项卡、卡片 3 行截断、标签行（dp/fx/jqb/lua/emoji/abc/xime 英文词 + 2481036245 电话号）、点卡片/标签上屏。
- 符号网格 ✅：「←」固定左上角、分类标签横滑、无底部 ABC/空格/退格行。
- 崩溃检查 ✅：logcat 无 Oime FATAL（仅 uiautomator 自身注册冲突）。
- 验证后已恢复默认输入法为 xime.az。


## 反馈轮 6（0.8.0-oime vc6）：11 条
| # | 需求 | 实现 |
|---|------|------|
| 1 | 九宫格滑键改滑动+点击上屏；返回键改「返回」；中英键改符号面板 | NumpadSliderKey 滑动仅预览（松手保留选中），点击键面上屏当前符号并回中；第4行首键 label「返回」；右列「中/EN」→「符」（打开符号网格） |
| 2 | 主键盘长按 popup 改滑动选择-松手上屏 | 长按气泡弹出后横向滑动切换符号（40dp/符号，高亮+震动），松手上屏选中项；单符号行为不变（松手直出）；气泡点选保留 |
| 3 | 长按触发 180ms | KeyboardKey 长按定时 400→180ms |
| 4 | 26键 G 对齐 V 失效 + 第四行右侧空白 + 剪贴板覆盖工具栏 | 根因：编辑器保存的旧同名自定义布局遮蔽内置（旧 JSON 无 spacer 行）。KeyboardLayout 加 rev 字段（内置 rev=2），加载时旧版同名自定义自动删除；第四行 123/⏎ 加宽到 2 填满整行；剪贴板条改为覆盖整条工具栏 |
| 5 | 字体多选（参考 xime.az）+ 入口移主题下层 | FontManager 重写为 xime AppFonts 方式：selected_fonts 多选列表，FontFamily(fonts) 回退链（选择顺序=缺字形回退顺序），缓存签名；FontManagerActivity 改多选卡片 UI；入口移入「主题与配色」页下层，删除独立「外观」页 |
| 6 | 主题与配色参考小企鹅重做 | ThemeCard 迷你键盘预览卡片网格（2 列：工具栏+三行键+强调色回车，按当前色彩模式即时渲染），点卡片应用；自定义 RGB 保留 |
| 7 | 剪贴板返回键左上角 | ClipboardPanel 顶行「←」固定左上，双选项卡跟随其后，删右侧「收起▲」 |
| 8 | 摇杆与○合一居中 + 工具先左后右 + 定制工具栏同高 | 红摇杆删除，○ 键居中：点击菜单/长按定制/横向拖动移光标（原摇杆功能）；指针模式切换移入○菜单磁贴；工具列表前半左、后半右；ToolbarCustomizePanel 加 totalHeight 参数与主键盘等高 |
| 9 | 输入码与候选上下排布（参考 xime） | 组合行改 Column：上行 preedit 11sp 灰色 + 下行候选横滚；◀▶▾ 右侧竖排区域 |
| 10 | 次选/三选预设键（trime2 composing=select_2/3） | 组合中主键盘 123 键变「三选」（Candidate(2)）、句号键变「次选」（Candidate(1)），强调色底显示 |
| 11 | 记录+推送 | 本条 |

- 版本 0.8.0-oime（versionCode 6）；括号配平状态机扫描 7 文件全 0。
- 教训补充：内置键盘布局升级结构时必须递增 rev——编辑器保存的同名自定义 JSON 会遮蔽内置布局（本轮 G/V 对齐失效即此因）。


## 反馈轮 7（0.8.1-oime vc7）：10 条 + 追加 2 条
| # | 需求 | 实现 |
|---|------|------|
| 1 | 工具栏输入码+候选显示不全 | 组合行 fixed 38dp → heightIn(min=38dp) 自动增高；上行输入码 12sp + 下行候选 18sp 完整两行，不再裁剪 |
| 2 | 关闭键盘按钮放最后 | KeyAction.HideKeyboard（requestHideSelf）；工具栏最右固定「⌄」 |
| 3 | 增高行开关默认关闭 | barEnabled 默认 true→false |
| 4 | 键盘背景沉浸系统圆角（尝试） | 键盘根容器顶部 18dp 圆角 clip + IME 窗口背景透明（圆角下透出应用内容，仿系统底部弹层） |
| 5 | 第四行首键减宽 | 123/ABC 键 2.0 → 1.7（稍宽于 shift 1.5），空格 4.0 → 4.3 补位；布局 rev=3（旧 rev 自定义自动失效） |
| 6 | 九宫格滑键改回滑动-松手上屏 | NumpadSliderKey 恢复轮5交互：滑动选择、松手 DirectCommit 并回中 |
| 7 | numpad 编辑器同步 + 滑键符号自定义 | 编辑器 numpad 页新增「滑键符号（空格分隔）」输入框（示例"！ @ 。 、 ？"），存 prefs（numpad_slider_symbols），留空=内置默认；NumpadSliderKey 改读自定义带 |
| 8 | 前三候选映射预设键 | 组合中：空格键=候选1、句号键=候选2、123键=候选3，键面显示实际候选文本点击上屏（无对应候选退回原动作；替代轮6的「次选/三选」文字标签） |
| 9 | 部署键没生效/切换方案打不出字 | 根因两处：①导入只平面拷贝 yaml/txt，方案包内 lua/、opencc/、models/ 子目录全部丢失→候选翻译链挂掉（真机 logcat 证实：exe_processor/LuaTranslation 报 nil，虎单整 8+ 组件缺失）；②Deploy 键导入无变化时不触发引擎维护。修复：导入保留相对路径结构+全扩展名；Deploy 强制 startMaintenance(true)+重建会话（Lua/opencc/模型重新加载）；SelectSchema 部署中失败给「引擎部署中，请稍后重试」提示 |
| A | 剪贴板条上屏后不消亡 | lastCommittedClip 记录已上屏文本，readClipboard 读到同文本不再弹条（复制新内容才重现，xime 式） |
| B | 空格键自定义显示文本 | space_label 偏好：自定义 > 当前方案短名 > 默认；设置→键盘新增输入框；顺带修掉此前显示「○输入法 · pinyin_simp」全串被截断的问题 |

- 版本 0.8.1-oime（versionCode 7）；括号配平状态机扫描 7 文件全 0。
- 真机排查记录：0.8.0 上候选为空为用户导入虎单整时 lua/opencc/models 被平面导入丢失所致（非 UI 回归）；已手工向设备 shared/ 补齐 14 个 lua + rime.lua + opencc(32) + models(224MB)，装上本版后在○菜单按「部署」触发全量维护即可生效。
- 注意：空格键=候选1、句号键=候选2、123键=候选3 仅在主键盘组合中生效；九宫格页滑键交互为滑动-松手上屏。


## 反馈轮 7 真机验证 + 修复版（0.8.2-oime vc8）
| # | 项 | 真机结果 |
|---|-----|---------|
| 1 | 组合行两行自动增高 | ✅ preedit 行 + 候选行完整显示（pinyin_simp / 虎单整均验证） |
| 2 | 工具栏 ⌄ 收起 | ✅ 最右 ⌄ 点击 requestHideSelf 生效；注意剪贴板条覆盖工具栏时无 ⌄（设计取舍） |
| 3 | 增高行默认关 | ✅ 默认紧凑高度，设置页开关在 |
| 4 | 键盘顶部圆角沉浸 | ✅ 18dp 圆角可见 |
| 5 | 123 键减宽 | ✅ 1.7 宽 + 空格补位 |
| 6 | 滑键滑动-松手上屏 | ✅ 默认 15 符号带（、。，！？：；～·…—（）《》），松手上屏选中符号并回中（水平轻划=选中位） |
| 7 | 滑键符号自定义 | ✅ 编辑器 numpad 页输入框生效（实测 A B C 带写入后滑动上屏对应符号） |
| 8 | 前三候选映射 | ✅ 空格=候选1、句号=候选2、123=候选3 键面显示候选文本；仅 1 个候选时回退原动作 |
| 9 | ○菜单部署 | ✅ 部署→切虎单整→候选恢复正常（"ed gk"→「窝去」），轮6导入丢文件问题的运行时闭环打通 |
| A | 剪贴板条消亡 | ✅ 点击上屏后立即消失，键盘重开不复活（lastCommittedClip） |
| B | 空格自定义文本 | ✅ 设 AZ 即显示 AZ，清空恢复方案名 |

### 真机发现的新 bug（本轮修复）
- **编辑器可把 symbols/numpad 激活为主键盘**：内置列表行点击即 setActiveMain(name)，误点 numpad 行（或保存同名副本后激活）会把主键盘指针劫持到专用页布局——主键盘页走通用分支渲染出无滑键的 4 列数字网格，qwerty 被顶掉。修复：①KeyboardManager.ReservedPageNames（symbols/numpad/emoji），setActiveMainLocked 拒绝 + initialize 读回防御；②编辑器内置/自定义列表对保留名禁用激活、副标题标注「专用页布局/不参与主键盘」。
- 版本 0.8.2-oime（versionCode 8）。


## 反馈轮 8（0.8.3-oime vc9）

| # | 反馈 | 处理 |
|---|------|------|
| 1 | 键盘背景色与系统底部增高（导航条）没有沉浸 | IME 窗口 navigationBarColor 涂键盘背景色（深浅色感知 0xFFE9EBEE / 0xFF1B1D1F），关闭系统对比度压暗 isNavigationBarContrastEnforced=false；onStartInputView 每次弹键刷新（主题切换生效） |
| 2 | 工具栏输入时两行增高突兀 | 组合行改单行内联：输入码在前 + 候选横滚同排 + 翻页/更多候选箭头，高度不变（参考 xime CandidateBar） |
| 3 | 前三候选映射键背景变色 | 映射键保持原键背景/字色，仅键面文本换成候选词 |
| 4 | switches 开关进○菜单 + 移除页面项 + 子面板同风格 | ①新大项「方案开关」：子级面板内切换方案 + 当前方案 schema.yaml switches 段开关（RimeManager.schemaSwitches 解析 name/states，跳过 options 型；引擎 getOption/setOption 实时切换）②○菜单移除 26键/数字/符号/表情 ③「输入方案」「定制工具栏」改为面板内子级页（MenuSubPanel：← 返回 + 标题 + ↑ 关闭，与一级菜单同区域同风格），不再跳独立界面 |
| 5 | 九宫格「符」改「符号」 | NumpadPane 右列键标签改为「符号」 |
| 6 | popup 每行 5 个 + 翻页 + 字号调小 | 长按气泡改纵向 5 个/页网格（16sp），滑动跨页自动翻页，底部 ‹ x/y › 指示器可点击翻页 |
| 7 | 剪贴板条点任意键消亡 | onKeyAction 入口统一处理：剪贴板条显示时任一按键动作先清 clipText（点条本身上屏除外） |
| 8 | 长按太容易触发；回车键微信无法发送 | ①对齐 xime.az KeyButton：长按触发前移动超 5dp 取消定时器（longCancelled），180ms 阈值不变 ②handleEnter 重写（对齐 xime.az ImeKeyRouter）：composing 交给 RIME；无编码时 imeOptions 声明 GO/SEARCH/SEND/NEXT/DONE 则 performEditorAction（微信可回车发送），否则 sendDownUpKeyEvents(KEYCODE_ENTER) 换行 |
| 9 | Z键符号 ` 无法触发反查 | 对齐 xime.az：中文模式单字符（ASCII）DirectCommit 先 RimeManager.processKey(charCode)（recognizer 反查引导符可识别），引擎未消费再直出上屏 |

技术记录：
- RimeManager 新增 getOption/setOption/schemaSwitches(schemaId)（解析 shared/<id>.schema.yaml switches 段）+ SchemaSwitch data class
- KeyAction 新增 ToggleSwitch(name)；AZimeService currentEditorInfo 字段
- 参考源码：xime.az（github AZNixl/Xime.az）KeyButton.kt 长按 5dp 取消 / ImeKeyRouter.kt 回车与反查


## 反馈轮 9（0.9.0-oime vc10）

| # | 反馈 | 处理 |
|---|------|------|
| 1 | popup 改横向 | 长按气泡改横向网格：一行 5 个，多出的排第二行（去除纵向分页指示器） |
| 2 | ○菜单子级界面排布 | 方案开关子级（切方案 + schema.yaml switches 开关）、输入方案、定制工具栏全部改为一级菜单同 chrome（← 标题 ↑）+ 同款卡片排布（keyBg 圆角卡） |
| 3 | 工具栏样式 | 背景色跟随主键盘背景（c.bg）；去除圆角（键盘整体平直，不再顶部 18dp 圆角裁剪）；高度 38→46dp（+1/5）；组合行恢复上下排布（上=输入码小字 12sp，下=候选横滚 + 翻页/更多） |
| 4 | 九宫格数字排布 | 数字列存改为 1,4,7 / 2,5,8 / 3,6,9（视觉上横向 123/456/789）；第 4 行改 返回/= 0 ./⏎（0 左 = 号、右英文句号，去掉逗号键） |
| 5 | 长按符号位置 | 气泡向右、向上各移 3dp（offset 0,-58 → 3,-61） |
| 6 | 空格文本框不能输空格 | 根因：Space 无条件走 RimeManager.processKey(KEY_SPACE)，中文模式无编码时 librime 吞掉空格。修复（对齐 xime.az）：preedit 与候选均空时直接 commitText(" ")；有编码时仍走引擎顶屏 |
| 7 | 编辑器 numpad 对齐 | KeyboardPages.numpad 数据改为 5 列×4 行（滑键占位列 + 数字 + 功能列，第 4 行含 = 0 .），rev=2→3（旧自定义副本自动清理），编辑器预览与实际 NumpadPane 渲染一致 |
| 8 | 字号设置 | 键盘键面字号（12-30sp，默认 20）与工具栏/候选字号（12-28sp，默认 18）分开滑杆；键面字号按比例缩放功能键（0.7x） |
| 9 | 按键外观设置 | 按键圆角（0-20dp，默认 8）/ 行距（1-10dp）/ 列距（1-10dp）三条滑杆；NumpadPane 与滑键同步 |
| 10 | 悬浮窗 | 新增「悬浮窗」设置大项（默认关）：输入时在键盘上方 Popup 悬浮显示输入码（参考 trime 悬浮窗/悬浮窗显示优化.lua）；默认模式固定样式（16dp,100dp,22sp,92% 透明度），自定义模式 X/Y 位置、字号、背景不透明度四条滑杆 |
| 11 | 面板底部悬浮栏 | 剪贴板面板与 emoji/符号网格的返回键+选项卡/分类标签移到底部悬浮胶囊栏（参考 PiliPlus）；内容区底部留白 56dp；emoji 分类多时底栏横向滑动 |
| 12 | 设置界面 | KSU 布局：左侧大状态方块 + 右侧两个小方块（版本/项目）上下排；滑条改 xime 风格 XimeSlider（标题左+值右+滑杆）；enableEdgeToEdge() 状态栏沉浸 |
| 13 | 主题自定义卡片 | 樱粉后加「自定义」卡片（当前为非预设色时自动选中态），点击才展开 RGB 调整区 |
| 14 | 字体热加载 | FontManager 增加版本号 rev()（setSelectedFonts/invalidate 自增），纳入 KeyboardManager.sizeSignature()——字体选择变化后回键盘即重建视图加载新字体 |
| 15 | ○指针灵敏度 | 指针模式拖动步长 18dp→30dp（灵敏度降低约 40%）；光标模式不变 |
| 16 | 退格滑动删除降敏 | 选择步长 24px→30px（每字需滑更远，降敏 1/5）；进入阈值 10→12px |
| 17 | 语音输入调研 | 仅报告（见下） |

### 第 17 条调研结论：小企鹅（fcitx5-android）语音输入
- **原版不内置语音模型**：语音按钮只是「切到系统语音输入法」的快捷键（PR #251，imm.setInputMethod 切 Google 语音输入或 Sayboard 等第三方，输入完切回）。
- 作者的 SpeechRecognizer 直连方案（PR #899，调系统 android.speech.SpeechRecognizer）至今仍是 WIP 未合并。
- 三条可选路线：A 切换 IME（~1 天，零权限零模型，体验割裂）；B 系统 SpeechRecognizer（~3-5 天，需 RECORD_AUDIO，依赖设备语音引擎）；C 内置离线模型 sherpa-onnx/Vosk（1-2 周，40-80MB 中文流式模型）。待用户决策。

技术记录：
- KeyboardManager 新增 fontSizeKey/fontSizeBar/keyCornerDp/rowGapDp/colGapDp/float* 系列 prefs，全部纳入 sizeSignature
- AZimeService handleSpace 分流逻辑；AZimeService currentEditorInfo 沿用轮 8
- 版本 0.9.0-oime（versionCode 10）

## 反馈轮 10（0.9.1-oime vc11）：12 条

| # | 需求 | 实现 |
|---|------|------|
| 1 | 删⌄收起键 / O键下滑关键盘 / 工具居中 | 工具栏 ⌄ 关闭键删除，○ 菜单键下滑（>40dp 且纵向占优）触发 HideKeyboard；左右工具组在各自剩余空间内居中排列 |
| 2 | ○菜单去指针模式 / 子级横向 / 悬浮半透明 | 菜单删除「光标/指针模式」项；方案开关子级只留功能开关（去方案选择+当前方案显示）；输入方案、定制工具栏子级改 4 列横向卡片网格；父/子级圆形功能键 funcKeyBg alpha 0.55 半透明悬浮样式；底部悬浮栏 alpha 0.8 |
| 3 | 打字时工具栏不加高 | composing 分支 heightIn(min=barHeight+10) → 固定 height(barHeight)，preedit 11sp/13sp 行高 + 候选 16sp 压缩塞入，打字全程 46dp 恒定 |
| 4 | 设置布局/部署/关于子级/BackHandler/未启用灰态 | 右侧两小方块 IntrinsicSize.Min 等高 + weight 均分 + 图标 20→16dp；「项目」→「部署」（CloudUpload 图标）；备份设置移入关于页；SettingsScreen 加 BackHandler(subPage!="main")；StatusCard 检测 Settings.Secure.DEFAULT_INPUT_METHOD，未启用时整体灰色 +「未启用 · 点击去启用」+ 跳转系统启用页 |
| 5 | 滑条改 xime 样式 | XimeSlider 重写为自绘（pointerInput tap+horizontal drag）：深色圆角轨道(#232527, R5) + 强调色填充段 + 白色竖线 thumb(8x22dp 描边)；不依赖 material3 Slider thumb/track slot API（BOM 2024.02 兼容性风险规避） |
| 6 | 内置配色切换选中框不实时刷新 | 选中态依赖 km.accentLight() 直接读取不触发重组；改 remember(accentRev){km.accentLight()/accentDark()}，应用配色后 accentRev++ 使预设卡与自定义卡选中态实时刷新 |
| 7 | 空格键显示文本不识别空格 | 根因：Space 直出条件要求 candidates.isEmpty()，部分方案空编码带常驻候选 → 空格送 librime 被吞。AZimeService KeyAction.Space 条件放宽为仅 preedit.isEmpty() 即直出空格 |
| 8 | 按键响应时间进设置 | KeyboardManager 新增 longPressMs(默认180)/repeatStartMs(150)/repeatIntervalMs(45)/swipeThresholdDp(30) 四 prefs；KeyboardKey 长按定时/连发/滑动阈值全部接入；键盘页新增「按键响应」卡片 4 滑杆 |
| 9 | 符号提示相对位置 | 长按气泡 offset 由固定 -61dp 改为 -(键高+15dp)，键高 36-64dp 变化时气泡始终悬浮在按键上方不与字母重叠 |
| 10 | 悬浮栏同心圆角 | 剪贴板/分类网格底部悬浮栏外层 R22 + 内层选项卡 R9→R18（同心：22-4padding=18，内方外圆） |
| 11 | 图标 material 化 / O键圆环动画 | toolbarToolItem 的 📋/方案/123/☺/符/⚙ 全部替换 Material 图标（Assignment/List/Dialpad/EmojiEmotions/Category/Settings）；○ 键改为 Canvas 圆环造型：底环 + 按下旋转弧（InfiniteTransition 1.8s 旋转，等价 lottie）+ 拖动时强调色内点跟随手指（限幅圆环半径内，满足「移动距离不超过圆环中心点」） |
| 12 | 增高行支持九宫格 | NumpadPane 末尾接入 barEnabled + barHeightDp 增高行，主键盘/九宫格切换高度一致 |

技术记录：
- ToolbarRow ○ 键 pointerInput(Unit)：下滑收起 (dy>40dp && |dy|>|dx|)、拖动移光标 18dp/步、ringKnob Offset 限幅 off*(capR/r)
- ResponseTimingSettings 四滑杆（100-800/50-500/20-200/10-80），下次键盘弹出生效
- 遗留：joystickMode/SetJoystickMode 字段保留未删（兼容），MyLocation/Checkbox/heightIn import 未清理
- 版本 0.9.1-oime（versionCode 11）

## vc11 构建与装机记录
- commit 608c8e0 首次 CI 失败：KeyboardScreen.kt `size.minDimension` 不存在（IntSize 只有 width/height，minDimension 属于浮点 Size），capR 类型污染连带 `r > capR` compareTo 歧义。修复 `minOf(size.width, size.height)` → commit 0c8a7f3c，run 34109422790 ✅ success。
- APK 已取回：app-debug.apk 26.9MB（artifact app-debug #10013919428）。
- 构建完成后首次连接手机失败（adb devices 为空，2026-09-07 18:13），按指示记录后停止，待用户指令再装机验证。

## 反馈轮 11（0.9.2-oime vc12）：6 条

| # | 需求 | 实现 |
|---|------|------|
| 1 | 工具图标调大 / 工具栏按 O 键居中 | toolbarToolItem 图标 16→24dp（tint c.text）；ToolbarRow barHeight 46→44dp（O 键 36dp + 上下 4dp），左右工具组在剩余空间居中，与 O 键视觉居中 |
| 2 | 菜单功能键/标题改悬浮栏；子级横向；方案名 | 主菜单顶行合并悬浮栏（↑ + 「○ 菜单」标题 + ⚙，R22 胶囊 funcKeyBg alpha 0.55）；MenuSubPanel 顶部改居中悬浮栏（← 标题 ↑）；switches 子级改 2 列卡片网格（开 → accentKeyBg + accentActive 状态字，点按整卡切换，去 material3.Switch）；schema 子级 4 列卡片改 RimeManager.schemaDisplayName（读 shared/&lt;id&gt;.schema.yaml 的 name 字段，超长 Ellipsis），不再截 6 字符；工具栏方案快捷菜单同步改方案名；死函数 schemaDisplay() 删除 |
| 3 | emoji 手势分类 | EmojiData 新增 👍 手势分类 40 个（👍👎👌✌🤞🤟🤘🤙…🫶🫰🫵🫱🫲等） |
| 4 | 空格不识别（第三轮根因） | 照抄 xime.az ImeKeyRouter "space"：以引擎实时组词状态为准——非组词（getProcessResult().inputText 为空）一律 commitText(" ") 直出，组词走 processKey(KEY_SPACE) 交引擎选首选；不再依赖 UI preedit 残留态。配套 setSpaceLabel 去 .trim()（空格是合法标签字符），显示条件改 isNotEmpty |
| 5 | 四向/长按符号位置进设置 | KeyboardManager 新增 bubbleXDp(3, 0-24)/bubbleYExtraDp(15, 5-40)（进 sizeSignature）+ swipePreviewAbove(false)；KeyboardKey 长按气泡 offset 接 prefs；swipePreviewAbove=true 时四向预览改键上方 Popup 气泡（13sp barBg R8），false 时键面中央原样显示；设置页键盘组新增「手势提示位置」卡片（2 滑杆 + 1 开关） |
| 6 | O 圆环加粗明显 + 圆环本体动画 | 底环改虚线圆环 PathEffect.dashPathEffect(6dp/4dp) + Stroke 2.5dp + alpha 0.8；动画改整环 rotate（InfiniteTransition 3600ms 匀速 LinearEasing）——虚线环本体旋转，不再是附加弧线动画；按下 accentActive 高亮，拖动强调色内点跟随手指不变 |

技术记录：
- KeyboardScreen 净删 schemaDisplay 死函数与 material3.Switch import
- GesturePositionSettings：长按气泡水平偏移 0-24dp / 垂直余量 5-40dp / 四向预览键上方开关，下次键盘弹出即生效
- 版本 0.9.2-oime（versionCode 12）

## vc12 构建记录
- commit 44ebdd82（parent fb1cd17，67 文件），CI run 34117992551 ✅ success。
- APK 已取回：app-debug.apk 26.9MB（artifact app-debug #10017190209），工作区副本 oime-0.9.2-vc12.apk。
- 构建完成后手机未连接（adb devices 为空，2026-09-07 19:56），按指示记录后停止，待用户指令再装机验证。

## 反馈轮 12（0.9.3-oime vc13）：4+1 条

| # | 需求 | 实现 |
|---|------|------|
| 7 | ○ 菜单加主题亮暗切换键 | KeyAction.ToggleThemeMode：跟随系统时按当前实际状态取反（sysDark→LIGHT，否则 DARK）；themeRev++ 强制 uiState 变化整键盘立即重组换色；sizeSignature 纳入 KeyboardTheme.mode()（下次弹出兜底重建）；主菜单第 6 项（4+2 两行网格），图标/标签显示切换目标（暗色态显示「亮色」+ LightMode） |
| 8 | 两套键盘图标供选，两处应用 | 四套 SVG 设计稿（A 细线 / B 圆面 / C 双色 / D 粗线）供选，**用户定稿 A · 细线**：①工具栏六图标改自绘 ImageVector（PathParser 解析 24 网格 path，stroke 1.8 圆头，tint 随主题变色）替换 Material 图标；②桌面启动图标同风格重绘——adaptive foreground 改「细线 ○ 环 + 3x3 空心点阵 + 底中横线」（#2C2C2A on #F1EFE8 暖浅灰），legacy PNG（48-192px 五密度，PIL 432px 超采样生成）同步替换 |
| 9 | 设置主页排版修正 | 大方块「○输入法」titleLarge→17sp + 状态行 bodyMedium→12sp，均 maxLines=1 + Ellipsis（窄方块不换行）；右上版本块「版本」并到 (i) 图标同行（两行结构）；右下块去掉「部署」字样与 CloudUpload，改「项目」卡（Language 图标 + AZNixl/Oime，排版同版本块，点击开 GitHub 保留） |
| 10 | 构建后记录 / 无连接即停 | 按工作流执行（见下方构建记录） |
| 11 | 复制内容显示到工具栏，打字不能消亡 | 根因：onKeyAction 对任意按键清除 clipText + updateFromResult 组词时清空。两处移除——复制条仅在「剪贴板面板上屏（CommitClipboard）」或「新复制覆盖」时更新，打字/组词不再消亡 |

技术记录：
- KeyboardUiState 新增 themeRev（toggle 后强制重组换色；toolbarRev 同款模式）
- ToolbarOutlineIcons：ImageVector.Builder + PathParser().parsePathString(d).toNodes()，fill/stroke 双模式 parts；工具栏细线图标不可 tint 双色（方案 C 弃选原因之一）
- 启动图标 legacy PNG：gen_launcher_icons.py（432px 超采样 LANCZOS 缩 5 密度）；空心点参数 r2.4/stroke1.5（r2/stroke2 会内孔填满变实心）
- 版本 0.9.3-oime（versionCode 13）

## vc13 构建记录
- commit ee95753f（parent 95395fc9，67 文件），CI run 34121280492 ✅ success，一次通过。
- APK 已取回：app-debug.apk 27.0MB（artifact app-debug #10018470161），工作区副本 oime-0.9.3-vc13.apk。
- 构建完成后手机未连接（adb devices 为空，2026-09-07 20:29 两次确认），按指示记录后停止，待用户指令再装机验证。

## 反馈轮 13（0.9.4-oime vc14）：方案组架构重做

| # | 需求 | 实现 |
|---|------|------|
| 1 | 排查「方案自己变动成别的方案」根因 | **根因确认**：旧 deployPendingImport 把所有导入方案的 schema_id 全量平铺进 default.custom.yaml（pinyin_simp 恒第一），librime 每次部署完成后激活方案回落 schema_list[0]，用户切过的方案被跳回拼音 |
| 2 | 方案管理重做：方案组→方案 两级（参考 trime2） | RimeManager 新增方案组区块：SchemaGroup(id/name/builtin/schemaIds)、schemaGroups()（内置组 + Documents/Oime/schema/ 子目录=方案组，组内扫 *.schema.yaml）、currentGroupId/setCurrentGroup/recordGroupSchema（schema_group_prefs）；syncGroup 共用启动/切组——删上一组文件（.imported 清单，与内置资产同名从 assets 恢复）→ 拷入组文件（组覆盖层）→ 重写 default.custom.yaml（schema_list 仅含组内方案，组内上次使用置首）→ 不变化跳过；switchSchemaGroup=setCurrentGroup→syncGroup→全量维护→重建会话；删 deployPendingImport；ensureReady/deployImportedSchemas 接线 |
| 3 | O 菜单加「方案组」大项 + 设置页同步 | KeyAction.SelectSchemaGroup + Service 处理（statusMessage「正在切换方案组…」）；SelectSchema 成功后 recordGroupSchema（组内记忆）；主菜单第 3 项「方案组」（Apps 图标）+ groups 子级 4 列卡片网格（组名 + 「N 个方案」副文本，当前组 accent 高亮，state.schemas 作刷新 key）；设置页 SchemaList 顶部方案组单选区（RadioButton + 方案数），组内方案列表保留并同样记录；导入文案改「已导入方案组，可在方案组中切换」（不自动切换，对齐 trime2 安装语义）；promptRename 重命名当前组时同步 setCurrentGroup |

技术记录：
- 修复机制：schema_list 只写当前组 + recordGroupSchema 把组内最后使用的方案置首 → 部署后 librime 回落 schema_list[0] 即回到用户方案，不再跳回
- 旧版平滑迁移：升级后首次启动 currentGroup=内置组，syncGroup 按旧 .imported 清单删除全部导入文件（同名内置资产从 assets 恢复），源文件仍留在 Documents/Oime/schema/<组名>/ 不丢
- syncAssets 顺序保持在前（组文件未动时 marker 命中即跳过，不覆盖组定制同名文件）
- 版本 0.9.4-oime（versionCode 14）
| 4 | 按键按下没有动画，做按下的动画反馈 | KeyboardKey 统一按下动画：有手势键复用 pressing（awaitEachGesture down/up），无手势键新增 MutableInteractionSource + collectIsPressedAsState；animateFloatAsState（spring NoBouncy StiffnessHigh）驱动 ①背景渐变 lerp(bg, 白/黑 12% compositeOver(bg))——暗色键盘按下变亮、亮色键盘按下变暗（c.barBg.luminance() 判定）②graphicsLayer 缩放 1→0.95（lambda 内 deferred read，不触发重组）；主键盘/符号/九宫格共用 KeyboardKey，一处改动全键盘生效；clickable 分支 indication=null，以自绘渐变替代 ripple |

技术记录（续）：
- 踩坑①：walkTopDown() 链式结果是 Sequence，无 isNotEmpty()、不能直接传 List 参数——需 .toList()（CI 首败）
- 踩坑②：compose Spring 常量没有 StiffnessMediumHigh（只有 High/Medium/MediumLow/Low/VeryLow）——按键反馈用 StiffnessHigh（CI 二败）
- 版本 0.9.4-oime（versionCode 14）

## vc14 构建与装机记录
- commit c156590（parent 049fa9e，67 文件）首推 CI 失败：RimeManager.kt:287 walkTopDown Sequence 未 toList。
- commit d6baf1f（按下动画 + 修复）二推 CI 失败：Spring.StiffnessMediumHigh Unresolved。
- commit f5b402e（StiffnessHigh 修复）run 34128229025 ✅ success。
- APK 已取回：app-debug.apk 26.4MB（artifact #10021193864），工作区副本 oime-0.9.4-vc14.apk。
- 手机 b72e0041 在线，Streamed Install Success，dumpsys 确认 versionCode=14 / 0.9.4-oime。
- 按用户指令：记录上传后停止工作，等待下一步指令。

## 反馈轮 14（0.9.5-oime vc15）：3 条

| # | 需求 | 实现 |
|---|------|------|
| 1 | 加载方案组后组内方案未被识别 | **logcat+run-as 现场定位两个根因**：①librime 只在 shared 根目录解析 `<id>.schema.yaml`，聚合组（组内嵌套方案包子目录，如 AZ 组内嵌 rime-frost 完整包、build/ 预编译）保留结构拷贝后嵌套包的 schema 引擎全部找不到 → schema_list 里的 id 无法部署；②syncGroup 的 ids 漏 distinct，schema_list 出现重复条目（设备上 33 条实况确认）。修复：syncGroup 拷贝规则改为 **yaml/txt 一律拍平到 shared 根**（同名后者覆盖），lua/opencc/models/build 等资源保留子目录结构；组内自带 default.custom.yaml 跳过（schema_list 由 Oime 生成）；ids 加 distinct |
| 2 | O 菜单所有悬浮栏移到底部（对齐剪贴板） | MenuSubPanel 改 Box 布局：滚动内容（底部 Spacer 56dp 避让）+ BottomCenter 悬浮栏（alpha 0.55→0.8 + bottom 8dp，同剪贴板参数）；主菜单顶行「↑ ○ 菜单 ⚙」同样移到底部居中，菜单网格顶部起排 |
| 3 | O 键白色圆环、不要一直转圈 | 去掉 InfiniteTransition 3600ms 旋转 + 虚线（dashPathEffect），改**静态白色实线圆环**（Color.White，stroke 2.5dp）；按下 accentActive 高亮、拖动内点跟随限幅行为保留；清理 LinearEasing/animateFloat/infiniteRepeatable/rememberInfiniteTransition/tween 五个失效 import |

技术记录：
- 调试手段：logcat 无应用日志（unchanged 静默路径 + 缓冲被冲）→ `run-as com.oime.input cat files/rime/shared/default.custom.yaml` 直接看部署产物定位（比日志快）
- librime 资源解析规则：schema/dict/custom yaml 与 txt 词典只查 shared_data_dir 根；lua/opencc/models 子目录资源与 build/（预编译产物）支持子目录
- ime set 需完整类名：com.oime.input/com.azime.input.ime.AZimeService（applicationId 与 namespace 不同，`.短类名` 展开会失败）
- 版本 0.9.5-oime（versionCode 15）

## vc15 构建与装机记录
- commit 4037803（parent 8544d48，67 文件），CI run 34131155961 ✅ success，一次通过。
- APK 已取回：app-debug.apk 26.4MB（artifact），工作区副本 oime-0.9.5-vc15.apk。
- 手机 b72e0041 装机 Success，dumpsys 确认 0.9.5-oime。
- **修复实机验证通过**：切回 AZ 组重新部署后，default.custom.yaml 22 个唯一 id 无重复；shared 根 23 个 .schema.yaml（22 组内 + pinyin_simp 内置）全部拍平到位；build/ 编译产物 41 个（core2022/double_pinyin 全家/easy_english/japanese/rime_frost/tiger 等 prism+table+reverse）——librime 已完整部署组内全部方案。
- 按工作流：记录上传后停止工作，等待下一步指令。

## 反馈轮 15（0.9.6-oime vc16）：2 条

| # | 需求 | 实现 |
|---|------|------|
| 1 | 方案不能正确启用 + 参考同文/trime2 在方案组-方案之间插「方案选择」层；O 菜单加方案管理大项；设置同步 | 现场排查（run-as）：部署产物正常（41 编译产物全），判定痛点=组内 22 个方案全量自动启用（列表混乱+全量部署慢+选中方案被淹没）。**三层架构**：方案组 → 启用集（新增）→ 输入方案切换。RimeManager：group_enabled_&lt;gid&gt; prefs（未设置=全部启用，兼容迁移）、setGroupEnabled（空=恢复全部）、syncGroup schema_list 只写启用集。UI：O 菜单第 4 项「方案管理」（PlaylistAddCheck）+ manage 子级（2 列勾选卡片 + 底部「应用」按钮 → ApplySchemaEnable → setGroupEnabled+重入当前组重部署）；「输入方案」列表（availableSchemas）自动变为启用集；设置页 SchemaList 新增「方案管理（启用集）」区（Checkbox + 应用按钮，同套协程刷新） |
| 2 | O 圆环缩小 1/5、加粗 1/4、呼吸动画（不刺眼）；O 长按改语音输入（声纹动画覆盖工具栏，点击结束）；系统接口 + 设置大项（本地模型/联网 API 占位） | ①圆环 36→29dp、2.5→3.1dp、InfiniteTransition alpha 0.55↔1.0（1600ms Reverse + FastOutSlowIn）白色呼吸；按下 accentActive、拖动内点保留。②长按 ○ 400ms → ToggleVoiceInput（原定制工具栏入口保留在 ○ 菜单，AzimeKeyboardScreen 死分支清理）；SpeechInputManager（新文件 core/speech）封装 SpeechRecognizer：zh-CN、MAX_RESULTS=1、onRmsChanged 直通回调、onResults 首选上屏、错误映射中文提示、isAvailable 检测（国产 ROM 缺服务时提示）；声纹 VoiceWavePanel 覆盖整条工具栏（28 根圆头条形，钟形包络×相位正弦×RMS 振幅，底部「正在听写…点击结束」，点击 stopListening）；RMS 走独立 mutableStateOf（Service voiceRmsState）不经 uiState 重组链；RECORD_AUDIO 权限：Manifest 声明 + IME 无法弹窗 → 无权限时提示并跳设置页授权；收起键盘/销毁服务时 cancel。③设置新增「语音输入」卡：麦克风权限行（rememberLauncherForActivityResult 申请）+ 识别方式 RadioButton（系统可用 / 本地模型占位 / 联网 API 占位，voice_prefs.mode） |

技术记录：
- 本轮起 AzimeKeyboardScreen 签名带 voiceRms: State&lt;Float&gt;（Service 传 voiceRmsState）；KeyboardUiState 新增 voiceState（idle|listening）
- ToolbarRow 签名：onOpenCustomize 参数移除（长按改语音），定制工具栏入口仅存 O 菜单
- 语音扩展位：SpeechInputManager 单实现，后续本地模型/API 替换 start() 内部即可
- 版本 0.9.6-oime（versionCode 16）

## vc16 构建与装机记录
- commit e367900（parent 6fa1803）首推 CI 失败：VoiceWavePanel 插入时原 toolbarToolItem 的 @Composable 注解被夹成孤立重复（"This annotation is not repeatable"），toolbarToolItem 失去注解报 Composable 上下文错误。
- commit b3cc8eb（注解重排修复）run 34171999643 ✅ success。
- APK 已取回：app-debug.apk 26.5MB（artifact），工作区副本 oime-0.9.6-vc16.apk。
- 手机 b72e0041 装机 Success，dumpsys 确认 versionName=0.9.6-oime。
- 按用户指令：记录上传后停止工作，等待下一步指令。

## 反馈轮 16（0.9.7-oime vc17）：5 条

| # | 需求 | 实现 |
|---|------|------|
| 1 | 输入方案页重构：方案组/导入方案/语音输入为父级菜单；选中组后挂「方案管理」子级（第一次启用必须进入）；父级下只显示已选方案；部署提到标题文本后 | TopAppBar title Row：标题旁「部署」文字按钮（schemas 页显示，部署中灰显，移除原独立部署卡）；showManage 子页状态（BackHandler/返回键/标题切「方案管理」）；SchemaList 选中组行后挂「方案管理」入口行（Tune 图标+已启用数+chevron）；「已选方案（点击切换）」只列启用集（enabledIds==null 引导先进方案管理；空集红色提示）；新 SchemaManagePage 全屏子页（Checkbox 列表+全选/清空+Button 应用，setGroupEnabled+switchSchemaGroup）；导入方案/语音输入卡各加父级标题 |
| 2 | 键盘设计器显示模式切换（横向一排/多行，三个按钮） | GridEditorScreen 加 FilterChip 三模式：0 多行（默认，宽度权重还原）/ 1 横向一排（全部按键拼一行 horizontalScroll，EditorKeyCell 固定 64×52dp）/ 2 紧凑（行高 52→30dp、字号 12sp 纵览）；选择持久化 kb_editor_prefs.display_mode |
| 3 | 启动界面加语音权限开启 | SettingsActivity onCreate 检查 RECORD_AUDIO 未授权即弹系统权限框（micPermissionLauncher），与语音输入卡手动入口并存 |
| 4 | O 菜单切方案组失败（修复） | **根因**：startMaintenance 是异步的，切组时旧会话在维护结束前仍存活 → ensureSession() 第 221 行「有会话且有方案」短路直接 return true，会话仍挂在上一组方案上（部署换了、会话没换）。修复 switchSchemaGroup：startMaintenance 后轮询 isMaintaining()（≤180s）等维护真正结束 → ensureSessionNow → **显式 switchSchema(组内首选)**（新增 groupPreferredSchemaId：上次使用∈启用集 → 启用集第一个 → 内置兜底，与 schema_list 置首规则同源） |
| 5 | 键盘设计器入口加到键盘 | KeyAction.OpenKeyboardEditor（data object）+ O 菜单「键盘编辑」（Icons.Default.Edit，第 9 项）+ AZimeService startActivity(KeyboardEditorActivity, NEW_TASK) |

技术记录：
- 实机排查（adb b72e0041）：**该 ROM 无任何系统语音识别服务**（cmd package query-services android.speech.RecognitionService = No services found；RECOGNIZE_SPEECH activity 亦无）→ isRecognitionAvailable=false，长按 ○ 的声纹动画从不出现（voiceState 停留 idle），属系统层缺失非代码 bug；本轮起 handleVoiceToggle 无服务时改 Toast 醒目告知（本地模型/联网 API 后续接入）
- O 键长按/拖动后松手误弹菜单修复：awaitEachGesture 抬起事件在 oLongFired 时 consume()，不再传给后面的 clickable
- 混输方案（虎单整 tiger_danzheng）现场：schema_1788533984946 组内主码=tiger.extended（custom patch）、副=lua_translator@tiger_danzheng_sentence；当前设备 active 组=AZ，虎单整文件未同步进 shared（属预期，等切组修复后实测）
- 版本 0.9.7-oime（versionCode 17）


---

# 反馈轮17（0.9.10-oime vc20）：方案管理架构重构 —— 改用 trime 方法

## 用户实机反馈（vc18 测试）

| 现象 | 截图证据 |
|------|----------|
| 勾选4个方案只显示2个 | 图1：虎码单整混输组勾选4个，已选方案只列2个 |
| O菜单显示另一组方案 | 图2：当前组=虎码单整混输，O菜单显示 tiger_frost/tigress_frost（AZ组） |
| 所有方案打不出字 | librime 维护未完成 + 方案列表是旧缓存 |

## 根因分析

1. **xime 方案组架构与 librime 模型不匹配**：librime 只管理扁平方案列表，方案组是 xime 自己发明的概念
2. **syncGroup 重写 default.custom.yaml**：只写 schema_list，覆盖用户的 patch（switcher/menu/key_binder/混输 custom.yaml）→ 混输挂载失效
3. **switchGroup 后读 availableSchemas()**：librime 维护未完成时返回旧缓存 → 显示错误方案
4. **push_via_api.py 增量推送缺陷**：只上传变更文件，导致 GitHub 仓库不完整 → CI 连续失败

## 重构方案（参考 trime/trime2）

### 1. 删除 xime 方案组代码（约250行）

删除：SchemaGroup、currentGroupId、setCurrentGroup、recordGroupSchema、groupEnabledIds、setGroupEnabled、groupPreferredSchemaId、syncGroup、switchSchemaGroup、deployImportedSchemas

### 2. 实现 trime 风格 API（RimeManager）

| 方法 | 对应 trime | 实现 |
|------|-----------|------|
| getSelectedSchemas() | getSelectedRimeSchemaList() | 解析 default.custom.yaml 的 patch.schema_list |
| setSelectedSchemas(ids) | selectRimeSchemas(Array) | YamlPatcher 只改 schema_list，保留其他 patch |
| deploy() | deploy() | syncAssets + startMaintenance |

### 3. 新增 YamlPatcher 工具类

智能解析 default.custom.yaml：只修改 patch.schema_list，保留用户所有其他 patch 内容（注释/空行/switcher/menu/key_binder 等），避免覆盖混输方案的 custom.yaml 配置。

### 4. UI 简化

- SchemaList：删除方案组选择 UI → 直接显示已启用方案列表
- SchemaManagePage：删除组概念 → 直接管理所有可用方案
- KeyboardScreen：删除 groups 分支（方案组选择），manage 分支改用扁平方案列表
- AZimeService：删除 SelectSchemaGroup KeyAction，ApplySchemaEnable 改用 setSelectedSchemas+deploy

### 5. ensureReady 简化

删除 syncGroup 调用，只保留 syncAssets + startMaintenance。

## 技术说明

trime 正确做法是用 JNI selectRimeSchemas() 设置启用方案，完全不碰 yaml。xime RimeEngine 缺该 JNI 方法（librime_jni.so 预编译无源码），故用 YamlPatcher 模拟 trime 的 Kotlin 层行为。未来补充 JNI（参考 trime 的 C++ 实现）后可无缝切换。

## 关键改进

- 不再覆盖用户的 default.custom.yaml patch → 混输方案 custom.yaml 挂载生效
- 方案列表与 librime 实际状态同步 → 不再是旧缓存
- 代码符合 trime 架构理念（扁平方案管理）

## 参考

- https://github.com/nirenr/trime2
- https://github.com/osfans/trime


---

# 轮18（0.9.12-oime vc22）：彻底转向 trime2 架构 —— 组目录即 librime 数据目录，零拷贝

## 用户最终决策

用户实机对比（weasel PC / trime2 手机均正常，Oime 打不出字）后拍板：
**清除全部 xime 系方案管理代码，改用 trime2 的方案管理方法。**

## 设备实测发现（trime2 fork 真实架构）

检查手机上 `Documents/rime/schemas/<组>/`（方圆 fork 的方案组目录）：
- 每个组目录 = **一个完整独立的 rime 环境**（自带 default.custom.yaml、build/、opencc/、lua/、models/、installation.yaml、user.yaml、userdb）
- 组目录里有 build/、installation.yaml、user.yaml —— 这些是 **librime user 数据目录的特征文件**
- 结论：**trime2 fork 把组目录直接作为 librime 的 user_data_dir**（方案/dict/lua 从 user 目录优先加载，build 产物生成在组目录 build/ 下）
- shared 目录只放公共资源（default.yaml/opencc），组目录缺资源时 librime 多目录回落

## Oime 新架构（零拷贝）

```
userDataDir    = Documents/Oime/schema/<当前组>/   （内置组 → files/rime/user）
sharedDataDir  = files/rime/shared（固定，assets 同步 default.yaml/opencc/内置方案）
```

- **不再拷贝组文件**：组目录原样使用，用户可用文件管理器直接编辑（所见即所得，同 trime2）
- **App 完全不碰 default.custom.yaml**：组自带的 custom.yaml 由 librime 部署时自动 patch（标准行为）
- **删除「启用集」概念**：方案列表 = librime 部署成功的方案（availableSchemas），与 trime2 一致
- **切组 = 记录组 id + 进程重启**：librime JNI 无 finalize 接口，user_data_dir 在 setup 时固定无法在线更换；Runtime.exit(0) 后系统自动重建 IME 服务，onCreate 按新组目录初始化

## 清除的 xime 系代码

| 删除项 | 位置 |
|--------|------|
| syncGroup（拍平拷贝+YamlPatcher 重写 yaml） | RimeManager |
| YamlPatcher 工具类（整个文件） | core/rime/YamlPatcher.kt |
| groupEnabledIds / setGroupEnabled（启用集） | RimeManager + UI |
| ApplySchemaEnable KeyAction 及处理 | KeyboardScreen + AZimeService |
| importFromFolder（SAF 文件夹导入） | SchemaImporter |
| syncGroup unchanged 检测（.imported manifest） | RimeManager |

## 新/改实现

- `userDirForGroup(groupId)`：内置组 → files/rime/user；导入组 → Documents/Oime/schema/<组>/
- `switchSchemaGroup`：setCurrentGroup + Runtime.exit(0)（进程重启）
- `schemaGroups`：枚举组目录，schema_id = 文件名去 .schema.yaml 后缀
- `deployImportedSchemas`：简化为 startMaintenance(true) + 等待完成
- SchemaList / SchemaManagePage：组切换 + availableSchemas 方案列表（无勾选）
- KeyboardScreen manage 分支：当前组方案列表，点击直接切换

## 混输方案问题的架构级解释

此前混输方案（tiger_danzheng/tiger_sentence）识别不出来：syncGroup 拍平拷贝时
组的 lua/、models/、rime.lua 未同步进 shared/（仅拷 yaml/txt 拍平 + 部分资源目录），
方案编译因缺 lua 组件而失败。trime2 模式下组目录原样加载，依赖天然齐全。

## 参考

- https://github.com/nirenr/trime2（及用户 fork 方圆：Documents/rime/schemas 实测结构）
- https://github.com/rime/weasel


# 轮19（0.9.13-oime vc23）：语音输入本地模型 + 方案管理页精简

## 语音三引擎（删除系统 SpeechRecognizer）

ColorOS 无 RecognitionService（探测不到、调用即失败），改为自带引擎，设置页单选：

| 引擎 | 说明 |
|---|---|
| `sense_voice` | SenseVoice Small int8 离线解码，`zh/en/ja/ko/yue`，松手出全文 |
| `zipformer` | streaming zipformer zh-en int8，边说边出（流式） |
| `web_api` | OpenAI 兼容 `/v1/audio/transcriptions`（自填 BaseURL/Key/模型） |

- `SpeechEngineManager`：AudioRecord 16k mono PCM16 管线 + RMS 声纹；模型侧载 `Documents/Oime/models/`
- 就绪探测：设置页显示每个引擎「已就绪 / 缺模型」，模型缺失时按键提示而不是崩溃

## sherpa-onnx 接入方式（重要）

- 依赖 jitpack 在线拉取在本机网络下卡死（>50 分钟无进展），改用**官方 release AAR**：
  `sherpa-onnx-1.13.5.aar`（49MB，含 Kotlin API + 4 ABI 的 so）
- **49MB 超过 Git Data API blob 上限**（POST 报 422 `input too large`）→ AAR **不进仓库**：
  `.gitignore` 加 `app/libs/*.aar`，CI 在构建前 `curl` 官方 release 下载（GitHub Actions 侧直连很快）
- API 签名核对方法：`api.github.com/repos/k2-fsa/sherpa-onnx/contents/...?ref=v1.13.5` 拉官方
  Kotlin 源码，逐字段比对（类名是 `OfflineSenseVoiceModelConfig` 而不是 `SenseVoiceModelConfig`）

## 方案管理页精简

- 去掉页内「方案组」区块（切组只在上一级「输入方案」页），页内只列组内方案
- `Column` → `LazyColumn`（组内方案可达 20+，原来固定高度不可滚）

# 轮19.1（0.9.14-oime vc24）：五项反馈修复

| 问题 | 根因 | 修复 |
|---|---|---|
| 退格下滑撤回失效 | 撤回栈只存「上屏文本」且撤回**永远执行删除**（上滑全删→下滑撤回会把无关文本再删一遍）；退格方向滑动阈值沿用全局 30dp 太高 | 撤回改**操作栈** `UndoOp(text,isDelete)`：INSERT→删除、DELETE→**恢复**；退格上下滑阈值降至 18dp |
| 剪贴板要再复制一次才显示 | `onCreate` 加载 `clipboard.json` 后**没推 uiState**，面板读到空列表 | 加载后立即 `uiState.update`；︙菜单加「分词」（按标点切词入历史）/「全清」 |
| 震动滑杆 UI 不一致 | 用了原生 `Slider` | 换成 `XimeSlider` |
| 四向符号提示不显示 | 原实现只控制**滑动过程中的临时预览**，键面从不显示 | 常驻渲染四向提示（长按=右上、上=左上、下=左下、左=中左、右=中右） |
| 方案管理后界面不刷新 | 无刷新机制 | 标题栏「部署」后加「刷新」，`SchemaList`/`SchemaManagePage` 加 `refreshRev` |

# 轮19.2（0.9.15-oime vc25）：六项交互

- popup 滑动选择：步长 40dp→**22dp**，并支持上滑换行（每行 5 项）——原值下 K 键 6 对括号要滑 200dp
- 第四行首键长按 popup：只留 26键符号 / 九宫格两项，横向排布 + 图标（`GridOn`/`Dialpad`）
- 26键符号页第四行首键：切九宫格 → **返回**
- 九宫格高度：垂直内边距 `colGap`→`rowGap`（行列距不同时切页跳变）
- 设置页：大方块去 ○ 图标；版本号改**动态读取**（原硬编码 0.9.8，两处）
- 九宫格：滑键默认符号 → `+ - * / = ？ ！`；第四行第二键 `=` → `00`（`rev` 3→4）

# 轮19.3（0.9.16-oime vc26）：三项修复 + 语音波纹

- ○ 圆环下滑：改为**松手才关闭**（原实现越过阈值就 `break` 并立即 `HideKeyboard`，手指没松键盘就没了）
- 九宫格高度**仍**不一致的真因：增高行前多了一个显式 `Spacer(rowGap)`，而外层 Column 已有
  `Arrangement.spacedBy(rowGap)` → 比主键盘高 2×rowGap
- 上一轮第六项未生效的真因：九宫格第 4 行实际渲染在 **`NumpadPane` 内硬编码**，
  改 `KeyboardPages.numpad` 只影响键盘编辑器预览 → 现改 NumpadPane
  **教训：布局改动先找「真实渲染点」，编辑器预览数据与实际渲染是两套**
- 语音动画：铺满工具栏的 28 根音量条 → 中央 120dp **同心波纹**（3 圈错相扩散 + 中心点随音量脉动）

# 轮19.4（0.9.17-oime vc27）：图标方案 J 落地 + 四项交互

## OimeIcons（新文件 `ui/icons/OimeIcons.kt`）

- 自绘 **21 枚** 图标（24 网格）：剪贴板/方案/数字/符号/设置/退格/回车/空格/换挡/返回/语音/云/
  刷新/信息/链接/字体/调色/悬浮窗/代码/emoji/键盘/勾选
- **Compose 1.6 的 `addGroup` 没有 block 重载** → 用 `addGroup(...)` + `addPath` + `clearGroup()` 配对
- 回车 = **纸飞机**（两片机翼留缝形成折痕，单色 tint 下也能读出折线）
- 键面图标：`Key` 新增 `icon: String?` 字段；`KeyboardKey` 优先渲染图标（滑动预览中回落文字），
  尺寸 = `fontSizeKey×1.25` 限幅 14~34dp
- 工具栏 `toolbarToolIcon` 与设置页 13 处图标全部替换

## 其余

- 工具栏复制条：**任意按键消亡**（原来只有上屏消亡），白名单判定按键类动作
- 工具栏字号收敛：`上限=(barHeight-6dp)/2.04`——高度不变但字号再大也不伸出窗口
- 主键盘/九宫格底部留白 `rowGap→2dp`（与最下沿距离过大）
- O 圆环改**呼啦圈**：移动光标时圆环自身平移（锚点不动），去掉环内蓝点

# 轮19.5（0.9.18-oime vc28）：图标去重影 + 空格键显示方案名称

- OimeIcons 去掉重影层（用户反馈阴影看得眼花）→ 纯单层主体
- 空格键显示优先级：自定义文本有可见字符→该文本；**只打空格→只显示图标**；留空→**方案名称**
  - 修正：原来显示的是 schema **文件名**（`schemaName.substringAfterLast('.')`），
    改用 `RimeManager.schemaDisplayName()` 读 `schema.yaml` 的 `name` 字段
  - `KeyboardKey` 加例外：空格键有文本时优先文本，文本为空才用图标（其它功能键仍图标优先）

# 轮19.6（0.9.19-oime vc29）：O 圆环形状与应用弧 + 间距对齐 + 黑底白圆环图标

## 间距与面板高度

- 工具栏 `barHeight` 44→**40dp**；键盘顶留白 `rowGap→0`（图标在「灰色带+首行」整体中才居中）
- **面板高度公式修正**：`stdH = keyH*4 + rowGap*3 + 2dp`（原 `+rowGap*5`）——
  19.4 改底留白后，面板比键盘高 `rowGap-2dp`，剪贴板/O菜单/emoji/符号面板随之对齐
- 九宫格/符号页空格键永远显示图标（`preferText` 仅主键盘生效）
- emoji/全部符号网格**行高自适应填充**（原来只 3 行、下方大片空白）

## O 圆环：三形状 + 上滑应用弧

- 形状：`ring` 圆环 / `square` 圆角方形环 / `eye` 环内双眼
  - eye 随机动作随机时间：眨眼（圆点 ↔ 长条）、左右看（`eyeDx ±1`），`LaunchedEffect` 循环
- **上滑呼出应用弧**：5 个应用图标半圆分布（半径 110dp，角度 160/125/90/55/20），
  按横向位移选槽（55dp/槽），松手 `AppLauncher.launch`
- `core/apps/AppLauncher.kt`：`installedApps` / `icon` / `launch` / `canQueryApps`
- Manifest 加 `QUERY_ALL_PACKAGES`；首次向导加**第 5 页**（说明 + 可读应用数状态）
- 设置新大项「O 圆环」：形状三选一 + 5 槽位（应用选择器含搜索/清除槽位/图标列表）

## 黑底白圆环 App 图标

- `ic_launcher_background.xml` → `#000000`；`ic_launcher_foreground.xml` → 只留白环（stroke 3.4）
- PNG 全密度重生成（48/72/96/144/192，方形 + 圆形）

# 轮19.7（0.9.20-oime vc30）：耗电优化四项

| # | 问题 | 优化 |
|---|---|---|
| 1 | `schemaDisplayName` **每次调用读磁盘**（schema.yaml `useLines`），空格键标签每次重组都会调 → 键盘可见期间≈每帧一次 IO | RimeManager 加 HashMap 缓存 + UI `remember(schemaName)`；切组/部署时 `clearDisplayNameCache()` |
| 2 | `refreshState()` 每次按键都调 `availableSchemas()`（JNI + 列表分配） | 改按需（`schemasDirty` 脏标记） |
| 3 | **无上限 3s 轮询** `while(!isSessionReady) delay(3000)`——引擎起不来会永久每 3 秒唤醒 | 有上限退避：5×3s + 15×15s ≈ 4 分钟后放弃，按键兜底 |
| 4 | 设置页 O 圆环一次性解码上百个应用图标（几十 MB Bitmap） | 列表只带包名/名称，图标渲染时按需 + 缓存上限 240 |

# 构建与推送链路（轮19 期间的变化）

- 版本推进：vc23 → vc30（0.9.13 → 0.9.20-oime），全部经 GitHub Actions 构建后 `adb install -r` 装机
- **本地 `.git` 已损坏**：`git add -A` 报 `bad tree object HEAD`，index 不可用。
  改用 `push_via_api_tree.py`（不读 index，遍历工作树 + 内置忽略规则）推送到 Git Data API
- 推送后已逐文件核对（git blob 哈希比对）：远程与本地**完全一致**

# 轮19.8（0.9.21-oime vc31）：O 圆环上滑改横向悬浮栏 + 工具栏高度可调

## 上滑快捷应用：半圆弧 → 横向悬浮栏

- 原来是 Popper 面板内按 160°/125°/90°/55°/20° 摆 5 个图标（半圆），现改为
  **一行横向居中排布**的悬浮栏：面板 296×72dp，图标 48dp、间距 8dp、左右内边距 12dp
- 选择逻辑相应简化：`idx = ((dx - arcFirst)/arcStep).roundToInt()`，
  其中 `arcStep = 56dp`（图标 48 + 间距 8），`arcFirst = -112dp`
  （面板居中后，槽 0 的中心落在 ○ 键中心左侧 112dp）
- 选中态：强调色底 + 图标放大（30→34dp）；松手即启动；未排布的槽显示「＋」

### Popup 定位原理（备忘）

Compose 的 `Popup(alignment = ..., offset = ...)` 走 `AlignmentOffsetPositionProvider`：
`alignment.align(IntSize.Zero, 父容器尺寸, ld)` —— 计算的是**零尺寸点**在父容器内的位置。
所以对 `Alignment.TopCenter`，弹层**左边缘**落在「父容器水平中点 + offset.x」。
要水平居中于父容器：`offset.x = -面板宽/2`；要贴在上方：`offset.y = -(面板高 + 间距)`。
（这也是长按气泡 `bubbleOffset` 的算法基础。）

## 工具栏高度可调（新设置项）

- 新增 pref `toolbar_height_dp`（默认 40dp，钳位 32~72）
- 设置路径：键盘 → 键高 下方「工具栏高度」滑杆
- `barHeight` 不再硬编码，读 `KeyboardManager.toolbarHeightDp()`
- 已计入 `sizeSignature()` → 改完自动重建输入视图（热生效）
- 说明：工具栏高度同时决定「输入码 + 候选」两行字号上限 `(barHeight-6dp)/2.04`（轮19.4），
  调高工具栏自然能放更大字号

## 轮19.8b 修正：上滑悬浮栏没有居中（vc32）

**现象**：上滑呼出的横向悬浮栏明显偏左。

**根因**：对 Compose `Popup(alignment = TopCenter, offset)` 的定位语义理解错了。
`AlignmentOffsetPositionProvider` 的真实公式是：

```
popupPos = 父左上 + alignment.align(Zero, 父尺寸) − alignment.align(Zero, 弹层尺寸) + offset
```

即 TopCenter 下 **弹层中心 = 父中心 + offset.x**（弹层自身对齐点被减掉了），
**offset.x = 0 就是水平居中**。上一版按「左边缘对齐父中心」推断写了 `-面板宽/2 = -148dp`，
把整条往左推了 148dp，所以看起来没居中。

**旁证**：长按符号气泡（同 TopCenter）的 `bubbleXDp` 默认值是 3dp、范围 0~24 的「微调」量——
若真按左边缘对齐，3dp 的微调会让气泡整个跑到键右侧，早就该被反馈了。

**修复**：`offset.x = 0`；同时把面板从 296dp 收窄到 280dp（图标 48dp、间距 6dp、内边距 8dp），
避免在 308dp 级窄屏上几乎占满整宽。选择映射同步为 54dp/槽、首槽 −108dp。

# 轮19.9（0.9.23-oime vc33）：实测耗电定位 + 三项释放策略 + 呼吸动画降帧

## 实机测量（2026-09-11，vc32 / 0.9.22-oime）

`dumpsys batterystats` + `/proc` 线程级采样：

| 指标 | 数值 | 说明 |
|---|---|---|
| 进程累计 CPU | 2339 s（pid 存活 9.7 h） | 平均约 6.7% 单核 |
| **main 线程** | **1659 s（71%）** | 组合/布局/绘制 + 按键 JNI |
| **RenderThread** | **496 s（21%）** | UI 渲染 ← 与主线程合计 92% |
| HeapTaskDaemon | 26 s | 无明显分配风暴 |
| binder（引擎调用） | 各线程 5~10 s | librime 侧开销很小 |
| 空闲 30 s（键盘隐藏） | **0 ticks** | 后台彻底静默（轮19.7 轮询优化生效） |
| AlarmManager | **0 条** | 无闹钟 |
| App 自持 WakeLock | **无**（仅系统 *vibrator* 2s/102 次） | —— |
| 会话耗电占比 | 6.06 mAh / 166 mAh（39 min 会话） | 其中 screen 4.63、cpu 1.42 |

**结论：耗电主因不是引擎，而是 UI 渲染**——librime 的 binder/JNI 开销是零头，
唯一的常驻动画（○ 环呼吸）在每个 vsync 都让 IME 窗口失效重绘。

## 三项释放策略（对齐 trime2 / Xime 的做法）

调研结论：

| 项目 | 做法 |
|---|---|
| **trime2** | `Rime.finalize()` 只在 `TrimeService.onDestroy()` 调用（无空闲释放）；`onWindowHidden` 销毁 Speech 对象；`onDestroy` 里 `mHandler.removeCallbacksAndMessages(null)`；引擎跑在专用 dispatcher 线程；`onFinishInputView` 里 `ic.requestCursorUpdates(0)` |
| **Xime** | `onFinishInputView` **立即 release 手写模型**（"用键盘时加载、键盘收起即卸载"，release 幂等）；`onFinishInput`/`onWindowHidden` → `clearInputState()`（关面板、清剪贴板条状态、重置临时表单，释放引用）；`onWindowShown` 只做**两次资源读取对比**（取色未变则零成本）；`onDestroy` 全量释放（rimeEngine.destroy / clipboard / feedback / Association / ASR / handwriting / Extension / ONNX shared env）；release 构建抑制调试日志 |

Oime 落地（本轮）：

1. **`RimeManager.releaseAll()`**：`RimeEngine.destroy()` + 复位 session + 清显示名缓存；
   在 `AZimeService.onDestroy()` 调用（原来只 cancel 语音，librime native 资源不释放）
2. **`onDestroy` 释放语音 ONNX 会话**（`SpeechEngineManager.releaseEngines()`）
3. **`onFinishInputView`**：关闭残留面板（剪贴板/菜单/候选面板，对齐 Xime `clearInputState`）
   + **语音引擎 90s 闲置卸载**（Xime 是立即卸；这里给宽限，连续听写不必反复加载模型；
   再次触发语音时取消计时）
4. **○ 环呼吸动画降帧**：`rememberInfiniteTransition`（每 vsync 一帧）→ 120ms 步进（≈8fps）
   的三角波取值，重绘次数降到 1/8~1/15，视觉几乎无差

**待做（下一轮候选）**：键盘整体重组范围过大——`AzimeKeyboardScreen(state = 整个 uiState)`
导致任一字段变化都重组整棵树；可用 derivedState/分片 state 收敛（这是 main 线程剩余的
大头，属于结构性改动，需要真机逐项验证）。

# 轮19.10（0.9.24-oime vc34）：六项交互/界面修正

1. **定制工具栏的「保存」移到悬浮栏标题右侧**：`MenuSubPanel` 新增 `headerTrailing` 槽位，
   渲染在「← 返回 + 标题」之后、「↑ 关闭」之前；面板底部只留「取消」
2. **九宫格「00」只出一个 0**：`KeyType.CHARACTER` 的分发原来写 `CharKey(key.code.first())`，
   多字符 code 被截成首字符 → 现在 `key.code.length > 1` 走 `DirectCommit(key.code)` 整串上屏
3. **主键盘第 2/3 行不对齐**（S..K 与 Z..M）：
   - 根因：第 2 行 11 个子元素（10 道行距），第 3 行 9 个（8 道行距）；两行等宽的前提下
     单位键宽 U=(W−n·G)/Σw 不等（差 0.2×rowGap），累到行尾偏出近一个键位
   - 修复：第 3 行改为 `[spacer 0.5][⇧ 1.0][Z..M][⌫ 1.0][spacer 0.5]`——与第 2 行同为
     11 子元素、权重和同为 10 ⇒ U 完全一致，且 Z..M 精确落在 S..K 正下方
   - qwerty `rev` 3→4（触发淘汰设备上的旧自定义副本）
4. **设置主页大项拆卡**：原来 7 个条目共用一个 Card 背景，现各自独立成卡
5. **预设置（Lua 编辑器）状态栏不沉浸**：`LuaEditorActivity` 缺 `enableEdgeToEdge()`
   （只有 SettingsActivity 有）。同时给 `KeyboardEditorActivity` / `FontManagerActivity` 一并加上，
   避免同族页面表现不一致
   - 坑：`enableEdgeToEdge` 是 **ComponentActivity 的扩展函数**，不能用全限定名
     `androidx.activity.enableEdgeToEdge()` 调用（Kotlin 扩展函数不支持这种写法），
     必须 `import androidx.activity.enableEdgeToEdge` 后直接调用
6. 推送并构建（用户已授权）

# 轮19.11（0.9.25-oime vc35）：八项交互/功能

1. **⇧/⌫ 宽度回退 1.5**（用户只要字母键对齐）：第 3 行改为
   `[ε填充][⇧ 1.5][Z..M][⌫ 1.5][ε填充]`（ε=0.01 权重）——仍是 11 子元素/10 道行距，
   Σw=10.02 vs 第 2 行 10 ⇒ 单位键宽差 0.2%，Z..M 与 S..K 偏差 < 0.3dp（肉眼不可见），
   同时 ⇧/⌫ 回到原宽度。qwerty `rev` 4→5
2. **悬浮窗重做**：
   - **跟随光标**：`onStartInputView` 请求 `requestCursorUpdates(CURSOR_UPDATE_MONITOR)`，
     `onUpdateCursorAnchorInfo` 用 `insertionMarkerHorizontal/Top/Bottom` + matrix 换成屏幕坐标
     存入 uiState；UI 用 `LocalView.getLocationOnScreen` 得到 IME 窗口 top，把屏幕坐标换算成
     Popup 偏移（浮在光标上方 6dp，高度用 `onGloballyPositioned` 实测回退修正）；
     收起键盘时 `requestCursorUpdates(0)` 停监听并清坐标
   - **同步候选**：悬浮窗内显示「前三码 + 前 6 个候选」
   - **字号同步**：改用 `fontSizeBar()`（原来固定 `floatTextSp`）
   - **前三码规则**：悬浮窗只显示 `preedit.take(3)`，工具栏显示 `preedit.drop(3)`
3. **定制工具栏**：去掉顶部说明文字与底部「取消」按钮（保存仍在标题右侧）
4. **O 圆环眼睛**：动作扩到 5 种（眨眼 / 连眨两下 / 左右看 / 上下看 / 眯眼），
   间隔拉长到 **2000~9000ms**（原 500~3100ms），单次时长也拉长（看 700~2400ms、眯眼 900~2600ms）
5. **上滑悬浮栏可关闭**：栏内**下滑即关闭**且不再误触「下滑收键盘」（新增 gestureDone 守卫）；
   另外必须**横向移动 > 24dp** 才算选中某槽，否则松手只关闭不启动（原来必启动中间槽 = 「只能打开不能关闭」）
6. **工具栏工具扩充**：availableToolbarTools 从 6 个扩到 **17 个**（新增语音/候选/键盘编辑/部署/
   主题/中英/撤回/全清/收起/悬浮窗/O 圆环），上限 6 个（每侧 3）；
   **默认不再显示任何工具（工具栏只有 ○ 圆环）**，旧默认存档自动视为空
7. **方案按钮 → 剪贴板同款悬浮栏**：新增 `ToggleSchemaPanel` + `SchemaQuickPanel`
   （MenuSubPanel chrome + 底部悬浮栏），方案**横向排布**（可横滑）、点击切换并关闭；
   当前方案高亮 + 勾选图标
8. **○ 菜单图标同步**：原来仍是 Material 图标，现全部换成 OimeIcons（并新增 10 枚图标：
   tune/toggle/apps/manage/sun/moon/ring/candidates/undo/trash）；菜单项也扩充到 19 项

# 轮19.12（待发 vc36）：八项修正 + 横屏设计（横屏待选型，故未推送）

1. **第 3 行填充键挪到内侧**：`[⇧ 1.5][ε][Z..M][ε][⌫ 1.5]`（ε=0.01）——
   端部不再有填充键 ⇒ ⇧ 左边缘=0（与下行 # 键左缘齐）、⌫ 右边缘=行尾（与 ⏎ 齐），
   同时仍 11 子元素/10 道行距（单位键宽与第 2 行差 0.6%，Z..M 对齐 S..K 偏差 <0.3dp）。rev 5→6
2. **○ 环呼吸放慢**：19.9 为省电把 InfiniteTransition 换成 120ms 步进的**三角波**，
   周期没变但线性往返没有缓入缓出 → 观感"急促"。现改为**周期 3.6s 的正弦波**（步进仍是 120ms，省电不变）
3. **○ 菜单瘦回 9 项**：19.11 塞进菜单的那些（O 圆环/更多候选/中英/全清/撤回/语音/悬浮窗/预设置/主题/收起）
   全部移出——只在「定制工具栏」的可选列表里
4. **方案切换改为键盘下方中间的横向悬浮栏**：去掉原来的左上角 popup 菜单 + 19.11 的整屏面板，
   改为 `Popup(alignment = BottomCenter)` 的圆角悬浮栏（方案 chip 横排、可横滑，点击切换并关闭）
5. **`{Left}` 光标回退改用 setSelection**：原来发 KEYCODE_DPAD_LEFT，多数 App（微信等）不理会
   软键盘的 DPAD 事件 → 括号上屏后光标不在中间。现用 `getExtractedText` 算绝对位置后 `setSelection`，
   失败才回落 DPAD。K 键括号气泡（BracketPairs 已带 `{Left}`）即刻生效
6. **设置主页配色**：大方块=强调色（不变）、右侧版本/项目两方块=**按键色**、其余 7 个大项卡=**浅灰**
   （浅色 #F1F1F2 / 深色 #26262A）。坑：`plainCardColors` 必须建在 composable 作用域，
   放 LazyColumn 的 scope 里会报 "@Composable invocations can only happen from …"
7. **语音动画改长条波纹**：16 根竖条 + 钟形包络 + 相位流动，宽度 = `fillMaxWidth(0.5f)`（工具栏一半）
8. **键面提示位置**：四向提示回归字面方向（上→TopCenter、下→BottomCenter、左→CenterStart、右→CenterEnd），
   长按仍固定右上角（TopEnd）

## 横屏键盘三套设计（待用户选型）
- **L1 双侧分体**：左右各 5 列 + 中间手势区；拇指行程短
- **L2 侧栏候选**：左右分栏，中间竖条常显 3 个候选；底行全宽
- **L3 全宽紧凑**：候选独立一行在最上，四行满宽；最接近竖屏肌肉记忆、改动最小

# 轮19.13（0.9.26-oime vc36）：横屏分体键盘（方案 L4，B 键归右半）

用户在两轮共 6 套设计里选定 **L4 镜像错位分体**，并要求 **B 键挪到右半**（左 Z X C V / 右 B N M）。

## 结构（数据侧就用占位键表达，无需改 Key 模型）

```
Q W E R T ‖ Y U I O P
 A S D F G ‖ H J K L           ← 左半右缩 0.4 / 右半左缩 0.4（镜像）
  Z X C V ‖ B N M              ← 缩进 0.8
123 ‖ 空格 ｜ 空格 ‖ ⌫          ← 权重和同为 10.9，与上面各列对齐
```

- 每行权重和都取 **10.9**（行内占位键表达缩进与中间分体空隙）⇒ 各行单位键宽一致、上下按键对齐
- 中间分体空隙 = 0.9 键宽（横屏 ≈ 70dp），左半越往下越向中间缩（镜像错位）
- 横屏**键高** = 竖屏键高的 72%（钳位 28~40dp）：横屏可视高度只有竖屏一半，
  「工具栏 + 4 行」控制在横屏高度 ~55%

## 切换方式

`AzimeKeyboardScreen` 读 `LocalConfiguration.orientation`：
- 横屏 + main 页 → `KeyboardManager.landscapeMainLayout()`（qwertyLand）+ 横屏键高
- 其余情况（竖屏、横屏下的符号/九宫格/emoji 页）→ 原逻辑不变
竖屏布局、用户自定义布局、键盘编辑器均**不受影响**。

## 踩坑

`kotlin.math.roundToInt(Float)` 是**扩展函数**，不能当普通函数调用（`kotlin.math.roundToInt(x)` 报
Unresolved reference）；要么 import 后 `x.roundToInt()`，要么直接 `x.toInt()`（此处用了后者）。

# 轮19.14（0.9.27-oime vc37）：○ 菜单瘦身 / 九宫格列宽 / 符号网格暗色可读 + 四行

1. **○ 菜单去掉「方案组 / 方案管理 / 输入方案」**：方案管理统一在设置里做；
   菜单保留：剪贴板 / 方案开关 / 部署 / 键盘编辑 / 定制工具栏 / 亮暗色（方案切换走工具栏「方案」按钮的底部悬浮栏）
2. **九宫格列宽不再均分**：第一列（滑键）与第五列（功能键）权重 1.0 → **0.78**，
   中间三列 → **(5−0.78×2)/3 ≈ 1.147**（总权重仍为 5）；第四行同步
   （返回/⏎ = 0.78、00/0/. = 1.147），保证上下列对齐
3. **全部符号（symgrid）暗色下看不清**：
   - 根因：网格里的 `Text` **没有指定颜色**，而 IME 内的 MaterialTheme 恒为 `lightColorScheme`
     ⇒ 暗色主题下键面是深色、字仍然是黑的。现显式用 `keyboardColors().text`（主题反色）
   - **四行排布**：`perRow = ceil(n/4)`（上限 8 列）——24 个符号 = 6 列 × 4 行；
     条目 >24 的页仍按 8 列滚动；行高仍走 19.6 的自适应填充
