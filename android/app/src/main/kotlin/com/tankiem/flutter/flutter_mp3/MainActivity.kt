package com.tankiem.flutter.flutter_mp3

import android.Manifest
import android.app.ActivityManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.*
import android.util.Log
import androidx.annotation.NonNull
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import java.io.File
import androidx.core.app.ActivityCompat

import android.content.pm.PackageManager
import android.graphics.Color
import android.media.MediaMetadataRetriever
import android.os.Build
import android.os.IBinder
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.tankiem.flutter.flutter_mp3.Mp3Service.Companion.ACTION_PAUSE
import com.tankiem.flutter.flutter_mp3.Mp3Service.Companion.ACTION_PLAY
import com.tankiem.flutter.flutter_mp3.Mp3Service.Companion.ACTION_SONG_CHANGE
import com.tankiem.flutter.flutter_mp3.Mp3Service.Companion.ACTION_STOP
import io.flutter.plugin.common.EventChannel
import kotlinx.coroutines.*


class MainActivity : FlutterActivity() {
    companion object {
        private const val METHOD_CHANNEL = "com.tankiem.flutter.flutter_mp3/method_channel"
        private const val EVENT_CHANNEL = "com.tankiem.flutter.flutter_mp3/event_channel"
        private const val REQUEST_EXTERNAL_STORAGE = 1
        private val PERMISSIONS_STORAGE = arrayOf(
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        )
    }

    private var service: Mp3Service? = null
    private var eventSink: EventChannel.EventSink? = null
    var fileList: MutableList<HashMap<String, Any?>> = mutableListOf()

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                Mp3Service.CHANNEL_ID,
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

    override fun configureFlutterEngine(@NonNull flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        Log.d("TanKiem","in main " + Thread.currentThread())
        createNotificationChannel()
        if (isServiceRunning(Mp3Service::class.java)) {
            Log.d("TanKiem", "Service is running")
            val intent = Intent(this, Mp3Service::class.java)
            bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        }
        val intentFilter1 = IntentFilter(ACTION_SONG_CHANGE)
        intentFilter1.addAction(ACTION_STOP)
        intentFilter1.addAction(ACTION_PLAY)
        intentFilter1.addAction(ACTION_PAUSE)
        LocalBroadcastManager.getInstance(this).registerReceiver(localBroadcastReceiver,intentFilter1)
        MethodChannel(
            flutterEngine.dartExecutor.binaryMessenger,
            METHOD_CHANNEL
        ).setMethodCallHandler { call, result ->
            if (call.method == "checkPermission") {
                val permission = ActivityCompat.checkSelfPermission(
                    activity,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                )
                result.success(permission == PackageManager.PERMISSION_GRANTED)
            } else if (call.method == "promptPermission") {
                ActivityCompat.requestPermissions(
                    activity,
                    PERMISSIONS_STORAGE,
                    REQUEST_EXTERNAL_STORAGE
                )
            } else if (call.method == "getSongs") {
                fileList.clear()
//                fileList.addAll(getPlayList("/storage/self/primary/Zing MP3"))
//                fileList.addAll(getPlayList("/storage/self/primary/Download"))
//                fileList.addAll(getPlayList("/storage/self/primary"))
                CoroutineScope(Dispatchers.IO).launch {
                    fileList.addAll(getPlayList("/storage/self/primary"))
                    runOnUiThread {
                        eventSink?.success(hashMapOf(
                            "action" to "getSongs",
                            "data" to fileList
                        ))
                    }
                }
            } else if (call.method == "currentState") {
                if (isServiceRunning(Mp3Service::class.java)) {
                    val res = service?.currentState()
                    result.success(res)
                } else {
                    result.success(null)
                }
            } else if (call.method == "playSong") {
                val filePath: String = call.argument<String>("filePath")!!
                if (!isServiceRunning(Mp3Service::class.java)) {
                    Log.d("TanKiem", "Service is not running")
                    CoroutineScope(Dispatchers.IO).launch {
                        val intent = Intent(this@MainActivity, Mp3Service::class.java)
                        intent.putExtra("filePath", filePath)
                        ContextCompat.startForegroundService(this@MainActivity, intent)
                        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
                    }
                } else {
                    Log.d("TanKiem", "Service is running")
                    service?.playSong(filePath)
                }
                result.success(null)
            } else if (call.method == "pause") {
                service?.pause()
                result.success(null)
            } else if (call.method == "play") {
                service?.play()
                result.success(null)
            } else if (call.method == "next") {
                service?.next()
                result.success(null)
            } else if (call.method == "previous") {
                service?.previous()
                result.success(null)
            } else if (call.method == "seekTo") {
                service?.seekTo(call.argument<Int>("seekTo")!!)
                result.success(null)
            } else if (call.method == "dispose") {
                stopService(Intent(this, Mp3Service::class.java))
                result.success(null)
            } else if (call.method == "loop") {
                service?.loop(call.arguments as Boolean)
                result.success(null)
            } else if (call.method == "random") {
                service?.random(call.arguments as Boolean)
                result.success(null)
            }
        }

        EventChannel(flutterEngine.dartExecutor.binaryMessenger, EVENT_CHANNEL)
            .setStreamHandler(object: EventChannel.StreamHandler {
                override fun onListen(arguments: Any?, events: EventChannel.EventSink?) {
                    eventSink = events
                }

                override fun onCancel(arguments: Any?) {
                    eventSink = null
                }

            })
    }

    override fun onDestroy() {
        service?.dispose()
        unbindService(serviceConnection)
        LocalBroadcastManager.getInstance(this).unregisterReceiver(localBroadcastReceiver)
        super.onDestroy()
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(p0: ComponentName?, p1: IBinder?) {
            val binder = p1 as Mp3Service.Mp3Binder
            service = binder.getService()
            val data = arrayListOf<Song>()
            fileList.forEach {
                data.add(Song(
                    it["filePath"] as String,
                    it["fileName"] as String,
                    it["image"] as ByteArray?,
                    it["artist"] as String?,
                    it["album"] as String?,
                    it["title"] as String?,
                ))
            }
            service!!.bindData(data)
        }

        override fun onServiceDisconnected(p0: ComponentName?) {
//            service = null
        }

    }
    private fun isServiceRunning(mClass: Class<Mp3Service>): Boolean {
        val manager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        for (service: ActivityManager.RunningServiceInfo in manager.getRunningServices(Int.MAX_VALUE)) {
            if (mClass.name.equals(service.service.className)) {
                return true
            }
        }
        return false
    }

    private fun getPlayList(rootPath: String): MutableList<HashMap<String, Any?>> {
        val fileList: MutableList<HashMap<String, Any?>> = mutableListOf()
        return try {
            val root = File(rootPath)
            if (root.isDirectory) {
                val files = root.listFiles()
                if (files != null) {
                    for (file in files) {
                        if (file.isDirectory) {
                            fileList.addAll(getPlayList(file.absolutePath))
                        } else if (file.name.endsWith(".mp3")) {
                            val song: HashMap<String, Any?> = hashMapOf()
                            song["filePath"] = file.absolutePath
                            song["fileName"] = file.name
                            val mmr = MediaMetadataRetriever()
                            mmr.setDataSource(file.absolutePath)
                            song["duration"] =
                                mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                            song["image"] = mmr.embeddedPicture
                            song["artist"] = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                            song["album"] = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
                            song["title"] = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                            fileList.add(song)
                        }
                    }
                }
            }
            fileList
        } catch (e: Exception) {
            fileList
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        if (requestCode == REQUEST_EXTERNAL_STORAGE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                this.runOnUiThread {
                    eventSink?.success(
                        hashMapOf(
                            "action" to "promptPermission",
                            "data" to true
                        )
                    )
                }
            } else {
                this.runOnUiThread {
                    eventSink?.success(
                        hashMapOf(
                            "action" to "promptPermission",
                            "data" to false
                        )
                    )
                }
            }
        }
    }

    private val localBroadcastReceiver = object:BroadcastReceiver(){
        override fun onReceive(p0: Context?, p1: Intent?) {
            Log.d("TanKiem","BroadCastReceiver in main ${p1?.action}")
            runOnUiThread {
                when(p1?.action){
                    ACTION_SONG_CHANGE -> {
                        eventSink?.success(hashMapOf(
                            "action" to "songChanged",
                            "data" to p1.extras?.getString("filePath")
                        ))
                    }
                    ACTION_PAUSE -> {
                        eventSink?.success(hashMapOf(
                            "action" to "pause",
                            "data" to true
                        ))
                    }
                    ACTION_PLAY -> {
                        eventSink?.success(hashMapOf(
                            "action" to "pause",
                            "data" to false
                        ))
                    }
                    ACTION_STOP -> {
                        eventSink?.success(hashMapOf(
                            "action" to "stop"
                        ))
                    }
                }
            }
        }

    }
}
