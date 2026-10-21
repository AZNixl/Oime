package com.azime.input.core.ai

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * 轮19.182：**输入法 AI 能力**（统一入口）
 *
 * 设计：
 * - **提供商与 Key 复用**「语音手写管理 → 联网语音」里的配置 ✓（同一套 `wasr_<id>_*` ✓）
 *   ⇒ 用户只需配一次 Key ✓ AI 与语音共用 ✓
 * - 本模块只管：**用哪个提供商 / 用哪个对话模型 / 各功能开关** ✓
 * - 调用统一走 **OpenAI 兼容** `POST {baseUrl}/chat/completions` ✓（智谱/百炼/硅基流动/阶跃/OpenAI 都支持 ✓）
 */
object AiManager {

    private const val PREFS = "ai_prefs"
    private const val K_PROVIDER = "ai_provider"
    private const val K_MODEL = "ai_model"

    const val F_CLIPBOARD = "ai_clipboard"   // 剪贴板智能整理
    const val F_TRANSLATE = "ai_translate"   // 翻译输入
    const val F_REWRITE = "ai_rewrite"       // 风格改写
    const val F_EXPLAIN = "ai_explain"       // 划词解释/搜索/总结
    const val F_CONTINUE = "ai_continue"     // 智能续写
    const val F_COMMAND = "ai_command"       // 口头指令

    /** 各功能默认开关（先默认关 ✓ 用户自己开 ✓ 隐私友好） */
    private val DEFAULTS = mapOf(
        F_CLIPBOARD to false, F_TRANSLATE to false, F_REWRITE to false,
        F_EXPLAIN to false, F_CONTINUE to false, F_COMMAND to false,
    )

    /** 各提供商的**默认对话模型**（ASR 的 model 字段不能拿来对话 ✗ 必须分开 ✓） */
    val DEFAULT_CHAT_MODEL = mapOf(
        "zhipu" to "glm-4-flash",
        "bailian" to "qwen-plus",
        "siliconflow" to "Qwen/Qwen2.5-7B-Instruct",
        "stepfun" to "step-1-flash",
        "openai" to "gpt-4o-mini",
    )

    private fun prefs(c: Context): SharedPreferences = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** AI 可选的提供商（豆包语音是语音专用 ✗ 不参与对话 ✓）。 */
    fun providers(c: Context) =
        com.azime.input.core.speech.WebAsrProviders.all(c).filter { it.id != "doubao" }

    fun providerId(c: Context): String =
        prefs(c).getString(K_PROVIDER, "") ?: ""

    fun setProvider(c: Context, id: String) = prefs(c).edit().putString(K_PROVIDER, id).apply()

    /** 生效的 AI 提供商（未选 ⇒ 跟随语音当前提供商 ✓；再不行就用第一个 ✓）。 */
    fun provider(c: Context) = providers(c).firstOrNull { it.id == providerId(c) }
        ?: com.azime.input.core.speech.WebAsrProviders.byId(c, com.azime.input.core.speech.WebAsrProviders.activeId(c))
            ?.takeIf { it.id != "doubao" }
        ?: providers(c).firstOrNull()

    fun model(c: Context): String {
        val saved = prefs(c).getString(K_MODEL, "") ?: ""
        if (saved.isNotBlank()) return saved
        val p = provider(c) ?: return "gpt-4o-mini"
        return DEFAULT_CHAT_MODEL[p.id] ?: ""
    }

    fun setModel(c: Context, m: String) = prefs(c).edit().putString(K_MODEL, m.trim()).apply()

    fun isEnabled(c: Context, feature: String): Boolean =
        prefs(c).getBoolean(feature, DEFAULTS[feature] ?: false)

    fun setEnabled(c: Context, feature: String, on: Boolean) =
        prefs(c).edit().putBoolean(feature, on).apply()

    /** 当前是否可用（有提供商 + 已填 Key ✓）。 */
    fun isReady(c: Context): Boolean {
        val p = provider(c) ?: return false
        return com.azime.input.core.speech.WebAsrProviders.cfgOf(c, p).key.isNotBlank()
    }

    data class Result(val ok: Boolean, val text: String, val error: String = "")

    /**
     * 一次对话（**阻塞，必须在 IO 线程调用** ✓）。
     * @param system 系统提示词（决定功能：翻译/改写/解释… ✓）
     * @param user 用户内容（文本）
     * @param imageBase64 可选：PNG 的 base64（多模态 ✓ 用于手写兜底 ✓）
     */
    fun chat(context: Context, system: String, user: String, imageBase64: String? = null): Result {
        val p = provider(context) ?: return Result(false, "", "未选择 AI 提供商")
        val cfg = com.azime.input.core.speech.WebAsrProviders.cfgOf(context, p)
        if (cfg.key.isBlank()) return Result(false, "", "「${p.name}」还没填 API Key（语音手写管理 → 联网语音）")
        val model = model(context).ifBlank { DEFAULT_CHAT_MODEL[p.id] ?: "" }
        if (model.isBlank()) return Result(false, "", "未设置 AI 模型")
        val base = cfg.url.trimEnd('/')
        val url = when {
            base.endsWith("/v1") || base.contains("/api/v3") || base.contains("/paas/v4") -> "$base/chat/completions"
            else -> "$base/v1/chat/completions"
        }
        return runCatching {
            val userContent = if (imageBase64 == null) JSONObject().put("role", "user").put("content", user)
            else JSONObject().put("role", "user").put(
                "content",
                JSONArray()
                    .put(JSONObject().put("type", "text").put("text", user))
                    .put(
                        JSONObject().put("type", "image_url").put(
                            "image_url",
                            JSONObject().put("url", "data:image/png;base64,$imageBase64"),
                        ),
                    ),
            )
            val payload = JSONObject().apply {
                put("model", model)
                put("stream", false)
                put("temperature", 0.3)
                put("messages", JSONArray()
                    .put(JSONObject().put("role", "system").put("content", system))
                    .put(userContent))
            }
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 10_000
                readTimeout = 45_000
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Authorization", "Bearer ${cfg.key.trim()}")
            }
            conn.outputStream.use { it.write(payload.toString().toByteArray()) }
            val code = conn.responseCode
            val body = if (code in 200..299) conn.inputStream.bufferedReader().readText()
            else conn.errorStream?.bufferedReader()?.readText().orEmpty()
            com.azime.input.core.diag.Diag.log("AI", "chat $model http=$code len=${body.length}")
            if (code !in 200..299) {
                return@runCatching Result(false, "", "AI 请求失败（$code）：${body.take(120)}")
            }
            val text = runCatching {
                JSONObject(body).optJSONArray("choices")?.optJSONObject(0)
                    ?.optJSONObject("message")?.optString("content", "").orEmpty()
            }.getOrDefault("")
            if (text.isBlank()) Result(false, "", "AI 返回为空") else Result(true, text.trim())
        }.getOrElse { Result(false, "", "网络失败：${it.message ?: "未知"}") }
    }

    // ── 各功能的系统提示词 ────────────────────────────────────

    const val SYS_TRANSLATE = "你是输入法翻译助手。把用户给的文本翻译成%s，只输出译文，不要解释、不要引号。若原文已是目标语言则输出原文。"
    const val SYS_REWRITE = "你是输入法改写助手。把用户给的文本改写为%s，保持原意与语言，只输出改写结果，不要任何说明。"
    const val SYS_EXPLAIN = "你是输入法里的解释助手。用不超过 80 字简洁解释用户给的内容；若像网址/名词则说明它是什么；不要客套。"
    const val SYS_SUMMARIZE = "你是输入法里的总结助手。用不超过 60 字概括用户给的内容，只输出概括。"
    const val SYS_CONTINUE = "你是输入法续写助手。根据用户已输入的内容，自然地续写一句（不超过 30 字），只输出续写内容，不要重复原文。"
    const val SYS_CLIPBOARD = "你是剪贴板整理助手。从用户给的杂乱文本里提取关键信息（姓名/电话/地址/时间/单号/链接），用简洁的列表输出；没提取到的项不要编造。"
    const val SYS_COMMAND = "你是输入法指令助手。用户会用口语提出写作需求，请直接给出可插入的成品文本（不要解释）。"
}
