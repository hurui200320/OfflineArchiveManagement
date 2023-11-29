package info.skyblond.oam.command.media

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.types.path
import info.skyblond.oam.datastore.FilesOnMedia
import info.skyblond.oam.datastore.Medias
import info.skyblond.oam.sha3
import info.skyblond.oam.unixString
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.lessEq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.transactions.transaction
import kotlin.io.path.fileSize
import kotlin.io.path.relativeToOrNull

object ScanCommand : CliktCommand(
    help = "Scan a given media to calculate hash and update the index"
) {
    private val path by argument("path").path(mustExist = true, canBeFile = false)
    override fun run() {
        val mediaId = MediaCommand.mediaId
        // create media if not exists
        transaction {
            if (!Medias.existById(MediaCommand.mediaId)) {
                Medias.insert(MediaCommand.mediaId, "unknown", "unknown")
            }
        }

        echo("[I]Listing files...")
        val listOfPaths = listFiles(path)
        echo("[I]Listed ${listOfPaths.size} file(s)")
        val startTime = System.currentTimeMillis() / 1000
        // calculate the hash, write to index with newer last seen
        listOfPaths.map { p ->
            val relativePathString = p.relativeToOrNull(path)!!.unixString
            echo("[I]Scanning $relativePathString")
            val size = p.fileSize()
            val t = System.currentTimeMillis() / 1000
            val hash = p.sha3() // TODO: option for buffer size?
            val t2 = System.currentTimeMillis() / 1000 - t
            echo("[I]Finished $relativePathString, (${size / 1024 / 1024 / t2})MB/s")
            transaction {
                FilesOnMedia.insertOrUpdate(
                    mediaId = mediaId,
                    path = relativePathString,
                    size = size,
                    sha3Hash256 = hash
                )
            }
            p to hash
        }
        // finally delete old files
        transaction {
            FilesOnMedia.deleteWhere {
                (FilesOnMedia.mediaId eq mediaId) and (lastSeen lessEq startTime)
            }
        }
        echo(
            "[I]Done"
        )
    }
}
