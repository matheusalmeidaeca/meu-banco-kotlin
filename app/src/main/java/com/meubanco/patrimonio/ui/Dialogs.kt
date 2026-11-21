package com.meubanco.patrimonio.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Diálogo genérico com campos. `onConfirm` retorna true para fechar. */
@Composable
fun FormDialog(
    title: String, onDismiss: () -> Unit, confirmText: String = "Confirmar",
    onConfirm: () -> Boolean,
    content: @Composable ColumnScope.() -> Unit,
) {
    AlertDialog(onDismissRequest = onDismiss, containerColor = P.card, titleContentColor = P.text, textContentColor = P.text,
        title = { Txt(title, style = T.title, bold = true) },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp), content = content) },
        confirmButton = { TextButton({ if (onConfirm()) onDismiss() }) { Txt(confirmText, color = P.brand, bold = true) } },
        dismissButton = { TextButton(onDismiss) { Txt("Cancelar", color = P.muted) } })
}
