package com.azime.input.ime

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.inputmethodservice.InputMethodService
import android.net.Uri
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.azime.input.core.keyboard.KeyboardManager
import com.azime.input.core.lua.LuaScriptManager
import com.azime.input.core.lua.ResolvedAction
import com.azime.input.core.rime.RimeManager
import com.azime.input.core.rime.RimeManager.KEY_BACKSPACE
import com.azime.input.core.rime.RimeManager.KEY_RETURN
import com.azime.input.core.rime.RimeManager.KEY_SPACE
import com.azime.input.core.rime.toCandidate
import com.azime.input.core.speech.SpeechEngineManager
import com.azime.input.ui.editor.KeyboardEditorActivity
import com.azime.input.ui.keyboard.AzimeKeyboardScreen
import com.azime.input.ui.keyboard.KeyAction
import com.azime.input.ui.keyboard.KeyboardUiState
import com.azime.input.ui.settings.SettingsActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

class AZimeService : InputMethodService() {

    private val lifecycleOwner = ImeLifecycleOwner()
    /**
     * 轮19.24（打字手感）：引擎调用**单线程串行**执行。
     * 原来用 `Dispatchers.Default`（多线程池）→ 每次按键各起一个协程并发打 librime，
     * 而 librime 内部有全局锁，多线程只会互相等待、还可能出现处理顺序错乱 ⇒ 快速打字时
     * 表现为"粘滞、跟不上手速"。单线程（FIFO）与 trime2 的 RimeDispatcher 同思路。
     */
    private val engineDispatcher = Dispatchers.Default.limitedParallelism(1)
    private val scope = CoroutineScope(SupervisorJob() + engineDispatcher)
    private val uiState = MutableStateFlow(KeyboardUiState())

    /** 语音输入 RMS（dB）：高频回调独立 State，只供声纹 Canvas 读取，不走 uiState 重组链。 */
    private val voiceRmsState = androidx.compose.runtime.mutableStateOf(0f)

    /**
     * 撤回栈（轮19.1）：记录**操作**而非仅上屏文本——修复退格下滑「撤回」失效：
     * - INSERT：上屏过文本 → 撤回时删除该段（撤销上屏）
     * - DELETE：删除过文本 → 撤回时重新插入（恢复删除，这才是「撤回」的预期语义）
     * 此前只记上屏文本且撤回总是删除，导致「上滑全删 → 下滑撤回」把无关文本又删一遍。
     */
    private class UndoOp(val text: String, val isDelete: Boolean)
    private val undoStack = ArrayDeque<UndoOp>()
    /** 轮19.17：redo 栈（撤回过的操作可重做；任何新操作都会清空它）。 */
    private val redoStack = ArrayDeque<UndoOp>()

    /** 退格左滑选择态（trime2 退格脚本锚点模型）：锚点与选区活动端（UTF-16 坐标）。 */
    private var selectAnchor = -1
    private var selectCursor = -1

    /** 摇杆「快捷指针」选区锚点（-1 表示未开始）。 */
    private var joystickAnchor = -1

    // ── 剪贴板历史 / 收藏（jqb.lua 风格，持久化 clipboard.json / phrase.json） ──
    private val clipHistory = mutableListOf<String>()
    private val phraseItems = mutableListOf<String>()
    private val clipHistoryFile by lazy { File(filesDir, "clipboard.json") }
    private val phraseFile by lazy { File(filesDir, "phrase.json") }

    /** 键盘尺寸签名：变化时在 onStartInputView 重建视图（高度滑杆热生效）。 */
    private var lastSizeSignature: String = ""

    /** 轮19.7：方案列表是否需要重新拉取（部署/切组/引擎就绪后置 true）。 */
    @Volatile private var schemasDirty = true

    /**
     * 轮19.16：被「按键消亡」过的复制条文本。
     * 真因——原来消亡只是把 clipText 清空，而 readClipboard() 只跳过 `lastCommittedClip`
     * （那条只在**点击复制条上屏**时才写），于是任何再次触发的 readClipboard
     * （剪贴板监听器重放 / 重新弹出键盘）都会把文本重新塞回去 ⇒ 表现为
     * 「打字怎么都不消失，只有点一下上屏才消失」。现在消亡时记住文本，同文本不再复活。
     */
    @Volatile private var dismissedClip: String = ""

    private val clipboardManager by lazy {
        getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    }

    private val clipboardListener = ClipboardManager.OnPrimaryClipChangedListener {
        // 轮19.28：这是**用户真的复制了一次** → 必须显示（不受"已上屏/已消亡"抑制）
        readClipboard(fromUserCopy = true)
    }

    override fun onCreate() {
        super.onCreate()
        lifecycleOwner.onCreate()
        // 沉浸式圆角：IME 窗口透明，键盘顶部圆角下透出应用内容；
        // 底部导航条增高区涂键盘背景色（随深浅色主题），实现底部沉浸
        runCatching {
            window.window?.let { w ->
                w.setBackgroundDrawableResource(android.R.color.transparent)
                w.navigationBarColor = navBarColorInt()
                w.isNavigationBarContrastEnforced = false
                // 轮19.20：悬浮模式 → IME 窗口铺满整屏（否则键盘往上拖会被窗口裁掉/被 App 挡住），
                // 具体可触摸范围由 onComputeInsets 的 touchableRegion 限定为键盘矩形，其余穿透给 App。
                if (KeyboardManager.floatKeyboard()) {
                    w.setLayout(
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                    w.setDimAmount(0f)
                }
            }
        }
        KeyboardManager.initialize(applicationContext)
        LuaScriptManager.loadScript()
        clipHistory.addAll(loadJsonList(clipHistoryFile))
        phraseItems.addAll(loadJsonList(phraseFile))
        // 轮19.1 修复：加载的剪贴板历史必须推给 uiState，否则要等下一次复制（recordClip）
        // 才会出现在面板里——表现为「更新后要复制一段内容，原有内容才显示」。
        uiState.update {
            it.copy(
                clipHistory = clipHistory.toList(),
                phraseItems = phraseItems.toList(),
            )
        }
        if (KeyboardManager.clipStripEnabled()) {
            clipboardManager.addPrimaryClipChangedListener(clipboardListener)
        }
        // 轮19.17（省电 P0）：**引擎懒初始化**——onCreate 不再加载 librime/建会话。
        // 依据：手机侧报告显示系统在熄屏期会反复重绑 IME（launches: 4），
        // 每次重绑都在这里做「加载词典 + 建会话」的重活，形成熄屏 CPU（4.55 mAh）。
        // 现在推迟到第一次 onStartInputView（键盘真要显示时）；屏幕解锁时**预热**补回首弹延迟。
        registerUnlockPrewarm()
    }

    /** 屏幕解锁后预热引擎（把懒初始化的首弹延迟补回来）。 */
    private fun registerUnlockPrewarm() {
        runCatching {
            val filter = android.content.IntentFilter(android.content.Intent.ACTION_USER_PRESENT)
            androidx.core.content.ContextCompat.registerReceiver(
                this,
                object : android.content.BroadcastReceiver() {
                    override fun onReceive(c: android.content.Context?, i: android.content.Intent?) {
                        ensureEngineAsync()
                    }
                },
                filter,
                androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED,
            )
        }
    }

    @Volatile private var engineInitStarted = false

    /** 懒初始化入口（可重复调用，内部只跑一次）。 */
    private fun ensureEngineAsync() {
        if (engineInitStarted) return
        engineInitStarted = true
        scope.launch {
            val ok = RimeManager.ensureReady(applicationContext)
            val sessionOk = ok && RimeManager.ensureSession()
            schemasDirty = true
            uiState.update {
                it.copy(
                    ready = sessionOk,
                    schemaName = if (sessionOk) RimeManager.currentSchema() else "",
                    statusMessage = if (!ok) "引擎初始化失败" else "",
                )
            }
            refreshState()
            // 首次部署可能超过会话等待窗口（大词典编译）：有上限的退避重试
            //（轮19.7：无上限 3s 轮询会在引擎起不来时永久唤醒，已改上限 20 次 ≈ 4 分钟）
            var attempt = 0
            while (!RimeManager.isSessionReady() && attempt < 20) {
                kotlinx.coroutines.delay(if (attempt < 5) 3000L else 15_000L)
                attempt++
                if (RimeManager.ensureSessionNow()) {
                    schemasDirty = true
                    refreshState()
                    break
                }
            }
        }
    }

    /** 轮19.21：按当前模式应用 IME 窗口布局（悬浮 → 铺满整屏；普通 → 由内容决定高度）。 */
    private fun applyKeyboardWindowLayout() {
        val w = window.window ?: return
        if (KeyboardManager.floatKeyboard()) {
            w.setLayout(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            )
            w.setDimAmount(0f)
        } else {
            w.setLayout(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
            )
        }
    }

    /**
     * 轮19.20：悬浮模式下把窗口的可触摸区域限定为键盘矩形，其余触摸穿透给下面的 App；
     * contentTopInsets 报满屏（不让 App 为悬浮键盘让位，键盘浮在上层）。
     */
    override fun onComputeInsets(outInsets: android.inputmethodservice.InputMethodService.Insets?) {
        super.onComputeInsets(outInsets)
        if (outInsets == null) return
        if (!KeyboardManager.floatKeyboard()) return
        val top = KeyboardManager.floatKbdTop
        val bottom = KeyboardManager.floatKbdBottom
        val hm = resources.displayMetrics
        // 轮19.22：**关键**——contentTopInsets 报满屏高度，App 不会被键盘顶起/压扁，
        // 而是整屏铺开、键盘浮在它之上（19.20/19.21 只设了 touchableRegion，App 仍被让位）
        outInsets.contentTopInsets = hm.heightPixels
        outInsets.visibleTopInsets = hm.heightPixels
        if (top < 0 || bottom <= top) return
        outInsets.touchableInsets = android.inputmethodservice.InputMethodService.Insets.TOUCHABLE_INSETS_REGION
        outInsets.touchableRegion.set(0, top, hm.widthPixels, bottom)
    }

    override fun onCreateInputView(): View {
        lifecycleOwner.resume() // 视图可能被重建（配置变化），确保 Compose 生命周期就绪
        val composeView = ComposeView(this)
        composeView.setViewTreeLifecycleOwner(lifecycleOwner)
        composeView.setViewTreeSavedStateRegistryOwner(lifecycleOwner)
        // 不能预先 setContent：ComposeView.onAttachedToWindow 会立刻创建组合，
        // 而 WindowRecomposer 从窗口「根视图」查找 ViewTreeLifecycleOwner，此时祖先链
        // 还没挂 owner，会抛 "ViewTreeLifecycleOwner not found from ...parentPanel" 并崩溃。
        // 所以 attach 后先补挂整条祖先链，再 post 设内容。
        composeView.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                var p = v.parent as? View
                while (p != null) {
                    p.setViewTreeLifecycleOwner(lifecycleOwner)
                    p.setViewTreeSavedStateRegistryOwner(lifecycleOwner)
                    p = p.parent as? View
                }
                v.post {
                    if (!v.isAttachedToWindow) return@post
                    composeView.setContent {
                        MaterialTheme(colorScheme = lightColorScheme()) {
                            val state by uiState.collectAsState()
                            AzimeKeyboardScreen(state = state, onAction = ::onKeyAction, voiceRms = voiceRmsState)
                        }
                    }
                }
            }

            override fun onViewDetachedFromWindow(v: View) {}
        })
        return composeView
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        lifecycleOwner.resume()
        currentEditorInfo = info
        // 主题深浅色可能已切换：每次弹键刷新导航条增高区颜色
        applyWindowBarColors()
        selectAnchor = -1
        selectCursor = -1
        joystickAnchor = -1
        readClipboard()
        // 键盘高度/增高行设置变化后热重建视图
        val sig = KeyboardManager.sizeSignature()
        if (lastSizeSignature.isNotEmpty() && sig != lastSizeSignature) {
            setInputView(onCreateInputView())
        }
        lastSizeSignature = sig
        // 轮19.17：真正的引擎初始化推迟到这里（键盘第一次要显示时）
        ensureEngineAsync()
        // 轮19.11：悬浮窗跟随光标——请求系统回传光标位置（Xime/trime2 同款做法）
        runCatching {
            getCurrentInputConnection()?.requestCursorUpdates(
                android.view.inputmethod.InputConnection.CURSOR_UPDATE_MONITOR,
            )
        }
        scope.launch { refreshState() }
    }

    /** 轮19.11：系统回传光标位置 → 存进 uiState 供悬浮窗定位；同时把光标附近的文本告诉引擎。 */
    override fun onUpdateCursorAnchorInfo(info: android.view.inputmethod.CursorAnchorInfo?) {
        super.onUpdateCursorAnchorInfo(info)
        if (info == null) return
        val matrix = info.matrix
        val r = android.graphics.RectF()
        val hasInsertion = runCatching {
            info.getInsertionMarkerTop() != Float.MAX_VALUE
        }.getOrDefault(false)
        if (hasInsertion) {
            val h = info.insertionMarkerHorizontal
            r.set(h, info.insertionMarkerTop, h + 1f, info.insertionMarkerBottom)
        } else {
            r.set(0f, 0f, 0f, 0f)
        }
        matrix.mapRect(r)
        val left = r.left.toInt()
        val bottom = r.bottom.toInt()
        if (left == uiState.value.cursorLeft && bottom == uiState.value.cursorBottom) return
        uiState.update { it.copy(cursorLeft = left, cursorBottom = bottom) }
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        lifecycleOwner.pause()
        // 键盘收起时若在听写中，取消识别
        if (uiState.value.voiceState == "listening") {
            SpeechEngineManager.cancel()
            voiceRmsState.value = 0f
            uiState.update { it.copy(voiceState = "idle") }
        }
        // 轮19.9（对齐 Xime clearInputState）：收起键盘即关闭残留面板——
        // 既避免下次弹出时渲染上一次的面板背景，也让面板持有的列表/图标引用可被回收。
        uiState.update {
            it.copy(
                showClipboardPanel = false,
                showMenuPanel = false,
                showCandidatePanel = false,
                showSchemaPanel = false,
            )
        }
        // 轮19.11：停止光标监听 + 清掉光标坐标
        runCatching {
            getCurrentInputConnection()?.requestCursorUpdates(0)
        }
        uiState.update { it.copy(cursorLeft = -1, cursorBottom = -1) }
        // 语音 = 最重的可选资源：闲置后卸载（90s 宽限）
        scheduleSpeechEngineRelease()
        super.onFinishInputView(finishingInput)
    }

    override fun onFinishInput() {
        RimeManager.clearComposition()
        scope.launch { refreshState() }
        super.onFinishInput()
    }

    override fun onDestroy() {
        runCatching { clipboardManager.removePrimaryClipChangedListener(clipboardListener) }
        SpeechEngineManager.cancel()
        // 轮19.9（省电，对齐 trime2 / Xime 的退出释放）：服务真的被系统销毁时，
        // 主动释放 librime 引擎与语音 ONNX 会话，避免 native 内存长期驻留。
        speechReleaseJob?.cancel()
        runCatching { RimeManager.releaseAll() }
        runCatching { SpeechEngineManager.releaseEngines() }
        scope.cancel()
        lifecycleOwner.destroy()
        super.onDestroy()
    }

    /** 轮19.9：语音引擎延迟释放任务（键盘收起后开始计时，期间再次听写会取消）。 */
    private var speechReleaseJob: kotlinx.coroutines.Job? = null

    /**
     * 语音引擎「用完即卸」——Xime 是键盘收起立即 release（见其 onFinishInputView），
     * trime2 是 onWindowHidden 销毁 Speech。这里给 90s 宽限：连续听写不会反复加载模型，
     * 真正闲置后再卸载，省掉 ONNX 会话常驻的内存与后台开销。
     */
    private fun scheduleSpeechEngineRelease(delayMs: Long = 90_000L) {
        speechReleaseJob?.cancel()
        speechReleaseJob = scope.launch {
            kotlinx.coroutines.delay(delayMs)
            if (uiState.value.voiceState != "listening") {
                runCatching { SpeechEngineManager.releaseEngines() }
            }
        }
    }

    // ── 语音输入（轮19：本地模型 / 联网 API，系统 SpeechRecognizer 已删除） ──

    /** 当前选中引擎（speech_prefs；与设置页共用 key）。 */
    private fun currentSpeechEngine(): String =
        getSharedPreferences("speech_prefs", Context.MODE_PRIVATE)
            .getString("engine", SpeechEngineManager.ENGINE_SENSE_VOICE)
            ?: SpeechEngineManager.ENGINE_SENSE_VOICE

    private fun handleVoiceToggle() {
        // 轮19.9：本次要用语音，取消「闲置卸载」计时，避免引擎刚加载又被回收
        speechReleaseJob?.cancel()
        if (uiState.value.voiceState == "listening") {
            // 点击结束：停止录音，sense_voice/web_api 在 stop 后解码上屏
            SpeechEngineManager.stop()
            return
        }
        if (androidx.core.content.ContextCompat.checkSelfPermission(
                this, android.Manifest.permission.RECORD_AUDIO,
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            // IME 无法弹权限对话框：跳设置页授权（语音输入大项里有申请按钮）
            uiState.update { it.copy(statusMessage = "语音输入需要麦克风权限，请在设置中开启") }
            onKeyAction(KeyAction.OpenSettings)
            return
        }
        val engine = currentSpeechEngine()
        // 引擎就绪检查（web_api 只查配置完整，网络可达性由请求时反馈）
        val ready = if (engine == SpeechEngineManager.ENGINE_WEB_API) {
            SpeechEngineManager.webApiConfig(this) != null
        } else {
            SpeechEngineManager.isEngineReady(engine)
        }
        if (!ready) {
            val hint = when (engine) {
                SpeechEngineManager.ENGINE_SENSE_VOICE ->
                    "SenseVoice 模型未就绪：将模型文件放入 Documents/Oime/models/sense-voice/"
                SpeechEngineManager.ENGINE_ZIPFORMER ->
                    "zipformer 模型未就绪：将模型文件放入 Documents/Oime/models/zipformer/"
                else -> "联网 API 未配置：设置 → 语音输入 → 联网 API"
            }
            uiState.update { it.copy(statusMessage = hint) }
            return
        }
        uiState.update { it.copy(voiceState = "listening", statusMessage = "") }
        SpeechEngineManager.start(
            engine = engine,
            callbacks = object : SpeechEngineManager.Callbacks {
                override fun onRms(rmsDb: Float) {
                    voiceRmsState.value = rmsDb
                }

                override fun onResult(text: String) {
                    voiceRmsState.value = 0f
                    uiState.update { it.copy(voiceState = "idle") }
                    if (text.isNotBlank()) {
                        currentInputConnection?.commitText(text, 1)
                        pushUndo(text)
                    }
                }

                override fun onError(message: String) {
                    voiceRmsState.value = 0f
                    uiState.update { it.copy(voiceState = "idle", statusMessage = message) }
                }

                override fun onPartial(text: String) {
                    // 流式 zipformer 增量文本：显示在工具栏 statusMessage（不打断输入）
                    uiState.update { it.copy(statusMessage = text) }
                }
            },
        )
    }

    // ── 剪贴板 ───────────────────────────────────────────────

    /** 最近一次从剪贴板条/面板上屏的文本：再次读到同文本时不再显示（xime 式消亡）。 */
    @Volatile private var lastCommittedClip: String? = null

    /** 导航条增高区颜色 = 键盘背景色（深浅色感知，与 buildKeyboardColors 的 bg 保持一致）。 */
    /**
     * 轮19.27：立即重刷 IME 窗口的导航栏（系统底部条）颜色。
     * 原来只在 onStartInputView 里设过一次 → ○ 菜单切亮/暗色后，**键盘外的系统底部栏**
     * 要等重新弹键盘才变色。切换动作里调用它即可即时跟随。
     */
    private fun applyWindowBarColors() {
        runCatching {
            window.window?.let { w ->
                w.navigationBarColor = navBarColorInt()
                w.isNavigationBarContrastEnforced = false
            }
        }
    }

    private fun navBarColorInt(): Int {
        val nightMask = resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
        val dark = com.azime.input.core.theme.KeyboardTheme.isDark(
            nightMask == android.content.res.Configuration.UI_MODE_NIGHT_YES,
        )
        return if (dark) 0xFF1B1D1F.toInt() else 0xFFE9EBEE.toInt()
    }

    private fun readClipboard(fromUserCopy: Boolean = false) {
        // 轮19.17：复制条关闭时完全不读剪贴板（隐私 + 少一个回调唤醒源）
        if (!KeyboardManager.clipStripEnabled()) return
        val clip = runCatching { clipboardManager.primaryClip }.getOrNull() ?: return
        if (clip.itemCount == 0) return
        val item = clip.getItemAt(0)
        val uri: Uri? = item.uri
        if (uri != null && (uri.scheme == "content")) {
            // 图片类剪贴板：落盘保存（存图片）
            scope.launch(Dispatchers.IO) {
                runCatching {
                    val dir = File(filesDir, "clipboard").apply { mkdirs() }
                    val target = File(dir, "clip_${System.currentTimeMillis()}")
                    contentResolver.openInputStream(uri)?.use { input ->
                        target.outputStream().use { output -> input.copyTo(output) }
                    }
                    if (target.length() > 0) {
                        uiState.update {
                            it.copy(
                                clipText = "🖼 [图片 ${target.length() / 1024}KB]",
                                clipAtMs = System.currentTimeMillis(),
                                clipFull = target.absolutePath,
                            )
                        }
                    }
                }
            }
            return
        }
        val text = item.coerceToText(this)?.toString().orEmpty()
        if (text.isNotBlank()) {
            // 轮19.28：抑制规则只对"弹键盘时的复读"生效——
            // 真·复制事件（fromUserCopy）永远显示，否则「复制 A → 再复制一次」会被判定成重复而不显示
            // （用户反馈：复制一条内容后，再次复制无法显示到工具栏）。
            val key = text.take(80)
            if (!fromUserCopy) {
                if (key == lastCommittedClip) return
                if (key == dismissedClip) {
                    com.azime.input.core.diag.Diag.log("Clip", "skip re-show (dismissed)")
                    return
                }
            } else if (key == uiState.value.clipText) {
                return // 已经在显示同一条了，避免重复触发
            }
            com.azime.input.core.diag.Diag.log("Clip", "show strip: ${key.take(20)}")
            // 轮19.24：clipText 只存前 80 字用于显示；clipFull 存完整文本供上屏
            uiState.update {
                it.copy(clipText = key, clipAtMs = System.currentTimeMillis(), clipFull = text)
            }
            recordClip(text)
        }
    }

    /** 新复制文本入历史：去重后插到最前，上限 100 条（jqb 同款策略）。 */
    private fun recordClip(text: String) {
        clipHistory.remove(text)
        clipHistory.add(0, text)
        while (clipHistory.size > 100) clipHistory.removeAt(clipHistory.size - 1)
        saveJsonList(clipHistoryFile, clipHistory)
        uiState.update { it.copy(clipHistory = clipHistory.toList()) }
    }

    private fun loadJsonList(file: File): MutableList<String> = runCatching {
        val arr = org.json.JSONArray(file.readText())
        (0 until arr.length()).map { arr.optString(it) }.filter { it.isNotEmpty() }.toMutableList()
    }.getOrDefault(mutableListOf())

    private fun saveJsonList(file: File, list: List<String>) {
        runCatching { file.writeText(org.json.JSONArray(list).toString()) }
    }

    // ── 按键处理 ─────────────────────────────────────────────

    /**
     * 轮19.11b：按字符数移动光标（负=左）。优先 `setSelection`（用 ExtractedText 拿绝对位置），
     * 拿不到再退回 DPAD 方向键事件。
     */
    /**
     * 轮19.19：按字符数移动光标（负=左）。
     * ① `setSelection`（用 ExtractedText 取绝对位置）→ **回读校验**是否真的动了；
     * ② 没动就回落到 DPAD 方向键事件（部分 App 只认它）；
     * ③ 再不行用 `deleteSurroundingText` 无副作用探测（仅记录日志，便于真机定位）。
     * 关键：调用点必须在 commitText 的 batch 之外（batch 内 getExtractedText 是过期快照）。
     */
    /**
     * 轮19.21：**成对括号自动居中**——上屏「（）」这类成对符号后光标移到中间。
     * 不依赖用户在编辑器里写 `{Left}`：只要提交的文本本身是一对括号就自动处理。
     */
    private fun bracketMiddleMove(text: String): Int = when (text) {
        "()", "（）", "[]", "【】", "{}", "｛｝", "「」", "『』", "《》", "〈〉", "“”", "‘’" -> 1
        else -> 0
    }

    private fun moveCursorByConnection(ic: android.view.inputmethod.InputConnection, delta: Int) {
        val req = android.view.inputmethod.ExtractedTextRequest()
        val moved = runCatching {
            val ex = ic.getExtractedText(req, 0) ?: return@runCatching false
            val pos = ex.startOffset + ex.selectionEnd
            val target = (pos + delta).coerceAtLeast(0)
            ic.setSelection(target, target)
            val chk = ic.getExtractedText(req, 0) ?: return@runCatching false
            (chk.startOffset + chk.selectionEnd) == target
        }.getOrDefault(false)
        com.azime.input.core.diag.Diag.log("Cursor", "move delta=$delta setSelection=${if (moved) "ok" else "failed"}")
        if (moved) return
        val key = if (delta < 0) android.view.KeyEvent.KEYCODE_DPAD_LEFT
        else android.view.KeyEvent.KEYCODE_DPAD_RIGHT
        repeat(kotlin.math.abs(delta)) {
            ic.sendKeyEvent(android.view.KeyEvent(0, 0, 0, 0, 0, 0, key, 0))
        }
    }

    private fun onKeyAction(action: KeyAction) {
        scope.launch {
            // 轮19.4：任意按键让工具栏「复制条」消亡（原来只有上屏才消亡，条会一直占着工具栏）。
            // 仅对真正的按键动作生效——剪贴板面板自身的操作（上屏/收藏/删除/切页）不清除。
            // 轮19.18（按用户要求定稿）：复制条**只有两条消亡途径**——
            //   ① 点击复制条上屏（CommitClipboard）
            //   ② **在复制条上左右划动**（DismissClipStrip，见下）
            // 撤掉 19.17 的「退格键消亡」：强制复制的无效内容不该被逼着先上屏才能清掉；
            // 打字/组词依旧不消亡。
            when (action) {
                is KeyAction.CharKey -> handleChar(action.c)
                is KeyAction.DirectCommit -> {
                    // 中文模式下的单字符先送 Rime（识别反查引导符，如 ` 笔画反查），
                    // 引擎未消费再直出（对齐 xime.az ImeKeyRouter 的符号键盘处理）
                    val text = action.text
                    if (!uiState.value.asciiMode && text.length == 1 && text[0].code < 0x80) {
                        val result = RimeManager.processKey(text[0].code)
                        if (result.processed) {
                            applyResult(result)
                            return@launch
                        }
                    }
                    val ic = currentInputConnection
                    ic?.commitText(text, 1)
                    // 成对括号：上屏后光标自动居中（不依赖 {Left}）
                    val bm = bracketMiddleMove(text)
                    if (bm > 0 && ic != null) moveCursorByConnection(ic, -bm)
                    pushUndo(text)
                    uiState.update { it.copy(shiftOn = false) }
                    refreshState()
                }
                KeyAction.Shift -> uiState.update { it.copy(shiftOn = !it.shiftOn, capsOn = false) }
                KeyAction.Backspace -> handleBackspace()
                KeyAction.Space -> {
                    // 抄 xime.az ImeKeyRouter "space"：以引擎实时组词状态（inputText）
                    // 为准，非组词状态一律直出空格。不依赖 UI state（残留态曾吞空格）：
                    // 组词中 → 选首选/顶屏；非组词 → commitText(" ")
                    val composing = RimeManager.getProcessResult().inputText.isNotEmpty()
                    if (!composing) {
                        currentInputConnection?.commitText(" ", 1)
                        pushUndo(" ")
                        refreshState()
                    } else {
                        applyResult(RimeManager.processKey(KEY_SPACE))
                    }
                }
                KeyAction.Enter -> handleEnter()
                KeyAction.ToggleSymbols -> {
                    val target = if (uiState.value.page == "main") KeyboardManager.preferredPage() else "main"
                    uiState.update { it.copy(page = target) }
                }
                KeyAction.ToggleAscii -> {
                    val ascii = RimeManager.toggleAsciiMode()
                    uiState.update { it.copy(asciiMode = ascii, shiftOn = false) }
                    refreshState()
                }
                is KeyAction.Candidate -> {
                    val selected = uiState.value.candidates.getOrNull(action.index)?.text ?: ""
                    RimeManager.selectCandidate(action.index)
                    if (selected.isNotEmpty()) pushUndo(selected)
                    applyResult(RimeManager.getProcessResult())
                }
                is KeyAction.SelectSchema -> {
                    // 部署进行中 switchSchema 会直接返回 false——给出提示而非静默失败
                    val ok = runCatching { RimeManager.switchSchema(action.schemaId) }.getOrDefault(false)
                    if (ok) {
                        // 轮13：记录组内上次使用的方案（重写 schema_list 时置首 → 部署后回落即回到它）
                        runCatching { RimeManager.recordGroupSchema(applicationContext, action.schemaId) }
                        schemasDirty = true // 轮19.7
                        uiState.update { it.copy(statusMessage = "") }
                        refreshState()
                    } else {
                        uiState.update { it.copy(statusMessage = "引擎部署中，请稍后重试") }
                    }
                }
                is KeyAction.SelectSchemaGroup -> {
                    // 轮18.2（trime2 架构）：在线切组——destroy 引擎 → 新组目录重 init → 部署。
                    // 不再杀进程（旧实现 apply()+exit(0) 落盘竞态 = 切组失败 + 闪退感）。
                    uiState.update { it.copy(statusMessage = "正在切换方案组，部署中…") }
                    RimeManager.switchSchemaGroupOnline(applicationContext, action.groupId) { ok ->
                        // 轮19.7：切组后方案名缓存与列表都要失效重取
                        RimeManager.clearDisplayNameCache()
                        schemasDirty = true
                        uiState.update {
                            it.copy(
                                statusMessage = if (ok) "" else "切换失败，请重试",
                                ready = ok,
                                schemaName = if (ok) RimeManager.currentSchema() else it.schemaName,
                                schemas = if (ok) RimeManager.availableSchemas() else it.schemas,
                            )
                        }
                        if (ok) scope.launch { refreshState() }
                    }
                }
                KeyAction.ToggleVoiceInput -> handleVoiceToggle()
                is KeyAction.ToggleSwitch -> {
                    RimeManager.setOption(action.name, !RimeManager.getOption(action.name))
                    refreshState()
                }
                KeyAction.PageDown -> {
                    RimeManager.processKey(0xFF55) // Prior/PageDown keysym
                    applyResult(RimeManager.getProcessResult())
                }

                // ── 扩展动作 ──
                is KeyAction.Resolved -> {
                    when (val resolved = LuaScriptManager.resolveAction(action.value)) {
                        is ResolvedAction.Commit -> {
                            val ic = currentInputConnection
                            if (ic != null) {
                                ic.beginBatchEdit()
                                ic.commitText(resolved.text, 1)
                                ic.endBatchEdit()
                                // 轮19.19：**光标回退必须在 batch 之外做**——batch 内
                                // getExtractedText 常返回提交前的过期快照，导致 setSelection
                                // 算错位置（19.12 的括号居中就是这么失效的）。
                                // 约定：delta 负 = 左移。{Left} → 左移；括号对无 {Left} 时自动居中
                                val raw = resolved.moveLeft - resolved.moveRight
                                var delta = -raw
                                if (raw == 0) delta = -bracketMiddleMove(resolved.text)
                                if (delta != 0) moveCursorByConnection(ic, delta)
                                pushUndo(resolved.text)
                            }
                        }
                        is ResolvedAction.Command -> runCommand(resolved.ident)
                        null -> {}
                    }
                }
                KeyAction.DeleteAll -> deleteAllText()
                KeyAction.Undo -> undo()
                KeyAction.Redo -> redo()
                // 轮19.19：单手模式循环（off→left→right）+ 悬浮模式开关；
                // layoutRev 自增用于强制键盘重组（只改 prefs 不会触发重组）
                // 轮19.20：工具栏「单手」= 开关（关↔开），空白处箭头 = 切左右手
                KeyAction.ToggleHandMode -> {
                    val on = KeyboardManager.handMode() != KeyboardManager.HAND_OFF
                    val next = if (on) KeyboardManager.HAND_OFF else KeyboardManager.handMode().let { prev ->
                        if (prev == KeyboardManager.HAND_OFF) KeyboardManager.HAND_LEFT else prev
                    }
                    KeyboardManager.setHandMode(if (on) KeyboardManager.HAND_OFF else next)
                    uiState.update { it.copy(layoutRev = it.layoutRev + 1, statusMessage = if (on) "单手模式：关闭" else "单手模式：开启") }
                }
                KeyAction.SwitchHandSide -> {
                    val next = if (KeyboardManager.handMode() == KeyboardManager.HAND_LEFT) {
                        KeyboardManager.HAND_RIGHT
                    } else {
                        KeyboardManager.HAND_LEFT
                    }
                    KeyboardManager.setHandMode(next)
                    uiState.update { it.copy(layoutRev = it.layoutRev + 1, statusMessage = "单手模式：" + if (next == KeyboardManager.HAND_LEFT) "左手" else "右手") }
                }
                KeyAction.ToggleFloatKeyboard -> {
                    val on = !KeyboardManager.floatKeyboard()
                    KeyboardManager.setFloatKeyboard(on)
                    if (!on) KeyboardManager.resetFloatKbdPos()
                    // 轮19.21：**立即**重设窗口布局（否则切换后窗口仍是键盘高度，
                    // 键盘往上拖会被窗口裁掉——用户截图里只看见下面两行就是这个原因）
                    runCatching { applyKeyboardWindowLayout() }
                    uiState.update { it.copy(layoutRev = it.layoutRev + 1, statusMessage = if (on) "悬浮模式：拖动顶部横条移动" else "") }
                }
                KeyAction.BackspaceSelectStart -> startSelectBack()
                is KeyAction.BackspaceSelectTo -> moveSelectBack(action.charsFromAnchor)
                KeyAction.DeleteSelection -> deleteSelection()
                KeyAction.CapsLock -> uiState.update { it.copy(capsOn = !it.capsOn, shiftOn = false) }
                is KeyAction.OpenPage -> uiState.update { it.copy(page = action.page) }
                is KeyAction.SwitchPage -> uiState.update { it.copy(page = action.page) }
                is KeyAction.Joystick -> joystickMove(action.dx)
                is KeyAction.SetJoystickMode -> uiState.update { it.copy(joystickMode = action.mode) }
                KeyAction.OpenSettings -> {
                    val intent = Intent(this@AZimeService, SettingsActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(intent)
                }
                KeyAction.OpenKeyboardEditor -> {
                    // 反馈轮16：O 菜单「键盘编辑」直达布局编辑器
                    val intent = Intent(this@AZimeService, KeyboardEditorActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(intent)
                }
                KeyAction.HideKeyboard -> requestHideSelf(0)
                KeyAction.Deploy -> {
                    // 重新部署方案（○ 菜单 / 方案设置页）
                    uiState.update { it.copy(statusMessage = "正在重新部署方案…") }
                    runCatching { RimeManager.deployImportedSchemas(applicationContext) }
                    schemasDirty = true // 轮19.7：方案列表可能变化，下次 refreshState 重拉
                    uiState.update { it.copy(statusMessage = "") }
                    refreshState()
                }
                KeyAction.ToggleThemeMode -> {
                    // 反馈轮12：O 菜单亮暗切换——跟随系统时按当前实际状态取反，
                    // themeRev++ 强制 uiState 变化 → 键盘立即重组换色
                    val sysDark = (resources.configuration.uiMode and
                        android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                        android.content.res.Configuration.UI_MODE_NIGHT_YES
                    val next = if (com.azime.input.core.theme.KeyboardTheme.isDark(sysDark)) {
                        com.azime.input.core.theme.KeyboardTheme.MODE_LIGHT
                    } else {
                        com.azime.input.core.theme.KeyboardTheme.MODE_DARK
                    }
                    com.azime.input.core.theme.KeyboardTheme.setMode(next)
                    uiState.update { it.copy(themeRev = it.themeRev + 1) }
                    // 轮19.27：立即刷新系统底部栏颜色（否则要重开键盘才变）
                    applyWindowBarColors()
                }
                KeyAction.ToggleClipboardPanel ->
                    uiState.update {
                        it.copy(
                            showClipboardPanel = !it.showClipboardPanel,
                            showMenuPanel = false,
                            showSchemaPanel = false,
                        )
                    }
                // 轮19.11：方案快捷面板（与其它面板互斥）
                KeyAction.ToggleSchemaPanel ->
                    uiState.update {
                        it.copy(
                            showSchemaPanel = !it.showSchemaPanel,
                            showMenuPanel = false,
                            showClipboardPanel = false,
                            showCandidatePanel = false,
                        )
                    }
                KeyAction.ToggleMenuPanel ->
                    uiState.update { it.copy(showMenuPanel = !it.showMenuPanel) }
                // 轮19.18：复制条左右划动 → 只消亡不上屏（并记住，避免被剪贴板回调复活）
                KeyAction.DismissClipStrip -> {
                    val ck = uiState.value.clipText.take(80)
                    dismissedClip = ck
                    lastCommittedClip = ck
                    com.azime.input.core.diag.Diag.log("Clip", "dismiss by swipe/长按")
                    uiState.update { it.copy(clipText = "", clipAtMs = 0L, clipFull = "") }
                }
                is KeyAction.CommitClipboard -> {
                    currentInputConnection?.commitText(action.text, 1)
                    pushUndo(action.text)
                    // 轮19.25：**必须按 take(80) 记**——readClipboard 用的是 `text.take(80)` 比对，
                    // 19.24 把上屏文本换成了完整长文本却直接赋给 lastCommittedClip ⇒ 长文本永远比不中，
                    // 上屏后又被剪贴板回调塞回工具栏（用户反馈「点击上屏也不能消亡」）。
                    val ck = action.text.take(80)
                    lastCommittedClip = ck
                    dismissedClip = ck
                    com.azime.input.core.diag.Diag.log("Clip", "commit+clear len=${action.text.length}")
                    // 上屏后条目消失 + 面板收起，不干扰后续输入
                    uiState.update {
                        it.copy(
                            clipText = "", clipAtMs = 0L, clipFull = "",
                            showClipboardPanel = false, showMenuPanel = false,
                        )
                    }
                }
                is KeyAction.SetToolbarItems -> {
                    KeyboardManager.setToolbarItems(action.ids)
                    uiState.update { it.copy(toolbarRev = it.toolbarRev + 1) }
                }
                KeyAction.ToggleCandidatePanel ->
                    uiState.update { it.copy(showCandidatePanel = !it.showCandidatePanel) }
                KeyAction.PageUp -> {
                    RimeManager.processKey(0xFF54) // Prior/PageUp keysym
                    applyResult(RimeManager.getProcessResult())
                }

                // ── 剪贴板面板（jqb 风格：历史/收藏 + ︙菜单） ──
                is KeyAction.SetClipTab -> uiState.update {
                    it.copy(clipTab = if (action.tab == "phrase") "phrase" else "clipboard")
                }
                is KeyAction.CommitClipText -> {
                    currentInputConnection?.commitText(action.text, 1)
                    pushUndo(action.text)
                }
                is KeyAction.ClipFav -> {
                    if (!phraseItems.contains(action.text)) phraseItems.add(0, action.text)
                    saveJsonList(phraseFile, phraseItems)
                    uiState.update { it.copy(phraseItems = phraseItems.toList()) }
                }
                is KeyAction.ClipDelete -> {
                    if (action.list == "phrase") {
                        if (action.index in phraseItems.indices) {
                            phraseItems.removeAt(action.index)
                            saveJsonList(phraseFile, phraseItems)
                            uiState.update { it.copy(phraseItems = phraseItems.toList()) }
                        }
                    } else {
                        if (action.index in clipHistory.indices) {
                            clipHistory.removeAt(action.index)
                            saveJsonList(clipHistoryFile, clipHistory)
                            uiState.update { it.copy(clipHistory = clipHistory.toList()) }
                        }
                    }
                }
                is KeyAction.ClipTop -> {
                    if (action.list == "phrase" && action.index in phraseItems.indices) {
                        val item = phraseItems.removeAt(action.index)
                        phraseItems.add(0, item)
                        saveJsonList(phraseFile, phraseItems)
                        uiState.update { it.copy(phraseItems = phraseItems.toList()) }
                    } else if (action.list != "phrase" && action.index in clipHistory.indices) {
                        val item = clipHistory.removeAt(action.index)
                        clipHistory.add(0, item)
                        saveJsonList(clipHistoryFile, clipHistory)
                        uiState.update { it.copy(clipHistory = clipHistory.toList()) }
                    }
                }
                is KeyAction.ClipClear -> {
                    if (action.list == "phrase") {
                        phraseItems.clear()
                        saveJsonList(phraseFile, phraseItems)
                        uiState.update { it.copy(phraseItems = emptyList()) }
                    } else {
                        clipHistory.clear()
                        saveJsonList(clipHistoryFile, clipHistory)
                        uiState.update { it.copy(clipHistory = emptyList()) }
                    }
                }
                is KeyAction.ClipSplit -> {
                    // 轮19.1 分词：按空白/中英标点切分，词条插到历史最前（顺序保持原文）
                    val parts = action.text
                        .split(Regex("[\\s，。！？；：、,.!?;:（）()\\[\\]【】「」《》\"'“”‘’·…—]+"))
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                        .take(50)
                    if (parts.isNotEmpty()) {
                        for (p in parts.asReversed()) {
                            clipHistory.remove(p)
                            clipHistory.add(0, p)
                        }
                        while (clipHistory.size > 100) clipHistory.removeAt(clipHistory.size - 1)
                        saveJsonList(clipHistoryFile, clipHistory)
                        uiState.update {
                            it.copy(
                                clipHistory = clipHistory.toList(),
                                statusMessage = "已分词 ${parts.size} 条",
                            )
                        }
                    }
                }
            }
        }
    }

    /** 执行 preset_keys / 布局动作里的内置命令。 */
    private suspend fun runCommand(ident: String) {
        val ic = currentInputConnection
        when (ident) {
            "toggle_ascii" -> onKeyAction(KeyAction.ToggleAscii)
            "newline" -> {
                ic?.commitText("\n", 1)
                pushUndo("\n")
            }
            "backspace" -> handleBackspace()
            "delete" -> ic?.deleteSurroundingText(0, 1)
            "space" -> {
                ic?.commitText(" ", 1)
                pushUndo(" ")
            }
            "tab" -> ic?.commitText("\t", 1)
            "esc" -> RimeManager.clearComposition()
            "left" -> ic?.sendKeyEvent(android.view.KeyEvent(0, 0, 0, 0, 0, 0, android.view.KeyEvent.KEYCODE_DPAD_LEFT, 0))
            "right" -> ic?.sendKeyEvent(android.view.KeyEvent(0, 0, 0, 0, 0, 0, android.view.KeyEvent.KEYCODE_DPAD_RIGHT, 0))
            "up" -> ic?.sendKeyEvent(android.view.KeyEvent(0, 0, 0, 0, 0, 0, android.view.KeyEvent.KEYCODE_DPAD_UP, 0))
            "down" -> ic?.sendKeyEvent(android.view.KeyEvent(0, 0, 0, 0, 0, 0, android.view.KeyEvent.KEYCODE_DPAD_DOWN, 0))
            "page_up" -> applyResult(RimeManager.processKey(0xFF54))
            "page_down" -> applyResult(RimeManager.processKey(0xFF55))
            "home" -> ic?.sendKeyEvent(android.view.KeyEvent(0, 0, 0, 0, 0, 0, android.view.KeyEvent.KEYCODE_MOVE_HOME, 0))
            "end" -> ic?.sendKeyEvent(android.view.KeyEvent(0, 0, 0, 0, 0, 0, android.view.KeyEvent.KEYCODE_MOVE_END, 0))
            "select_all" -> ic?.run {
                val before = (getTextBeforeCursor(MAX_TEXT, 0) ?: "").toString()
                val sel = (getSelectedText(0) ?: "").toString()
                val after = (getTextAfterCursor(MAX_TEXT, 0) ?: "").toString()
                val all = before + sel + after
                if (all.isNotEmpty()) setSelection(0, all.length)
            }
            "copy" -> ic?.getSelectedText(0)?.let { text ->
                clipboardManager.setPrimaryClip(android.content.ClipData.newPlainText("azime", text))
            }
            "cut" -> ic?.getSelectedText(0)?.let { text ->
                clipboardManager.setPrimaryClip(android.content.ClipData.newPlainText("azime", text))
                ic.commitText("", 1)
            }
            "paste" -> {
                val clip = runCatching { clipboardManager.primaryClip }.getOrNull()
                val text = clip?.getItemAt(0)?.coerceToText(this)?.toString().orEmpty()
                if (text.isNotEmpty()) {
                    ic?.commitText(text, 1)
                    pushUndo(text)
                }
            }
            "caps_lock" -> uiState.update { it.copy(capsOn = !it.capsOn, shiftOn = false) }
            "shift" -> uiState.update { it.copy(shiftOn = !it.shiftOn) }
            "delete_all" -> deleteAllText()
            "undo" -> undo()
            "toggle_symbols" -> onKeyAction(KeyAction.ToggleSymbols)
            "choose_page" -> {} // UI 层气泡处理
            else -> if (ident.startsWith("page:")) {
                uiState.update { it.copy(page = ident.removePrefix("page:")) }
            }
        }
    }

    /** 记录一次「上屏」操作（撤回时删除该文本）。 */
    private fun pushUndo(text: String) {
        if (text.isEmpty()) return
        undoStack.addLast(UndoOp(text, isDelete = false))
        while (undoStack.size > 50) undoStack.removeFirst()
        redoStack.clear() // 新操作使 redo 失效（标准撤销语义）
    }

    /** 记录一次「删除」操作（撤回时恢复该文本）。 */
    private fun pushDeleted(text: String) {
        if (text.isEmpty()) return
        undoStack.addLast(UndoOp(text, isDelete = true))
        while (undoStack.size > 50) undoStack.removeFirst()
        redoStack.clear()
    }

    /**
     * 撤回（退格下滑）：按栈顶操作类型反向执行——
     * DELETE → 重新插入被删文本（恢复）；INSERT → 删除刚上屏的文本（撤销上屏）。
     */
    private suspend fun undo() {
        val op = undoStack.removeLastOrNull() ?: return
        val ic = currentInputConnection ?: return
        if (op.isDelete) {
            ic.commitText(op.text, 1)      // 原操作是删除 → 撤销 = 恢复
            redoStack.addLast(UndoOp(op.text, isDelete = false)) // 重做 = 再删掉
        } else {
            ic.deleteSurroundingText(op.text.length, 0)          // 原操作是插入 → 撤销 = 删除
            redoStack.addLast(UndoOp(op.text, isDelete = true))  // 重做 = 再插入
        }
        while (redoStack.size > 50) redoStack.removeFirst()
        refreshState()
    }

    /** 轮19.17：重做（与 undo 对称，反向压回 undo 栈）。 */
    private suspend fun redo() {
        val op = redoStack.removeLastOrNull() ?: return
        val ic = currentInputConnection ?: return
        if (op.isDelete) {
            ic.commitText(op.text, 1)
            undoStack.addLast(UndoOp(op.text, isDelete = false))
        } else {
            ic.deleteSurroundingText(op.text.length, 0)
            undoStack.addLast(UndoOp(op.text, isDelete = true))
        }
        while (undoStack.size > 50) undoStack.removeFirst()
        refreshState()
    }

    /** 上滑全删：删除光标前后全部文本。 */
    private suspend fun deleteAllText() {
        val ic = currentInputConnection ?: return
        val before = ic.getTextBeforeCursor(MAX_TEXT, 0) ?: ""
        val after = ic.getTextAfterCursor(MAX_TEXT, 0) ?: ""
        if (before.isNotEmpty() || after.isNotEmpty()) {
            pushDeleted(before.toString() + after.toString()) // 全删内容可撤回恢复
            ic.deleteSurroundingText(before.length, after.length)
        }
        RimeManager.clearComposition()
        refreshState()
    }

    /** 退格左滑进入选择模式：记锚点（组合中由 UI 侧拦截不会到达）。 */
    private fun startSelectBack() {
        val ic = currentInputConnection ?: return
        val before = (ic.getTextBeforeCursor(MAX_TEXT, 0) ?: "").length
        selectAnchor = before
        selectCursor = before
    }

    /** 退格左滑位移换算：moved 为相对锚点的字符偏移（负值向左），右滑回退不超过锚点。 */
    private fun moveSelectBack(moved: Int) {
        val ic = currentInputConnection ?: return
        if (selectAnchor < 0) return
        val target = (selectAnchor + moved).coerceIn(0, selectAnchor)
        if (target != selectCursor) {
            ic.setSelection(target, selectAnchor)
            selectCursor = target
        }
    }

    /** 退格左滑松手：删除选区（空选区安全跳过）。 */
    private suspend fun deleteSelection() {
        val ic = currentInputConnection ?: return
        val sel = ic.getSelectedText(0)?.toString().orEmpty()
        if (sel.isNotEmpty()) {
            pushDeleted(sel) // 删除的选区可撤回恢复
            ic.commitText("", 1)
        }
        selectAnchor = -1
        selectCursor = -1
        refreshState()
    }

    /** 红摇杆：cursor 模式 1 字/步；pointer（快捷指针）10 字/步远距跳转。 */
    private fun joystickMove(dx: Int) {
        val ic = currentInputConnection ?: return
        val stride = if (uiState.value.joystickMode == "pointer") 10 else 1
        val before = (ic.getTextBeforeCursor(MAX_TEXT, 0) ?: "").length
        val after = (ic.getTextAfterCursor(MAX_TEXT, 0) ?: "").length
        val target = (before + dx * stride).coerceIn(0, before + after)
        ic.setSelection(target, target)
        joystickAnchor = -1
    }

    private suspend fun handleChar(c: Char) {
        val state = uiState.value
        // emoji / 非字母符号直出
        if (c.code > 0x7F) {
            currentInputConnection?.commitText(c.toString(), 1)
            pushUndo(c.toString())
            return
        }
        // 英文模式 / 临时 shift / 大写锁定：字母直出，不进编码
        if (state.asciiMode || state.shiftOn || state.capsOn) {
            val text = when {
                state.shiftOn -> c.uppercaseChar()
                state.capsOn && c.isLetter() -> c.uppercaseChar()
                else -> c
            }
            currentInputConnection?.commitText(text.toString(), 1)
            pushUndo(text.toString())
            uiState.update { it.copy(shiftOn = false) }
            return
        }
        val result = RimeManager.processKey(c.lowercaseChar().code)
        if (result.processed) {
            applyResult(result)
            return
        }
        // 编码未消费（如数字/标点无菜单时）：直接上屏
        currentInputConnection?.commitText(c.toString(), 1)
        pushUndo(c.toString())
        refreshState()
    }

    /**
     * 退格：组合存在时 librime 消费（缩组合）；组合为空时 librime 返回 processed=false，
     * 此时由输入连接删除光标前一个字符（emoji 代理对场景先按 2 个 code point 兜底）。
     */
    private suspend fun handleBackspace() {
        val result = RimeManager.processKey(KEY_BACKSPACE)
        if (result.processed) {
            applyResult(result)
            return
        }
        val ic = currentInputConnection
        if (ic != null) {
            val before = (ic.getTextBeforeCursor(2, 0) ?: "").toString()
            if (before.length == 2 && Character.isSurrogatePair(before[0], before[1])) {
                pushDeleted(before) // 记录删除内容，下滑「撤回」可恢复
                ic.deleteSurroundingText(2, 0)
            } else {
                pushDeleted(before.takeLast(1))
                ic.deleteSurroundingText(1, 0)
            }
        }
        refreshState()
    }

    /** 当前输入框的 EditorInfo（回车键行为判断用）。 */
    private var currentEditorInfo: EditorInfo? = null

    private suspend fun handleEnter() {
        val result = RimeManager.processKey(KEY_RETURN)
        if (result.processed && result.committedText.isNotEmpty()) {
            pushUndo(result.committedText)
            applyResult(result)
            return
        }
        if (result.processed) {
            // 引擎消化了回车（如清空编码），只同步状态
            applyResult(result)
            return
        }
        // 无编码时回车：输入框声明了编辑动作（发送/搜索/完成/前往）则触发动作（对齐 xime.az，
        // 微信等聊天应用可用回车直接发送）；多行文本框（NO_ENTER_ACTION）或未声明动作时
        // 发系统回车键（与 xime.az 一致，由应用自行处理换行）。
        val ic = currentInputConnection
        val info = currentEditorInfo
        if (ic != null && info != null) {
            val action = info.imeOptions and EditorInfo.IME_MASK_ACTION
            val noEnterAction = (info.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0
            if (action == EditorInfo.IME_ACTION_GO ||
                action == EditorInfo.IME_ACTION_SEARCH ||
                action == EditorInfo.IME_ACTION_SEND ||
                action == EditorInfo.IME_ACTION_NEXT ||
                action == EditorInfo.IME_ACTION_DONE
            ) {
                if (!noEnterAction) {
                    ic.performEditorAction(action)
                    refreshState()
                    return
                }
            }
        }
        sendDownUpKeyEvents(android.view.KeyEvent.KEYCODE_ENTER)
        refreshState()
    }

    /** 把一次按键结果同步到输入框与 UI 状态。 */
    private fun applyResult(result: com.kingzcheung.xime.rime.RimeProcessResult) {
        if (result.committedText.isNotEmpty()) {
            currentInputConnection?.commitText(result.committedText, 1)
            pushUndo(result.committedText)
        }
        updateFromResult(result)
    }

    private fun updateFromResult(result: com.kingzcheung.xime.rime.RimeProcessResult) {
        // 轮19.24：内容完全一致时**不发射新状态**（StateFlow 值相等即跳过，避免整棵键盘白重组）
        val newList = result.candidates.map { c -> c.toCandidate() }
        val cur = uiState.value
        if (cur.candidates == newList && cur.preedit == result.preeditText &&
            cur.asciiMode == result.isAsciiMode && cur.hasNextPage == result.hasNextPage &&
            cur.hasPrevPage == result.hasPrevPage
        ) return
        uiState.update {
            it.copy(
                candidates = newList,
                preedit = result.preeditText,
                asciiMode = result.isAsciiMode.also { now ->
                    // 轮19.21：ascii 状态变化打点（排查「英文模式下 H 长按不出 _」）
                    if (now != it.asciiMode) com.azime.input.core.diag.Diag.log("Ascii", "ascii=$now")
                },
                hasNextPage = result.hasNextPage,
                hasPrevPage = result.hasPrevPage,
                // 编码清空（候选消失）时自动收起更多候选面板
                showCandidatePanel = it.showCandidatePanel && result.candidates.isNotEmpty(),
                // 轮19.15：**打字即消亡**复制条（兜底，覆盖所有按键路径）。
                // 反馈轮12 曾特意做成「组词不清空」，但用户明确要求「直接打字也要消失」。
                // 轮19.17：组词**不再**消亡复制条（只有「点击上屏」与「无候选时按退格」两条途径）
            )
        }
    }

    private suspend fun refreshState() {
        val result = RimeManager.getProcessResult()
        updateFromResult(result)
        // 轮19.7：方案列表改为**按需刷新**（schemasDirty）——原来每次按键都调
        // RimeManager.availableSchemas()（JNI + 列表分配），是打字期间的无谓开销。
        val needSchemas = schemasDirty || uiState.value.schemas.isEmpty()
        val schemasNow = if (needSchemas && RimeManager.isReady()) {
            schemasDirty = false
            RimeManager.availableSchemas()
        } else null
        uiState.update { state ->
            state.copy(
                ready = RimeManager.isReady(),
                schemaName = RimeManager.currentSchema().ifBlank { state.schemaName },
                schemas = schemasNow ?: state.schemas,
                statusMessage = if (RimeManager.isMaintaining()) "正在部署词典，请稍候…" else "",
            )
        }
    }

    companion object {
        private const val MAX_TEXT = 100000
    }
}
