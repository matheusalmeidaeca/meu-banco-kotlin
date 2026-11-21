package com.meubanco.patrimonio.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors

/** Leitor de código de barras (CameraX + ML Kit). */
@Composable
fun BarcodeScannerDialog(onCode: (String) -> Unit, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LaunchedEffect(Unit) { if (!granted) launcher.launch(Manifest.permission.CAMERA) }
    Dialog(onDismissRequest = onDismiss) {
        Card {
            H2("Escanear código de barras")
            if (!granted) {
                Txt("Permita o uso da câmera para escanear.")
                PrimaryButton("Permitir câmera") { launcher.launch(Manifest.permission.CAMERA) }
            } else {
                CameraPreview(onCode)
            }
            GhostButton("Cancelar", Modifier.fillMaxWidth()) { onDismiss() }
        }
    }
}

@Composable
private fun CameraPreview(onCode: (String) -> Unit) {
    val ctx = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val executor = remember { Executors.newSingleThreadExecutor() }
    val scanner = remember { BarcodeScanning.getClient() }
    var done by remember { mutableStateOf(false) }
    DisposableEffect(Unit) { onDispose { executor.shutdown(); scanner.close() } }
    AndroidView(
        modifier = Modifier.fillMaxWidth().height(300.dp),
        factory = { c ->
            val view = PreviewView(c)
            val future = ProcessCameraProvider.getInstance(c)
            future.addListener({
                val provider = future.get()
                val preview = Preview.Builder().build().also { it.setSurfaceProvider(view.surfaceProvider) }
                val analysis = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
                analysis.setAnalyzer(executor) { proxy ->
                    val media = proxy.image
                    if (media != null && !done) {
                        val img = InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees)
                        scanner.process(img).addOnSuccessListener { codes ->
                            val v = codes.firstOrNull { !it.rawValue.isNullOrBlank() }?.rawValue
                            if (v != null && !done) { done = true; onCode(v) }
                        }.addOnCompleteListener { proxy.close() }
                    } else proxy.close()
                }
                try {
                    provider.unbindAll()
                    provider.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
                } catch (_: Exception) {}
            }, ContextCompat.getMainExecutor(c))
            view
        },
    )
}
