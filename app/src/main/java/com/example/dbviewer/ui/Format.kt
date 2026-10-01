package com.example.dbviewer.ui

import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val countFormat = DecimalFormat("#,###")

fun formatCount(value: Long): String = countFormat.format(value)

fun formatElapsed(ms: Long?): String = when {
    ms == null -> "—"
    ms < 1000 -> "$ms ms"
    else -> String.format(Locale.US, "%.2f s", ms / 1000.0)
}

fun formatBytes(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
    bytes < 1024L * 1024 * 1024 -> String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024))
    else -> String.format(Locale.US, "%.2f GB", bytes / (1024.0 * 1024 * 1024))
}

fun formatTime(at: Long): String = SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault()).format(Date(at))

fun objectKindLabel(kind: String): String = when (kind.lowercase()) {
    "table" -> "表"
    "view" -> "视图"
    "index" -> "索引"
    "trigger" -> "触发器"
    else -> kind
}

/** Width of a string in monospace cells; CJK glyphs occupy roughly two cells. */
fun visualLength(text: String): Int = text.fold(0) { total, character -> total + if (character.code > 0x2E7F) 2 else 1 }

/** Turns an object name into something safe to hand to the storage access framework. */
fun exportFileName(table: String, extension: String): String {
    val safe = table.map { if (it.isLetterOrDigit() || it == '_' || it == '-') it else '_' }.joinToString("")
    // A name made only of separators is useless as a file name, so fall back to a generic one.
    val usable = if (safe.any { it.isLetterOrDigit() }) safe else "export"
    return "$usable.$extension"
}
