package com.azime.input.core.speech

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineSenseVoiceModelConfig
import com.k2fsa.sherpa.onnx.OnlineModelConfig
import com.k2fsa.sherpa.onnx.OnlineRecognizer
import com.k2fsa.sherpa.onnx.OnlineRecognizerConfig
import com.k2fsa.sherpa.onnx.OnlineTransducerModelConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 语音输入引擎管理（轮19）：完全替换系统 SpeechRecognizer（AZ 的 ColorOS ROM 无
 * RecognitionService，系统路径不可用已删除）。
 *
 * 三种引擎，设置页点选：
 * 1. sense_voice  —— SenseVoice Small int8 离线解码（zh/en/ja/ko/yue，准确率优先）
 * 2. zipformer    —— streaming zipformer zh-en int8 流式（边说边出，73MB 轻量）
 * 3. web_api      —— 联网 API（通用 OpenAI 兼容 /v1/audio/transcriptions）
 *
 * 模型侧载（不进 APK）：
 *   Documents/Oime/models/sense-voice/{model.int8.onnx, tokens.txt}
 *   Documents/Oime/models/zipformer/{encoder.int8.onnx, decoder.onnx, joiner.int8.onnx, tokens.txt}
 *
 * 录音管线：AudioRecord 16kHz mono PCM16 → short[] / 32768f → FloatArray。
 * RMS 声纹动画由录音线程分帧能量计算（替代原 SpeechRecognizer.onRmsChanged）。
 */
object SpeechEngineManager {

    private const val TAG = "SpeechEngine"
    private const val SAMPLE_RATE = 16000

    /** 引擎标识（持久化 key）。 */
    const val ENGINE_SENSE_VOICE = "sense_voice"
    const val ENGINE_ZIPFORMER = "zipformer"
    const val ENGINE_WEB_API = "web_api"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val recording = AtomicBoolean(false)
    private var recordJob: Job? = null

    // ── 引擎实例缓存（懒加载，模型目录变化时清空） ──
    @Volatile private var offlineRecognizer: OfflineRecognizer? = null
    @Volatile private var onlineRecognizer: OnlineRecognizer? = null
    @Volatile private var cachedEngineDir: String? = null

    /** 录音回调接口（对齐原 SpeechInputManager，UI 无需改动）。 */
    interface Callbacks {
        fun onRms(rmsDb: Float)
        fun onResult(text: String)
        fun onError(message: String)
        /** 流式引擎的增量文本（zipformer 边说边出）；离线引擎不调用。 */
        fun onPartial(text: String) {}
    }

    // ── 模型探测 ──────────────────────────────────────────────

    /** sense-voice 模型目录内容齐全？ */
    fun hasSenseVoiceModel(): Boolean {
        val dir = File(com.azime.input.core.storage.StorageManager.modelsDir, "sense-voice")
        val model = dir.listFiles()?.firstOrNull { it.name.endsWith(".onnx") } ?: return false
        val tokens = File(dir, "tokens.txt")
        return model.isFile && tokens.isFile
    }

    /** zipformer 流式模型目录内容齐全？ */
    fun hasZipformerModel(): Boolean {
        val dir = File(com.azime.input.core.storage.StorageManager.modelsDir, "zipformer")
        val names = dir.listFiles()?.filter { it.isFile }?.map { it.name } ?: return false
        return names.any { it.startsWith("encoder") && it.endsWith(".onnx") } &&
            names.any { it.startsWith("decoder") && it.endsWith(".onnx") } &&
            names.any { it.startsWith("joiner") && it.endsWith(".onnx") } &&
            names.any { it == "tokens.txt" }
    }

    /** 模型目录里找前缀匹配的 onnx 文件全路径（容忍官方包的长文件名）。 */
    private fun findOnnx(dir: File, prefix: String): String? =
        dir.listFiles()?.firstOrNull { it.isFile && it.name.startsWith(prefix) && it.name.endsWith(".onnx") }
            ?.absolutePath

    // ── 引擎构建 ──────────────────────────────────────────────

    private fun senseVoiceRecognizer(): OfflineRecognizer? {
        val dir = File(com.azime.input.core.storage.StorageManager.modelsDir, "sense-voice")
        val model = findOnnx(dir, "model") ?: return null
        val tokens = File(dir, "tokens.txt").absolutePath
        return runCatching {
            OfflineRecognizer(
                config = OfflineRecognizerConfig(
                    featConfig = FeatureConfig(sampleRate = SAMPLE_RATE, featureDim = 80),
                    modelConfig = OfflineModelConfig(
                        senseVoice = OfflineSenseVoiceModelConfig(
                            model = model,
                            language = "zh",                          // auto/zh/en/ja/ko/yue
                            useInverseTextNormalization = true,       // ITN：数字/日期自然格式
                        ),
                        tokens = tokens,
                        numThreads = 4,
                        debug = false,
                    ),
                ),
            )
        }.onFailure { Log.e(TAG, "create OfflineRecognizer failed", it) }
            .getOrNull()
    }

    private fun zipformerRecognizer(): OnlineRecognizer? {
        val dir = File(com.azime.input.core.storage.StorageManager.modelsDir, "zipformer")
        val encoder = findOnnx(dir, "encoder") ?: return null
        val decoder = findOnnx(dir, "decoder") ?: return null
        val joiner = findOnnx(dir, "joiner") ?: return null
        val tokens = File(dir, "tokens.txt").absolutePath
        return runCatching {
            OnlineRecognizer(
                config = OnlineRecognizerConfig(
                    featConfig = FeatureConfig(sampleRate = SAMPLE_RATE, featureDim = 80),
                    modelConfig = OnlineModelConfig(
                        transducer = OnlineTransducerModelConfig(
                            encoder = encoder,
                            decoder = decoder,
                            joiner = joiner,
                        ),
                        tokens = tokens,
                        numThreads = 4,
                        debug = false,
                    ),
                    enableEndpoint = true,
                ),
            )
        }.onFailure { Log.e(TAG, "create OnlineRecognizer failed", it) }
            .getOrNull()
    }

    /** 引擎是否就绪（选中引擎 + 模型/配置可用）。 */
    fun isEngineReady(engine: String): Boolean = when (engine) {
        ENGINE_SENSE_VOICE -> hasSenseVoiceModel()
        ENGINE_ZIPFORMER -> hasZipformerModel()
        ENGINE_WEB_API -> true // URL 在设置中配置，启动时才知道可达性
        else -> false
    }

    // ── 录音与识别 ────────────────────────────────────────────

    /**
     * 开始听写。重复调用会先停止旧会话。
     * 录音线程分帧送引擎：zipformer 流式增量回调 onPartial；sense_voice 累积采样，
     * stop() 时整段离线解码；web_api 累积采样，stop() 时上传识别。
     */
    @SuppressLint("MissingPermission") // 调用方已检查 RECORD_AUDIO
    fun start(engine: String, callbacks: Callbacks) {
        stop()
        if (!recording.compareAndSet(false, true)) return

        val audioSrc = runCatching {
            val minBuf = AudioRecord.getMinBufferSize(
                SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
            )
            AudioRecord(
                MediaRecorder.AudioSource.MIC, SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
                maxOf(minBuf, SAMPLE_RATE * 2),
            )
        }.getOrNull()
        if (audioSrc == null || audioSrc.state != AudioRecord.STATE_INITIALIZED) {
            recording.set(false)
            runCatching { audioSrc?.release() }
            callbacks.onError("无法初始化麦克风录音")
            return
        }

        recordJob = scope.launch {
            val chunks = ArrayList<ShortArray>()  // sense_voice / web_api 用
            var onlineStream: com.k2fsa.sherpa.onnx.OnlineStream? = null
            var onlineRec: OnlineRecognizer? = null

            // 引擎预初始化（在线切引擎/首载模型可能耗时，失败降级到累积模式）
            if (engine == ENGINE_ZIPFORMER) {
                onlineRec = zipformerRecognizer()
                onlineStream = onlineRec?.createStream()
                if (onlineRec == null || onlineStream == null) {
                    recording.set(false)
                    runCatching { audioSrc.release() }
                    withMain { callbacks.onError("zipformer 模型加载失败，请检查 models/zipformer 目录") }
                    return@launch
                }
            } else if (engine == ENGINE_SENSE_VOICE && !hasSenseVoiceModel()) {
                recording.set(false)
                runCatching { audioSrc.release() }
                withMain { callbacks.onError("未找到 SenseVoice 模型（models/sense-voice）") }
                return@launch
            }

            val buf = ShortArray(SAMPLE_RATE / 10) // 100ms/帧
            audioSrc.startRecording()
            try {
                while (isActive && recording.get()) {
                    val n = audioSrc.read(buf, 0, buf.size)
                    if (n <= 0) continue
                    // RMS（dB 近似，对齐原 onRmsChanged 量级 -2..~10）
                    var sum = 0.0
                    for (i in 0 until n) sum += buf[i].toDouble() * buf[i]
                    val rms = kotlin.math.sqrt(sum / n)
                    val db = (20 * kotlin.math.log10(maxOf(rms, 1.0)) - 74).toFloat().coerceIn(-2f, 10f)
                    withMain { callbacks.onRms(db) }

                    val samples = FloatArray(n) { buf[it] / 32768.0f }

                    if (engine == ENGINE_ZIPFORMER && onlineStream != null && onlineRec != null) {
                        onlineStream.acceptWaveform(samples, SAMPLE_RATE)
                        while (onlineRec.isReady(onlineStream)) onlineRec.decode(onlineStream)
                        val partial = onlineRec.getResult(onlineStream).text
                        if (partial.isNotEmpty()) withMain { callbacks.onPartial(partial) }
                    } else {
                        chunks.add(buf.copyOf(n))
                    }
                }
            } finally {
                runCatching { audioSrc.stop() }
                runCatching { audioSrc.release() }
            }

            // 停止后处理
            if (engine == ENGINE_ZIPFORMER && onlineStream != null && onlineRec != null) {
                val text = onlineRec.getResult(onlineStream).text
                runCatching {
                    onlineRec.reset(onlineStream)
                    onlineStream.release()
                }
                withMain { callbacks.onResult(text) }
            } else if (engine == ENGINE_SENSE_VOICE) {
                val pcm = concatShorts(chunks)
                if (pcm.isEmpty()) {
                    withMain { callbacks.onResult("") }
                    return@launch
                }
                val rec = offlineRecognizer ?: senseVoiceRecognizer()?.also { offlineRecognizer = it }
                if (rec == null) {
                    withMain { callbacks.onError("SenseVoice 引擎初始化失败") }
                    return@launch
                }
                val text = runCatching {
                    val stream = rec.createStream()
                    stream.acceptWaveform(pcm.map { it / 32768.0f }.toFloatArray(), SAMPLE_RATE)
                    rec.decode(stream)
                    rec.getResult(stream).text.also { stream.release() }
                }.getOrDefault("")
                withMain { callbacks.onResult(text) }
            } else if (engine == ENGINE_WEB_API) {
                val pcm = concatShorts(chunks)
                if (pcm.isEmpty()) {
                    withMain { callbacks.onResult("") }
                    return@launch
                }
                webApiTranscribe(pcm) { ok, text ->
                    withMain { if (ok) callbacks.onResult(text) else callbacks.onError(text) }
                }
            }
        }
    }

    /** 停止录音（用户点击结束）：触发停止后的解码/上传流程。 */
    fun stop() {
        if (!recording.compareAndSet(true, false)) {
            // 已停止：直接 cancel 残余 job（防御）
            recordJob?.cancel()
            recordJob = null
            return
        }
        // recordJob 的 while 循环读到 recording=false 自然退出，随后走解码分支
    }

    /** 取消（不产结果）：服务销毁/离开键盘。 */
    fun cancel() {
        recording.set(false)
        recordJob?.cancel()
        recordJob = null
    }

    /** 释放引擎缓存（模型目录变更/内存告警）。 */
    fun releaseEngines() {
        runCatching { offlineRecognizer?.release() }
        runCatching { onlineRecognizer?.release() }
        offlineRecognizer = null
        onlineRecognizer = null
        cachedEngineDir = null
    }

    private fun concatShorts(chunks: List<ShortArray>): ShortArray {
        val total = chunks.sumOf { it.size }
        val out = ShortArray(total)
        var pos = 0
        for (c in chunks) {
            System.arraycopy(c, 0, out, pos, c.size)
            pos += c.size
        }
        return out
    }

    private fun withMain(block: () -> Unit) {
        android.os.Handler(android.os.Looper.getMainLooper()).post(block)
    }

    // ── 联网 API（OpenAI 兼容 /v1/audio/transcriptions） ─────

    /** 从应用 prefs 读 API 配置（设置页写入）。 */
    data class WebApiConfig(
        val baseUrl: String,      // https://api.xxx.com/v1
        val apiKey: String,
        val model: String,        // whisper-1 / gpt-4o-mini-transcribe 等
    )

    fun webApiConfig(context: Context): WebApiConfig? {
        val prefs = context.getSharedPreferences("speech_prefs", Context.MODE_PRIVATE)
        val url = prefs.getString("web_api_url", "") ?: ""
        val key = prefs.getString("web_api_key", "") ?: ""
        val model = prefs.getString("web_api_model", "whisper-1") ?: "whisper-1"
        return if (url.isNotBlank() && key.isNotBlank()) WebApiConfig(url.trimEnd('/'), key, model) else null
    }

    /** PCM16 mono 16k → wav → multipart 上传。结果回调 IO 线程。 */
    private fun webApiTranscribe(pcm: ShortArray, onDone: (Boolean, String) -> Unit) {
        scope.launch(Dispatchers.IO) {
            val cfg = webApiConfig(com.azime.input.AZimeApplication.instance)
            if (cfg == null) {
                onDone(false, "未配置联网 API（设置 → 语音输入）")
                return@launch
            }
            runCatching {
                val boundary = "----OimeBoundary${System.currentTimeMillis()}"
                val url = URL("${cfg.baseUrl}/audio/transcriptions")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.doOutput = true
                conn.connectTimeout = 15_000
                conn.readTimeout = 60_000
                conn.setRequestProperty("Authorization", "Bearer ${cfg.apiKey}")
                conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")

                val wav = pcmToWav(pcm)
                conn.outputStream.use { os ->
                    fun part(name: String, value: String) {
                        os.write("--$boundary\r\nContent-Disposition: form-data; name=\"$name\"\r\n\r\n$value\r\n".toByteArray())
                    }
                    os.write(("--$boundary\r\nContent-Disposition: form-data; name=\"file\"; filename=\"audio.wav\"\r\n" +
                        "Content-Type: audio/wav\r\n\r\n").toByteArray())
                    os.write(wav)
                    os.write("\r\n".toByteArray())
                    part("model", cfg.model)
                    os.write("--$boundary--\r\n".toByteArray())
                }
                val code = conn.responseCode
                val body = if (code in 200..299) conn.inputStream.bufferedReader().readText()
                else conn.errorStream?.bufferedReader()?.readText().orEmpty()
                if (code !in 200..299) {
                    Log.e(TAG, "web api $code: ${body.take(200)}")
                    onDone(false, "API 请求失败（$code）")
                } else {
                    val text = runCatching { JSONObject(body).optString("text", "") }.getOrDefault("")
                    onDone(true, text.trim())
                }
            }.onFailure {
                Log.e(TAG, "web api error", it)
                onDone(false, "网络请求失败：${it.message ?: "未知错误"}")
            }
        }
    }

    /** PCM16 mono 16k → WAV 容器（44 字节头）。 */
    private fun pcmToWav(pcm: ShortArray): ByteArray {
        val byteLen = pcm.size * 2
        val header = java.io.ByteArrayOutputStream(44 + byteLen)
        fun le32(v: Int) { header.write(byteArrayOf((v and 0xff).toByte(), ((v shr 8) and 0xff).toByte(), ((v shr 16) and 0xff).toByte(), ((v shr 24) and 0xff).toByte())) }
        fun le16(v: Int) { header.write(byteArrayOf((v and 0xff).toByte(), ((v shr 8) and 0xff).toByte())) }
        header.write("RIFF".toByteArray()); le32(36 + byteLen); header.write("WAVE".toByteArray())
        header.write("fmt ".toByteArray()); le32(16); le16(1); le16(1)
        le32(SAMPLE_RATE); le32(SAMPLE_RATE * 2); le16(2); le16(16)
        header.write("data".toByteArray()); le32(byteLen)
        val bb = java.nio.ByteBuffer.allocate(byteLen).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        for (s in pcm) bb.putShort(s)
        header.write(bb.array())
        return header.toByteArray()
    }
}
