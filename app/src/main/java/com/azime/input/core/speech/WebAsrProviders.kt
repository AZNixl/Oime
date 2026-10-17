package com.azime.input.core.speech

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/**
 * 轮19.171：**联网语音提供商**（页面改版 ✓ 用户要求）
 *
 * - 内置 5 家：智谱 BigModel / 阿里云百炼 / 硅基流动 / 阶跃星辰 / OpenAI（顺序 = 界面顺序 ✓）
 * - **每家独立记忆配置**（Base URL / API Key / 模型 / 调用方式）✓ 切换回来还记得 ✓
 * - 「新增 API」= 自定义提供商（用户命名，可编辑 / 删除 ✓）
 * - 当前生效的提供商记在 `wasr_active` ✓（引擎按它取配置 ✓）
 */
object WebAsrProviders {

    private const val PREFS = "speech_prefs"
    private const val K_ACTIVE = "wasr_active"
    private const val K_CUSTOM = "wasr_custom"     // JSON 数组
    private const val PREFIX = "wasr_"             // wasr_<id>_url / _key / _model / _protocol

    data class Provider(
        val id: String,
        val name: String,
        val baseUrl: String,
        val model: String,
        val protocol: String,
        val custom: Boolean = false,
    )

    /** 某家的**已保存配置**（未保存时 url/model 用预设默认 ✓ key 为空 ✓）。 */
    data class Cfg(
        val url: String,
        val key: String,
        val model: String,
        val protocol: String,
    )

    /** 内置预设（顺序即界面顺序 ✓ 已按用户要求去掉 Groq / Mistral ✗）。 */
    val PRESETS: List<Provider> = listOf(
        Provider("zhipu", "智谱 BigModel", "https://open.bigmodel.cn/api/paas/v4",
            "glm-asr-2512", SpeechEngineManager.PROTO_TRANSCRIBE),
        Provider("bailian", "阿里云百炼", "https://dashscope.aliyuncs.com/compatible-mode/v1",
            "qwen3-asr-flash", SpeechEngineManager.PROTO_CHAT),
        Provider("siliconflow", "硅基流动", "https://api.siliconflow.cn/v1",
            "FunAudioLLM/SenseVoiceSmall", SpeechEngineManager.PROTO_TRANSCRIBE),
        Provider("stepfun", "阶跃星辰", "https://api.stepfun.com/v1",
            "step-audio-2.5-asr", SpeechEngineManager.PROTO_TRANSCRIBE),
        Provider("openai", "OpenAI", "https://api.openai.com/v1",
            "whisper-1", SpeechEngineManager.PROTO_TRANSCRIBE),
    )

    private fun prefs(c: Context): SharedPreferences = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // ── 提供商列表 ─────────────────────────────────────────────

    /** 自定义提供商（「新增 API」添加的 ✓）。 */
    fun customProviders(c: Context): List<Provider> {
        val raw = prefs(c).getString(K_CUSTOM, "") ?: ""
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val id = o.optString("id")
                if (id.isBlank()) return@mapNotNull null
                Provider(
                    id = id,
                    name = o.optString("name", "自定义"),
                    baseUrl = o.optString("url", ""),
                    model = o.optString("model", ""),
                    protocol = o.optString("protocol", SpeechEngineManager.PROTO_TRANSCRIBE),
                    custom = true,
                )
            }
        }.getOrDefault(emptyList())
    }

    /** 全部提供商（内置在前 ✓ 自定义在后 ✓）。 */
    fun all(c: Context): List<Provider> = PRESETS + customProviders(c)

    fun byId(c: Context, id: String): Provider? = all(c).firstOrNull { it.id == id }

    // ── 当前生效 ───────────────────────────────────────────────

    fun activeId(c: Context): String = prefs(c).getString(K_ACTIVE, "") ?: ""

    fun setActive(c: Context, id: String) {
        prefs(c).edit().putString(K_ACTIVE, id).apply()
    }

    /** 当前生效的提供商的联网配置（引擎用 ✓；未配置 key 时返回 null ✓）。 */
    fun activeConfig(c: Context): SpeechEngineManager.WebApiConfig? {
        migrateLegacy(c)
        val p = byId(c, activeId(c)) ?: return null
        val cfg = cfgOf(c, p)
        if (cfg.url.isBlank() || cfg.key.isBlank()) return null
        return SpeechEngineManager.WebApiConfig(
            baseUrl = cfg.url.trimEnd('/'),
            apiKey = cfg.key,
            model = cfg.model.ifBlank { p.model },
            protocol = cfg.protocol,
        )
    }

    // ── 单家配置读写 ───────────────────────────────────────────

    fun cfgOf(c: Context, p: Provider): Cfg {
        val sp = prefs(c)
        return Cfg(
            url = sp.getString("${PREFIX}${p.id}_url", "")?.ifBlank { p.baseUrl } ?: p.baseUrl,
            key = sp.getString("${PREFIX}${p.id}_key", "") ?: "",
            model = sp.getString("${PREFIX}${p.id}_model", "")?.ifBlank { p.model } ?: p.model,
            protocol = sp.getString("${PREFIX}${p.id}_protocol", "")?.ifBlank { p.protocol } ?: p.protocol,
        )
    }

    fun saveCfg(c: Context, id: String, url: String, key: String, model: String, protocol: String) {
        prefs(c).edit()
            .putString("${PREFIX}${id}_url", url.trim())
            .putString("${PREFIX}${id}_key", key.trim())
            .putString("${PREFIX}${id}_model", model.trim())
            .putString("${PREFIX}${id}_protocol", protocol)
            .apply()
        // 自定义提供商：同步更新列表里的名称/默认值（列表页展示要用 ✓）
        val customs = customProviders(c).toMutableList()
        val idx = customs.indexOfFirst { it.id == id }
        if (idx >= 0) {
            customs[idx] = customs[idx].copy(baseUrl = url.trim(), model = model.trim(), protocol = protocol)
            saveCustoms(c, customs)
        }
    }

    /** 是否已配置（有 key 就算 ✓）。 */
    fun isConfigured(c: Context, p: Provider): Boolean = cfgOf(c, p).key.isNotBlank()

    // ── 自定义提供商增删 ───────────────────────────────────────

    fun addCustom(c: Context, name: String): Provider {
        val p = Provider(
            id = "c" + System.currentTimeMillis().toString(36),
            name = name.ifBlank { "自定义" },
            baseUrl = "",
            model = "",
            protocol = SpeechEngineManager.PROTO_TRANSCRIBE,
            custom = true,
        )
        saveCustoms(c, customProviders(c) + p)
        return p
    }

    fun renameCustom(c: Context, id: String, name: String) {
        saveCustoms(c, customProviders(c).map { if (it.id == id) it.copy(name = name) else it })
    }

    fun removeCustom(c: Context, id: String) {
        saveCustoms(c, customProviders(c).filterNot { it.id == id })
        prefs(c).edit()
            .remove("${PREFIX}${id}_url").remove("${PREFIX}${id}_key")
            .remove("${PREFIX}${id}_model").remove("${PREFIX}${id}_protocol")
            .apply()
        if (activeId(c) == id) prefs(c).edit().putString(K_ACTIVE, "").apply()
    }

    private fun saveCustoms(c: Context, list: List<Provider>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(JSONObject().apply {
                put("id", it.id); put("name", it.name)
                put("url", it.baseUrl); put("model", it.model); put("protocol", it.protocol)
            })
        }
        prefs(c).edit().putString(K_CUSTOM, arr.toString()).apply()
    }

    // ── 旧版单一配置迁移（不丢用户已经填好的 Key ✓）──────────────

    private fun migrateLegacy(c: Context) {
        val sp = prefs(c)
        if (!activeId(c).isNullOrEmpty()) return
        val oldUrl = sp.getString("web_api_url", "") ?: ""
        val oldKey = sp.getString("web_api_key", "") ?: ""
        if (oldUrl.isBlank() || oldKey.isBlank()) return
        val oldModel = sp.getString("web_api_model", "") ?: ""
        val oldProto = sp.getString("web_api_protocol", SpeechEngineManager.PROTO_TRANSCRIBE)
            ?: SpeechEngineManager.PROTO_TRANSCRIBE
        // 按 URL 认出是哪家 ✓ 认不出 ⇒ 建成"自定义"（保留可用 ✓）
        val matched = PRESETS.firstOrNull { it.baseUrl.trimEnd('/') == oldUrl.trim().trimEnd('/') }
        val id = if (matched != null) matched.id else {
            val p = addCustom(c, "我的 API")
            p.id
        }
        sp.edit()
            .putString("${PREFIX}${id}_url", oldUrl.trim())
            .putString("${PREFIX}${id}_key", oldKey)
            .putString("${PREFIX}${id}_model", oldModel.ifBlank { matched?.model ?: "" })
            .putString("${PREFIX}${id}_protocol", oldProto)
            .putString(K_ACTIVE, id)
            .apply()
    }
}
