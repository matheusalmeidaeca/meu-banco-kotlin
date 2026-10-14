package com.meubanco.nativo

import android.content.Context
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning

/** Abre o leitor de código de barras do Google (não precisa pedir permissão de câmera). */
fun scanBarcode(ctx: Context, onCode: (String) -> Unit) {
    val options = GmsBarcodeScannerOptions.Builder().build()
    GmsBarcodeScanning.getClient(ctx, options).startScan()
        .addOnSuccessListener { b -> b.rawValue?.let { onCode(it) } }
        .addOnFailureListener { showToast(ctx, "Não foi possível abrir o leitor de código de barras.") }
}
