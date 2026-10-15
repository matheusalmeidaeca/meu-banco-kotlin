package com.meubanco.nativo

import android.Manifest
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/** Resposta da tela ao ler um código: mensagem para mostrar e se deve fechar o leitor. */
class ScanAction(val msg: String? = null, val close: Boolean = false)

fun Context.findActivity(): ComponentActivity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is ComponentActivity) return c
        c = c.baseContext
    }
    return null
}

fun hasPermission(ctx: Context, perm: String): Boolean =
    ContextCompat.checkSelfPermission(ctx, perm) == PackageManager.PERMISSION_GRANTED

fun openAppSettings(ctx: Context) {
    val i = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + ctx.packageName))
    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    ctx.startActivity(i)
}

private fun beep() {
    try {
        val tg = ToneGenerator(AudioManager.STREAM_MUSIC, 70)
        tg.startTone(ToneGenerator.TONE_PROP_BEEP, 100)
        Handler(Looper.getMainLooper()).postDelayed({ tg.release() }, 300)
    } catch (e: Exception) {
    }
}

private class LastScan {
    var code = ""
    var t = 0L
}

@androidx.annotation.OptIn(ExperimentalGetImage::class)
@Composable
fun CameraPreviewBox(onCode: (String) -> Unit) {
    val ctx = LocalContext.current
    val activity = ctx.findActivity()
    val executor = remember { Executors.newSingleThreadExecutor() }
    val last = remember { LastScan() }
    val scanner = remember {
        BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder()
                .setBarcodeFormats(
                    Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8, Barcode.FORMAT_UPC_A,
                    Barcode.FORMAT_UPC_E, Barcode.FORMAT_CODE_128,
                )
                .build(),
        )
    }
    DisposableEffect(Unit) {
        onDispose {
            executor.shutdown()
            scanner.close()
        }
    }
    if (activity == null) return
    AndroidView(
        modifier = Modifier.fillMaxWidth().height(260.dp).clip(RoundedCornerShape(14.dp)),
        factory = { c ->
            val view = PreviewView(c)
            val future = ProcessCameraProvider.getInstance(c)
            future.addListener(
                {
                    try {
                        val provider = future.get()
                        val preview = Preview.Builder().build()
                        preview.setSurfaceProvider(view.surfaceProvider)
                        val analysis = ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()
                        analysis.setAnalyzer(executor) { proxy ->
                            val media = proxy.image
                            if (media == null) {
                                proxy.close()
                            } else {
                                val img = InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees)
                                scanner.process(img)
                                    .addOnSuccessListener { list ->
                                        val v = list.firstOrNull()?.rawValue
                                        if (v != null) {
                                            val now = System.currentTimeMillis()
                                            if (v != last.code || now - last.t > 2000) {
                                                last.code = v
                                                last.t = now
                                                ContextCompat.getMainExecutor(c).execute { onCode(v) }
                                            }
                                        }
                                    }
                                    .addOnCompleteListener { proxy.close() }
                            }
                        }
                        provider.unbindAll()
                        provider.bindToLifecycle(activity, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
                    } catch (e: Exception) {
                    }
                },
                ContextCompat.getMainExecutor(c),
            )
            view
        },
    )
}

/**
 * Leitor de código de barras pela câmera. Pede a permissão da câmera quando abre.
 * continuous = true mantém a câmera aberta (várias leituras seguidas, com bipe).
 */
@Composable
fun ScanDialog(continuous: Boolean, onCode: (String) -> ScanAction, onClose: () -> Unit) {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    var granted by remember { mutableStateOf(hasPermission(ctx, Manifest.permission.CAMERA)) }
    var denied by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Aponte a câmera para o código de barras…") }
    var manual by remember { mutableStateOf("") }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        granted = ok
        denied = !ok
    }
    LaunchedEffect(Unit) {
        if (!granted) launcher.launch(Manifest.permission.CAMERA)
    }
    val handle: (String) -> Unit = { raw ->
        val code = raw.trim()
        if (code.isNotEmpty()) {
            val r = onCode(code)
            beep()
            if (r.msg != null) status = r.msg
            if (r.close) onClose()
        }
    }
    val shape = RoundedCornerShape(22.dp)
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(12.dp)
                .clip(shape)
                .background(p.card)
                .border(1.dp, p.border, shape)
                .padding(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Ler código de barras", color = p.text, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Box(Modifier.size(36.dp).clip(CircleShape).clickable { onClose() }, contentAlignment = Alignment.Center) {
                    Text("✕", color = p.muted, fontSize = 18.sp)
                }
            }
            Spacer(Modifier.height(10.dp))
            if (granted) {
                CameraPreviewBox(onCode = handle)
            } else {
                Text(
                    if (denied) "Sem permissão da câmera. Digite o código abaixo ou libere a câmera nas configurações do app."
                    else "Pedindo permissão da câmera…",
                    color = p.muted,
                    fontSize = 13.sp,
                )
                Spacer(Modifier.height(8.dp))
                GhostButton("Permitir câmera") { launcher.launch(Manifest.permission.CAMERA) }
                Spacer(Modifier.height(6.dp))
                GhostButton("Abrir configurações do app") { openAppSettings(ctx) }
            }
            Spacer(Modifier.height(8.dp))
            Text(status, color = p.text, fontSize = 13.sp)
            Spacer(Modifier.height(10.dp))
            GField("Ou digite o código", manual, { manual = it.filter { ch -> ch.isDigit() } }, keyboard = KeyboardType.Number)
            Spacer(Modifier.height(6.dp))
            GhostButton("Usar o código digitado") {
                handle(manual)
                manual = ""
            }
            if (continuous) {
                Spacer(Modifier.height(8.dp))
                PrimaryButton("Concluir") { onClose() }
            }
        }
    }
}

/** Busca nome e marca do produto no Open Food Facts (precisa de internet). Chama onResult na tela principal. */
fun fetchProductInfo(code: String, onResult: (name: String, brand: String) -> Unit) {
    Thread {
        var name = ""
        var brand = ""
        try {
            val url = URL("https://world.openfoodfacts.org/api/v2/product/" + Uri.encode(code) + ".json?fields=product_name,brands")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 6000
            conn.readTimeout = 6000
            val text = conn.inputStream.bufferedReader().use { it.readText() }
            val j = JSONObject(text)
            if (j.optInt("status") == 1) {
                val pr = j.optJSONObject("product")
                name = pr?.optString("product_name", "")?.trim() ?: ""
                brand = (pr?.optString("brands", "") ?: "").split(",").firstOrNull()?.trim() ?: ""
            }
        } catch (e: Exception) {
        }
        if (name.isNotEmpty()) {
            val n = name
            val b = brand
            Handler(Looper.getMainLooper()).post { onResult(n, b) }
        }
    }.start()
}
