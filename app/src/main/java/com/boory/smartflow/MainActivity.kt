package com.boory.smartflow

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Button
import android.media.ToneGenerator
import android.media.AudioManager
import android.os.Handler
import android.os.Looper

class MainActivity : Activity() {

    private var tone: ToneGenerator? = null
    private val handler = Handler(Looper.getMainLooper())
    private var soundEnabled = true
    private lateinit var soundStatus: TextView

    private val green = Color.rgb(40, 210, 130)
    private val yellow = Color.rgb(255, 190, 60)
    private val purple = Color.rgb(180, 140, 255)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        tone = ToneGenerator(AudioManager.STREAM_ALARM, 85)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(25, 30, 25, 30)
            setBackgroundColor(Color.rgb(10, 18, 30))
        }

        fun text(value: String, size: Float, color: Int, bold: Boolean = false) {
            root.addView(TextView(this).apply {
                text = value
                textSize = size
                setTextColor(color)
                gravity = Gravity.CENTER
                if (bold) setTypeface(null, android.graphics.Typeface.BOLD)
                setPadding(8, 10, 8, 10)
            })
        }

        fun soundButton(label: String, color: Int, action: () -> Unit) {
            root.addView(Button(this).apply {
                text = label
                setTextColor(Color.WHITE)
                setBackgroundTintList(
                    android.content.res.ColorStateList.valueOf(color)
                )
                setOnClickListener { action() }
            })
        }

        text("BOORY SMART FLOW", 27f, Color.WHITE, true)
        text("مساعدك الذكي لمراقبة الأسهم الأمريكية", 17f, Color.LTGRAY)
        text("الإصدار التجريبي 0.2.0", 13f, Color.GRAY)
        text("حالة النظام: جاهز للتطوير", 17f, green, true)

        text("محرك التحليل: قيد التطوير", 15f, Color.WHITE)
        text("الفاصل المستهدف: 5 دقائق", 15f, Color.WHITE)
        text("RVOL  •  VWAP  •  ATR  •  الزخم والاختراقات", 13f, Color.CYAN)

        text("━━━━━━━━━━━━━━━━━━━━", 14f, Color.GRAY)
        text("نظام التنبيهات الصوتية", 21f, Color.WHITE, true)

        text("قوة 85 - 100 | فرصة قوية", 15f, green, true)
        soundButton("تجربة صوت الفرصة القوية", Color.rgb(20, 120, 75)) {
            playStrong()
        }

        text("قوة 65 - 84 | فرصة متوسطة", 15f, yellow, true)
        soundButton("تجربة صوت الفرصة المتوسطة", Color.rgb(145, 100, 20)) {
            playMedium()
        }

        text("قوة 50 - 64 | فرصة ضعيفة", 15f, purple, true)
        soundButton("تجربة صوت الفرصة الضعيفة", Color.rgb(90, 65, 135)) {
            playWeak()
        }

        soundStatus = TextView(this).apply {
            text = "الصوت مفعل"
            textSize = 15f
            gravity = Gravity.CENTER
            setTextColor(green)
            setPadding(8, 15, 8, 8)
        }
        root.addView(soundStatus)

        soundButton("كتم الصوت / تشغيل الصوت", Color.rgb(55, 65, 85)) {
            soundEnabled = !soundEnabled
            soundStatus.text = if (soundEnabled) "الصوت مفعل" else "الصوت مكتوم"
            soundStatus.setTextColor(if (soundEnabled) green else yellow)
        }

        setContentView(root)
    }

    private fun playStrong() {
        if (!soundEnabled) return
        tone?.startTone(ToneGenerator.TONE_PROP_ACK, 180)
        handler.postDelayed({
            if (soundEnabled) tone?.startTone(ToneGenerator.TONE_PROP_ACK, 180)
        }, 260)
    }

    private fun playMedium() {
        if (!soundEnabled) return
        tone?.startTone(ToneGenerator.TONE_PROP_BEEP, 300)
    }

    private fun playWeak() {
        if (!soundEnabled) return
        tone?.startTone(ToneGenerator.TONE_PROP_PROMPT, 120)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        tone?.release()
        tone = null
        super.onDestroy()
    }
}
