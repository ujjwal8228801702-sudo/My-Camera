package com.ujjwal.mycamera

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.BLACK
        window.navigationBarColor = android.graphics.Color.BLACK
        setContent { MyCameraScreen() }
    }
}

private val cameraModes = listOf("PHOTO", "PORTRAIT", "VIDEO", "DUAL VIDEO", "TIME-LAPSE", "SLOW-MO")

@Composable
private fun MyCameraScreen() {
    val context = LocalContext.current
    val previewView = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { hasPermission = it }
    var frontCamera by remember { mutableStateOf(false) }
    var flashOn by remember { mutableStateOf(false) }
    var showGrid by remember { mutableStateOf(false) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var videoCapture by remember { mutableStateOf<VideoCapture<Recorder>?>(null) }
    var recording by remember { mutableStateOf<Recording?>(null) }
    var cameraControl by remember { mutableStateOf<androidx.camera.core.CameraControl?>(null) }
    var lastPhoto by remember { mutableStateOf<Uri?>(null) }
    var selectedMode by remember { mutableStateOf("PHOTO") }
    var videoQuality by remember { mutableStateOf("4K") }
    var showQualityMenu by remember { mutableStateOf(false) }
    val isVideoMode = selectedMode == "VIDEO"

    LaunchedEffect(Unit) {
        if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    LaunchedEffect(hasPermission, frontCamera, flashOn, selectedMode, videoQuality) {
        if (!hasPermission) return@LaunchedEffect
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            try {
                val provider = future.get()
                val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                val selector = if (frontCamera) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
                provider.unbindAll()
                if (isVideoMode) {
                    val quality = if (videoQuality == "4K") Quality.UHD else Quality.FHD
                    val recorderBuilder = Recorder.Builder().setQualitySelector(
                        QualitySelector.from(quality, FallbackStrategy.lowerQualityOrHigherThan(Quality.SD))
                    )
                    val recorder = recorderBuilder.build()
                    val video = VideoCapture.withOutput(recorder)
                    val camera = provider.bindToLifecycle(lifecycleOwner, selector, preview, video)
                    videoCapture = video
                    imageCapture = null
                    cameraControl = camera.cameraControl
                } else {
                    val capture = ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                        .setFlashMode(if (flashOn) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF)
                        .build()
                    val camera = provider.bindToLifecycle(lifecycleOwner, selector, preview, capture)
                    imageCapture = capture
                    videoCapture = null
                    cameraControl = camera.cameraControl
                }
                cameraControl?.setZoomRatio(zoom.coerceAtLeast(1f))
            } catch (e: Exception) {
                Toast.makeText(context, "Camera start nahi ho paya: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }, ContextCompat.getMainExecutor(context))
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (hasPermission) {
            AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
            if (showGrid) GridOverlay()
            Column(Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(top = 18.dp, start = 18.dp, end = 18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    RoundControl(icon = if (flashOn) Icons.Default.FlashOn else Icons.Default.FlashOff, label = "Flash") { flashOn = !flashOn }
                    Text("MY CAMERA", color = Color.White, fontSize = 17.sp, letterSpacing = 3.sp)
                    RoundControl(icon = Icons.Default.Cameraswitch, label = "Switch") {
                        if (recording != null) Toast.makeText(context, "Recording ke dauran camera switch nahi kar sakte", Toast.LENGTH_SHORT).show()
                        else frontCamera = !frontCamera
                    }
                }
                if (isVideoMode) {
                    Box {
                        Text(videoQuality + "  ⌄", color = Color.White, modifier = Modifier
                            .background(Color(0x99000000), MaterialTheme.shapes.medium)
                            .clickable { showQualityMenu = true }.padding(horizontal = 14.dp, vertical = 9.dp))
                        DropdownMenu(expanded = showQualityMenu, onDismissRequest = { showQualityMenu = false }) {
                            listOf("4K", "1080p").forEach { q ->
                                DropdownMenuItem(text = { Text(q) }, onClick = {
                                    videoQuality = q; showQualityMenu = false
                                })
                            }
                        }
                    }
                }
            }
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Color(0xD9080808)).padding(bottom = 20.dp, top = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    cameraModes.forEach { mode ->
                        Text(mode, color = if (selectedMode == mode) Color(0xFFFFD45A) else Color.LightGray,
                            fontSize = 11.sp, modifier = Modifier.clickable {
                                if (recording != null) {
                                    Toast.makeText(context, "Pehle recording band karein", Toast.LENGTH_SHORT).show()
                                } else if (mode == "DUAL VIDEO" || mode == "TIME-LAPSE" || mode == "SLOW-MO") {
                                    Toast.makeText(context, when(mode) {
                                        "DUAL VIDEO" -> "Dual Video ke liye phone mein simultaneous front + back camera support zaroori hai. Is build mein abhi active nahi hai."
                                        "TIME-LAPSE" -> "Time-lapse recording agle feature update mein aayegi."
                                        else -> "Slow-motion recording agle feature update mein aayegi."
                                    }, Toast.LENGTH_LONG).show()
                                } else {
                                    selectedMode = mode
                                }
                            }.padding(vertical = 10.dp))
                    }
                }
                if (selectedMode == "PORTRAIT") {
                    Text("Portrait photo • background blur phone support par depend karta hai", color = Color.LightGray, fontSize = 10.sp)
                }
                if (isVideoMode) {
                    Text("Video • ${videoQuality} (device-supported quality; 60 FPS not guaranteed)", color = Color.LightGray, fontSize = 10.sp)
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
                    IconButton(onClick = {
                        lastPhoto?.let {
                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                                setDataAndType(it, "image/*"); addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            runCatching { context.startActivity(intent) }.onFailure {
                                Toast.makeText(context, "Gallery mein media dekhein", Toast.LENGTH_SHORT).show()
                            }
                        } ?: Toast.makeText(context, "Abhi koi photo preview nahi hai", Toast.LENGTH_SHORT).show()
                    }) { Icon(Icons.Default.PhotoLibrary, contentDescription = "Gallery", tint = Color.White, modifier = Modifier.size(30.dp)) }

                    if (isVideoMode) {
                        Box(Modifier.size(76.dp).background(Color.White, CircleShape).padding(5.dp)
                            .background(Color(0xFF111111), CircleShape).padding(8.dp)
                            .background(if (recording == null) Color.Red else Color(0xFFFF5252), CircleShape)
                            .clickable {
                                if (recording != null) {
                                    recording?.stop()
                                    recording = null
                                } else {
                                    startVideoRecording(context, videoCapture) { recording = it }
                                }
                            })
                    } else {
                        Box(Modifier.size(76.dp).background(Color.White, CircleShape).padding(5.dp)
                            .background(Color(0xFF111111), CircleShape).padding(4.dp)
                            .background(Color.White, CircleShape)
                            .clickable { takePhoto(context, imageCapture) { lastPhoto = it } })
                    }
                    IconButton(onClick = { showGrid = !showGrid }) {
                        Icon(Icons.Default.GridOn, contentDescription = "Grid", tint = if (showGrid) Color(0xFFFFD45A) else Color.White, modifier = Modifier.size(28.dp))
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                    Text("1x", color = Color.White, fontSize = 13.sp)
                    Slider(value = zoom.coerceIn(1f, 5f), onValueChange = {
                        zoom = it; cameraControl?.setZoomRatio(it)
                    }, valueRange = 1f..5f, modifier = Modifier.width(180.dp),
                        colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color(0xFFFFD45A), inactiveTrackColor = Color.Gray))
                    Text("5x", color = Color.White, fontSize = 13.sp)
                }
            }
        } else {
            Column(Modifier.align(Alignment.Center).padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.CameraAlt, contentDescription = null, tint = Color.White, modifier = Modifier.size(54.dp))
                Spacer(Modifier.height(16.dp))
                Text("My Camera ko camera permission chahiye", color = Color.White)
                Spacer(Modifier.height(12.dp))
                Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) { Text("Permission dein") }
            }
        }
    }
}

private fun startVideoRecording(context: Context, capture: VideoCapture<Recorder>?, onStarted: (Recording) -> Unit) {
    if (capture == null) {
        Toast.makeText(context, "Video camera taiyar ho raha hai", Toast.LENGTH_SHORT).show()
        return
    }
    val values = ContentValues().apply {
        put(MediaStore.Video.Media.DISPLAY_NAME, "MyCamera_${System.currentTimeMillis()}.mp4")
        put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
        put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/My Camera")
    }
    val output = MediaStoreOutputOptions.Builder(context.contentResolver, MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
        .setContentValues(values).build()
    val active = capture.output.prepareRecording(context, output).start(ContextCompat.getMainExecutor(context)) { event ->
        if (event is VideoRecordEvent.Finalize) {
            if (event.hasError()) Toast.makeText(context, "Video save nahi hua: ${event.error}", Toast.LENGTH_LONG).show()
            else Toast.makeText(context, "Video Movies/My Camera mein save ho gaya", Toast.LENGTH_LONG).show()
        }
    }
    onStarted(active)
    Toast.makeText(context, "Video recording shuru", Toast.LENGTH_SHORT).show()
}

private fun takePhoto(context: Context, capture: ImageCapture?, onSaved: (Uri) -> Unit) {
    if (capture == null) {
        Toast.makeText(context, "Camera taiyar ho raha hai", Toast.LENGTH_SHORT).show()
        return
    }
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, "MyCamera_${System.currentTimeMillis()}.jpg")
        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
        put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/My Camera")
    }
    val options = ImageCapture.OutputFileOptions.Builder(context.contentResolver, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values).build()
    capture.takePicture(options, ContextCompat.getMainExecutor(context), object : ImageCapture.OnImageSavedCallback {
        override fun onImageSaved(output: ImageCapture.OutputFileResults) {
            output.savedUri?.let(onSaved)
            Toast.makeText(context, "Full-quality photo save ho gayi!", Toast.LENGTH_SHORT).show()
        }
        override fun onError(exception: ImageCaptureException) {
            Toast.makeText(context, "Photo save nahi hui: ${exception.message}", Toast.LENGTH_LONG).show()
        }
    })
}

@Composable
private fun RoundControl(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(46.dp).background(Color(0x66000000), CircleShape)) {
        Icon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(24.dp))
    }
}

@Composable
private fun GridOverlay() {
    Canvas(Modifier.fillMaxSize()) {
        val thirdW = size.width / 3f
        val thirdH = size.height / 3f
        for (i in 1..2) {
            drawLine(Color(0x88FFFFFF), Offset(thirdW * i, 0f), Offset(thirdW * i, size.height), 1.dp.toPx())
            drawLine(Color(0x88FFFFFF), Offset(0f, thirdH * i), Offset(size.width, thirdH * i), 1.dp.toPx())
        }
    }
}
