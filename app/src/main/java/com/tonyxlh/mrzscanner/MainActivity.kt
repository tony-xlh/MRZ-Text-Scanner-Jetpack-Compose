package com.tonyxlh.mrzscanner

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.Size
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.tonyxlh.mrzscanner.ui.theme.MRZScannerTheme
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {
    private val analysisExecutor = Executors.newSingleThreadExecutor()
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private lateinit var mrzRecognizer: MRZRecognizer
    private lateinit var overlayView: MRZOverlayView
    private lateinit var fileOverlayView: MRZOverlayView

    private var codeText by mutableStateOf("")
    private var fileImage by mutableStateOf<android.graphics.Bitmap?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mrzRecognizer = MRZRecognizer(this)
        mrzRecognizer.setLinesListener(object : MRZRecognizer.LinesListener {
            override fun onLines(lines: String, verified: Boolean) {
                runOnUiThread {
                    codeText = lines
                }
            }

            override fun onParsed(codeType: String, fields: HashMap<String, String>) {
                // The MRZ text lines are displayed. Parsed fields can be read from here.
            }

            override fun onLineLocations(
                quads: MutableList<FloatArray>,
                texts: MutableList<String>,
                verified: MutableList<Boolean>,
                imageWidth: Int,
                imageHeight: Int
            ) {
                runOnUiThread {
                    // Route the polygons to the overlay of the active data source.
                    val target = if (fileImage != null && ::fileOverlayView.isInitialized) {
                        fileOverlayView
                    } else if (::overlayView.isInitialized) {
                        overlayView
                    } else {
                        null
                    }
                    target?.setTargets(quads, texts, verified, imageWidth, imageHeight)
                }
            }
        })
        setContent {
            MRZScannerTheme {
                val context = LocalContext.current
                val lifecycleOwner = LocalLifecycleOwner.current
                val cameraProviderFuture = remember {
                    ProcessCameraProvider.getInstance(context)
                }
                var hasCamPermission by remember {
                    mutableStateOf(
                        ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.CAMERA
                        ) == PackageManager.PERMISSION_GRANTED
                    )
                }
                val launcher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission(),
                    onResult = { granted ->
                        hasCamPermission = granted
                    }
                )
                val galleryLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.PickVisualMedia()
                ) { uri: Uri? ->
                    if (uri != null) {
                        recognizeFromUri(uri)
                    }
                }
                LaunchedEffect(key1 = true) {
                    if (!hasCamPermission) {
                        launcher.launch(Manifest.permission.CAMERA)
                    }
                }
                Box(
                    modifier = Modifier.fillMaxSize()
                ) {
                    if (hasCamPermission) {
                        // The camera preview stays composed at all times so it does
                        // not freeze when the file result view is shown.
                        AndroidView(
                            factory = { context ->
                                val previewView = PreviewView(context)
                                val preview = Preview.Builder().build()
                                val selector = CameraSelector.Builder()
                                    .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                                    .build()
                                preview.setSurfaceProvider(previewView.surfaceProvider)
                                val imageAnalysis = ImageAnalysis.Builder()
                                    .setTargetResolution(Size(1080, 1920))
                                    .setBackpressureStrategy(STRATEGY_KEEP_ONLY_LATEST)
                                    .build()
                                imageAnalysis.setAnalyzer(
                                    analysisExecutor,
                                    MRZAnalyzer(mrzRecognizer)
                                )
                                try {
                                    cameraProviderFuture.get().bindToLifecycle(
                                        lifecycleOwner,
                                        selector,
                                        preview,
                                        imageAnalysis
                                    )
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                                previewView
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                        AndroidView(
                            factory = { context ->
                                overlayView = MRZOverlayView(context)
                                overlayView
                            },
                            modifier = Modifier.fillMaxSize()
                        )

                        // File result view: the picked image with its own overlay,
                        // shown on top of the camera view.
                        val fileImageBitmap = fileImage
                        if (fileImageBitmap != null) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black)
                            ) {
                                AndroidView(
                                    factory = { context ->
                                        android.widget.ImageView(context).apply {
                                            scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
                                            setImageBitmap(fileImageBitmap)
                                        }
                                    },
                                    update = { it.setImageBitmap(fileImageBitmap) },
                                    modifier = Modifier.fillMaxSize()
                                )
                                AndroidView(
                                    factory = { context ->
                                        fileOverlayView = MRZOverlayView(context)
                                        fileOverlayView.setFitCenter(true)
                                        fileOverlayView
                                    },
                                    modifier = Modifier.fillMaxSize()
                                )
                                Text(
                                    text = "Close",
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier
                                        .align(Alignment.TopStart)
                                        .padding(12.dp)
                                        .background(Color(0x99000000), RoundedCornerShape(20.dp))
                                        .clickable { resumeLiveFromFileDialog() }
                                        .padding(horizontal = 16.dp, vertical = 10.dp)
                                )
                            }
                        }

                        if (fileImage == null) {
                            Text(
                                text = "Load Image",
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(12.dp)
                                    .background(Color(0x99000000), RoundedCornerShape(20.dp))
                                    .clickable {
                                        galleryLauncher.launch(
                                            PickVisualMediaRequest(
                                                ActivityResultContracts.PickVisualMedia.ImageOnly
                                            )
                                        )
                                    }
                                    .padding(horizontal = 16.dp, vertical = 10.dp)
                            )
                        }

                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .background(Color(0xCC202020))
                        ) {
                            Text(
                                text = codeText,
                                color = Color.White,
                                fontSize = 16.sp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(140.dp)
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                                    .verticalScroll(rememberScrollState())
                            )
                        }
                    }
                }
            }
        }
    }

    private fun recognizeFromUri(uri: Uri) {
        runOnUiThread {
            codeText = "Recognizing..."
            if (::fileOverlayView.isInitialized) {
                fileOverlayView.clearTargets()
            }
        }
        analysisExecutor.execute {
            try {
                val inputStream = contentResolver.openInputStream(uri)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                inputStream?.close()
                if (bitmap != null) {
                    runOnUiThread { fileImage = bitmap }
                    // Feed the image through the video pipeline so the multi-frame
                    // verification of the SDK applies. Results arrive via the listener.
                    mrzRecognizer.scanBitmapAsFrames(bitmap, 10, 150)
                    Thread.sleep(3000)
                    runOnUiThread {
                        if (codeText == "Recognizing...") {
                            codeText = "No MRZ recognized"
                        }
                    }
                    // Show the image result for a moment, then resume the camera.
                    mainHandler.postDelayed({ resumeLiveFromFileDialog() }, 8000)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun resumeLiveFromFileDialog() {
        mainHandler.removeCallbacksAndMessages(null)
        fileImage = null
        if (::fileOverlayView.isInitialized) {
            fileOverlayView.clearTargets()
        }
        mrzRecognizer.endFileScan()
    }

    override fun onResume() {
        super.onResume()
        if (::mrzRecognizer.isInitialized) {
            mrzRecognizer.start()
        }
    }

    override fun onPause() {
        super.onPause()
        if (::mrzRecognizer.isInitialized) {
            mrzRecognizer.stop()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        analysisExecutor.shutdown()
    }
}
