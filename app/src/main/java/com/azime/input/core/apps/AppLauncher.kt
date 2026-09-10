package com.azime.input.core.apps

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap

/**
 * O 圆环上滑快捷启动（轮19.6）：已安装应用列表 / 图标 / 启动。
 *
 * 依赖 Manifest 的 `QUERY_ALL_PACKAGES`（Android 11+ 不声明则只能看到部分应用）。
 * 图标按包名缓存（Bitmap + ImageBitmap 双层，避免每次重组重解码）。
 */
object AppLauncher {

    /** 一个可启动应用。 */
    data class AppInfo(val pkg: String, val label: String, val icon: ImageBitmap?)

    private val iconCache = HashMap<String, ImageBitmap?>()
    private var listCache: List<AppInfo>? = null

    /** 全部带桌面入口的应用（按名称排序）。首次调用后缓存。 */
    fun installedApps(context: Context, forceReload: Boolean = false): List<AppInfo> {
        listCache?.let { if (!forceReload) return it }
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved = runCatching {
            pm.queryIntentActivities(intent, 0)
        }.getOrDefault(emptyList())
        val self = context.packageName
        val list = resolved
            .asSequence()
            .map { it.activityInfo }
            .filter { it != null && it.packageName != self }
            .distinctBy { it.packageName }
            .map { ai ->
                val pkg = ai.packageName
                AppInfo(
                    pkg = pkg,
                    label = runCatching { ai.loadLabel(pm).toString() }.getOrDefault(pkg),
                    icon = icon(context, pkg),
                )
            }
            .sortedBy { it.label.lowercase() }
            .toList()
        listCache = list
        return list
    }

    /** 应用图标（ImageBitmap），失败返回 null。 */
    fun icon(context: Context, pkg: String): ImageBitmap? {
        iconCache[pkg]?.let { return it }
        val bmp: ImageBitmap? = runCatching {
            val d: Drawable = context.packageManager.getApplicationIcon(pkg)
            d.toBitmap(96, 96, Bitmap.Config.ARGB_8888).asImageBitmap()
        }.getOrNull()
        iconCache[pkg] = bmp
        return bmp
    }

    /** 应用名（未安装返回包名）。 */
    fun label(context: Context, pkg: String): String = runCatching {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    }.getOrDefault(pkg)

    /** 启动应用：返回是否成功。 */
    fun launch(context: Context, pkg: String): Boolean {
        if (pkg.isBlank()) return false
        return runCatching {
            val intent = context.packageManager.getLaunchIntentForPackage(pkg)
                ?: return false
            intent.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED,
            )
            context.startActivity(intent)
            true
        }.getOrDefault(false)
    }

    /** 读取应用列表权限是否可用（QUERY_ALL_PACKAGES 生效判定：能否查到 >= 1 个应用）。 */
    fun canQueryApps(context: Context): Boolean =
        runCatching {
            val pm: PackageManager = context.packageManager
            pm.queryIntentActivities(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0,
            ).isNotEmpty()
        }.getOrDefault(false)
}
