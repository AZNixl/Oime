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
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val uiState = MutableStateFlow(KeyboardUiState())

    /** 撤回栈：记录最近上屏的文本（DirectCommit / 候选上屏 / 字母直出）。 */
    private val undoStack = ArrayDeque<String>()

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

    private val clipboardManager by lazy {
        getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    }

    private val clipboardListener = ClipboardManager.OnPrimaryClipChangedListener {
        readClipboard()
    }

    override fun onCreate() {
        super.onCreate()
        lifecycleOwner.onCreate()
        KeyboardManager.initialize(applicationContext)
        LuaScriptManager.loadScript()
        clipHistory.addAll(loadJsonList(clipHistoryFile))
        phraseItems.addAll(loadJsonList(phraseFile))
        clipboardManager.addPrimaryClipChangedListener(clipboardListener)
        scope.launch {
            val ok = RimeManager.ensureReady(applicationContext)
            val sessionOk = ok && RimeManager.ensureSession()
            uiState.update {
                it.copy(
                    ready = sessionOk,
                    schemaName = if (sessionOk) RimeManager.currentSchema() else "",
                    statusMessage = if (!ok) "引擎初始化失败" else "",
                )
            }
            refreshState()
        }
        // 首次部署可能超过会话等待窗口（大词典编译），由 onKeyAction 按键重试兜底
        scope.launch {
            while (!RimeManager.isSessionReady()) {
                kotlinx.coroutines.delay(3000)
                if (RimeManager.ensureSessionNow()) {
                    refreshState()
                    break
                }
            }
        }
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
                            AzimeKeyboardScreen(state = state, onAction = ::onKeyAction)
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
        scope.launch { refreshState() }
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        lifecycleOwner.pause()
        super.onFinishInputView(finishingInput)
    }

    override fun onFinishInput() {
        RimeManager.clearComposition()
        scope.launch { refreshState() }
        super.onFinishInput()
    }

    override fun onDestroy() {
        runCatching { clipboardManager.removePrimaryClipChangedListener(clipboardListener) }
        scope.cancel()
        lifecycleOwner.destroy()
        super.onDestroy()
    }

    // ── 剪贴板 ───────────────────────────────────────────────

    private fun readClipboard() {
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
                        uiState.update { it.copy(clipText = "🖼 [图片 ${target.length() / 1024}KB]") }
                    }
                }
            }
            return
        }
        val text = item.coerceToText(this)?.toString().orEmpty()
        if (text.isNotBlank()) {
            uiState.update { it.copy(clipText = text.take(80), clipAtMs = System.currentTimeMillis()) }
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

    private fun onKeyAction(action: KeyAction) {
        scope.launch {
            when (action) {
                is KeyAction.CharKey -> handleChar(action.c)
                is KeyAction.DirectCommit -> {
                    currentInputConnection?.commitText(action.text, 1)
                    pushUndo(action.text)
                    uiState.update { it.copy(shiftOn = false) }
                    refreshState()
                }
                KeyAction.Shift -> uiState.update { it.copy(shiftOn = !it.shiftOn, capsOn = false) }
                KeyAction.Backspace -> handleBackspace()
                KeyAction.Space -> applyResult(RimeManager.processKey(KEY_SPACE))
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
                    RimeManager.switchSchema(action.schemaId)
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
                                repeat(resolved.moveLeft) { ic.sendKeyEvent(android.view.KeyEvent(0, 0, 0, 0, 0, 0, android.view.KeyEvent.KEYCODE_DPAD_LEFT, 0)) }
                                repeat(resolved.moveRight) { ic.sendKeyEvent(android.view.KeyEvent(0, 0, 0, 0, 0, 0, android.view.KeyEvent.KEYCODE_DPAD_RIGHT, 0)) }
                                ic.endBatchEdit()
                                pushUndo(resolved.text)
                            }
                        }
                        is ResolvedAction.Command -> runCommand(resolved.ident)
                        null -> {}
                    }
                }
                KeyAction.DeleteAll -> deleteAllText()
                KeyAction.Undo -> undo()
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
                KeyAction.Deploy -> {
                    // 重新部署方案（○ 菜单 / 方案设置页）
                    uiState.update { it.copy(statusMessage = "正在重新部署方案…") }
                    runCatching { RimeManager.deployImportedSchemas(applicationContext) }
                    uiState.update { it.copy(statusMessage = "") }
                    refreshState()
                }
                KeyAction.ToggleClipboardPanel ->
                    uiState.update { it.copy(showClipboardPanel = !it.showClipboardPanel) }
                KeyAction.ToggleMenuPanel ->
                    uiState.update { it.copy(showMenuPanel = !it.showMenuPanel) }
                is KeyAction.CommitClipboard -> {
                    currentInputConnection?.commitText(action.text, 1)
                    pushUndo(action.text)
                    // 上屏后条目消失 + 面板收起，不干扰后续输入
                    uiState.update {
                        it.copy(clipText = "", clipAtMs = 0L, showClipboardPanel = false, showMenuPanel = false)
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

    private fun pushUndo(text: String) {
        if (text.isEmpty()) return
        undoStack.addLast(text)
        while (undoStack.size > 50) undoStack.removeFirst()
    }

    /** 撤回：删除最近一次上屏的文本。 */
    private suspend fun undo() {
        val last = undoStack.removeLastOrNull() ?: return
        currentInputConnection?.deleteSurroundingText(last.length, 0)
        refreshState()
    }

    /** 上滑全删：删除光标前后全部文本。 */
    private suspend fun deleteAllText() {
        val ic = currentInputConnection ?: return
        val before = ic.getTextBeforeCursor(MAX_TEXT, 0) ?: ""
        val after = ic.getTextAfterCursor(MAX_TEXT, 0) ?: ""
        if (before.isNotEmpty() || after.isNotEmpty()) {
            pushUndo(before.toString() + after.toString())
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
            pushUndo(sel)
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
            val before = ic.getTextBeforeCursor(2, 0) ?: ""
            if (before.length == 2 && Character.isSurrogatePair(before[0], before[1])) {
                ic.deleteSurroundingText(2, 0)
            } else {
                ic.deleteSurroundingText(1, 0)
            }
        }
        refreshState()
    }

    private suspend fun handleEnter() {
        val result = RimeManager.processKey(KEY_RETURN)
        if (result.processed && result.committedText.isNotEmpty()) {
            pushUndo(result.committedText)
            applyResult(result)
            return
        }
        // 无编码时回车 = 换行
        currentInputConnection?.commitText("\n", 1)
        pushUndo("\n")
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
        uiState.update {
            it.copy(
                candidates = result.candidates.map { c -> c.toCandidate() },
                preedit = result.preeditText,
                asciiMode = result.isAsciiMode,
                hasNextPage = result.hasNextPage,
                hasPrevPage = result.hasPrevPage,
                // 编码清空（候选消失）时自动收起更多候选面板
                showCandidatePanel = it.showCandidatePanel && result.candidates.isNotEmpty(),
                // 打字即消亡：开始组合后工具栏剪贴板条消失（参考 复制自动添加到候选.lua）
                clipText = if (result.preeditText.isNotEmpty()) "" else it.clipText,
                clipAtMs = if (result.preeditText.isNotEmpty()) 0L else it.clipAtMs,
            )
        }
    }

    private suspend fun refreshState() {
        val result = RimeManager.getProcessResult()
        updateFromResult(result)
        uiState.update { state ->
            state.copy(
                ready = RimeManager.isReady(),
                schemaName = RimeManager.currentSchema().ifBlank { state.schemaName },
                schemas = if (RimeManager.isReady()) RimeManager.availableSchemas() else state.schemas,
                statusMessage = if (RimeManager.isMaintaining()) "正在部署词典，请稍候…" else "",
            )
        }
    }

    companion object {
        private const val MAX_TEXT = 100000
    }
}
