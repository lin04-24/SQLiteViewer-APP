package com.example.dbviewer

import com.example.dbviewer.ui.formatBytes
import com.example.dbviewer.ui.formatCount
import com.example.dbviewer.ui.formatElapsed
import com.example.dbviewer.ui.objectKindLabel
import com.example.dbviewer.ui.visualLength
import org.junit.Assert.assertEquals
import org.junit.Test

class FormatTest {

    @Test
    fun countUsesGrouping() {
        assertEquals("0", formatCount(0))
        assertEquals("106", formatCount(106))
        assertEquals("1,234,567", formatCount(1_234_567))
    }

    @Test
    fun elapsedSwitchesToSeconds() {
        assertEquals("—", formatElapsed(null))
        assertEquals("0 ms", formatElapsed(0))
        assertEquals("950 ms", formatElapsed(950))
        assertEquals("1.50 s", formatElapsed(1500))
    }

    @Test
    fun bytesScale() {
        assertEquals("512 B", formatBytes(512))
        assertEquals("1.5 KB", formatBytes(1536))
        assertEquals("2.0 MB", formatBytes(2L * 1024 * 1024))
        assertEquals("1.50 GB", formatBytes(1024L * 1024 * 1024 * 3 / 2))
    }

    @Test
    fun objectKindsAreLocalised() {
        assertEquals("表", objectKindLabel("table"))
        assertEquals("视图", objectKindLabel("view"))
        assertEquals("索引", objectKindLabel("index"))
        assertEquals("触发器", objectKindLabel("trigger"))
        assertEquals("other", objectKindLabel("other"))
    }

    @Test
    fun visualLengthCountsWideGlyphsTwice() {
        assertEquals(0, visualLength(""))
        assertEquals(2, visualLength("id"))
        assertEquals(4, visualLength("名称"))
        assertEquals(4, visualLength("a名b"))
    }
}
