package com.tonyxlh.mrzscanner

import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy

class MRZAnalyzer(
    private val mrzRecognizer: MRZRecognizer
) : ImageAnalysis.Analyzer {
    @OptIn(ExperimentalGetImage::class)
    override fun analyze(image: ImageProxy) {
        try {
            val bitmap = BitmapUtils.getBitmap(image)
            if (bitmap != null) {
                mrzRecognizer.feedBitmap(bitmap)
            }
        } catch(e: Exception) {
            e.printStackTrace()
        } finally {
            image.close()
        }
    }
}
