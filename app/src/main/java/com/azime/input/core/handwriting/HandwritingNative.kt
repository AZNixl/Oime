package com.azime.input.core.handwriting

import com.azime.input.core.diag.Diag

/**
 * 轮19.124：手写 ONNX 推理的 JNI 薄壳 ✓
 * · 复用包内已有的 `libonnxruntime.so`（来自 sherpa-onnx AAR ✓）⇒ **体积增量≈0** ✓
 * · 失败一律降级（不抛 ✓ 不崩 ✓），错误信息走 [lastError] ✓
 */
object HandwritingNative {

    @Volatile private var libLoaded = false

    /** 库是否可用 ✓（不可用时上层降级 ✓） */
    val available: Boolean get() = libLoaded

    init {
        libLoaded = runCatching {
            System.loadLibrary("oime_hw")
            true
        }.getOrElse { t ->
            Diag.err("Hw", "loadLibrary(oime_hw) 失败 ⇒ 手写识别不可用", t)
            false
        }
    }

    private external fun nativeInit(modelPath: String, threads: Int): Boolean
    private external fun nativeReady(): Boolean
    private external fun nativeRun(input: FloatArray): FloatArray?
    private external fun nativeRelease(): Unit
    private external fun nativeLastError(): String

    fun init(modelPath: String, threads: Int = 2): Boolean =
        available && runCatching { nativeInit(modelPath, threads) }.getOrDefault(false)

    fun ready(): Boolean = available && runCatching { nativeReady() }.getOrDefault(false)

    /** 输入 [1×3×120×120] float（0..255 ✓）→ 输出各类分数 ✓ */
    fun run(input: FloatArray): FloatArray? =
        if (ready()) runCatching { nativeRun(input) }.getOrNull() else null

    fun lastError(): String =
        runCatching { if (available) nativeLastError() else "JNI 库未加载" }.getOrDefault("")

    fun release() {
        runCatching { if (available) nativeRelease() }
    }
}
