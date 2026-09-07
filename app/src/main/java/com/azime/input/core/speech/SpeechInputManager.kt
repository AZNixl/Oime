package com.azime.input.core.speech

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log

/**
 * 语音输入（轮15）：系统语音识别接口（android.speech.SpeechRecognizer）封装。
 *
 * 状态机：idle → listening →（onRmsChanged 高频回调 RMS）→ onResults 上屏 / onError 提示。
 * 键盘端通过 [start] 的三个回调接入：RMS 驱动声纹动画（只写独立 State，不走 uiState 重组链）、
 * 结果 commitText、错误 statusMessage。
 * 识别方式扩展位：本地模型 / 联网 API（设置页占位，后续版本完善）。
 */
object SpeechInputManager {

    private const val TAG = "SpeechInput"

    @Volatile private var current: SpeechRecognizer? = null

    /** 设备是否有系统语音识别服务（国产 ROM 常缺失 Google 语音组件）。 */
    fun isAvailable(context: Context): Boolean = runCatching {
        SpeechRecognizer.isRecognitionAvailable(context)
    }.getOrDefault(false)

    /**
     * 开始识别。重复调用会先释放旧实例再新建。
     * @param onRms RMS 音量回调（dB，高频——只写独立 State）
     * @param onResult 识别结果（取置信度最高的一条）
     * @param onError 用户可读的错误提示
     */
    @Synchronized
    fun start(
        context: Context,
        onRms: (Float) -> Unit,
        onResult: (String) -> Unit,
        onError: (String) -> Unit,
    ) {
        stop()
        val recognizer = runCatching { SpeechRecognizer.createSpeechRecognizer(context) }
            .onFailure { Log.e(TAG, "createSpeechRecognizer failed", it) }
            .getOrNull()
        if (recognizer == null) {
            onError("无法创建系统语音识别器")
            return
        }
        current = recognizer

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }

        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                Log.i(TAG, "ready for speech")
            }

            override fun onBeginningOfSpeech() {}

            override fun onRmsChanged(rmsdB: Float) {
                onRms(rmsdB)
            }

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {}

            override fun onError(error: Int) {
                val msg = when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH -> "没有听清，请再试一次"
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "没有检测到语音"
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "缺少麦克风权限"
                    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "语音网络服务不可用"
                    SpeechRecognizer.ERROR_CLIENT -> "语音客户端错误"
                    SpeechRecognizer.ERROR_AUDIO -> "录音错误"
                    SpeechRecognizer.ERROR_SERVER -> "语音服务器错误"
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "识别器忙，请稍后再试"
                    else -> "语音识别失败（$error）"
                }
                Log.w(TAG, "onError: $error $msg")
                release()
                onError(msg)
            }

            override fun onResults(results: Bundle?) {
                val text = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull().orEmpty()
                Log.i(TAG, "onResults: $text")
                release()
                onResult(text)
            }

            override fun onPartialResults(partialResults: Bundle?) {}

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        recognizer.startListening(intent)
    }

    /** 停止录音并等待结果回调（用户点击结束）。 */
    @Synchronized
    fun stop() {
        runCatching { current?.stopListening() }
    }

    /** 取消并释放（服务销毁 / 离开键盘时调用）。 */
    @Synchronized
    fun cancel() {
        release()
    }

    @Synchronized
    private fun release() {
        runCatching { current?.destroy() }
        current = null
    }
}
