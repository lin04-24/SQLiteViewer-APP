package com.example.dbviewer.ui.components

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.graphics.Color

/**
 * SQL syntax error detector
 */
object SqlValidator {

    /**
     * Common SQL syntax errors to detect
     */
    private val errorPatterns = listOf(
        // Unclosed quotes
        Regex("""'[^']*$""") to "未闭合的单引号",
        Regex(""""[^"]*$""") to "未闭合的双引号",

        // Unmatched parentheses
        Regex("""\([^)]*$""") to "未闭合的括号",

        // Missing FROM clause
        Regex("""(?i)\bSELECT\s+[^;]*\bWHERE\b""") to "可能缺少 FROM 子句",

        // Comma issues
        Regex("""(?i),\s*FROM\b""") to "FROM 前的多余逗号",
        Regex("""(?i),\s*WHERE\b""") to "WHERE 前的多余逗号",
        Regex("""(?i)\bSELECT\s+,""") to "SELECT 后缺少列名",

        // Missing semicolon at end (optional)
        Regex("""[^;]\s*$""") to "语句可能需要以分号结尾"
    )

    /**
     * Validate SQL and return error positions
     */
    fun validate(sql: String): List<SqlError> {
        val errors = mutableListOf<SqlError>()

        // Check for basic syntax errors
        if (sql.isBlank()) {
            return errors
        }

        // Check parentheses balance
        var parenDepth = 0
        var firstUnmatchedParen = -1
        sql.forEachIndexed { index, char ->
            when (char) {
                '(' -> {
                    parenDepth++
                    if (firstUnmatchedParen == -1) {
                        firstUnmatchedParen = index
                    }
                }
                ')' -> {
                    parenDepth--
                    if (parenDepth < 0) {
                        errors.add(SqlError(index, index + 1, "多余的右括号"))
                        parenDepth = 0
                    } else if (parenDepth == 0) {
                        firstUnmatchedParen = -1
                    }
                }
            }
        }

        if (parenDepth > 0 && firstUnmatchedParen >= 0) {
            errors.add(SqlError(firstUnmatchedParen, firstUnmatchedParen + 1, "未闭合的左括号"))
        }

        // Check quote balance
        var inSingleQuote = false
        var inDoubleQuote = false
        var lastQuotePos = -1

        sql.forEachIndexed { index, char ->
            when (char) {
                '\'' -> {
                    if (!inDoubleQuote && (index == 0 || sql[index - 1] != '\\')) {
                        if (!inSingleQuote) {
                            lastQuotePos = index
                        }
                        inSingleQuote = !inSingleQuote
                    }
                }
                '"' -> {
                    if (!inSingleQuote && (index == 0 || sql[index - 1] != '\\')) {
                        if (!inDoubleQuote) {
                            lastQuotePos = index
                        }
                        inDoubleQuote = !inDoubleQuote
                    }
                }
            }
        }

        if (inSingleQuote && lastQuotePos >= 0) {
            errors.add(SqlError(lastQuotePos, lastQuotePos + 1, "未闭合的单引号"))
        }

        if (inDoubleQuote && lastQuotePos >= 0) {
            errors.add(SqlError(lastQuotePos, lastQuotePos + 1, "未闭合的双引号"))
        }

        // Check for common keyword errors
        val upperSql = sql.uppercase()

        // Missing FROM between SELECT and WHERE
        val selectMatch = Regex("""(?i)\bSELECT\b""").find(sql)
        val whereMatch = Regex("""(?i)\bWHERE\b""").find(sql)
        val fromMatch = Regex("""(?i)\bFROM\b""").find(sql)

        if (selectMatch != null && whereMatch != null && fromMatch == null) {
            errors.add(SqlError(whereMatch.range.first, whereMatch.range.last + 1, "WHERE 前缺少 FROM 子句"))
        }

        return errors
    }

    /**
     * Build annotated string with error highlighting
     */
    fun buildAnnotatedStringWithErrors(sql: String, errors: List<SqlError>): AnnotatedString {
        return buildAnnotatedString {
            append(sql)

            errors.forEach { error ->
                if (error.start < sql.length) {
                    val end = minOf(error.end, sql.length)
                    addStyle(
                        SpanStyle(
                            background = Color(0x40FF6B6B),
                            color = Color(0xFFFF6B6B)
                        ),
                        error.start,
                        end
                    )
                }
            }
        }
    }
}

/**
 * SQL error data class
 */
data class SqlError(
    val start: Int,
    val end: Int,
    val message: String
)
