package com.example.foldmorph

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.display.DisplayManager
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.os.IBinder
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.Toast

class MainActivity : Activity(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var hingeSensor: Sensor? = null
    private var displayManager: DisplayManager? = null

    private var captureService: CaptureService? = null
    private var isServiceBound = false

    private lateinit var innerShaderView: FoldShaderView
    private var coverShaderView: FoldShaderView? = null
    private var coverPresentation: CoverPresentation? = null

    private var isTransitionActive = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as CaptureService.LocalBinder
            captureService = binder.getService()
            isServiceBound = true
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            isServiceBound = false
            captureService = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val rootLayout = FrameLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        innerShaderView = FoldShaderView(this).apply {
            setIsCover(false)
            visibility = View.GONE
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        val startButton = Button(this).apply {
            text = "Enable Fold Morph Pipeline"
            setOnClickListener { launchCaptureIntent() }
        }

        rootLayout.addView(startButton)
        rootLayout.addView(innerShaderView)
        setContentView(rootLayout)

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        hingeSensor = sensorManager.getDefaultSensor(Sensor.TYPE_HINGE_ANGLE)
        displayManager = getSystemService(Context.DISPLAY_SERVICE) as DisplayManager

        bindCaptureService()
        setupCoverPresentation()
    }

    private fun bindCaptureService() {
        val intent = Intent(this, CaptureService::class.java)
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    private fun launchCaptureIntent() {
        val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        startActivityForResult(projectionManager.createScreenCaptureIntent(), REQUEST_CODE_CAPTURE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_CAPTURE && resultCode == RESULT_OK && data != null) {
            val intent = Intent(this, CaptureService::class.java)
            startForegroundService(intent)
            captureService?.initializeProjection(resultCode, data)
            registerHingeSensor()
            Toast.makeText(this, "Capture initialized. Fold the device.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupCoverPresentation() {
        val presentationDisplays = displayManager?.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)
        if (!presentationDisplays.isNullOrEmpty()) {
            val coverDisplay = presentationDisplays[0]
            coverShaderView = FoldShaderView(this).apply {
                setIsCover(true)
            }
            coverPresentation = CoverPresentation(coverDisplay, this, coverShaderView!!)
            coverPresentation?.show()
        }
    }

    private fun registerHingeSensor() {
        hingeSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_FASTEST)
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        val angle = event.values[0]
        val inMotion = angle in 5.0f..175.0f

        if (inMotion && !isTransitionActive) {
            // Latch current screen buffer instantly
            val frame = captureService?.snapshotEngine?.freezeCurrentFrame()
            frame?.let {
                innerShaderView.setTexture(it)
                coverShaderView?.setTexture(it)
                innerShaderView.visibility = View.VISIBLE
                coverShaderView?.visibility = View.VISIBLE
                isTransitionActive = true
            }
        }

        if (isTransitionActive) {
            val progress = (angle / 180f).coerceIn(0f, 1f)
            innerShaderView.updateProgress(progress)
            coverShaderView?.updateProgress(progress)

            if (!inMotion) {
                isTransitionActive = false
                innerShaderView.visibility = View.GONE
                coverShaderView?.visibility = View.GONE
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onDestroy() {
        super.onDestroy()
        sensorManager.unregisterListener(this)
        coverPresentation?.dismiss()
        if (isServiceBound) {
            unbindService(serviceConnection)
        }
    }

    companion object {
        private const val REQUEST_CODE_CAPTURE = 1001
    }
}
