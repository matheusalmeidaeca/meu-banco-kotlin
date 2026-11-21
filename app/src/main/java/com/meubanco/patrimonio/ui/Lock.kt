package com.meubanco.patrimonio.ui

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.meubanco.patrimonio.domain.*

fun biometricAvailable(a: FragmentActivity): Boolean =
    BiometricManager.from(a).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS

fun promptBiometric(a: FragmentActivity, onOk: () -> Unit, onFail: () -> Unit = {}) {
    val exec = ContextCompat.getMainExecutor(a)
    val prompt = BiometricPrompt(a, exec, object : BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onOk()
        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) = onFail()
    })
    val info = BiometricPrompt.PromptInfo.Builder()
        .setTitle("Meu Banco & Patrimônio").setSubtitle("Confirme que é você")
        .setNegativeButtonText("Usar PIN")
        .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK).build()
    prompt.authenticate(info)
}

@Composable
fun LockScreen(onUnlock: () -> Unit) {
    val s = state()
    val act = LocalContext.current as FragmentActivity
    var pin by remember { mutableStateOf("") }
    var err by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (s.settings.bioEnabled && biometricAvailable(act)) promptBiometric(act, onUnlock)
    }
    Column(Modifier.fillMaxSize().padding(28.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Txt("🔒", style = T.big)
        Spacer(Modifier.height(8.dp))
        Txt("Meu Banco & Patrimônio", style = T.title, bold = true)
        Muted(if (s.settings.displayName.isBlank()) "Digite seu PIN" else "Olá, ${s.settings.displayName}. Digite seu PIN")
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(pin, { v -> pin = v.filter { it.isDigit() }.take(8); err = false },
            label = { Txt("PIN") }, singleLine = true, isError = err,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword))
        if (err) Txt("PIN incorreto.", color = P.red, style = T.small)
        Spacer(Modifier.height(12.dp))
        PrimaryButton("Entrar", Modifier.width(220.dp)) {
            if (verifyPin(pin, s.settings.pinHash)) onUnlock() else { err = true; pin = "" }
        }
        if (s.settings.bioEnabled && biometricAvailable(act)) {
            Spacer(Modifier.height(8.dp))
            GhostButton("Usar digital / rosto", Modifier.width(220.dp)) { promptBiometric(act, onUnlock) }
        }
    }
}
