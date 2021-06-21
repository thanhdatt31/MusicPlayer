package com.example.musicplayer.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.*
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.provider.MediaStore
import android.support.v4.media.session.MediaSessionCompat
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.example.musicplayer.MainActivity
import com.example.musicplayer.R
import com.example.musicplayer.helper.Constants
import com.example.musicplayer.helper.Constants.ACTION_CLEAR
import com.example.musicplayer.helper.Constants.ACTION_FROM_ACTIVITY
import com.example.musicplayer.helper.Constants.ACTION_NEXT
import com.example.musicplayer.helper.Constants.ACTION_PAUSE
import com.example.musicplayer.helper.Constants.ACTION_PREVIOUS
import com.example.musicplayer.helper.Constants.ACTION_RESUME
import com.example.musicplayer.helper.Constants.ACTION_START
import com.example.musicplayer.helper.Constants.ACTION_STOP
import com.example.musicplayer.helper.Constants.CHANNEL_ID
import com.example.musicplayer.helper.Constants.MUSIC_NOTIFICATION_ID
import com.example.musicplayer.helper.Constants.REPEAT_ALL
import com.example.musicplayer.helper.Constants.REPEAT_OFF
import com.example.musicplayer.helper.Constants.REPEAT_ONE
import com.example.musicplayer.helper.Constants.SEND_ACTION_FROM_NOTIFICATION
import com.example.musicplayer.helper.Constants.SEND_POSITION
import com.example.musicplayer.model.Audio
import kotlinx.coroutines.*

class AudioService : Service(), MediaPlayer.OnCompletionListener {
    private var audioList: ArrayList<Audio> = arrayListOf()
    private var musicPlayer: MediaPlayer = MediaPlayer()
    private lateinit var musicTitle: String
    private var audio: Audio? = null
    private lateinit var mAudio: Audio
    private lateinit var job: Job
    private var audioPosition = 0
    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        getAudioFile()
        val bundle: Bundle? = intent?.extras
        if (bundle != null) {
            audio = bundle.getParcelable("audio")
            audioPosition = bundle.getInt("pos")
            if (audio != null) {
                mAudio = audio as Audio
            }
            if (musicPlayer.isPlaying) {
                musicPlayer.pause()
            }
            if (this::job.isInitialized) {
                job.cancel()
                Log.d("datnt", "cancel")
            }
            musicTitle = bundle.getString("title").toString()
            initMusic(audioPosition)
            audio?.let { showNotification(audioPosition) }

        }

        val actionMusic = intent?.getIntExtra("action_music_service", 0)
        if (actionMusic != null) {
            handleActionMusic(actionMusic)
        }
        registerReceiver(broadcastReceiver, IntentFilter(SEND_ACTION_FROM_NOTIFICATION))
        registerReceiver(broadcastReceiver, IntentFilter(ACTION_FROM_ACTIVITY))
        registerReceiver(broadcastReceiver, IntentFilter("progress_from_activity"))
        return START_NOT_STICKY
    }

    private fun getAudioFile() {
        val collection =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Audio.Media.getContentUri(
                    MediaStore.VOLUME_EXTERNAL
                )
            } else {
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            }

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.SIZE
        )
        contentResolver.query(
            collection,
            projection,
            null,
            null,
            null
        )?.use { cursor ->
            audioList.clear()
            while (cursor.moveToNext()) {
                val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID))
                val name =
                    cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE))
                val artist =
                    cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST))
                val duration =
                    cursor.getInt(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION))
                val contentUri: Uri = ContentUris.withAppendedId(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    id
                )
                val audio = Audio(contentUri, name, artist, duration)
                audioList.add(audio)
            }

        }
    }

    private fun initMusic(position: Int) {
        musicPlayer = MediaPlayer.create(this, audioList[position].uri)
        musicPlayer.setOnCompletionListener(this)
        musicPlayer.start()
        saveIsPlaying(ACTION_RESUME)
        sendDataToActivity(ACTION_START)
    }


    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    private fun handleActionMusic(action: Int) {
        when (action) {
            ACTION_PAUSE ->
                pauseMusic()
            ACTION_RESUME ->
                resumeMusic()
            ACTION_NEXT -> nextMusic()
            ACTION_PREVIOUS -> previousMusic()
            ACTION_CLEAR -> {
                stopSelf()
                sendDataToActivity(ACTION_CLEAR)
                if (this::job.isInitialized && job.isActive) {
                    job.cancel()
                }
                saveIsPlaying(ACTION_CLEAR)
            }
        }
    }

    private fun resumeMusic() {
        musicPlayer.start()
        showNotification(audioPosition)
        sendDataToActivity(ACTION_RESUME)
        saveIsPlaying(ACTION_RESUME)
    }

    private fun pauseMusic() {
        musicPlayer.pause()
        showNotification(audioPosition)
        sendDataToActivity(ACTION_PAUSE)
        saveIsPlaying(ACTION_PAUSE)
//        sendDataToFragment(ACTION_PAUSE)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "My Service",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            serviceChannel.setSound(null, null)
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    private fun showNotification(position: Int) {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent =
            PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)
        val mediaSessionCompat = MediaSessionCompat(this, "tag")
        val notificationBuilder: NotificationCompat.Builder =
            NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(audioList[position].name)
                .setContentText(audioList[position].artist)
                .setLargeIcon(getAlbumImage(audioList[position].uri))
                .setStyle(
                    androidx.media.app.NotificationCompat.MediaStyle()
                        .setMediaSession(mediaSessionCompat.sessionToken)
                )
                .setContentIntent(pendingIntent)
                .addAction(
                    R.drawable.ic_baseline_arrow_left_24, "Back", getPendingIntent(
                        this,
                        ACTION_PREVIOUS
                    )
                )
        if (musicPlayer.isPlaying) {
            notificationBuilder
                .addAction(
                    R.drawable.ic_baseline_pause_24_black, "Pause", getPendingIntent(
                        this,
                        ACTION_PAUSE
                    )
                )
        } else {
            notificationBuilder
                .addAction(
                    R.drawable.ic_baseline_play_arrow_24_black, "Resume", getPendingIntent(
                        this,
                        ACTION_RESUME
                    )
                )
        }
            .addAction(
                R.drawable.ic_baseline_arrow_right_24, "Next", getPendingIntent(
                    this,
                    ACTION_NEXT
                )
            )
            .addAction(
                R.drawable.ic_baseline_cancel_24, "Cancel", getPendingIntent(
                    this,
                    ACTION_CLEAR
                )
            )

        val notification = notificationBuilder.build()
        startForeground(MUSIC_NOTIFICATION_ID, notification)
    }

    private fun getPendingIntent(context: Context, action: Int): PendingIntent {
        val intent = Intent(SEND_ACTION_FROM_NOTIFICATION)
        intent.putExtra("action_music", action)
        return PendingIntent.getBroadcast(
            context.applicationContext,
            action,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun getAlbumImage(audio: Uri): Bitmap? {
        val mmr = MediaMetadataRetriever()
        val bfo = BitmapFactory.Options()
        mmr.setDataSource(this, audio)
        val rawArt: ByteArray? = mmr.embeddedPicture
        return rawArt?.let {
            BitmapFactory.decodeByteArray(rawArt, 0, it.size, bfo)
        }
    }

    override fun onDestroy() {
        saveIsPlaying(ACTION_CLEAR)
        unregisterReceiver(broadcastReceiver)
        if (this::job.isInitialized && job.isActive) {
            job.cancel()
        }
        musicPlayer.release()
        super.onDestroy()
    }

    private fun sendDataToActivity(action: Int) {
        val intent = Intent("send_data_to_activity")
        val bundle = Bundle()
        if (audioPosition > audioList.size - 1) {
            bundle.putParcelable("audio", audioList[audioList.size - 1])
        } else {
            bundle.putParcelable("audio", audioList[audioPosition])
        }
        bundle.putBoolean("status_player", musicPlayer.isPlaying)
        bundle.putInt("action", action)
        intent.putExtras(bundle)
        if (this::job.isInitialized) {
            job.cancel()
        }
        if (action == ACTION_CLEAR) {
            LocalBroadcastManager.getInstance(this@AudioService).sendBroadcast(intent)
        } else {
            LocalBroadcastManager.getInstance(this@AudioService).sendBroadcast(intent)
            sendPosition()
        }

    }

    private fun sendPosition() {
        job = GlobalScope.launch(Dispatchers.IO) {
            repeat(Int.MAX_VALUE) {
                delay(50)
                val intent = Intent(SEND_POSITION)
                val bundle = Bundle()
                bundle.putInt("current_position", musicPlayer.currentPosition)
                intent.putExtras(bundle)
                LocalBroadcastManager.getInstance(this@AudioService).sendBroadcast(intent)
            }
        }
    }

    private val broadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent != null) {
                when (intent.action) {
                    SEND_ACTION_FROM_NOTIFICATION -> {
                        val actionMusic: Int = intent.getIntExtra("action_music", 0)
                        handleActionMusic(actionMusic)
                    }
                    ACTION_FROM_ACTIVITY -> {
                        when (intent.getIntExtra("action", 0)) {
                            ACTION_PAUSE -> pauseMusic()
                            ACTION_RESUME -> resumeMusic()
                            ACTION_NEXT -> nextMusic()
                            ACTION_PREVIOUS -> previousMusic()
                            ACTION_STOP -> stopMusic()
                        }
                    }
                    "progress_from_activity" -> {
                        val progress = intent.getIntExtra("progress", 0)
                        musicPlayer.seekTo(progress)


                    }
                }
            }
        }
    }

    private fun stopMusic() {
        if (this::job.isInitialized) {
            job.cancel()
        }
        musicPlayer.stop()
    }

    private fun previousMusic() {
        if (this::job.isInitialized) {
            job.cancel()
        }
        musicPlayer.seekTo(0)
        musicPlayer.pause()
        if (audioPosition > 0) {
            audioPosition -= 1
            initMusic(audioPosition)
            showNotification(audioPosition)
        } else {
            audioPosition = 0
            initMusic(audioPosition)
            showNotification(audioPosition)
        }

    }

    private fun nextMusic() {
        if (musicPlayer.isPlaying) {
            if (this::job.isInitialized) {
                job.cancel()
            }
            musicPlayer.seekTo(0)
            musicPlayer.pause()
            if (restorePrefData()) {
                audioPosition = (0 until audioList.size).random()
            } else {
                audioPosition += 1
            }
            if (audioPosition > audioList.size - 1) {
                sendDataToActivity(ACTION_STOP)
            } else {
                initMusic(audioPosition)
                showNotification(audioPosition)
            }

        }

    }

    override fun onCompletion(mp: MediaPlayer?) {
        if (this::job.isInitialized) {
            job.cancel()
        }
        if (mp != null) {
            when (restoreRepeatMode()) {
                REPEAT_OFF -> {
                    if (restorePrefData()) {
                        audioPosition = (0 until audioList.size).random()
                    } else {
                        audioPosition += 1
                    }
                    if (audioPosition < audioList.size - 1) {
                        initMusic(audioPosition)
                        showNotification(audioPosition)
                    } else {
                        sendDataToActivity(ACTION_STOP)
                    }
                }
                REPEAT_ONE -> {
                    initMusic(audioPosition)
                    showNotification(audioPosition)
                }
                REPEAT_ALL -> {
                    audioPosition += 1
                    if (audioPosition < audioList.size) {
                        initMusic(audioPosition)
                        showNotification(audioPosition)
                    } else if (audioPosition == audioList.size) {
                        audioPosition = 0
                        initMusic(audioPosition)
                        showNotification(audioPosition)
                    }
                }
            }
        }

    }

    private fun restorePrefData(): Boolean {
        val pref = applicationContext.getSharedPreferences("myPrefs", MODE_PRIVATE)
        return pref.getBoolean("isPlayShuffle", false)
    }

    private fun restoreRepeatMode(): Int {
        val pref = applicationContext.getSharedPreferences("myPrefs", MODE_PRIVATE)
        return pref.getInt("isRepeat", Constants.REPEAT_OFF)
    }

    private fun saveIsPlaying(mode: Int) {
        val pref = applicationContext.getSharedPreferences("myPrefs", MODE_PRIVATE)
        val editor = pref.edit()
        editor.putInt("current_state", mode)
        editor.putInt("position", audioPosition)
        editor.apply()
    }

}