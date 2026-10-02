package xyz.blacksheep.mjolnir.utils

import android.os.Build
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.channels.FileChannel
import java.nio.file.StandardOpenOption

/**
 * Crash-safe storage for Mjolnir's small, human-editable config files.
 *
 * **Why:** A plain write-then-rename is not durable. If the device loses power or panics before the
 * kernel flushes the data, the renamed file can survive as an empty or NUL-filled file, which the
 * readers then treat as "no settings" (observed in the field after an unclean reboot).
 *
 * **How:**
 * - [write] stores the content durably (fsync) in a sibling `.bak` copy first, then in a temp file
 *   that is fsync'd and renamed over the target. A crash at any point can tear at most one copy.
 * - [read] returns the primary file when it is readable and passes validation. Only a primary that
 *   exists but is corrupt falls back to the backup (it is preserved as `.corrupt` for inspection);
 *   a deleted primary is honored, since users may delete files to reset them.
 */
object DurableFiles {
    private const val TAG = "DurableFiles"

    fun write(file: File, content: String) {
        val dir = file.parentFile
        dir?.mkdirs()
        val bytes = content.toByteArray(Charsets.UTF_8)

        writeSynced(backupOf(file), bytes)

        val tmp = File(dir, file.name + ".tmp")
        writeSynced(tmp, bytes)
        if (!tmp.renameTo(file)) {
            writeSynced(file, bytes)
            tmp.delete()
        }
        syncDirectory(dir)
    }

    enum class Source { PRIMARY, BACKUP, NONE }

    data class ReadResult(val text: String?, val source: Source)

    /**
     * Reads [file], falling back to its backup copy when [file] exists but is corrupt.
     *
     * @param isValid Format-specific check; content containing NUL bytes is always rejected.
     * @return The first valid content and where it came from; `text` is `null` if neither copy
     * exists or is valid.
     */
    fun read(file: File, isValid: (String) -> Boolean): ReadResult {
        if (!file.exists()) return ReadResult(null, Source.NONE)

        val primary = readValid(file, isValid)
        if (primary != null) return ReadResult(primary, Source.PRIMARY)

        Log.w(TAG, "${file.name} is unreadable or invalid; trying backup")
        try {
            file.copyTo(File(file.parentFile, file.name + ".corrupt"), overwrite = true)
        } catch (e: IOException) {
            Log.w(TAG, "Failed to preserve corrupt ${file.name}: ${e.message}")
        }
        val backup = readValid(backupOf(file), isValid)
        return if (backup != null) ReadResult(backup, Source.BACKUP) else ReadResult(null, Source.NONE)
    }

    /** Deletes the file together with its backup copy. */
    fun delete(file: File): Boolean {
        backupOf(file).delete()
        return file.delete()
    }

    fun backupOf(file: File): File = File(file.parentFile, file.name + ".bak")

    private fun readValid(file: File, isValid: (String) -> Boolean): String? {
        if (!file.isFile) return null
        val text = try {
            file.readText()
        } catch (e: IOException) {
            return null
        }
        if (text.indexOf('\u0000') >= 0) return null
        return if (isValid(text)) text else null
    }

    private fun writeSynced(file: File, bytes: ByteArray) {
        FileOutputStream(file).use { out ->
            out.write(bytes)
            out.flush()
            out.fd.sync()
        }
    }

    private fun syncDirectory(dir: File?) {
        if (dir == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        try {
            FileChannel.open(dir.toPath(), StandardOpenOption.READ).use { it.force(true) }
        } catch (e: Exception) {
            // Not supported on every filesystem; the file contents are already synced.
        }
    }
}
