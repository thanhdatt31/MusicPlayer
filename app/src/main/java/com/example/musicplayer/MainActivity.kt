package com.example.musicplayer

import android.Manifest
import android.annotation.SuppressLint
import android.content.*
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.musicplayer.adapter.AudioListAdapter
import com.example.musicplayer.databinding.ActivityMainBinding
import com.example.musicplayer.helper.Constants
import com.example.musicplayer.helper.Constants.ACTION_CLEAR
import com.example.musicplayer.helper.Constants.ACTION_FROM_ACTIVITY
import com.example.musicplayer.helper.Constants.ACTION_NEXT
import com.example.musicplayer.helper.Constants.ACTION_PAUSE
import com.example.musicplayer.helper.Constants.ACTION_PREVIOUS
import com.example.musicplayer.helper.Constants.ACTION_RESUME
import com.example.musicplayer.helper.Constants.ACTION_START
import com.example.musicplayer.helper.Constants.ACTION_STOP
import com.example.musicplayer.helper.Constants.READ_STORAGE_REQUEST_CODE
import com.example.musicplayer.helper.Constants.REPEAT_ALL
import com.example.musicplayer.helper.Constants.REPEAT_OFF
import com.example.musicplayer.helper.Constants.REPEAT_ONE
import com.example.musicplayer.helper.Constants.SEND_POSITION
import com.example.musicplayer.helper.Constants.getAlbumBitmap
import com.example.musicplayer.model.Audio
import com.example.musicplayer.service.AudioService
import kotlinx.android.synthetic.main.toolbar.*


class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private var audioList: ArrayList<Audio> = arrayListOf()
    private val audioAdapter = AudioListAdapter()
    private var currentPosition: Int = 0
    private lateinit var mAudio: Audio
    private var isClicked = false
    private var isRepeat = REPEAT_OFF
    private var handler: Handler = Handler()
    private lateinit var storagePermission: Array<String>
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        LocalBroadcastManager.getInstance(this)
            .registerReceiver(broadcastReceiver, IntentFilter(Constants.SEND_POSITION))
        LocalBroadcastManager.getInstance(this)
            .registerReceiver(broadcastReceiver, IntentFilter("send_data_to_activity"))
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(toolbar)
        storagePermission = arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)

        if (checkPermissionReadStorage()) {
            getAudioFile()
        } else {
            requestPermission()
        }
        if (audioList.size != 0) {
            when (restoreIsPlaying()) {
                ACTION_RESUME -> {
                    sendActionToService(ACTION_RESUME)
                    handleLayoutMiniMusic(ACTION_START, true, audioList[restorePosition()])
                    handleLayoutFullMusic(ACTION_START, true, audioList[restorePosition()])
                }
                ACTION_PAUSE -> {
                    sendActionToService(ACTION_PAUSE)
                    handleLayoutMiniMusic(ACTION_START, false, audioList[restorePosition()])
                    handleLayoutFullMusic(ACTION_START, false, audioList[restorePosition()])
                }
            }
        }
        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            audioAdapter.setData(audioList)
            audioAdapter.setOnClickListener(onClicked)
            adapter = audioAdapter
        }
        binding.viewMini.setOnClickListener {
            binding.viewFull.visibility = View.VISIBLE
            binding.constraintLayout.visibility = View.INVISIBLE
            binding.btnDown.visibility = View.VISIBLE
            binding.btnDown.setOnClickListener {
                binding.constraintLayout.visibility = View.VISIBLE
                binding.btnDown.visibility = View.GONE
                binding.viewFull.visibility = View.INVISIBLE
                toolbar.title = "Home"
            }
            toolbar.title = "Playing"
        }
    }

    private fun handleLayoutMiniMusic(int: Int, isPlaying: Boolean, audio: Audio) {
        when (int) {
            ACTION_START -> {
                binding.viewMini.visibility = View.VISIBLE
                setStatusPauseOrResume(ACTION_START)
                binding.viewMini.visibility = View.VISIBLE
                binding.tvSongTitle.text = audio.name
                binding.tvArtistTitle.text = audio.artist
                binding.imgAlbum.setImageBitmap(getAlbumBitmap(this, audio.uri))
                updateSeekBar()
                binding.btnNextMini.setOnClickListener {
                    sendActionToService(ACTION_NEXT)
                }
                binding.btnPreviousMini.setOnClickListener {
                    sendActionToService(ACTION_PREVIOUS)
                }

            }
            ACTION_PAUSE -> {
//                setStatusPauseOrResume(false)
                setStatusPauseOrResume(ACTION_PAUSE)
            }
            ACTION_RESUME -> {
//                setStatusPauseOrResume(true)
                setStatusPauseOrResume(ACTION_RESUME)
            }
            ACTION_CLEAR -> {
                binding.viewMini.visibility = View.GONE
            }
            ACTION_STOP -> {
                setStatusPauseOrResume(ACTION_STOP)
            }
        }

    }

    private fun updateSeekBar() {
        if (currentPosition != 0) {
            binding.tvCurrentTime.text =
                Constants.durationConverter(currentPosition.toLong())
        }
        seekBarSetUp()

        handler.postDelayed(runnable, 950)
    }

    private fun seekBarSetUp() {
        binding.seekBar.progress = currentPosition
        if (this::mAudio.isInitialized) {
            binding.seekBar.max = mAudio.duration
        }
        binding.seekBar.setOnSeekBarChangeListener(@SuppressLint("AppCompatCustomView")
        object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(
                seekBar: SeekBar?,
                progress: Int,
                fromUser: Boolean
            ) {
                if (fromUser) {
                    sendProgress(progress)
                    binding.tvCurrentTime.text = Constants.durationConverter(progress.toLong())
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
//
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                sendProgress(seekBar!!.progress)
            }

        })
    }

    private fun sendProgress(progress: Int) {
        val intent = Intent("progress_from_activity")
        intent.putExtra("progress", progress)
        sendBroadcast(intent)
    }

    private var runnable = Runnable { updateSeekBar() }

    private fun setStatusPauseOrResume(action: Int) {
        when (action) {
            ACTION_PAUSE -> {
                binding.btnPause.visibility = View.INVISIBLE
                binding.btnPlay.visibility = View.VISIBLE
                binding.btnPlay.setOnClickListener {
                    sendActionToService(ACTION_RESUME)
                }
            }
            ACTION_START, ACTION_RESUME -> {
                binding.btnPause.visibility = View.VISIBLE
                binding.btnPause.setOnClickListener {
                    sendActionToService(ACTION_PAUSE)
                }
                binding.btnPlay.visibility = View.INVISIBLE
            }
            ACTION_STOP ->{
                binding.btnPause.visibility = View.INVISIBLE
                binding.btnPlay.visibility = View.VISIBLE
                binding.btnPlay.setOnClickListener {
                }
            }
        }
    }

    private val broadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent != null) {
                when (intent.action) {
                    "send_data_to_activity" -> {
                        val bundle = intent.extras
                        if (bundle != null) {
                            mAudio = bundle.getParcelable("audio")!!
                            binding.tvDuration.text =
                                Constants.durationConverter(mAudio.duration.toLong())
                            handleLayoutMiniMusic(
                                bundle.getInt("action"),
                                bundle.getBoolean("status_player"),
                                bundle.getParcelable("audio")!!
                            )
                            handleLayoutFullMusic(
                                bundle.getInt("action"),
                                bundle.getBoolean("status_player"),
                                bundle.getParcelable("audio")!!
                            )
                            Log.d("datnt", bundle.getInt("action").toString())
                        }
                    }
                    SEND_POSITION -> {
                        val bundle = intent.extras
                        if (bundle != null) {
                            currentPosition = bundle.getInt("current_position")
                        }
                    }
                }
            }
        }

    }

    private fun handleLayoutFullMusic(int: Int, isPlaying: Boolean, audio: Audio) {
        when (int) {
            ACTION_START -> {
                setStatusPauseOrResumeFull(ACTION_START)
                binding.imgAlbumFull.setImageBitmap(
                    getAlbumBitmap(
                        this,
                        audio.uri
                    )
                )
                binding.tvSongTitleFull.text = audio.name
                binding.tvArtistTitleFull.text = audio.artist
                updateSeekBar()
                binding.btnNext.setOnClickListener {
                    sendActionToService(ACTION_NEXT)
                    currentPosition = 0
                }
                binding.btnPrevious.setOnClickListener {
                    sendActionToService(ACTION_PREVIOUS)
                    currentPosition = 0
                }
//                toolbar.title = audio.name
                isClicked = restoreShuffleMode()
                if (isClicked) {
                    binding.btnShuffle.setImageResource(R.drawable.ic_baseline_shuffle_24_selected)
                } else {
                    binding.btnShuffle.setImageResource(R.drawable.ic_baseline_shuffle_24_white)
                }
                isRepeat = restoreRepeatMode()
                when (isRepeat) {
                    REPEAT_OFF -> {
                        binding.btnRepeat.setImageResource(R.drawable.ic_baseline_repeat_one_24)
                    }
                    REPEAT_ONE -> {
                        binding.btnRepeat.setImageResource(R.drawable.ic_baseline_repeat_one_24_selected)
                    }
                    REPEAT_ALL -> {
                        binding.btnRepeat.setImageResource(R.drawable.ic_baseline_repeat_24)
                    }
                }
                binding.btnRepeat.setOnClickListener {
                    isRepeat = when (isRepeat) {
                        REPEAT_OFF -> {
                            saveIsRepeat(REPEAT_ONE)
                            binding.btnRepeat.setImageResource(R.drawable.ic_baseline_repeat_one_24_selected)
                            REPEAT_ONE
                        }
                        REPEAT_ONE -> {
                            saveIsRepeat(REPEAT_ALL)
                            binding.btnRepeat.setImageResource(R.drawable.ic_baseline_repeat_24)
                            REPEAT_ALL
                        }
                        else -> {
                            saveIsRepeat(REPEAT_OFF)
                            binding.btnRepeat.setImageResource(R.drawable.ic_baseline_repeat_one_24)
                            REPEAT_OFF
                        }
                    }
                }
                binding.btnShuffle.setOnClickListener {
                    isClicked = when (isClicked) {
                        false -> {
                            saveIsPlayShuffle(true)
                            binding.btnShuffle.setImageResource(R.drawable.ic_baseline_shuffle_24_selected)
                            true
                        }
                        true -> {
                            saveIsPlayShuffle(false)
                            binding.btnShuffle.setImageResource(R.drawable.ic_baseline_shuffle_24_white)
                            false
                        }
                    }

                }
            }

            ACTION_PAUSE -> {
//                setStatusPauseOrResumeFull(false)
                setStatusPauseOrResumeFull(ACTION_PAUSE)
            }
            ACTION_RESUME -> {
//                setStatusPauseOrResumeFull(true)
                setStatusPauseOrResumeFull(ACTION_RESUME)
                Log.d("datnt", "handleLayoutFullMusic: ")
            }
            ACTION_STOP -> {
                handler.removeCallbacks(runnable)
                binding.btnPlayPause.setImageResource(R.drawable.ic_baseline_play_circle_outline_24_white)
                binding.btnPlayPause.setOnClickListener {
                }
            }
        }
    }

    private fun saveIsPlayShuffle(b: Boolean) {
        val pref = applicationContext.getSharedPreferences("myPrefs", MODE_PRIVATE)
        val editor = pref.edit()
        editor.putBoolean("isPlayShuffle", b)
        editor.apply()
    }

    private fun saveIsRepeat(mode: Int) {
        val pref = applicationContext.getSharedPreferences("myPrefs", MODE_PRIVATE)
        val editor = pref.edit()
        editor.putInt("isRepeat", mode)
        editor.apply()
    }

    private fun setStatusPauseOrResumeFull(action: Int) {
        when (action) {
            ACTION_PAUSE -> {
                binding.btnPlayPause.setImageResource(R.drawable.ic_baseline_play_circle_outline_24_white)
                binding.btnPlayPause.setOnClickListener {
                    sendActionToService(ACTION_RESUME)
                }
            }
            ACTION_RESUME, ACTION_START -> {
                binding.btnPlayPause.setImageResource(R.drawable.ic_baseline_pause_circle_outline_24_white)
                binding.btnPlayPause.setOnClickListener {
                    sendActionToService(ACTION_PAUSE)
                }
            }
        }
    }

    override fun onDestroy() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(broadcastReceiver)
        super.onDestroy()
    }

    private fun sendActionToService(action: Int) {
        val intent = Intent(ACTION_FROM_ACTIVITY)
        intent.putExtra("action", action)
        sendBroadcast(intent)
    }

    private val onClicked = object : AudioListAdapter.OnItemClickListener {
        override fun onClicked(audio: Audio, pos: Int) {
            val intent = Intent(this@MainActivity, AudioService::class.java)
            val bundle = Bundle()
            bundle.putInt("pos", pos)
            bundle.putParcelable("audio", audio)
            intent.putExtras(bundle)
            startService(intent)
        }
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
                val id =
                    cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID))
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

    private fun checkPermissionReadStorage(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.READ_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestPermission() {
        ActivityCompat.requestPermissions(this, storagePermission, READ_STORAGE_REQUEST_CODE)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == READ_STORAGE_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                getAudioFile()
                if (audioList.size != 0) {
                    when (restoreIsPlaying()) {
                        ACTION_RESUME -> {
                            sendActionToService(ACTION_RESUME)
                            handleLayoutMiniMusic(ACTION_START, true, audioList[restorePosition()])
                            handleLayoutFullMusic(ACTION_START, true, audioList[restorePosition()])
                        }
                        ACTION_PAUSE -> {
                            sendActionToService(ACTION_PAUSE)
                            handleLayoutMiniMusic(ACTION_START, false, audioList[restorePosition()])
                            handleLayoutFullMusic(ACTION_START, false, audioList[restorePosition()])
                        }
                    }
                }
                binding.recyclerView.apply {
                    layoutManager = LinearLayoutManager(this@MainActivity)
                    audioAdapter.setData(audioList)
                    audioAdapter.setOnClickListener(onClicked)
                    adapter = audioAdapter
                }
            } else {
                Toast.makeText(this, "Failed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onBackPressed() {
        if (binding.viewFull.visibility == View.VISIBLE) {
            binding.viewFull.visibility = View.INVISIBLE
            binding.constraintLayout.visibility = View.VISIBLE
        } else {
            super.onBackPressed()
        }
    }

    private fun restoreShuffleMode(): Boolean {
        val pref = applicationContext.getSharedPreferences("myPrefs", MODE_PRIVATE)
        return pref.getBoolean("isPlayShuffle", false)
    }

    private fun restoreRepeatMode(): Int {
        val pref = applicationContext.getSharedPreferences("myPrefs", MODE_PRIVATE)
        return pref.getInt("isRepeat", REPEAT_OFF)
    }

    private fun restoreIsPlaying(): Int {
        val pref = applicationContext.getSharedPreferences("myPrefs", MODE_PRIVATE)
        return pref.getInt("current_state", 0)
//        Log.d("datnt", "restoreIsPlaying: ${pref.getInt("1",0)}")
    }

    private fun restorePosition(): Int {
        val pref = applicationContext.getSharedPreferences("myPrefs", MODE_PRIVATE)
        return pref.getInt("position", 0)
    }
}
