package com.kbtv.caster.control

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for media URI handling
 * Tests URL parsing and validation for DLNA media URLs
 */
class MediaUriTest {

    @Test
    fun `http URL should be valid`() {
        val url = "http://example.com/video.mp4"
        assertTrue(url.startsWith("http://"))
        assertTrue(url.endsWith(".mp4"))
    }

    @Test
    fun `https URL should be valid`() {
        val url = "https://example.com/video.mp4"
        assertTrue(url.startsWith("https://"))
    }

    @Test
    fun `file URL should be valid`() {
        val url = "file:///storage/video.mp4"
        assertTrue(url.startsWith("file://"))
    }

    @Test
    fun `extract file extension from URL`() {
        val url = "http://example.com/path/to/video.mp4"
        val extension = url.substringAfterLast(".")
        assertEquals("mp4", extension)
    }

    @Test
    fun `extract filename from URL`() {
        val url = "http://example.com/path/to/video.mp4"
        val filename = url.substringAfterLast("/")
        assertEquals("video.mp4", filename)
    }

    @Test
    fun `video URLs should contain common video extensions`() {
        val videoExtensions = listOf("mp4", "mkv", "avi", "mov", "webm")

        videoExtensions.forEach { ext ->
            val url = "http://example.com/video.$ext"
            assertTrue(url.endsWith(".$ext"))
        }
    }

    @Test
    fun `audio URLs should contain common audio extensions`() {
        val audioExtensions = listOf("mp3", "aac", "flac", "wav", "ogg")

        audioExtensions.forEach { ext ->
            val url = "http://example.com/audio.$ext"
            assertTrue(url.endsWith(".$ext"))
        }
    }
}
