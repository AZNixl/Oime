package com.azime.input.core.rime

import android.content.Context
import com.azime.input.core.keyboard.KeyboardManager
import java.io.File

/**
 * 轮19.61：把「候选快捷键」**写进 RIME 配置**（`default.custom.yaml` 的 key_binder）并触发重部署。
 *
 * 为什么改成写配置：
 * - 之前在 App 层拦截按键，遇到"标点键走哪条路径"的问题（CHARACTER / FUNCTION / Resolved / UI 直出…），
 *   埋点证明有的键**根本不经过 Service**，拦截不可靠 ✗
 * - 写进 `key_binder` 后由 **RIME 引擎自己选候选** ⇒ 与按键路径无关，稳定可靠 ✓
 *
 * 规则（写入 `key_binder.bindings`）：
 * - 先删除所有**生效中的** `send: 2 / 3` 候选绑定（避免新旧抢按键）
 * - 再按设置写入：字符类 → `accept: "<字符>"`；`shift` → `Shift_L`
 *   （`symbols` 等非 RIME 键由 App 层处理，不写进配置）
 * - 「无」= 不写（等于解除绑定）
 */
object RimeKeyBinder {

    /** 部署用的共享用户目录（与 RimeManager.sharedDirOf 保持一致）。 */
    private fun configFile(context: Context): File =
        File(context.filesDir, "rime/shared/default.custom.yaml")

    /** 把当前候选快捷键设置写入配置。返回 true 表示配置内容有变化。 */
    fun applyCandidateBindings(context: Context): Boolean = runCatching {
        val f = configFile(context)
        if (!f.exists()) return@runCatching false
        val origin = f.readText()
        val lines = origin.lines().toMutableList()

        // 1) 删掉所有生效中的候选选择绑定（send: 2 / send: 3）
        val candRe = Regex("""^\s*-\s*\{.*send:/s*[23]/s*/}/s*$""")
        lines.removeAll { candRe.matches(it) }

        // 2) 找到 bindings: 的插入点
        var insertAt = -1
        for (i in lines.indices) {
            if (lines[i].trimStart().startsWith("bindings:")) {
                insertAt = i + 1
                break
            }
        }
        if (insertAt < 0) return@runCatching false

        // 3) 生成新行
        val newLines = mutableListOf<String>()
        fun add(code: String, n: Int) {
            if (code.isEmpty()) return
            val accept = when (code.lowercase()) {
                "shift" -> "Shift_L"
                "control", "ctrl" -> "Control_L"
                "alt" -> "Alt_L"
                "symbols" -> return   // 非 RIME 键：由 App 层处理
                else -> "\"$code\""
            }
            newLines.add(
                "      - { when: has_menu, accept: $accept, send: $n }   # 轮19.61 候选$n（设置里可改）",
            )
        }
        add(KeyboardManager.candidateKey2(), 2)
        add(KeyboardManager.candidateKey3(), 3)

        val out = (lines.subList(0, insertAt) + newLines + lines.subList(insertAt, lines.size))
            .joinToString("\n")
        if (out == origin) return@runCatching false
        f.writeText(out)
        true
    }.getOrDefault(false)

    /** 应用并重部署（设置页「保存」调用）。 */
    suspend fun applyAndDeploy(context: Context) {
        applyCandidateBindings(context)
        RimeManager.deployImportedSchemas(context)
    }
}
