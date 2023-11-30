package info.skyblond.oam.command.media

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.check
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.int
import com.github.ajalt.clikt.parameters.types.path
import info.skyblond.oam.datastore.FilesOnMedia
import info.skyblond.oam.datastore.Medias
import info.skyblond.oam.sha3
import info.skyblond.oam.unixString
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.*
import kotlin.io.path.relativeToOrNull

object VerifyCommand : CliktCommand(
    help = "Verify a given media to ensure the data is correct"
) {
    private val bufferSize by option("-b", "--buffer-size").int()
        .default(128)
        .help("buffer size (in MB) for hash calculation, by default is 128MB")
        .check("buffer size must be positive") { it > 0 }

    private val path by argument("path").path(mustExist = true, canBeFile = false)
    override fun run() {
        val mediaId = MediaCommand.mediaId
        // create media if not exists
        if (transaction { !Medias.existById(mediaId) }) {
            echo("[E]Media id $mediaId not found", err = true)
            return
        }
        // sorted by starting block, thus we only run the tape once
        echo("[I]Listing files to verify")
        val listOfPaths = listFiles(path)
        echo("[I]Listed ${listOfPaths.size} file(s), verifying... Only issues will be shown")

        val corruptedFiles = LinkedList<String>()
        listOfPaths.forEach { p ->
            val relativePathString = p.relativeToOrNull(path)!!.unixString
            val dbHash = transaction {
                FilesOnMedia.selectByMediaIdAndPath(mediaId, relativePathString)
                    ?.get(FilesOnMedia.sha3Hash256)
            }
            if (dbHash == null) {
                echo(
                    "[E]Index missing for $relativePathString, need rescan",
                    err = true
                )
                corruptedFiles.add(relativePathString)
                return@forEach
            }

            val actualHash = p.sha3(bufferSize)

            // checking the hash
            if (!actualHash.contentEquals(dbHash)) {
                // error
                echo(
                    "[E]Corrupted file $relativePathString, should solve before rescan",
                    err = true
                )
                corruptedFiles.add(relativePathString)
            } else {
                // ok, update file last seen
                transaction {
                    FilesOnMedia.updateByMediaIdAndPath(
                        mediaId, relativePathString,
                        lastSeen = System.currentTimeMillis() / 1000
                    )
                }
            }
        }
        val remainFileCount = transaction {
            corruptedFiles.forEach {
                FilesOnMedia.deleteByMediaIdAndPath(
                    mediaId, it
                )
            }
            Medias.updateById(
                mediaId,
                lastSeen = System.currentTimeMillis() / 1000
            )
            FilesOnMedia.selectByMediaId(mediaId).count()
        }
        echo("${corruptedFiles.size} corrupted file(s) removed")
        echo("$remainFileCount remaining file(s) in good condition")
    }
}
