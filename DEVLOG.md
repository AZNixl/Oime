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
