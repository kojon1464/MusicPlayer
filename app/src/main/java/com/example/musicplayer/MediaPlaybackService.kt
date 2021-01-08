package com.example.musicplayer

import android.app.PendingIntent
import android.content.ContentUris
import android.content.Intent
import android.database.Cursor
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import androidx.media.MediaBrowserServiceCompat
import androidx.media.session.MediaButtonReceiver




private const val MY_EMPTY_MEDIA_ROOT_ID = "empty_root_id"

class MediaPlaybackService : MediaBrowserServiceCompat() {

    private var mediaSession: MediaSessionCompat? = null
    private lateinit var stateBuilder: PlaybackStateCompat.Builder
    private lateinit var mediaPlayer: MediaPlayer

    override fun onCreate() {
        super.onCreate()

        val requestedColumns =
        arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.DURATION
        )

        var id: Long = 0
        var metadata = MediaMetadataCompat.Builder().build()
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
                    id = cursor.getLong(idColumn)
                    metadata = MediaMetadataCompat.Builder().apply {
                        putString(MediaStore.Audio.Media.TITLE, cursor.getString(1))
                    }.build()
                    Log.d("My", cursor.getString(1))
                } while(cursor.moveToNext())

            }
        }
        cursor?.close()



        val contentUri: Uri =
            ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)

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

            setCallback(MySessionCallback(this@MediaPlaybackService, this, mediaPlayer))

            setSessionToken(sessionToken)

            val intent = Intent(this@MediaPlaybackService, MainActivity::class.java)
            setSessionActivity(PendingIntent.getActivity(this@MediaPlaybackService, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT))

            setMetadata(metadata)
        }
    }

    override fun onLoadChildren(
        parentMediaId: String,
        result: MediaBrowserServiceCompat.Result<List<MediaBrowserCompat.MediaItem>>
    ) {
            result.sendResult(null)
            return
    }

    override fun onGetRoot(
        clientPackageName: String,
        clientUid: Int,
        rootHints: Bundle?
    ): MediaBrowserServiceCompat.BrowserRoot {
           return MediaBrowserServiceCompat.BrowserRoot(MY_EMPTY_MEDIA_ROOT_ID, null)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        MediaButtonReceiver.handleIntent(mediaSession, intent)
        return super.onStartCommand(intent, flags, startId)
    }
}