package com.meubanco.patrimonio.domain

/** Pilha de "desfazer": guarda cópias do estado (em texto) tiradas antes das alterações. */
class UndoStack(private val max: Int = 8, private val minGapMs: Long = 1500) {
    private val items = ArrayDeque<String>()
    private var lastMs = 0L
    val size get() = items.size

    /** Guarda uma cópia, no máximo uma a cada [minGapMs] para não encher a pilha com digitação. */
    fun push(snapshot: () -> String, nowMs: Long): Boolean {
        if (items.isNotEmpty() && nowMs - lastMs < minGapMs) return false
        items.addLast(snapshot()); lastMs = nowMs
        while (items.size > max) items.removeFirst()
        return true
    }
    fun pop(): String? = items.removeLastOrNull().also { lastMs = 0L }
    fun clear() { items.clear(); lastMs = 0L }
}
