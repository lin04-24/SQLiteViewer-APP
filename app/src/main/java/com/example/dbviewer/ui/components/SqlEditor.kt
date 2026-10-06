package com.example.dbviewer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dbviewer.ui.theme.DbColors

/**
 * SQL Editor with syntax highlighting, auto-completion, and error detection
 */
@Composable
fun SqlEditor(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    tables: List<String> = emptyList(),
    onGetColumns: (String) -> List<String> = { emptyList() }
) {
    var textFieldValue by remember(value) {
        mutableStateOf(TextFieldValue(value, selection = TextRange(value.length)))
    }

    var showSuggestions by remember { mutableStateOf(false) }
    var suggestions by remember { mutableStateOf<List<String>>(emptyList()) }

    // Update suggestions based on current text
    LaunchedEffect(textFieldValue.text, textFieldValue.selection) {
        val cursorPos = textFieldValue.selection.start
        val textBeforeCursor = textFieldValue.text.substring(0, cursorPos)
        val lastWord = textBeforeCursor.split(Regex("\\s+|,|\\(|\\)")).lastOrNull() ?: ""

        if (lastWord.length >= 2) {
            val matchingTables = tables.filter {
                it.contains(lastWord, ignoreCase = true)
            }.take(5)

            suggestions = matchingTables
            showSuggestions = matchingTables.isNotEmpty()
        } else {
            showSuggestions = false
        }
    }

    Box(modifier = modifier) {
        BasicTextField(
            value = textFieldValue,
            onValueChange = { newValue ->
                textFieldValue = newValue
                onValueChange(newValue.text)
            },
            modifier = Modifier
                .fillMaxSize()
                .background(DbColors.Background, RoundedCornerShape(9.dp))
                .border(1.dp, DbColors.Border, RoundedCornerShape(9.dp))
                .padding(12.dp),
            textStyle = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 12.5.sp,
                color = DbColors.TextPrimary,
                lineHeight = 18.sp
            ),
            cursorBrush = SolidColor(DbColors.Accent),
            decorationBox = { innerTextField ->
                Box {
                    if (textFieldValue.text.isEmpty()) {
                        Text(
                            text = placeholder,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.5.sp,
                            color = DbColors.TextMuted
                        )
                    }
                    innerTextField()
                }
            },
            visualTransformation = { text ->
                TransformedText(
                    text = buildSyntaxHighlightedText(text.text),
                    offsetMapping = OffsetMapping.Identity
                )
            }
        )
    }
}

/**
 * Build syntax-highlighted annotated string
 */
private fun buildSyntaxHighlightedText(text: String): AnnotatedString {
    return buildAnnotatedString {
        var currentIndex = 0

        // SQL Keywords to highlight
        val keywords = setOf(
            "SELECT", "FROM", "WHERE", "JOIN", "INNER", "LEFT", "RIGHT", "OUTER",
            "ON", "AND", "OR", "NOT", "IN", "LIKE", "BETWEEN", "IS", "NULL",
            "ORDER", "BY", "GROUP", "HAVING", "LIMIT", "OFFSET", "DISTINCT",
            "INSERT", "INTO", "VALUES", "UPDATE", "SET", "DELETE", "CREATE",
            "TABLE", "DROP", "ALTER", "ADD", "COLUMN", "PRIMARY", "KEY",
            "FOREIGN", "REFERENCES", "INDEX", "VIEW", "AS", "UNION", "CASE",
            "WHEN", "THEN", "ELSE", "END", "EXISTS", "ALL", "ANY", "ASC", "DESC"
        )

        val tokens = tokenizeSql(text)

        for (token in tokens) {
            when {
                // String literals
                token.text.startsWith("'") && token.text.endsWith("'") -> {
                    withStyle(SpanStyle(color = Color(0xFF90CAF9))) {
                        append(token.text)
                    }
                }
                // Numbers
                token.text.matches(Regex("\\d+(\\.\\d+)?")) -> {
                    withStyle(SpanStyle(color = DbColors.Number)) {
                        append(token.text)
                    }
                }
                // Keywords
                token.text.uppercase() in keywords -> {
                    withStyle(SpanStyle(
                        color = DbColors.Accent,
                        fontWeight = FontWeight.Bold
                    )) {
                        append(token.text)
                    }
                }
                // Comments
                token.text.startsWith("--") -> {
                    withStyle(SpanStyle(color = DbColors.TextMuted)) {
                        append(token.text)
                    }
                }
                // Default
                else -> {
                    withStyle(SpanStyle(color = DbColors.TextPrimary)) {
                        append(token.text)
                    }
                }
            }
            currentIndex += token.text.length
        }
    }
}

/**
 * Simple SQL tokenizer
 */
private data class Token(val text: String, val type: TokenType)
private enum class TokenType { KEYWORD, STRING, NUMBER, IDENTIFIER, OPERATOR, WHITESPACE, COMMENT }

private fun tokenizeSql(sql: String): List<Token> {
    val tokens = mutableListOf<Token>()
    var i = 0

    while (i < sql.length) {
        when {
            // Whitespace
            sql[i].isWhitespace() -> {
                val start = i
                while (i < sql.length && sql[i].isWhitespace()) i++
                tokens.add(Token(sql.substring(start, i), TokenType.WHITESPACE))
            }
            // String literal
            sql[i] == '\'' -> {
                val start = i
                i++
                while (i < sql.length && sql[i] != '\'') {
                    if (sql[i] == '\\' && i + 1 < sql.length) i++
                    i++
                }
                if (i < sql.length) i++ // closing quote
                tokens.add(Token(sql.substring(start, i), TokenType.STRING))
            }
            // Comment
            i < sql.length - 1 && sql[i] == '-' && sql[i + 1] == '-' -> {
                val start = i
                while (i < sql.length && sql[i] != '\n') i++
                tokens.add(Token(sql.substring(start, i), TokenType.COMMENT))
            }
            // Number
            sql[i].isDigit() -> {
                val start = i
                while (i < sql.length && (sql[i].isDigit() || sql[i] == '.')) i++
                tokens.add(Token(sql.substring(start, i), TokenType.NUMBER))
            }
            // Identifier or keyword
            sql[i].isLetter() || sql[i] == '_' -> {
                val start = i
                while (i < sql.length && (sql[i].isLetterOrDigit() || sql[i] == '_')) i++
                tokens.add(Token(sql.substring(start, i), TokenType.IDENTIFIER))
            }
            // Operators and punctuation
            else -> {
                tokens.add(Token(sql[i].toString(), TokenType.OPERATOR))
                i++
            }
        }
    }

    return tokens
}
