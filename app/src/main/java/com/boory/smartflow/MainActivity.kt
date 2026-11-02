package com.boory.smartflow

import android.Manifest
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.*
import android.text.InputType

class MainActivity : Activity() {

    private lateinit var providerSpinner: Spinner
    private lateinit var apiKeyInput: EditText
    private lateinit var secretInput: EditText
    private lateinit var watchlistInput: EditText
    private lateinit var statusText: TextView
    private lateinit var resultsText: TextView

    private var receiverRegistered = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val message = intent?.getStringExtra("message") ?: return

            runOnUiThread {
                statusText.text = "حالة الماسح: يعمل"
                resultsText.text = "$message\n\n${resultsText.text}"
            }
        }
    }

    private val background = Color.rgb(10, 18, 30)
    private val panel = Color.rgb(22, 34, 51)
    private val green = Color.rgb(40, 210, 130)
    private val white = Color.WHITE

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        buildInterface()
        loadSettings()
        registerUpdates()
    }

    private fun buildInterface() {
        val scroll = ScrollView(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(22, 28, 22, 35)
            setBackgroundColor(background)
        }

        fun label(value: String, size: Float = 15f, color: Int = white) {
            root.addView(TextView(this).apply {
                text = value
                textSize = size
                setTextColor(color)
                setPadding(4, 12, 4, 8)
            })
        }

        fun button(
            title: String,
            color: Int,
            action: () -> Unit
        ) {
            root.addView(Button(this).apply {
                text = title
                textSize = 15f
                setTextColor(white)
                setBackgroundTintList(
                    android.content.res.ColorStateList.valueOf(color)
                )
                setOnClickListener { action() }
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, 6, 0, 6)
                }
            })
        }

        fun field(
            hintText: String,
            secret: Boolean = false
        ): EditText {
            return EditText(this).apply {
                hint = hintText
                textSize = 15f
                setTextColor(white)
                setHintTextColor(Color.LTGRAY)
                setPadding(14, 10, 14, 10)
                setBackgroundColor(panel)
                inputType = if (secret) {
                    InputType.TYPE_CLASS_TEXT or
                        InputType.TYPE_TEXT_VARIATION_PASSWORD
                } else {
                    InputType.TYPE_CLASS_TEXT
                }

                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, 4, 0, 8)
                }
            }
        }

        label("BOORY SMART FLOW", 27f, green)
        label("المساعد الذكي لمراقبة الأسهم الأمريكية", 17f)
        label("الإصدار 1.0 — نسخة التطوير", 13f, Color.LTGRAY)

        statusText = TextView(this).apply {
            text = "حالة الماسح: متوقف"
            textSize = 17f
            setTextColor(green)
            setPadding(4, 18, 4, 18)
        }
        root.addView(statusText)

        label("إعداد مصدر البيانات", 20f, green)

        providerSpinner = Spinner(this)
        providerSpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            listOf("Twelve Data", "Alpaca")
        )
        root.addView(providerSpinner)

        label("مفتاح API")
        apiKeyInput = field("أدخل مفتاح مصدر البيانات")
        root.addView(apiKeyInput)

        label("Secret Key — مطلوب لـ Alpaca")
        secretInput = field("أدخل المفتاح السري", true)
        root.addView(secretInput)

        label("قائمة الأسهم")
        watchlistInput = field("SOFI,PLTR,IONQ,RKLB,ACHR")
        root.addView(watchlistInput)

        button("حفظ الإعدادات", Color.rgb(55, 75, 105)) {
            saveSettings()
            toast("تم حفظ الإعدادات المشفرة")
        }

        label("التحكم بالماسح", 20f, green)

        button("▶ تشغيل مراقبة الأسهم", Color.rgb(20, 125, 75)) {
            saveSettings()
            requestPermissionAndStart()
        }

        button("■ إيقاف المراقبة", Color.rgb(150, 55, 55)) {
            stopService(Intent(this, ScannerService::class.java))
            statusText.text = "حالة الماسح: متوقف"
        }

        label("نتائج التحليل والتنبيهات", 20f, green)

        resultsText = TextView(this).apply {
            text = "بانتظار تشغيل الماسح..."
            textSize = 14f
            setTextColor(white)
            setPadding(14, 16, 14, 16)
            setBackgroundColor(panel)
        }
        root.addView(resultsText)

        button("مشاركة آخر تقرير عبر واتساب", Color.rgb(30, 110, 80)) {
            shareReport()
        }

        label(
            "تنبيه: نتائج المؤشر تحليل آلي وليست توصية استثمارية. " +
                "تأكد من حداثة بيانات المصدر قبل اتخاذ أي قرار.",
            12f,
            Color.LTGRAY
        )

        scroll.addView(root)
        setContentView(scroll)
    }

    private fun loadSettings() {
        val provider = SecureStore.get(this, "provider")
        providerSpinner.setSelection(
            if (provider == "ALPACA") 1 else 0
        )

        apiKeyInput.setText(SecureStore.get(this, "api_key"))
        secretInput.setText(SecureStore.get(this, "secret_key"))

        watchlistInput.setText(
            SecureStore.get(this, "watchlist")
                .ifBlank {
                    "SOFI,PLTR,IONQ,RKLB,ACHR,JOBY,HOOD,AMD,NVDA,TSLA"
                }
        )
    }

    private fun saveSettings() {
        val provider = if (providerSpinner.selectedItemPosition == 1) {
            "ALPACA"
        } else {
            "TWELVE_DATA"
        }

        SecureStore.save(this, "provider", provider)
        SecureStore.save(this, "api_key", apiKeyInput.text.toString().trim())
        SecureStore.save(this, "secret_key", secretInput.text.toString().trim())
        SecureStore.save(this, "watchlist", watchlistInput.text.toString())
    }

    private fun requestPermissionAndStart() {
        if (
            Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                700
            )
            return
        }

        startScanner()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(
            requestCode,
            permissions,
            grantResults
        )

        if (requestCode == 700) {
            startScanner()
        }
    }

    private fun startScanner() {
        val intent = Intent(this, ScannerService::class.java).apply {
            action = ScannerService.ACTION_START
        }

        try {
            if (Build.VERSION.SDK_INT >= 26) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }

            statusText.text = "حالة الماسح: جارٍ التشغيل"
        } catch (e: Exception) {
            toast("تعذر تشغيل الماسح: ${e.message}")
        }
    }

    private fun registerUpdates() {
        val filter = IntentFilter(ScannerService.ACTION_UPDATE)

        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(
                receiver,
                filter,
                Context.RECEIVER_NOT_EXPORTED
            )
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(receiver, filter)
        }

        receiverRegistered = true
    }

    private fun shareReport() {
        val report = resultsText.text.toString()

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, report)
        }

        startActivity(Intent.createChooser(intent, "مشاركة تقرير BOORY"))
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    override fun onDestroy() {
        if (receiverRegistered) {
            unregisterReceiver(receiver)
        }
        super.onDestroy()
    }
}
