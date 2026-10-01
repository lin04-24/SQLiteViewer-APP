package com.example.dbviewer.ui

/** A finished export waiting for the user to pick a destination file. */
data class ExportRequest(val fileName: String, val mimeType: String, val content: String)

fun buildCsv(columns: List<String>, rows: List<List<String?>>): String {
    val builder = StringBuilder()
    builder.append(columns.joinToString(",") { escapeCsv(it) }).append('\n')
    rows.forEach { row ->
        builder.append(columns.indices.joinToString(",") { escapeCsv(row.getOrNull(it)) }).append('\n')
    }
    return builder.toString()
}

private fun escapeCsv(value: String?): String {
    if (value == null) return ""
    val escaped = value.replace("\"", "\"\"")
    return if (escaped.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"$escaped\"" else escaped
}

fun buildJson(columns: List<String>, rows: List<List<String?>>): String {
    val builder = StringBuilder("[\n")
    rows.forEachIndexed { rowIndex, row ->
        builder.append("  {")
        columns.forEachIndexed { columnIndex, name ->
            builder.append(jsonString(name)).append(": ").append(jsonValue(row.getOrNull(columnIndex)))
            if (columnIndex != columns.lastIndex) builder.append(", ")
        }
        builder.append('}')
        if (rowIndex != rows.lastIndex) builder.append(',')
        builder.append('\n')
    }
    builder.append("]\n")
    return builder.toString()
}

/** Matches the JSON number grammar, so text such as "007" or "1." stays quoted. */
private val JSON_NUMBER = Regex("^-?(0|[1-9]\\d*)(\\.\\d+)?([eE][+-]?\\d+)?$")

private fun jsonValue(value: String?): String {
    if (value == null) return "null"
    // Blob markers and other synthetic values must stay strings even when they look numeric.
    val numeric = value.none { it == '<' } && JSON_NUMBER.matches(value)
    return if (numeric) value else jsonString(value)
}

private fun jsonString(value: String): String {
    val builder = StringBuilder("\"")
    value.forEach { character ->
        when (character) {
            '"' -> builder.append("\\\"")
            '\\' -> builder.append("\\\\")
            '\n' -> builder.append("\\n")
            '\r' -> builder.append("\\r")
            '\t' -> builder.append("\\t")
            else -> if (character.code < 0x20) builder.append(String.format("\\u%04x", character.code)) else builder.append(character)
        }
    }
    return builder.append('"').toString()
}
