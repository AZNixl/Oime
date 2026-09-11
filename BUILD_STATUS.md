# ○输入法 / Oime 项目状态

> 最后更新：2026-09-11（轮19.7，vc30 / 0.9.20-oime 装机验证）

## 项目信息

- **项目名称**：○输入法（Oime）
- **版本**：0.9.20-oime（versionCode 30）
- **包名**：`com.azime.input`
- **仓库**：https://github.com/AZNixl/Oime （曾名 AZime，main 分支）
- **技术栈**：Kotlin 1.9.22 / AGP 8.3.0 / Gradle 8.4 / Compose BOM 2024.02.00
- **构建目标**：compileSdk 34 / minSdk 24 / targetSdk 34 / JDK 17
- **引擎**：librime（`librime_jni.so`，arm64-v8a）+ 平台 RIME；方案组目录即 librime user 数据目录（trime2 架构，零拷贝）
- **语音**：sherpa-onnx `1.13.5`（AAR 不进仓库，CI 构建时下载）+ SenseVoice / 流式 zipformer / OpenAI 兼容 API

## 构建与产物

| 环境 | 结果 |
|---|---|
| GitHub Actions（ubuntu-latest，`assembleDebug`） | ✅ 每轮 success，约 5~7 分钟 |
| Windows 本机 `./gradlew compileDebugKotlin` | ✅ 语法/类型校验用，约 1~2 分钟（**不在本机出 APK**） |

- 产物 `app-debug.apk` **约 55.9 MB**（含 sherpa-onnx arm64 so ≈30MB：libonnxruntime 20MB +
  libsherpa-onnx-jni 4MB + c-api 4MB + cxx-api + librime_jni 5MB）
- CI 步骤：checkout → JDK17 → `chmod +x gradlew` → **下载 sherpa-onnx AAR** →
  `assembleDebug` → 上传 APK / reports
- AAR 不提交仓库（49MB 超 Git Data API blob 上限），由 CI `curl` 官方 release 获取

### 本机环境

| 组件 | 路径 |
|---|---|
| Android SDK | `C:\Users\HinYoung\AppData\Local\Android\Sdk` |
| JDK 17 | `C:\Program Files\Eclipse Adoptium\jdk-17.0.20.8-hotspot` |
| adb | SDK `platform-tools/adb.exe`（USB 真机，包名 `com.oime.input`） |

本机构建/推送注意：

1. 项目路径含中文（`Desktop\搞机\...`）→ `gradle.properties` 里 `android.overridePathCheck=true` 绕过
2. AGP 8.3 不能配 Gradle 9，wrapper 固定 Gradle 8.4
3. **本机 `.git` 已损坏**（`bad tree object HEAD`）：不能用 `git add/commit/push`；
   改用 `push_via_api_tree.py`（遍历工作树 + 内置忽略规则 → Git Data API）

## 功能完成度（截至 vc30）

| 模块 | 状态 | 说明 |
|---|---|---|
| 输入法服务 `AZimeService` | ✅ 可用 | 全链路：按键 → librime → 候选 → 上屏；退格选区/撤回、剪贴板条、语音、工具栏 |
| 键盘视图 `KeyboardScreen` | ✅ 可用 | 26键 / 九宫格 / 26键符号 / 全部符号 / emoji 五页；键面图标（OimeIcons 21 枚自绘） |
| RIME 引擎 `RimeManager` | ✅ 可用 | 方案组（组目录即 user 目录）、在线切组、部署、方案显示名缓存 |
| 键盘编辑器 | ✅ 可用 | 可视化网格编辑：标签/code/宽度/长按/四向滑动/提示，增删键与行，保存即持久化 |
| 设置页 | ✅ 可用 | KSU 风格主页；输入方案 / 键盘 / O 圆环 / 主题配色 / 悬浮窗 / 预设置 / 关于 |
| 语音输入 | ✅ 可用 | SenseVoice（离线）/ 流式 zipformer / 联网 API；模型侧载 `Documents/Oime/models/` |
| 剪贴板 | ✅ 可用 | 历史 + 收藏、分词、全清、置顶、图片落盘；复制条任意键消亡 |
| O 圆环 | ✅ 可用 | 点击菜单 / 长按语音 / 拖动移光标（呼啦圈）/ 下滑收键盘 / **上滑快捷应用弧**；三形状（圆环·圆角方·眼睛） |
| 首次向导 | ✅ 可用 | 5 页：存储 / 启用 / 选择 / 进入设置 / O 圆环应用（含可读应用数） |
| 字体管理 | ✅ 可用 | `Documents/Oime/fonts/` 扫描，键帽/候选双角色回退链 |
| 单元测试 | ❌ 无 | junit/espresso 依赖在，零用例 |

## 已知技术债与风险

- **本机 git 仓库损坏**：历史不可用，只能经 API 推送；网络可达时建议重新 clone
- 远程存在一条重复 commit（`e64f3888` 与 `a5db01d0` 内容相同，无害）
- 耗电优化（vc30）已落地 4 项，**待 1~2 天实机统计验证**：原 24h 7.77%
- `KeyboardPages`（编辑器预览数据）与 `KeyboardScreen` 内联渲染是**两套**布局定义，
  改布局必须同步——轮19.3 的「改了不生效」即此坑
- R8/混淆未启用（CI 出的是 debug 包）
- `QUERY_ALL_PACKAGES` 已声明（O 圆环应用弧需要）；上架 Google Play 需说明用途

## 版本推进（轮19 系列）

| 版本 | vc | 内容 |
|---|---|---|
| 0.9.13-oime | 23 | 语音三引擎 + 方案管理页精简 + sherpa-onnx AAR |
| 0.9.14-oime | 24 | 退格撤回操作栈 / 剪贴板刷新+分词全清 / 震动滑杆 / 四向提示 / 方案页刷新键 |
| 0.9.15-oime | 25 | popup 滑动 / 页面 popup 图标化 / 符号页返回 / 九宫格高度 / 版本号动态 / 九宫格 00 |
| 0.9.16-oime | 26 | ○环松手关闭 / 九宫格增高行 Spacer / NumpadPane 硬编码修复 / 语音波纹 |
| 0.9.17-oime | 27 | OimeIcons 图标集 + 键面图标 / 复制条任意键消亡 / 字号收敛 / 呼啦圈 |
| 0.9.18-oime | 28 | 图标去重影 / 空格键显示方案名称 |
| 0.9.19-oime | 29 | 工具栏间距 / 面板高度公式 / O 圆环三形状 + 上滑应用弧 / 黑底白圆环图标 |
| 0.9.20-oime | 30 | 耗电优化四项（显示名缓存 / 方案列表按需 / 轮询有上限 / 图标懒加载） |

详细逐轮记录见 [DEVLOG.md](DEVLOG.md)。
