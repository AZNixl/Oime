package com.azime.input.core.speech

import android.util.Base64
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import okio.ByteString.Companion.toByteString
import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * 轮19.185：**OpenAI Realtime 风格**的实时语音识别客户端（一次覆盖三家 ✓）
 *
 * 三家都用「JSON 事件 + base64 PCM」这套：
 * - **OpenAI**  `wss://api.openai.com/v1/realtime?intent=transcription`（`gpt-4o-transcribe`）
 * - **阶跃**    `wss://api.stepfun.com/v1/realtime/asr/stream`（`stepaudio-2.5-asr-stream`）
 * - **百炼**    `wss://dashscope.aliyuncs.com/api-ws/v1/realtime`（`qwen3-asr-flash-realtime`）
 *
 * 事件：`session.update` → `input_audio_buffer.append`（音频 ✓）→ `input_audio_buffer.commit`
 * 结果：`…transcription.delta` / `…transcription.completed`（也兼容 `transcript.text.*` ✓）
 *
 * ⚠️ 三家 schema 有细微差别（见 [Profile]）⇒ 出错时日志会写 `oa-rt …`，按事件名定位 ✓
 */
object RealtimeAsrClient {

    private const val CHUNK_BYTES = 6400          // 200ms @16k/16bit/mono ✓

    private val http: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .pingInterval(15, TimeUnit.SECONDS)
            .build()
    }

    data class LiveResult(val ok: Boolean, val text: String, val error: String = "")

    /** 每家的端点 / 默认实时模型 / 会话配置（含 provider 特有字段 ✓）。 */
    data class Profile(
        val url: String,
        val model: String,
        val extraHeaders: Map<String, String> = emptyMap(),
        val sendCommit: Boolean = true,
        /** 轮19.187：模型必须放在 **URL query** 里（百炼 ✓ 否则会退回默认 omni 模型 ✗） */
        val needsModelQuery: Boolean = false,
        /** 轮19.188：结束时要发 `session.finish`（百炼 ✓ 官方明确：不发会丢结果 ✗） */
        val sendFinish: Boolean = false,
        val sessionJson: (String) -> String,
    )

    fun profileFor(providerId: String): Profile? = when (providerId) {
        // 阶跃：session.update 里有 codec/rate/bits/channel + server_vad ✓
        "stepfun" -> Profile(
            url = "wss://api.stepfun.com/v1/realtime/asr/stream",
            model = "stepaudio-2.5-asr-stream",
            sendCommit = false,
            sessionJson = { m ->
                """{"event_id":"evt-1","type":"session.update","session":{"audio":{"input":{"format":{"type":"pcm","codec":"pcm_s16le","rate":16000,"bits":16,"channel":1},"transcription":{"model":"$m","language":"zh"},"turn_detection":{"type":"server_vad","silence_duration_ms":800,"threshold":0.6}}}}}"""
            },
        )
        // 百炼：DashScope realtime（OpenAI 兼容子集 ✓）
        "bailian" -> Profile(
            url = "wss://dashscope.aliyuncs.com/api-ws/v1/realtime",
            model = "qwen3-asr-flash-realtime",
            needsModelQuery = true,                       // ★ 不带 ?model= 会退回 qwen-omni-turbo-realtime ✗
            sendFinish = true,                            // ★ 官方：不发 session.finish 会丢结果 ✗
            extraHeaders = mapOf("OpenAI-Beta" to "realtime=v1"),
            sessionJson = { _ ->
                // 官方 Manual 模式示例：input_audio_format 必须是 "pcm" ✗（"pcm16" 会报 Audio format is not valid ✗）
                // 模型由 URL 的 ?model= 指定 ⇒ 这里 **不再** 带 model ✓；input_audio_transcription 只放 language ✓
                """{"event_id":"evt-1","type":"session.update","session":{"modalities":["text"],"input_audio_format":"pcm","sample_rate":16000,"input_audio_transcription":{"language":"zh"},"turn_detection":null}}"""
            },
        )
        // OpenAI：Realtime transcription session ✓
        "openai" -> Profile(
            url = "wss://api.openai.com/v1/realtime?intent=transcription",
            model = "gpt-4o-transcribe",
            extraHeaders = mapOf("OpenAI-Beta" to "realtime=v1"),
            sessionJson = { m ->
                """{"type":"session.update","session":{"type":"transcription","audio":{"input":{"format":{"type":"audio/pcm","rate":16000},"transcription":{"model":"$m"},"turn_detection":null}}}}"""
            },
        )
        else -> null
    }

    /** 是否支持实时（豆包走自己的 SAUC，不在这里 ✓）。 */
    fun supports(providerId: String): Boolean = profileFor(providerId) != null

    /**
     * 打开实时会话 ✓（[onPartial] 在 OkHttp 线程回调 ⇒ 调用方自行切主线程 ✓）。
     */
    fun openLive(
        providerId: String,
        apiKey: String,
        modelOverride: String,
        onPartial: (String) -> Unit,
        onFailure: (String) -> Unit,
    ): Session? {
        val prof = profileFor(providerId) ?: return null
        val model = modelOverride.trim().ifBlank { prof.model }
        val fullUrl = if (prof.needsModelQuery && !prof.url.contains("model=")) {
            prof.url + (if (prof.url.contains("?")) "&" else "?") + "model=" + model
        } else prof.url
        com.azime.input.core.diag.Diag.log("Speech", "oa-rt connect $providerId url=${fullUrl.take(80)}")
        val req = Request.Builder()
            .url(fullUrl)
            .header("Authorization", "Bearer ${apiKey.trim()}")
            .apply { prof.extraHeaders.forEach { (k, v) -> header(k, v) } }
            .build()

        lateinit var session: Session
        val listener = object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                com.azime.input.core.diag.Diag.log("Speech", "oa-rt open ✓ $providerId model=$model")
                runCatching {
                    ws.send(prof.sessionJson(model))
                    session.markOpen()
                }.onFailure { session.onErr("发送会话配置失败：${it.message}") }
            }

            override fun onMessage(ws: WebSocket, text: String) {
                session.onEvent(text)
            }

            override fun onMessage(ws: WebSocket, bytes: ByteString) {
                // 少数实现会发二进制（这里按 UTF-8 JSON 兜底 ✓）
                runCatching { session.onEvent(bytes.utf8()) }
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                com.azime.input.core.diag.Diag.warn("Speech", "oa-rt failure: ${t.message}")
                session.onErr("连接失败：${t.message ?: "未知"}")
            }

            override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                session.onClosed(code, reason)
            }
        }
        session = Session(http.newWebSocket(req, listener), prof, onPartial, onFailure)
        return session
    }

    class Session internal constructor(
        private val ws: WebSocket,
        private val prof: Profile,
        private val onPartial: (String) -> Unit,
        private val onFailure: (String) -> Unit,
    ) {
        private val lock = Any()
        private val pending = java.io.ByteArrayOutputStream()
        private var ready = false
        private var opened = false
        @Volatile private var partialText = ""
        @Volatile private var finalText = ""
        @Volatile private var error = ""
        @Volatile private var finished = false
        private val doneLatch = CountDownLatch(1)

        internal fun markOpen() {
            synchronized(lock) {
                opened = true
                ready = true
                flushLocked()
            }
        }

        /** 录音线程喂 PCM（16k/16bit/mono ✓）—— 未建连前先缓冲 ✓（避免"音频先于配置"✗）。 */
        fun feedPcm(pcm: ShortArray, n: Int) {
            val bb = ByteBuffer.allocate(n * 2).order(ByteOrder.LITTLE_ENDIAN)
            for (i in 0 until n) bb.putShort(pcm[i])
            synchronized(lock) {
                if (finished) return
                pending.write(bb.array())
                if (ready) flushLocked()
            }
        }

        private fun flushLocked() {
            while (pending.size() >= CHUNK_BYTES && !finished) {
                val all = pending.toByteArray()
                val head = all.copyOfRange(0, CHUNK_BYTES)
                pending.reset()
                pending.write(all, CHUNK_BYTES, all.size - CHUNK_BYTES)
                val b64 = Base64.encodeToString(head, Base64.NO_WRAP)
                ws.send("""{"type":"input_audio_buffer.append","audio":"$b64"}""")
            }
        }

        internal fun onEvent(json: String) {
            val o = runCatching { JSONObject(json) }.getOrNull() ?: return
            val type = o.optString("type", "")
            if (type.isBlank()) return
            // 记录关键事件（不记音频 ✓）
            if (!type.contains("speech_started") && !type.contains("speech_stopped")) {
                com.azime.input.core.diag.Diag.log("Speech", "oa-rt evt=$type")
            }
            when {
                // 百炼：conversation.item.input_audio_transcription.text（实时结果 ✓ 字段 text）
                type.endsWith("input_audio_transcription.text") -> {
                    val cur = o.optString("text", "").ifBlank { o.optString("transcript", "") }
                    if (cur.isNotBlank() && cur != partialText) {
                        partialText = cur
                        onPartial(cur)
                    }
                }
                type.endsWith("transcription.delta") || type.endsWith("transcript.text.delta") -> {
                    val d = o.optString("delta", "")
                    if (d.isNotEmpty()) {
                        partialText += d
                        onPartial(partialText)
                    }
                }
                type.endsWith("transcription.completed") || type.endsWith("transcript.text.done")
                    || type.endsWith("transcription.done") -> {
                    val t = o.optString("transcript", "").ifBlank { o.optString("text", "") }
                    if (t.isNotBlank()) {
                        finalText = t
                        onPartial(t)
                    }
                    finished = true
                    doneLatch.countDown()
                }
                type == "session.finished" -> {
                    finished = true
                    doneLatch.countDown()
                }
                type == "error" || type.endsWith(".error") -> {
                    val msg = o.optJSONObject("error")?.optString("message", "")
                        ?: o.optString("message", "")
                    onErr(msg.ifBlank { json.take(120) })
                }
            }
        }

        internal fun onErr(msg: String) {
            if (error.isBlank()) error = msg
            finished = true
            doneLatch.countDown()
        }

        internal fun onClosed(code: Int, reason: String) {
            if (!finished) {
                if (partialText.isNotBlank()) finalText = partialText
                else if (error.isBlank()) error = "连接被关闭（$code ${reason.take(40)}）"
            }
            finished = true
            doneLatch.countDown()
        }

        /** 结束并取最终结果 ✓（阻塞，调用方在 IO ✓）。 */
        fun finish(): LiveResult {
            if (!finished) {
                if (!opened) {
                    onErr("连接未建立（握手未完成）")
                } else {
                    synchronized(lock) {
                        // 把不足 200ms 的尾巴也发出去 ✓
                        if (pending.size() > 0 && !finished) {
                            val rest = pending.toByteArray()
                            pending.reset()
                            val b64 = Base64.encodeToString(rest, Base64.NO_WRAP)
                            ws.send("""{"type":"input_audio_buffer.append","audio":"$b64"}""")
                        }
                    }
                    if (prof.sendCommit) {
                        runCatching { ws.send("""{"type":"input_audio_buffer.commit"}""") }
                    }
                    // ★ 官方要求：关闭连接前必须发 session.finish，否则服务端丢弃 in_progress item ✗
                    if (prof.sendFinish) {
                        runCatching { ws.send("""{"type":"session.finish"}""") }
                        com.azime.input.core.diag.Diag.log("Speech", "oa-rt 已发 session.finish ✓")
                    }
                }
            }
            runCatching { doneLatch.await(12_000, TimeUnit.MILLISECONDS) }
            runCatching { ws.close(1000, null) }
            return when {
                finalText.isNotBlank() -> LiveResult(true, finalText.trim())
                partialText.isNotBlank() -> LiveResult(true, partialText.trim())
                error.isNotBlank() -> LiveResult(false, "", error)
                else -> LiveResult(false, "", "未收到识别结果（超时或服务端无返回）")
            }
        }

        fun cancel() { runCatching { ws.close(1000, null) } }
    }
}
