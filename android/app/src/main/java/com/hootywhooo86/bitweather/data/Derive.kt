package com.hootywhooo86.bitweather.data

import java.time.LocalDate
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.roundToInt

// ---------------------------------------------------------------- weather codes
object Wmo {
    private val map = mapOf(
        0 to ("Clear" to "clear"), 1 to ("Mainly clear" to "mostly"), 2 to ("Partly cloudy" to "partly"), 3 to ("Overcast" to "cloudy"),
        45 to ("Fog" to "fog"), 48 to ("Freezing fog" to "fog"),
        51 to ("Light drizzle" to "drizzle"), 53 to ("Drizzle" to "drizzle"), 55 to ("Heavy drizzle" to "drizzle"),
        56 to ("Freezing drizzle" to "freezing"), 57 to ("Freezing drizzle" to "freezing"),
        61 to ("Light rain" to "rain"), 63 to ("Rain" to "rain"), 65 to ("Heavy rain" to "rain"),
        66 to ("Freezing rain" to "freezing"), 67 to ("Freezing rain" to "freezing"),
        71 to ("Light snow" to "snow"), 73 to ("Snow" to "snow"), 75 to ("Heavy snow" to "snow"), 77 to ("Snow grains" to "snow"),
        80 to ("Light showers" to "showers"), 81 to ("Showers" to "showers"), 82 to ("Heavy showers" to "rain"),
        85 to ("Snow showers" to "snow"), 86 to ("Heavy snow showers" to "snow"),
        95 to ("Thunderstorm" to "thunder"), 96 to ("Thunderstorm, hail" to "thunder"), 99 to ("Thunderstorm, hail" to "thunder"),
    )
    fun name(c: Int) = map[c]?.first ?: "Unknown"
    fun kind(c: Int) = map[c]?.second ?: "clear"
    fun isSnow(c: Int) = c in 71..77 || c == 85 || c == 86
}

// ---------------------------------------------------------------- formatting (reads the live unit settings)
object Fmt {
    private val u get() = Prefs.units
    private fun ok(v: Double) = !v.isNaN()

    fun t(c: Double): String = if (!ok(c)) "--°" else "${(if (u.temp == "F") c * 9 / 5 + 32 else c).roundToInt()}°"
    private val windF = mapOf("kmh" to 1.0, "mph" to 0.621371, "ms" to 1 / 3.6, "kn" to 0.539957)
    val windUnit get() = mapOf("kmh" to "km/h", "mph" to "mph", "ms" to "m/s", "kn" to "kn")[u.wind] ?: "km/h"
    fun wNum(k: Double): String = if (!ok(k)) "--" else (k * (windF[u.wind] ?: 1.0)).roundToInt().toString()
    fun w(k: Double) = "${wNum(k)} $windUnit"
    fun p(mm: Double): String = when {
        !ok(mm) -> "--"
        u.precip == "in" -> String.format(Locale.US, "%.2f", mm / 25.4)
        mm < 10 -> String.format(Locale.US, "%.1f", mm)
        else -> mm.roundToInt().toString()
    }
    val pUnit get() = if (u.precip == "in") "in" else "mm"
    fun pr(h: Double): String = when {
        !ok(h) -> "--"
        u.pressure == "inHg" -> String.format(Locale.US, "%.2f", h * 0.02953)
        u.pressure == "kPa" -> String.format(Locale.US, "%.1f", h / 10)
        else -> String.format(Locale.US, "%,d", h.roundToInt())
    }
    fun dist(m: Double): Pair<String, String> {
        if (!ok(m)) return "--" to "km"
        return if (u.dist == "mi") {
            val v = m / 1609.34; (if (v < 10) String.format(Locale.US, "%.1f", v) else v.roundToInt().toString()) to "mi"
        } else {
            val v = m / 1000; (if (v < 10) String.format(Locale.US, "%.1f", v) else v.roundToInt().toString()) to "km"
        }
    }
    fun clock(s: String): String {
        if (s.length < 16) return s
        if (u.clock == "24") return s.substring(11, 16)
        val hh = s.substring(11, 13).toInt(); val mm = s.substring(14, 16)
        return "${if (hh % 12 == 0) 12 else hh % 12}${if (mm == "00") "" else ":$mm"}${if (hh < 12) "a" else "p"}"
    }
    fun dow(date: String): String = runCatching {
        LocalDate.parse(date.take(10)).dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, Locale.ENGLISH)
    }.getOrDefault("")
    fun compass(deg: Double): String = if (!ok(deg)) "--" else listOf("N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE", "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW")[((deg % 360) / 22.5).roundToInt() % 16]
    fun compass8(deg: Double): String = if (!ok(deg)) "--" else listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")[((deg % 360) / 45).roundToInt() % 8]
}

// ---------------------------------------------------------------- derived insights
object Derive {
    fun hoursAhead(w: Weather, n: Int): List<Int> {
        val i0 = w.i0(); return (i0 until minOf(w.hourly.time.size, i0 + n)).toList()
    }
    fun dayHours(w: Weather, d: Int): List<Int> {
        val day = w.daily.time.getOrNull(d) ?: return emptyList()
        return w.hourly.time.indices.filter { w.hourly.time[it].startsWith(day) }
    }
    fun days(w: Weather, n: Int = 10): List<Int> {
        val d0 = w.d0(); return (d0 until minOf(w.daily.time.size, d0 + n)).toList()
    }

    fun customAlerts(w: Weather): List<Alert> {
        val T = Prefs.thresholds; val H = w.hourly
        val hrs = hoursAhead(w, 48)
        if (hrs.isEmpty()) return emptyList()
        val out = ArrayList<Alert>()
        val minI = hrs.minBy { H.temp.at(it).takeUnless { v -> v.isNaN() } ?: Double.MAX_VALUE }
        val maxI = hrs.maxBy { H.temp.at(it).takeUnless { v -> v.isNaN() } ?: -Double.MAX_VALUE }
        val gI = hrs.maxBy { H.gust.at(it).takeUnless { v -> v.isNaN() } ?: -1.0 }
        fun whenOf(i: Int) = "${Fmt.dow(H.time[i])} ${Fmt.clock(H.time[i])}"
        fun day(i: Int) = H.time[i].take(10)
        T.cold?.let { if (H.temp.at(minI) <= it) out += Alert("custom", "Below ${Fmt.t(it)}", "Drops to ${Fmt.t(H.temp.at(minI))} ${whenOf(minI)}", "Your cold threshold alert.", key = "cold$it${day(minI)}") }
        T.hot?.let { if (H.temp.at(maxI) >= it) out += Alert("custom", "Above ${Fmt.t(it)}", "Reaches ${Fmt.t(H.temp.at(maxI))} ${whenOf(maxI)}", "Your heat threshold alert.", key = "hot$it${day(maxI)}") }
        T.gust?.let { if (H.gust.at(gI) >= it) out += Alert("custom", "Gusts over ${Fmt.w(it)}", "Gusts to ${Fmt.w(H.gust.at(gI))} ${whenOf(gI)}", "Your wind threshold alert.", key = "gust$it${day(gI)}") }
        val snow = hrs.sumOf { H.snow.at(it).takeUnless { v -> v.isNaN() } ?: 0.0 }
        T.snow?.let { if (snow >= it) out += Alert("custom", "Snow ${snow.roundToInt()} cm", "About ${snow.roundToInt()} cm of snow in the next 48 h", "Your snowfall threshold alert.", key = "snow$it${day(hrs[0])}") }
        return out
    }

    fun allAlerts(b: WeatherBundle) = b.alerts + customAlerts(b.weather)

    private fun tempWord(t: Double) = when {
        t <= -20 -> "bitterly cold"; t <= -5 -> "very cold"; t <= 5 -> "cold"; t <= 14 -> "cool"; t <= 22 -> "mild"; t <= 28 -> "warm"; else -> "hot"
    }
    fun trend(w: Weather): String? {
        val D = w.daily; val d = w.d0()
        if (d + 2 >= D.time.size) return null
        val diff = (D.tmax.at(d + 1) + D.tmax.at(d + 2)) / 2 - D.tmax.at(d)
        return when { diff >= 3 -> "warming"; diff <= -3 -> "cooling"; else -> null }
    }
    private fun partOfDay(s: String): String {
        val h = s.substring(11, 13).toIntOrNull() ?: 12
        return when { h < 5 -> "overnight"; h < 12 -> "this morning"; h < 17 -> "this afternoon"; h < 21 -> "this evening"; else -> "tonight" }
    }

    fun brief(w: Weather): String {
        val H = w.hourly
        val hrs = hoursAhead(w, 8)
        if (hrs.isEmpty()) return "Forecast loading."
        val codes = hrs.map { H.code.at(it) }
        val dom = codes.groupingBy { it }.eachCount().maxBy { it.value }.key
        val avg = hrs.map { H.temp.at(it) }.filter { !it.isNaN() }.average()
        val sb = StringBuilder("Expect ${Wmo.name(dom).lowercase()} and ${tempWord(avg)} conditions ${partOfDay(H.time[w.i0()])}")
        val tr = trend(w)
        sb.append(if (tr != null) ", with $tr trends anticipated over the next two days." else ".")
        val next12 = hoursAhead(w, 12)
        next12.firstOrNull { H.pop.at(it) >= 50 }?.let {
            sb.append(" ${if (Wmo.isSnow(H.code.at(it))) "Snow" else "Precipitation"} likely from around ${Fmt.clock(H.time[it])}.")
        }
        val g = next12.maxOfOrNull { H.gust.at(it).takeUnless { v -> v.isNaN() } ?: 0.0 } ?: 0.0
        if (g >= 50) sb.append(" Gusty winds up to ${Fmt.w(g)}.")
        return sb.toString()
    }

    fun briefExtra(w: Weather): List<String> {
        val D = w.daily; val d = w.d0(); val out = ArrayList<String>()
        val tn = minOf(d + 1, D.time.size - 1)
        out += "Tonight: low ${Fmt.t(D.tmin.at(tn))}${if (D.tmin.at(tn) <= 0) ", below freezing" else ""}."
        if (d + 1 < D.time.size) out += "Tomorrow: ${Wmo.name(D.code.at(d + 1)).lowercase()}, high ${Fmt.t(D.tmax.at(d + 1))}, ${D.popMax.at(d + 1)}% chance of precipitation."
        val wet = D.time.indices.firstOrNull { it > d && D.popMax.at(it) >= 50 }
        if (wet != null) out += "Next wet day: ${Fmt.dow(D.time[wet])} (${D.popMax.at(wet)}%)."
        return out
    }

    /** (glyph, text) — the single most notable thing coming up. */
    fun insight(b: WeatherBundle): Pair<String, String> {
        val w = b.weather; val D = w.daily; val d = w.d0()
        val a = allAlerts(b)
        if (a.isNotEmpty()) return "!" to (a[0].title + if (a[0].type == "custom") " — ${a[0].name}" else "")
        val snowDay = (d until minOf(d + 4, D.time.size)).firstOrNull { D.snowSum.at(it) >= 1 }
        if (snowDay != null) return "*" to "Snow expected ${if (snowDay == d) "today" else Fmt.dow(D.time[snowDay])} — about ${D.snowSum.at(snowDay).roundToInt()} cm"
        trend(w)?.let { return (if (it == "warming") "↗" else "↘") to "${it.replaceFirstChar { c -> c.uppercase() }} expected over the next 2 days" }
        if (D.tmin.at(d) <= 0 && D.tmax.at(d) > 0) return "*" to "Freezing overnight — watch for frost and ice"
        if (D.uvMax.at(d) >= 6) return "☀" to "High UV today (${D.uvMax.at(d).roundToInt()}) — sunscreen up"
        val rainy = D.time.indices.firstOrNull { it > d && D.popMax.at(it) >= 60 }
        if (rainy != null) return "⋮" to "Wet weather likely ${Fmt.dow(D.time[rainy])}"
        return "✓" to "Steady weather — no big changes ahead"
    }

    /** Canada's Air Quality Health Index from 3-hour averages of O3, NO2 (ppb) and PM2.5. */
    fun aqhi(air: Air?): Int? {
        air ?: return null
        val now = System.currentTimeMillis() + air.utcOffset * 1000L
        val k = air.hTime.indexOfFirst { naive(it) > now } - 1
        if (k < 2) return null
        fun avg(a: DoubleArray): Double? = listOf(a.at(k), a.at(k - 1), a.at(k - 2)).filter { !it.isNaN() }.takeIf { it.isNotEmpty() }?.average()
        val o3 = avg(air.hO3) ?: return null; val no2 = avg(air.hNo2) ?: return null; val pm = avg(air.hPm25) ?: return null
        val v = (1000 / 10.4) * ((exp(0.000537 * o3 / 1.96) - 1) + (exp(0.000871 * no2 / 1.88) - 1) + (exp(0.000487 * pm) - 1))
        return max(1, v.roundToInt())
    }

    data class AqInfo(val value: String, val n: Int, val max: Int, val colors: List<Long>, val label: String, val scale: String)

    private val AQHI_COL = listOf(0xFF00CCFF, 0xFF0099CC, 0xFF006699, 0xFFFFFF00, 0xFFFFCC00, 0xFFFF9933, 0xFFFF6666, 0xFFFF0000, 0xFFCC0000, 0xFF990000, 0xFF660000)

    fun aqInfo(air: Air?): AqInfo? {
        if (Prefs.aqScale == "aqhi") {
            val v = aqhi(air) ?: return null
            val risk = when { v <= 3 -> "Low"; v <= 6 -> "Moderate"; v <= 10 -> "High"; else -> "Very high" }
            return AqInfo(if (v > 10) "10+" else "$v", minOf(v, 11), 11, AQHI_COL, "$risk health risk", "AQHI")
        }
        val v = air?.usAqi?.takeUnless { it.isNaN() } ?: return null
        val bands = listOf(50 to ("Good" to 0xFF00E400), 100 to ("Moderate" to 0xFFFFFF00), 150 to ("Unhealthy for sensitive groups" to 0xFFFF7E00),
            200 to ("Unhealthy" to 0xFFFF0000), 300 to ("Very unhealthy" to 0xFF8F3F97), 9999 to ("Hazardous" to 0xFF7E0023))
        val b = bands.first { v <= it.first }
        val cols = bands.flatMap { listOf(it.second.second, it.second.second) }
        return AqInfo("${v.roundToInt()}", minOf(12, max(1, Math.ceil(v / 25).toInt())), 12, cols, b.second.first, "US AQI")
    }

    fun uvInfo(v: Double): Pair<String, Long> = when {
        v < 3 -> "Low" to 0xFF69F0AE; v < 6 -> "Moderate" to 0xFFFFEB3B; v < 8 -> "High" to 0xFFFF9F1C
        v < 11 -> "Very high" to 0xFFFF5252; else -> "Extreme" to 0xFFB388FF
    }

    fun beaufort(k: Double): String {
        val names = listOf("Calm", "Light air", "Light breeze", "Gentle breeze", "Moderate breeze", "Fresh breeze", "Strong breeze", "Near gale", "Gale", "Strong gale", "Storm", "Violent storm", "Hurricane")
        val lim = listOf(1, 6, 12, 20, 29, 39, 50, 62, 75, 89, 103, 118)
        val i = lim.indexOfFirst { k < it }
        return names[if (i < 0) 12 else i]
    }

    data class Moon(val phase: Double, val name: String, val illum: Int, val nextFull: Int)

    fun moon(ms: Long = System.currentTimeMillis()): Moon {
        val syn = 29.530588853
        val ref = 947182440000L // 2000-01-06 18:14 UTC new moon
        val days = (ms - ref) / 86400000.0
        val p = (((days % syn) + syn) % syn) / syn
        val names = listOf("New moon", "Waxing crescent", "First quarter", "Waxing gibbous", "Full moon", "Waning gibbous", "Last quarter", "Waning crescent")
        return Moon(p, names[(p * 8).roundToInt() % 8], ((1 - cos(p * 2 * Math.PI)) / 2 * 100).roundToInt(), (((0.5 - p + 1) % 1) * syn).roundToInt())
    }

    fun visibilityDesc(m: Double) = when { m >= 20000 -> "Perfectly clear"; m >= 10000 -> "Clear"; m >= 4000 -> "Light haze"; m >= 1000 -> "Haze / mist"; else -> "Fog" }

    fun feelsWhy(c: Current): String = when {
        c.feels < c.temp - 1 -> if (c.wind > 5) "Wind chill makes it feel colder" else "Feels colder than actual"
        c.feels > c.temp + 1 -> if (c.humidity > 50) "Humidity makes it feel warmer" else "Sun makes it feel warmer"
        else -> "Similar to the actual temperature"
    }

    fun nowSummary(w: Weather): String {
        val c = w.current; val D = w.daily; val d = w.d0()
        return "${Fmt.t(c.temp)} ${Wmo.name(c.code)} · Feels ${Fmt.t(c.feels)} · H ${Fmt.t(D.tmax.at(d))} L ${Fmt.t(D.tmin.at(d))}"
    }

    fun pressureTrend(w: Weather): String {
        val i = w.i0(); val d = w.current.pressure - w.hourly.pressure.at(maxOf(0, i - 3))
        return when { d.isNaN() -> "Steady"; d > 1 -> "▲ Rising"; d < -1 -> "▼ Falling"; else -> "► Steady" }
    }

    fun abs0(v: Double) = if (v.isNaN()) 0.0 else abs(v)
}
