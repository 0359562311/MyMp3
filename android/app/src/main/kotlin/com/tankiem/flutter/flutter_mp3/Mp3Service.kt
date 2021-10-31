package com.tankiem.flutter.flutter_mp3

import android.app.*
import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import kotlin.math.min
import kotlin.random.Random
import android.media.session.MediaSessionManager
import android.graphics.BitmapFactory

import android.media.MediaMetadata
import androidx.annotation.RequiresApi
import android.app.NotificationManager

import android.app.PendingIntent
import android.content.Context
import android.graphics.Color

import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.MediaSessionCompat
import androidx.core.app.NotificationCompat.Builder


class Mp3Service : Service() {
    companion object {
        const val ACTION_PLAY = "com.tankiem.flutter.flutter_mp3.ACTION_PLAY"
        const val ACTION_PAUSE = "com.tankiem.flutter.flutter_mp3.ACTION_PAUSE"
        const val ACTION_PREVIOUS = "com.tankiem.flutter.flutter_mp3.ACTION_PREVIOUS"
        const val ACTION_NEXT = "com.tankiem.flutter.flutter_mp3.ACTION_NEXT"
        const val ACTION_STOP = "com.tankiem.flutter.flutter_mp3.ACTION_STOP"
        const val ACTION_SONG_CHANGE = "com.tankiem.flutter.flutter_mp3.ACTION_SONG_CHANGE"
        const val CHANNEL_ID = "MP3_SERVICE_CHANNEL"
        const val NOTIFICATION_ID = 1
    }
    private var mediaPlayer = MediaPlayer()
    private var mediaSessionManager: MediaSessionManager? = null
    private var mediaSession: MediaSessionCompat? = null
    private var transportControls: MediaControllerCompat.TransportControls? = null

    private val binder = Mp3Binder()
    private lateinit var data: ArrayList<String>
    private var current: Int = -1
    private var isRandom = false
    private var isLoop = false

    private var fileList: MutableList<HashMap<String, Any?>> = mutableListOf(hashMapOf())

    override fun onBind(p0: Intent?): IBinder {
        return binder
    }

    fun bindData(data: MutableList<HashMap<String, Any?>>) {
        fileList = data
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
//        showNotification("title", "text")
        createNotificationChannel()
        if(intent?.getStringExtra("filePath") != null) {
            data = intent.getStringArrayListExtra("data")!!
            val filePath = intent.getStringExtra("filePath")!!
            current = data.indexOf(filePath)
            playSong(filePath)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            initMediaSession()
        }
        buildNotification(PlaybackStatus.PLAYING)
        handleIncomingActions(intent)
        return START_NOT_STICKY
    }

    private fun createNotificationChannel() {
        Log.d("TanKiem", "createNotificationChannel")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "mp3 channel",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "this is description"
                enableLights(true)
                lightColor = Color.BLUE
            }

            val manager = getSystemService(NotificationManager::class.java)

            manager.createNotificationChannel(serviceChannel)

        }
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    private fun initMediaSession() {
        if (mediaSessionManager != null) return  //mediaSessionManager exists
        mediaSessionManager = getSystemService(MEDIA_SESSION_SERVICE) as MediaSessionManager
        // Create a new MediaSession
        mediaSession = MediaSessionCompat(applicationContext, "AudioPlayer")
        //Get MediaSessions transport controls
        transportControls = mediaSession!!.controller.transportControls
        //set MediaSession -> ready to receive media commands
        mediaSession!!.isActive = true
        //indicate that the MediaSession handles transport control commands
        // through its MediaSessionCompat.Callback.
        mediaSession!!.setFlags(MediaSessionCompat.FLAG_HANDLES_QUEUE_COMMANDS)

        //Set mediaSession's MetaData
        updateMetaData()

        // Attach Callback to receive MediaSession updates
        mediaSession!!.setCallback(object : MediaSessionCompat.Callback() {
            // Implement callbacks
            override fun onPlay() {
                Log.d("TanKiem", "mediaSession onPlay")
                super.onPlay()
                play()
            }

            override fun onPause() {
                Log.d("TanKiem", "mediaSession onPause")
                super.onPause()
                pause()
            }

            override fun onSkipToNext() {
                Log.d("TanKiem", "mediaSession next")
                super.onSkipToNext()
                next()
            }

            override fun onSkipToPrevious() {
                Log.d("TanKiem", "mediaSession previous")
                super.onSkipToPrevious()
                previous()
            }

            override fun onStop() {
                super.onStop()
                removeNotification()
                val intent = Intent(ACTION_STOP)
                LocalBroadcastManager.getInstance(this@Mp3Service).sendBroadcast(intent)
                //Stop the service
                stopSelf()
            }

            override fun onSeekTo(position: Long) {
                seekTo(position.toInt())
                super.onSeekTo(position)
            }
        })
    }

    private fun updateMetaData() {
        val file = fileList[current]
        val albumArt = if(file["image"] != null) {
            BitmapFactory.decodeByteArray(file["image"] as ByteArray, 0,
                (file["image"] as ByteArray).size)
        } else {
            BitmapFactory.decodeResource(
                resources,
                R.drawable.mp3
            )
        }
        // Update the current metadata
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            mediaSession!!.setMetadata(
                MediaMetadataCompat.Builder()
                    .putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, albumArt)
                    .putString(MediaMetadata.METADATA_KEY_ARTIST,
                        (file["artist"]?:"N/A") as String?
                    )
                    .putString(MediaMetadata.METADATA_KEY_ALBUM, file["album"] as String?)
                    .putString(MediaMetadata.METADATA_KEY_TITLE,
                        (file["title"]?:file["filename"]) as String?
                    )
                    .build()
            )
        }
    }

    private fun buildNotification(playbackStatus: PlaybackStatus) {
        var notificationAction = android.R.drawable.ic_media_pause //needs to be initialized
        var play_pauseAction: PendingIntent? = null

        //Build a new notification according to the current state of the MediaPlayer
        if (playbackStatus === PlaybackStatus.PLAYING) {
            notificationAction = android.R.drawable.ic_media_pause
            //create the pause action
            play_pauseAction = playbackAction(1)
        } else if (playbackStatus === PlaybackStatus.PAUSED) {
            notificationAction = android.R.drawable.ic_media_play
            //create the play action
            play_pauseAction = playbackAction(0)
        }
        val file = fileList[current]
        val largeIcon = if(file["image"] != null) {
            BitmapFactory.decodeByteArray(file["image"] as ByteArray, 0,
                (file["image"] as ByteArray).size)
        } else {
            BitmapFactory.decodeResource(
                resources,
                R.drawable.mp3
            )
        }

        // Create a new Notification
        val notificationBuilder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val notificationIntent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
                addCategory(Intent.CATEGORY_LAUNCHER)
                action = Intent.ACTION_MAIN
            }
            val pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT)
            Builder(this, CHANNEL_ID)
                .setShowWhen(false) // Set the Notification style
                .setStyle(
                    androidx.media.app.NotificationCompat.MediaStyle() // Attach our MediaSession token
                        .setMediaSession(mediaSession!!.sessionToken) // Show our playback controls in the compact notification view.
                        .setShowActionsInCompactView(0, 2, 1)
                ) // Set the Notification color
//                .setColor(resources.getColor(R.color.colorPrimary)) // Set the large and small icons
                .setLargeIcon(largeIcon)
                .setSmallIcon(android.R.drawable.stat_sys_headset) // Set Notification content information
                .setContentText(file["artist"] as String?)
                .setContentTitle(file["title"] as String?)
                .setContentIntent(pendingIntent)
                .setContentInfo(file["album"] as String?)
                .addAction(android.R.drawable.ic_media_previous, "previous", playbackAction(3))
                .addAction(notificationAction, "pause", play_pauseAction)
                .addAction(
                    android.R.drawable.ic_media_next,
                    "next",
                    playbackAction(2)
                ) as Builder
        } else {
            TODO("VERSION.SDK_INT < LOLLIPOP")
        }
        startForeground(NOTIFICATION_ID,notificationBuilder.build())
//        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).notify(
//            NOTIFICATION_ID,
//            notificationBuilder.build()
//        )
    }

    private fun removeNotification() {
//        val notificationManager =
//            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
//        notificationManager.cancel(NOTIFICATION_ID)
        stopForeground(true)
    }

    private fun playbackAction(actionNumber: Int): PendingIntent? {
        val playbackAction = Intent(this, Mp3Service::class.java)
        return when (actionNumber) {
            0 -> {
                // Play
                playbackAction.action = ACTION_PLAY
                PendingIntent.getService(this, actionNumber, playbackAction, 0)
            }
            1 -> {
                // Pause
                playbackAction.action = ACTION_PAUSE
                PendingIntent.getService(this, actionNumber, playbackAction, 0)
            }
            2 -> {
                // Next track
                playbackAction.action = ACTION_NEXT
                PendingIntent.getService(this, actionNumber, playbackAction, 0)
            }
            3 -> {
                // Previous track
                playbackAction.action = ACTION_PREVIOUS
                PendingIntent.getService(this, actionNumber, playbackAction, 0)
            }
            4 -> {
                // cancel
                playbackAction.action = ACTION_STOP
                PendingIntent.getService(this, actionNumber, playbackAction, 0)
            }
            else -> {
                null
            }
        }
    }

    private fun handleIncomingActions(playbackAction: Intent?) {
        if (playbackAction == null || playbackAction.action == null) return
        val actionString = playbackAction.action
        when {
            actionString.equals(ACTION_PLAY, ignoreCase = true) -> {
                transportControls!!.play()
            }
            actionString.equals(ACTION_PAUSE, ignoreCase = true) -> {
                transportControls!!.pause()
            }
            actionString.equals(ACTION_NEXT, ignoreCase = true) -> {
                transportControls!!.skipToNext()
            }
            actionString.equals(ACTION_PREVIOUS, ignoreCase = true) -> {
                transportControls!!.skipToPrevious()
            }
            actionString.equals(ACTION_STOP, ignoreCase = true) -> {
                transportControls!!.stop()
            }
        }
    }

    private fun showNotification(title: String, text: String) {

        val notificationIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            addCategory(Intent.CATEGORY_LAUNCHER)
            action = Intent.ACTION_MAIN
        }
        val pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Builder(this, CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(text)
                .setSmallIcon(R.drawable.launch_background)
                .setContentIntent(pendingIntent)
                .build()
        } else {
            Builder(this)
                .setContentTitle(title)
                .setContentText(text)
                .setSmallIcon(R.drawable.launch_background)
                .setContentIntent(pendingIntent)
                .build()
        }
        startForeground(NOTIFICATION_ID, notification)
    }

    fun playSong(filePath: String) {
        if (mediaPlayer.isPlaying) {
            mediaPlayer.stop()
            mediaPlayer.reset()
        }
        mediaPlayer = MediaPlayer.create(this, Uri.parse(filePath))
        mediaPlayer.isLooping = isLoop
        mediaPlayer.setOnCompletionListener {
            Log.d("TanKiem", "complete")
            if(mediaPlayer.isLooping.not()){
                next()
            } else {
                playSong(data[current])
            }
        }
        mediaPlayer.setOnPreparedListener {
            val intent = Intent()
            intent.putExtra("filePath", data[current])
            intent.action = ACTION_SONG_CHANGE
            LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
            it.start()
        }
    }

    fun pause() {
        if (mediaPlayer.isPlaying) {
            mediaPlayer.pause()
            buildNotification(PlaybackStatus.PAUSED)
            val intent = Intent(ACTION_PAUSE)
            LocalBroadcastManager.getInstance(this@Mp3Service).sendBroadcast(intent)
        }
    }

    fun play() {
        if (!mediaPlayer.isPlaying) {
            mediaPlayer.start()
            buildNotification(PlaybackStatus.PLAYING)
            val intent = Intent(ACTION_PLAY)
            LocalBroadcastManager.getInstance(this@Mp3Service).sendBroadcast(intent)
        }
    }

    fun next() {
        current = if(!isRandom) {
            (current + 1) % data.size
        } else {
            Random.nextInt(data.size)
        }
        updateMetaData()
        buildNotification(PlaybackStatus.PLAYING)
        playSong(data[current])
    }

    fun previous() {
        current = if(!isRandom) {
            (current - 1 + data.size) % data.size
        } else {
            Random.nextInt(data.size)
        }
        updateMetaData()
        buildNotification(PlaybackStatus.PLAYING)
        playSong(data[current])
    }

    fun seekTo(value: Int) {
        mediaPlayer.seekTo(min(value,mediaPlayer.duration - 500))
    }

    fun loop(b : Boolean) {
        mediaPlayer.isLooping = b
        isLoop = b
    }

    fun random(b: Boolean) {
        isRandom = b
    }

    fun currentState(): HashMap<String, Any> {
        return hashMapOf(
            "filePath" to data[current],
            "seconds" to mediaPlayer.currentPosition,
            "isLoop" to isLoop,
            "isRandom" to isRandom
        )
    }

    fun dispose() {
        if(mediaPlayer.isPlaying.not())
            stopSelf()
    }

    override fun onDestroy() {
        mediaPlayer.release()
        mediaSession?.release()
        super.onDestroy()
    }

    inner class Mp3Binder : Binder() {
        fun getService(): Mp3Service {
            return this@Mp3Service
        }
    }
}

enum class PlaybackStatus {
    PLAYING, PAUSED
}