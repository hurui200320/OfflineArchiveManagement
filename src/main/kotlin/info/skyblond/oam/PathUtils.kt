package info.skyblond.oam

import java.nio.file.AccessDeniedException
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
import java.util.*
import kotlin.io.path.*

internal const val KB = 1024
internal const val MB = 1024 * KB

private fun digest(
    algorithm: String,
    file: Path,
    bufferSize: Int
): ByteArray = file.inputStream(StandardOpenOption.READ)
    .buffered(bufferSize).use { fis ->
        val messageDigest = MessageDigest.getInstance(algorithm)
        val buffer = ByteArray(4 * MB)
        while (true) {
            val read = fis.read(buffer)
            if (read == -1) break
            messageDigest.update(buffer, 0, read)
        }
        messageDigest.digest()
    }

/**
 * Calculate the SHA3-256 of a given.
 * */
fun Path.sha3(
    bufferSize: Int
): ByteArray = digest("SHA3-256", this, bufferSize)

private fun Path.readLTFSAttr(name: String): String? = runCatching {
    val process = ProcessBuilder("ltfsattr", "-p", name, pathString)
        .redirectOutput(ProcessBuilder.Redirect.PIPE)
        .redirectError(ProcessBuilder.Redirect.PIPE)
        .start()
    val ret = process.waitFor()
    if (ret != 0) return null
    return process.inputReader(Charsets.UTF_8).use { it.readText() }
}.getOrNull()

/**
 * Get file's starting block number from LTFS using attr `ltfs.startblock`.
 * Return null if not found, which might be caused by ltfsattr not found,
 * or filesystem is not LTFS.
 * */
fun Path.readLTFSStartBlock(): Long? = readLTFSAttr("ltfs.startblock")?.trim()?.toLongOrNull()

fun Path.walkFile(
    block: (Path) -> Unit
) {
    val list = LinkedList<Path>()
    list.add(this)
    while (list.isNotEmpty()) {
        val p = list.poll()
        if (p.isDirectory(LinkOption.NOFOLLOW_LINKS)) {
            try { // put at the head, in case we're tape
                // ltfs.startblock
                list.addAll(p.listDirectoryEntries())
            } catch (_: AccessDeniedException) {
                // ignore inaccessible folder
            }
        } else if (p.isRegularFile()) {
            block(p)
        }
    }
}

val Path.unixString: String
    get() = (this.root?.pathString ?: "") + (0 until nameCount).map { getName(it) }.joinToString("/")
