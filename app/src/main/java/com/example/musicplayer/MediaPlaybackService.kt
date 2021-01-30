package com.example.musicplayer

import android.app.PendingIntent
import android.content.ContentUris
import android.content.Intent
import android.database.Cursor
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.MediaPlayer.OnCompletionListener
import android.media.browse.MediaBrowser
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.media.MediaBrowserServiceCompat
import androidx.media.session.MediaButtonReceiver


private const val MY_MEDIA_ROOT_ID = "root_id"

class MediaPlaybackService : MediaBrowserServiceCompat() {

    private var mediaSession: MediaSessionCompat? = null
    private lateinit var stateBuilder: PlaybackStateCompat.Builder
    private lateinit var mediaPlayer: MediaPlayer
    private var mediaMetadatas: MutableList<MediaMetadataCompat> = ArrayList()
    private var currentPlaying: Int = 0

    override fun onCreate() {
        super.onCreate()

        createMediaMetadataList()

        val contentUri: Uri =
            ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, mediaMetadatas[currentPlaying].getString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID).toLong())

        mediaPlayer = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build()
            )
            setDataSource(applicationContext, contentUri)
            prepare()
        }

        mediaSession = MediaSessionCompat(baseContext, "MY_MUSIC_SESSION").apply {

            setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS
                    or MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
            )

            stateBuilder = PlaybackStateCompat.Builder()
                .setActions(PlaybackStateCompat.ACTION_PLAY
                        or PlaybackStateCompat.ACTION_PLAY_PAUSE
                )
            setPlaybackState(stateBuilder.build())

            val callback = MySessionCallback(this@MediaPlaybackService, this, mediaPlayer)
            setCallback(callback)

            mediaPlayer.setOnCompletionListener(OnCompletionListener {
                callback.onSkipToNext()
            })

            setSessionToken(sessionToken)

            val intent = Intent(this@MediaPlaybackService, MainActivity::class.java)
            setSessionActivity(PendingIntent.getActivity(this@MediaPlaybackService, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT))

            setMetadata(mediaMetadatas[currentPlaying])
        }
    }

    override fun onLoadChildren(
        parentMediaId: String,
        result: MediaBrowserServiceCompat.Result<List<MediaBrowserCompat.MediaItem>>
    ) {
        if(parentMediaId == MY_MEDIA_ROOT_ID){
            result.sendResult(mediaMetadatas.map { m -> MediaBrowserCompat.MediaItem(m.description, MediaBrowserCompat.MediaItem.FLAG_PLAYABLE)})
        } else {
            result.sendResult(null)
        }
        return
    }

    override fun onGetRoot(
        clientPackageName: String,
        clientUid: Int,
        rootHints: Bundle?
    ): MediaBrowserServiceCompat.BrowserRoot {
           return MediaBrowserServiceCompat.BrowserRoot(MY_MEDIA_ROOT_ID, null)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        MediaButtonReceiver.handleIntent(mediaSession, intent)
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer.release()
    }

    fun playNext() {
        currentPlaying = (currentPlaying + 1) % mediaMetadatas.size

        mediaPlayer.reset();

        val contentUri: Uri =
            ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, mediaMetadatas[currentPlaying].getString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID).toLong())
        mediaPlayer.setDataSource(applicationContext, contentUri)

        mediaPlayer.prepare()
        mediaSession?.setMetadata(mediaMetadatas[currentPlaying])
    }

    fun playPrevious() {
        currentPlaying -= 1
        if(currentPlaying < 0)
            currentPlaying = mediaMetadatas.size - 1

        mediaPlayer.reset();

        val contentUri: Uri =
            ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, mediaMetadatas[currentPlaying].getString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID).toLong())
        mediaPlayer.setDataSource(applicationContext, contentUri)

        mediaPlayer.prepare()
        mediaSession?.setMetadata(mediaMetadatas[currentPlaying])
    }

    fun playMedia(mediaId: String) {
        var index = -1
        for (i in 0..mediaMetadatas.size-1){
            if(mediaMetadatas[i].description.mediaId == mediaId)
                index = i
        }

        if(index == -1)
            return

        currentPlaying = index

        mediaPlayer.reset();

        val contentUri: Uri =
            ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, mediaMetadatas[currentPlaying].getString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID).toLong())
        mediaPlayer.setDataSource(applicationContext, contentUri)

        mediaPlayer.prepare()
        mediaSession?.setMetadata(mediaMetadatas[currentPlaying])
    }

    private fun createMediaMetadataList(){
        val requestedColumns =
            arrayOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.DURATION
            )

        val cursor: Cursor? = contentResolver.query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, requestedColumns, MediaStore.Audio.Media.IS_MUSIC+"=1", null, null)
        when {
            cursor == null -> {
                // query failed, handle error.
            }
            !cursor.moveToFirst() -> {
                // no media on the device
            }
            else -> {
                do{
                    val idColumn: Int = cursor.getColumnIndex(MediaStore.Audio.Media._ID)
                    val durationColumn: Int = cursor.getColumnIndex(MediaStore.Audio.Media.DURATION)
                    val metadata = MediaMetadataCompat.Builder().apply {
                        putString(MediaMetadataCompat.METADATA_KEY_TITLE, cursor.getString(1))
                        putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, cursor.getLong(idColumn).toString())
                        putLong(MediaMetadataCompat.METADATA_KEY_DURATION, cursor.getLong(durationColumn))
                    }.build()

                    mediaMetadatas.add(metadata)
                } while(cursor.moveToNext())

            }
        }
        cursor?.close()
    }
}