package com.example.musicplayer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.session.PlaybackState
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.ContextCompat.getSystemService
import androidx.media.session.MediaButtonReceiver


private const val NOTIFICATION_ID = 1;
private const val CHANNEL_ID = "com.example.musicplayer";

class MySessionCallback(private val service: MediaPlaybackService, private val mediaSession: MediaSessionCompat, private val mediaPlayer: MediaPlayer) : MediaSessionCompat.Callback(), AudioManager.OnAudioFocusChangeListener {

    private val context = service.applicationContext
    private val intentFilter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)

    private lateinit var audioFocusRequest: AudioFocusRequest

    override fun onPlay() {

        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

        audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN).run {
            setOnAudioFocusChangeListener(this@MySessionCallback)
            setAudioAttributes(AudioAttributes.Builder().run {
                setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                build()
            })
            build()
        }

        val result = audioManager.requestAudioFocus(audioFocusRequest)
        if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            context.startService(Intent(context, MediaPlaybackService::class.java))

            mediaSession.isActive = true
            mediaSession.setPlaybackState(PlaybackStateCompat.Builder(mediaSession.controller.playbackState).setState(PlaybackStateCompat.STATE_PLAYING, mediaSession.controller.playbackState.position,1f).build())

            mediaPlayer.start()
            context.registerReceiver(becomingNoisyReceiver, intentFilter)
            // Put the service in the foreground, post notification
            val controller = mediaSession.controller
            val mediaMetadata = controller.metadata
            val description = mediaMetadata.description

            createNotificationChannel()
            service.startForeground(NOTIFICATION_ID, createNotification(description, controller.sessionActivity))
        }
    }

    public override fun onStop() {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.abandonAudioFocusRequest(audioFocusRequest)
        context.unregisterReceiver(becomingNoisyReceiver)
        service.stopSelf()
        mediaSession.isActive = false
        mediaPlayer.stop()
        service.stopForeground(false)
    }

    public override fun onPause() {
        mediaSession.setPlaybackState(PlaybackStateCompat.Builder(mediaSession.controller.playbackState).setState(PlaybackStateCompat.STATE_PAUSED, mediaSession.controller.playbackState.position,1f).build())

        mediaPlayer.pause()
        context.unregisterReceiver(becomingNoisyReceiver)
        service.stopForeground(false)
    }

    override fun onAudioFocusChange(focusChange: Int) {
        TODO("Not yet implemented")
    }

    private val becomingNoisyReceiver  = object : BroadcastReceiver() {

        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                onPause()
            }
        }
    }

    private fun createNotification(description: MediaDescriptionCompat, sessionActivity: PendingIntent) : Notification {
        return NotificationCompat.Builder(context, CHANNEL_ID).apply {
            // Add the metadata for the currently playing track
            setContentTitle(description.title)

            // Enable launching the player by clicking the notification
            setContentIntent(sessionActivity)

            // Stop the service when the notification is swiped away
            setDeleteIntent(
                MediaButtonReceiver.buildMediaButtonPendingIntent(
                    context,
                    PlaybackStateCompat.ACTION_STOP
                )
            )

            // Make the transport controls visible on the lockscreen
            setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

            // Add an app icon and set its accent color
            // Be careful about the color
            setSmallIcon(R.drawable.notification_icon)
            color = ContextCompat.getColor(context, R.color.colorPrimaryDark)

            // Add a pause button
            addAction(
                NotificationCompat.Action(
                    R.drawable.pause,
                    context.getString(R.string.pause),
                    MediaButtonReceiver.buildMediaButtonPendingIntent(
                        context,
                        PlaybackStateCompat.ACTION_PLAY_PAUSE
                    )
                )
            )

            // Take advantage of MediaStyle features
            setStyle(androidx.media.app.NotificationCompat.MediaStyle()
                .setMediaSession(mediaSession.sessionToken)
                .setShowActionsInCompactView(0)

                // Add a cancel button
                .setShowCancelButton(true)
                .setCancelButtonIntent(
                    MediaButtonReceiver.buildMediaButtonPendingIntent(
                        context,
                        PlaybackStateCompat.ACTION_STOP
                    )
                )
            )
        }.build()
    }

    private fun createNotificationChannel() {
        val name = context.getString(R.string.channel_name)
        val mChannel = NotificationChannel(CHANNEL_ID, name, NotificationManager.IMPORTANCE_DEFAULT)

        val notificationManager = context.getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(mChannel)
    }
}