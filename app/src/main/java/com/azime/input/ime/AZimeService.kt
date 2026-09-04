package com.azime.input.ime

import android.inputmethodservice.InputMethodService
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.azime.input.core.rime.RimeManager
import com.azime.input.core.rime.RimeManager.KEY_BACKSPACE
import com.azime.input.core.rime.RimeManager.KEY_RETURN
import com.azime.input.core.rime.RimeManager.KEY_SPACE
import com.azime.input.core.rime.toCandidate
import com.azime.input.ui.keyboard.AzimeKeyboardScreen
import com.azime.input.ui.keyboard.KeyAction
import com.azime.input.ui.keyboard.KeyboardUiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AZimeService : InputMethodService() {

    private val lifecycleOwner = ImeLifecycleOwner()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val uiState = MutableStateFlow(KeyboardUiState())

    override fun onCreate() {
        super.onCreate()
        lifecycleOwner.onCreate()
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
        }
    }

    override fun onCreateInputView(): View {
        lifecycleOwner.resume() // 视图可能被重建（配置变化），确保 Compose 生命周期就绪
        val composeView = ComposeView(this)
        composeView.setViewTreeLifecycleOwner(lifecycleOwner)
        composeView.setViewTreeSavedStateRegistryOwner(lifecycleOwner)
        composeView.setContent {
            MaterialTheme(colorScheme = lightColorScheme()) {
                val state by uiState.collectAsState()
                AzimeKeyboardScreen(state = state, onAction = ::onKeyAction)
            }
        }
        return composeView
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        lifecycleOwner.resume()
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
        scope.cancel()
        lifecycleOwner.destroy()
        super.onDestroy()
    }

    // ── 按键处理 ─────────────────────────────────────────────

    private fun onKeyAction(action: KeyAction) {
        scope.launch {
            when (action) {
                is KeyAction.CharKey -> handleChar(action.c)
                is KeyAction.DirectCommit -> {
                    // 长按符号等直出文本：绕过编码，直接上屏
                    currentInputConnection?.commitText(action.text, 1)
                    uiState.update { it.copy(shiftOn = false) }
                    refreshState()
                }
                KeyAction.Shift -> uiState.update { it.copy(shiftOn = !it.shiftOn) }
                KeyAction.Backspace -> applyResult(RimeManager.processKey(KEY_BACKSPACE))
                KeyAction.Space -> applyResult(RimeManager.processKey(KEY_SPACE))
                KeyAction.Enter -> handleEnter()
                KeyAction.ToggleSymbols -> uiState.update { it.copy(symbolPage = !it.symbolPage) }
                KeyAction.ToggleAscii -> {
                    val ascii = RimeManager.toggleAsciiMode()
                    uiState.update { it.copy(asciiMode = ascii, shiftOn = false) }
                    refreshState()
                }
                is KeyAction.Candidate -> {
                    RimeManager.selectCandidate(action.index)
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
            }
        }
    }

    private suspend fun handleChar(c: Char) {
        val state = uiState.value
        // 英文模式或临时 shift：字母直出，不进编码
        if (state.asciiMode || state.shiftOn) {
            val text = if (state.shiftOn) c.uppercaseChar() else c
            currentInputConnection?.commitText(text.toString(), 1)
            uiState.update { it.copy(shiftOn = false) }
            return
        }
        applyResult(RimeManager.processKey(c.lowercaseChar().code))
    }

    private suspend fun handleEnter() {
        val result = RimeManager.processKey(KEY_RETURN)
        if (result.processed && result.committedText.isNotEmpty()) {
            applyResult(result)
            return
        }
        // 无编码时回车 = 换行
        currentInputConnection?.commitText("\n", 1)
        refreshState()
    }

    /** 把一次按键结果同步到输入框与 UI 状态。 */
    private fun applyResult(result: com.kingzcheung.xime.rime.RimeProcessResult) {
        if (result.committedText.isNotEmpty()) {
            currentInputConnection?.commitText(result.committedText, 1)
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
}
