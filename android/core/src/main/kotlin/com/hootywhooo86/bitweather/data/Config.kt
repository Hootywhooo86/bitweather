package com.hootywhooo86.bitweather.data

/**
 * Settings the shared weather logic reads (units, alert thresholds, air-quality scale).
 * Each app loads and saves them its own way (Android: SharedPreferences, Windows: a JSON file).
 */
object Config {
    val units = Units()
    val thresholds = Thresholds()
    var aqScale = "aqhi"
}

data class Units(
    var temp: String = "C", var wind: String = "kmh", var precip: String = "mm",
    var pressure: String = "hPa", var dist: String = "km", var clock: String = "24",
)

/** Custom alert thresholds in metric (°C, km/h, cm). null = off. */
data class Thresholds(var cold: Double? = -20.0, var hot: Double? = 30.0, var gust: Double? = 70.0, var snow: Double? = 10.0)

data class Slot(val id: String, var size: String, var hidden: Boolean = false)

