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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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

@Composable
private fun MyCameraScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        hasPermission = it
    }
    var frontCamera by remember { mutableStateOf(false) }
    var flashOn by remember { mutableStateOf(false) }
    var showGrid by remember { mutableStateOf(false) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var cameraControl by remember { mutableStateOf<androidx.camera.core.CameraControl?>(null) }
    var lastPhoto by remember { mutableStateOf<Uri?>(null) }
    var selectedMode by remember { mutableStateOf("PHOTO") }

    LaunchedEffect(Unit) {
        if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    LaunchedEffect(hasPermission, frontCamera, flashOn) {
        if (!hasPermission) return@LaunchedEffect
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            try {
                val provider = future.get()
                val preview = Preview.Builder().build()
                val capture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .setFlashMode(if (flashOn) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF)
                    .build()
                val selector = if (frontCamera) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
                provider.unbindAll()
                val camera = provider.bindToLifecycle(lifecycleOwner, selector, preview, capture)
                imageCapture = capture
                cameraControl = camera.cameraControl
                camera.cameraControl.setZoomRatio(zoom.coerceAtLeast(1f))
                preview.setSurfaceProvider(previewViewFor(context).surfaceProvider)
            } catch (e: Exception) {
                Toast.makeText(context, "Camera start nahi ho paya: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }, ContextCompat.getMainExecutor(context))
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (hasPermission) {
            AndroidView(
                factory = { previewViewFor(it) },
                modifier = Modifier.fillMaxSize()
            ) { view ->
                // Preview surface is attached by CameraX when the camera is bound.
            }
            if (showGrid) GridOverlay()
            Column(Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(top = 18.dp, start = 22.dp, end = 22.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    RoundControl(icon = if (flashOn) Icons.Default.FlashOn else Icons.Default.FlashOff, label = "Flash") {
                        flashOn = !flashOn
                    }
                    Text("MY CAMERA", color = Color.White, fontSize = 17.sp, letterSpacing = 3.sp)
                    RoundControl(icon = Icons.Default.Cameraswitch, label = "Switch") {
                        frontCamera = !frontCamera
                    }
                }
            }
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Color(0xCC080808)).padding(bottom = 22.dp, top = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(horizontalArrangement = Arrangement.spacedBy(22.dp), verticalAlignment = Alignment.CenterVertically) {
                    listOf("VIDEO", "PHOTO", "PORTRAIT").forEach { mode ->
                        Text(
                            mode,
                            color = if (selectedMode == mode) Color(0xFFFFD45A) else Color.LightGray,
                            fontSize = 13.sp,
                            modifier = Modifier.clickable {
                                selectedMode = mode
                                if (mode != "PHOTO") Toast.makeText(context, "Ye mode agle update mein add hoga. Abhi Photo mode ready hai.", Toast.LENGTH_SHORT).show()
                            }.padding(8.dp)
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
                    IconButton(onClick = {
                        lastPhoto?.let {
                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                                setDataAndType(it, "image/*")
                                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            runCatching { context.startActivity(intent) }.onFailure {
                                Toast.makeText(context, "Gallery mein photo dekhein", Toast.LENGTH_SHORT).show()
                            }
                        } ?: Toast.makeText(context, "Pehle ek photo kheechiye", Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = "Gallery", tint = Color.White, modifier = Modifier.size(30.dp))
                    }
                    Box(
                        Modifier.size(76.dp).background(Color.White, CircleShape).padding(5.dp)
                            .background(Color(0xFF111111), CircleShape).padding(4.dp)
                            .background(Color.White, CircleShape)
                            .clickable { takePhoto(context, imageCapture) { lastPhoto = it } }
                    )
                    IconButton(onClick = { showGrid = !showGrid }) {
                        Icon(Icons.Default.GridOn, contentDescription = "Grid", tint = if (showGrid) Color(0xFFFFD45A) else Color.White, modifier = Modifier.size(28.dp))
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                    Text("1x", color = Color.White, fontSize = 13.sp)
                    Slider(
                        value = zoom.coerceIn(1f, 5f),
                        onValueChange = {
                            zoom = it
                            cameraControl?.setZoomRatio(it)
                        },
                        valueRange = 1f..5f,
                        modifier = Modifier.width(180.dp),
                        colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color(0xFFFFD45A), inactiveTrackColor = Color.Gray)
                    )
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

private fun previewViewFor(context: Context): PreviewView =
    (context as? MainActivity)?.let { (it as? MainActivity)?.let { _ -> PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } } }
        ?: PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }

private fun takePhoto(context: Context, capture: ImageCapture?, onSaved: (Uri) -> Unit) {
    if (capture == null) {
        Toast.makeText(context, "Camera taiyar ho raha hai", Toast.LENGTH_SHORT).show()
        return
    }
    val name = "MyCamera_${System.currentTimeMillis()}.jpg"
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, name)
        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
        put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/My Camera")
    }
    val options = ImageCapture.OutputFileOptions.Builder(
        context.contentResolver, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values
    ).build()
    capture.takePicture(options, ContextCompat.getMainExecutor(context), object : ImageCapture.OnImageSavedCallback {
        override fun onImageSaved(output: ImageCapture.OutputFileResults) {
            output.savedUri?.let(onSaved)
            Toast.makeText(context, "Photo save ho gayi!", Toast.LENGTH_SHORT).show()
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
