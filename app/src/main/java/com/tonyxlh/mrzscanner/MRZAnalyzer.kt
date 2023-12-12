package com.tonyxlh.mrzscanner

import android.content.Context
import android.util.Log
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.dynamsoft.dlr.DLRResult

class MRZAnalyzer(
    private val onMRZScanned: (Array<DLRResult>) -> Unit,
    private val context: Context
): ImageAnalysis.Analyzer {
    private var mrzRecognizer:MRZRecognizer = MRZRecognizer(context)

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(image: ImageProxy) {
        try {
            val bitmap = BitmapUtils.getBitmap(image)
            if (bitmap != null) {
                val results = mrzRecognizer.recognizeBitmap(bitmap)
                if (results.isNotEmpty()) {
                    onMRZScanned(results);
                }
            }
        } catch(e: Exception) {
            e.printStackTrace()
        } finally {
            image.close()
        }
    }
}
