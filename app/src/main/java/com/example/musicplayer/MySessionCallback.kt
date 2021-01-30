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
import android.os.Bundle
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
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
        if(mediaSession.controller.playbackState.state == PlaybackStateCompat.STATE_PLAYING)
            return

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

            val controller = mediaSession.controller
            val mediaMetadata = controller.metadata
            val description = mediaMetadata.description

            mediaPlayer.start()
            context.registerReceiver(becomingNoisyReceiver, intentFilter)
            becomeNoisyRegistered = true

            mediaSession.setPlaybackState(PlaybackStateCompat.Builder(mediaSession.controller.playbackState).setState(PlaybackStateCompat.STATE_PLAYING, mediaPlayer.currentPosition.toLong(),1f).build())

            // Put the service in the foreground, post notification
            createNotificationChannel()
            service.startForeground(NOTIFICATION_ID, createNotification(description, controller.sessionActivity))
        }
    }

    public override fun onStop() {
        if(mediaSession.controller.playbackState.state == PlaybackStateCompat.STATE_STOPPED)
            return

        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.abandonAudioFocusRequest(audioFocusRequest)

        if(becomeNoisyRegistered) {
            context.unregisterReceiver(becomingNoisyReceiver)
            becomeNoisyRegistered = false
        }

        service.stopSelf()
        mediaSession.isActive = false

        mediaPlayer.seekTo(0)
        mediaPlayer.stop()
        mediaPlayer.prepare()

        mediaSession.setPlaybackState(PlaybackStateCompat.Builder(mediaSession.controller.playbackState).setState(PlaybackStateCompat.STATE_STOPPED, 0,1f).build())

        val controller = mediaSession.controller
        val mediaMetadata = controller.metadata
        val description = mediaMetadata.description


        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, createNotification(description, controller.sessionActivity, false))

        service.stopForeground(false)
    }

    public override fun onPause() {
        if(mediaSession.controller.playbackState.state == PlaybackStateCompat.STATE_PAUSED)
            return

        mediaPlayer.pause()

        if(becomeNoisyRegistered) {
            context.unregisterReceiver(becomingNoisyReceiver)
            becomeNoisyRegistered = false
        }

        val controller = mediaSession.controller
        val mediaMetadata = controller.metadata
        val description = mediaMetadata.description

        mediaSession.setPlaybackState(PlaybackStateCompat.Builder(mediaSession.controller.playbackState).setState(PlaybackStateCompat.STATE_PAUSED, mediaSession.controller.playbackState.position,1f).build())

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, createNotification(description, controller.sessionActivity, false))

        service.stopForeground(false)
    }

    override fun onSkipToPrevious() {
        service.playPrevious()
        if(mediaSession.controller.playbackState.state == PlaybackStateCompat.STATE_PLAYING){
            mediaSession.setPlaybackState(PlaybackStateCompat.Builder(mediaSession.controller.playbackState).setState(PlaybackStateCompat.STATE_PLAYING, 0,1f).build())
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, createNotification(mediaSession.controller.metadata.description, mediaSession.controller.sessionActivity, true))
            mediaPlayer.start()
        }  else
            onPlay()
    }

    override fun onSkipToNext() {

        service.playNext()
        if(mediaSession.controller.playbackState.state == PlaybackStateCompat.STATE_PLAYING){
            mediaSession.setPlaybackState(PlaybackStateCompat.Builder(mediaSession.controller.playbackState).setState(PlaybackStateCompat.STATE_PLAYING, 0,1f).build())
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, createNotification(mediaSession.controller.metadata.description, mediaSession.controller.sessionActivity, true))
            mediaPlayer.start()
        } else
            onPlay()
    }

    override fun onSeekTo(pos: Long) {
        mediaSession.setPlaybackState(PlaybackStateCompat.Builder(mediaSession.controller.playbackState).setState(mediaSession.controller.playbackState.state, pos,1f).build())
        mediaPlayer.seekTo(pos.toInt())
    }

    override fun onPlayFromMediaId(mediaId: String?, extras: Bundle?) {
        if(mediaId == null)
            return

        service.playMedia(mediaId)
        if(mediaSession.controller.playbackState.state == PlaybackStateCompat.STATE_PLAYING){
            mediaSession.setPlaybackState(PlaybackStateCompat.Builder(mediaSession.controller.playbackState).setState(PlaybackStateCompat.STATE_PLAYING, 0,1f).build())
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, createNotification(mediaSession.controller.metadata.description, mediaSession.controller.sessionActivity, true))
            mediaPlayer.start()
        } else
            onPlay()
    }

    override fun onAudioFocusChange(focusChange: Int) {
        TODO("Not yet implemented")
    }

    private var becomeNoisyRegistered = false
    private val becomingNoisyReceiver  = object : BroadcastReceiver() {

        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                onPause()
            }
        }
    }

    private fun createNotification(description: MediaDescriptionCompat, sessionActivity: PendingIntent, pause: Boolean =true) : Notification {
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
                    if (pause) R.drawable.pause else R.drawable.play,
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