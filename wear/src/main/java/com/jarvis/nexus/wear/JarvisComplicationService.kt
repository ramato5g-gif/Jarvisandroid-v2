package com.jarvis.nexus.wear

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.MonochromaticImage
import androidx.wear.watchface.complications.data.MonochromaticImageComplicationData
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.data.SmallImage
import androidx.wear.watchface.complications.data.SmallImageComplicationData
import androidx.wear.watchface.complications.data.SmallImageType
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceService
import androidx.wear.watchface.complications.datasource.ComplicationRequest

/** Complication con l'icona JARVIS: mettila su qualsiasi quadrante, il tocco apre l'app. */
class JarvisComplicationService : ComplicationDataSourceService() {

    override fun onComplicationRequest(request: ComplicationRequest, listener: ComplicationRequestListener) {
        listener.onComplicationData(data(request.complicationType))
    }

    override fun getPreviewData(type: ComplicationType): ComplicationData? = data(type)

    private fun data(type: ComplicationType): ComplicationData? {
        val tap = PendingIntent.getActivity(this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val mono = MonochromaticImage.Builder(Icon.createWithResource(this, R.drawable.ic_jarvis)).build()
        val desc = PlainComplicationText.Builder("Apri JARVIS").build()
        return when (type) {
            ComplicationType.SHORT_TEXT -> ShortTextComplicationData.Builder(PlainComplicationText.Builder("JARVIS").build(), desc)
                .setMonochromaticImage(mono).setTapAction(tap).build()
            ComplicationType.MONOCHROMATIC_IMAGE -> MonochromaticImageComplicationData.Builder(mono, desc).setTapAction(tap).build()
            ComplicationType.SMALL_IMAGE -> SmallImageComplicationData.Builder(
                SmallImage.Builder(Icon.createWithResource(this, R.drawable.ic_launcher_foreground), SmallImageType.PHOTO).build(), desc)
                .setTapAction(tap).build()
            else -> null
        }
    }
}
