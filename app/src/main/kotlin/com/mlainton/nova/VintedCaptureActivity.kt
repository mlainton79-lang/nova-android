package com.mlainton.nova

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.AspectRatio
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import java.io.File
import java.util.UUID
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
    private lateinit var flashButton: Button
    private lateinit var thumbnailContainer: HorizontalScrollView
    private lateinit var thumbnailRow: LinearLayout

    private var imageCapture: ImageCapture? = null
    private var camera: Camera? = null
    private var flashMode: Int = ImageCapture.FLASH_MODE_OFF
    private var isCapturing: Boolean = false
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
        flashButton = findViewById(R.id.flashButton)
        thumbnailContainer = findViewById(R.id.thumbnailContainer)
        thumbnailRow = findViewById(R.id.thumbnailRow)

        captureButton.setOnClickListener { takePhoto() }
        flashButton.setOnClickListener { cycleFlashMode() }
        doneButton.setOnClickListener { onDoneClicked() }

        val scaleDetector = ScaleGestureDetector(this, scaleListener)
        @SuppressLint("ClickableViewAccessibility")
        previewView.setOnTouchListener { _, event: MotionEvent ->
            scaleDetector.onTouchEvent(event)
            true
        }

        if (savedInstanceState != null) {
            val restoredPaths = savedInstanceState.getStringArrayList("captured_photos")
            if (restoredPaths != null) {
                restoredPaths.forEach { path ->
                    val file = File(path)
                    if (file.exists()) {
                        capturedPhotos.add(file)
                    }
                }
                rebuildThumbnails()
            }
            flashMode = savedInstanceState.getInt("flash_mode", ImageCapture.FLASH_MODE_OFF)
            flashButton.text = when (flashMode) {
                ImageCapture.FLASH_MODE_ON -> "⚡ On"
                ImageCapture.FLASH_MODE_AUTO -> "⚡ Auto"
                else -> "⚡ Off"
            }
        }
        refreshControls()

        cameraExecutor = Executors.newSingleThreadExecutor()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA),
                CAMERA_PERMISSION_REQUEST
            )
        }
    }

    override fun onResume() {
        super.onResume()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED) {
            startCamera()
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
                if (!lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                    return@addListener
                }
                val cameraProvider = cameraProviderFuture.get()

                val preview = Preview.Builder()
                    .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                    .build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

                imageCapture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                    .setTargetRotation(previewView.display.rotation)
                    .build()

                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                cameraProvider.unbindAll()
                camera = cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture)
                imageCapture?.flashMode = flashMode
                Log.i(TAG, "Camera bound")
            } catch (e: Exception) {
                Log.e(TAG, "Camera bind failed: ${e.message}", e)
                Toast.makeText(this, "Failed to start camera.", Toast.LENGTH_LONG).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun takePhoto() {
        if (isCapturing) return

        val capture = imageCapture ?: run {
            Log.w(TAG, "takePhoto called before imageCapture ready")
            Toast.makeText(this, "Camera not ready", Toast.LENGTH_SHORT).show()
            return
        }

        if (capturedPhotos.size >= MAX_PHOTOS) {
            Toast.makeText(this, "Maximum $MAX_PHOTOS photos reached.", Toast.LENGTH_SHORT).show()
            return
        }

        val vintedDir = File(cacheDir, "vinted").apply { mkdirs() }
        val photoFile = File(vintedDir, "IMG_${UUID.randomUUID()}.jpg")

        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        isCapturing = true
        refreshControls()

        capture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) {
                    Log.e(TAG, "Photo capture failed: ${exc.message}", exc)
                    Toast.makeText(this@VintedCaptureActivity,
                        "Capture failed.", Toast.LENGTH_SHORT).show()
                    isCapturing = false
                    refreshControls()
                }

                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    capturedPhotos.add(photoFile)
                    Log.i(TAG, "Photo saved: ${photoFile.absolutePath} (${capturedPhotos.size}/$MAX_PHOTOS)")
                    addThumbnail(photoFile)
                    isCapturing = false
                    refreshControls()
                }
            }
        )
    }

    private fun refreshControls() {
        photoCounter.text = "Photos: ${capturedPhotos.size} / $MAX_PHOTOS"
        captureButton.isEnabled = !isCapturing && capturedPhotos.size < MAX_PHOTOS
        doneButton.isEnabled = !isCapturing && capturedPhotos.isNotEmpty()
        thumbnailContainer.visibility =
            if (capturedPhotos.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun addThumbnail(file: File) {
        val sizePx = dpToPx(72)
        val marginPx = dpToPx(4)
        val xVisualSize = dpToPx(40)
        val xMarginPx = dpToPx(2)

        val frame = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(sizePx, sizePx).apply {
                setMargins(marginPx, marginPx, marginPx, marginPx)
            }
            tag = file
        }

        val imageView = ImageView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            scaleType = ImageView.ScaleType.CENTER_CROP
            val bitmap = decodeThumbnail(file, sizePx)
            if (bitmap != null) {
                setImageBitmap(bitmap)
            } else {
                setBackgroundColor(Color.DKGRAY)
            }
        }

        val deleteButton = TextView(this).apply {
            layoutParams = FrameLayout.LayoutParams(xVisualSize, xVisualSize).apply {
                gravity = Gravity.TOP or Gravity.END
                setMargins(0, xMarginPx, xMarginPx, 0)
            }
            text = "×"
            textSize = 18f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0xCC000000.toInt())
            }
            setOnClickListener { deletePhoto(frame, file) }
        }

        frame.addView(imageView)
        frame.addView(deleteButton)
        thumbnailRow.addView(frame)
    }

    private fun deletePhoto(view: View, file: File) {
        capturedPhotos.remove(file)
        try {
            file.delete()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to delete cache file: ${file.absolutePath}", e)
        }
        thumbnailRow.removeView(view)
        refreshControls()
    }

    private fun rebuildThumbnails() {
        thumbnailRow.removeAllViews()
        capturedPhotos.forEach { addThumbnail(it) }
    }

    private fun onDoneClicked() {
        if (isCapturing || capturedPhotos.isEmpty()) return
        val paths = ArrayList(capturedPhotos.map { it.absolutePath })
        val data = Intent().apply {
            putStringArrayListExtra("photo_paths", paths)
            putExtra("platform", platform)
        }
        setResult(RESULT_OK, data)
        finish()
    }

    private fun dpToPx(dp: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp.toFloat(),
            resources.displayMetrics
        ).toInt()
    }

    private fun decodeThumbnail(file: File, targetSizePx: Int): android.graphics.Bitmap? {
        return try {
            val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, boundsOptions)

            if (boundsOptions.outWidth <= 0 || boundsOptions.outHeight <= 0) return null

            var sampleSize = 1
            while ((boundsOptions.outWidth / sampleSize) > targetSizePx * 2 ||
                   (boundsOptions.outHeight / sampleSize) > targetSizePx * 2) {
                sampleSize *= 2
            }

            val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            BitmapFactory.decodeFile(file.absolutePath, decodeOptions)
        } catch (e: Exception) {
            Log.e(TAG, "Thumbnail decode failed for ${file.absolutePath}", e)
            null
        }
    }

    private fun cycleFlashMode() {
        flashMode = when (flashMode) {
            ImageCapture.FLASH_MODE_OFF -> ImageCapture.FLASH_MODE_ON
            ImageCapture.FLASH_MODE_ON -> ImageCapture.FLASH_MODE_AUTO
            else -> ImageCapture.FLASH_MODE_OFF
        }
        imageCapture?.flashMode = flashMode
        flashButton.text = when (flashMode) {
            ImageCapture.FLASH_MODE_ON -> "⚡ On"
            ImageCapture.FLASH_MODE_AUTO -> "⚡ Auto"
            else -> "⚡ Off"
        }
    }

    private val scaleListener = object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val cam = camera ?: return true
            val zoomState = cam.cameraInfo.zoomState.value ?: return true
            val current = zoomState.zoomRatio
            val newRatio = (current * detector.scaleFactor)
                .coerceIn(zoomState.minZoomRatio, zoomState.maxZoomRatio)
            cam.cameraControl.setZoomRatio(newRatio)
            return true
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putStringArrayList(
            "captured_photos",
            ArrayList(capturedPhotos.map { it.absolutePath })
        )
        outState.putInt("flash_mode", flashMode)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::cameraExecutor.isInitialized) {
            cameraExecutor.shutdown()
        }
    }
}
