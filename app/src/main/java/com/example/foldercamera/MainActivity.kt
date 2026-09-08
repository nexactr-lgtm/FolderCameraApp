package com.example.foldercamera

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.*
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { FolderCameraApp() }
    }
}

@Composable
fun FolderCameraApp() {
    val context = LocalContext.current
    var selectedFolder by remember { mutableStateOf<File?>(null) }
    var folders by remember { mutableStateOf(listFolders(context)) }
    var showCreate by remember { mutableStateOf(false) }

    if (selectedFolder != null) {
        CameraScreen(folder = selectedFolder!!, onBack = {
            selectedFolder = null
            folders = listFolders(context)
        })
        return
    }

    Scaffold(topBar = {
        TopAppBar(title = { Text("📁 Folder Camera") })
    }) { pad ->
        Column(Modifier.padding(pad).padding(16.dp)) {
            Button(
                onClick = { showCreate = true },
                modifier = Modifier.fillMaxWidth()
            ) { Text("＋ Create New Folder") }

            Spacer(Modifier.height(16.dp))
            Text("Your Folders", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))

            LazyColumn {
                items(folders, key = { it.name }) { folder ->
                    Card(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                        Row(
                            Modifier.fillMaxWidth().padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("📂  ${folder.name}", Modifier.weight(1f))
                            Button(onClick = { selectedFolder = folder }) {
                                Text("Open Camera")
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreate) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreate = false },
            title = { Text("Create Folder") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Folder name") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val clean = name.trim().replace(Regex("[\\\\/:*?\"<>|]"), "_")
                    if (clean.isNotEmpty()) {
                        val f = File(rootDir(context), clean)
                        f.mkdirs()
                        folders = listFolders(context)
                    }
                    showCreate = false
                }) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = { showCreate = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun CameraScreen(folder: File, onBack: () -> Unit) {
    val context = LocalContext.current
    val previewView = remember { PreviewView(context) }
    val executor = remember { Executors.newSingleThreadExecutor() }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var message by remember { mutableStateOf("Ready") }

    DisposableEffect(Unit) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            val provider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
            val capture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()
            imageCapture = capture
            provider.unbindAll()
            provider.bindToLifecycle(
                context as ComponentActivity,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                capture
            )
        }, ContextCompat.getMainExecutor(context))

        onDispose { executor.shutdown() }
    }

    Box(Modifier.fillMaxSize()) {
        AndroidView({ previewView }, Modifier.fillMaxSize())

        Column(
            Modifier.fillMaxWidth().align(Alignment.TopCenter)
                .padding(18.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = onBack) { Text("← Back") }
                Spacer(Modifier.width(10.dp))
                Surface(tonalElevation = 4.dp) {
                    Text("📂 ${folder.name}", Modifier.padding(10.dp))
                }
            }
        }

        Text(
            text = message,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 120.dp),
            color = Color.White
        )

        FloatingActionButton(
            onClick = {
                val capture = imageCapture ?: return@FloatingActionButton
                val now = Date()
                val stamp = SimpleDateFormat("dd-MM-yyyy HH:mm:ss", Locale.getDefault()).format(now)
                val fileName = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.getDefault()).format(now) + ".jpg"
                val photoFile = File(folder, fileName)

                val output = ImageCapture.OutputFileOptions.Builder(photoFile).build()
                capture.takePicture(
                    output, executor,
                    object : ImageCapture.OnImageSavedCallback {
                        override fun onError(exc: ImageCaptureException) {
                            previewView.post { message = "Save failed: ${exc.message}" }
                        }
                        override fun onImageSaved(result: ImageCapture.OutputFileResults) {
                            addTimestamp(photoFile, stamp)
                            previewView.post { message = "Saved: ${photoFile.name}" }
                        }
                    }
                )
            },
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 32.dp),
            containerColor = Color.White
        ) { Text("📷", style = MaterialTheme.typography.headlineSmall) }
    }
}

fun rootDir(context: android.content.Context): File =
    File(context.getExternalFilesDir(null), "Photos").apply { mkdirs() }

fun listFolders(context: android.content.Context): List<File> =
    rootDir(context).listFiles()?.filter { it.isDirectory }?.sortedBy { it.name.lowercase() } ?: emptyList()

fun addTimestamp(file: File, stamp: String) {
    val original = BitmapFactory.decodeFile(file.absolutePath) ?: return
    val mutable = original.copy(Bitmap.Config.ARGB_8888, true)
    val canvas = Canvas(mutable)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 58f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        setShadowLayer(6f, 2f, 2f, Color.BLACK)
    }
    canvas.drawText(stamp, 40f, mutable.height - 60f, paint)
    FileOutputStream(file).use { out ->
        mutable.compress(Bitmap.CompressFormat.JPEG, 95, out)
    }
    original.recycle()
    mutable.recycle()
}
