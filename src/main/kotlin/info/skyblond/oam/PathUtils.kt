package info.skyblond.oam

import org.bouncycastle.crypto.digests.SHA3Digest
import java.nio.ByteBuffer
import java.nio.file.AccessDeniedException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardOpenOption
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
    bufferSize: Int = 256 * MB
): ByteArray = calculateSha3(SHA3Digest(256), this, bufferSize)

fun Path.listAttr(prefixFilter: String? = null) =
    Files.getFileAttributeView(this, UserDefinedFileAttributeView::class.java)
        .list()
        .filter { prefixFilter == null || it.startsWith(prefixFilter) }

fun Path.readAttr(name: String): ByteArray? {
    val attrs = Files.getFileAttributeView(this, UserDefinedFileAttributeView::class.java)
    val size = attrs.size(name)
    if (size < 0) return null
    val byte = ByteArray(size)
    attrs.read(name, ByteBuffer.wrap(byte))
    return byte
}

fun Path.writeAttr(name: String, value: ByteArray) {
    val attrs = Files.getFileAttributeView(this, UserDefinedFileAttributeView::class.java)
    attrs.write(name, ByteBuffer.wrap(value))
}

fun Path.walkFile(
    block: (Path) -> Unit
) {
    val list = LinkedList<Path>()
    list.add(this)
    while (list.isNotEmpty()) {
        val p = list.poll()
        if (p.isDirectory(LinkOption.NOFOLLOW_LINKS)) {
            try {
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
