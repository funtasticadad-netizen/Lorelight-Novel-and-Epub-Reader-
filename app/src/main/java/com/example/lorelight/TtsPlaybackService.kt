package com.example.lorelight

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.IBinder
import android.view.KeyEvent
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TtsPlaybackService : Service() {

    companion object {
        const val TAG = "TtsPlaybackService"
        const val NOTIFICATION_ID = 2026
        const val CHANNEL_ID = "tts_playback_channel"

        const val ACTION_PLAY = "com.example.action.PLAY"
        const val ACTION_PAUSE = "com.example.action.PAUSE"
        const val ACTION_PREV = "com.example.action.PREV"
        const val ACTION_NEXT = "com.example.action.NEXT"
        const val ACTION_STOP = "com.example.action.STOP"
        const val ACTION_UPDATE = "com.example.action.UPDATE"

        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_AUTHOR = "extra_author"
        const val EXTRA_PLAYING = "extra_playing"
        const val EXTRA_COVER = "extra_cover"
        const val EXTRA_TEXT = "extra_text"

        var currentCoverBase64: String? = null

        var onPlayPauseAction: (() -> Unit)? = null
        var onPlayAction: (() -> Unit)? = null
        var onPauseAction: (() -> Unit)? = null
        var onNextAction: (() -> Unit)? = null
        var onPrevAction: (() -> Unit)? = null
        var onStopAction: (() -> Unit)? = null
    }

    private var mediaSession: MediaSession? = null
    private var wakeLock: android.os.PowerManager.WakeLock? = null

    private var lastLoadedCoverUrl: String? = null
    private var urlCoverBitmap: Bitmap? = null

    private fun acquireWakeLock() {
        if (wakeLock == null) {
            try {
                val powerManager = getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
                wakeLock = powerManager.newWakeLock(android.os.PowerManager.PARTIAL_WAKE_LOCK, "Lorelight::TtsWakeLock").apply {
                    acquire() // hold indefinitely until released
                }
                Log.d(TAG, "WakeLock acquired successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to acquire wake lock", e)
            }
        }
    }

    private fun releaseWakeLock() {
        try {
            wakeLock?.let {
                if (it.isHeld) {
                    it.release()
                    Log.d(TAG, "WakeLock released successfully")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to release wake lock", e)
        }
        wakeLock = null
    }

    private fun scaleDownBitmap(bitmap: Bitmap, maxDimension: Int): Bitmap {
        if (bitmap.width <= maxDimension && bitmap.height <= maxDimension) {
            return bitmap
        }
        val aspectRatio = bitmap.width.toFloat() / bitmap.height.toFloat()
        val width: Int
        val height: Int
        if (aspectRatio > 1) {
            width = maxDimension
            height = (maxDimension / aspectRatio).toInt()
        } else {
            height = maxDimension
            width = (maxDimension * aspectRatio).toInt()
        }
        return Bitmap.createScaledBitmap(bitmap, width, height, true)
    }

    private fun loadCoverBitmap(context: Context, coverStr: String?, onLoaded: (Bitmap?) -> Unit) {
        if (coverStr.isNullOrEmpty()) {
            onLoaded(null)
            return
        }

        if (coverStr.startsWith("http://") || coverStr.startsWith("https://")) {
            if (coverStr == lastLoadedCoverUrl && urlCoverBitmap != null) {
                onLoaded(urlCoverBitmap)
                return
            }
            CoroutineScope(Dispatchers.IO).launch {
                var bitmap: Bitmap? = null
                try {
                    val loader = coil.Coil.imageLoader(context)
                    val request = coil.request.ImageRequest.Builder(context)
                        .data(coverStr)
                        .allowHardware(false)
                        .build()
                    val result = loader.execute(request)
                    bitmap = (result.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to load network cover via Coil: ${e.message}")
                    try {
                        val url = java.net.URL(coverStr)
                        val connection = url.openConnection() as java.net.HttpURLConnection
                        connection.doInput = true
                        connection.connect()
                        val input = connection.inputStream
                        bitmap = BitmapFactory.decodeStream(input)
                    } catch (ex: Exception) {
                        Log.e(TAG, "Fallback manual URL loader also failed: ${ex.message}")
                    }
                }
                withContext(Dispatchers.Main) {
                    if (bitmap != null) {
                        lastLoadedCoverUrl = coverStr
                        urlCoverBitmap = scaleDownBitmap(bitmap, 512)
                    }
                    onLoaded(urlCoverBitmap)
                }
            }
        } else {
            onLoaded(rememberDecodedBitmap(coverStr))
        }
    }

    private val noisyReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (android.media.AudioManager.ACTION_AUDIO_BECOMING_NOISY == intent?.action) {
                Log.d(TAG, "Headphones disconnected! Pausing playback via noisyReceiver.")
                onPauseAction?.invoke() ?: onPlayPauseAction?.invoke()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        try {
            // Guarantee that TtsPlaybackManager is initialized, even when service restarts without activity alive
            TtsPlaybackManager.init(this)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize TtsPlaybackManager in service", e)
        }
        createNotificationChannel()
        setupMediaSession()
        try {
            registerReceiver(noisyReceiver, android.content.IntentFilter(android.media.AudioManager.ACTION_AUDIO_BECOMING_NOISY))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register ACTION_AUDIO_BECOMING_NOISY receiver", e)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Audiobook Reading Controls",
                NotificationManager.IMPORTANCE_LOW
            ).apply { setShowBadge(false) }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun setupMediaSession() {
        mediaSession = MediaSession(this, "EpubTtsSession").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() {
                    Log.d(TAG, "MediaSession: onPlay()")
                    onPlayAction?.invoke() ?: onPlayPauseAction?.invoke()
                }

                override fun onPause() {
                    Log.d(TAG, "MediaSession: onPause()")
                    onPauseAction?.invoke() ?: onPlayPauseAction?.invoke()
                }

                override fun onSkipToNext() {
                    Log.d(TAG, "MediaSession: onSkipToNext()")
                    onNextAction?.invoke()
                }

                override fun onSkipToPrevious() {
                    Log.d(TAG, "MediaSession: onSkipToPrevious()")
                    onPrevAction?.invoke()
                }

                override fun onStop() {
                    Log.d(TAG, "MediaSession: onStop()")
                    onStopAction?.invoke()
                    stopSelf()
                }

                override fun onMediaButtonEvent(mediaButtonIntent: Intent): Boolean {
                    Log.d(TAG, "MediaSession: onMediaButtonEvent Action=${mediaButtonIntent.action}")
                    return super.onMediaButtonEvent(mediaButtonIntent)
                }
            })
            isActive = true
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        when (intent.action) {
            ACTION_PLAY -> {
                Log.d(TAG, "Notification Action: ACTION_PLAY")
                onPlayAction?.invoke()
            }
            ACTION_PAUSE -> {
                Log.d(TAG, "Notification Action: ACTION_PAUSE")
                onPauseAction?.invoke()
            }
            ACTION_PREV -> onPrevAction?.invoke()
            ACTION_NEXT -> onNextAction?.invoke()
            ACTION_STOP -> {
                onStopAction?.invoke()
                stopPlayService()
                return START_NOT_STICKY
            }
            ACTION_UPDATE -> {
                val title = intent.getStringExtra(EXTRA_TITLE) ?: "Reading"
                val author = intent.getStringExtra(EXTRA_AUTHOR) ?: ""
                val isPlaying = intent.getBooleanExtra(EXTRA_PLAYING, false)
                val coverBase64 = currentCoverBase64
                val text = intent.getStringExtra(EXTRA_TEXT) ?: ""
                
                // Immediately update to satisfy startForeground requirement within 5 secs
                updatePlaybackStateAndNotification(title, author, isPlaying, null, text)
                
                // Asynchronously load the real cover and update again if it changed
                loadCoverBitmap(this, coverBase64) { coverBitmap ->
                    if (coverBitmap != null) {
                        updatePlaybackStateAndNotification(title, author, isPlaying, coverBitmap, text)
                    }
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun updatePlaybackStateAndNotification(title: String, author: String, isPlaying: Boolean, coverBitmap: Bitmap?, text: String) {
        val session = mediaSession ?: return
        session.isActive = true
        @Suppress("DEPRECATION")
        session.setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS or MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS)
        val finalCoverBitmap = coverBitmap ?: generateProceduralPlaceholder(title)

        val metaBuilder = MediaMetadata.Builder()
            .putString(MediaMetadata.METADATA_KEY_TITLE, title)
            .putString(MediaMetadata.METADATA_KEY_ARTIST, author)
            .putString(MediaMetadata.METADATA_KEY_ALBUM, "Audiobook Novel")
            .putString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE, title)
            .putString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE, text.take(100))
        metaBuilder.putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, finalCoverBitmap)
        metaBuilder.putBitmap(MediaMetadata.METADATA_KEY_ART, finalCoverBitmap)
        session.setMetadata(metaBuilder.build())

        val stateBuilder = PlaybackState.Builder()
            .setActions(
                PlaybackState.ACTION_PLAY or
                PlaybackState.ACTION_PAUSE or
                PlaybackState.ACTION_PLAY_PAUSE or
                PlaybackState.ACTION_SKIP_TO_NEXT or
                PlaybackState.ACTION_SKIP_TO_PREVIOUS or
                PlaybackState.ACTION_STOP
            )
            .setState(if (isPlaying) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED, 0L, 1.0f)
        session.setPlaybackState(stateBuilder.build())

        val notification = buildMediaNotification(title, author, text, isPlaying, finalCoverBitmap)

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Fatal error calling startForeground, attempting standard fallback...", e)
            try {
                startForeground(NOTIFICATION_ID, notification)
            } catch (ex: Exception) {
                Log.e(TAG, "All startForeground attempts failed", ex)
            }
        }

        if (isPlaying) {
            acquireWakeLock()
        } else {
            releaseWakeLock()
        }
    }

    private var cachedCoverBase64: String? = null
    private var cachedBitmap: Bitmap? = null

    private fun rememberDecodedBitmap(base64: String?): Bitmap? {
        if (base64.isNullOrEmpty()) return null
        val cleanBase64 = if (base64.contains("base64,")) {
            base64.substringAfter("base64,")
        } else {
            base64
        }.trim().replace("\n", "").replace("\r", "")

        if (cleanBase64 == cachedCoverBase64 && cachedBitmap != null) return cachedBitmap
        return try {
            val bytes = Base64.decode(cleanBase64, Base64.DEFAULT)
            var bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            if (bmp != null) {
                bmp = scaleDownBitmap(bmp, 512)
            }
            cachedCoverBase64 = cleanBase64
            cachedBitmap = bmp
            bmp
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun generateProceduralPlaceholder(title: String): Bitmap {
        val width = 200
        val height = 280
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        
        val colors = listOf(0xFF6366F1.toInt(), 0xFF10B981.toInt(), 0xFF06B6D4.toInt(), 0xFFF59E0B.toInt(), 0xFFEC4899.toInt())
        val index = kotlin.math.abs(title.hashCode()) % colors.size
        val bgColor = colors[index]
        
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        val shader = android.graphics.LinearGradient(0f, 0f, width.toFloat(), height.toFloat(), bgColor, 0xFFF43F5E.toInt(), android.graphics.Shader.TileMode.CLAMP)
        paint.shader = shader
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        
        paint.shader = null
        paint.color = 0x33FFFFFF
        canvas.drawRect(0f, 0f, 10f, height.toFloat(), paint)
        
        paint.color = android.graphics.Color.WHITE
        paint.textSize = 90f
        paint.typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        paint.textAlign = android.graphics.Paint.Align.CENTER
        
        val displayChar = if (title.isNotEmpty()) title.take(1).uppercase() else "B"
        val textBounds = android.graphics.Rect()
        paint.getTextBounds(displayChar, 0, displayChar.length, textBounds)
        val x = width / 2.0f
        val y = height / 2.0f - textBounds.exactCenterY()
        canvas.drawText(displayChar, x, y, paint)
        
        paint.textSize = 24f
        paint.color = 0xB2FFFFFF.toInt()
        canvas.drawText("EPUB", x, height - 30f, paint)
        
        return bitmap
    }

    private fun buildMediaNotification(title: String, author: String, text: String, isPlaying: Boolean, coverBitmap: Bitmap?): Notification {
        val session = mediaSession ?: throw IllegalStateException("MediaSession must not be null")
        val openAppIntent = Intent(this, MainActivity::class.java)
        val openAppPendingIntent = PendingIntent.getActivity(this, 0, openAppIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        val playPendingIntent = PendingIntent.getService(this, 1, Intent(this, TtsPlaybackService::class.java).apply { action = ACTION_PLAY }, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val pausePendingIntent = PendingIntent.getService(this, 2, Intent(this, TtsPlaybackService::class.java).apply { action = ACTION_PAUSE }, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val prevPendingIntent = PendingIntent.getService(this, 3, Intent(this, TtsPlaybackService::class.java).apply { action = ACTION_PREV }, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val nextPendingIntent = PendingIntent.getService(this, 4, Intent(this, TtsPlaybackService::class.java).apply { action = ACTION_NEXT }, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val stopPendingIntent = PendingIntent.getService(this, 5, Intent(this, TtsPlaybackService::class.java).apply { action = ACTION_STOP }, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        val activePlayAction = if (isPlaying) Notification.Action.Builder(android.R.drawable.ic_media_pause, "Pause", pausePendingIntent).build()
        else Notification.Action.Builder(android.R.drawable.ic_media_play, "Play", playPendingIntent).build()

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) Notification.Builder(this, CHANNEL_ID) else @Suppress("DEPRECATION") Notification.Builder(this)

        builder.setContentTitle(title)
            .setContentText(if (text.isNotEmpty()) text else "Narrating Audiobook")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(openAppPendingIntent)
            .setDeleteIntent(stopPendingIntent)
            .setVisibility(Notification.VISIBILITY_PUBLIC)

        if (coverBitmap != null) builder.setLargeIcon(coverBitmap)

        builder.addAction(Notification.Action.Builder(android.R.drawable.ic_media_previous, "Previous", prevPendingIntent).build())
        builder.addAction(activePlayAction)
        builder.addAction(Notification.Action.Builder(android.R.drawable.ic_media_next, "Next", nextPendingIntent).build())

        builder.setStyle(Notification.MediaStyle().setMediaSession(session.sessionToken).setShowActionsInCompactView(0, 1, 2))
        return builder.build()
    }

    private fun stopPlayService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) stopForeground(STOP_FOREGROUND_REMOVE)
        else @Suppress("DEPRECATION") stopForeground(true)
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(noisyReceiver)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to unregister ACTION_AUDIO_BECOMING_NOISY receiver", e)
        }
        releaseWakeLock()
        mediaSession?.isActive = false
        mediaSession?.release()
        mediaSession = null

        // Clear companion callbacks to avoid leaked references or execution of dead handlers
        onPlayPauseAction = null
        onPlayAction = null
        onPauseAction = null
        onNextAction = null
        onPrevAction = null
        onStopAction = null
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        // Check if playback is currently active
        if (com.example.lorelight.TtsPlaybackManager.isPlaying.value) {
            Log.d(TAG, "onTaskRemoved: active TTS playback will continue in the background")
        } else {
            Log.d(TAG, "onTaskRemoved: idle, stopping foreground service")
            stopPlayService()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
