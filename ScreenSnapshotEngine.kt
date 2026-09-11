package com.example.foldmorph

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.util.DisplayMetrics
import android.view.WindowManager

class ScreenSnapshotEngine(
    private val context: Context,
    private val mediaProjection: MediaProjection
) {
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var imageReader: ImageReader? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var latestBitmap: Bitmap? = null

    fun startStreaming() {
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getRealMetrics(metrics)

        imageReader = ImageReader.newInstance(
            metrics.widthPixels,
            metrics.heightPixels,
            PixelFormat.RGBA_8888,
            2
        )

        virtualDisplay = mediaProjection.createVirtualDisplay(
            "FoldCaptureStream",
            metrics.widthPixels,
            metrics.heightPixels,
            metrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader!!.surface,
            null,
            null
        )
    }

    fun freezeCurrentFrame(): Bitmap? {
        val reader = imageReader ?: return latestBitmap
        val image = reader.acquireLatestImage() ?: return latestBitmap

        val plane = image.planes[0]
        val buffer = plane.buffer
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        val rowPadding = rowStride - pixelStride * image.width

        val bitmap = Bitmap.createBitmap(
            image.width + rowPadding / pixelStride,
            image.height,
            Bitmap.Config.ARGB_8888
        )
        bitmap.copyPixelsFromBuffer(buffer)
        image.close()

        latestBitmap = if (rowPadding == 0) {
            bitmap
        } else {
            Bitmap.createBitmap(bitmap, 0, 0, image.width, image.height)
        }
        return latestBitmap
    }

    fun stop() {
        virtualDisplay?.release()
        imageReader?.close()
        virtualDisplay = null
        imageReader = null
    }
}
