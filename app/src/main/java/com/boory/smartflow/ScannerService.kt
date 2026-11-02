package com.boory.smartflow

import android.app.Service
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.IBinder
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap

class ScannerService : Service() {

    companion object {
        const val ACTION_START = "com.boory.smartflow.START"
        const val ACTION_STOP = "com.boory.smartflow.STOP"
        const val ACTION_UPDATE = "com.boory.smartflow.UPDATE"

        private const val FOREGROUND_ID = 9001
        private const val SCANNER_CHANNEL = "boory_scanner"
        private const val ALERT_CHANNEL = "boory_alerts"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var scanJob: Job? = null
    private var tone: ToneGenerator? = null
    private var soundEnabled = true

    private val lastAlerts = ConcurrentHashMap<String, Long>()

    override fun onCreate() {
        super.onCreate()
        createChannels()
        tone = ToneGenerator(AudioManager.STREAM_ALARM, 85)
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {

        when (intent?.action) {
            ACTION_STOP -> {
                stopScanner()
                return START_NOT_STICKY
            }

            ACTION_START -> startScanner()
            else -> startScanner()
        }

        return START_STICKY
    }

    private fun createChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(
                NotificationManager::class.java
            )

            val scanner = NotificationChannel(
                SCANNER_CHANNEL,
                "مراقبة BOORY",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "إشعار تشغيل ماسح الأسهم"
            }

            val alerts = NotificationChannel(
                ALERT_CHANNEL,
                "تنبيهات فرص الأسهم",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "تنبيهات فرص الأسهم المكتشفة"
            }

            manager.createNotificationChannel(scanner)
            manager.createNotificationChannel(alerts)
        }
    }

    private fun foregroundNotification(): Notification {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, SCANNER_CHANNEL)
                .setContentTitle("BOORY SMART FLOW")
                .setContentText("ماسح الأسهم يعمل")
                .setSmallIcon(android.R.drawable.ic_menu_search)
                .setOngoing(true)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setContentTitle("BOORY SMART FLOW")
                .setContentText("ماسح الأسهم يعمل")
                .setSmallIcon(android.R.drawable.ic_menu_search)
                .setOngoing(true)
                .build()
        }
    }

    private fun startScanner() {
        if (scanJob?.isActive == true) return

        try {
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(
                    FOREGROUND_ID,
                    foregroundNotification(),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
            } else {
                startForeground(
                    FOREGROUND_ID,
                    foregroundNotification()
                )
            }
        } catch (e: Exception) {
            sendUpdate("تعذر تشغيل خدمة المراقبة: ${e.message}")
            stopSelf()
            return
        }

        scanJob = scope.launch {
            scanLoop()
        }

        sendUpdate("بدأت مراقبة الأسهم")
    }

    private suspend fun scanLoop() {
        while (currentCoroutineContext().isActive) {

            val cycleStart = System.currentTimeMillis()

            val providerName = SecureStore.get(this, "provider")
                .ifBlank { "TWELVE_DATA" }

            val apiKey = SecureStore.get(this, "api_key")
            val secretKey = SecureStore.get(this, "secret_key")

            val symbols = SecureStore.get(this, "watchlist")
                .ifBlank {
                    "SOFI,PLTR,IONQ,RKLB,ACHR,JOBY,HOOD,AMD,NVDA,TSLA"
                }
                .split(",")
                .map { it.trim().uppercase() }
                .filter { it.matches(Regex("[A-Z.]{1,10}")) }
                .distinct()
                .take(30)

            if (apiKey.isBlank()) {
                sendUpdate("أدخل مفتاح مصدر البيانات من الإعدادات")
                delay(60_000)
                continue
            }

            val provider: MarketDataProvider = when (providerName) {
                "ALPACA" -> AlpacaProvider()
                else -> TwelveDataProvider()
            }

            for (symbol in symbols) {
                if (!currentCoroutineContext().isActive) break

                try {
                    val candles = provider.fetchBars(
                        symbol,
                        apiKey,
                        secretKey
                    )

                    if (candles.size >= 25) {
                        val latest = candles.maxByOrNull { it.time }!!

                        val age = System.currentTimeMillis() - latest.time

                        if (age in 0..(20 * 60 * 1000L)) {
                            val result = SmartAnalyzer.analyze(
                                symbol,
                                candles
                            )

                            sendUpdate(
                                "${result.symbol} | ${result.score}/100 | " +
                                    "${"%.2f".format(result.price)} | " +
                                    "RVOL ${"%.2f".format(result.rvol)} | " +
                                    result.signal
                            )

                            if (result.score >= 50) {
                                alertIfNeeded(result)
                            }
                        } else {
                            sendUpdate("$symbol: آخر البيانات قديمة، تم تجاهلها")
                        }
                    } else {
                        sendUpdate("$symbol: لا توجد شموع كافية")
                    }

                } catch (e: Exception) {
                    sendUpdate("$symbol: ${e.message ?: "تعذر التحليل"}")
                }

                if (providerName == "TWELVE_DATA") {
                    delay(8_000)
                } else {
                    delay(1_000)
                }
            }

            val elapsed = System.currentTimeMillis() - cycleStart
            val remaining = (5 * 60 * 1000L - elapsed).coerceAtLeast(10_000L)

            sendUpdate("اكتملت دورة الفحص. الدورة التالية خلال دقائق.")
            delay(remaining)
        }
    }

    private fun alertIfNeeded(result: AnalysisResult) {
        val now = System.currentTimeMillis()
        val previous = lastAlerts[result.symbol] ?: 0L

        if (now - previous < 30 * 60 * 1000L) return

        lastAlerts[result.symbol] = now

        val category = when {
            result.score >= 85 -> "فرصة قوية جداً"
            result.score >= 65 -> "فرصة متوسطة"
            else -> "فرصة ضعيفة"
        }

        val message = buildString {
            append("${result.symbol} — $category\n")
            append("القوة: ${result.score}/100\n")
            append("السعر: ${"%.3f".format(result.price)}\n")
            append("الدخول: ${"%.3f".format(result.entry)}\n")
            append("الوقف: ${"%.3f".format(result.stopLoss)}\n")
            append("الهدف الأول: ${"%.3f".format(result.target1)}\n")
            append("الهدف الثاني: ${"%.3f".format(result.target2)}\n")
            append("الهدف الثالث: ${"%.3f".format(result.target3)}\n")
            append("RVOL: ${"%.2f".format(result.rvol)}\n")
            append("VWAP: ${"%.3f".format(result.vwap)}\n")
            append("الأسباب: ${result.reasons.joinToString("، ")}")
        }

        val notification = if (Build.VERSION.SDK_INT >= 26) {
            Notification.Builder(this, ALERT_CHANNEL)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("${result.symbol} — $category")
                .setContentText("قوة الفرصة ${result.score}/100")
                .setStyle(Notification.BigTextStyle().bigText(message))
                .setAutoCancel(true)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("${result.symbol} — $category")
                .setContentText("قوة الفرصة ${result.score}/100")
                .setAutoCancel(true)
                .build()
        }

        getSystemService(NotificationManager::class.java)
            .notify(result.symbol.hashCode(), notification)

        if (soundEnabled) {
            when {
                result.score >= 85 -> {
                    tone?.startTone(ToneGenerator.TONE_PROP_ACK, 180)
                    scope.launch {
                        delay(300)
                        tone?.startTone(ToneGenerator.TONE_PROP_ACK, 180)
                    }
                }

                result.score >= 65 ->
                    tone?.startTone(ToneGenerator.TONE_PROP_BEEP, 300)

                else ->
                    tone?.startTone(ToneGenerator.TONE_PROP_PROMPT, 120)
            }
        }

        sendUpdate(message)
    }

    private fun sendUpdate(message: String) {
        val intent = Intent(ACTION_UPDATE)
            .setPackage(packageName)
            .putExtra("message", message)

        sendBroadcast(intent)
    }

    private fun stopScanner() {
        scanJob?.cancel()
        scanJob = null

        stopForeground(STOP_FOREGROUND_REMOVE)
        sendUpdate("تم إيقاف الماسح")
        stopSelf()
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        sendUpdate("انتهت مهلة خدمة المراقبة التي يفرضها أندرويد. أعد تشغيل الماسح.")
        stopScanner()
    }

    override fun onDestroy() {
        scanJob?.cancel()
        scope.cancel()
        tone?.release()
        tone = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
