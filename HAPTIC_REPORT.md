# 震动调研报告：Oime vs Xime（「嗡嗡」问题定位）

> 日期：2026-09-22 · 反馈来源：Redmi Note 12 Turbo（X 轴线性马达）用户反馈「震动嗡嗡嗡」
> 结论先行：**两者接入方式不一样** ✗ —— 我们发的是「满幅度裸 one-shot」，Xime 默认走「系统键盘触感」。
> 已在 main 上按 Xime 思路修复（`HapticsManager`），并出 debug 待真机验证 ✓

---

## 一、两边实现对比（源码级）

| 维度 | **Xime**（`FeedbackManager.kt`） | **Oime（修复前）**（`HapticsManager.kt`） |
|---|---|---|
| **振动器获取** | API 31+ 用 `VibratorManager` → `defaultVibrator` ✓；否则 `VIBRATOR_SERVICE` | 只用已废弃的 `VIBRATOR_SERVICE` ✗ |
| **系统触感开关** | `HapticMode.FollowingSystem`（默认）会读 `Settings.System.HAPTIC_FEEDBACK_ENABLED` ✓，关了就不振 | **不读** ✗，只看自家开关 |
| **默认怎么振** | **`view.performHapticFeedback(KEYBOARD_TAP, FLAG_IGNORE_VIEW_SETTING)`** ✓ ⇒ 用**系统键盘的触感波形**（ROM 调好的短促 tick ✓） | **`VibrationEffect.createOneShot(15ms, DEFAULT_AMPLITUDE)`** ✗ ⇒ 裸波形、**满幅度** |
| **幅度** | 检查 `hasAmplitudeControl()` ✓，用**用户设定的幅度** | 不检查 ✗，一律 `DEFAULT_AMPLITUDE`（≈满驱 ✗） |
| **抬起振动** | `KEYBOARD_RELEASE`（API 27+ ✓），且默认**关** | 无该概念，靠子开关 ✗ |
| **usage / 通道** | 走系统触感通道 ✓ | 无 usage ✗ |
| 可调项 | 按下/长按的**时长 + 幅度**分别可调 | 只有时长 ✗ |

## 二、「嗡嗡嗡」的根因

1. 我们每次按键都发 **`createOneShot(15ms, 满幅度)`** ✗
2. 小米（MIUI / HyperOS）的**震动服务**对「**不带 usage 的裸波形**」会按**通用马达驱动**渲染 ✗
   —— 它不是系统键盘的「触感反馈」通道，于是得到**一声闷响 / 嗡** ✗
3. 反馈那台是 **X 轴线性马达**（Note 12 Turbo）✓ —— 线性马达对这种**长驱/满幅**波形特别"诚实" ✗
   （转子马达时代会被掩盖 ✗）
4. 结果：**打字密集时就成了"嗡嗡嗡"** ✓

> 注：Xime 如果用户**手动设了自定义时长**，也会走 one-shot ✗ —— 但它**默认不走**这条路 ✓
> 所以"同一个上游"并不等于"同样的震感" ✓ —— 差别就在**默认值**上 ✗

## 三、修复方案（已实现于 main）

| 修复点 | 做法 |
|---|---|
| **默认走系统 tick** | API 29+ 用 `VibrationEffect.createPredefined(EFFECT_TICK)` ✓（与系统键盘同源，短促清脆 ✓） |
| **不带 usage 的问题** | Android 12+ 尝试带 usage（键盘/触感通道 ✓）→ 失败则退回普通 `vibrate` ✓（常量名各版本不一致，故加兜底 ✓） |
| **幅度上限** | 有幅度控制时才自定幅度：**自定义 160 / 默认 90** ✓（不再满驱 ✗） |
| **振动器获取** | 对齐 Xime：API 31+ 用 `VibratorManager.defaultVibrator` ✓ |
| **自定义模式** | 仍保留（用户显式选「自定义时长」时才走 one-shot ✓），时长夹紧 5~60ms ✓ |
| 未采用 | 「读系统 `HAPTIC_FEEDBACK_ENABLED`」—— 我们已有自家总开关 ✓，避免"两处开关打架" ✓（如需可加 ✓）|

## 四、你手上的 Redmi K20P（HyperOS 2）有没有用？

**非常有用 ✓✓** —— 三个理由：

1. **同一套震动栈** ✓：K20P 刷的 **HyperOS 2** 与 Note 12 Turbo 的 MIUI/HyperOS **同源** ✓
   ⇒ 「裸波形 → 嗡」这条行为**极可能复现** ✓（这正是问题所在层 ✗）
2. **同样是线性马达** ✓（K20P 是 Z 轴 ✓，Note 12 Turbo 是 X 轴 ✓）—— 轴不同但都是线性 ✓，
   对"长驱/满幅"的反馈方向一致 ✓
3. **可反复对比** ✓：装 debug 包 → 打字听感 → 切「系统 / 自定义」两种模式对比 ✓，
   能直接确认修复是否生效 ✓

> 局限：**具体音色**会因马达型号不同而有差异 ✗（K20P 的 Z 轴 vs Note 12 Turbo 的 X 轴 ✓）
> ⇒ 你能确认「**不嗡了、变清脆了**」✓；最终是否满意还要那台机器的人反馈 ✓

## 五、建议的验证步骤（K20P 上）

1. 装本报告附带的 **debug 包**（arm64 ✓）
   - ⚠️ 若提示签名冲突 ✗：先卸载已装的 Oime（旧包是 CI/正式签名 ✗），再装 debug ✓
2. 打开 App → **设置 → 打字振动**：确认「模式 = 系统默认」✓（这是修复后的新路径 ✓）
3. **打字听感** ⇒ 期望：**短促"嗒"** ✓，而不是"嗡" ✗
4. 再把模式切到「自定义时长」→ 调 15/30/50ms 听差别 ✓（自定义走 one-shot ✓，可对比）
5. 结论回我 ✓ —— 若仍嗡 ✗，我再往「完全改用 `performHapticFeedback` 系统键盘触感」那条彻底方案走 ✓


---

# 附篇：搜狗输入法在 K20 Pro 上是怎么调振动的？（实测调研）

> 方法：把 K20 Pro（HyperOS 2 / Android 15）的当前输入法切到**搜狗**（`com.sohu.inputmethod.sogou.xiaomi`
> · uid **10183**）→ 清空 logcat → 用户打字 → 抓 `VibratorManagerService` 日志 ✓

## 实测结果

```
8 次 × uid=1000 and with attrs=VibrationAttributes{mUsage=TOUCH}
```

**关键点（两条）**：

1. **发起方是 `uid=1000`（系统）** ✗ —— **不是**搜狗自己的 uid（10183 ✗）
   ⇒ 说明搜狗**没有**直接调 `Vibrator.vibrate()` ✗，
   而是走**框架的按键触感**（`View.performHapticFeedback` / `HapticFeedbackConstants` ✓），
   由**系统**代为发出 ✓
2. **通道（usage）是 `USAGE_TOUCH`** ✓ —— 这是系统为「触摸/按键触感」专门维护的通道 ✓
   （对比：我们 A 版传的是 `12` ✗ → 日志显示 **`unknown usage 12`** ✗，ROM 根本不认 ✗）

另外注意时长：搜狗的每次振动约 **37ms** ✓（对比我们 A 版 ≈ **14ms** ✗）—— 这也是"手感差异"的一部分 ✓

## 结论：这正好印证了 B 版是对的

| | 发起方 | 通道 | 手感 |
|---|---|---|---|
| **搜狗**（机器上"好手感"的基准 ✓） | **系统**（uid=1000 ✓） | **`USAGE_TOUCH`** ✓ | 短促清脆 ✓ |
| **Oime B 版**（=`performHapticFeedback(KEYBOARD_TAP)` ✓） | 预期同为**系统** ✓ | 预期同为 **`USAGE_TOUCH`** ✓ | **用户实测：更好** ✓✓ |
| **Oime A 版**（=`createPredefined(EFFECT_TICK)` ✗） | 我们自己（app uid ✗） | `unknown usage 12` ✗ | 一般 ✗ |

⇒ **B 与系统键盘（含搜狗）走的是同一条路** ✓ —— 用户实测 B 更好 ✓，与平台数据吻合 ✓✓

## 方法学备注（这台机器上的两个坑）

- ⚠️ `dumpsys vibrator_manager` 在 HyperOS 上**不留振动历史** ✗（只有 Current/Next ✗）
  ⇒ 客观证据只能靠**实时 logcat 抓 `VibratorManagerService`** ✓
- ⚠️ `adb shell input tap` 被拒 ✗（`SecurityException: INJECT_EVENTS` ✗）
  ⇒ 想自动化点按需开 **开发者选项 → 「USB 调试（安全设置）」** ✓（需登小米账号 ✓）
