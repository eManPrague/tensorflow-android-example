package android.eman.cz.tflitedemo

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.eman.cz.tflitedemo.R.id.texture
import android.eman.cz.tflitedemo.R.id.toggle_photo
import android.graphics.Bitmap
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import android.media.ImageReader
import android.os.*
import android.support.v4.app.ActivityCompat
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.util.Size
import android.view.Surface
import android.view.TextureView
import android.widget.Toast
import kotlinx.android.synthetic.main.activity_main.*
import org.jetbrains.anko.collections.forEachReversedWithIndex
import org.jetbrains.anko.doAsync
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.lang.StringBuilder
import java.util.Arrays

/**
 * Simple MainActivity of app, holds all camera actions
 */
class MainActivity : AppCompatActivity() {
    val TAG = this.javaClass.name

    var cameraId: String? = null
    var cameraDevice: CameraDevice? = null
    lateinit var cameraCaptureSessions: CameraCaptureSession
    lateinit var captureRequestBuilder: CaptureRequest.Builder
    lateinit var imageDimension: Size
    var imageReader: ImageReader? = null

    var backgroundHandler: Handler? = null
    var backgroundThread: HandlerThread? = null

    lateinit var classifier: ImageClassifier


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        texture.surfaceTextureListener = textureListener

        classifier = ImageClassifier(this)

        doAsync {
            while (true) {
                processFrame()
                Thread.sleep(100)
            }
        }

        toggle_photo.setOnCheckedChangeListener { button, boolean ->
            if (!boolean) {
                showToast("Images saved to /TFLiteDemo_images")
            }
        }
    }


    private fun processFrame() {
        if (cameraDevice == null) {
            return
        }
        val bitmap = texture.getBitmap(classifier.imageSizeX, classifier.imageSizeY)
        // classification
        val startTime = SystemClock.uptimeMillis()
        val output = classifier.classifyFrame(bitmap)
        val stopTime = SystemClock.uptimeMillis()
        // capture photo
        if (toggle_photo.isChecked) {
            saveImageFile(bitmap, System.currentTimeMillis().toString())
        }
        bitmap.recycle()

        // print to UI
        val classificationTime = stopTime - startTime
        val classificationDetail = StringBuilder()
        classifier.labelList.forEachIndexed { index, label ->
            classificationDetail.append(String.format("%s: %.2f\n", label, output[index]))
        }

        runOnUiThread {
            text_time.text = "$classificationTime ms"
            text.text = classificationDetail.toString()
        }
    }


    private fun saveImageFile(bitmap: Bitmap, filename: String) {
        val folder = File(Environment.getExternalStorageDirectory(), "TFLiteDemo_images")
        folder.mkdirs()
        val file = File(folder, "$filename.png")
        var out: FileOutputStream? = null
        try {
            out = FileOutputStream(file)
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            try {
                out?.close()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }



    fun showToast(textToShow: String) {
        runOnUiThread {
            Toast.makeText(this@MainActivity, textToShow, Toast.LENGTH_SHORT).show()
        }
    }


    var textureListener: TextureView.SurfaceTextureListener = object : TextureView.SurfaceTextureListener {
        override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
            //open your camera here
            openCamera()
        }

        override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {}

        override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
            return false
        }

        override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
    }

    val stateCallback = object : CameraDevice.StateCallback() {
        override fun onOpened(camera: CameraDevice) {
            cameraDevice = camera
            createCameraPreview()
        }

        override fun onDisconnected(camera: CameraDevice) {
            cameraDevice?.close()
        }

        override fun onError(camera: CameraDevice, error: Int) {
            cameraDevice?.close()
            cameraDevice = null
        }
    }

    fun startBackgroundThread() {
        backgroundThread = HandlerThread("Camera Background")
        backgroundThread?.start()
        backgroundHandler = Handler(backgroundThread?.looper)
    }

    fun stopBackgroundThread() {
        backgroundThread?.quitSafely()
        try {
            backgroundThread?.join()
            backgroundThread = null
            backgroundHandler = null
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }

    }

    fun createCameraPreview() {
        try {
            val texture = texture.surfaceTexture
            texture.setDefaultBufferSize(imageDimension.width, imageDimension.height)
            val surface = Surface(texture)
            captureRequestBuilder = cameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
            captureRequestBuilder.addTarget(surface)
            cameraDevice?.createCaptureSession(Arrays.asList(surface), object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(cameraCaptureSession: CameraCaptureSession) {
                    // camera is already closed
                    if (null == cameraDevice) {
                        return
                    }
                    // when the session is ready, start displaying the preview.
                    cameraCaptureSessions = cameraCaptureSession
                    updatePreview()
                }

                override fun onConfigureFailed(cameraCaptureSession: CameraCaptureSession) {}
            }, null)
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
    }

    fun openCamera() {
        val manager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        try {
            cameraId = manager.cameraIdList[0]
            val characteristics = manager.getCameraCharacteristics(cameraId)
            val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            val cameraDimensions = map.getOutputSizes(SurfaceTexture::class.java)
            // find the squarest camera dimensions, ratio has to be as close to 1 as possible
            var ratio = Float.MAX_VALUE
            var squareIndex = 0
            cameraDimensions.forEachReversedWithIndex { index, cameraSize ->
                val tempRatio = cameraSize.width / cameraSize.height.toFloat()
                if (tempRatio >= 1 && tempRatio < ratio) {
                    ratio = tempRatio
                    squareIndex = index
                }
            }
            imageDimension = map.getOutputSizes(SurfaceTexture::class.java)[squareIndex]
            // Add permission for camera and let user grant the permission
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this@MainActivity, arrayOf(Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE), REQUEST_CAMERA_PERMISSION)
                return
            }
            manager.openCamera(cameraId, stateCallback, null)
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
    }

    fun updatePreview() {
        if (null == cameraDevice) {
            return
        }
        captureRequestBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)
        try {
            cameraCaptureSessions.setRepeatingRequest(captureRequestBuilder.build(), null, backgroundHandler)
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
    }

    fun closeCamera() {
        if (null != cameraDevice) {
            cameraDevice?.close()
            cameraDevice = null
        }
        if (null != imageReader) {
            imageReader?.close()
            imageReader = null
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults[0] == PackageManager.PERMISSION_DENIED) {
                // close the app
                showToast("You can't use this app without granting permission")
                finish()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        startBackgroundThread()
        if (texture.isAvailable) {
            openCamera()
        } else {
            texture.surfaceTextureListener = textureListener
        }
    }

    override fun onPause() {
        //closeCamera();
        stopBackgroundThread()
        super.onPause()
    }

    companion object {
        val REQUEST_CAMERA_PERMISSION = 777
    }
}