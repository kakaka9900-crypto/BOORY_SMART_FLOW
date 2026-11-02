package com.boory.smartflow

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(28, 36, 28, 36)
            setBackgroundColor(Color.rgb(10, 18, 30))
        }

        fun text(value: String, size: Float, color: Int, bold: Boolean = false) {
            root.addView(TextView(this).apply {
                text = value
                textSize = size
                setTextColor(color)
                gravity = Gravity.CENTER
                if (bold) setTypeface(null, android.graphics.Typeface.BOLD)
                setPadding(8, 12, 8, 12)
            })
        }

        text("BOORY SMART FLOW", 27f, Color.WHITE, true)
        text("مساعدك الذكي لمراقبة الأسهم الأمريكية", 17f, Color.LTGRAY)
        text("الإصدار التجريبي 0.1.0", 13f, Color.GRAY)
        text("حالة النظام: جاهز للتجهيز", 18f, Color.rgb(40, 200, 130), true)
        text("محرك التحليل: قيد التطوير", 15f, Color.WHITE)
        text("الفاصل المستهدف: 5 دقائق", 15f, Color.WHITE)
        text("RVOL  •  VWAP  •  ATR  •  الزخم والاختراقات", 14f, Color.CYAN)

        setContentView(root)
    }
}
