package com.azime.input.core.handwriting

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import com.azime.input.core.diag.Diag
import com.azime.input.core.storage.StorageManager
import java.io.File

/**
 * 轮19.124：手写识别引擎（离线图片路线 ✓ DeepHCCR/GoogLeNet）
 *
 * 模型目录：`Documents/Oime/models/handwriting/`（`model.onnx` + `labels.txt` ✓ 用户自备 ✓）
 * 输入规格（与转换脚本/验证脚本严格一致 ✓）：
 *   笔迹包围盒裁剪 → 拉伸到 **120×120** → 3 通道 → 值域 **0..255** ✓
 */
object HandwritingEngine {

    private const val SIZE = 120
    private const val INK_WIDTH = 6f      // 轨迹线宽（与训练图观感对齐 ✓ 可调 ✓）

    private val labels = ArrayList<String>()

    @Volatile private var inited = false
    @Volatile private var lastError = ""

    // 轮19.126：下载源（本项目的 GitHub Release ✓ 直链 ✓ 无需解压 ✓）
    const val REMOTE_MODEL_ONNX =
        "https://github.com/AZNixl/Oime/releases/download/models-hw/model.onnx"
    const val REMOTE_LABELS =
        "https://github.com/AZNixl/Oime/releases/download/models-hw/labels.txt"

    fun modelDir(): File = File(StorageManager.modelsDir, "handwriting")
    fun modelFile(): File = File(modelDir(), "model.onnx")
    fun labelsFile(): File = File(modelDir(), "labels.txt")

    /** 模型文件是否齐（只看文件 ✓ 不看是否加载成功 ✓） */
    fun isModelPresent(): Boolean = modelFile().exists() && labelsFile().exists()

    fun errorText(): String = lastError

    /** 懒加载：读字符表 + 初始化 JNI ✓ */
    @Synchronized
    fun ensureInit(): Boolean {
        if (inited) return true
        if (!HandwritingNative.available) { lastError = "JNI 库不可用"; return false }
        if (!isModelPresent()) { lastError = "缺少模型文件"; return false }
        val lines = runCatching { labelsFile().readLines() }.getOrNull()
        if (lines.isNullOrEmpty()) { lastError = "labels.txt 读取失败"; return false }
        labels.clear(); labels.addAll(lines)
        val ok = HandwritingNative.init(modelFile().absolutePath, 2)
        lastError = if (ok) "" else "模型加载失败：${HandwritingNative.lastError()}"
        if (ok) Diag.log("Hw", "手写模型已加载（${labels.size} 类 ✓）") else Diag.warn("Hw", lastError)
        inited = ok
        return ok
    }

    /**
     * 识别：strokes 为每笔的坐标点（x0,y0,x1,y1,… ✓ 画布坐标系 ✓）
     * 返回 (字符, 分数) 列表（按分数降序 ✓），失败返回空 ✓
     */
    fun recognize(strokes: List<FloatArray>, topK: Int = 10): List<Pair<String, Float>> {
        if (!ensureInit()) return emptyList()
        val bitmap = rasterize(strokes) ?: return emptyList()
        val input = bitmapToInput(bitmap)
        val scores = HandwritingNative.run(input)
        if (scores == null) { lastError = "推理失败：${HandwritingNative.lastError()}"; return emptyList() }
        return scores.indices
            .sortedByDescending { scores[it] }
            .take(topK.coerceAtLeast(1))
            .mapNotNull { i -> labels.getOrNull(i)?.takeIf { it.isNotEmpty() }?.let { it to scores[i] } }
    }

    /** 笔迹 → 120×120 灰度图（先按墨迹包围盒裁剪 ✓ 再拉伸 ✓ 与验证脚本一致 ✓） */
    private fun rasterize(strokes: List<FloatArray>): Bitmap? {
        var minX = Float.MAX_VALUE; var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
        var any = false
        strokes.forEach { a ->
            var i = 0
            while (i + 1 < a.size) {
                val x = a[i]; val y = a[i + 1]
                if (x < minX) minX = x; if (x > maxX) maxX = x
                if (y < minY) minY = y; if (y > maxY) maxY = y
                any = true; i += 2
            }
        }
        if (!any) return null
        val bw = (maxX - minX).coerceAtLeast(1f)
        val bh = (maxY - minY).coerceAtLeast(1f)
        val pad = 4f
        val sx = (SIZE - 2 * pad) / bw
        val sy = (SIZE - 2 * pad) / bh

        val bmp = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(android.graphics.Color.WHITE)     // 与训练图一致：白底黑字 ✓
        val paint = Paint().apply {
            color = android.graphics.Color.BLACK
            style = Paint.Style.STROKE
            strokeWidth = INK_WIDTH
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            isAntiAlias = true
        }
        canvas.save()
        canvas.translate(pad - minX * sx, pad - minY * sy)
        canvas.scale(sx, sy)
        strokes.forEach { a ->
            if (a.size < 4) return@forEach
            val path = Path()
            path.moveTo(a[0], a[1])
            var i = 2
            while (i + 1 < a.size) { path.lineTo(a[i], a[i + 1]); i += 2 }
            canvas.drawPath(path, paint)
        }
        canvas.restore()
        return bmp
    }

    /** 位图 → [1,3,120,120] float（灰度复制 3 通道 ✓ 值域 0..255 ✓） */
    private fun bitmapToInput(bmp: Bitmap): FloatArray {
        val px = IntArray(SIZE * SIZE)
        bmp.getPixels(px, 0, SIZE, 0, 0, SIZE, SIZE)
        val out = FloatArray(3 * SIZE * SIZE)
        for (i in px.indices) {
            val c = px[i]
            // 灰度（加权 ✓）—— 与 skimage as_grey 近似 ✓
            val g = 0.299f * ((c shr 16) and 0xFF) + 0.587f * ((c shr 8) and 0xFF) + 0.114f * (c and 0xFF)
            out[i] = g
            out[i + SIZE * SIZE] = g
            out[i + 2 * SIZE * SIZE] = g
        }
        return out
    }
}
