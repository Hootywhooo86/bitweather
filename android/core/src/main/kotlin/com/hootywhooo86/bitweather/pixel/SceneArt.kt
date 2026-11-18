package com.hootywhooo86.bitweather.pixel

import com.hootywhooo86.bitweather.data.Derive
import com.hootywhooo86.bitweather.data.Weather
import com.hootywhooo86.bitweather.data.Wmo
import com.hootywhooo86.bitweather.data.at
import com.hootywhooo86.bitweather.data.naive
import com.hootywhooo86.bitweather.pixel.Px.hexes
import com.hootywhooo86.bitweather.pixel.Px.rnd
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The 128-pixel landscape behind the weather: dithered sky that follows the weather and time of day,
 * sun/moon on their real arc, shaded drifting clouds, the Rockies, a pine forest, a little cabin,
 * snow on the ground when it's cold, and animated rain/snow/fog/lightning.
 */
object SceneArt {
    class Spec(
        val kind: String, val day: Boolean,
        /** Position of the sun (day) or moon (night) along its arc, 0..1. */
        val arc: Double,
        /** 0 = full day/night, 1 = sunrise/sunset glow. */
        val twilight: Float,
        val cold: Boolean,
        val moonPhase: Double,
        val windy: Boolean = false,
    )

    fun specFor(w: Weather): Spec {
        val c = w.current; val D = w.daily; val d = w.d0()
        val now = w.nowLocal()
        val rise = D.sunrise.getOrNull(d)?.let { naive(it) }
        val set = D.sunset.getOrNull(d)?.let { naive(it) }
        var arc = 0.5; var tw = 0f
        if (rise != null && set != null) {
            val dayLen = (set - rise).toDouble()
            if (c.isDay) arc = ((now - rise) / dayLen).coerceIn(0.0, 1.0)
            else {
                val nightLen = 24 * 3600_000.0 - dayLen
                val since = if (now >= set) now - set else now + (24 * 3600_000L) - set
                arc = (since / nightLen).coerceIn(0.0, 1.0)
            }
            val toEdge = minOf(abs(now - rise), abs(now - set)).toDouble()
            tw = (1 - toEdge / (50 * 60_000.0)).coerceIn(0.0, 1.0).toFloat()
        }
        val cold = c.temp <= 0.5 || (D.snowSum.at(d).takeUnless { it.isNaN() } ?: 0.0) > 0.5
        return Spec(Wmo.kind(c.code), c.isDay, arc, tw, cold, Derive.moon().phase, c.gust >= 40)
    }

    // ---------------------------------------------------------------- palettes (hue-shifted ramps)
    private val SKY = mapOf(
        "clear" to hexes(0xFF2346A8, 0xFF2F62C8, 0xFF4A86E0, 0xFF7AAEEE, 0xFFB6D6F6),
        "partly" to hexes(0xFF33529E, 0xFF456EBC, 0xFF6790D2, 0xFF94B4E2, 0xFFC4D6EE),
        "grey" to hexes(0xFF3A4460, 0xFF4B5674, 0xFF5F6A88, 0xFF7A85A0, 0xFF98A2B8),
        "snow" to hexes(0xFF5F6C8C, 0xFF7886A6, 0xFF93A0BE, 0xFFB2BED6, 0xFFD3DCEA),
        "storm" to hexes(0xFF1C1630, 0xFF2A2244, 0xFF3A3058, 0xFF4C426C, 0xFF605880),
        "night" to hexes(0xFF070A1E, 0xFF0D1434, 0xFF15204C, 0xFF1F2E64, 0xFF2C3E7A),
        "nightgrey" to hexes(0xFF0E1020, 0xFF161A30, 0xFF1F2440, 0xFF2A3050, 0xFF363D60),
        "dusk" to hexes(0xFF27286A, 0xFF52368A, 0xFF9A4A8C, 0xFFDC7866, 0xFFF7B868),
    )
    private val CLOUD = hexes(0xFFFFFFFF, 0xFFE9EDF8, 0xFFC9D0E6, 0xFF9DA6C6)
    private val CLOUD_GREY = hexes(0xFFBFC5DA, 0xFF9AA2BE, 0xFF7A83A3, 0xFF5D6587)
    private val CLOUD_STORM = hexes(0xFF6E6A8C, 0xFF545072, 0xFF3E3A5A, 0xFF2A2742)
    private val CLOUD_DUSK = hexes(0xFFFFE2C0, 0xFFF4B49A, 0xFFC97E92, 0xFF7E5888)
    private val SUN = hexes(0xFFFFFBE0, 0xFFFFE780, 0xFFFFC948, 0xFFFFA22C)
    private val MOON = hexes(0xFFFFFFFF, 0xFFEAE4FF, 0xFFC6BBF0, 0xFF9A8ED4)
    private val MOUNT = hexes(0xFF6C7DB4, 0xFF5566A0, 0xFF44538A)
    private val SNOWCAP = hexes(0xFFF4F7FF, 0xFFCDD7EE, 0xFFA2AFD2)
    private val HILL = hexes(0xFF3C6670, 0xFF2F5560, 0xFF244650)
    private val PINE = hexes(0xFF3F7656, 0xFF2C5A44, 0xFF1D4234, 0xFF132E26)
    private val GRASS = hexes(0xFF5C9446, 0xFF497E3C, 0xFF386830, 0xFF2A5226)
    private val SNOWGROUND = hexes(0xFFF6F9FF, 0xFFDDE5F4, 0xFFBCC8E2, 0xFF9AA8CC)
    private val WOOD = hexes(0xFFA0623A, 0xFF7C4628, 0xFF5A301C)
    private val ROOF = hexes(0xFF6A3E5A, 0xFF4E2C46, 0xFF341C32)
    private const val RAIN = 0xFFA8DCFF.toInt()
    private const val WINDOW_LIT = 0xFFFFD36A.toInt()

    private fun skyRamp(s: Spec): IntArray {
        val k = s.kind
        val base = when {
            !s.day && k in setOf("cloudy", "fog", "rain", "drizzle", "snow", "freezing", "thunder", "showers") -> SKY["nightgrey"]!!
            !s.day -> SKY["night"]!!
            k == "thunder" -> SKY["storm"]!!
            k == "snow" -> SKY["snow"]!!
            k in setOf("cloudy", "fog", "rain", "drizzle", "freezing", "showers") -> SKY["grey"]!!
            k == "partly" -> SKY["partly"]!!
            else -> SKY["clear"]!!
        }
        if (s.twilight <= 0f) return base
        val dusk = SKY["dusk"]!!
        val t = s.twilight * (if (k in setOf("clear", "mostly", "partly")) 0.85f else 0.4f)
        return IntArray(5) { Px.mix(base[it], dusk[it], t) }
    }

    fun render(W: Int, H: Int, s: Spec, frame: Int): IntArray {
        val R = Raster(W, H)
        val hy = (H * 0.66).toInt()                 // horizon
        val sky = skyRamp(s)
        val land: (Int) -> Int = if (s.day) { c -> if (s.twilight > 0) Px.mix(c, Px.night(c), s.twilight * 0.35f) else c } else { c -> Px.night(c) }

        // sky gradient, dithered between bands
        for (y in 0 until hy + 4) for (x in 0 until W) R[x, y] = Px.ramp(sky, y.toFloat() / (hy + 4), x, y, 0.45f)

        // stars
        if (!s.day && s.kind !in setOf("cloudy", "rain", "snow", "thunder", "fog", "drizzle", "freezing")) {
            for (i in 0 until W * hy / 70) {
                val x = (rnd(i, 1) * W).toInt(); val y = (rnd(i, 2) * hy * 0.85f).toInt()
                val tw = rnd(i, 3 + (frame / 3) % 4)
                if (tw < 0.25f) continue
                R[x, y] = if (tw > 0.85f) 0xFFFFFFFF.toInt() else 0xFF9CA8E0.toInt()
                if (i % 9 == 0 && tw > 0.6f) for ((dx, dy) in listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)) R[x + dx, y + dy] = 0xFF6F7CC0.toInt()
            }
        }

        // sun or moon on its arc
        val bodyX = W * (0.08 + 0.84 * s.arc)
        val bodyY = hy - sin(s.arc * PI) * hy * 0.78 + 2
        val overcast = s.kind in setOf("cloudy", "rain", "drizzle", "freezing", "thunder", "snow")
        if (overcast) {
            // sun/moon hidden behind full cloud cover
        } else if (s.day) {
            val r = W / 22.0
            for (y in 0 until hy) for (x in 0 until W) {
                val d = sqrt((x + 0.5 - bodyX).let { it * it } + (y + 0.5 - bodyY).let { it * it })
                if (d <= r) {
                    val t = sqrt((x + 0.5 - (bodyX - r * 0.4)).let { it * it } + (y + 0.5 - (bodyY - r * 0.4)).let { it * it }) / (r * 1.7)
                    R[x, y] = Px.ramp(SUN, t.toFloat(), x, y, 0.3f)
                } else if (d <= r * 1.7) {
                    // soft glow: a lighter ring, dithered only at its outer edge
                    val g = 1 - (d - r) / (r * 0.7)
                    if (g > 0.45 || Px.bayer(x, y) < g * 1.6) R[x, y] = Px.mix(R[x, y], 0xFFFFF2C4.toInt(), 0.32f)
                }
            }
        } else {
            val r = W / 26.0
            val k = cos(s.moonPhase * 2 * PI)
            for (y in 0 until hy) for (x in 0 until W) {
                val dx = x + 0.5 - bodyX; val dy = y + 0.5 - bodyY
                val d = sqrt(dx * dx + dy * dy)
                if (d <= r) {
                    val nx = dx / r
                    val lit = if (s.moonPhase < 0.5) nx > k else nx < -k
                    R[x, y] = if (lit) Px.ramp(MOON, ((d / r) * 0.8).toFloat(), x, y, 0.3f) else Px.mix(R[x, y], 0xFF3A3F6A.toInt(), 0.7f)
                } else if (d <= r * 2.4 && Px.bayer(x, y) < (1 - (d - r) / (r * 1.4)) * 0.35) {
                    R[x, y] = Px.mix(R[x, y], 0xFFB8C0F0.toInt(), 0.25f)
                }
            }
        }

        // clouds
        val (count, ramp, cover) = when (s.kind) {
            "clear" -> Triple(if (s.day) 1 else 0, CLOUD, 0.25)
            "mostly" -> Triple(2, CLOUD, 0.35)
            "partly" -> Triple(4, CLOUD, 0.45)
            "thunder" -> Triple(9, CLOUD_STORM, 0.55)
            "rain", "drizzle", "freezing", "showers" -> Triple(8, CLOUD_GREY, 0.5)
            "snow" -> Triple(8, CLOUD, 0.5)
            else -> Triple(8, CLOUD_GREY, 0.5) // cloudy, fog
        }
        val cRamp = when {
            !s.day -> IntArray(4) { Px.night(ramp[it]).let { c -> Px.mix(c, ramp[it], 0.25f) } }
            s.twilight > 0.3f && ramp === CLOUD -> IntArray(4) { Px.mix(ramp[it], CLOUD_DUSK[it], s.twilight) }
            else -> ramp
        }
        for (i in 0 until count) {
            val speed = 0.12 + rnd(i, 4) * 0.2
            val cw = W * (0.22 + rnd(i, 5) * 0.2)
            val ch = cw * (0.32 + rnd(i, 6) * 0.1)
            val span = W + cw * 2
            val cx = ((rnd(i, 7) * span + frame * speed) % span) - cw
            val cy = hy * (0.08 + rnd(i, 8) * cover)
            cloud(R, cx, cy, cw, ch, cRamp, hy)
        }

        // far mountains (the Rockies) with snow caps, lit from the left
        fun ridge(x: Int): Double {
            val m = 0.5 + 0.32 * sin(x * 0.083 + 0.6) + 0.18 * sin(x * 0.21 + 2.1) + 0.09 * sin(x * 0.53 + 1.3)
            return hy - H * 0.05 - H * 0.2 * m
        }
        for (x in 0 until W) {
            val top = ridge(x).toInt()
            val slope = ridge(x + 1) - ridge(x - 1)
            for (y in top until hy) {
                val depth = (y - top).toFloat()
                val capLine = 2.5 + (1 - (top - (hy - H * 0.27)) / (H * 0.2)).coerceIn(0.0, 1.0) * 3
                val c = if (depth < capLine && top < hy - H * 0.14) {
                    if (slope > 0.15) SNOWCAP[1] else if (slope < -0.15) SNOWCAP[0] else SNOWCAP[if (Px.bayer(x, y) > 0.5) 0 else 1]
                } else if (slope > 0.1) MOUNT[2] else if (slope < -0.1) MOUNT[0] else MOUNT[1]
                // haze toward the horizon
                R[x, y] = Px.mix(land(c), sky[4], 0.25f + 0.25f * (y - top) / (hy - top + 1f))
            }
        }

        // rolling hills
        for (x in 0 until W) {
            val top = (hy - H * 0.03 - H * 0.05 * (0.5 + 0.5 * sin(x * 0.047 + 3.2))).toInt()
            for (y in top until H) R[x, y] = land(Px.ramp(HILL, (y - top) / (H * 0.12f), x, y))
        }

        // ground
        val groundTop = hy + (H * 0.06).toInt()
        val ground = if (s.cold) SNOWGROUND else GRASS
        for (y in groundTop until H) for (x in 0 until W) {
            val wave = sin(x * 0.11 + 1.5) * 1.5
            if (y >= groundTop + wave) R[x, y] = land(Px.ramp(ground, ((y - groundTop) / (H - groundTop + 1f)) * 0.9f, x, y))
        }

        // pine forest: a darker back row, then the front row, leaving a clearing for the cabin
        for (row in 0..1) {
            var tx = -2 + row * 2
            var ti = row * 1000
            val back = row == 0
            while (tx < W + 4) {
                val th = (H * (if (back) 0.09 + rnd(ti, 9) * 0.05 else 0.12 + rnd(ti, 9) * 0.08)).toInt()
                val base = groundTop + (if (back) -1 else (rnd(ti, 10) * 3).toInt() + 1)
                if (abs(tx - W * 0.72) > W * 0.1) {
                    pine(R, tx, base, th, s.cold, if (back) { c -> land(Px.shadow(c, 0.75f)) } else land)
                }
                tx += (if (back) 3 else 4) + (rnd(ti, 11) * 4).toInt()
                ti++
            }
        }

        // cabin with a lit window and chimney smoke
        cabin(R, (W * 0.72).toInt(), groundTop + 3, (W / 9.0).toInt().coerceAtLeast(10), s, frame, land)

        // weather overlays
        when (s.kind) {
            "rain", "drizzle", "showers", "thunder", "freezing" -> {
                val n = W * H / (if (s.kind == "drizzle") 90 else 55)
                for (i in 0 until n) {
                    val len = if (s.kind == "drizzle") 2 else 3 + (i % 2)
                    val y0 = ((rnd(i, 12) * H + frame * (if (s.kind == "drizzle") 3 else 6)) % H).toInt()
                    val x0 = (rnd(i, 13) * (W + 10)).toInt() - 5
                    for (k in 0 until len) {
                        val x = x0 - (y0 + k) / 7; val y = y0 + k
                        if (R.inside(x, y)) R[x, y] = Px.mix(R[x, y], if (s.kind == "freezing") 0xFFD8FAFF.toInt() else RAIN, if (k == 0) 0.3f else 0.55f)
                    }
                }
                // splashes on the ground
                for (i in 0 until W / 6) {
                    if (rnd(i, 14 + frame % 4) < 0.5f) continue
                    val x = (rnd(i, 15) * W).toInt(); val y = groundTop + 2 + (rnd(i, 16) * (H - groundTop - 3)).toInt()
                    R[x, y] = Px.mix(R[x, y], RAIN, 0.6f); R[x - 1, y - 1] = Px.mix(R[x - 1, y - 1], RAIN, 0.4f); R[x + 1, y - 1] = Px.mix(R[x + 1, y - 1], RAIN, 0.4f)
                }
            }
            "snow" -> for (i in 0 until W * H / 45) {
                val y = ((rnd(i, 12) * H + frame * (0.8 + rnd(i, 17) * 1.2)) % H).toInt()
                val x = (rnd(i, 13) * W + sin((frame + i * 7) * 0.25) * 1.5).toInt()
                R[x, y] = 0xFFFFFFFF.toInt()
                if (i % 5 == 0) { R[x + 1, y] = 0xFFE4EEFF.toInt(); R[x, y + 1] = 0xFFE4EEFF.toInt(); R[x + 1, y + 1] = 0xFFC8D8F4.toInt() }
            }
        }
        if (s.kind == "fog") {
            // drifting fog banks: soft bands that thicken toward the ground, dithered only at their edges
            val fogC = if (s.day) 0xFFD6DBE8.toInt() else 0xFF4A5070.toInt()
            for (y in hy / 3 until H) for (x in 0 until W) {
                val dens = 0.1 + 0.6 * (y - hy / 3.0) / (H - hy / 3.0) + 0.18 * sin((x + frame * 0.6) * 0.07 + y * 0.31)
                val band = (dens * 3).toInt().coerceIn(0, 3)
                val edge = dens * 3 - band
                val b2 = if (edge > 0.8 && Px.bayer(x, y) < (edge - 0.8) * 5) band + 1 else band
                if (b2 > 0) R[x, y] = Px.mix(R[x, y], fogC, minOf(0.75f, b2 * 0.22f))
            }
        }
        if (s.kind == "thunder" && frame % 23 < 2) {
            // lightning: flash the sky, then a jagged bolt from the clouds to the ground
            for (i in R.px.indices) R.px[i] = Px.mix(R.px[i], 0xFFFFFFFF.toInt(), 0.18f)
            var bx = (W * (0.12 + rnd(frame / 23, 18) * 0.4)).toInt(); var by = (hy * 0.35).toInt()
            var seg = 0
            while (by < groundTop) {
                R[bx, by] = 0xFFFFFDE0.toInt(); R[bx + 1, by] = 0xFFFFE36A.toInt()
                by++
                if (by % 3 == 0) bx += if (rnd(seg++, 19 + frame / 23) < 0.5f) -1 else 1
            }
        }
        return R.px
    }

    private fun cloud(R: Raster, cx: Double, cy: Double, cw: Double, ch: Double, ramp: IntArray, hy: Int) {
        // a cloud is a row of overlapping puffs with a flatter bottom
        val puffs = listOf(0.18 to 0.6, 0.38 to 0.95, 0.6 to 0.8, 0.8 to 0.55)
        val bottom = cy + ch
        val m = R.mask { x, y ->
            if (y + 0.5 > bottom || y > hy) return@mask false
            puffs.any { (fx, fr) ->
                val px = cx + cw * fx; val r = ch * fr
                val dx = (x + 0.5 - px) / (r * 1.25); val dy = (y + 0.5 - (bottom - r * 0.75)) / r
                dx * dx + dy * dy <= 1.0
            }
        }
        var top = R.h
        for (i in m.indices) if (m[i]) { top = minOf(top, i / R.w) }
        R.paint(m, { x, y ->
            val rim = y > 0 && !m[(y - 1) * R.w + x]
            if (rim) ramp[0] else Px.ramp(ramp, ((y + 0.5f - top) / (bottom - top).toFloat()).coerceIn(0f, 1f) * 0.95f + 0.05f, x, y, 0.3f)
        }) { c, side, _, _ ->
            // selective outline: soft tops (left as sky), darker undersides and sides
            when (side) { 0 -> null; 2 -> Px.shadow(c, 0.72f); else -> Px.mix(c, Px.shadow(c, 0.8f), 0.5f) }
        }
    }

    private fun pine(R: Raster, x0: Int, base: Int, h: Int, snowy: Boolean, land: (Int) -> Int) {
        // trunk
        R[x0, base] = land(WOOD[2]); R[x0, base - 1] = land(WOOD[2])
        val top = base - h
        for (y in top until base - 1) {
            val t = (y - top).toFloat() / (h - 1)
            // tiers: width grows down each tier, then steps back in
            val tier = ((y - top) % (h / 3 + 1)).toFloat() / (h / 3 + 1)
            val half = (t * h * 0.42 * (0.5 + 0.5 * tier) + 0.5).toInt()
            for (dx in -half..half) {
                val c = when {
                    snowy && tier < 0.3f && abs(dx) < half -> SNOWCAP[if (dx < 0) 0 else 1]
                    dx == -half -> PINE[0]
                    dx < 0 -> PINE[1]
                    dx == half -> PINE[3]
                    else -> PINE[2]
                }
                R[x0 + dx, y] = land(c)
            }
        }
        R[x0, top - 1] = land(if (snowy) SNOWCAP[0] else PINE[0])
    }

    private fun cabin(R: Raster, cx: Int, base: Int, w: Int, s: Spec, frame: Int, land: (Int) -> Int) {
        val h = (w * 0.55).toInt(); val left = cx - w / 2
        // walls (log planks)
        for (y in base - h until base) for (x in left until left + w) {
            val plank = (y - (base - h)) % 3 == 2
            R[x, y] = land(if (plank) WOOD[2] else if (x == left) WOOD[0] else WOOD[1])
        }
        // roof
        val roofTop = base - h - w / 2
        for (y in roofTop until base - h + 1) {
            val half = (y - roofTop) + 1
            for (x in cx - half - 1..cx + half + 1) {
                val edge = y == roofTop || x <= cx - half || x >= cx + half
                R[x, y] = land(if (s.cold && (y - roofTop) < 2) SNOWCAP[0] else if (edge) ROOF[2] else if (x < cx) ROOF[0] else ROOF[1])
            }
        }
        // door + window (lit at night and at dusk)
        val lit = !s.day || s.twilight > 0.4f
        val wx = left + 2; val wy = base - h + 2
        for (y in wy until wy + 3) for (x in wx until wx + 3) R[x, y] = if (lit) WINDOW_LIT else land(0xFF2E4A78.toInt())
        if (lit) { R[wx + 1, wy + 1] = 0xFFFFF4C8.toInt() }
        for (y in base - 4 until base) for (x in left + w - 4 until left + w - 2) R[x, y] = land(WOOD[2])
        // chimney with rising smoke
        val chx = cx + w / 4
        for (y in roofTop + 2 until roofTop + 6) { R[chx, y] = land(0xFF6B5A6A.toInt()); R[chx + 1, y] = land(0xFF4E4054.toInt()) }
        if (s.cold || !s.day) for (k in 0 until 5) {
            val y = roofTop + 1 - k * 2 - (frame / 2) % 2
            val x = chx + (sin((frame * 0.3) + k) * 1.2).toInt() + k / 2
            if (R.inside(x, y)) R[x, y] = Px.mix(R[x, y], 0xFFD0D4E2.toInt(), 0.6f - k * 0.1f)
        }
    }
}
