package com.licode.li63050a6.data

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale

/** 语音输入控制器：长按说话、松手转文字、上滑取消。 */
class VoiceInputController(private val context: Context) {

    enum class State { 空闲, 录音中, 取消中, 识别中 }

    private var recognizer: SpeechRecognizer? = null

    private val _state = MutableStateFlow(State.空闲)
    val state: StateFlow<State> = _state

    private val _level = MutableStateFlow(0f)
    val level: StateFlow<Float> = _level

    var onResult: ((String) -> Unit)? = null
    var onCancel: (() -> Unit)? = null
    var onError: ((String) -> Unit)? = null

    fun isAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    fun start() {
        if (_state.value != State.空闲) return
        val rec = SpeechRecognizer.createSpeechRecognizer(context).also { recognizer = it }
        rec.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { _state.value = State.录音中 }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rms: Float) { _level.value = rms.coerceIn(0f, 15f) / 15f }
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { if (_state.value == State.录音中) _state.value = State.识别中 }

            override fun onError(error: Int) {
                val msg = when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH -> "没有识别到语音"
                    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "网络不可用"
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "缺少麦克风权限"
                    else -> "识别出错（$error）"
                }
                _state.value = State.空闲
                if (error != SpeechRecognizer.ERROR_CLIENT) onError?.invoke(msg)
            }

            override fun onResults(results: Bundle?) {
                _state.value = State.空闲
                val list = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = list?.firstOrNull()?.trim().orEmpty()
                if (text.isNotEmpty()) onResult?.invoke(text)
            }

            override fun onPartialResults(partialResults: Bundle?) {
                // 部分结果暂不作为最终输出
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.SIMPLIFIED_CHINESE.toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        rec.startListening(intent)
    }

    /** 标记为取消（松手时丢弃）。 */
    fun markCancel() {
        if (_state.value == State.录音中) _state.value = State.取消中
    }

    /** 结束录音：正常识别或取消。 */
    fun finish(cancel: Boolean) {
        if (cancel || _state.value == State.取消中) {
            recognizer?.cancel()
            _state.value = State.空闲
            onCancel?.invoke()
        } else {
            _state.value = State.识别中
            recognizer?.stopListening()
        }
    }

    fun release() {
        recognizer?.destroy()
        recognizer = null
        _state.value = State.空闲
    }
}