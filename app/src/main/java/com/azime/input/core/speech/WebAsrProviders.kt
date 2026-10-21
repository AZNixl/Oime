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
        /** 轮19.186：**是否支持语音识别**（Kimi/DeepSeek 只做 AI ✗ 不进语音列表 ✓） */
        val asr: Boolean = true,
    )

    /** 某家的**已保存配置**（未保存时 url/model 用预设默认 ✓ key 为空 ✓）。 */
    data class Cfg(
        val url: String,
        val key: String,
        val model: String,
        val protocol: String,
        /** 火山旧版控制台的 AppID（可选 ✓ 新版留空 ✓） */
        val appId: String = "",
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
            "stepaudio-2.5-asr", SpeechEngineManager.PROTO_TRANSCRIBE),
        // 轮19.176：**豆包语音（火山语音）** ✓ 走「录音文件识别极速版」：同步返回、base64 直传 ✓
        // 鉴权 = 控制台 API Key（X-Api-Key）✓「模型」字段在这家存 **Resource ID** ✓
        // 轮19.179：改走 **SAUC WebSocket**（HTTP 极速版多数新账号不可用 ✗）
        // 默认「一句话识别」（准确率优先 ✓ 适合输入法）；流式用 bigmodel_async ✓ 资源同为 2.0 小时版 ✓
        Provider(
            "doubao", "豆包语音（火山）",
            "wss://openspeech.bytedance.com/api/v3/sauc/bigmodel_nostream",
            "volc.seedasr.sauc.duration", SpeechEngineManager.PROTO_VOLC,
        ),
        Provider("openai", "OpenAI", "https://api.openai.com/v1",
            "whisper-1", SpeechEngineManager.PROTO_TRANSCRIBE),
        // 轮19.186：**仅 AI** 的提供商（无语音识别 ✗ ⇒ 不进「联网语音」✓）
        Provider("kimi", "Kimi（月之暗面）", "https://api.moonshot.cn/v1",
            "moonshot-v1-8k", SpeechEngineManager.PROTO_TRANSCRIBE, asr = false),
        Provider("deepseek", "DeepSeek", "https://api.deepseek.com/v1",
            "deepseek-chat", SpeechEngineManager.PROTO_TRANSCRIBE, asr = false),
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

    /** 只支持**语音识别**的提供商（联网语音页用 ✓ 排除 Kimi/DeepSeek 等纯 AI ✗）。 */
    fun asrProviders(c: Context): List<Provider> = all(c).filter { it.asr }

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
            appId = cfg.appId,
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
            appId = sp.getString("${PREFIX}${p.id}_appid", "") ?: "",
        )
    }

    fun saveCfg(
        c: Context, id: String, url: String, key: String, model: String, protocol: String,
        appId: String = "",
    ) {
        prefs(c).edit()
            .putString("${PREFIX}${id}_url", url.trim())
            .putString("${PREFIX}${id}_key", key.trim())
            .putString("${PREFIX}${id}_model", model.trim())
            .putString("${PREFIX}${id}_protocol", protocol)
            .putString("${PREFIX}${id}_appid", appId.trim())
            .apply()
        // 自定义提供商：同步更新列表里的名称/默认值（列表页展示要用 ✓）
        val customs = customProviders(c).toMutableList()
        val idx = customs.indexOfFirst { it.id == id }
        if (idx >= 0) {
            customs[idx] = customs[idx].copy(baseUrl = url.trim(), model = model.trim(), protocol = protocol)
            saveCustoms(c, customs)
        }
    }

    /** 轮19.185：**实时（边说边出）开关**（豆包走 URL 判断 ✓ 其它家用这个 ✓）。 */
    fun liveEnabled(c: Context, id: String): Boolean =
        prefs(c).getBoolean("${PREFIX}${id}_live", false)

    fun setLiveEnabled(c: Context, id: String, on: Boolean) =
        prefs(c).edit().putBoolean("${PREFIX}${id}_live", on).apply()

    /** 这家是否支持实时（豆包 = SAUC ✓ 阶跃/百炼/OpenAI = Realtime ✓）。 */
    fun supportsLive(id: String): Boolean =
        id == "doubao" || com.azime.input.core.speech.RealtimeAsrClient.supports(id)

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
            .remove("${PREFIX}${id}_appid")
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
        // 轮19.174：**已迁移过但旧键还在** ⇒ 一次性清掉 ✓
        // （否则同一把 Key 在 prefs 里有两份明文副本 ✗ 用户升级路径也会残留 ✗）
        // 轮19.179：豆包语音从「HTTP 极速版」切到「SAUC WebSocket」⇒ 旧值自动升级 ✓
        sp.getString("${PREFIX}doubao_url", "")?.let { u ->
            if (u.contains("/auc/bigmodel/")) {
                sp.edit().putString(
                    "${PREFIX}doubao_url",
                    "wss://openspeech.bytedance.com/api/v3/sauc/bigmodel_nostream",
                ).apply()
            }
        }
        sp.getString("${PREFIX}doubao_model", "")?.let { m ->
            if (m == "volc.bigasr.auc_turbo" || m.isBlank()) {
                sp.edit().putString("${PREFIX}doubao_model", "volc.seedasr.sauc.duration").apply()
            }
        }
        if (!activeId(c).isNullOrEmpty()) {
            if (sp.contains("web_api_key") || sp.contains("web_api_url")) {
                sp.edit()
                    .remove("web_api_url").remove("web_api_key")
                    .remove("web_api_model").remove("web_api_protocol")
                    .apply()
            }
            return
        }
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
            // 轮19.174：迁移完成 ⇒ **清掉旧键** ✓（否则同一把 Key 有两份明文副本 ✗）
            .remove("web_api_url").remove("web_api_key")
            .remove("web_api_model").remove("web_api_protocol")
            .apply()
    }
}
