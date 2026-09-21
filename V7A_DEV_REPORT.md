# v7a（armeabi-v7a）开发路径与模拟器选型报告

> 日期：2026-09-21 · 适用项目：○输入法（Oime, github.com/AZNixl/Oime）
> 背景：v7a 已暂时撤回发行（预编译 so 缺 `DT_HASH`，Android 5.x 必失败 ✗），
> 若后续要继续做 v7a，本报告给出**不影响主仓库**的开发路径与测试方案。

---

## 一、跑 debug 会不会影响主仓库？

**结论：只要不在 `main` 上推代码，就不会影响主仓库 ✓** —— 具体分三层：

| 动作 | 会不会动到 main | 说明 |
|---|---|---|
| **本地跑 `./gradlew assembleDebug` / `assembleRelease`** | **不会** ✓ | 纯本地行为；只在 `app/build/` 下产生产物，和远程无关 ✓ |
| **推到新建分支**（如 `dev/v7a`） | **不会** ✓ | 分支独立；`main` 只在**合并**时才变 ✓ |
| **推 tag `v*`** | ⚠️ 会**触发发行** | `release.yml` 监听的正是 tag ⇒ 别用 tag 做实验 ✗ |
| **推到 `main` / `develop`** | 会触犯构建（CI 跑 debug APK）| 这是 `android.yml` 的触发条件，改动会进主干 ✗ |

**本仓库工作流的触发条件**（已核查 ✓）：

- `.github/workflows/android.yml` —— `push: [main, develop]`、`pull_request: [main]`、`workflow_dispatch`
- `.github/workflows/release.yml` —— `push: tags: ['v*']`、`workflow_dispatch`

⇒ **新建一个 `dev/v7a` 分支推代码：两个工作流都不会自动跑** ✓；
需要出包时用 **手动触发**（Actions 页面点 `Run workflow`，或 API `POST /actions/workflows/android.yml/dispatches`）✓

> 本机 `.git` 已损坏（`git add` 报 `bad tree object HEAD` ✗）⇒ 建分支/推送一律走 **GitHub API** ✓
> （仓库里 `~/.workbuddy/skills/github-api-push/` 的脚本即为此准备 ✓）

---

## 二、分支 / fork / 独立项目，怎么选？

| 方案 | 会不会影响主仓库 | 优点 | 缺点 | 适用 |
|---|---|---|---|---|
| ⭐ **新建分支 `dev/v7a`（推荐）** | 不会 ✓ | 零额外维护；一个仓库内切换；PR 流程天然可用 | 分支多了要清理 | **本场景最合适** ✓ |
| **Fork 一份** | 不会 ✓✓（最安全） | 完全隔离；可以随便实验（改包名/加 ABI/换库） | 要手动同步上游；issue/PR 跨仓库处理 | 想**大幅试错**（例如同时试 x86、换 librime 来源）时 ✓ |
| **另建独立项目** | 不会 ✓ | 最干净 | 代码会与主仓库分叉 ✗ 后续功能要手动搬 | 不推荐 ✓（除非要做成另一个 App）|

### 为什么这里推荐**分支**就够

v7a 开发实际要改的东西**极少** ✓ —— 只有两处：

1. `app/src/main/jniLibs/armeabi-v7a/librime_jni.so`（放回那个 so 即可 ✓）
2. 无 —— 构建脚本**已按 jniLibs 内容自适应** ✓（`splits.abi` 会自动把 v7a 纳入 ✓）

⇒ 改动是**一个文件** ✓ ⇒ 分支完全够用，且合并/放弃都只涉及一个文件 ✓

### 具体怎么做（API 方式，因本地 .git 损坏）

```bash
# 1) 从 main 的 HEAD 建分支
POST /repos/AZNixl/Oime/git/refs
     { "ref": "refs/heads/dev/v7a", "sha": "<main 的 head sha>" }

# 2) 往分支推文件：把 tree 的 base_tree 指向分支 HEAD，ref 也指向分支
#    （用仓库内 github-api-push 脚本，把 GH_BRANCH 设为 dev/v7a ✓）

# 3) 需要出测试包时手动触发 CI
POST /repos/AZNixl/Oime/actions/workflows/android.yml/dispatches
     { "ref": "dev/v7a" }
#    产物在 Actions 里下载 app-debug 工件（保 3 天 ✓）

# 4) 满意了再合并：开 PR（POST /repos/.../pulls, base=main, head=dev/v7a）
```

**全程 `main` 不动** ✓；要回退就把分支删掉 ✓（`DELETE /repos/.../git/refs/heads/dev/v7a`）✓

---

## 三、模拟器选型

### 3.1 先明确一个硬约束 ⚠️

本应用的输入核心是**原生库**（`librime_jni.so` / `sherpa-onnx` / `onnxruntime`），
**必须 ABI 匹配**才能加载 ✓ ——

- **x86 / x86_64 模拟器**：装 arm 版 APK 会 `UnsatisfiedLinkError` ✗（除非该模拟器自带 **ARM 翻译层**）
- **ARM 模拟器**：能真正加载 arm 库 ✓，但速度慢（尤其冷启动）✗

⇒ **"能不能跑起来"取决于模拟器是不是 ARM（或有 ARM 翻译）**，不只是安卓版本 ✓

### 3.2 各方案对比

| 方案 | 能加载 v7a 原生库？ | 覆盖老安卓版本 | 速度 | 备注 |
|---|---|---|---|---|
| ⭐ **官方 AVD（ARM 镜像）**<br>`android-23;google_apis;armeabi-v7a` | **能** ✓ | **能到 5.0/5.1/6.0** ✓ | 慢 ✗（纯软件模拟）| **免装第三方** ✓、与 adb/CI 同一套工具 ✓、**首选** ✓ |
| 官方 AVD（x86_64，API 30+） | ✗ 默认不能（Google 镜像已去掉 ARM 翻译 ✗） | 只到新版本 ✗ | 快 ✓ | 只能测 **UI/交互/设置页** ✓（装 arm 包会崩 ✗）|
| **MuMu 12 / 雷电 9 / 蓝叠 5** | 部分 ✓（自带 ARM 翻译，但多为**新安卓** ✗） | 差 ✗（Android 9 / 12 为主）| 很快 ✓✓ | 适合**日常 UI 体验** ✓；测 v7a **老系统** 不合适 ✗ |
| **Waydroid**（Linux 容器） | 能 ✓（可自选镜像） | 视镜像 ✓ | 较快 ✓ | 需 **Linux/WSL2** ✗，Windows 上折腾成本高 ✗ |
| **QEMU + 自制 AOSP arm 镜像** | 能 ✓ | 全都能 ✓ | 慢 ✗ | 最灵活、最费事 ✗ 不推荐 |
| ⭐⭐ **真机（你手上那台 Android 5.1 arm32 / Moto XT1085）** | **能** ✓✓ | **就是 5.1** ✓✓ | 最快 ✓✓ | **最有价值** ✓ —— 很多问题（比如这次的 `DT_HASH` ✗）只在真机复现 ✓ |

### 3.3 推荐组合

> **真机为主（必测）+ 官方 ARM AVD 为辅（回归/自动化时用）** ✓

理由：
1. 你已经有 **Android 5.1 arm32 真机** ✓ —— 这正是 v7a 的目标场景 ✓，且**免费、最快、最真实** ✓
2. 官方 ARM AVD 用来做**回归测试**（清空数据/换版本/复现干净环境 ✓），并可将来接进 CI ✓
3. 第三方模拟器留给**日常界面体验**（启动快 ✓），但**不作为 v7a 的判定依据** ✗

### 3.4 建 ARM 模拟器的命令

```bash
# 需要 Android Studio / cmdline-tools
sdkmanager "system-images;android-23;google_apis;armeabi-v7a"
avdmanager create avd -n oime-arm23 \
  -k "system-images;android-23;google_apis;armeabi-v7a" -d pixel
emulator -avd oime-arm23        # 冷启动较慢，耐心等
adb install -r oime-1.0.0-vcXXX-armeabi-v7a.apk
```

> 若 ARM 镜像列表里找不到某个版本：用 `sdkmanager --list | grep system-images` 查可用项 ✓
> （Google 的 ARM 镜像**止步于 API 30 左右** ✗，所以**老版本恰好都有** ✓ —— 对我们反而有利 ✓）

---

## 四、如果要继续做 v7a，技术上还差什么（基于本次实测）

| 项目 | 现状 | 结论 |
|---|---|---|
| `librime_jni.so`（v7a） | 已从 Xime v2.6.2 官方 release 取到；**已补 `DT_HASH`** ✓ | 可用 ✓（vc104+ 起）|
| `libsherpa-onnx-*.so`（v7a） | 自带 `DT_HASH` ✓ | 可用 ✓ |
| **`libonnxruntime.so`（v7a）** | **缺 `DT_HASH`** ✗ | 在 Android 5.x 上会 dlopen 失败 ✗（语音功能不可用）✓ 其余功能不受影响 ✓ |
| 安卓版本 | minSdk 已提到 23 ✓ | **5.0/5.1 设备：引擎不可用** ✗；**6.0+ 的 32 位设备：可用** ✓ |

⇒ 结论：**v7a 在 Android 6.0+ 的 32 位机上是可以支持的** ✓；
若要覆盖 **Android 5.x** ✗，还需给 `libonnxruntime.so` 也补 `DT_HASH`（同一手术 ✓），
或者干脆**不用 onnxruntime**（语音走联网 API / 或用别的推理后端 ✓）

---

## 五、一页总结

1. **不动 main 就行** ✓ —— 本地 build 无影响 ✓；推新分支不会触发任何工作流 ✓；**别推 tag**（会发版 ✗）
2. **用 `dev/v7a` 分支** 即可 ✓（改动只有 1 个 so 文件 ✓，构建脚本已自适应 ✓）；想大幅试错再考虑 fork ✓
3. **测试首选你手上那台真机** ✓（唯一能复现 `DT_HASH` 这类问题的地方 ✓）；
   需要干净/自动化环境时建 **官方 ARM AVD（API 23）** ✓；第三方模拟器只适合看 UI ✓
4. v7a 若要发：**Android 6.0+ 可用** ✓；5.x 需再补 `libonnxruntime.so` 的 `DT_HASH` ✓
