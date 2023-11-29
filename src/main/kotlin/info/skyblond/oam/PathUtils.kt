package info.skyblond.oam

import org.bouncycastle.crypto.digests.SHA3Digest
import java.nio.ByteBuffer
import java.nio.file.*
import java.nio.file.attribute.UserDefinedFileAttributeView
import java.util.*
import kotlin.io.path.*

private const val KB = 1024
private const val MB = 1024 * KB

private fun calculateSha3(
    digest: SHA3Digest,
    file: Path,
    bufferSize: Int
): ByteArray = file.inputStream(StandardOpenOption.READ).use { fis ->
    val buffer = ByteArray(bufferSize)
    while (true) {
        val read = fis.read(buffer)
        if (read == -1) break
        digest.update(buffer, 0, read)
    }
    val output = ByteArray(digest.digestSize)
    digest.doFinal(output, 0)
    output
}

/**
 * Calculate the SHA3-256 of a given. By default, use buffer size of 256MB.
 * */
fun Path.sha3(
    bufferSize: Int = 128 * MB
): ByteArray = calculateSha3(SHA3Digest(256), this, bufferSize)

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
