package com.jarvis.nexus.wear

import androidx.concurrent.futures.CallbackToFutureAdapter
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DimensionBuilders.degrees
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.DimensionBuilders.expand
import androidx.wear.protolayout.DimensionBuilders.sp
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.LayoutElementBuilders.Arc
import androidx.wear.protolayout.LayoutElementBuilders.ArcLine
import androidx.wear.protolayout.LayoutElementBuilders.ArcSpacer
import androidx.wear.protolayout.LayoutElementBuilders.Box
import androidx.wear.protolayout.LayoutElementBuilders.Column
import androidx.wear.protolayout.LayoutElementBuilders.FontStyle
import androidx.wear.protolayout.LayoutElementBuilders.Spacer
import androidx.wear.protolayout.LayoutElementBuilders.Text
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.google.common.util.concurrent.ListenableFuture

/** Tile JARVIS: anello ciano HUD, ultima risposta abbreviata; il tocco apre JARVIS già in ascolto. */
class JarvisTileService : TileService() {
    companion object {
        private const val RES = "1"
        private const val CYAN = 0xFF00E5FF.toInt()
        private const val CYAN_DIM = 0xFF00838F.toInt()
        private const val WHITE = 0xFFFFFFFF.toInt()
    }

    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> = done(
        TileBuilders.Tile.Builder()
            .setResourcesVersion(RES)
            .setTileTimeline(TimelineBuilders.Timeline.fromLayoutElement(layout()))
            .build()
    )

    override fun onTileResourcesRequest(requestParams: RequestBuilders.ResourcesRequest): ListenableFuture<ResourceBuilders.Resources> =
        done(ResourceBuilders.Resources.Builder().setVersion(RES).build())

    private fun <T> done(v: T): ListenableFuture<T> = CallbackToFutureAdapter.getFuture { it.set(v); "jarvis-tile" }

    private fun text(s: String, size: Float, color: Int, bold: Boolean = false, lines: Int = 1) = Text.Builder()
        .setText(s).setMaxLines(lines)
        .setMultilineAlignment(LayoutElementBuilders.TEXT_ALIGN_CENTER)
        .setOverflow(LayoutElementBuilders.TEXT_OVERFLOW_ELLIPSIZE_END)
        .setFontStyle(FontStyle.Builder().setSize(sp(size)).setColor(argb(color))
            .setWeight(if (bold) LayoutElementBuilders.FONT_WEIGHT_BOLD else LayoutElementBuilders.FONT_WEIGHT_NORMAL).build())
        .build()

    private fun layout(): LayoutElementBuilders.LayoutElement {
        val last = WatchPrefs.last(this).ifBlank { "Tocca e chiedi qualcosa." }
        val listen = ModifiersBuilders.Clickable.Builder().setId("ascolta").setOnClick(
            ActionBuilders.LaunchAction.Builder().setAndroidActivity(
                ActionBuilders.AndroidActivity.Builder()
                    .setPackageName(packageName)
                    .setClassName(MainActivity::class.java.name)
                    .addKeyToExtraMapping(MainActivity.EXTRA_AUTO_LISTEN, ActionBuilders.AndroidBooleanExtra.Builder().setValue(true).build())
                    .build()
            ).build()
        ).build()

        // Anello HUD: tre archi ciano separati da piccoli spazi
        val ring = Arc.Builder().setAnchorAngle(degrees(10f)).setAnchorType(LayoutElementBuilders.ARC_ANCHOR_START)
        repeat(3) {
            ring.addContent(ArcLine.Builder().setLength(degrees(100f)).setThickness(dp(4f)).setColor(argb(CYAN)).build())
            ring.addContent(ArcSpacer.Builder().setLength(degrees(20f)).setThickness(dp(4f)).build())
        }
        // Anello interno sottile (rientrato con padding)
        val inner = Box.Builder().setWidth(expand()).setHeight(expand())
            .setModifiers(ModifiersBuilders.Modifiers.Builder()
                .setPadding(ModifiersBuilders.Padding.Builder().setAll(dp(10f)).build()).build())
            .addContent(Arc.Builder().setAnchorAngle(degrees(70f)).setAnchorType(LayoutElementBuilders.ARC_ANCHOR_START)
                .addContent(ArcLine.Builder().setLength(degrees(220f)).setThickness(dp(2f)).setColor(argb(CYAN_DIM)).build())
                .build())
            .build()

        val col = Column.Builder().setWidth(dp(150f))
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .addContent(text("J.A.R.V.I.S.", 15f, CYAN, bold = true))
            .addContent(Spacer.Builder().setHeight(dp(6f)).build())
            .addContent(text(last.take(110), 12f, WHITE, lines = 3))
            .addContent(Spacer.Builder().setHeight(dp(8f)).build())
            .addContent(text("TOCCA PER PARLARE", 10f, CYAN_DIM))
            .build()

        return Box.Builder().setWidth(expand()).setHeight(expand())
            .setModifiers(ModifiersBuilders.Modifiers.Builder().setClickable(listen).build())
            .addContent(ring.build())
            .addContent(inner)
            .addContent(col)
            .build()
    }
}
