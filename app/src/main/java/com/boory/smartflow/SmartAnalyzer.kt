
package com.boory.smartflow

import java.time.Instant
import java.time.ZoneId

object SmartAnalyzer {

    private val marketZone = ZoneId.of("America/New_York")

    private const val BAR_DURATION = 5 * 60 * 1000L

    fun analyze(
        symbol: String,
        candles: List<Candle>
    ): AnalysisResult {

        val now = System.currentTimeMillis()

        // لا نستخدم شمعة الخمس دقائق قبل اكتمالها
        val bars = candles
            .filter {
                it.time + BAR_DURATION <= now &&
                it.close > 0.0 &&
                it.high >= it.low &&
                it.volume >= 0.0
            }
            .sortedBy { it.time }

        require(bars.size >= 25) {
            "لا توجد 25 شمعة مكتملة كافية للتحليل"
        }

        val last = bars.last()

        val previousBars = bars
            .dropLast(1)
            .takeLast(20)

        val averageVolume = previousBars
            .map { it.volume }
            .average()
            .coerceAtLeast(1.0)

        val rvol = last.volume / averageVolume

        // تحديد يوم التداول حسب توقيت نيويورك
        val sessionDate = Instant.ofEpochMilli(last.time)
            .atZone(marketZone)
            .toLocalDate()

        val sessionBars = bars.filter {
            Instant.ofEpochMilli(it.time)
                .atZone(marketZone)
                .toLocalDate() == sessionDate
        }

        // VWAP تراكمي لشموع اليوم المتاحة من المصدر
        val totalVolume = sessionBars.sumOf { it.volume }

        val vwap = if (totalVolume > 0.0) {
            sessionBars.sumOf {
                ((it.high + it.low + it.close) / 3.0) *
                    it.volume
            } / totalVolume
        } else {
            last.close
        }

        // ATR
        val trueRanges = bars.zipWithNext { previous, current ->
            maxOf(
                current.high - current.low,
                kotlin.math.abs(current.high - previous.close),
                kotlin.math.abs(current.low - previous.close)
            )
        }

        val atr = trueRanges
            .takeLast(14)
            .average()
            .coerceAtLeast(last.close * 0.001)

        // الزخم
        val oldClose = bars[bars.size - 4].close

        val momentum = if (oldClose > 0.0) {
            ((last.close - oldClose) / oldClose) * 100.0
        } else {
            0.0
        }

        // الاختراق فوق أعلى 20 شمعة مكتملة سابقة
        val highestPrevious = previousBars.maxOf { it.high }
        val breakout = last.close > highestPrevious

        val reasons = mutableListOf<String>()
        var score = 0

        val volumeScore = when {
            rvol >= 4.0 -> 25
            rvol >= 3.0 -> 22
            rvol >= 2.0 -> 18
            rvol >= 1.5 -> 13
            rvol >= 1.2 -> 8
            else -> 0
        }

        score += volumeScore

        if (rvol >= 1.5) {
            reasons.add("ارتفاع ملحوظ في حجم التداول RVOL")
        }

        if (last.close > vwap) {
            score += 20
            reasons.add("السعر أعلى من VWAP")
        }

        if (breakout) {
            score += 25
            reasons.add("اختراق أعلى 20 شمعة سابقة")
        }

        val momentumScore = when {
            momentum >= 2.0 -> 20
            momentum >= 1.0 -> 16
            momentum >= 0.5 -> 12
            momentum > 0.0 -> 7
            else -> 0
        }

        score += momentumScore

        if (momentum > 0.0) {
            reasons.add("زخم سعري إيجابي")
        }

        val trendBars = bars.takeLast(5)

        val risingCloses = trendBars
            .zipWithNext()
            .count {
                it.second.close > it.first.close
            }

        if (risingCloses >= 3) {
            score += 10
            reasons.add("اتجاه قصير المدى صاعد")
        }

        score = score.coerceIn(0, 100)

        val risk = atr * 1.5
        val entry = last.close
        val stop = (entry - risk).coerceAtLeast(0.01)

        val signal = when {
            score >= 85 -> "قوية جداً"
            score >= 65 -> "قوية"
            score >= 50 -> "متوسطة"
            else -> "ضعيفة"
        }

        if (reasons.isEmpty()) {
            reasons.add("لم تتوفر شروط دخول قوية حتى الآن")
        }

        return AnalysisResult(
            symbol = symbol.uppercase(),
            price = last.close,
            score = score,
            rvol = rvol,
            vwap = vwap,
            atr = atr,
            momentum = momentum,
            breakout = breakout,
            entry = entry,
            stopLoss = stop,
            target1 = entry + risk,
            target2 = entry + risk * 2,
            target3 = entry + risk * 3,
            reasons = reasons,
            signal = signal
        )
    }
}
