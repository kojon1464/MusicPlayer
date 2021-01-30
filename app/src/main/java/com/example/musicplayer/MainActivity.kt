package com.example.musicplayer

import android.Manifest
import android.content.ComponentName
import android.content.DialogInterface
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.session.PlaybackState
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.widget.Button
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView


private const val STORAGE_PERMISSION_CODE = 1

class MainActivity : AppCompatActivity() {

    private lateinit var mediaBrowser: MediaBrowserCompat
    private lateinit var seekBar: SeekBar
    private lateinit var title: TextView
    private lateinit var recyclerView: RecyclerView
    private var lastPlaybackState: PlaybackStateCompat? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if(!hasPermission())
            requestPermission()

        mediaBrowser = MediaBrowserCompat(
            this,
            ComponentName(this, MediaPlaybackService::class.java),
            connectionCallbacks,
            null
        )

        setContentView(R.layout.activity_main)
    }

    override fun onStart() {
        super.onStart()

        if(hasPermission() && !mediaBrowser.isConnected)
            mediaBrowser.connect()

        seekBar = findViewById<SeekBar>(R.id.seekBar)
        title = findViewById<TextView>(R.id.title)
        recyclerView = findViewById<RecyclerView>(R.id.recycler_view)
    }

    override fun onResume() {
        super.onResume()
        volumeControlStream = AudioManager.STREAM_MUSIC
    }

    override fun onStop() {
        super.onStop()
        MediaControllerCompat.getMediaController(this)?.unregisterCallback(controllerCallback)
        mediaBrowser.disconnect()
    }

    private val connectionCallbacks = object : MediaBrowserCompat.ConnectionCallback() {
        override fun onConnected() {

            val mediaController = MediaControllerCompat(
                this@MainActivity,
                mediaBrowser.sessionToken
            )

            MediaControllerCompat.setMediaController(this@MainActivity, mediaController)

            buildTransportControls()
            title.text = mediaController.metadata.getString(MediaMetadataCompat.METADATA_KEY_TITLE)
            seekBar.max = mediaController.metadata.getLong(MediaMetadataCompat.METADATA_KEY_DURATION).toInt()

            val handler  =  Handler(Looper.getMainLooper())

            handler.post(object : Runnable {
                override fun run() {
                    updateProgress()
                    handler.postDelayed(this, 1000)
                }
            })

            mediaBrowser.subscribe(mediaBrowser.root, mediaBrowserCallback)
        }

        override fun onConnectionSuspended() {
            // The Service has crashed. Disable transport controls until it automatically reconnects
        }

        override fun onConnectionFailed() {
            // The Service has refused our connection
        }

    }

    fun buildTransportControls() {
        val mediaController = MediaControllerCompat.getMediaController(this@MainActivity)
        // Grab the view for the play/pause button
        val playPause = findViewById<Button>(R.id.play_pause).apply {
            setOnClickListener {
                // Since this is a play/pause button, you'll need to test the current state
                // and choose the action accordingly

                val pbState = mediaController.playbackState.state
                if (pbState == PlaybackStateCompat.STATE_PLAYING) {
                    mediaController.transportControls.pause()
                } else {
                    mediaController.transportControls.play()
                }
            }
        }

        val stop = findViewById<Button>(R.id.stop).apply {
            setOnClickListener {
                mediaController.transportControls.stop()
            }
        }

        val previous = findViewById<Button>(R.id.previous).apply {
            setOnClickListener {
                mediaController.transportControls.skipToPrevious()
            }
        }

        val next = findViewById<Button>(R.id.next).apply {
            setOnClickListener {
                mediaController.transportControls.skipToNext()
            }
        }

        val plus_10 = findViewById<Button>(R.id.plus_10).apply {
            setOnClickListener {
                mediaController.transportControls.seekTo(seekBar.progress.toLong() + 10*1000)
            }
        }

        val minus_10 = findViewById<Button>(R.id.minus_10).apply {
            setOnClickListener {
                mediaController.transportControls.seekTo(seekBar.progress.toLong() - 10*1000)
            }
        }

        seekBar.apply {
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {

                override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                    if(fromUser){
                        mediaController.transportControls.seekTo(progress.toLong())
                    }
                }

                override fun onStartTrackingTouch(seekBar: SeekBar) {}

                override fun onStopTrackingTouch(seekBar: SeekBar) {}
            })
        }

        // Display the initial state
        val metadata = mediaController.metadata
        val pbState = mediaController.playbackState

        // Register a Callback to stay in sync
        mediaController.registerCallback(controllerCallback)
    }

    private var controllerCallback = object : MediaControllerCompat.Callback() {

        override fun onMetadataChanged(metadata: MediaMetadataCompat?) {
            if (metadata != null) {
                seekBar.max = metadata.getLong(MediaMetadataCompat.METADATA_KEY_DURATION).toInt()
                title.text = metadata.getString(MediaMetadataCompat.METADATA_KEY_TITLE)
            }
        }

        override fun onPlaybackStateChanged(state: PlaybackStateCompat?) {
            if (state != null){
                lastPlaybackState = state
            }
        }
    }

    private var mediaBrowserCallback = object : MediaBrowserCompat.SubscriptionCallback() {
        override fun onChildrenLoaded(parentId: String, children: MutableList<MediaBrowserCompat.MediaItem>) {
            recyclerView.adapter = MyRecyclerAdapter(children) {mediaItem ->  changeSong(mediaItem)}
        }
    }

    private fun changeSong(item: MediaBrowserCompat.MediaItem){
        val mediaController = MediaControllerCompat.getMediaController(this@MainActivity)
        mediaController.transportControls.playFromMediaId(item.mediaId, null)
    }

    private fun updateProgress() {
        if (lastPlaybackState == null) {
            return
        }
        var currentPosition: Long = lastPlaybackState!!.getPosition()
        if (lastPlaybackState!!.getState() == PlaybackStateCompat.STATE_PLAYING) {

            val timeDelta: Long = SystemClock.elapsedRealtime() -
                    lastPlaybackState!!.getLastPositionUpdateTime()
            currentPosition += (timeDelta.toInt() * lastPlaybackState!!.getPlaybackSpeed()).toLong()
        }
        seekBar.setProgress(currentPosition.toInt())
    }

    private fun hasPermission() : Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
    }

    private fun requestPermission(){
        if(ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.READ_EXTERNAL_STORAGE)){
            AlertDialog.Builder(this).apply {
                setTitle("Permission needed")
                setMessage("Permission is needed for proper application behavior")
                setPositiveButton("ok") { dialog: DialogInterface, which: Int ->
                    dialog.dismiss()
                    ActivityCompat.requestPermissions(this@MainActivity, arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), STORAGE_PERMISSION_CODE)
                }
                create()
                show()
            }
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), STORAGE_PERMISSION_CODE)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        if(requestCode == STORAGE_PERMISSION_CODE){
            if(grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED){
                mediaBrowser.connect()
            } else {
                finish()
            }
        }
    }
}