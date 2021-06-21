package com.example.musicplayer.helper

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.example.musicplayer.R
import java.util.concurrent.TimeUnit

object Constants {
    const val CHANNEL_ID = "music"
    const val MUSIC_NOTIFICATION_ID = 1
    const val ACTION_PAUSE = 1
    const val ACTION_RESUME = 2
    const val ACTION_CLEAR = 3
    const val ACTION_START = 4
    const val ACTION_NEXT = 5
    const val ACTION_PREVIOUS = 6
    const val ACTION_STOP = 7
    const val ACTION_FROM_ACTIVITY = "ACTION_FROM_ACTIVITY"
    const val SEND_ACTION_FROM_NOTIFICATION = "SEND_ACTION_FROM_NOTIFICATION"
    const val SEND_POSITION = "SEND_POSITION_TO_FRAGMENT"
    const val REPEAT_OFF = 0
    const val REPEAT_ONE = 1
    const val REPEAT_ALL = 2
    const val READ_STORAGE_REQUEST_CODE = 1
    fun durationConverter(duration: Long): String {
        return String.format(
            "%02d:%02d",
            TimeUnit.MILLISECONDS.toMinutes(duration),
            TimeUnit.MILLISECONDS.toSeconds(duration) -
                    TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(duration))
        )
    }

    fun getAlbumBitmap(context: Context, audioUri: Uri): Bitmap {
        val mmr = MediaMetadataRetriever()
        val art: Bitmap
        val bfo = BitmapFactory.Options()
        mmr.setDataSource(context, audioUri)
        val rawArt: ByteArray? = mmr.embeddedPicture
        return if (null != rawArt) {
            art = BitmapFactory.decodeByteArray(rawArt, 0, rawArt.size, bfo)
            art
        } else {
            BitmapFactory.decodeResource(context.resources, R.drawable.img_beat)
        }
    }
}

