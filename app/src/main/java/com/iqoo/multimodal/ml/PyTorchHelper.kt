package com.iqoo.multimodal.ml

import android.content.Context
import android.graphics.Bitmap
import org.pytorch.IValue
import org.pytorch.LiteModuleLoader
import org.pytorch.Module
import org.pytorch.Tensor
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

import org.pytorch.torchvision.TensorImageUtils
import android.util.Log

object PyTorchHelper {

    private var imageModule: Module? = null
    private var audioModule: Module? = null

    fun initModels(context: Context) {
        try {
            val imageModelPath = assetFilePath(context, "vision_best.ptl")
            imageModule = LiteModuleLoader.load(imageModelPath)
            Log.d("PyTorchHelper", "Image model loaded successfully.")
            
            val audioModelPath = assetFilePath(context, "acoustic_cnn.ptl")
            audioModule = LiteModuleLoader.load(audioModelPath)
            Log.d("PyTorchHelper", "Audio model loaded successfully.")
        } catch (e: Exception) {
            e.printStackTrace()
            Log.e("PyTorchHelper", "Error loading models: ${e.message}")
        }
    }

    fun predictImage(bitmap: Bitmap): String {
        val module = imageModule ?: return "Error: Model not loaded yet"
        
        try {
            // 1. Resize bitmap to 640x640 (Ultralytics YOLOv8 standard exported size)
            val resizedBitmap = Bitmap.createScaledBitmap(bitmap, 640, 640, true)
            
            // 2. YOLOv8 expects 0.0 - 1.0 RGB values without ImageNet normalization. 
            // TensorImageUtils divides by 255, so using mean 0 and std 1 keeps it in 0-1 range.
            val NO_MEAN_RGB = floatArrayOf(0.0f, 0.0f, 0.0f)
            val NO_STD_RGB = floatArrayOf(1.0f, 1.0f, 1.0f)
            
            val inputTensor = TensorImageUtils.bitmapToFloat32Tensor(
                resizedBitmap,
                NO_MEAN_RGB,
                NO_STD_RGB
            )
            
            // 3. Run Inference
            val outputTuple = module.forward(IValue.from(inputTensor))
            
            // Handle output (YOLOv8 returns a tuple or a tensor of shape [1, classes+4, 8400])
            val outputTensor = if (outputTuple.isTuple) {
                outputTuple.toTuple()[0].toTensor()
            } else {
                outputTuple.toTensor()
            }
            
            val shape = outputTensor.shape()
            val shapeStr = shape.joinToString("x")
            
            return "Success!\nYOLO Output Shape: [$shapeStr]"
        } catch (e: Exception) {
            e.printStackTrace()
            return "Error during inference: ${e.message}"
        }
    }

    fun predictAudio(pcmData: ShortArray): String {
        // Placeholder for real inference
        // 1. Convert PCM to FloatArray / Mel-Spectrogram
        // 2. Tensor.fromBlob(...)
        // 3. audioModule?.forward(IValue.from(tensor))?.toTensor()
        return "Audio Model Output: [Sound Y] (Placeholder)"
    }

    @Throws(IOException::class)
    private fun assetFilePath(context: Context, assetName: String): String {
        val file = File(context.filesDir, assetName)
        if (file.exists() && file.length() > 0) {
            return file.absolutePath
        }
        context.assets.open(assetName).use { `is` ->
            FileOutputStream(file).use { os ->
                val buffer = ByteArray(4 * 1024)
                var read: Int
                while (`is`.read(buffer).also { read = it } != -1) {
                    os.write(buffer, 0, read)
                }
                os.flush()
            }
            return file.absolutePath
        }
    }
}
