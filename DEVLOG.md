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
| 2 | 方案管理重做：方案组→方案 两级（参考 trime2） | RimeManager 新增方案组区块：SchemaGroup(id/name/builtin/schemaIds)、schemaGroups()（内置组 + Documents/Oime/schemas/ 子目录=方案组，组内扫 *.schema.yaml）、currentGroupId/setCurrentGroup/recordGroupSchema（schema_group_prefs）；syncGroup 共用启动/切组——删上一组文件（.imported 清单，与内置资产同名从 assets 恢复）→ 拷入组文件（组覆盖层）→ 重写 default.custom.yaml（schema_list 仅含组内方案，组内上次使用置首）→ 不变化跳过；switchSchemaGroup=setCurrentGroup→syncGroup→全量维护→重建会话；删 deployPendingImport；ensureReady/deployImportedSchemas 接线 |
| 3 | O 菜单加「方案组」大项 + 设置页同步 | KeyAction.SelectSchemaGroup + Service 处理（statusMessage「正在切换方案组…」）；SelectSchema 成功后 recordGroupSchema（组内记忆）；主菜单第 3 项「方案组」（Apps 图标）+ groups 子级 4 列卡片网格（组名 + 「N 个方案」副文本，当前组 accent 高亮，state.schemas 作刷新 key）；设置页 SchemaList 顶部方案组单选区（RadioButton + 方案数），组内方案列表保留并同样记录；导入文案改「已导入方案组，可在方案组中切换」（不自动切换，对齐 trime2 安装语义）；promptRename 重命名当前组时同步 setCurrentGroup |

技术记录：
- 修复机制：schema_list 只写当前组 + recordGroupSchema 把组内最后使用的方案置首 → 部署后 librime 回落 schema_list[0] 即回到用户方案，不再跳回
- 旧版平滑迁移：升级后首次启动 currentGroup=内置组，syncGroup 按旧 .imported 清单删除全部导入文件（同名内置资产从 assets 恢复），源文件仍留在 Documents/Oime/schemas/<组名>/ 不丢
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
userDataDir    = Documents/Oime/schemas/<当前组>/   （内置组 → files/rime/user）
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

- `userDirForGroup(groupId)`：内置组 → files/rime/user；导入组 → Documents/Oime/schemas/<组>/
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

# 轮19.15（0.9.28-oime vc38）：横屏回车 / 复制条消亡与居中 / 设置配色与状态卡

1. **横屏键盘补回回车键**：横屏首版的第 4 行是 `123 / 空格 / 空格 / ⌫`——漏了 ⏎（用户截图反馈）。
   现改为 `[123 1.5][空格 3.5] ‖ [空格 2.2][⌫ 1.0][⏎ 1.8]`，权重和仍为 10.9（与上面各列对齐）。rev 1→2
2. **工具栏复制条**：
   - **文字居中**（原来左对齐）
   - **打字即消亡（真因修复）**：`updateFromResult` 里有一行「反馈轮12：组词不再清空工具栏复制条」——
     即组词期间特意保留复制条；而消亡只靠 `onKeyAction` 里的白名单判定，
     白名单一旦漏掉某个动作（或组词路径不经过它）就会出现「怎么打字都不消失」。
     现在：① `updateFromResult` 里**只要有输入码/候选就清空复制条**（兜底，覆盖所有按键路径）；
     ② `onKeyAction` 的白名单改**黑名单**（只有剪贴板面板自身的操作不清除）
3. **设置界面配色与状态卡**：
   - 大方块（状态卡）继续跟随强调色；**右侧两小方块改浅白色**（浅色 #FCFCFD / 深色 #2E2E33）
   - **二级页卡片统一浅灰**：在 `MaterialTheme` 里覆盖 `surfaceContainerLow` / `surfaceContainer`
     （Card 默认底色）为浅灰 #F1F1F2（深色 #26262A）——一处改动，所有二级页生效
   - 状态卡改为**三行**：`○输入法` → `运行状态`（运行正常 / 未启用 · 点击去启用 / 引擎未就绪）→ `方案 · <当前方案>`

# 轮19.16（0.9.29-oime vc39）：复制条复活真因 / 菜单悬浮栏统一返回 / 方案栏半透明 / 设置配色

1. **「复制条打字不消亡」的真因（终于）**：
   - 现象不对称：**点击复制条上屏**能永久消失，**打字**怎么都不消失
   - 原因：`readClipboard()` 里只跳过 `lastCommittedClip`，而它**只在点击复制条上屏时**才被赋值；
     消亡（按键/组词）只是把 `clipText` 清空，没留痕 ⇒ 任何再次触发的 `readClipboard()`
     （剪贴板监听器重放、重新弹出键盘时的 `onStartInputView`）都会把文本**重新塞回来**
   - 修复：新增 `dismissedClip`，消亡时记住文本；`readClipboard()` 对「已上屏 / 已消亡」的文本一律跳过
   - 同时**加了埋点日志**（tag `OimeClip`）：show clip strip / dismiss by key / dismiss by composing /
     skip re-show (dismissed)——下次复现可直接从 logcat 看到是哪条路径在复活
   - 附带：复制条**垂直+水平居中**，字号**跟随工具栏字号**（原来固定 14sp）
2. **菜单父级/子级悬浮栏统一「左侧返回」**：删掉 `MenuSubPanel` 右侧的「↑ 关闭」与 ○ 菜单一级栏的
   「↑」，两个层级都只保留左侧「← 返回」
3. **设置右侧两小方块改色**：上一版的浅白跟页面底色撞了 → 改用**功能键灰**
   （浅色 #E3E5E8 / 深色 #3A3A3F，与键盘功能键同色系）
4. **工具栏「方案」悬浮栏**：背景由 `barBg @96%` 改成 **`funcKeyBg @80%`**（灰色半透明，
   与剪贴板/菜单悬浮栏同一 chrome）

# 轮19.17（0.9.30-oime vc40）：耗电三项优化 + 用户三点改动

## 一、按手机侧报告做的耗电优化（报告结论见 `oime-优化结论-PC侧核实.md`）

| # | 优化 | 具体改动 |
|---|---|---|
| P0-1 | **振动统一入口 + 节流** | `HapticsManager` 新增 `haptic(Type)` 统一入口与 **70ms 节流**；`KeyboardScreen` 里 **11 处** `performHapticFeedback`（6×LongPress / 5×TextHandleMove）全部改走它 —— 顺带修掉一个 bug：**以前关了「打字振动」手势照样振** |
| P0-2 | **引擎懒初始化 + 解锁预热** | `onCreate` 不再加载 librime/建会话（熄屏期系统重绑 IME 时会做「加载词典+建会话」重活 → 熄屏 CPU 4.55 mAh）；推迟到第一次 `onStartInputView`，并注册 `ACTION_USER_PRESENT` 广播在**解锁时预热**补回首弹延迟 |
| P1 | **复制条开关** | 新增 `KeyboardManager.clipStripEnabled()`（默认开）；关闭时不注册剪贴板监听、`readClipboard()` 直接返回；设置 → 键盘 加开关 |

未做：AudioRecord 实例复用（收益有限、有麦克风状态风险）、targetSdk 36（见结论文档：四个被限制机制我们都没用，收益≈0）。

## 二、用户三点改动

1. **横屏分体键盘废弃**：删掉 `landscapeMainLayout()` 的使用（qwertyLand 不再引用），
   横屏沿用竖屏布局，**只把键高限低 25%**（`landscapeKeyHeightDp = keyHeightDp × 0.75`，钳位 26~48dp）
2. **复制条消亡规则定稿**：只有两条途径 —— ①**点击复制条上屏** ②**按退格键**（且**当前无输入码/候选**；
   组词中按退格是删编码，不消亡）。撤销 19.15/19.16 的「任意按键消亡 / 组词消亡」
3. **工具栏白名单精简到 11 项**：剪贴板 / 方案 / 数字 / emoji / 符号 / 设置 / 语音 / 收起 / 中英 / **撤回** / **重做**
   - 「中英」原来 fallback 到「方案」图标（重合）→ 新增专属 `OimeIcons.lang`（"文 A" 造型）
   - **新增 redo**：`KeyAction.Redo` + `redoStack`（撤回时把反向操作压入 redo 栈；任何新操作清空 redo 栈）

# 轮19.18（0.9.31-oime vc41）：复制条改为「左右划动消亡」

- **撤掉 19.17 的「退格键消亡」**：用户场景是**强制复制的无效内容**——被逼着先上屏才能清掉不合理
- **新增：在复制条上左右划动即消亡**（`KeyAction.DismissClipStrip`）：
  - 实现在复制条 Box 上挂 `detectHorizontalDragGestures`，横向累计位移 > **40dp** 触发消亡
  - 拖动时 `change.consume()`，因此**不会同时触发 clickable 的上屏**（点=上屏，划=消亡，互不干扰）
  - 消亡同样写入 `dismissedClip`，避免被剪贴板回调"复活"
- 打字 / 组词依旧**不消亡**复制条（保持上一轮语义）
- 坑（第二次踩）：`detectHorizontalDragGestures` 是 **PointerInputScope 的扩展函数**，
  不能用全限定名调用（和 19.10 的 `enableEdgeToEdge` 同一个坑）→ 必须 `import` 后直调

# 轮19.19（0.9.32-oime vc42）：长按 popup 可编辑 / 工具栏内容四态 / 单手+悬浮模式 / 两处修复

1. **长按 popup 现在可以在键盘编辑器里自定义**
   - `Key` 新增 `popup: List<String>` 字段（非空时**覆盖**内置 LongPressSymbols 与 K 键括号表）
   - 编辑器属性对话框新增「长按气泡（空格分隔多个符号）」输入框，支持内置命令（copy/cut/…）与
     `{text}{Left}` 光标后缀语法
2. **「嵌入式」设置：工具栏候选行显示内容**（设置 → 键盘）
   - `首选` 只显示首选候选（大字）· `编码` 只显示输入码 · `输入码` 输入码+候选（默认）· `无` 不显示
   - 实现在 composing 分支按 `barContentMode()` 取舍两行
3. **单手模式 + 悬浮模式**
   - 单手：`off / left / right` 三态循环，键盘缩到 **78%** 并贴左/右侧（外层 Box 全宽铺底色，两侧不留透明）
   - 悬浮：键盘缩到 **86%**，顶部出现 **16dp 拖动条**（拖它整体移动），位置持久化（松手落盘、钳位）
   - 定制工具栏新增两个工具：**单手**（新图标 `OimeIcons.oneHand`）/ **悬浮**
   - 新增 `KeyboardUiState.layoutRev`：这类"只改 prefs"的动作靠它强制重组
   - 设置 → 键盘 也加了对应入口（含关闭悬浮时重置位置）
4. **修复：H 键长按在英文模式应输出 `_`**（原来固定出「——」）
   - H 的内置表改 `("——", "_")`；长按气泡在有 asciiMode 时**优先取 ASCII 符号**（无则回落原列表）
5. **修复：K 键括号上屏后光标不居中**
   - 真因：19.12 加的 `setSelection` 是在 **`beginBatchEdit()` 内**调的，batch 内
     `getExtractedText` 常返回**提交前的过期快照** → 位置算错
   - 现在：光标移动移到 `endBatchEdit()` **之后**；加 **回读校验**（没真动就回落到 DPAD）；
     并加埋点日志 `OimeCursor`（`move delta=… setSelection=ok/failed`）便于真机确认

# 轮19.20（0.9.33-oime vc43）：嵌入式语义修正 / 悬浮浮到最上层 / 单手交互 / K 键覆盖真因

## 1. 「嵌入式」我之前理解错了 → 现按中文输入法的**嵌入式编辑**实现
- 19.19 做成了"工具栏候选行显示什么"，**错了**。真正含义：把编码/候选**内嵌到应用文本框**（带下划线）
- 现在：`KeyboardManager.inlineMode()` = 首选 / 编码 / 输入码（编码+首选，最常见）/ 无（默认）
- 实现：`applyInlineComposing()` 在 `updateFromResult` 里用 **`setComposingText`** 把
  「编码 / 首选候选」作为 composing text 写进输入框；组合结束或关闭时 `finishComposingText()`
- 默认「无」⇒ 不改变现有行为，只有选了才生效
- 同时**回退** 19.19 对工具栏候选行的改动（工具栏恢复恒定显示）

## 2. K 键「不能完全覆盖原生配置」的真因
- 手势优先级是：`longPressSymbols.isNotEmpty()` → 才轮到 `hasCustomLong`
- 而 K 键的**内置括号表恒非空** ⇒ 自定义的 `longClick`（编辑器「长按」字段）**永远到不了** ✗
- 现在：`popup`（编辑器「长按气泡」）**或** `longClick`（编辑器「长按」）任一非空，
  就**完全替换**内置表（含 K 的括号表）
- 另加埋点 `OimeSym`（`code=… ascii=… list=[…]`）：下次长按即可确认实际用的是哪份列表

## 3. 悬浮模式浮不到最上层（被 App 挡住）→ 改窗口级实现
- 真因：IME 窗口只有键盘那么高，把内容往上偏移就画到窗口外 → 被 App 覆盖且不可触摸
- 现在：悬浮模式把 **IME 窗口设为铺满整屏** + 背景透明 + `setDimAmount(0)`；
  并用 **`onComputeInsets`** 把 `touchableInsets` 设为 `TOUCHABLE_INSETS_REGION`、
  `touchableRegion` = 键盘矩形（UI 通过 `onGloballyPositioned` 上报 `floatKbdTop/Bottom`）
  ⇒ 键盘浮在最上层、其余区域触摸穿透给 App
- 悬浮时外层**不再铺键盘底色**（否则整屏被盖）

## 4. 单手模式交互按用户要求改
- 工具栏「单手」= **开关**（关 ↔ 开，开时沿用上次那侧/默认左手）
- 单手时**空白一侧显示圆形箭头 ▶/◀**，点击切左右手（`SwitchHandSide`）
- 替换掉 19.19 的「循环三态」逻辑

# 轮19.21（0.9.34-oime vc44）：嵌入式工具栏去重 / 括号自动居中 / 悬浮可见性 / 恢复备份

1. **嵌入式选中「编码/输入码」时，工具栏不再重复显示编码**（只留候选行）
2. **括号上屏后光标居中——不再依赖 `{Left}`**：
   - 之前只有写 `{Left}` 才居中；用户自定义的 popup 若没写就完全不生效
   - 现在 `bracketMiddleMove()` 检测「提交文本本身是一对括号」（() （） [] 【】 {} 「」 『』 《》 〈〉 “” ‘’）
     → 自动 `setSelection` 移到中间。DirectCommit 与 Resolved 两条路径都接上
   - **埋点修位**：19.19/19.20 的 `OimeSym` 放在 `remember{}` 里（长按不会重算 → 日志永远为空），
     现改到**长按触发时**与**提交时**两处；另加 `OimeAscii`（ascii 状态变化打点）
3. **悬浮键盘必须整体可见**：
   - 真因：19.20 只做了"窗口铺满整屏"，**在切换悬浮的当下没有重设窗口** → 窗口仍是键盘高度，
     往上拖就把上半截拖到窗口外（用户截图里只剩最后两行）
   - 现在：① 抽出 `applyKeyboardWindowLayout()`，**切换悬浮时立即调用**；
     ② UI 用 `BoxWithConstraints` 拿窗口高度，拖动时**把键盘钳制在窗口内**（任何情况都完整可见）
4. **设置 → 关于 → 新增「恢复备份」**：文件选择器选 `Oime_backup_*.json` →
   `restoreSettings()` 按类型写回全部偏好（JSON 数字按是否整数还原 int/float），提示恢复项数

# 轮19.22（0.9.35-oime vc45）：文件埋点（logcat 被系统吞了）+ 候选行居中 + 悬浮真正浮起

## 0. 重要发现：本机 logcat **只放行系统日志**，第三方 App 的 Log.d 一条都读不到
- 实测：App 进程在跑（pid/版本都对），但 `logcat -d | grep OimeXxx` 恒为 0 条
- 结论：之前三轮"零埋点"不是代码没跑，而是**日志被 OEM 吞了** —— 诊断手段本身失效
- 修复：新增 `com.azime.input.core.diag.Diag`——**同时写 `/sdcard/Download/oime_diag.log`**
  （app 有 MANAGE_EXTERNAL_STORAGE；>128KB 自动清空），PC 端用 adb 直接读文件
- 所有埋点（Sym / Cursor / Ascii / Clip）已改走 Diag

## 1. 嵌入编码时候选行居中
- 只显示候选行（编码已内嵌）时，`verticalArrangement = Arrangement.Center` → 候选在工具栏里上下居中

## 2. 悬浮键盘真正浮在 App 之上（关键补漏）
- 19.20/19.21 只设了 `touchableRegion`，**没有改 `contentTopInsets`** → App 仍被键盘"让位"（被顶起/压扁），
  看起来不像悬浮
- 现在 `onComputeInsets` 里把 `contentTopInsets` / `visibleTopInsets` 报成**满屏高度** ⇒
  App 整屏铺开、键盘浮在其之上；触摸区仍限定为键盘矩形（其余穿透给 App）

# 轮19.23（0.9.36-oime vc46）：H 键英文不出 `_` 的真因（Compose 闭包过期）/ 悬浮可自由拖动

## 1. H 键英文不出 `_` —— 文件埋点抓到了铁证
```
22:11:13.876 [Sym] show   code=h ascii=true  list=[_]      ← 气泡里的列表过滤**正确**
22:11:14.173 [Sym] commit sym=—— ascii=false                ← 提交的却是中文那项、ascii 还是 false
```
**真因**：长按气泡的手势块是 `pointerInput(key.code, key.longClick, state.page)` ——
**`asciiMode` 不在 key 里** ⇒ 切换中英后手势闭包**不重建**，里面的 `state`/`longPressSymbols`
都是切换前的旧值（ascii=false、列表 `[——, _]`），所以提交回到 `——`。
（气泡本身是在 composition 里渲染的，读的是新列表 → 所以"看得见 `_`，点了却出 `——`"）

**修复**：按 Compose 处理过期闭包的标准做法——
- `val curState by rememberUpdatedState(state)` / `val curSymbols by rememberUpdatedState(longPressSymbols)`
- 手势块内 **6 处**读取（show 判定、拖动选号、提交取符号、size 判定）全部改读 `cur*`
- 并把 `state.asciiMode` 加进 `pointerInput` 的 key（双保险）

## 2. 悬浮键盘"被框在下半部分"不能自由拖
**真因**：IME 窗口是**按内容高度测量**的（WRAP_CONTENT），`window.setLayout(MATCH_PARENT, MATCH_PARENT)`
会被系统覆盖回来 ⇒ 窗口只有键盘那么高，拖动范围自然被限制在小半屏。
**修复**：让**内容自己占满屏幕高度**（`Modifier.height(screenHeightDp - 48dp)`）→ 窗口随之变高
→ 拖动范围＝整屏（配合已有的 `contentTopInsets` 满屏 + `touchableRegion` 限定键盘矩形，App 不会被顶起）

# 轮19.24（0.9.37-oime vc47）：十一项修正（手感/符号/剪贴板/精简）

1. **空格长按切中英后按键卡在"按下态"** —— 真因是**我上一轮引入的**：
   为修 H 键把 `state.asciiMode` 加进了 `pointerInput` 的 key，长按切换 ascii 时手势块被
   **中途取消重启**，抬起分支不再执行 ⇒ `pressing` 永远为 true。
   现在：从 key 里移除 `asciiMode`（`rememberUpdatedState` 已足够）+ 加 `LaunchedEffect(asciiMode)`
   复位 `pressing/longFired/longCancelled` 双保险。
2. **悬浮模式图标更换**：新增 `OimeIcons.floatKbd`（可移动键盘：圆角框 + 四向箭头），
   工具栏 `floatkbd` 映射到它（原来复用 pip）。
3. **全部符号加「中文符号」「英文符号」两类**，放最左（常用→中文→英文→引号→数学→箭头→货币…）。
4. **键面长按符号随中英自动切换**（对齐 trime2 `26键.lua` 的 `ascii = { long_click = ... }` 语义）：
   新增 `LongPressSymbolsAscii`（h→`_`、m→`:`、`,`→`!`、`.`→`?`、b/n 引号、k→ASCII 括号气泡），
   键面提示与长按气泡都按 `asciiMode` 取对应的那一套。
5. **编辑器：长按动作与长按符号合二为一**——只保留一个「长按」输入框
   （单个=直接执行；空格分隔多个=长按气泡），保存时同步写入 `longClick` + `popup`。
6. **打字粘滞感（性能）**：
   - 引擎调用改为**单线程串行**（`Dispatchers.Default.limitedParallelism(1)`）：原来多线程并发打 librime，
     而 librime 内部有全局锁 ⇒ 互相等待 + 顺序错乱 = 手感粘滞；与 trime2 的 RimeDispatcher 同思路
   - `updateFromResult` 内容无变化时**不发射新状态**（避免每次按键都整棵键盘重组）
   - 触感节流 **70ms → 40ms**（70ms 会在快速打字时丢振动，手感"不清脆"）
7. **剪贴板**：
   - ︙ 菜单改为**横向悬浮栏**，呼出时**覆盖面板的功能键区**（原来是 DropdownMenu 弹窗）
   - **长文本上屏不全**的真因：显示用的是 `clipText = text.take(80)`，**上屏也用它** ✗
     → 新增 `clipFull` 保存完整文本，显示仍用截断值，**上屏用完整文本**
8. **去除嵌入式编辑**（用户确认不要）：删掉设置项 + `applyInlineComposing` + 工具栏的 inline 分支，
   工具栏恢复恒定显示"输入码 + 候选"。
9. **设置 → 键盘：删掉「单手模式 / 悬浮模式 / 复制条」三个设置项**（功能保留，仍由工具栏工具切换）。
10. **去除「按键响应」设置项**（长按/连发/滑动阈值面板整段删除，阈值仍用默认值）。
11. 记录并推送（手机未连接，装机待指令）。

# 轮19.25（0.9.38-oime vc48）：复制条消亡回归修复 / K 键括号回归修复 / 编辑器提示精简 / 符号键图标

1. **复制条「点击上屏、左右划动都不能消亡」——19.24 引入的回归**：
   19.24 为了让长文本完整上屏，把上屏文本换成了 `clipFull`（完整长文本），
   但 `CommitClipboard` 里仍写 `lastCommittedClip = action.text`，
   而 `readClipboard` 的判重是 `text.take(80) == lastCommittedClip`
   ⇒ **长文本永远比不中** → 上屏后又被剪贴板回调塞回工具栏。
   修复：上屏/划动都统一记 `take(80)` 到 `lastCommittedClip` **和** `dismissedClip`，并清空 `clipFull`；
   划动阈值 40dp → **24dp**；另加**长按即消亡**（多一条可靠途径）。
2. **编辑器「按键」界面删掉「右上角提示（长按符号）」输入框**，只保留长按动作（保存时保持原值不变）。
3. **K 键长按 popup 中文只输出字面「括号」——也是 19.24 引入的回归**：
   19.24 重构 `longPressSymbolsFor()` 时漏了 K 键特判，
   于是中文走了 `LongPressSymbols['k'] = ["括号"]` 这个占位项（英文走了 ASCII 变体所以只有 3 对）。
   修复：`code == "k"` 时中文恒返回 `BracketPairs`（6 对），英文返回 6 对 ASCII 括号
   （`{} <> () [] "" ''`，与中文一一对应）。
4. **第四行首键图标跟随设置偏好**：偏好「26 键符号」显示符号网格图标、偏好「九宫格」显示数字键盘图标
   （切页逻辑 `ToggleSymbols` 本就走 `preferredPage()`，这次只补图标同步）。

# 轮19.26（0.9.39-oime vc49）：设置页二级菜单的紫底 → 统一浅灰

- 现象：二级页（悬浮窗等）的卡片是**淡紫底**，与主页面的浅灰不一致
- 真因：**M3 默认配色是淡紫色系**（`lightColorScheme()` 的 surface 家族全是紫调），
  卡片/容器会按不同角色取色（`surfaceContainerLow` / `surfaceVariant` / `surfaceContainerHighest`…），
  19.15 只覆盖了 `surfaceContainerLow` + `surfaceContainer` 两个角色 ⇒ 其余角色仍是紫的
- 修复（两层）：
  1. **主题全量覆盖中性色**：`background/surface/surfaceVariant/surfaceContainerLowest/Low/High/Highest/
     secondaryContainer/tertiaryContainer` 全部改成灰阶（浅色 #FFFFFF/#F1F1F2/#EAEAEC/#E4E4E7，
     深色 #1B1B1F/#26262A/#2C2C31/#323238）
  2. **设置页所有 Card 显式指定灰色**：新增 `grayCardColors()` 助手，
     把 SettingsActivity 里 **16 处** Card（含 `Card { Column { … } }` 这种同行写法）全部改为
     `Card(colors = grayCardColors())` —— 不再依赖 M3 默认取色
- 抽出的教训：**别依赖主题默认中性色**，要让 UI 一致就显式指定容器色

# 轮19.27（0.9.40-oime vc50）：复制条划动阈值可调 / 亮暗切换即时刷新系统底部栏

1. **复制条划动阈值做成设置项**：设置 → 键盘 → 「复制条划动阈值」滑杆（4~60dp）
   - 默认 24dp → **12dp**（用户实测 24dp 不够灵敏，划不到就不消亡）
   - 阈值纳入 `sizeSignature()`（改完下次弹键盘即生效）
2. **○ 菜单切亮/暗色后，系统底部栏不跟随**：真因是导航栏颜色只在 `onStartInputView` 设过一次
   - 新增 `applyWindowBarColors()`，**在切换动作里立即调用**，同时把 `onStartInputView` 里那行也统一走它
   - 顺带保证 `isNavigationBarContrastEnforced = false`（避免系统再叠一层对比色）

# 轮19.28（0.9.41-oime vc51）：复制条交互重做（按钮/长按消亡+震动）+ 修复「再次复制不显示」

1. **复制条按用户要求重做**：
   - 内容**靠左**排布（原来居中），右侧放一个 **✕ 消亡按钮**
   - **整条长按也可消亡**（`detectTapGestures(onLongPress)`，避免 `combinedClickable` 的 OptIn 噪音）
   - 两种消亡都**震动提示**（新增 `HapticsManager.Type.DISMISS`，固定 30ms，受总开关约束）
   - **废弃左右划动**（连带删掉 19.27 加的「复制条划动阈值」设置项）
   - 点内容仍 = 上屏
2. **修复「复制一条后，再次复制不显示到工具栏」**：
   - 真因：`readClipboard()` 的抑制规则（跳过 `lastCommittedClip` / `dismissedClip`）**对"真·复制事件"也生效**，
     于是复制 A → 上屏/消亡后，**再复制一次 A（或内容相同）就被判定重复而静默丢弃**
   - 修复：`readClipboard(fromUserCopy)` —— 剪贴板监听回调（真·复制）传 `true`，**永远显示**；
     只有 `onStartInputView` 的复读才应用抑制规则（保持"上屏后不复活"的原意）

# 轮19.29（0.9.42-oime vc52）：复制条只留按钮 / 第四行宽度 / 眼睛表情扩充 / 豆包 API 调研报告

1. **复制条长按消亡实测无效 → 废弃**，只保留右侧 **✕ 按钮**（点击消亡 + 震动）
2. 设置 → 键盘 的「复制条划动阈值」确认已随划动废弃一并移除 ✓
3. **第四行宽度**：空格 4.3 → **4.5**，回车 2.0 → **1.8**（Σ 仍 10.0，列对齐不变）；
   **主键盘 + 符号键盘同步**（横屏 qwertyLand 保持自己的 10.9 体系）
4. **O 圆环眼睛动画扩充到 10 套**（原 5 套：眨眼 / 连眨两下 / 左右看 / 上下看 / 眯眼）：
   新增 **惊讶**（瞳孔放大 1.55× + 上抬）、**困倦**（半闭下沉 → 惊醒）、
   **笑眼**（眼睛画成上弧 ∩）、**转圈看**（上→右→下→左）、**生气**（瞳孔缩小 + 下压 + 抖动）
   - 新增状态 `eyePupil`（瞳孔缩放）/ `eyeArc`（弧线绘制），Canvas 里按状态取形
5. **新增调研报告 `DOUBAO_ASR_REPORT.md`**：豆包输入法本身无开放 API；
   但火山引擎 ASR 有 **OpenAI 兼容通道（边缘大模型网关）→ 我们只需加「PCM 直传」约 20 行**；
   原生流式（WS 二进制帧 + gzip 分包）需 300~500 行适配 + OkHttp 依赖

# 轮19.30（0.9.43-oime vc53）：空格长按回归 / 空格文本可微调 / 主题分组配色+调色板 / 输入框类型自动切页

1. **长按空格切中英会多输入一个空格** —— **19.24 引入的回归**：
   那轮为修"按键卡在按下态"加了 `LaunchedEffect(asciiMode)`，里面**把 `longFired` 也复位**了；
   而抬手判定是 `!longFired -> onKeyAction(...)` ⇒ 长按切完中英抬手时按普通空格处理 ✗
   → 现在只复位 `pressing`，绝不动 `longFired`
2. **空格自定义文本只能居中**：
   - 真因：渲染时 `custom.trim()` **把前后空格全 trim 掉了** → 空格无法用于微调位置
   - 现在**原样返回**（保留前后空格）+ 新增**位置滑杆**（设置 → 键盘 → 「文本位置」-80~80dp，默认 0 居中）
3. **主题与配色扩展**：
   - **分组**：字母键（26 字母 + 逗号 + 句号，共享 `keyBg`）/ 功能键（Shift·符号·退格，共享 `funcKeyBg`）/
     强调键（回车 + 高亮）
   - **亮色自定义 + 暗色自定义两套**（原来只有一套共用的 RGB 滑杆）
   - **取色改为「调色板 + 十六进制 + 透明度」弹窗**：24 色常用板、`#AARRGGBB` 输入、透明度滑杆、
     实时预览、恢复默认
   - 配色纳入 `sizeSignature()`（下次弹键盘生效）
4. **按输入框类型自动切页**（设置 → 键盘 可关）：
   - 数字 / 电话 / 日期框 → **九宫格数字页**（顺手切英文，避免上屏变候选）
   - 密码 / 邮箱 / 网址（`TYPE_TEXT_VARIATION_*`）→ **切英文（ascii）+ 主键盘**
   - 其它 → 回主键盘；带 `AutoPage` 埋点便于真机核对

# 轮19.31（0.9.44-oime vc54）：界面风格（Material / Miuix）+ 强调色原色

## 问题：主题配色"不能完整呈现原色"
两个原因叠加：
1. **我们自己的锅（主因）**：`accentKeyBg = accent.copy(alpha = 0.28/0.35).compositeOver(键底色)`
   —— 强调色被**半透明混合**冲淡了；设置页的 `primaryContainer` 又直接取这个值 ⇒ 颜色永远不还原 ✗
2. **M3 的容器色角色**：Material 3 的 `primaryContainer / surfaceContainer*` 带色调层，
   即使给了纯色也会被派生出一层"容器色"观感

## 新增：界面风格（设置 → 主题与配色）
- **Material**（默认）：保持现有观感——强调色柔和混合 + 中性灰卡片
- **Miuix**：MIUI 风格扁平色阶 —— 页面浅灰 `#F2F3F5` / 卡片**纯白** / 暗色 `#191919` + `#2C2C2E`，
  **键圆角 +6dp**（更圆）；强调色走原色
- 风格 + 原色都纳入 `sizeSignature()` ⇒ 重开键盘即生效

## 新增：强调色用原色（开关）
- 关闭：强调色与键底做半透明混合（柔和，但颜色变淡）
- 开启：**严格按所选颜色呈现**（突出"自定义色还原"）
- 另外：**开了「亮色/暗色自定义配色」时自动按原色**（自定义就应该原样呈现）

# 轮19.32（0.9.45-oime vc55）：导航条跟随键盘底色 / 滑杆样式统一 / 原色默认开 / Miuix 加深

1. **切 Miuix 后键盘下方系统增高区没跟随（割裂感）**：
   真因：`navBarColorInt()` 是**写死的两个色值**（#1B1D1F / #E9EBEE），跟外观无关 ✗
   → 改为**直接取键盘底色** `buildKeyboardColors(dark).bg` ⇒ 风格/亮暗/外观任何调整都一起跟随
2. **取色弹窗的透明度滑杆**改用设置内同款 **`XimeSlider`**（原来用 Material `Slider`，样式不一致）
3. **后续 a：强调色「原色」默认改为开启** —— "选了什么色就显示什么色"；
   柔和混合（冲淡颜色）改为需主动关闭原色才会出现
4. **后续 b：Miuix 加深**：
   - 设置页卡片圆角 **16dp**（Material 12dp）
   - 键盘**行距 +2dp**（更扁平、更大间隔）
   - （已有）MIUI 灰阶、卡片纯白、键圆角 +6dp

# 轮19.33（0.9.46-oime vc56）：界面风格**只改配色**，不再动几何参数

- 用户反馈：切到 Miuix 后"没有跟随我调整过的键高、行距"，像是用了默认值
- 真因：19.31/19.32 我给 Miuix **强加了版式差异**——键圆角 +6dp、行距 +2dp、设置页卡片 16dp；
  这些覆盖了用户自己调好的值 ✗（键高本身没被改，但行距变化会连带改变整体高度观感）
- 修复：**风格切换只影响配色**，几何参数（键高/行距/列距/圆角/字号/工具栏高度）一律原样沿用用户设置
  - 去掉：键圆角 +6dp、行距 +2dp、设置页卡片 16dp
  - Miuix 的"风格感"改为**纯配色层次**：键盘底 `#F2F3F5` + 工具栏/键面**纯白**（暗色 `#191919` + 工具栏 `#1F1F1F` + 键 `#2C2C2E`）

# 轮19.34（0.9.47-oime vc57）：原色下选中文字修复 / 空格上滑切中英 / ○菜单切输入法 / 悬浮窗增强 / 打字音效 / 拆字与字体兜底 / 风格调研报告

1. **强调色原色时"方案开关"等文字看不见**（截图）：真因是选中卡片
   `background = accentKeyBg（纯强调色）` 而 `color = accentActive`（**也是强调色**）⇒ 蓝底蓝字 ✗
   → 全部 5 处统一改为 `accentKeyText`（on-accent），开关卡片的副标题（开关名）同样处理
2. **空格长按切中英 → 改为上滑切中英**：`space()` 去掉 `longClick`，改 `swipeUp = toggle_ascii`
3. **○ 菜单新增「切换输入法」**：调 `InputMethodManager.showInputMethodPicker()`
4. **悬浮窗增强**：背景色可自定义（取色弹窗，0=跟随主题）· 首选候选加强调底色 ·
   候选数量可调（1~9）· 候选横向/竖向可切换
5. **打字音效**：新增 `core/sound/SoundManager`（SoundPool）——
   总开关 + **外置文件夹**（默认 `/sdcard/Documents/Oime/sounds`）+ 目录内文件选择 + 试听 + 音量；
   关闭时热路径零开销；按键处已挂钩
6. **拆字支持 + 字体兜底**：
   - 字体链**追加系统字体兜底**（`DeviceFontFamilyName("sans-serif")`）——
     用户自定义字体常缺部首/部件字形（⺮ 龸 亻 等），原来只含用户字体 ⇒ 豆腐块
   - 「更多候选」显示 `candidate.comment`（RIME 的拆字/编码，开 chaifen 开关后有值）
7. **界面风格调研报告** → `UI_STYLE_RESEARCH.md`：提出 token 表骨架 + 8 种候选风格（含成本/风险）
   + 落地顺序建议（Miuix 深化 → Material You 动态取色 → 毛玻璃 → 胶囊/One UI → 小众风格），
   并重申「风格只改视觉、不动用户几何」的约定

# 轮19.35/36（0.9.48-oime vc58）：6 种界面风格（token 表）+ 振动跨页同步 + 耗电复核

## 一、耗电复核（手机侧归一化报告：2026-09-15 3.99h / 2026-09-16 22.35h，均 0.9.46）

| 指标（归一化） | vc39 基线（9-12） | **vc56（9-16）** | 变化 |
|---|---|---|---|
| UID 归因耗电 | 4.34 mAh/h | **1.39 mAh/h** | **↓68%** |
| CPU 时间 | 55.5 s/h | **44.8 s/h** | **↓19%** |
| 内存 RSS | 291 MB | **229 MB** | **↓21%** |
| 振动次数 | 387 次/h | 337 次/h | ≈持平（用户打字量大 + **明确要求保留振动**）|
| Fg Service 占比 | 5.8% | 6.5% | 持平（系统带账）|
| Background 占比 | 93% | 93.4% | 持平（默认输入法固有）|

**结论**：耗电优化三项（引擎懒加载 / 振动节流 / 触感统一）**确实见效**：归一化耗电 ↓68%、CPU/h ↓19%、RSS ↓21%。
两条系统带账项保持不变，与 9-12 的核实结论一致。**用户明确表示振动不用再优化**（喜欢振动），该待办撤除。

## 二、界面风格：6 种（token 表驱动）

- **新增 `core/theme/UiStyleTokens.kt`**：`UiStyle` 枚举 + `StyleTokens`（配色/边框/阴影/字重/等宽/动态取色）
  + `UiStyles.ofCurrent()`；新增 `core/theme/DynamicPalette.kt`（Material You 壁纸取色，API 31+，带缓存与回退）
- **6 种风格**：Material（默认）· Miuix（MIUI 扁平）· **One UI**（大圆角+柔和阴影+半粗字重）·
  **iOS / 胶囊**（大圆角、无描边）· **Nothing OS**（单色极简 + 等宽字体）· **Material You**（壁纸动态取色）
- **`buildKeyboardColors()` 改为读 token**：自定义配色仍然优先；`KeyboardColors` 增加
  `keyBorder / keyShadowDp / keyBold / monoFont` 四个视觉 token 并在键面渲染中生效
- **几何策略（19.33 教训的正式解法）**：新增「**几何也跟随风格**」开关，**默认关**——
  键高/行距/列距/圆角一律沿用用户设置；开启后才用风格建议圆角
- 设置 → 主题与配色：3 列风格选择器 + 「几何也跟随风格」+ 「强调色用原色」

## 三、振动跨页同步（用户反馈：九宫格/符号页振动与主键盘不同）

真因：**振动触发时机不一致**——
- 主键盘（有手势的键）在 `awaitEachGesture` 的 **down 分支**振动 ⇒ **按下即振** ✓
- 九宫格/符号页的键没有手势，振动挂在 `clickable {}` 里 ⇒ **抬手才振** ✗（体感延迟、发闷）
- 组合中的候选键同样挂在 clickable 上 ✗

修法：无手势键改用交互源 pressed 状态（`LaunchedEffect(clickPressed)`）在**按下瞬间**振；
候选键改用 `awaitPointerEventScope { awaitFirstDown() }` 同样按下即振。
⇒ 三个页面的振动时点、时长、音量**完全统一**（用户要求保留振动，只对齐时机）。

# 轮19.37（0.9.48-oime vc58 修补）：CI「Upload APK」失败 → 产物配额

- 现象：CI 的 **Build with Gradle 成功**，但 **Upload APK 失败**，产物列表为空（重跑仍失败）
- 真因：**GitHub Actions 产物存储配额**——仓库累积了 **99 个 app-debug 产物 = 2486 MB**，
  远超免费账号的 **500MB** 产物额度 ⇒ 上传被拒 ✗（构建没问题，只是传不上去）
- 处置：
  1. **清理历史产物**（保留最新 2 个）：释放 **2420 MB** ⇒ 剩余 65MB
  2. **工作流加 `retention-days: 3`**（+ `if-no-files-found: error`）⇒ 产物 3 天自动过期，不再堆积
- 教训：**CI 产物不设保留期，高频构建会把配额撑爆**（我们一天能推 6+ 次、单包 33MB）。

# 轮19.38（0.9.49-oime vc59）：密码框输完不恢复中文（19.30 自动切页的真 bug）

- 现象：登录输密码时自动切了英文（/ 数字九宫格），**回到普通打字界面后不恢复中文** ✗
- 真因（我 19.30 写的）：`applyAutoPageForEditor()` 里 **`setOption("ascii_mode", true)` 只设过 true，
  从来没有设回 false** ⇒ 自动切过去就回不来了
- 修法：引入 `autoAsciiApplied` 标记——
  · 密码/邮箱/数字类 → 自动切英文，并**记住"是我们自动切的"**
  · 回到普通文本框且该英文是自动切来的 → **恢复中文（ascii_mode=false）**，清除标记
  · **用户自己手动切的英文不受影响**（只恢复自动切的那一次，避免与用户意图打架）
- 另外补 hook：同一窗口内切换输入框时 `onStartInputView` 不一定重跑 → 增加 `onStartInput` 兜底调用
- 埋点增强：`AutoPage` 日志增加 `restore=` / `changed=` 便于真机核对

# 轮19.39（CI 维护）：GitHub Actions 升到最新版（node24），消除 Node.js 20 弃用警告

- 现象：CI 每次告警 `Node.js 20 is deprecated. ... actions/checkout@v4, actions/setup-java@v4,
  actions/upload-artifact@v4`（被强制跑在 Node 24 上）
- 处置：工作流 `android.yml` 内 4 处全部升级到**最新大版本**（已逐个核对 `action.yml` 的 `using:` 均为 **node24**）：
  · `actions/checkout@v4` → **@v7**
  · `actions/setup-java@v4` → **@v6**
  · `actions/upload-artifact@v4` → **@v7**（2 处：APK + build-reports）
- 踩坑记录：先升到 **v5 是不够的**——`upload-artifact@v5` 的 `action.yml` 仍是 `using: node20` ⇒ 警告照旧；
  必须升到 **v7**（2026-04 发布，node24）才消除 ✓
- 另：Xime.az 仓库**按用户要求不再改动**（该仓库后续不再更新）

# 轮19.40（CI 维护）：产物配额被卡时的 Release 附件兜底

- 现象：09-16 清理历史产物后（账号实际用量 **156MB** / 免费额度 500MB），
  构建仍持续在「Upload APK」失败，原文一字未变：
  `##[error]Failed to CreateArtifact: Artifact storage quota has been hit.
   Unable to upload any new artifacts. Usage is recalculated every 6-12 hours.`
- 判定：**GitHub 的用量计数器没重算**（不是我们真的超了）——
  证据：API 实测 Oime 65.6MB + Xime.az 90.3MB = **155.9MB < 500MB**；
  且加 `retention-days: 3` 后仍然失败（说明卡的不是保留期，是**新建产物**这个动作本身）
- 修法（`android.yml`）：
  1. 顶部加 `permissions: contents: write`（仓库默认 workflow 权限是 **read**，不显式声明发布不了 Release）
  2. 「Upload APK」加 `id: upload_apk` + `continue-on-error: true`（配额卡住不再让整轮 CI 变红）
  3. 新增兜底步骤「Publish APK to Release (fallback)」——
     `if: steps.upload_apk.outcome == 'failure'` 时才跑，用 `gh release create` 把 APK 作为
     **Release 附件**（`oime-<versionName>-vc<versionCode>.apk`）发布
- 关键认知：**Release 附件不计入 Actions 产物配额** ⇒ 配额再卡也不丢包；配额恢复后兜底步骤自动不跑
- 同轮顺带落地了 19.39 的 action 版本升级（checkout@v7 / setup-java@v6 / upload-artifact@v7 ×2）
  ——上一轮只推了 v5，`using: node20` 警告仍在；本次推送的才是真正的 v7


# 轮19.41：撤掉 CI 的 Release 兜底（用户要求：未正式发版前不走 Releases）

- 背景：19.40 曾加「Publish APK to Release (fallback)」绕开产物配额（配额 6~12h 才重算）
- 用户要求：**未正式发版前不要用 Releases**，只在正式发版时才走 → 已撤掉该步骤
- 具体改动（`.github/workflows/android.yml`）：
  · 删除 `Publish APK to Release (fallback)` 步骤
  · `Upload APK` 去掉 `continue-on-error: true` 与 `id`（恢复"失败即失败"，问题透明）
  · 去掉为 Release 加的 `permissions: contents: write`（恢复最小权限）
- 同时清理：删除 `ci-133` / `ci-134` 两条 CI Release（含 tag）→ Releases 归零，只等正式发版
- 产物通道回到纯 Actions artifact（`retention-days: 3` 保留，防配额再堆积）

# 轮19.42（0.9.50-oime vc60）：三个真因修复 + 风格选择改下拉

1. **空格上滑切中英"没生效、仍是长按切换"**：
   内置数据其实已改对（`space()` 的 longClick=null + swipeUp=toggle_ascii），
   但**设备上已保存的自定义布局 JSON** 里仍是旧的 `longClick = "toggle_ascii"`，覆盖了内置定义 ✗
   → 新增 `migrateSpaceGesture()`：加载布局时做一次无副作用迁移
   （空格键若 longClick 是 toggle_ascii ⇒ 搬到 swipeUp，清 longClick）✓
2. **Nothing OS 下 O 圆环看不见**：
   圆环颜色是**写死的 `Color.White`** ⇒ 白环压白底 ✗
   → 改为 `c.text.copy(alpha = breathAlpha)`（浅色主题=深色环，深色主题=浅色环）✓
3. **Material You 下按键不可见、按空白无反应**：
   `DynamicPalette` 里用了 **`color.value.toLong()`**（Compose 的 **packed ULong**，含色彩空间位），
   而 `Color(Long)` 构造按 **ARGB** 解释 ⇒ 取到垃圾色值（透明/错色）✗
   → 全部改用 **`toArgb()` 并强制不透明**（新增 `opaque()` 助手，13 处）✓
4. **界面风格选择改为下拉栏**（原 3 列平铺改掉）：点一行展开 6 个风格，当前项带 ✓

# 轮19.43（0.9.51-oime vc61）：空格连发 / 候选注释居上 / 三套新布局 / 去预设置+内置功能清单

1. **空格长按 = 连续输入空格**（用户要 PC 手感）：渲染层把 `KeyType.SPACE` 一并纳入 `autoRepeat`
   （原来只有退格连发）⇒ 长按空格像电脑空格键一样连续出空格；上滑仍切中英 ✓
2. **更多候选面板**：注释（拆字/拼音）从候选**右侧**移到**上方**，并加**随文字长度伸缩**的胶囊背景
   （原来右侧被裁切，如「（禾口」✂）
3. **新增三种主键盘布局** + **去除 lua/keyboards 读取**：
   · **九键**（T9 数字键盘，键面标字母组）/ **十四键** / **十七键**（字母分组：点=首字母、长按/上滑=次字母）
   · 注册进 `builtinByName` + `builtinLayoutNames()`，编辑器布局列表随之显示；**默认仍是 26 键**
   · Lua 编辑器里「布局放 lua/keyboards/」的说法去掉（明确不再从该目录读取）
4. **去除「预设置（preset_keys）」功能**：
   · 不再生成 `preset_keys.lua` 模板；`resolveAction` 去掉预设表查表
   · **动作改为「内置功能键值」**，命名对齐 RIME：`escape/clear/return/prior/next/ascii_mode` 等别名 +
     新增 `switch_ime` / `clipboard` / `menu` / `deploy`；大小写、`-`、`_` 容错
   · **键盘编辑器新增「内置功能键值清单」**（按编辑/上屏/中英/光标/翻页/组合/页面/内置分组，含中文含义）

# 轮19.44：CI 产物配额问题的**真正大头**找到了 —— Gradle 缓存

- 现象：清理 artifacts 后 `Upload APK` 仍报 `Artifact storage quota has been hit`
- 复查：Actions 存储 = **artifacts + caches**，而 `setup-java` 的 `cache: gradle`
  积累了 **15 个缓存 × 763MB ≈ 10.4 GB**（每次依赖哈希变化就新建一个，旧的不过期）✗✗
  —— artifacts 只有 156MB，缓存才是元凶
- 处置：
  1. **删除 14 个旧缓存**（保留最新 1 个）：释放 **10046 MB**
  2. 工作流**去掉 `cache: gradle`**（单个 Gradle 缓存 600~760MB > 500MB 额度本身，
     留着它产物通道永远被顶掉）；代价是每次构建多 1~2 分钟
- 备注：GitHub 用量计数 6~12h 才重算 ⇒ 改完仍需等一次重算，产物通道才恢复

# 轮19.45（根本解决）：仓库改 **public** ⇒ GitHub Actions 分钟与存储全免费

- 查证官方计费文档（docs.github.com/billing/concepts/product-billing/github-actions）：
  · 「GitHub Actions usage is **free** for self-hosted runners and for **public repositories**
    that use standard GitHub-hosted runners」
  · 「The use of standard GitHub-hosted runners is free: **In public repositories** / Pages / Dependabot」
  · 额度表（Free: artifacts 500MB · 2000 分钟 · **cache 10GB/仓库**）适用于**私有仓库**
  · 「GitHub updates your artifact storage usage within **6 to 12 hours**」← 解释了清理后仍报配额的滞后
  · **cache 与 artifacts 是两套独立额度** ⇒ 我们同时超了两项（artifacts 2.4GB、cache 10.4GB）
- 实证：`AZNixl/Xime.az` 是 public → 从未被卡；`AZNixl/Oime` 是 private → 一直被卡 ✓
- 处置：
  1. **仓库私有一律改公开**（`PATCH /repos/AZNixl/Oime {private:false}`）⇒ 分钟 + 存储免费，配额问题消失
     - 公开前安全检查：无 token/密钥/keystore/local.properties 入库；无 .log 入库；许可证 GPL-3.0 ✓
  2. 工作流**恢复 `cache: gradle`**（公开仓库存储免费，构建快 1~2 分钟）
  3. 产物保留期仍保持 `retention-days: 3`（好习惯，避免无限堆积）
- 附带效果：**产物通道不再受配额限制**，APK 可正常下载 ✓

# 轮19.46（0.9.52-oime vc62）：内置默认音效 + 不再创建 lua 目录 + README 修订

1. **内置默认打字音效**：把 `click.ogg`（Ogg，9.8KB）打进 APK 的 `assets/sounds/`，
   安装后由 `StorageManager` 释放到 **`Documents/Oime/sounds/`**（该目录同时改为**安装时自动创建**）；
   已存在的同名文件**不覆盖**，用户替换后不会被还原 ⇒ 打开音效开关即可出声 ✓
2. **不再创建 `Documents/Oime/lua/`**：预设置（preset_keys）已去除，该目录没有保留必要 ⇒
   去掉 `luaDir.mkdirs()`（Lua 编辑器保留，但不再随安装建目录；README 相应措辞已改）
3. **README 修订**（用户审阅意见）：
   · 修 `○ 圆环` 段落被截断 + `**` 未闭合（会把后文整段渲染成粗体）
   · 修「参考项目」列表断行（trime2 少 `-`），并补回 **PiliPlus / KernelSU**（代码与设计仍在用）
   · 修「视觉模型与语音识别的取舍」这句错误表述（DOUBAO 报告只讲 **ASR**）
   · 新增 **隐私说明**（默认不联网 / 剪贴板本地 / 语音可离线 / 日志不外发 / QUERY_ALL_PACKAGES 用途）
   · 音效一节写明目录路径、自动创建与内置默认音效；外部目录树同步
   · 构建一节补充「开发期 APK 从 Actions 产物取（需登录），正式版再上 Releases」
   · 文字规范：直引号改中文引号；「Compose 自绘键盘」改为「Compose 声明式键盘（键面与圆环手绘）」
4. 踩坑：**Kotlin 块注释可嵌套** —— 注释里写 `assets/sounds/*` 会因 `/*` 打开嵌套注释而「Unclosed comment」，
   必须避开在注释中出现 `/*` 字样

# 轮19.47（0.9.53-oime vc63）：去掉三种新布局 / 设置页清理 / ○ 菜单统一振动

1. **撤销「九键 / 十四键 / 十七键」**（用户决定：不做，留给 fork 者自己扩展）
   删除三个布局定义与只服务于它们的 `dualLetterKey` / `t9Key` helper，
   `builtinByName` 与 `builtinLayoutNames()` 还原为 qwerty / symbols / numpad；
   编辑器布局列表随之回到三项；**默认仍是 26 键**
2. **设置主页去掉「预设置」入口**（`preset_keys.lua` 的编辑器入口）——该功能彻底移除
3. **设置主页状态大方块改版**：
   · 去掉「○输入法」标题行，只留**运行状态**（12 → **18sp SemiBold**）与**方案**（12 → 14sp）
   · 右缘新增**四分之一圆环**背景装饰：**已启用 = 白色**（α 0.38）、**未启用 = 黑色**（α 0.30）
   · 未启用时整块仍是灰色底、点击可跳系统启用页（沿用原行为）
   · 顺带把状态圆点 8dp → 11dp，与大字号配套
4. **○ 菜单内所有按键统一振动**：父级菜单项 + 子级悬浮栏（方案开关 / 定制工具栏 / 方案组 / 输入方案）
   + 关闭与「打开设置」按钮，共 **10 处** `clickable` 补上 `HapticsManager.press()`

# 轮19.48（0.9.54-oime vc64）：更多候选改为「按内容自适应宽度 + 自动换行」

- 现象：用户反馈「动态长度没有生效，仍是固定长度」（截图里注释胶囊几乎占满整格）
- 真因：**不是背景被拉伸**，而是**单元格等宽**（原实现 `chunked(5)` + `weight(1f)` 的等宽网格）——
  注释（拆字/编码）很长时被单元格**裁掉**，于是每格看起来一样宽 ⇒ 像"固定长度"
- 修法：候选网格改为 **`FlowRow`**（`ExperimentalLayoutApi`）：
  · 单元格**由内容决定宽度**（`padding` + 背景，无 `weight`/`fillMaxSize`/固定 height）
  · 注释胶囊**自然贴合文字**，长注释**完整显示**，一行放不下自动换行
  · 顺序与序号（1..9 前缀）不变，点击上屏不变

# 轮19.49（0.9.55-oime vc65）：编辑器配色跟随主题 / 去 + 号 / 清理 preset_keys 遗留

1. **键盘布局编辑器（及子级页）配色不跟随**：
   真因：它用的是**裸 `MaterialTheme {}`**（M3 默认 → 淡紫 + 紫强调色），
   而设置页里那套"整套中性色覆盖成灰阶 + 强调色跟随回车键"的逻辑只写在 SettingsActivity 内部 ✗
   ⇒ 抽出**共用主题 `ui/theme/OimeTheme.kt`**（与设置页同一套规则），
   编辑器 / 字体管理 / Lua 编辑器三个 Activity 全部换用它 ✓ 之后配色与设置页完全一致
2. **去掉编辑器右下角的 + 号**（`FloatingActionButton` 新建布局入口）
3. **清理 preset_keys 遗留**（功能已移除，注释还在会误导）：
   · 编辑器可见提示：「动作值兼容 trime2 preset_keys」→「动作值用内置功能键值（见下方清单）」
   · 编辑器 KDoc、`KeyboardLayout` KDoc、`LuaScriptManager` KDoc 同步改写
   · 顺手删掉**已是死代码**的预设表解析（`PresetEntry` / `presetEntries` / `parseEntries` /
     `getEntries` / `getKeyAction`），`loadScript()` 简化为"只加载用户脚本、不参与动作解析"

# 轮19.49b（0.9.56-oime vc66）：preset_keys 遗留文案彻底清零

继续清掉第一轮漏掉的**用户可见文案**：
- `StorageManager.getLuaScriptFile()`：文件名 `preset_keys.lua` → **`script.lua`**（预设表已移除，改通用名）
- `OimeIcons.kt` 图标分组注释：「预设置（Lua 代码）」→「Lua 代码」
- `KeyboardScreen.kt` 动作分组注释：「扩展动作（preset_keys identifier / 手势）」→「（内置功能键值 / 手势）」
- `LuaEditorActivity`：KDoc、对话框标题「预设置 (preset_keys.lua)」→「Lua 脚本 (script.lua)」、
  空文本占位、帮助弹窗标题「预设置语法说明」→「说明」，并把「用途 / 动作取值优先级」两段说明
  改写成当前真实语义（动作走内置功能键值），删除已无意义的 `preset_keys` 表「完整示例」段
- 复查：`grep preset_keys|预设置` 现在只剩「已移除 / 已去除」这类**历史沿革说明** ✓

# 轮19.50（0.9.57-oime vc67）：编辑器铅笔色 / schemas 目录改名 / 方案导入结构统一

1. **编辑器选中行的铅笔看不见**：铅笔 tint 用 `primary`（强调色），而选中行底色是 `primaryContainer`
   ⇒ 蓝压蓝 ✗（用户截图）。改为**选中时用 `onPrimaryContainer`**；顺带把方案名/副标题也改成 on 色
   （原来 `onSurface` / `onSurfaceVariant` 压强调色底同样发暗）
2. **目录改名 `Documents/Oime/schema/` → `schemas/`**（用户打漏了一个 s）：
   - 常量改为 `schemas`，并新增**自动迁移**：启动时把旧 `schema/` 下的子目录搬进 `schemas/`，
     旧目录清空后删除（已导入的方案组不必重新导入）
   - README 目录树、RimeManager / SettingsActivity 的注释同步更新
3. **方案导入结构统一**（用户要求）：
   - **去掉「保持原名」** ⇒ 改为**导入前先命名文件夹**（对话框必填，重名/非法字符会拦下）
   - **解压后拍平一层**：压缩包内是「文件夹/方案文件」还是直接「方案文件」，
     结果恒为 `schemas/<用户命名>/<方案文件>`（`flattenSingleTopFolder`，连续单目录链也会收敛）
   - 导入卡片下方新增说明：「也可以不用导入：用文件管理器把方案文件直接复制到
     Documents/Oime/schemas/ 下新建的文件夹里即可」

# 轮19.51（0.9.58-oime vc68）：Lua 子系统整体移除

- **删除**：`LuaEditorActivity`（19.47 起已无入口，是死界面）、`LuaScriptManager`、
  `luaj-jse` 依赖（app/build.gradle.kts）、`StorageManager.luaDir` / `getLuaScriptFile()`、
  Manifest 里的 Activity 声明、SettingsActivity 的 `onEditLuaScript` 参数与传参
- **保留并改名**：动作解析 → **`core/action/ActionResolver.kt`**
  （`resolveAction` / `ResolvedAction`：内置功能键值 + 字面文本；键盘与 Service 都在调，**不能删**）
- 引用点同步：AZimeService（导入 + 去掉 `loadScript()` 调用）、KeyboardScreen（导入 + 全限定名）
- 踩坑：改名后仍残留两处**旧包路径引用**（`core.lua.ResolvedAction` 的 import 与一处全限定名）
  ⇒ 编译报 Unresolved reference，按包路径全局搜一遍才清干净
- README：「Lua 脚本」条目、技术栈里的 luaj、参考项目里的"Lua 脚本"字样一并去掉

# 轮19.52（0.9.59-oime vc69）：气泡文字可见 / 键面提示间距 / 编辑器说明改弹窗 + 4 个新动作 / 键盘左右边距

1. **长按气泡里看不到字**（用户截图 = 一整块蓝色）：
   真因同前三次——选中项 `color = accentActive` 压在 `background = accentKeyBg`（同色）上 ✗
   ⇒ 改用 **`accentKeyText`**（on 色）。这是"压在强调色上却用强调色"的第 4 次，规则已写进阶段报告
2. **键面提示与主字重合**（Q 的 1/1、A 的 全选、X/C/V 的 剪切… 与字母重叠）：
   · 提示边距加大：上/下 3 → **6dp**，左/右 5 → **7dp**；提示字号 9 → **8sp** + `maxLines=1`
   · **带提示的键主字缩小 15%**（四向/长按提示存在时），给提示让位
3. **按键编辑器**：
   · 动作说明**移到独立弹窗**（原来内联一大段占满屏幕）⇒ 一行提示 + 「动作说明」按钮
   · **默认长按动作写入输入框**：原来只有自定义过的键才有值（"一部分写一部分没得"）⇒
     空值时用内置长按符号表（空格连接）预填
   · 说明里补上**成对符号写法**：`（）{Left}` / `(){Left}` —— `{Left}` = 上屏后光标左移一位
     （K 键长按默认就是成对括号，光标落在中间）
   · **新增 4 个内置动作**：`Date` 日期（2026-09-18）· `Time` 时间（08:30）·
     `ChineseDate` 农历（二〇二六年八月廿八）· `RepeatCommit` 重复上一次上屏内容
     —— 农历用新增的 `utils/LunarCalendar.kt`（1900–2049 表 + 标准换算，含闰月与"春节前归上一农历年"）
4. **键盘设置**：去掉「手势提示位置」整组（气泡水平偏移 / 垂直余量 / 四向预览位置）；
   新增 **「左右边距」**（0–48dp，默认 0）—— 曲面屏可把键盘两侧往中间收

# 轮19.53（0.9.60-oime vc70）：提示位置改为设置可调（撤回主字缩小）

- **撤回** 19.52 的「带提示时主字缩小 15%」——用户明确要求：不要动文本大小，只调提示位置 ✗
- 键面提示位置改为**设置里可调**（新卡片「提示位置微调」，5 组 × X/Y 共 10 条滑杆）：
  · 上滑提示 · X / Y（向里，0–60）· 下滑提示 · X / Y · 左滑提示 · X（向里）/ Y（向下，±40）
  · 右滑提示 · X（向里）/ Y（向下）· 长按符号 · X（向里）/ Y（向下）
  · 约定：上/下 的 Y 与 左/右 的 X 都是「从该侧边缘往里」；左/右 与长按的 Y 正数向下（用来躲开主字）
  · 渲染改为 `align(...) + offset(x, y)`（原来用固定 padding，写死不可调）
  · 默认值 = 19.52 调过的那组（上/下向里 5、左/右向里 4、左侧 Y 7、右侧 Y 7、长按向里 4/5）
- 说明文案里注明：调好数值告诉我，可以把它们固化成默认值

# 轮19.54（0.9.61-oime vc71）：编辑器保存热重载 + 提示偏移放宽到 ±80dp

1. **键盘编辑器「保存」后不生效**（用户：保存了新符号但用不上）：
   真因——`saveLayout()` 只更新内存与文件，**键盘 UI 没有任何东西触发重读** ✗
   ⇒ 新增 **`KeyboardManager.layoutRev()`**（用 `mutableIntStateOf` 做成 Compose 可观察状态）：
   · `saveLayout` / `deleteLayout` / `setActiveMainLocked` 三处变更都 `bumpLayoutRev()` ✓
   · `KeyboardScreen` 里 `val layoutRev = KeyboardManager.layoutRev()` +
     `remember(layoutRev, state.page) { … }` ⇒ 保存后**正在显示的键盘自动重组**（保存即热重载）✓
   · 编辑器保存后 Toast「已保存并热重载」✓
2. **提示位置偏移全部放宽到 ±80dp**（原来上/下 Y 与左/右 X 是 0–60、其余 ±40，调不到合适位置）：
   10 条滑杆统一 −80…+80，`KeyboardManager` 的 clamp 同步放宽 ✓

# 轮19.55（0.9.62-oime vc72）：一批交互修复与新增（7 项）

1. **四向滑动动作编辑后不即时生效**：手势块 `pointerInput(key.code, key.longClick, state.page)` 的 key
   太窄 ⇒ 编辑了 swipeUp/Down/Left/Right 后手势不重启，用的还是旧动作（与 H 键 `_` 同类问题）。
   改为 `pointerInput(key, state.page)`（整个 key 数据类参与比较）✓
2. **长按动作编辑后键面仍显示默认符号**：键面提示原来是 `key.hint ?: longPressHint(内置表)` ⇒
   编辑过的长按动作被内置表盖住。改为 **优先键自己的 popup / longClick**，都没有才回落内置表 ✓
3. **新增「恢复默认」按钮**（按键外观卡片）：一键复位 键高 / 工具栏高度 / 键盘与工具栏字号 /
   按键圆角 / 行距 / 列距 / 键盘左右边距 / 四向与长按提示偏移 ✓
4. **提示偏移默认值改为用户实测坐标，并删除「提示位置微调」设置**：
   上(0,-6) 下(0,-6) 左(3,0) 右(3,0) 长按(3,-3)；偏移统一为**字面屏幕增量**（+x 右、+y 下）✓
5. **悬浮窗**：
   · 输入码**显示完整**（原来只显示前三码，余下的漏到工具栏 ⇒ 用户截图里 zhh 在悬浮窗、mn 在工具栏）
   · 悬浮窗已显示输入码时，**工具栏不再重复显示** ✓
   · 竖向新增**正向/反向**开关（反向 = 第 1 个候选在最下、越靠后越靠上；序号仍按真实候选号）✓
6. **新增「候选快捷键」设置**：第二 / 第三候选可绑到 句号 / 逗号 / Shift / 符号键 / 自定义 code / 无；
   候选数足够时按下即上屏该候选，否则按键按原行为走；**默认「无」= 保持现状** ✓
7. **备份改存 `Documents/Oime/backup/`**（原来写系统 Download）✓

# 轮19.56（0.9.63-oime vc73）：坐标语义修正 / 动作中文名 / 候选键下拉+自定义子页 / 外置坐标文件

1. **提示坐标整体偏右偏上**：19.55 我按"字面屏幕增量"实现，而用户给的数值是按**上一次「提示位置微调」
   的语义**（从该侧边缘往里）⇒ 改成**正值一律朝键面内侧**：
   上/下 y = 距上/下边缘往里；左/右 x = 距左/右边缘往里（y 正数向下）；长按 x = 距右边往里、y = 距顶边往里。
   默认：上 y6 · 下 y6 · 左 x3 · 右 x3 · 长按 (3,3) ✓
2. **坐标改读外置文件**（用户补充要求）：`Documents/Oime/hint_offsets.txt`，首次运行写默认模板（含注释），
   之后**以文件为准**（按键面内侧的 dp 值），改动 2 秒内自动生效，不必重装 ⇒ 用户自己调好后把文件发我，
   我再把值固化成默认 ✓（读文件带 mtime 缓存 + 2 秒节流，热路径不碰 IO）
3. **内置动作在键面/气泡显示中文**：`actionDisplayName()` —— Time→时间 · Date→日期 · ChineseDate→农历 ·
   RepeatCommit→重复（另含 部署/切换输入法/剪贴板/○菜单/全选/剪切/复制/粘贴/撤回/全删/换行/清空）✓
4. **候选快捷键改下拉栏** + 默认值改为**第二候选=句号、第三候选=符号键**（与用户现状一致）；
   「无」= 解除绑定（打字时不触发）✓；**自定义候选键 → 独立子页面**（两个 code 输入 + 保存/清空），
   保存后上一级下拉栏会多出「自定义（值）」一项 ✓
5. **「恢复默认」按钮**文案去掉括号，说明移到按钮下方 ✓

# 轮19.57（0.9.64-oime vc74）：坐标定稿为默认（删除外置文件）+ 候选键加埋点诊断

1. **坐标定稿**：读取用户改好的 `Documents/Oime/hint_offsets.txt`，把实测值写成**默认值**：
   `up(0,-5) · down(0,-5) · left(2,0) · right(2,0) · press(3,-3)`
   ⇒ **删除外置文件机制**（不再生成/读取该文件，避免两边不一致）；X 的 clamp 也放宽到 ±80（允许负值）✓
2. **候选快捷键"改了不生效"排查**：加埋点（`Diag` tag `CandKey`，写 `Download/oime_diag.log`），
   记录 code / 配置值 / 候选数 / select 结果；同时：
   · **中文标点归一** —— 配置写 `.` 也能命中「。」（同理 ，/、；：？！）✓
   · **DirectCommit 路径也拦一次**（多字符 code，如九宫格自定义）✓
   下一步：让用户在组词状态按一下配置键，读日志定性（是"没匹配上"还是"候选数为 0"）再精准修

# 轮19.58（0.9.65-oime vc75）：候选快捷键**真凶定位并修掉**（埋点驱动）

**排查过程**（这次的坑很典型，记下来）：
1. 先加 `Diag` 埋点（tag `CandKey`）→ 用户组词后按键 → 读 `Download/oime_diag.log`
2. 日志显示：**拦截函数确实在跑**（`code=d/l/s/f` 都进了），且**读到了设置** `k2=. k3=,`
   —— 但**按句号/逗号时一次都没进过这个函数** ✗
3. 结论：用户自定义布局里的 `.`/`,` 是 **FUNCTION 键** ⇒ 走 `KeyAction.Resolved`，
   而候选快捷键当时只挂在 `handleChar`（CHARACTER 键）上 ✗

**两个修复**：
1. **`KeyAction.Resolved` 路径也拦**：执行前先 `handleCandidateShortcut(action.value)`，
   命中且候选足够就选候选，否则按原行为（上屏标点）✓
2. **清掉真正的"默认行为"来源**：随包的 `assets/rime/default.custom.yaml` 里**生效中的**两条 key_binder
   `semicolon → 候选2`、`apostrophe → 候选3`（方案本身并不绑定，是这个全局默认配置在抢）
   ⇒ 已注释让位给 App 内的「候选快捷键」设置（想用分号/单引号，在设置里选/填对应 code 即可）✓

# 轮19.59（0.9.66-oime vc76）：候选键**全量按键埋点**（继续定位，不靠猜）

- 19.58 修了两处后，用户反馈仍不生效；再读 diag：`[CandKey] no-match code=d/h/m k2=, k3=shift` ——
  函数在跑、设置读到了，但**标点键（逗号）依旧没进函数** ✗（说明还存在第三条路径）
- 本轮加**全量按键埋点**（tag `Key`）：记录每个动作的类名与关键字段（CharKey.c / DirectCommit.text /
  Resolved.value / Candidate.index / SwitchPage.page）⇒ 按一次标点键即可确定它属于哪类动作，再精准拦

# 轮19.61（0.9.67-oime vc77）：候选快捷键**改为写入 RIME 配置 + 保存热重载**（用户建议方案）

**换思路的原因**：埋点证明有的标点键**根本不经过 Service**（全量 `[Key]` 日志里没有逗号键的任何记录），
在 App 层拦截不可靠 ✗ ⇒ 采用用户建议：**把设置写进 `default.custom.yaml` 的 key_binder`，保存后重部署**，
由 **RIME 引擎自己选候选** ⇒ 与按键路径无关 ✓

## 实现
- 新增 `core/rime/RimeKeyBinder.kt`：
  · `applyCandidateBindings()`：读取 `files/rime/shared/default.custom.yaml`，
    **先删掉所有生效中的 `send: 2/3` 候选绑定**，再按设置写入新行
    （字符 → `accept: "，"`；`shift` → `Shift_L`；`symbols` 等非 RIME 键不写、由 App 层兜）
  · `applyAndDeploy()`：写配置 + `RimeManager.deployImportedSchemas()` = **保存即热重载** ✓
- **设置项搬到子级菜单**（用户要求）：键盘设置页只留一个入口「候选快捷键（当前：…）▸」，
  子页里同时有第二/第三候选下拉 + 自定义 code + **「保存并重载」** 按钮 ✓
- **启动自愈**：`AZimeService.onCreate` 每次补写一次（防止资产同步把配置覆盖回去）✓
- App 层拦截**保留**作为兜底（UI 层 + Service 层都拦一道），但不再是唯一依赖 ✓

# 轮19.62（0.9.68-oime vc78）：候选快捷键**找到真实现点**——组合中候选映射改为读设置

**关键转折**：用户提示「读仓库记录，看当时做候选键时有没有写记录」⇒ 在 DEVLOG 里查到**轮8**：
> 前三候选映射预设键：组合中 **空格=候选1、句号=候选2、123键=候选3**，键面显示实际候选文本点击上屏

⇒ 顺着记录定位到 `KeyboardScreen.kt` 的组合分支里有一段**写死**的映射：
```kotlin
key.type == FUNCTION && key.code == "symbols" -> 2   // 符号键 = 候选3
key.type == CHARACTER && key.code == "."       -> 1   // 句号键 = 候选2
key.type == SPACE && key.code == "space"       -> 0   // 空格键 = 候选1
```
**这才是"默认行为"的唯一来源** ✗ —— 它直接替换键面并派发 `Candidate(n)`，
所以我前面在 Service / Resolved / yaml 上做的一切都白费（根本走不到那儿）✗

## 本轮修复
- 该映射块**改为读设置**：`k2 = candidateKey2()` → 候选2（index 1）、`k3 = candidateKey3()` → 候选3（index 2）；
  **「无」= 不映射**（该键恢复原行为）；空格键仍固定第一候选 ✓
- **撤销前面绕路的东西**：删除 `RimeKeyBinder`（写 yaml 那条路）、去掉 Service 的 `handleCandidateShortcut`
  与各处拦截、去掉 UI 层拦截、去掉全量按键埋点 ⇒ 回到**单一实现点**，简洁可靠 ✓
- 设置子页的按钮从「保存并重载」改为「保存」（组合映射每次渲染都读设置，**改完直接生效**）✓

**教训（很重要）**：改功能前先**读仓库记录定位"当前实现点"**，不要在猜测的路径上打补丁 ✗

# 轮19.63（0.9.69-oime vc79）：候选快捷键入口做明显 + 子页删多余说明
- 「设置候选快捷键」入口改为**强调色描边按钮**（图标 + 半粗文字 + ▸），不再是一行小字 ✓
- 自定义候选键子页说明删掉后半句（"保存后会多出「自定义」一项"——现在全都是自定义了）✓

# 轮19.64（0.9.70-oime vc80）：图标全套重画（方案 A）+ ○ 菜单加「设置」+ 状态卡白字加大

1. **图标集按方案 A 全套重画**（用户选型）：
   · 统一 **1.6px 细描边圆头**（原来 1.8~2.4 混用，视觉重量不齐）；实心点改为**双弧闭合小圆**（一致性更好）
   · **回车 = 纸飞机**（用户指定保留个性）· **○ 菜单/圆环 = 圆环**（用户指定）
   · 37 枚全部重画（含 schemas/numpad/symbols/settings/backspace/space/shift/back/mic/cloud/refresh/info/
     link/font/palette/pip/code/emoji/keyboard/check/tune/toggle/apps/manage/sun/moon/ring/candidates/
     lang/floatKbd/oneHand/redo/undo/trash 等）， 映射保持不变
2. **○ 菜单新增「设置」**（8 个格子）：点击直达设置页 ✓
3. **设置内状态大方块**：文本改**白色**（原来是 onPrimaryContainer 深色）+ 字号加大一号（运行状态 18→20sp，方案 14→15sp）✓

# 轮19.65（0.9.71-oime vc81）：数字/符号键改文字 + 空格改一条直线

- **数字键 / 符号键**：去掉图标，键面直接用文字 —— 同一个键按「首选符号页」显示 **123** 或 **？#！**
  （渲染层不再用图标覆写 ，改为文字 ）✓
- **空格键**：图标改**一条直线**（原来两端有竖线）；空格键默认 label 置空 ⇒ 显示直线，
  用户若自定义了空格文本仍然优先显示 ✓
- 预览页 （含备选写法）已给出 ✓

# 轮19.66（0.9.72-oime vc82）：返回键与长按气泡也用文字，与 123 / ？#！ 统一

- **九宫格页 / 符号页的「返回主键盘」键**：去掉 back 图标 → 文字 **abc**
  （原来用图标，和新入口 123 / ？#！ 的语言脱节 ✗）
- **长按符号键弹出的页气泡**：两个图标（symbols/numpad）→ 文字 **123 / ？#！**
  （选中项用 accentKeyBg 底 + accentKeyText 字，仍是 on 色规则 ✓）
- 至此「符号/数字/返回」三处入口在**键面与气泡里都是文字**，风格统一 ✓

# 轮19.67（0.9.73-oime vc83）：图标回退修正 + 去除悬浮模式 + 修复浮窗不跟随光标

## 1. 图标（修正 19.65/19.66 的过度改动）
- **符号键回退为图标**：只有「数字（九宫格）」这一态用文字 **123**；符号态恢复**符号图标** ✓
  判据：key.code == "symbols" 且 preferredPage() == "numpad" ⇒ 显示 123，否则显示 key.icon
- **长按页气泡**：修正 19.66 的**左右写反** ⇒ **左 = 符号（图标）**、**右 = 九宫格（文字 123）** ✓
- **○ 菜单 → 定制工具栏里的「数字」**：图标 → 文字 **123** ✓（工具栏与勾选列表同一渲染）
- **符号页的九宫格入口**：pageKey("九宫格", icon = "numpad") → **文字 123**（去图标）✓

## 2. 去除悬浮模式
- availableToolbarTools 移除 "floatkbd"（工具栏不再提供「悬浮」）
- KeyboardManager.floatKeyboard() **恒返回 false**（旧偏好残留也不会再浮起来）；
  setter 保留仅为兼容旧配置读写 ✓

## 3. 修复「悬浮窗在部分 App 顶部搜索栏不跟随光标」（闲鱼等）
- **真因**：onUpdateCursorAnchorInfo 旧逻辑只用 insertionMarker；闲鱼这类**自定义搜索栏不提供**
  insertionMarker ⇒ 走 else 分支把坐标**清零** ⇒ UI 里 cursorBottom > 0 判定失败 ⇒ 浮窗退化为
  **固定位置**（观感就是"不跟随光标"）✗
- **修复**：优先用 **getCharacterBounds(insertionIndex)**（这类 App 通常仍提供字符外框），
  退而用 insertionMarker；**两者都没有时保持上一次位置**（不再清零）✓
  · 取下标用反射（不同 compileSdk 下可见性有差异）；该下标取不到时回退取前一个字符
- 加埋点 CursorAnchor（记录命中哪条分支 + 矩形），便于在闲鱼里直接验证 ✓

# 轮19.68（0.9.74-oime vc84）：浮窗光标跟随 —— 埋点抓到真因（NaN 未过滤）

**埋点证据**（闲鱼搜索栏打字）：
```
[CursorAnchor] insertionMarker h=NaN top=NaN
```
**真因**：旧判断只挡 `Float.MAX_VALUE`，**放行了 NaN** ⇒ `NaN.toInt()` = **0** ⇒ `cursorBottom = 0` ⇒
UI 判定「没有光标位置」⇒ 悬浮窗退化为**固定位置**，观感就是"不跟随" ✗

**修复**：
1. 插入点标记必须**过滤 NaN / Infinity / 零高度**（`b > t`）才算有效
2. **字符外框**改为「下标未知时扫描全部可用外框、取最后一个有效值」（光标通常在已输入内容末尾）
   —— 用反射取 `getCharacterBoundsCount` / `getInsertionIndex`，兼容不同 compileSdk
3. `matrix` 变换后再次校验（NaN / `bottom <= 0` 一律视为无效）
4. 无效时**保持上一次位置**（不再清零）

# 轮19.69（0.9.75-oime vc85）：浮窗跟随真因②——IME 窗口太矮，浮窗被裁在窗口外

**埋点证据**（第 2 次，修好 NaN 后）：
```
[CursorAnchor] insertionMarker h=302.0 top=109.0 bottom=195.0   ← 坐标正常，就在屏幕顶部搜索栏
[CursorAnchor] insertionMarker h=42.0  top=33.0  bottom=119.0
```
坐标拿得到 ⇒ 说明问题不在取值，而在**画不出来**：
浮窗偏移 = `cursorBottom - imeTop - floatH - 6dp`，而 `imeTop` = **IME 窗口顶部**；
普通键盘的 IME 窗口**只有键盘那么高**（约 1900）⇒ 偏移 ≈ 195 - 1900 ≈ **-1700**
⇒ 浮窗被放到窗口外 ⇒ Android 不绘制（IME 不能画到自己窗口之外）✗

**修复（复用既有"高窗口 + 区域触摸"机制）**：
- 编码浮窗开启时把**根内容撑满屏**（`height(screenHdp)`）⇒ IME 窗口覆盖全屏 ⇒ 浮窗可贴到任意光标位置 ✓
- 服务端 `onComputeInsets`：此时仍把 `contentTopInsets`/`visibleTopInsets` 报成**键盘上沿**
  ⇒ App 照常让位、输入框不被键盘盖住 ✓；`touchableRegion` 限定成键盘 ⇒ 其余区域触摸穿透给 App ✓
- 键盘列在浮窗模式下同样**上报自身矩形**（原来只在悬浮键盘模式上报）
- 普通模式（未开浮窗）行为**完全不变**（矩形上报 -1 ⇒ 走系统默认 insets）✓
- 新增埋点 `Float`：记录 cursor / imeTop / floatH / 最终 offset，便于下次一眼确认 ✓

# 轮19.70（0.9.76-oime vc86）：撤销撑屏（修浮窗错乱）+ 工具栏勾选列表 123 + 界面风格回退 + 悬浮窗权限前置

## 1. 撤销 19.69 的「撑满屏」（**浮窗错乱的真因**）
用户在真机看到：浮窗被画在键盘区、与空格/候选重叠、还伴随小键盘残影 ✗
⇒ 根因是我把 IME 窗口撑成整屏（内容顶到全屏高 + 键盘贴底），Popup 的坐标系与键盘布局互相错位 ✗
⇒ **整段撤销**（UI 的 `floatFollow` 撑高 + 宽度上报；服务端的 insets 分支），回到 19.68 的状态 ✓

## 2. 定制工具栏勾选列表里的「数字」也改文字 123
19.67 只改了工具栏**按钮**那一处（`KeyboardScreen:1665`），
勾选列表（`KeyboardScreen:2292`）是另一处渲染 ✗ ⇒ 一并改成文字 **123** ✓

## 3. 界面风格：状态大方块回退到「换图标前那一版」
19.64 我把状态卡文字写死为 **白色**（用户当时要求）⇒ 在**浅色强调色**的界面风格下看不清 ✗
⇒ 按用户要求回退：颜色改回 **`onPrimaryContainer`**（由主题按强调色给对比色，任何风格都清晰 ✓），
字号回退 **20→18sp / 15→14sp** ✓

## 4. 悬浮窗权限前置（为"系统级悬浮窗"铺路，见下）
- Manifest 新增 `<uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />` ✓
- 设置 → 悬浮窗：未授权时显示**「授予『显示在其他应用上层』权限」**直达入口（跳 MANAGE_OVERLAY_PERMISSION）✓

## 5. 下一步（依据用户提供的《TRIME 悬浮窗实现路径分析报告》）
报告结论：**「中文输入法」(com.osfans.trime.accessibility) 的悬浮窗是
`SYSTEM_ALERT_WINDOW` + `PopupWindow.setWindowLayoutType(TYPE_APPLICATION_OVERLAY=2038)` 的系统级窗口**，
并配 `setClippingEnabled(false)` + `setInputMethodMode(2)`（显示时不拉起输入法）+ 反射 `setLayoutInScreenEnabled(true)`；
**与无障碍服务无关**（无 AccessibilityService、无 2032）✓
⇒ 我们要做的等价改造：把编码浮窗从「IME 窗口内的 Compose Popup」搬到**自建 PopupWindow（2038）**，
用**屏幕坐标**定位（不再需要 imeTop 换算）；未授权时自动降级回窗口内浮窗 ✓（下一步实施）

# 轮19.71（0.9.77-oime vc87）：界面风格立刻生效 + 候选排列去重 + 状态卡改回白字

## 1. 界面风格（主题与配色）**改了不生效** —— 真因与修法
- 与「换图标前一版」逐行 diff 后确认：**界面风格那段的代码一模一样** ✗ ⇒ 问题不在那段
- 真正的 bug：设置页主题外壳**直接读 `KeyboardTheme.isMiuix()`**（**不是 Compose 状态** ✗）
  ⇒ 下拉改了偏好，却**没有任何东西触发重组** ⇒ 看起来"改了没反应" ✗
- **修复**：在 `setContent` 里 hoist 一个 `uiStyleRev` 状态，主题外壳改读
  `remember(uiStyleRev) { isMiuix() }`；「主题与配色」里改风格时回调 +1 ⇒ **立即生效** ✓
  （`SettingsScreen` / `ThemeColorSettings` 增加 `onUiStyleChanged` 回调贯通 ✓）

## 2. 悬浮窗「候选排列」显示两遍（横向/竖向各重复一份）
19.55 插入「竖向反向」开关时，把 `listOf("h"…"v"…)` 那段**又复制了一遍** ✗ ⇒ 选项出现 4 个 ✗
⇒ 删掉重复的第二份 ✓（横向 / 竖向 / 竖向反向 三者都在 ✓）

## 3. 状态大方块：改回**白字 + 20/15sp**（按用户要求撤销 19.70 的回退）

# 轮19.72（0.9.78-oime vc88）：界面风格对比度兜底（4 个风格 bug 同一根因）+ 悬浮窗设置收尾

## 1. 界面风格 4 个问题 ⇒ **同一个根因：键面与底色对比不足**
用户实测（修好"改了不生效"后终于能逐个试风格）：
- **Material You**：按键完全看不见（键底色 ≈ 键盘底色）
- **Nothing OS**：打字时候选压到键面、按键不显示
- **iOS**：功能键（Shift/123/退格）快和背景融一起
- **One UI**：同 Nothing OS
⇒ 根因：各风格 token（尤其 **Material You 的壁纸动态取色**）**不保证**键底/功能键底与键盘底有差异 ✗

**修法：在配色解析出口加统一兜底**（`buildKeyboardColors`，浅/深两套都走）：
- `ensureLuminanceDelta(键底, 键盘底, 0.055)`：亮度差不足就朝**白**方向推（保色相、只调明度）
- 功能键同理（阈值 0.045）
- `ensureTextReadable(文字, 键底)`：亮度差 < 0.32 时朝**黑/白中更远的一侧**推 ⇒ 字永远可读
  ⇒ 一次修好所有风格 + 未来新增风格自动受益 ✓

## 2. 悬浮窗设置页
- **候选排列 chips 可能不显示**：那行 Row 缺宽度约束（weight 子项可量成 0 宽）⇒ 补 `fillMaxWidth()` ✓
- **「悬浮窗背景色」入口做明显**：改成与其它设置行同规格（标题 + 右侧「自定义/跟随主题」+ 色块）✓
- **删除底部那段说明**（用户要求）✓

# 轮19.73（0.9.79-oime vc89）：系统级浮窗落地 + 上一轮遗留问题真修

## 1. 上一轮没修对的，这次找到真因
- **候选排列看不到**：19.71 删重复块时把 **`Row {` 的闭合括号一起删了** ✗
  ⇒ 后面的「竖向反向」开关被并进**同一个 Row**（它内部是 `fillMaxWidth`）⇒ 把两个 chips **挤成 0 宽** ✗✓
  ⇒ 补回括号 ✓（`fillMaxWidth` 只是缓解，真正的病因是结构错 ✗）
- **界面风格对比度兜底方向错了**：19.72 无条件朝**白**推 ✗ ⇒ 底色本就接近纯白时，键越推越白、差反而更小 ✗
  ⇒ 改为**按底色判断方向**（底色够暗才用"键比底亮"的惯例；否则压暗键）✓
  ⇒ 再加一层：**按需压暗键盘底色**（保住白键惯例，比把键压暗更符合各风格观感）✓，阈值也提到 0.075/0.06 ✓
- **悬浮窗背景色入口**：已是标准设置行（标题 + 自定义/跟随主题 + 色块）✓

## 2. ★ 系统级编码浮窗（按《TRIME 悬浮窗实现路径分析报告》配方落地）
- `AZimeService` 自建 **`PopupWindow`**（内容 = `ComposeView` 渲染 `FloatWindowBody`）：
  · `isClippingEnabled = false` ✓
  · `inputMethodMode = INPUT_METHOD_NOT_NEEDED`（显示时不拉起输入法）✓
  · `setWindowLayoutType(2038)`（`TYPE_APPLICATION_OVERLAY`；<26 用 1003）✓
  · `isFocusable = false` + `isOutsideTouchable = false` ✓
- **用屏幕坐标定位**：`showAtLocation(anchor, NO_GRAVITY, cursorLeft, cursorBottom)` 后按实测高度
  `update(x, cursorBottom - h - 6dp)` ⇒ **不再需要 imeTop 换算** ✓，且可画到屏幕任意位置（含闲鱼顶部搜索栏）✓
- **订阅 `uiState`** 同步显示/定位/隐藏；键盘收起或服务销毁时 `dismiss()` ✓
- **内容共用**：`FloatWindowBody`（编码 + 候选 + 横/竖 + 竖向反向）两套窗口渲染同一份 ✓
- **门控**：系统级浮窗激活时，窗口内浮窗自动让位（`FloatOverlayHost.active`）✓ 不会画两份 ✓
- **降级**：未授权（`canDrawOverlays == false`）或 API<23 ⇒ 自动回退窗口内浮窗 ✓

# 轮19.74（0.9.80-oime vc90）：修"浮窗不出现"（active 门控时机错）+ 删除三种风格

## 1. 浮窗根本不出现 —— 真因是我 19.73 的**门控时机写反** ✗
```kotlin
com.azime.input.ui.keyboard.FloatOverlayHost.active = true   // ← 先宣告"我接管了"
if (!pw.isShowing) { runCatching { pw.showAtLocation(...) } } // ← 再去显示（可能失败）
```
⇒ 一旦系统级窗口没能显示（异常/被系统拒），`active` 已是 true ⇒ **窗口内浮窗被抑制** ✗
⇒ 结果：**两边都不画** ⇒ "根本不出现" ✗✓

**修复**：
- 先 `showAtLocation`，**成功后才**把 `active = true`；失败则立刻 `active = false` 让窗口内浮窗顶上 ✓
- 整个显示过程加日志（`FloatOv`：ensure-failed / show-failed: … / shown at (x,y)）⇒ 下次一眼可判 ✓

## 2. 删除三种界面风格（用户要求：修不好的直接删）
- **移除 iOS / Nothing OS / Material You** ⇒ 保留 **Material / Miuix / One UI** 三套 ✓
- 三套 token 定义、枚举项、`selectable` 列表一并删除；用户旧偏好值（如 "ios"）会由
  `UiStyle.from()` **自动回落 Material** ✓ 不会崩 ✓

# 轮19.75（0.9.81-oime vc91）：修"浮窗出一次不出一次" —— PopupWindow 实例不能复用

**现象**：悬浮窗出来一次、下一次不出来（交替）✗
**真因**：`PopupWindow` 一旦 `dismiss()`，其 `contentView` 已从窗口 detach ⇒
**同一个实例再次 `showAtLocation` 会静默失败**（不抛异常、`isShowing` 保持 false）✗
⇒ 第一次成功、第二次不出 ⇒ 交替 ✗✓

**修复**：`hideFloatOverlay()` 里 **丢弃实例**（`floatPopup = null; floatView = null`）⇒
下次同步时用**全新实例**重建 ✓

**同时加足埋点**（`FloatOv`）：
- `hide`
- `sync showing=? cursor=(x,y) preedit=…`（每次状态同步）
- `update -> (x,y) h=?`（每次实际落点）
- `shown at (x,y)` / `show-failed: …` / `ensure-failed`
⇒ 下次在闲鱼打字后，一次日志即可判定："有没有走系统级窗口 / 落点是否跟着光标" ✓

# 轮19.76（0.9.82-oime vc92）：修"打两个字母浮窗就消失" —— 收网时机放错了回调

**现象**（用户实测）：浮窗**出现**后，**打两个字母就消失** ✗
**真因**：我把"收起浮窗"挂在了 `onFinishInputView` ✗ —— 不少 App 在**组合变化 / 自绘输入框刷新**时
会**反复触发** `onFinishInputView` ⇒ 打两个字就把浮窗关掉了 ✗✓

**修复**：
- `onFinishInputView` **不再收浮窗**（保留空实现 + 注释说明原因）
- 收网改到 **`onWindowHidden()`**（键盘窗口**真正隐藏**时才触发一次）✓ 与 `onDestroy()` 双保险 ✓

# 轮19.77（0.9.83-oime vc93）：**系统级浮窗从未显示过** —— 后台线程调窗口 API

**埋点铁证**：
```
[FloatOv] show-failed: Can't create handler inside thread
          Thread[DefaultDispatcher-worker-2,5,main] that has not called Looper.prepare()
[Float]   cursor=(261,311) imeTop=1996 h=335 offset=(261,-2044)   ← 一直在走窗口内降级路径
```
**真因**：`uiState.collect { syncFloatOverlay(it) }` 跑在 **DefaultDispatcher（后台线程）** ✗，
而 `PopupWindow.showAtLocation` / `ComposeView` 都是**窗口操作，必须主线程** ✗
⇒ 每次 show 都抛异常 ⇒ **系统级浮窗一次都没成功显示** ✗ ⇒ 一直退到窗口内路径
⇒ 而窗口内路径的偏移是 `cursorBottom - imeTop` = 311-1996 ≈ **-1700** ⇒ 被裁掉 ⇒ "闲鱼不跟随" ✓

**修复**：
1. 订阅改到 **`Dispatchers.Main.immediate`**（窗口操作回主线程）✓
2. 光标坐标无效（`-1/-1`）时**保持原位**（不再把浮窗挪到 (0,0)）✓

# 轮19.78（0.9.84-oime vc94）：修"打字即闪退" —— PopupWindow 里不能用 ComposeView

**崩溃堆栈（logcat crash buffer 拿到）**：
```
java.lang.IllegalStateException: ViewTreeLifecycleOwner not found from
    android.widget.PopupWindow$PopupDecorView{...}
  at WindowRecomposer_androidKt.createLifecycleAwareWindowRecomposer
  at AbstractComposeView.onAttachedToWindow(ComposeView.android.kt:283)
```
**真因**：把 **ComposeView** 塞进自建 `PopupWindow` ✗ —— popup 的装饰视图（`PopupDecorView`）链条上
**没有 ViewTreeLifecycleOwner** ⇒ Compose 在 `onAttachedToWindow` 里解析 recomposer 时直接抛异常 ✗
（与当年做 IME 输入视图踩的是**同一个坑**：Compose 需要从视图链上找到 owner ✗）

**修复**：浮窗内容改用**纯 Android View** 渲染（`LinearLayout` + `TextView`）✓
- 不再依赖 Compose 生命周期 ⇒ 从根上消除该崩溃 ✓（也和 TRIME 报告里的纯 View 做法一致 ✓）
- 配色仍复用 `buildKeyboardColors(dark)`（非 Composable 的 public 函数 ✓），`toArgb()` 转色 ✓
- 横/竖 + 竖向反向 + 首选强调底色 + 自定义背景色/透明度 全部保留 ✓

# 轮19.79（0.9.85-oime vc95）：浮窗字体同步 + 光标缺失兜底位置（闲鱼现象定性）

## 1. 闲鱼"固定在左上角"——**日志给出定性**：闲鱼压根不发光标信息
```
[CursorAnchor] no-bounds(keep-last)        ← 全程没有任何光标数据（连字符外框都没有）
[FloatOv]      shown at (0,-1)             ← 于是显示在 (0,-1) = 左上角
[FloatOv]      cursor-invalid, keep position
```
⇒ 不是我们算错 ✗，是**该 App 不上报 `CursorAnchorInfo`** ⇒ 无坐标可用 ⇒ 只能退到固定位置 ✓
**本轮改法**：光标不可用时退到「**键盘上沿之上**」的合理位置（不再跑到左上角 (0,0)）✓
（真正"跟随"需要另找信号源 —— 下一步用「中文输入法」在同页面做对照实验 ✗）

## 2. 浮窗字体没同步字体设置
系统级浮窗是**纯 Android View**（不是 Compose）⇒ 拿不到 `FontFamily` ✗
⇒ 新增 `FontManager.keyboardTypeface()`（按已选字体顺序取第一个可用字体 → `Typeface`）✓
⇒ 浮窗的编码行与候选行都套用该 Typeface ✓（字体设置一变，下次渲染即同步 ✓）

# 轮19.80（0.9.86-oime vc96）：修"浮窗先闪一下再跳回"—— 光标坐标被过早清零

**现象**（用户实测，闲鱼搜索框）：先在搜索框处**闪现**一个浮窗，随即**跳回**常规位置 ✗
**真因**：把"清光标坐标"放在了 **`onFinishInputView`** ✗ —— 而不少 App（如闲鱼）**打字中途会反复触发**它 ⇒
坐标被清成 `-1` ⇒ 浮窗被判"无坐标" ⇒ 从光标处掉到我加的兜底位置（键盘上沿之上）✗✓

**修复**：
- `onFinishInputView` **不再清光标坐标**（保留最后一次有效坐标 ✓）
- 清零移到 **`onWindowHidden`**（键盘真正收起时才清 ✓）

# 轮19.81（0.9.87-oime vc97）：**悬浮窗实现整体回退**到今早调查之前（保留横/竖与正反向设置）

**用户要求**：回退到今天"闲鱼不跟随"调查之前的悬浮窗代码，但保留今晚做的横/竖 + 竖向正反向设置 ✓
（那两项设置更早就有了，本就不受影响 ✓）

## 回退内容（逐行对照 `551072ec`（19.66）校验 ✓）
- **删除**：`AZimeService` 里整套系统级浮窗实现（字段 `floatPopup/floatRoot`、`canDrawOverlay`、`isDarkNow`、
  `renderFloatContent`、`ensureFloatOverlay`、`hideFloatOverlay`、`syncFloatOverlay`）、主线程订阅、
  `onWindowHidden` 覆写、`onDestroy` 的收网调用 ✓
- **恢复**：`onFinishInputView` 里"清光标坐标"的原逻辑 ✓（浮窗仍是**窗口内 Compose Popup** ✓）
- **删除**：`KeyboardScreen` 的 `FloatOverlayHost` 门控 + 共用的 `FloatWindowBody`/`FloatOverlayHost` ✓
- **删除**：设置页「授予显示在其他应用上层权限」入口（回退后不再需要 ✓）
- **保留**（与浮窗实现无关、且有益）：
  · `CursorAnchor` 取坐标改进（字符外框扫描 + NaN/Infinity 过滤 ⇒ 坐标更稳、不再被 NaN 归零 ✓）
  · 界面风格对比度兜底（`ensureLuminanceDelta` / `ensureTextReadable` ✓）
  · Manifest 的 `SYSTEM_ALERT_WINDOW` 声明（此刻为惰性，不申请即不生效 ✓）

校验方式：拉取 `551072ec` 的 `AZimeService.kt` / `KeyboardScreen.kt` 与本地逐行 diff ⇒ 剩余差异**仅上述两项** ✓

# 轮19.82（0.9.88-oime vc98）：chips 选中色 / 背景色入口加强调 / 语音使用说明改弹窗

1. **「候选排列」横向·竖向选中时文字不明显** —— 又是"底色改了、文字没换 on 色" ✗（同类第 5 次）
   ⇒ 选中：`primaryContainer` 底 + **`onPrimaryContainer` 字** + 1.5dp `primary` 描边 + 加粗 ✓
   ⇒ 未选中：`surfaceVariant` 底 + `onSurfaceVariant` 字 ✓
2. **悬浮窗背景色入口再加强调**：改成与「设置候选快捷键」同款 —— 强调色 10% 底 + `primary` 描边 +
   调色板图标 + 半粗强调色文字 ✓
3. **语音输入「使用方法」改为弹窗**：标题右侧加 **「使用说明」按钮** ⇒ 点开弹窗看详细说明 ✓
   说明重写得更细：两种本地模型（目录/文件/语言/交互差异）· 三步使用流程 · 联网 API · 下载地址 · 小贴士 ✓
   页脚那段长文本已移除 ✓
   （踩坑：`verticalScroll`/`rememberScrollState` 是**扩展函数**，必须 import 后直调 ✗ —— 本项目已知坑又踩了一次 ✓）

# 轮19.83（0.9.89-oime vc99）：日志系统（入口在关于）+ 关于页分卡与许可/隐私

## 一、日志系统（先做了口头+设备调研，再动手 ✓）
**调研**（直接看设备上 trime / xime 的实际产物）：
- **Xime**：`Documents/Xime/logs/kime_YYYYMMDD.log`，格式 `yyyy-MM-dd HH:mm:ss.SSS [I] TAG: msg`
  —— 一整天**只有 4 行**（只记 FileLogger 初始化 / ModelRuntime attached 等事件 ⇒ **轻量按需** ✓）
- **trime（同文）**：`Documents/rime/logs/crash-<时间戳>.log` —— 崩溃单独成文件，且**先打一整块设备信息**
  （versionName/versionCode/BARND/BOARD/CPU_ABI …）再跟堆栈 ⇒ 经典 ACRA 式报告 ✓

**结论（也是我们的实现）**：**默认轻量 + 详细按需开** ✓ —— 高频埋点最耗 IO/电，绝不能默认常开 ✗

**实现**：
- 目录 **`Documents/Oime/logs/`** ✓（`StorageManager.logsDir`，随其它目录一起创建 ✓）
- 文件：`oime-YYYY-MM-DD.log`（按天 ✓）+ `crash-YYYY-MM-DD.log`（崩溃单独 ✓）
- 级别：`err/warn/info` **常开**（错误/警告/关键事件：生命周期、部署、切引擎…）；
  `log()` **仅详细模式**记录（按键/候选/光标/复制条等埋点 ✓）
- **崩溃日志带设备信息块**（吸收 trime 的做法 ✓：版本/型号/Android/ABI/时间）+ 全局崩溃捕获 ✓
- 轮转：只保留 **最近 7 天** ✓ + 单文件 **2MB** 上限 ✓
- 入口在 **设置 → 关于 → 「日志」卡片**：详细日志开关 + 目录路径 + 文件列表（名/大小）+ 清空 + 写入测试日志 ✓

## 二、关于页重排（原来所有条目共用一个背景 ✗）
拆成 **6 张独立卡片** ✓：① 版本信息 ② 日志 ③ 数据与备份 ④ 开源许可 ⑤ 隐私条约 ⑥ 致谢
- **开源许可**（弹窗，可滚动）：librime BSD-3 / sherpa-onnx Apache-2.0 / AndroidX·Compose Apache-2.0 /
  LuaJ·AndroLua MIT / opencc Apache-2.0 / RIME 方案词库归属说明 ✓
- **隐私条约**（弹窗）：不采集输入内容与设备标识、无遥测、数据只在 `Documents/Oime/`、
  联网仅在用户主动操作时发生（GitHub / 自填的联网语音 API / 手动下载）· 权限逐条说明 ✓

## 轮19.83b（0.9.90-oime vc100）：修正初始化日志的插值
- 装机后实测日志首行是字面量 `pkg=${packageName}` ✗ —— 生成脚本把 $ 吞成了字面量
  ⇒ 改为**字符串拼接**写法 ✓（以后写含 $ 的 Kotlin 字符串一律用拼接或 heredoc 生成 ✓）
- 实测：`Documents/Oime/logs/` 已创建 ✓、`oime-2026-09-20.log` 已写入首行 ✓

# 轮19.84（1.0.0 / vc101）：首个正式发行版 —— Release 构建 + 兼容性扩展 + README

1. **版本定为 1.0.0**（versionCode 101）✓
2. **兼容更多安卓版本**：`minSdk 24 → **21**`（Android 5.0+）✓
   本地跑通 `assembleRelease`（含 lintVital）✓ ⇒ 21 没有 NewApi 问题 ✓
3. **ABI**：`abiFilters` 改为**按 `jniLibs` 实际内容自适应** ✓
   · arm64-v8a ✓（现成）
   · armeabi-v7a：需要 32 位 `librime_jni.so` —— 查了 Xime 仓库/release/CI 与本机留存的
     `Xime-2.6.2-arm64-v8a.apk`（内部也只有 arm64 ✗）⇒ **暂时没有**；
     构建脚本已留好钩子：把 so 放进 `jniLibs/armeabi-v7a/` 即自动纳入 ✓
   · x86 / x86_64 不发布（无设备测试 ✓）
4. **发行签名**：release 用 **debug keystore** 签名 ⇒ **不需要任何 secrets**（fork 也能直接出包 ✓）；
   代价是将来换正式密钥需卸载重装（已在 README/Release 说明里写明 ✓）
5. **新增 `.github/workflows/release.yml`**：打 `v*` tag 时自动 `assembleRelease` →
   重命名为 `oime-<版本>.apk` → 用内置 `GITHUB_TOKEN` 创建 **GitHub Release**（无自定义 secrets ✓）
6. **README 更新**：下载安装步骤 / 目录结构说明 / 兼容性表（含 armeabi-v7a 现状与钩子）/
   隐私与权限 / 日志说明（前两轮新增的能力都写进去了 ✓）

# 轮19.85（1.0.0 / vc102）：**补上 armeabi-v7a**，Release 重发

**来源**：Xime 的官方 release 里同时提供 `Xime-<ver>-armeabi-v7a-signed.apk`
⇒ 取 **v2.6.2**（与现有 arm64 的 `librime_jni.so` **同一版本** ✓ 保证 JNI 接口一致）的
`lib/armeabi-v7a/librime_jni.so`（3.86 MB，ELF class=1 / e_machine=40 = ARM 32 位 ✓）
⇒ 放入 `app/src/main/jniLibs/armeabi-v7a/` ✓（构建脚本自适应 ⇒ 自动纳入 ✓）

**验证**（本地 `assembleRelease`）：APK 39.4 MB，含
· `lib/arm64-v8a/librime_jni.so` 5.52 MB ✓
· `lib/armeabi-v7a/librime_jni.so` 3.86 MB ✓
· 两套 sherpa-onnx / onnxruntime so ✓

**版本**：versionCode 101 → **102**（versionName 仍 1.0.0）—— 让装过旧 1.0.0 的机器也能更新 ✓
**Release**：删掉旧 v1.0.0（release + tag）后重新打 tag 重发 ✓（用户要求 ✓）

# 轮19.86（1.0.0 / vc103）：发行包**按 ABI 拆分**（arm64-v8a 与 armeabi-v7a 各自出包）

**用户要求**：不要合并成一个包 ⇒ 改 `splits.abi` ✓（`isUniversalApk = false` ✓，ABI 列表仍按
`jniLibs` 实际内容自适应 ✓）

**产物**（本地 `assembleRelease` 验证）：
- `app-arm64-v8a-release.apk` → 27.5 MB（仅含 arm64-v8a 的 librime / sherpa / onnx ✓）
- `app-armeabi-v7a-release.apk` → 26.4 MB（仅含 v7a ✓）
（对比：之前的合并包 39.4 MB ⇒ 单包小了约 12 MB ✓）

**release.yml 同步改造**：`assembleRelease` 后把 `app-<abi>-release.apk` 重命名为
`oime-<版本>-<abi>.apk` 一并上传 ✓；Release 正文改成"按架构选包"的说明表 ✓

**版本**：vc 102 → **103** ✓（versionName 仍 1.0.0）· 旧 release/tag 已删除后重发 ✓

# 轮19.87（1.0.0 / vc104）：修 Android 5.x 32 位闪退 —— 给 v7a 的 so 补 DT_HASH

**用户实测**（老设备 motorola XT1085 / Android 5.1 / SDK 22）：装 v7a 包后过启动页即闪退 ✗

**崩溃日志**（我们新加的 crash 日志**带设备信息**，一眼看清 ✓）：
```
java.lang.UnsatisfiedLinkError: dlopen failed: empty/missing DT_HASH in "librime_jni.so"
  (built with --hash-style=gnu?)
```

**根因**：Android **5.x 的 linker 强制要求 ELF 带 SysV `DT_HASH`** ✗，而 Xime 用新 NDK 编的 so 只有
`DT_GNU_HASH` ✗（`--hash-style=gnu`，API 23+ 才放开 ✓）⇒ dlopen 直接拒绝 ✓

**修法（ELF 手术，本地可验证 ✓）**：补一个合法的 SysV hash 表：
1. 解析 ELF32 的 program headers / `PT_DYNAMIC`（严格用「落在 LOAD 文件部分」的 vaddr→offset 映射 ✓）
2. 从 dynsym（89462 项）+ dynstr（18250 个具名符号）**重建 SysV hash**：nbucket=18250、bucket[]、chain[]
3. 表追加到文件尾；把覆盖文件尾的那个 `PT_LOAD` 扩到包含它（跨过 1900 字节未映射间隙 ✓）
4. 把一个 `DT_NULL` 槽改成 `DT_HASH -> vaddr` ✓
5. **本地验证**：模拟查找 Java_* 符号 **5/5 命中** ✓（表正确 ✓）；成品 APK 里 `DT_HASH=True` ✓
原文件备份为 `librime_jni.so.orig` ✓

**待办**：arm64 的 so 同样缺 DT_HASH（Android 5.x arm64 也会失败 ✗，但极罕见 ✓）—— 下轮补上同一手术 ✓

# 轮19.92（1.0.0 / vc109）：正式发布签名 + 只发 arm64 + README/Release 说明更新

## 1. 只发布 arm64-v8a（用户决定 ✓）
- 撤下 `armeabi-v7a`：其 so 已移出仓库备份到工作区（`jniLibs-armeabi-v7a-backup/` ✓）
- `splits.abi` 仍**按 jniLibs 自适应** ⇒ 将来把 so 放回即自动恢复 ✓
- **v8a 的最低安卓版本 = Android 6.0（API 23）**：
  随包分发的预编译 so（libonnxruntime / librime / sherpa）只带 `DT_GNU_HASH` ✗，
  而 Android 5.x 的 linker 强制要求 `DT_HASH` ⇒ 5.x 上 dlopen 必失败
- ⇒ **minSdk 21 → 23** ✓（诚实：21 能装但起不来 ✗，反而更糟）

## 2. 正式发布签名（不再是 debug ✓）
- 用 `keytool` 生成发布密钥 `oime-signing/oime-release.jks`（RSA 2048 / 有效期 30 年 ✓）
- 密钥 base64 存入 GitHub **Secrets**（4 个：SIGNING_KEY / KEY_ALIAS / STORE_PASSWORD / KEY_PASSWORD ✓），
  **不进仓库** ✓（用 PyNaCl 对仓库公钥加密后经 API 写入 ✓）
- `app/build.gradle.kts`：新增 `signingConfigs.oimeRelease`，从**环境变量**读密钥；
  没有 Secrets 时**回退 debug** ⇒ 本地/fork 仍可构建 ✓
- `release.yml`：给 Gradle 步骤注入 4 个 env + **新增「Verify APK signature」步骤**（打印证书 DN 便于核对 ✓）
- ⚠️ **踩坑**：第一次补丁把步骤名写成 `Build release APK`（实际是 `Build release APKs (per-ABI)` ✗）
  ⇒ env 从未生效、线上仍是 debug 签名 ✗ ⇒ 用 apksigner 验出来才发现 ✓
  **教训：配置类改动必须"验证产物"，不能只看补丁是否写进文件** ✓

## 3. 文档
- README 新增「**签名**」段（密钥在 Secrets / fork 回退 debug / 旧 debug 签名包需先卸载 ✓）
- Release 正文同步说明 ✓
- 日志与「关于 → 版本」都显示 **versionCode** ✓（便于核对装的是哪一版 ✓）

# 轮19.104（1.0.1 / vc114）：**震动手感修复正式发版**（按 B 版方案）

## 一、问题与调研（详见 `HAPTIC_REPORT.md`）
- 用户反馈（Redmi Note 12 Turbo / X 轴线性马达）：打字震动「嗡嗡嗡」✗
- 读 Xime 源码对比 ⇒ **两边接入不一样** ✗：Xime 默认走「系统键盘触感」（`performHapticFeedback(KEYBOARD_TAP)` ✓），
  我们发的是**满幅度裸 one-shot** ✗（`createOneShot(15ms, DEFAULT_AMPLITUDE)` ✗）
- K20 Pro（HyperOS 2 / Android 15）平台数据印证：`defaultVibrationAmplitude = **255**` ✗ ⇒ 等于满驱 ✗
- 客观取证（logcat 抓 `VibratorManagerService`）：
  · 修复前 A 版：app 自己发 ✗ + `mUsage=**unknown usage 12**` ✗（传错常量 ✗）+ ≈14ms
  · **搜狗**（机器上好手感基准）：**由系统代发（uid=1000 ✓）+ `mUsage=TOUCH` ✓ + ≈37ms** ✓✓
  ⇒ 结论：**系统触感通道**才是对的路 ✓

## 二、A/B 真机对比（K20 Pro）
- **A** = `createPredefined(EFFECT_TICK)`（vc112）· **B** = `performHapticFeedback(KEYBOARD_TAP)`（vc113）
- **用户实测：B 更好** ✓✓ ⇒ 定版采用 B ✓

## 三、本版改动（1.0.1）
1. 打字震动**默认改为「系统键盘触感」** ✓（`HapticsManager` 新增 `MODE_KEYBOARD` ✓；
   取不到宿主 View 时**自动退回**系统轻点 ✓，绝不静默 ✗）
2. 设置页「震动模式」改**三选一** ✓：系统键盘触感（默认）/ 系统轻点 / 自定义时长 ✓
3. **修正振动通道 usage** ✓：之前传 `12`（AudioAttributes 的值 ✗，ROM 记 unknown ✗）
   ⇒ 改为 API 33+ `USAGE_HARDWARE_FEEDBACK` ✓ / 31·32 `USAGE_TOUCH` ✓
4. 振动器获取对齐 Xime ✓：API 31+ 走 `VibratorManager.defaultVibrator` ✓
5. 保留「按下 / 抬起」子开关与自定义时长（5~60ms ✓，幅度上限 160 ✓）
6. main 保持 **只发 arm64-v8a** ✓（v7a 仍只在 dev/v7a 分支试验 ✓）· minSdk **23** ✓

# 轮19.105（1.0.2 / vc116）：修「流式语音没有边说边出」+ v7a 正式回归 + 清理 statusMessage

## 一、★ 流式语音「点了结束才出字」的真因（**不是模型** ✓）
**逐环节核实**
1. 模型/引擎**没问题** ✓：`zipformer` 是流式模型；`SpeechEngineManager` **每帧**都在
   `acceptWaveform → decode → getResult(stream).text` 并回调 **`onPartial`** ✓
   （且加载失败会**明确报错** ✗ 不会静默降级 ✓ —— 用户"语音能用"⇒ 说明它加载成功 ✓）
2. **问题在显示** ✗：`onPartial` 把增量写进了 **`statusMessage`** ✗，而该字段**只在「方案组」子面板**
   （`KeyboardScreen` 的 `MenuSubPanel("方案组")`）里渲染 ✗ ⇒ **语音时那个面板根本没开** ⇒ **看不到** ✓
3. 只有停止后的 `onResult` 才 `commitText` 上屏 ✓ ⇒ 症状 **= "点结束才出字"** ✓✓ 完全吻合

**修法**：增量改为写**输入区 composing 预览** —— `setComposingText(text, 1)` ✓
（⚠️ zipformer 增量是**整段重写**、不是追加 ✗ ⇒ 必须**替换** ✓；写成 append 会越说越乱 ✗）
- `onResult`：先 `finishComposingText()` ✓ 再 `commitText` 定稿 ✓
- `onError` / 取消（`onFinishInputView` 等）：`setComposingText("", 1)` + `finishComposingText()` 清预览 ✓

## 二、清理 `statusMessage`（用户要求：引擎初始化失败那处）
- 该字段**只在方案组面板**可见 ✗ ⇒ 关键信息（引擎初始化失败 / 语音权限 / 语音失败）改为
  **Toast（可见 ✓）+ `Diag.warn`（可查 ✓）**；"引擎初始化失败"那处不再写 statusMessage ✓

## 三、v7a 正式回归（与 arm64 **分开构建** ✓）
- 把**原始（未打补丁）**的 `librime_jni.so` 放回 `jniLibs/armeabi-v7a/` ✓（K20P 实测通过 ✓）
- `splits.abi` 自适应 ⇒ 发布产出 **`oime-1.0.2-arm64-v8a.apk`** + **`oime-1.0.2-armeabi-v7a.apk`** 两个包 ✓
- Release 正文补上双架构说明 ✓（v7a 要求 **Android 6.0+** ✓）

# 轮19.112（1.0.3 / vc118）：修「流式语音结束重复上屏」+ 使用说明补硬件提示

## 一、修复：结束语音时把已流式预览的内容**又插一遍**
- 现象（一加 13 实测）：说「语音测试」→ 已经边说边出 ✓ → **点结束再插一遍** ⇒ 「语音测试语音测试」✗
- 根因：`onResult` 先 `finishComposingText()`（把 composing 预览**定稿** ✓）**再** `commitText(全文)` ✗
  ⇒ 定稿 + 再插入 = 两遍 ✓
- 修法：新增 `voiceStreamed` 标记 ✓，`onResult` 分两条路 ✓
  · **流过字** ⇒ `setComposingText(最终文本)` + `finishComposingText()` —— 只**定稿**，不再 commitText ✓
  · **没流过**（SenseVoice / 联网 API）⇒ 照旧 `commitText` ✓（无预览，不会重复 ✓）
  · `onError` / 新一轮听写 ⇒ 复位 ✓

## 二、使用说明增补（经用户审核通过 ✓）
- 「流式 Zipformer」小节加「⚠️ 对硬件（CPU）要求较高，见【六】」✓
- 新增 **【六、硬件性能提示】**：老机型"不出字"多为 **CPU 不足**（模型能加载、解不出结果 ✓）
  **非模型文件损坏** ✓；建议 ① 优先 SenseVoice ✓ ② 或换更小模型 ✓ 并**注明精度可能下降** ✓

## 三、v7a 流式语音真机结论（K20P 实测 ✓）
- **v7a + 流式 = 可用** ✓（只是模型加载/首字**偏慢** ✓ —— SD855 正常表现 ✓，用户判定"硬件问题，无碍" ✓）
- 修正上一轮推测 ✗：不是"算力不够不可用"，而是"**能用但慢**" ✓

---

> 以下为 **1.0.3（vc118）→ 1.0.4（vc146）** 的开发记录（19.113 ~ 19.146）。
> 这一段的主体是**手写输入整条线**（调研 → 模型转换 → JNI → 手写板 → 嵌入模式 → 模型下载）。

# 轮19.113 ~ 19.119：手写输入从调研到「识别接通」

## 一、路线选定（19.113 / 19.114）
- 调研两条路：**在线 API** ✗ vs **离线图片识别** ✓ ⇒ 选离线（不依赖网络、不上传笔迹 ✓）
- 模型选 **DeepHCCR**（GoogLeNet 结构，MIT 授权，论文精度 95.3%）✓
  · 用户明确要求：**不用 Xime 的模型** ✗
- 确认权重来源：`github.com/chongyangtao/DeepHCCR` 仓库内 `models/googlenet_hccr.caffemodel`（≈39MB）✓

## 二、Caffe → ONNX（19.117 ~ 19.119）
- 手机端只有 ONNX Runtime ⇒ 必须先把 `.caffemodel + deploy.prototxt` 转成 ONNX ✓
- 19.118：转换脚本**结构性完成** ✓ 但形状语义待收尾 ✗
- 19.119：★★ **转换成功并验证 4/4 命中** ✓✓ ⇒ 产出 `model.onnx`（量化后约 10MB）+ `labels.txt` ✓

## 三、前端接线（19.115 / 19.116）
- 19.115：嵌入式**重做** ✓ + 手写入口与检测 ✓（debug vc119）
- 19.116：手写接进**工具栏** ✓ + 键盘编辑器动作 ✓（debug vc120）

# 轮19.120 ~ 19.124：手写板手感 / 嵌入三态 / 识别全线接通

- 19.120：用户实测反馈「手写板生涩、不顺滑」✗ ⇒ 调整画笔与采样
- 19.121：确认「画板手感 + 路线本身」**都有问题** ✓（决策点）⇒ 嵌入模式扩成**三态**
  （不嵌入 / 嵌入编码 / **嵌入首选**）+ 新增「嵌入首选」（debug vc122 ✓）
- 19.123：★「嵌入首选」改为**输入码与首选候选位置对调** ✓（debug vc123 ✓）
- 19.124：★★ **手写识别全线接通**（JNI + 模型 + 手写板 ✓ debug vc124）✓

# 轮19.125 ~ 19.128：嵌入重复上屏真因 + 手写板重写 + 模型下载器

## 一、★ 嵌入模式的「重复上屏 / 要按两次退格」= 同一个根因（19.126）
- `AZimeService` 嵌入分支里调了 `finishComposingText()` ✗ ⇒ 把**预览定稿成真文本** ✗ ⇒
  ① 结束时 `commitText` 只能**追加** ⇒ 「还还好吧」✗ ② 退格删预览删不掉 ⇒ **要按两次** ✗
- 修法 ✓：嵌入模式（code / top）**一律禁用 `finishComposingText()`** ✗
  · 组合结束用 `setComposingText("", 1)` 清预览 ✓；提交交给 `commitText`（自带**替换**语义 ✓）
- 新增 `Embed` 埋点（mode / preedit / 预览 / 候选数 / 提交文本 ✓）⇒ 下次能直接还原时序 ✓

## 二、手写板重写（19.126）
- **自动识别** ✓：抬笔后 **800ms 停顿**即识别（期间再写重新计时 ✓ 不用点按钮 ✓）
- **结果进工具栏** ✓：新增 `hwCandidates` + `KeyAction.HwCandidates` ✓；点选即上屏、上屏后自动清空 ✓
- 去掉百分比 ✓（只显示字 + 序号）；**大面积书写** ✓（画布铺满整个键盘区）

## 三、模型下载器 `core/handwriting/ModelDownloader.kt`（19.126 ~ 19.127）
- **直连优先 → 失败自动依次试公益镜像** ✓（清单抄自 trime2「下载中心」`main.lua`，12 条 ✓ 全免费 ✓）
- 进度回调（256KB 节流 ✓）+ zip 解压（带目录穿越防护 ✓）
- 19.127：**只解需要的部分** ✓（`keepFilter` 默认只留 `.onnx` / `.txt` ✓）+ **解压完删下载包** ✓
  + 临时目录 `Documents/Oime/downloads/` ✓
- `extractTarBz2` ✓（commons-compress，**Maven 通** ✓）+ `VOICE_MODELS` 清单（SenseVoice 240MB / Zipformer 200MB）
- 📌 模型托管到**自己的 GitHub Release**：上传必须走 **`uploads.github.com`** ✓
  （`api.github.com` 的 assets 上传会 **404** ✗）
- ⚠️ 踩坑：Python heredoc 里把 `'\n'` 写成 `'\\n'` ✗ ⇒ 生成的字面量把源码搞坏 ✗
  ⇒ 教训：heredoc 里要真实换行就用单反斜杠，并在生成后**校验行数/行长** ✓

## 四、19.127 / 19.128 的真机返工
- 手写板变成**全屏** ✗ ⇒ 我重写面板时用了 `Modifier.fillMaxSize()` ✗（丢了原 `MenuSubPanel` 的高度约束 ✗）
  ⇒ 改 `fillMaxWidth().height(totalHeight)` ✓ —— 再次验证铁律：**面板一律用 `areaH` / `totalHeight`** ✓
- **上屏后画布没清空** ✗ ⇒ 新增 `hwClearSignal: Int` ✓：服务端提交后 `+1` → 面板 `LaunchedEffect` 清画布 ✓
- **语音设置块已从「方案」页搬进「语音手写管理」** ✓（brace-matching 精确切块 ✓ + 去掉 `item{}` 外壳 ✓）
- 19.128：手写板全屏**真修** ✓ + 管理页重写 ✓（debug vc128）

# 轮19.129 ~ 19.135：设置页统一观感（并揪出「回调被吃掉」的真凶）

- 19.129：手写板加**清空键** ✓ + 管理页版式对齐「输入方案」页 ✓
- 19.130：**页面重叠修复** ✓ + 去掉编号 ✓ + **统一可选按钮样式** ✓
- 19.132：版式回调 ✓ + 统一 `FilterChip` ✓ + 备份恢复 ✓
- 19.133：模型选择行也统一为 `FilterChip` ✓ + 备份列表挂到「恢复备份」项下 ✓
- 19.134：★ **无边框**选中样式 ✓ + 横向/竖向选项真改上了 ✓
- 19.135：★★ 找到「样式没回调」的**真凶 = 同名函数重载** ✗（改了一处不生效的典型 ✓）
  ⇒ 教训：Compose 里同名重载会导致**调用了另一个** ⇒ 改前先确认「点下去执行的是哪个函数」✓

# 轮19.136 ~ 19.138：模型下载「必然失败」的真因 + 崩溃修复

## 一、★★ 下载失败真因 = **manifest 少声明 `INTERNET`**（19.136）
- 日志铁证（`Dl` 埋点，12 个通道**同一原因**）✓：
  `[Dl] 直连 失败：Permission denied (missing INTERNET permission?)`
- 顺带发现：**「联网 API」语音识别其实一直是坏的** ✗（同样没权限）
- 修法 ✓：manifest 补 `<uses-permission android:name="android.permission.INTERNET" />` ✓
  + 核验 APK 里真有（`aapt2 dump permissions` ✓）
- 提示位置 ✓（用户要求）：写到**点击的那个模型行下方** ✓ ⇒ 新增 `msgFor`（按模型 id 存 ✓）
- ★ 教训：**新功能依赖系统权限时必须先查 manifest** ✗ + 及时读埋点日志 ✓（比猜快得多 ✓）

## 二、断点续传 + 完整性校验 + 闪退真因（19.137）
- 闪退铁证 ✓：`Ort::Exception: Load model from …/sense-voice/model.onnx failed: Protobuf parsing failed.`
  ⇒ 那个 `model.onnx`（**845MB** ✗）是**下载中断的残缺文件** ✗ ⇒ C++ 异常跨到 Java ⇒ **SIGABRT** ✗
- 修法 ✓（`ModelDownloader`）：
  · **全局作用域** ✓（`CoroutineScope(SupervisorJob()+IO)` 放在 object 里 ⇒ **切页不取消** ✓）
  · **全局状态** ✓（`DlState` + `states: mutableStateMapOf` ⇒ 切回来还能看到进度 ✓）
  · **断点续传** ✓（有 `.part` ⇒ `Range: bytes=N-` ✓ 206 续传 / 200 从头 ✓ + 追加写 ✓）
  · **★ 完整性校验** ✓（`done < total` ⇒ **抛错** ✗ 以前断流被当成功 ⇒ 残缺模型 ⇒ 崩溃 ✗✗）
  · 失败**保留 `.part`** ✓（以前删掉 ⇒ 永远从头 ✗）
- 解压**只留需要的** ✓（`tokens.txt` + `*.int8.onnx`）；⚠️ **两个分支都要套过滤器** ✗（原来只套了一个 ✗）

## 三、19.138：图标与过滤收严
- 手写图标重绘 ✓（24 网格描边铅笔 + 书写基线）；`byName("handwriting")` 指向修正 ✓
- zipformer 多解压一个 ✗ ⇒ 根因是我以为 "decoder 无 int8 版" ✗ 而放行了 `decoder*.onnx` ✗
  ⇒ **实测它有 int8 版** ✓ ⇒ 删掉该例外 ✓（规则只剩 `tokens.txt` + `*.int8.onnx`）
- 下载日志观察 ✓：代理通道断了很多次，但**最终成功** ⇒ 证明**断点续传生效** ✓

# 轮19.139 ~ 19.140：UI 四项调整 + 并列三卡

- 19.139：① 嵌入模式独立成方块 ✓ ② 新增 `OimeIcons.voiceHandwriting` ✓（「悬浮窗」同步改名
  「悬浮窗及嵌入式」✓）③ 麦克风权限行缩进对齐 ✓ ④ 向导补麦克风申请 ✓ ⑤ 出 v8a + v7a 双包 ✓
- 19.140：★ 拆分方式我上次**理解错了** ✗ —— 用户给的参照是「输入方案」页 ⇒ 要的是**并列卡片** ✓
  ⇒ `FloatingWindowSettings()` 拆成 **3 张并列 Card**（① 悬浮窗 ② 嵌入式 ③ 样式与数值 ✓）
  ⚠️ 收口括号：新增 Card/Column 要在函数末尾**补 2 个 `}`** ✓
- 图标重画 ✓：上一版是"麦克风 + 铅笔挤在一起" ✗ ⇒ 改为 **斜置铅笔 + 两道声波弧** ✓

# 轮19.141 ~ 19.143：闪退修复 + 卡片合并（连踩 3 次坑）

## 一、★ 点「悬浮窗及嵌入式」闪退（19.141）
- 崩溃铁证 ✓：`IllegalStateException: Vertically scrollable component was measured with an
  infinity maximum height constraints`
- 根因 ✗：我在 `FloatingWindowSettings()` 里加了 `fillMaxSize().verticalScroll(...)` ✗，
  而**调用处外层就是 `LazyColumn`** ⇒ item 给无限高度 ⇒ 抛异常 ✗
- 修法 ✓：删掉内层 `fillMaxSize` / `verticalScroll` / 重复 padding ⇒ 只留 `Column(spacedBy(12.dp))` ✓
  ⇒ 铁律：**`LazyColumn` 的 item 里禁止再套滚动容器** ✗

## 二、卡片合并的正确姿势（19.142 / 19.143）
- 需求：悬浮窗**开关**与**样式**合并成同一张卡 ✓；**嵌入式单独一个方块** ✓
- 踩坑 ✗✗✗：① **只 assert 没真删** ⇒ 卡片提前闭合 ⇒ 内容掉到卡外（**编译还能过** ✗ 只有看渲染才发现 ✗）
  ② **按缩进找函数结尾** ✗（收尾 `}` 未必顶格）③ **凭空推理括号配对** ✗（被字符串里的花括号带偏 ✗）
- 正解 ✓：**字符级扫描**（跳过字符串/注释）算**每行深度** ⇒ 用深度判断"卡内还是卡外" ✓；
  **尾部整体重建** ✓；找函数结尾用 `strip()=='}'` 且**其后第一个非空行以 `/**` 开头** ✓；
  改完复核 **EOF 深度 = 0** ✓ + **无负深度** ✓

# 轮19.144 ~ 19.146：v7a 旧机验证 + 微信表情退格 + 向导补权限

## 一、19.144：v7a 包装到旧机（红米 K20 Pro）
- 设备 `f25441b5` = Redmi K20 Pro（raphael / Android 15）✓
- 核对 ✓：`primaryCpuAbi=armeabi-v7a` ✓ / `secondaryCpuAbi=null` ✓（**确实吃到 v7a 那套 so** ✓）
- ⚠️ MIUI 上 `pm grant RECORD_AUDIO` 被拒 ✗ ⇒ 只能靠 App 内向导自己弹框 ✓

## 二、19.145：★★ 微信表情要按多次退格（根因定死 ✓）
- 根因 ✓：微信表情在输入框里的**底层文本是短代码** `[微笑]` / `[让我看看]` ✓（渲染成图片靠 `ImageSpan` ✗）
  老实现只判「1 码元」和「代理对」⇒ 一次只删掉 `]` ✗（前几次删的是**看不见的**修饰符 ✗）
- 真机铁证 ✓（`[Del]` 埋点）：
  · `[呲牙]` = 4 码元 ⇒ `chat=4 n=4` ✓
  · `[让我看看]` = 6 码元 ⇒ `chat=6 n=6` ✓
  · `[叹气]` = 4 码元 ⇒ `chat=4 n=4` ✓
  ⇒ **`n` 恒等于整个表情长度** ⇒ 一个表情一次删净 ✓（用户实测通过 ✓）
- 修法 ✓（`AZimeService.handleBackspace` 四级判定）：
  1. `spanBackedLength()` —— 光标前紧邻 `ReplacementSpan`/`ImageSpan` ⇒ 按 span 长度删
  2. `wechatEmoticonLength()` —— **仅 `com.tencent.mm`** ✓ + 光标前 `[...]` 且括号内 1~8 个
     **纯汉字或纯 ASCII 字母** ⇒ 整段删（`[1]` / `[a/b]` / `[链接](url)` 都不匹配 ⇒ 不误伤 ✓）
  3. `Grapheme.lastClusterLength()` 兜底 —— 扩展字素簇（变体选择符 / ZWJ / 国旗 / 肤色 / 键帽 ✓）
  4. 读文本必须带 **`GET_TEXT_WITH_STYLES`** ✓（否则多数编辑器走 `TextUtils.substring` 把 span 丢掉 ✗）
- 💡 实测反直觉的一点：微信这边 **`span=0` 而 `styled=true`** ⇒ **微信不走 ReplacementSpan** ✗
  ⇒ 真正解题的是第 2 条（短代码）✓ 第 1 条留给其它 App ✓

## 三、19.145 续：启动向导补「联网与模型下载」页
- 用户诉求「启动界面没有联网权限的索取界面，直接就能联网下模型了」
- **平台事实** ✓：`INTERNET` 是 **normal 权限** ⇒ 装机即授权 ✓，系统**不提供**运行时弹窗 ✗
  ⇒ 任何 App 都做不出"联网权限索取界面" ✗ ⇒ 改成**知情 + 显式开关** ✓
- 新增 `core/net/NetPrefs.kt` ✓（`net_allow_download` 默认开 ✓ / `net_wifi_only` **默认开** ✓
  + `NetState{WIFI,MOBILE,NONE,UNKNOWN}` + `downloadBlockReason()`）
- manifest 补 `ACCESS_NETWORK_STATE`（同为 normal ✓，只用来分辨 Wi-Fi/流量 ✓）
- 向导 5 页 → 6 页；设置页语音/手写管理顶部加「〇、联网下载」卡 ✓ + 移动网络下载前**二次确认** ✓
- 踩坑 ✗：`val net = com.azime.input.core.net`（**把包名当值用** ✗ Kotlin 不允许 ✗）

## 四、19.146：向导把「语音权限」做成独立一页（6 页 → 7 页）
- 改前 ✗：19.139 是「**进向导就 `LaunchedEffect` 自动弹**麦克风框」✗
  ⇒ 用户还没看到「语音」的说明就被要权限 ✗ 而且弹框与页面无关（停在第 1 页也照弹 ✗）
- 改法 ✓：新增第 4 页（索引 3）【语音输入权限】+ 删掉自动弹框逻辑 ✓
  · 新增 `micGranted()`（`RECORD_AUDIO` 是 **dangerous** ⇒ 有运行时弹窗 ✓ 与 `INTERNET` 不同 ✓）
  · 状态徽标「✓ 已授予麦克风权限 / 未授权（不影响打字）」+ 主按钮「授予麦克风权限 / 已授权 · 继续」
  · 索引平移：联网页 `page==3`→`4` ✓ 圆点 `repeat(6)`→`repeat(7)` ✓ 底部边界 `<5`→`<6` ✓
- 关于页新增「**本次更新**」条目（1.0.4 的新增/修改/修复 ✓）

## 五、1.0.4 发版
- `versionName = "1.0.4"` / `versionCode = 146` ✓（双 ABI：arm64-v8a + armeabi-v7a ✓）
- 两个 ABI 均已真机验证 ✓（一加 13 = arm64 ✓ 微信表情退格通过 ✓；K20 Pro = v7a ✓ 向导 7 页正常 ✓）
- 打 tag `v1.0.4` ⇒ 触发 Release 工作流（按 ABI 出正式签名包 ✓；fork 无 Secrets 自动回退 debug 签名 ✓）

## 六、真机验证环境备忘（2026-09-25）
- ⚠️ **红米 K20 Pro（MIUI/HyperOS）上 `adb shell input tap` 被拦** ✗：
  `SecurityException: Injecting input events requires ... INJECT_EVENTS` ⇒ 该机不能程序化点击 ✗
  （一加 13 可以 ✓）⇒ 老机只能**用户手点** ✓
- ⚠️ 向导在**已配置好的机器上必然不出现** ✓（`alreadyDone` = `wizard_done` **或**
  存储已授权 + IME 已启用 + 是默认输入法）⇒ 重现改用
  `run-as <pkg> rm -f shared_prefs/wizard_prefs.xml` ✓（debug 包可用 ✓ 别用 `pm clear` ✗）

