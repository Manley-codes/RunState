package com.runstate.mobile.reflection

import java.io.DataInputStream
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Six replaceable slots in Android's private, non-backed-up cache. Bind each recording to
 * its exact text and destination/credential fingerprint. Bump the profile when changing
 * the backend run voice, model, speed or encoding.
 */
internal class FixedRunVoiceFiles(private val directory: File) {
    private val access = Mutex()
    private val profile = "run-voice-v1-eleven-multilingual-v2-speed-0.9"

    suspend fun read(key: String, text: String): ByteArray? = withContext(Dispatchers.IO) {
        val file = slot(text) ?: return@withContext null
        access.withLock {
            try {
                val size = file.length() - 32
                if (size !in 1L..MAX_REFLECTION_AUDIO_BYTES.toLong()) return@withLock null
                DataInputStream(file.inputStream().buffered()).use { input ->
                    val expected = ByteArray(32)
                    val bytes = ByteArray(size.toInt())
                    input.readFully(expected)
                    input.readFully(bytes)
                    bytes.takeIf { input.read() == -1 && MessageDigest.isEqual(expected, digest(key, text, it)) }
                }
            } catch (_: IOException) { null }
        }
    }

    suspend fun write(key: String, text: String, audio: ByteArray, stillSelected: () -> Boolean): Unit =
        withContext(Dispatchers.IO) {
            val file = slot(text) ?: return@withContext
            if (audio.isEmpty() || audio.size > MAX_REFLECTION_AUDIO_BYTES) return@withContext
            access.withLock {
                if (!stillSelected()) return@withLock
                val pending = File(directory, "pending")
                try {
                    Files.createDirectories(directory.toPath())
                    pending.outputStream().use {
                        it.write(digest(key, text, audio))
                        it.write(audio)
                    }
                    Files.move(pending.toPath(), file.toPath(),
                        StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
                } catch (_: IOException) {
                    // A full or unavailable cache still permits this session's in-memory playback.
                } finally { pending.delete() }
            }
        }

    suspend fun clear(): Unit = withContext(Dispatchers.IO) {
        access.withLock {
            RUN_VOICE_PREPARED_LINES.forEach { slot(it)?.delete() }
            File(directory, "pending").delete()
        }
    }

    private fun slot(text: String): File? {
        val index = RUN_VOICE_PREPARED_LINES.indexOf(text)
        return if (index < 0) null else File(directory, "cue-$index")
    }

    private fun digest(key: String, text: String, audio: ByteArray): ByteArray {
        val sha = MessageDigest.getInstance("SHA-256")
        sha.update("$profile\u0000$key\u0000$text\u0000".toByteArray(Charsets.UTF_8))
        return sha.digest(audio)
    }
}
