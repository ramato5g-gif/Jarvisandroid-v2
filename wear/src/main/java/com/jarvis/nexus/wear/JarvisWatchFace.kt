package com.jarvis.nexus.wear

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
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
import androidx.wear.watchface.complications.rendering.CanvasComplicationDrawable
import androidx.wear.watchface.complications.rendering.ComplicationDrawable
import androidx.wear.watchface.style.CurrentUserStyleRepository
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

private const val CYAN = 0xFF00E5FF.toInt()
private const val CYAN_DIM = 0xFF00838F.toInt()
private const val AMBER = 0xFFFFB300.toInt()

/**
 * Quadrante JARVIS HUD: ora digitale, data, archi rotanti, secondi, ultima risposta, stato del telefono
 * e due complication (batteria e passi di default). Tocco al centro = JARVIS in ascolto.
 */
class JarvisWatchFace : WatchFaceService() {

    override fun createComplicationSlotsManager(currentUserStyleRepository: CurrentUserStyleRepository): ComplicationSlotsManager {
        val factory = CanvasComplicationFactory { ws, inv ->
            CanvasComplicationDrawable(ComplicationDrawable(this).apply {
                activeStyle.apply {
                    textColor = Color.WHITE; titleColor = Color.LTGRAY; iconColor = CYAN
                    borderColor = CYAN_DIM; rangedValuePrimaryColor = CYAN; highlightColor = CYAN
                }
                ambientStyle.apply {
                    textColor = Color.GRAY; titleColor = Color.GRAY; iconColor = Color.GRAY
                    borderColor = Color.DKGRAY; rangedValuePrimaryColor = Color.GRAY
                }
            }, ws, inv)
        }
        val types = listOf(ComplicationType.RANGED_VALUE, ComplicationType.SHORT_TEXT,
            ComplicationType.MONOCHROMATIC_IMAGE, ComplicationType.SMALL_IMAGE)
        fun slot(id: Int, left: Float, source: Int, type: ComplicationType) =
            ComplicationSlot.createRoundRectComplicationSlotBuilder(id, factory, types,
                DefaultComplicationDataSourcePolicy(source, type),
                ComplicationSlotBounds(RectF(left, 0.65f, left + 0.2f, 0.85f))).build()
        return ComplicationSlotsManager(listOf(
            slot(100, 0.25f, SystemDataSources.DATA_SOURCE_WATCH_BATTERY, ComplicationType.RANGED_VALUE),
            slot(101, 0.55f, SystemDataSources.DATA_SOURCE_STEP_COUNT, ComplicationType.SHORT_TEXT)
        ), currentUserStyleRepository)
    }

    override suspend fun createWatchFace(
        surfaceHolder: SurfaceHolder, watchState: WatchState,
        complicationSlotsManager: ComplicationSlotsManager, currentUserStyleRepository: CurrentUserStyleRepository
    ): WatchFace {
        val renderer = HudRenderer(this, surfaceHolder, watchState, complicationSlotsManager, currentUserStyleRepository)
        return WatchFace(WatchFaceType.DIGITAL, renderer).setTapListener(object : WatchFace.TapListener {
            override fun onTapEvent(tapType: Int, tapEvent: TapEvent, complicationSlot: ComplicationSlot?) {
                if (tapType != TapType.UP || complicationSlot != null) return
                val b = renderer.screenBounds
                // Solo il centro (ora): evita aperture accidentali sfiorando il bordo
                if (hypot((tapEvent.xPos - b.exactCenterX()).toDouble(), (tapEvent.yPos - b.exactCenterY()).toDouble()) > b.width() * 0.3) return
                try {
                    startActivity(Intent(this@JarvisWatchFace, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra(MainActivity.EXTRA_AUTO_LISTEN, true))
                } catch (_: Exception) { }
            }
        })
    }
}

private class HudRenderer(
    private val ctx: Context, holder: SurfaceHolder, watchState: WatchState,
    private val slots: ComplicationSlotsManager, repo: CurrentUserStyleRepository
) : Renderer.CanvasRenderer2<HudRenderer.Assets>(holder, repo, watchState, CanvasType.HARDWARE, 1000L, false) {

    class Assets : Renderer.SharedAssets { override fun onDestroy() {} }

    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val txt = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD); textAlign = Paint.Align.CENTER
    }
    private val dateFmt = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ITALIAN)
    private val timeFmt = DateTimeFormatter.ofPattern("HH:mm")
    private val oval = RectF()

    override suspend fun createSharedAssets() = Assets()

    private fun arc(cx: Float, cy: Float, r: Float, start: Float, sweep: Float, color: Int, width: Float, canvas: Canvas) {
        oval.set(cx - r, cy - r, cx + r, cy + r)
        line.color = color; line.strokeWidth = width
        canvas.drawArc(oval, start, sweep, false, line)
    }

    private fun text(canvas: Canvas, s: String, x: Float, y: Float, size: Float, color: Int, outline: Boolean = false) {
        txt.textSize = size; txt.color = color
        txt.style = if (outline) Paint.Style.STROKE else Paint.Style.FILL
        txt.strokeWidth = size / 30f
        canvas.drawText(s, x, y, txt)
    }

    override fun render(canvas: Canvas, bounds: Rect, zonedDateTime: ZonedDateTime, sharedAssets: Assets) {
        val amb = renderParameters.drawMode == DrawMode.AMBIENT
        val w = bounds.width().toFloat()
        val cx = bounds.exactCenterX(); val cy = bounds.exactCenterY(); val r = w / 2f
        val main = if (amb) Color.GRAY else CYAN
        canvas.drawColor(Color.BLACK)

        // Tacche dei minuti (in ambient solo le ore)
        for (i in 0 until 60) {
            val big = i % 5 == 0
            if (amb && !big) continue
            val a = Math.toRadians(i * 6.0 - 90)
            val r1 = r * if (big) 0.86f else 0.91f
            line.color = if (big) main else CYAN_DIM; line.strokeWidth = w * if (big) 0.012f else 0.006f
            canvas.drawLine(cx + r1 * cos(a).toFloat(), cy + r1 * sin(a).toFloat(),
                cx + r * 0.95f * cos(a).toFloat(), cy + r * 0.95f * sin(a).toFloat(), line)
        }

        val sec = zonedDateTime.second
        if (!amb) {
            // Secondi come arco di avanzamento + archi HUD rotanti
            arc(cx, cy, r * 0.80f, -90f, sec * 6f + 0.1f, CYAN, w * 0.012f, canvas)
            listOf(0f, 120f, 240f).forEach { arc(cx, cy, r * 0.73f, sec * 2f + it, 70f, CYAN_DIM, w * 0.008f, canvas) }
            listOf(60f, 240f).forEach { arc(cx, cy, r * 0.67f, -sec * 3f + it, 90f, CYAN_DIM, w * 0.005f, canvas) }
        }

        text(canvas, "J.A.R.V.I.S.", cx, cy - r * 0.46f, w * 0.055f, main)
        if (!amb) {
            WatchPrefs.link(ctx)?.let { ok ->
                fill.color = if (ok) Color.rgb(0, 230, 118) else Color.rgb(255, 59, 78)
                canvas.drawCircle(cx, cy - r * 0.38f, w * 0.012f, fill)
            }
            val last = WatchPrefs.last(ctx)
            if (last.isNotBlank()) {
                text(canvas, if (last.length > 22) last.take(21) + "…" else last, cx, cy - r * 0.25f, w * 0.042f, Color.LTGRAY)
            }
        }

        text(canvas, timeFmt.format(zonedDateTime), cx, cy + r * 0.12f, w * 0.2f, if (amb) Color.WHITE else CYAN, outline = amb)
        text(canvas, dateFmt.format(zonedDateTime).uppercase(Locale.ITALIAN).replace(".", ""), cx, cy + r * 0.26f,
            w * 0.05f, if (amb) Color.GRAY else AMBER)

        slots.complicationSlots.values.forEach { if (it.enabled) it.render(canvas, zonedDateTime, renderParameters) }
    }

    override fun renderHighlightLayer(canvas: Canvas, bounds: Rect, zonedDateTime: ZonedDateTime, sharedAssets: Assets) {
        renderParameters.highlightLayer?.let { canvas.drawColor(it.backgroundTint) }
        slots.complicationSlots.values.forEach { if (it.enabled) it.renderHighlightLayer(canvas, zonedDateTime, renderParameters) }
    }
}
