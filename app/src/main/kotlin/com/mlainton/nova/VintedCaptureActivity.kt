package com.mlainton.nova

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class VintedCaptureActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "VintedCapture"
        private const val CAMERA_PERMISSION_REQUEST = 2101
        private const val MAX_PHOTOS = 6
    }

    private var platform: String = ""
    private val capturedPhotos: MutableList<File> = mutableListOf()

    private lateinit var previewView: PreviewView
    private lateinit var captureButton: Button
    private lateinit var doneButton: Button
    private lateinit var photoCounter: TextView

    private var imageCapture: ImageCapture? = null
    private lateinit var cameraExecutor: ExecutorService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val platformExtra = intent.getStringExtra("platform")
        if (platformExtra.isNullOrBlank()) {
            Log.w(TAG, "No platform extra supplied — finishing")
            finish()
            return
        }
        platform = platformExtra
        Log.i(TAG, "Launched for platform: $platform")

        setContentView(R.layout.activity_vinted_capture)

        previewView = findViewById(R.id.previewView)
        captureButton = findViewById(R.id.captureButton)
        doneButton = findViewById(R.id.doneButton)
        photoCounter = findViewById(R.id.photoCounter)

        captureButton.setOnClickListener { takePhoto() }

        cameraExecutor = Executors.newSingleThreadExecutor()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA),
                CAMERA_PERMISSION_REQUEST
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_PERMISSION_REQUEST) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startCamera()
            } else {
                Toast.makeText(this, "Camera permission is required.", Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()

                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

                imageCapture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .build()

                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture)
                Log.i(TAG, "Camera bound")
            } catch (e: Exception) {
                Log.e(TAG, "Camera bind failed: ${e.message}", e)
                Toast.makeText(this, "Failed to start camera.", Toast.LENGTH_LONG).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun takePhoto() {
        val capture = imageCapture ?: run {
            Log.w(TAG, "takePhoto called before imageCapture ready")
            return
        }

        if (capturedPhotos.size >= MAX_PHOTOS) {
            Toast.makeText(this, "Maximum $MAX_PHOTOS photos reached.", Toast.LENGTH_SHORT).show()
            return
        }

        val vintedDir = File(cacheDir, "vinted").apply { mkdirs() }
        val photoFile = File(vintedDir, "IMG_${System.currentTimeMillis()}.jpg")

        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        capture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) {
                    Log.e(TAG, "Photo capture failed: ${exc.message}", exc)
                    Toast.makeText(this@VintedCaptureActivity,
                        "Capture failed.", Toast.LENGTH_SHORT).show()
                }

                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    capturedPhotos.add(photoFile)
                    Log.i(TAG, "Photo saved: ${photoFile.absolutePath} (${capturedPhotos.size}/$MAX_PHOTOS)")
                    updateCounter()
                }
            }
        )
    }

    private fun updateCounter() {
        photoCounter.text = "Photos: ${capturedPhotos.size} / $MAX_PHOTOS"
        doneButton.isEnabled = capturedPhotos.isNotEmpty()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::cameraExecutor.isInitialized) {
            cameraExecutor.shutdown()
        }
    }
}
