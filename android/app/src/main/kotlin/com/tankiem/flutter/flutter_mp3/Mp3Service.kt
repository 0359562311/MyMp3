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

import androidx.annotation.RequiresApi

import android.app.PendingIntent

import android.support.v4.media.session.MediaSessionCompat
import androidx.core.app.NotificationCompat.Builder


class Mp3Service : Service(){
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

    private val binder = Mp3Binder()
    private var current: Int = -1
    private var currentFilePath = ""
    private var isRandom = false
    private var isLoop = false

    private var fileList: ArrayList<Song> = ArrayList()

    override fun onBind(p0: Intent?): IBinder {
        return binder
    }

    fun bindData(fileList: ArrayList<Song>) {
        if(fileList.isNotEmpty()) {
            this.fileList = fileList
            current = fileList.indexOfFirst {
                it.filePath == currentFilePath
            }
            buildNotification(PlaybackStatus.PLAYING)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("TanKiem", "onStartCommand")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            initMediaSession()
        }
        if(intent?.getStringExtra("filePath") != null) {
            val filePath = intent.getStringExtra("filePath")!!
            playSong(filePath)
        }
        handleIncomingActions(intent)
        return START_NOT_STICKY
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    private fun initMediaSession() {
        if (mediaSessionManager != null) return  //mediaSessionManager exists
        mediaSessionManager = getSystemService(MEDIA_SESSION_SERVICE) as MediaSessionManager
        // Create a new MediaSession
        mediaSession = MediaSessionCompat(applicationContext, "AudioPlayer")
        //set MediaSession -> ready to receive media commands
        mediaSession!!.isActive = true
    }

    private fun buildNotification(playbackStatus: PlaybackStatus) {
        var notificationIconCallback = android.R.drawable.ic_media_pause //needs to be initialized
        var playOrPausePendingIntent: PendingIntent? = null

        //Build a new notification according to the current state of the MediaPlayer
        if (playbackStatus == PlaybackStatus.PLAYING) {
            notificationIconCallback = android.R.drawable.ic_media_pause
            //create the pause action
            playOrPausePendingIntent = playbackAction(1)
        } else if (playbackStatus == PlaybackStatus.PAUSED) {
            notificationIconCallback = android.R.drawable.ic_media_play
            //create the play action
            playOrPausePendingIntent = playbackAction(0)
        }
        val file = if(current >= 0) fileList[current] else null
        val largeIcon = if(file?.image != null) {
            BitmapFactory.decodeByteArray(file.image , 0,
                (file.image).size)
        } else {
            BitmapFactory.decodeResource(
                resources,
                R.drawable.mp3
            )
        }

        Log.d("TanKiem", "build notification ${file?.artist ?:"N/A"}  ${file?.title ?: file?.fileName}")

        // Create a new Notification
        val notificationBuilder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Builder(this, CHANNEL_ID)
        } else {
            Builder(this)
        }
        val notificationIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            addCategory(Intent.CATEGORY_LAUNCHER)
            action = Intent.ACTION_MAIN
        }
        val pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0)
        notificationBuilder.setShowWhen(false) // Set the Notification style
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle() // Attach our MediaSession token
                    .setMediaSession(mediaSession!!.sessionToken) // Show our playback controls in the compact notification view.
                    .setShowActionsInCompactView(0, 1, 2)
            ) // Set the Notification color
//                .setColor(resources.getColor(R.color.colorPrimary)) // Set the large and small icons
            .setLargeIcon(largeIcon)
            .setSmallIcon(android.R.drawable.stat_sys_headset) // Set Notification content information
            .setContentText((file?.artist ?:"N/A") as String?)
            .setContentTitle((file?.title ?: file?.fileName))
            .setContentIntent(pendingIntent)
            .setContentInfo(file?.album)
            .addAction(android.R.drawable.ic_media_previous, "previous", playbackAction(3))
            .addAction(notificationIconCallback, "pause", playOrPausePendingIntent)
            .addAction(
                android.R.drawable.ic_media_next,
                "next",
                playbackAction(2)
            )
            .addAction(android.R.drawable.ic_delete, "cancel", playbackAction(4))
        startForeground(NOTIFICATION_ID,notificationBuilder.build())
    }

    private fun removeNotification() {
        stopForeground(true)
    }

    private fun playbackAction(actionNumber: Int): PendingIntent? {
        val intent = Intent(this, Mp3Service::class.java)
        return when (actionNumber) {
            0 -> {
                // Play
                intent.action = ACTION_PLAY
                PendingIntent.getService(this, actionNumber, intent, 0)
            }
            1 -> {
                // Pause
                intent.action = ACTION_PAUSE
                PendingIntent.getService(this, actionNumber, intent, 0)
            }
            2 -> {
                // Next track
                intent.action = ACTION_NEXT
                PendingIntent.getService(this, actionNumber, intent, 0)
            }
            3 -> {
                // Previous track
                intent.action = ACTION_PREVIOUS
                PendingIntent.getService(this, actionNumber, intent, 0)
            }
            4 -> {
                // cancel
                intent.action = ACTION_STOP
                PendingIntent.getService(this, actionNumber, intent, 0)
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
                play()
            }
            actionString.equals(ACTION_PAUSE, ignoreCase = true) -> {
                pause()
            }
            actionString.equals(ACTION_NEXT, ignoreCase = true) -> {
                next()
            }
            actionString.equals(ACTION_PREVIOUS, ignoreCase = true) -> {
                previous()
            }
            actionString.equals(ACTION_STOP, ignoreCase = true) -> {
                pause()
                removeNotification()
                stopSelf()
            }
        }
    }

    fun playSong(filePath: String) {
        if (mediaPlayer.isPlaying) {
            mediaPlayer.stop()
            mediaPlayer.reset()
        }
        currentFilePath = filePath
        current = fileList.indexOfFirst {
            it.filePath == filePath
        }
        mediaPlayer = MediaPlayer.create(this, Uri.parse(filePath))
        mediaPlayer.setOnCompletionListener {
            if(!isLoop){
                next()
            } else {
                playSong(filePath)
            }
        }
        mediaPlayer.setOnPreparedListener {
            val intent = Intent()
            intent.putExtra("filePath", filePath)
            intent.action = ACTION_SONG_CHANGE
            LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
            it.start()
            if(fileList.isNotEmpty())
                buildNotification(PlaybackStatus.PLAYING)
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
            (current + 1) % fileList.size
        } else {
            Random.nextInt(fileList.size)
        }
        buildNotification(PlaybackStatus.PLAYING)
        playSong(fileList[current].filePath)
    }

    fun previous() {
        current = if(!isRandom) {
            (current - 1 + fileList.size) % fileList.size
        } else {
            Random.nextInt(fileList.size)
        }
        buildNotification(PlaybackStatus.PLAYING)
        playSong(fileList[current].filePath as String)
    }

    fun seekTo(value: Int) {
        mediaPlayer.seekTo(min(value,mediaPlayer.duration - 500))
    }

    fun loop(b : Boolean) {
        isLoop = b
    }

    fun random(b: Boolean) {
        isRandom = b
    }

    fun currentState(): HashMap<String, Any> {
        return hashMapOf(
            "filePath" to currentFilePath,
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
        Log.d("TanKiem", "onDestroy service")
        if(mediaPlayer.isPlaying)
            mediaPlayer.stop()
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