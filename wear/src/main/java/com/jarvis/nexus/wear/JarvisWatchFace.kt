package com.jarvis.nexus.wear

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import android.graphics.Typeface
import android.view.SurfaceHolder
import androidx.wear.watchface.CanvasComplicationFactory
import androidx.wear.watchface.CanvasType
import androidx.wear.watchface.ComplicationSlot
import androidx.wear.watchface.ComplicationSlotsManager
import androidx.wear.watchface.DrawMode
import androidx.wear.watchface.Renderer
import androidx.wear.watchface.TapEvent
import androidx.wear.watchface.TapType
import androidx.wear.watchface.WatchFace
import androidx.wear.watchface.WatchFaceService
import androidx.wear.watchface.WatchFaceType
import androidx.wear.watchface.WatchState
import androidx.wear.watchface.complications.ComplicationSlotBounds
import androidx.wear.watchface.complications.DefaultComplicationDataSourcePolicy
import androidx.wear.watchface.complications.SystemDataSources
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.RangedValueComplicationData
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.rendering.CanvasComplicationDrawable
import androidx.wear.watchface.complications.rendering.ComplicationDrawable
import androidx.wear.watchface.style.CurrentUserStyleRepository
import java.time.Instant
import java.time.ZonedDateTime
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

private const val CYAN = 0xFF00E5FF.toInt()
private const val ICE = 0xFFE8FBFF.toInt()
private const val TEAL = 0xFF5AA9B5.toInt()
private const val TEAL_M = 0xFF3D8A96.toInt()
private const val TRACK = 0xFF0A252C.toInt()
private const val TICK = 0xFF12323A.toInt()
private const val RING = 0xFF0F3A43.toInt()
private const val GREEN = 0xFF00E676.toInt()
private const val RED = 0xFFFF3B4E.toInt()
private const val SLOT_LEFT = 100
private const val SLOT_RIGHT = 101

/**
 * Quadrante "JARVIS Visore": ghiera con scala e numeri orientati, scia radar dei secondi, ora Roboto Thin sfumata,
 * due indicatori curvi alimentati da complication (default batteria e passi). Tocco sull'ora = JARVIS in ascolto.
 */
class JarvisWatchFace : WatchFaceService() {

    override fun createComplicationSlotsManager(currentUserStyleRepository: CurrentUserStyleRepository): ComplicationSlotsManager {
        // Il drawable serve solo per l'evidenziazione nell'editor: gli indicatori li disegna il renderer
        val factory = CanvasComplicationFactory { ws, inv -> CanvasComplicationDrawable(ComplicationDrawable(this), ws, inv) }
        val types = listOf(ComplicationType.RANGED_VALUE, ComplicationType.SHORT_TEXT)
        fun slot(id: Int, b: RectF, source: Int, type: ComplicationType) =
            ComplicationSlot.createRoundRectComplicationSlotBuilder(id, factory, types,
                DefaultComplicationDataSourcePolicy(source, type), ComplicationSlotBounds(b)).build()
        return ComplicationSlotsManager(listOf(
            slot(SLOT_LEFT, RectF(0.10f, 0.37f, 0.34f, 0.63f), SystemDataSources.DATA_SOURCE_WATCH_BATTERY, ComplicationType.RANGED_VALUE),
            slot(SLOT_RIGHT, RectF(0.66f, 0.37f, 0.90f, 0.63f), SystemDataSources.DATA_SOURCE_STEP_COUNT, ComplicationType.SHORT_TEXT)
        ), currentUserStyleRepository)
    }

    override suspend fun createWatchFace(
        surfaceHolder: SurfaceHolder, watchState: WatchState,
        complicationSlotsManager: ComplicationSlotsManager, currentUserStyleRepository: CurrentUserStyleRepository
    ): WatchFace {
        val renderer = VisoreRenderer(this, surfaceHolder, watchState, complicationSlotsManager, currentUserStyleRepository)
        return WatchFace(WatchFaceType.DIGITAL, renderer).setTapListener(object : WatchFace.TapListener {
            override fun onTapEvent(tapType: Int, tapEvent: TapEvent, complicationSlot: ComplicationSlot?) {
                if (tapType != TapType.UP || complicationSlot != null) return
                val b = renderer.screenBounds
                // Solo la zona dell'ora: evita aperture accidentali sfiorando la ghiera
                if (hypot((tapEvent.xPos - b.exactCenterX()).toDouble(), (tapEvent.yPos - b.exactCenterY()).toDouble()) > b.width() * 0.25) return
                try {
                    startActivity(Intent(this@JarvisWatchFace, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra(MainActivity.EXTRA_AUTO_LISTEN, true))
                } catch (_: Exception) { }
            }
        })
    }
}

private class VisoreRenderer(
    private val ctx: Context, holder: SurfaceHolder, watchState: WatchState,
    private val slots: ComplicationSlotsManager, repo: CurrentUserStyleRepository
) : Renderer.CanvasRenderer2<VisoreRenderer.Assets>(holder, repo, watchState, CanvasType.HARDWARE, 33L, false) {

    class Assets : Renderer.SharedAssets { override fun onDestroy() {} }

    // Roboto incluso nell'APK: sui Galaxy il font di sistema non è Roboto
    private fun font(file: String, wght: Int, wdth: Int = 100): Typeface = try {
        Typeface.Builder(ctx.assets, "fonts/$file").setFontVariationSettings(
            if (file == "Roboto.ttf") "'wght' $wght, 'wdth' $wdth" else "'wght' $wght").build()
    } catch (_: Exception) { Typeface.DEFAULT }
    // Ora in Roboto Thin un po' stretto: lascia spazio agli indicatori ai lati
    private val thin = font("Roboto.ttf", 100, 75)
    private val light = font("Roboto.ttf", 300)
    private val condLight = font("RobotoCondensed.ttf", 300)
    private val cond = font("RobotoCondensed.ttf", 400)

    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val txt = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val oval = RectF()
    private val tri = Path()
    private val sweepM = Matrix()
    private val giorni = arrayOf("LUN", "MAR", "MER", "GIO", "VEN", "SAB", "DOM")
    private val mesi = arrayOf("GEN", "FEB", "MAR", "APR", "MAG", "GIU", "LUG", "AGO", "SET", "OTT", "NOV", "DIC")

    // Geometria del frame corrente (disegno pensato su 450 px, scalato con k)
    private var k = 1f; private var cx = 0f; private var cy = 0f; private var r = 0f; private var amb = false

    override suspend fun createSharedAssets() = Assets()

    private fun cyanA(a: Float) = Color.argb((a.coerceIn(0f, 1f) * 255).toInt(), 0, 229, 255)
    private fun px(d: Float, deg: Float) = cx + d * cos(Math.toRadians(deg - 90.0)).toFloat()
    private fun py(d: Float, deg: Float) = cy + d * sin(Math.toRadians(deg - 90.0)).toFloat()
    private fun glow(p: Paint, color: Int, blur: Float) {
        if (amb || blur <= 0f) p.clearShadowLayer() else p.setShadowLayer(blur * 0.85f * k, 0f, 0f, color)
    }

    private fun arc(c: Canvas, rad: Float, from: Float, sweep: Float, color: Int, w: Float, blur: Float = 0f) {
        oval.set(cx - rad, cy - rad, cx + rad, cy + rad)
        line.color = color; line.strokeWidth = w * k; line.strokeCap = Paint.Cap.ROUND
        glow(line, CYAN, blur)
        c.drawArc(oval, from - 90f, sweep, false, line)
        line.clearShadowLayer()
    }

    private fun dot(c: Canvas, x: Float, y: Float, rad: Float, color: Int, glowColor: Int, blur: Float) {
        fill.shader = null; fill.color = color; glow(fill, glowColor, blur)
        c.drawCircle(x, y, rad * k, fill); fill.clearShadowLayer()
    }

    private fun text(c: Canvas, s: String, x: Float, y: Float, tf: Typeface, size: Float, color: Int, spacing: Float = 0f): Float {
        txt.typeface = tf; txt.textSize = size * k; txt.shader = null; txt.color = color
        txt.letterSpacing = if (spacing > 0f) spacing / size else 0f
        txt.clearShadowLayer()
        c.drawText(s, x, y, txt)
        return txt.measureText(s)
    }

    /** Valore (0..1) e testo di una complication: valore a intervallo (batteria) o testo breve (passi). */
    private fun reading(id: Int): Triple<Float, String, String?>? {
        val slot = slots.complicationSlots[id] ?: return null
        if (!slot.enabled) return null
        val now = Instant.now()
        return when (val d = slot.complicationData.value) {
            is RangedValueComplicationData -> {
                val span = (d.max - d.min).takeIf { it > 0f } ?: return null
                val t = d.text?.getTextAt(ctx.resources, now)?.toString()?.takeIf { it.isNotBlank() }
                    ?: (d.value.toInt().toString() + if (d.max == 100f) "%" else "")
                Triple(((d.value - d.min) / span).coerceIn(0f, 1f), t, d.title?.getTextAt(ctx.resources, now)?.toString())
            }
            is ShortTextComplicationData -> {
                val t = d.text.getTextAt(ctx.resources, now).toString()
                val n = t.filter { it.isDigit() }.toIntOrNull()
                Triple(if (n != null) (n / 10_000f).coerceIn(0f, 1f) else 1f, t, d.title?.getTextAt(ctx.resources, now)?.toString())
            }
            else -> null
        }
    }

    private fun gauge(c: Canvas, from: Float, sweep: Float, id: Int, label: String, reverse: Boolean) {
        val (v, value, title) = reading(id) ?: return
        val rad = r * 0.62f
        arc(c, rad, from, sweep, TRACK, 3f)
        val s0 = if (reverse) from + sweep * (1 - v) else from
        val sw = sweep * v
        if (sw > 0.5f) arc(c, rad, s0, sw, CYAN, 3f, blur = 8f)
        val end = if (reverse) s0 else s0 + sw
        dot(c, px(rad, end), py(rad, end), 3.5f, ICE, CYAN, 10f)
        val lx = px(rad - 26 * k, from + sweep / 2); val ly = py(rad - 26 * k, from + sweep / 2)
        text(c, value.take(6), lx, ly + 3 * k, light, 17f, ICE)
        text(c, (title ?: label).uppercase().take(10), lx, ly + 17 * k, cond, 8f, TEAL, 2.5f)
    }

    override fun render(canvas: Canvas, bounds: Rect, zonedDateTime: ZonedDateTime, sharedAssets: Assets) {
        amb = renderParameters.drawMode == DrawMode.AMBIENT
        k = bounds.width() / 450f; cx = bounds.exactCenterX(); cy = bounds.exactCenterY(); r = bounds.width() / 2f
        val s = zonedDateTime.second + zonedDateTime.nano / 1e9f
        val secDeg = s * 6f
        canvas.drawColor(Color.BLACK)

        if (!amb) {
            // Fondo e fascia della ghiera
            fill.clearShadowLayer()
            fill.shader = RadialGradient(cx, cy, r, intArrayOf(0xFF03090B.toInt(), Color.BLACK, 0xFF031015.toInt(), 0xFF06222A.toInt()),
                floatArrayOf(0f, 0.74f, 0.75f, 1f), Shader.TileMode.CLAMP)
            canvas.drawCircle(cx, cy, r, fill)
            // Scia radar dietro ai secondi
            val sg = SweepGradient(cx, cy, intArrayOf(cyanA(0f), cyanA(0f), cyanA(0.16f)), floatArrayOf(0f, 0.8f, 1f))
            sweepM.setRotate(secDeg - 90f, cx, cy); sg.setLocalMatrix(sweepM)
            fill.shader = sg
            canvas.drawCircle(cx, cy, r * 0.745f, fill)
            fill.shader = null
        }

        // Anello interno con tacche cardinali
        arc(canvas, r * 0.75f, 0f, 360f, if (amb) 0xFF222222.toInt() else RING, 1f)
        if (!amb) for (i in 0 until 4) {
            line.color = TEAL_M; line.strokeWidth = 2 * k; line.strokeCap = Paint.Cap.BUTT
            canvas.drawLine(px(r * 0.72f, i * 90f), py(r * 0.72f, i * 90f), px(r * 0.75f, i * 90f), py(r * 0.75f, i * 90f), line)
        }

        // Tacche con scia luminosa dei secondi
        line.strokeCap = Paint.Cap.BUTT
        for (i in 0 until 60) {
            val big = i % 5 == 0
            if (amb && !big) continue
            var color = if (amb) 0xFF4A4A4A.toInt() else if (big) TEAL_M else TICK
            var blur = 0f
            if (!amb) {
                val d = (s - i + 60f) % 60f
                if (d < 14f) { color = cyanA(1f - d / 14f * 0.85f); if (d < 3f) blur = 8f }
            }
            line.color = color; line.strokeWidth = (if (big) 2.6f else 1.3f) * k
            glow(line, CYAN, blur)
            val a = i * 6f
            val r1 = r * if (big) 0.895f else 0.93f
            canvas.drawLine(px(r1, a), py(r1, a), px(r * 0.97f, a), py(r * 0.97f, a), line)
        }
        line.clearShadowLayer()

        // Numeri della ghiera orientati lungo il cerchio, triangolo a ore 12
        for (i in 1 until 12) {
            val deg = i * 30f
            val bottom = deg > 90f && deg < 270f
            canvas.save()
            canvas.rotate(deg + if (bottom) 180f else 0f, cx, cy)
            val y = if (bottom) cy + r * 0.835f + 4 * k else cy - r * 0.835f + 5 * k
            text(canvas, "%02d".format(i * 5), cx, y, condLight, 14f, if (amb) 0xFF3A3A3A.toInt() else TEAL, 1f)
            canvas.restore()
        }
        tri.reset()
        tri.moveTo(cx - 7 * k, cy - r * 0.865f); tri.lineTo(cx + 7 * k, cy - r * 0.865f); tri.lineTo(cx, cy - r * 0.80f); tri.close()
        fill.shader = null; fill.color = if (amb) 0xFF555555.toInt() else CYAN; glow(fill, CYAN, 8f)
        canvas.drawPath(tri, fill); fill.clearShadowLayer()

        if (!amb) {
            dot(canvas, px(r * 0.95f, secDeg), py(r * 0.95f, secDeg), 4.5f, ICE, CYAN, 18f)
            gauge(canvas, 222f, 96f, SLOT_LEFT, "BATTERIA", false)
            gauge(canvas, 42f, 96f, SLOT_RIGHT, "PASSI", true)
        }

        // Intestazione + stato collegamento col telefono
        val lw = text(canvas, "J.A.R.V.I.S.", cx, cy - 78 * k, condLight, 13f, if (amb) 0xFF555555.toInt() else 0xFF7FDCEA.toInt(), 6f)
        if (!amb) WatchPrefs.link(ctx)?.let { ok ->
            dot(canvas, cx + lw / 2 + 10 * k, cy - 82.5f * k, 3f, if (ok) GREEN else RED, if (ok) GREEN else RED, 8f)
        }

        // Ora: Roboto Thin sfumata ghiaccio → ciano con bagliore
        txt.typeface = thin; txt.textSize = 100 * k; txt.letterSpacing = 0f
        if (amb) { txt.shader = null; txt.color = 0xFFD0D0D0.toInt(); txt.clearShadowLayer() }
        else {
            txt.color = Color.WHITE
            txt.shader = LinearGradient(0f, cy - 50 * k, 0f, cy + 40 * k, ICE, 0xFF5FE9FF.toInt(), Shader.TileMode.CLAMP)
            txt.setShadowLayer(22 * 0.85f * k, 0f, 0f, cyanA(0.6f))
        }
        canvas.drawText("%02d:%02d".format(zonedDateTime.hour, zonedDateTime.minute), cx, cy + 36 * k, txt)
        txt.shader = null; txt.clearShadowLayer()

        // Data tra due filetti
        val date = giorni[zonedDateTime.dayOfWeek.value - 1] + " " + zonedDateTime.dayOfMonth + " " + mesi[zonedDateTime.monthValue - 1]
        val dw = text(canvas, date, cx, cy + 70 * k, cond, 13f, if (amb) 0xFF555555.toInt() else CYAN, 5f)
        if (!amb) {
            line.color = cyanA(0.45f); line.strokeWidth = 1 * k; line.strokeCap = Paint.Cap.BUTT
            val y = cy + 65.5f * k
            canvas.drawLine(cx - dw / 2 - 34 * k, y, cx - dw / 2 - 10 * k, y, line)
            canvas.drawLine(cx + dw / 2 + 10 * k, y, cx + dw / 2 + 34 * k, y, line)
        }
    }

    override fun renderHighlightLayer(canvas: Canvas, bounds: Rect, zonedDateTime: ZonedDateTime, sharedAssets: Assets) {
        renderParameters.highlightLayer?.let { canvas.drawColor(it.backgroundTint) }
        slots.complicationSlots.values.forEach { if (it.enabled) it.renderHighlightLayer(canvas, zonedDateTime, renderParameters) }
    }
}
