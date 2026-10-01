package com.example.dbviewer

import com.example.dbviewer.ui.buildCsv
import com.example.dbviewer.ui.buildJson
import com.example.dbviewer.ui.exportFileName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExportTest {

    @Test
    fun csvWritesHeaderAndRows() {
        val csv = buildCsv(listOf("id", "name"), listOf(listOf("1", "test"), listOf("2", "other")))
        assertEquals("id,name\n1,test\n2,other\n", csv)
    }

    @Test
    fun csvQuotesSeparatorsQuotesAndNewlines() {
        val csv = buildCsv(listOf("a", "b", "c"), listOf(listOf("x,y", "he said \"hi\"", "line1\nline2")))
        assertEquals("a,b,c\n\"x,y\",\"he said \"\"hi\"\"\",\"line1\nline2\"\n", csv)
    }

    @Test
    fun csvRendersNullAsEmptyField() {
        assertEquals("a,b\n,\n", buildCsv(listOf("a", "b"), listOf(listOf(null, null))))
    }

    @Test
    fun jsonKeepsNumbersBareAndStringsQuoted() {
        val json = buildJson(listOf("id", "name", "price"), listOf(listOf("1", "test", "100.5")))
        assertEquals("[\n  {\"id\": 1, \"name\": \"test\", \"price\": 100.5}\n]\n", json)
    }

    @Test
    fun jsonRendersNullWithoutQuotes() {
        assertEquals("[\n  {\"a\": null}\n]\n", buildJson(listOf("a"), listOf(listOf(null))))
    }

    @Test
    fun jsonEscapesControlCharactersAndQuotes() {
        val json = buildJson(listOf("a"), listOf(listOf("he said \"hi\"\nnext\tcol")))
        assertEquals("[\n  {\"a\": \"he said \\\"hi\\\"\\nnext\\tcol\"}\n]\n", json)
    }

    @Test
    fun jsonKeepsNumericLookingTextQuoted() {
        // "007" and "1." are not valid JSON numbers, so they must stay strings.
        assertEquals("[\n  {\"a\": \"007\", \"b\": \"1.\", \"c\": \"+5\"}\n]\n", buildJson(listOf("a", "b", "c"), listOf(listOf("007", "1.", "+5"))))
    }

    @Test
    fun jsonAcceptsExponentAndNegativeNumbers() {
        assertEquals("[\n  {\"a\": -2, \"b\": 1e5}\n]\n", buildJson(listOf("a", "b"), listOf(listOf("-2", "1e5"))))
    }

    @Test
    fun jsonKeepsBlobMarkersQuoted() {
        assertEquals("[\n  {\"a\": \"<BLOB 4 B>\"}\n]\n", buildJson(listOf("a"), listOf(listOf("<BLOB 4 B>"))))
    }

    @Test
    fun jsonSeparatesRowsWithCommas() {
        val json = buildJson(listOf("a"), listOf(listOf("1"), listOf("2")))
        assertEquals("[\n  {\"a\": 1},\n  {\"a\": 2}\n]\n", json)
    }

    @Test
    fun exportFileNameStripsUnsafeCharacters() {
        assertEquals("item.csv", exportFileName("item", "csv"))
        assertEquals("my_table-1.json", exportFileName("my table-1", "json"))
        assertEquals("a_b.csv", exportFileName("a/b", "csv"))
        assertEquals("export.csv", exportFileName("***", "csv"))
    }

    @Test
    fun csvRoundTripsThroughAQuoteAwareReader() {
        val rows = listOf(listOf("plain", "with,comma", "with\"quote", "with\nnewline"))
        val csv = buildCsv(listOf("a", "b", "c", "d"), rows)
        assertEquals(rows, parseCsv(csv).drop(1))
    }

    /** Minimal RFC4180 reader used only to prove the writer output is well formed. */
    private fun parseCsv(text: String): List<List<String>> {
        val records = mutableListOf<List<String>>()
        var field = StringBuilder()
        var record = mutableListOf<String>()
        var quoted = false
        var index = 0
        while (index < text.length) {
            val character = text[index]
            when {
                quoted && character == '"' && index + 1 < text.length && text[index + 1] == '"' -> { field.append('"'); index++ }
                character == '"' -> quoted = !quoted
                !quoted && character == ',' -> { record.add(field.toString()); field = StringBuilder() }
                !quoted && character == '\n' -> { record.add(field.toString()); records.add(record); record = mutableListOf(); field = StringBuilder() }
                else -> field.append(character)
            }
            index++
        }
        if (field.isNotEmpty() || record.isNotEmpty()) { record.add(field.toString()); records.add(record) }
        return records
    }

    @Test
    fun exportRequestCarriesMimeType() {
        val request = com.example.dbviewer.ui.ExportRequest("item.csv", "text/csv", "a\n1\n")
        assertEquals("item.csv", request.fileName)
        assertTrue(request.content.endsWith("\n"))
    }
}
