package info.skyblond.oam.command.media

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.multiple
import com.github.ajalt.clikt.parameters.options.*
import com.github.ajalt.clikt.parameters.types.int
import com.github.ajalt.clikt.parameters.types.path
import info.skyblond.oam.MB
import info.skyblond.oam.datastore.FilesOnMedia
import info.skyblond.oam.datastore.Medias
import info.skyblond.oam.sha3
import info.skyblond.oam.unixString
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.*
import kotlin.io.path.fileSize
import kotlin.io.path.relativeToOrNull

object WalkCommand : CliktCommand(
    help = "Walk a given media to append, update or verify files."
) {
    private val append by option("--append", "-a").flag()
        .help("Append new files to index")
    private val update by option("--update", "-u").flag()
        .help("Update existing file indexes")
    private val verify by option("--verify", "-v").flag()
        .help("Verify existing file indexes")

    private val verbose by option("--verbose").flag()
        .help("Print what is skipped or overwrote")

    private val bufferSize by option("-b", "--buffer-size").int()
        .default(32)
        .help("buffer size (in MB) for hash calculation, by default is 32 MB")
        .check("buffer size must be positive") { it > 0 }

    private val pathList by argument("path").path(mustExist = true, canBeFile = false)
        .multiple()

    override fun run() {
        val mediaId = MediaCommand.mediaId

        if (append) {
            // for appending, create media if not exists
            transaction {
                if (!Medias.existById(MediaCommand.mediaId)) {
                    Medias.insert(MediaCommand.mediaId, "unknown", "unknown")
                }
            }
        } else {
            // otherwise, require existing media
            if (transaction { !Medias.existById(MediaCommand.mediaId) }) {
                echo("Media id $mediaId not found", err = true)
                return
            }
        }

        val startTime = System.currentTimeMillis() / 1000
        echo("[I]Start timestamp: $startTime")
        val corruptedFiles = LinkedList<String>()
        var totalFileCounter = 0L
        var existFileCounter = 0L
        var newFileCounter = 0L
        var verifiedFileCounter = 0L
        var updatedFileCounter = 0L
        pathList.forEach { path ->
            echo("[I]Listing $path")
            val listOfPaths = listFiles(path)
            totalFileCounter += listOfPaths.size
            echo("[I]Listed ${listOfPaths.size} file(s)")
            // calculate the hash, write to index with newer last seen
            for (p in listOfPaths) {
                val relativePathString = p.relativeToOrNull(path)!!.unixString
                val size = p.fileSize()
                if (transaction { FilesOnMedia.existByMediaIdAndPath(mediaId, relativePathString) }) {
                    existFileCounter++
                    // file exists, test verify and update
                    if (!update && !verify) {
                        if (verbose) echo("[D]Skipping existing file: $relativePathString")
                        continue
                    }
                } else {
                    newFileCounter++
                    // not exist, test append
                    if (!append) {
                        if (verbose) echo("[D]Skipping new file: $relativePathString")
                        continue
                    }
                }
                // now we can calculate hash
                echo("[I]Calculating hash for $relativePathString")
                val t = System.currentTimeMillis() / 1000
                val hash = p.sha3(bufferSize * MB)
                val dt = System.currentTimeMillis() / 1000 - t
                echo(
                    "[I]Finished $relativePathString, " +
                            "$dt s, ${size / 1024 / 1024 / dt} MB/s"
                )

                val dbHash = transaction {
                    FilesOnMedia.selectByMediaIdAndPath(mediaId, relativePathString)
                        ?.get(FilesOnMedia.sha3Hash256)
                }

                // if dbHash == null: file doesn't exist, no need to verify
                if (verify && dbHash != null) {
                    verifiedFileCounter++
                    if (verbose) echo("[D]Verifying $relativePathString")

                    // checking the hash
                    if (!hash.contentEquals(dbHash)) {
                        // error
                        echo(
                            "[E]Hash not match: $relativePathString",
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

                if (append || update) {
                    updatedFileCounter++
                    if (verbose) echo("[D]Updating index of $relativePathString")
                    transaction {
                        FilesOnMedia.insertOrUpdate(
                            mediaId = mediaId,
                            path = relativePathString,
                            size = size,
                            sha3Hash256 = hash
                        )
                    }
                }
            }
        }

        // delete old files only when update or verify
        if (update || verify) {
            transaction {
                FilesOnMedia.deleteWhere {
                    (FilesOnMedia.mediaId eq mediaId) and (lastSeen less startTime)
                }
                if (verify) {
                    corruptedFiles.forEach {
                        FilesOnMedia.deleteByMediaIdAndPath(
                            mediaId, it
                        )
                    }
                }
            }
        }
        // update media last seen
        if (append || update || verify)
            transaction {
                Medias.updateById(
                    mediaId,
                    lastSeen = System.currentTimeMillis() / 1000
                )
            }

        if (append) echo("[I]${newFileCounter} new file(s)")
        if (update) echo("[I]${updatedFileCounter - newFileCounter} changed file(s)")
        if (verify) echo("[I]${existFileCounter} verified file(s)")
        if (verify) echo("[I]${corruptedFiles.size} corrupted file(s)")

        echo("[I]Done")
    }
}
