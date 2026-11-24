package io.github.lenkr0.verba.ocr

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.IntentCompat
import androidx.core.graphics.createBitmap
import io.github.lenkr0.verba.R

/**
 * "Read screen": a quick settings tile that captures the screen once and opens
 * it in the text recognition screen. Android asks for consent every time; a
 * foreground service runs only while the single frame is taken. The frame
 * stays in memory – it is never written to storage.
 */
internal object ScreenCaptures {
    @Volatile
    private var latest: Bitmap? = null

    fun put(bitmap: Bitmap) {
        latest = bitmap
    }

    /** The capture, once. */
    fun take(): Bitmap? = latest.also { latest = null }
}

class ReadScreenTileService : TileService() {
    override fun onStartListening() {
        qsTile?.apply {
            state = Tile.STATE_INACTIVE
            updateTile()
        }
    }

    // Android 13 only has the Intent variant
    @SuppressLint("StartActivityAndCollapseDeprecated")
    override fun onClick() {
        val intent = Intent(this, ScreenCaptureActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE))
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}

/** Invisible: asks Android for the screen capture consent, hands it to the service, and is gone. */
class ScreenCaptureActivity : ComponentActivity() {
    private val consent = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val data = result.data
        if (result.resultCode == RESULT_OK && data != null) {
            startForegroundService(
                Intent(this, ScreenCaptureService::class.java)
                    .putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, result.resultCode)
                    .putExtra(ScreenCaptureService.EXTRA_RESULT_DATA, data),
            )
        }
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            consent.launch(getSystemService(MediaProjectionManager::class.java).createScreenCaptureIntent())
        }
    }
}

/** Takes one frame of the screen, then stops itself and the projection. */
class ScreenCaptureService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var projection: MediaProjection? = null
    private var display: VirtualDisplay? = null
    private var reader: ImageReader? = null
    private var done = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        val data = intent?.let { IntentCompat.getParcelableExtra(it, EXTRA_RESULT_DATA, Intent::class.java) }
        val code = intent?.getIntExtra(EXTRA_RESULT_CODE, 0) ?: 0
        if (data == null || projection != null) {
            if (projection == null) finish(null)
            return START_NOT_STICKY
        }
        val mp = getSystemService(MediaProjectionManager::class.java).getMediaProjection(code, data)
        if (mp == null) {
            finish(null)
            return START_NOT_STICKY
        }
        projection = mp
        mp.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() = finish(null)
        }, handler)
        // The consent dialog and the quick settings panel need a moment to leave the screen
        handler.postDelayed(::startCapture, SETTLE_MS)
        handler.postDelayed({ finish(null) }, TIMEOUT_MS)
        return START_NOT_STICKY
    }

    private fun startCapture() {
        val mp = projection ?: return
        val bounds = getSystemService(WindowManager::class.java).maximumWindowMetrics.bounds
        val width = bounds.width()
        val height = bounds.height()
        val imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        reader = imageReader
        imageReader.setOnImageAvailableListener({ r ->
            val image = r.acquireLatestImage() ?: return@setOnImageAvailableListener
            val bitmap = try {
                val plane = image.planes[0]
                val rowPixels = plane.rowStride / plane.pixelStride
                val full = createBitmap(rowPixels, height)
                full.copyPixelsFromBuffer(plane.buffer)
                if (rowPixels == width) full else Bitmap.createBitmap(full, 0, 0, width, height)
            } finally {
                image.close()
            }
            finish(bitmap)
        }, handler)
        display = mp.createVirtualDisplay(
            "Verba read screen", width, height, resources.configuration.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, imageReader.surface, null, handler,
        )
    }

    private fun finish(bitmap: Bitmap?) {
        if (done) return
        done = true
        handler.removeCallbacksAndMessages(null)
        reader?.setOnImageAvailableListener(null, null)
        display?.release()
        projection?.stop()
        reader?.close()
        if (bitmap != null) {
            ScreenCaptures.put(bitmap)
            startActivity(
                Intent(this, OcrActivity::class.java)
                    .setAction(OcrActivity.ACTION_READ_SCREEN)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        } else {
            Toast.makeText(this, R.string.capture_failed, Toast.LENGTH_SHORT).show()
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun notification(): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, getString(R.string.capture_channel), NotificationManager.IMPORTANCE_LOW),
        )
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_document_scanner)
            .setContentTitle(getString(R.string.capture_notification))
            .build()
    }

    companion object {
        const val EXTRA_RESULT_CODE = "io.github.lenkr0.verba.RESULT_CODE"
        const val EXTRA_RESULT_DATA = "io.github.lenkr0.verba.RESULT_DATA"
        private const val CHANNEL = "screen_capture"
        private const val NOTIFICATION_ID = 1
        private const val SETTLE_MS = 400L
        private const val TIMEOUT_MS = 5_000L
    }
}
