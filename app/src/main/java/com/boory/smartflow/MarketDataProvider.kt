package com.boory.smartflow

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

private val marketHttp = OkHttpClient.Builder()
    .connectTimeout(15, TimeUnit.SECONDS)
    .readTimeout(20, TimeUnit.SECONDS)
    .build()

private val newYorkZone = ZoneId.of("America/New_York")

class TwelveDataProvider : MarketDataProvider {

    override suspend fun fetchBars(
        symbol: String,
        apiKey: String,
        secretKey: String
    ): List<Candle> = withContext(Dispatchers.IO) {

        require(apiKey.isNotBlank()) {
            "أدخل مفتاح Twelve Data أولاً"
        }

        val encoded = URLEncoder.encode(symbol.trim(), "UTF-8")

        val url = "https://api.twelvedata.com/time_series" +
            "?symbol=$encoded" +
            "&interval=5min" +
            "&outputsize=500" +
            "&prepost=true" +
            "&timezone=America%2FNew_York" +
            "&apikey=$apiKey"

        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        marketHttp.newCall(request).execute().use { response ->

            if (!response.isSuccessful) {
                error("Twelve Data: خطأ الاتصال ${response.code}")
            }

            val body = response.body?.string()
                ?: error("لم تصل بيانات من Twelve Data")

            val json = JSONObject(body)

            if (json.optString("status") == "error") {
                error(
                    json.optString(
                        "message",
                        "خطأ في مصدر البيانات"
                    )
                )
            }

            val values = json.optJSONArray("values")
                ?: error("لا توجد شموع لهذا السهم")

            val formatter =
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

            val result = mutableListOf<Candle>()

            for (i in 0 until values.length()) {

                val item = values.getJSONObject(i)

                val localTime = LocalDateTime.parse(
                    item.getString("datetime"),
                    formatter
                )

                val timestamp = localTime
                    .atZone(newYorkZone)
                    .toInstant()
                    .toEpochMilli()

                result.add(
                    Candle(
                        time = timestamp,
                        open = item.getDouble("open"),
                        high = item.getDouble("high"),
                        low = item.getDouble("low"),
                        close = item.getDouble("close"),
                        volume = item.optDouble("volume", 0.0)
                    )
                )
            }

            result.sortedBy { it.time }
        }
    }
}

class AlpacaProvider : MarketDataProvider {

    override suspend fun fetchBars(
        symbol: String,
        apiKey: String,
        secretKey: String
    ): List<Candle> = withContext(Dispatchers.IO) {

        require(apiKey.isNotBlank()) {
            "أدخل Alpaca API Key"
        }

        require(secretKey.isNotBlank()) {
            "أدخل Alpaca Secret Key"
        }

        val encoded = URLEncoder.encode(symbol.trim(), "UTF-8")

        val url =
            "https://data.alpaca.markets/v2/stocks/$encoded/bars" +
            "?timeframe=5Min&limit=500&feed=iex"

        val request = Request.Builder()
            .url(url)
            .addHeader("APCA-API-KEY-ID", apiKey)
            .addHeader("APCA-API-SECRET-KEY", secretKey)
            .get()
            .build()

        marketHttp.newCall(request).execute().use { response ->

            if (!response.isSuccessful) {
                error("Alpaca: خطأ الاتصال ${response.code}")
            }

            val body = response.body?.string()
                ?: error("لم تصل بيانات من Alpaca")

            val json = JSONObject(body)

            val bars = json.optJSONArray("bars")
                ?: error("لا توجد شموع لهذا السهم")

            val result = mutableListOf<Candle>()

            for (i in 0 until bars.length()) {

                val item = bars.getJSONObject(i)

                result.add(
                    Candle(
                        time = Instant.parse(
                            item.getString("t")
                        ).toEpochMilli(),
                        open = item.getDouble("o"),
                        high = item.getDouble("h"),
                        low = item.getDouble("l"),
                        close = item.getDouble("c"),
                        volume = item.optDouble("v", 0.0)
                    )
                )
            }

            result.sortedBy { it.time }
        }
    }
}
