package android.eman.cz.tflitedemo

import android.app.Activity
import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Log

import org.tensorflow.lite.Interpreter

import java.io.BufferedReader
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStreamReader
import java.nio.FloatBuffer
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.util.*

/**
 * Classifies images with Tensorflow Lite
 */
class ImageClassifier(activity: Activity) {
    val TAG = this.javaClass.name

    val modelPath  = "model.tflite"
    val labelPath = "labels.txt"
    val imageSizeX = 32
    val imageSizeY = 32

    val imgDataBuffer: FloatBuffer
    val labelList: List<String>

    var tflite: Interpreter? = null

    init {
        tflite = Interpreter(loadModelFile(activity))
        labelList = loadLabelList(activity)
        imgDataBuffer = FloatBuffer.allocate(imageSizeX * imageSizeY)
        Log.d(TAG, "Created a Tensorflow Lite Image Classifier.")
    }


    fun classifyFrame(bitmap: Bitmap): FloatArray {
        if (tflite == null) {
            Log.e(TAG, "Image classifier has not been initialized; Skipped.")
        }
        extractFeatures(bitmap)
        val features = imgDataBuffer.array()
        return runInference(features)
    }

    private fun extractFeatures(bitmap: Bitmap) {
        imgDataBuffer.rewind()
        val intValues = IntArray(imageSizeX * imageSizeY)
        bitmap.getPixels(intValues, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)

        var pixel = 0
        while (pixel < imageSizeX*imageSizeY) {
            transformPixelValue(intValues[pixel++])
        }
    }

    private fun runInference(features: FloatArray): FloatArray {
        val input = Array(1) { _ -> features }
        val output = Array(1) { _ -> FloatArray(labelList.size)}

        tflite?.run(input, output)
        Log.d(TAG, "Inference run: ${Arrays.deepToString(output)}")

        return output[0]
    }



    private fun transformPixelValue(rgb: Int) {
        val red = rgb shr 16 and 0xFF
        val green = rgb shr 8 and 0xFF
        val blue = rgb and 0xFF

        val grayScale = (red + green + blue) / 3
        val normalized = grayScale / 255f

        imgDataBuffer.put(normalized)
    }


    @Throws(IOException::class)
    private fun loadLabelList(activity: Activity): List<String> {
        val labelList = ArrayList<String>()
        val reader = BufferedReader(InputStreamReader(activity.assets.open(labelPath)))
        var line = reader.readLine()
        while (line != null) {
            labelList.add(line)
            line = reader.readLine()
        }
        reader.close()
        return labelList
    }


    @Throws(IOException::class)
    private fun loadModelFile(activity: Activity): MappedByteBuffer {
        val fileDescriptor = activity.assets.openFd(modelPath)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }


    fun close() {
        tflite!!.close()
        tflite = null
    }
}
