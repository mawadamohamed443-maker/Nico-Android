package com.nico.assistant

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.util.Locale

class MainActivity : AppCompatActivity() {
    private lateinit var brain: NicoBrain
    private var tts: TextToSpeech? = null
    private var sr: SpeechRecognizer? = null
    private lateinit var log: TextView
    private lateinit var status: TextView
    private lateinit var scroll: ScrollView
    private lateinit var input: EditText

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        setContentView(R.layout.activity_main)
        brain = NicoBrain(this)
        log = findViewById(R.id.log); status = findViewById(R.id.status)
        scroll = findViewById(R.id.scroll); input = findViewById(R.id.input)

        tts = TextToSpeech(this) { if (it == TextToSpeech.SUCCESS) tts?.language = Locale("ar") }
        requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CALL_PHONE, Manifest.permission.READ_CONTACTS), 1)

        findViewById<Button>(R.id.btnPerm).setOnClickListener {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CALL_PHONE, Manifest.permission.READ_CONTACTS), 1)
        }
        findViewById<Button>(R.id.btnAcc).setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        findViewById<Button>(R.id.btnMic).setOnClickListener { listen() }
        findViewById<Button>(R.id.btnSend).setOnClickListener { send() }
        input.setOnEditorActionListener { _, a, _ -> if (a == EditorInfo.IME_ACTION_SEND) { send(); true } else false }
        add("نيكو: أهلا! اضغطي على المايك واتكلمي.")
    }

    private fun send() { val t = input.text.toString().trim(); if (t.isNotEmpty()) { input.setText(""); process(t) } }

    private fun process(t: String) {
        add("أنتِ: $t")
        val r = brain.handle(t)
        if (r.isNotEmpty()) { add("نيكو: $r"); say(r) }
    }

    private fun say(t: String) { tts?.speak(t, TextToSpeech.QUEUE_FLUSH, null, "nico") }

    private fun add(t: String) { log.append(t + "\n\n"); scroll.post { scroll.fullScroll(ScrollView.FOCUS_DOWN) } }

    private fun listen() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) { add("نيكو: الاستماع مش متاح على الجهاز ده."); return }
        tts?.stop(); sr?.destroy()
        sr = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(p: Bundle?) { status.text = "بسمعك..." }
                override fun onResults(r: Bundle?) { status.text = "نيكو"; r?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let { process(it) } }
                override fun onError(e: Int) { status.text = "نيكو"; add("نيكو: معرفتش أسمعك، جربي تاني (لو أوفلاين نزّلي اللغة العربية من إعدادات جوجل للصوت).") }
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(v: Float) {}
                override fun onBufferReceived(b: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onPartialResults(p: Bundle?) {}
                override fun onEvent(t: Int, p: Bundle?) {}
            })
            startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ar-EG")
                .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true))
        }
    }

    override fun onDestroy() { sr?.destroy(); tts?.shutdown(); super.onDestroy() }
}
