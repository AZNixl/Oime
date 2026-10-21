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
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * 轮19.179：**火山「豆包语音」SAUC WebSocket 客户端**
 *
 * 火山没有 HTTP 同步 ASR（极速版资源多数新账号不可用 ✗），官方给输入法场景的是：
 * - 一句话识别：`wss://openspeech.bytedance.com/api/v3/sauc/bigmodel_nostream`（准确率优先 ✓）
 * - 双向流式：  `wss://openspeech.bytedance.com/api/v3/sauc/bigmodel_async`（低延迟增量 ✓）
 *
 * 二进制帧（实测确认 ✓）：
 * ```
 * 客户端 → 服务端
 *   header(4B) + payloadSize(4B) + payload
 *     header: [0]=0x11(ver1,size1) [1]=msgType<<4|flags [2]=serialization<<4|compression [3]=0
 *     msgType: 1 = full client request(JSON) / 2 = audio only(裸 PCM)
 *     flags  : 0 = 普通 / 2 = 最后一包(无 sequence)
 * 服务端 → 客户端
 *   header(4B) + sequence(4B, 仅 flags=0b0001/0b0011 时存在) + payloadSize(4B) + payload(JSON)
 * ```
 * 结果：`result.text`（增量/累计 ✓ 取最后一次非空值 ✓）；结束帧 `flags=0b0011` ✓
 */
object VolcSaucClient {

    private const val CHUNK_BYTES = 6400          // 200ms @16k/16bit/mono ✓ 官方推荐分包 ✓
    private const val MAX_TOTAL_MS = 25_000L      // 整体超时 ✓

    data class Result(val ok: Boolean, val text: String, val error: String)

    private val http: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS)   // WS 长连接：不设读超时 ✓
            .pingInterval(15, TimeUnit.SECONDS)
            .build()
    }

    /**
     * 阻塞式转写（**必须在 IO 线程调用** ✓）。[endpoint] 为完整 wss 地址 ✓。
     * [appId] 非空 ⇒ 旧版鉴权（X-Api-App-Key + X-Api-Access-Key）；否则 X-Api-Key ✓
     */
    fun transcribe(
        endpoint: String,
        resourceId: String,
        apiKey: String,
        appId: String,
        pcm: ShortArray,
    ): Result {
        val url = endpoint.trim().ifBlank { "wss://openspeech.bytedance.com/api/v3/sauc/bigmodel_nostream" }
        val res = resourceId.trim().ifBlank { "volc.seedasr.sauc.duration" }
        val req = Request.Builder()
            .url(url)
            .header("X-Api-Resource-Id", res)
            .header("X-Api-Request-Id", UUID.randomUUID().toString())
            .header("X-Api-Connect-Id", UUID.randomUUID().toString())
            .apply {
                if (appId.isNotBlank()) {
                    header("X-Api-App-Key", appId.trim())
                    header("X-Api-Access-Key", apiKey.trim())
                } else {
                    header("X-Api-Key", apiKey.trim())
                }
            }
            .build()

        val latch = CountDownLatch(1)
        var text = ""
        var error = ""
        var finished = false

        val listener = object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                com.azime.input.core.diag.Diag.log("Speech", "volc ws open ✓")
                runCatching {
                    // ① 配置帧（JSON ✓ 不压缩 ✓）
                    val cfg = JSONObject().apply {
                        put("user", JSONObject().put("uid", "oime").put("platform", "Android"))
                        put("audio", JSONObject().apply {
                            put("format", "pcm"); put("codec", "raw")
                            put("rate", 16000); put("bits", 16); put("channel", 1)
                        })
                        put("request", JSONObject().apply {
                            put("model_name", "bigmodel")
                            put("enable_itn", true)
                            put("enable_punc", true)
                            put("show_utterances", true)
                        })
                    }.toString().toByteArray()
                    ws.send(frame(1, 0, cfg, isJson = true).toByteString())

                    // ② 音频帧（200ms 一块 ✓）
                    val bytes = toLittleEndian(pcm)
                    var off = 0
                    while (off < bytes.size) {
                        val end = minOf(off + CHUNK_BYTES, bytes.size)
                        ws.send(frame(2, 0, bytes.copyOfRange(off, end), isJson = false).toByteString())
                        off = end
                    }
                    // ③ 结束帧（flags=2 ✓ 空 payload ✓）
                    ws.send(frame(2, 2, ByteArray(0), isJson = false).toByteString())
                }.onFailure {
                    error = "发送失败：${it.message}"
                    latch.countDown()
                }
            }

            override fun onMessage(ws: WebSocket, bytes: ByteString) {
                runCatching {
                    val data = bytes.toByteArray()
                    if (data.size < 4) return@runCatching
                    val msgType = (data[1].toInt() shr 4) and 0x0F
                    val flags = data[1].toInt() and 0x0F
                    var pos = 4
                    // 有 sequence 时先跳 4 字节 ✓（实测服务端 flags=0b0001 ✓）
                    if (flags == 0b0001 || flags == 0b0011) pos += 4
                    if (data.size < pos + 4) return@runCatching
                    val size = ByteBuffer.wrap(data, pos, 4).order(ByteOrder.BIG_ENDIAN).int
                    pos += 4
                    val body = String(data, pos, minOf(size, data.size - pos), Charsets.UTF_8)
                    when (msgType) {
                        0x9 -> {
                            val t = runCatching {
                                JSONObject(body).optJSONObject("result")?.optString("text", "").orEmpty()
                            }.getOrDefault("")
                            if (t.isNotBlank()) text = t
                            if (flags == 0b0011) {
                                finished = true
                                com.azime.input.core.diag.Diag.log("Speech", "volc ws done text=${t.length}")
                                latch.countDown()
                            }
                        }
                        0xF -> {
                            error = body.take(200)
                            com.azime.input.core.diag.Diag.warn("Speech", "volc ws error: ${body.take(200)}")
                            latch.countDown()
                        }
                    }
                }
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                if (error.isBlank()) error = "连接失败：${t.message ?: "未知"}"
                com.azime.input.core.diag.Diag.warn("Speech", "volc ws failure: ${t.message}")
                latch.countDown()
            }

            override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                if (!finished && error.isBlank() && text.isNotBlank()) finished = true
                latch.countDown()
            }
        }

        val ws = http.newWebSocket(req, listener)
        val ok = runCatching { latch.await(MAX_TOTAL_MS, TimeUnit.MILLISECONDS) }.getOrDefault(false)
        runCatching { ws.close(1000, null) }
        return when {
            text.isNotBlank() -> Result(true, text.trim(), "")
            error.isNotBlank() -> Result(false, "", error)
            !ok -> Result(false, "", "超时未返回结果（${MAX_TOTAL_MS / 1000}s）")
            else -> Result(false, "", "服务端未返回识别文本")
        }
    }

    /** 4 字节头 + 4 字节长度 + payload ✓（客户端方向 ✓）。 */
    private fun frame(msgType: Int, flags: Int, payload: ByteArray, isJson: Boolean): ByteArray {
        val out = ByteArrayOutputStream(payload.size + 8)
        out.write(0x11)                                            // ver=1, headerSize=1(×4B)
        out.write((msgType shl 4) or flags)                        // msgType + flags
        out.write(if (isJson) 0x10 else 0x00)                      // JSON / raw + no compression
        out.write(0x00)                                            // reserved
        out.write(ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(payload.size).array())
        out.write(payload)
        return out.toByteArray()
    }

    /** ShortArray PCM16 → little-endian 字节流 ✓（SAUC 底包为裸 PCM ✓）。 */
    private fun toLittleEndian(pcm: ShortArray): ByteArray {
        val bb = ByteBuffer.allocate(pcm.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (s in pcm) bb.putShort(s)
        return bb.array()
    }
}
