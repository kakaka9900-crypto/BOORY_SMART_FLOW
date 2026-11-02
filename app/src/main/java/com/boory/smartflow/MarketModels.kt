package com.boory.smartflow

data class Candle(
    val time: Long,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Double
)

data class AnalysisResult(
    val symbol: String,
    val price: Double,
    val score: Int,
    val rvol: Double,
    val vwap: Double,
    val atr: Double,
    val momentum: Double,
    val breakout: Boolean,
    val entry: Double,
    val stopLoss: Double,
    val target1: Double,
    val target2: Double,
    val target3: Double,
    val reasons: List<String>,
    val signal: String
)

enum class DataSource {
    TWELVE_DATA,
    ALPACA
}

interface MarketDataProvider {
    suspend fun fetchBars(
        symbol: String,
        apiKey: String,
        secretKey: String = ""
    ): List<Candle>
}
