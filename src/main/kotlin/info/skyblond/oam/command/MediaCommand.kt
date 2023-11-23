package info.skyblond.oam.command

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.core.terminal
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.help
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.path
import info.skyblond.oam.*
import info.skyblond.oam.datastore.FilesOnMedia
import info.skyblond.oam.datastore.Medias
import org.bouncycastle.util.Pack
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.lessEq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.*
import kotlin.io.path.fileSize
import kotlin.io.path.pathString
import kotlin.io.path.relativeToOrNull

object MediaCommand : CliktCommand(
    help = "Operations related to a given media"
) {
    private val _mediaId: String by argument("media_id")

    lateinit var mediaId: String
        private set

    init {
        subcommands(
            ScanCommand,
            VerifyCommand,
            ListCommand,
            RemoveCommand,
            InfoCommand
        )
    }

    override fun run() {
        mediaId = _mediaId.trim()
    }
}

private object ScanCommand : CliktCommand(
    help = "Scan a given media to calculate hash and update the index"
) {
    private val path by argument("path").path(mustExist = true, canBeFile = false)
    override fun run() {
        val mediaId = MediaCommand.mediaId
        val startTime = System.currentTimeMillis() / 1000
        echo("[T+${System.currentTimeMillis() / 1000 - startTime}s]Listing files...")
        // sorted by starting block, thus we only run the tape once
        val listOfPaths = buildList { path.walkFile { add(it) } }
            .sortedBy { p ->
                // TODO: assuming it's long
                p.readAttr("ltfs.startblock")
                    ?.let { Pack.bigEndianToLong(it, 0) }
                    ?: Long.MAX_VALUE
            }
        echo("[T+${System.currentTimeMillis() / 1000 - startTime}s]Listed ${listOfPaths.size} file(s)")
        // calculate the hash, write to index with newer last seen
        listOfPaths.map { p ->
            val relativePathString = p.relativeToOrNull(path)!!.pathString
            echo("[T+${System.currentTimeMillis() / 1000 - startTime}s]Scanning $relativePathString")
            val size = p.fileSize()
            val hash = p.sha3() // TODO: option for buffer size?
            transaction {
                runCatching {
                    FilesOnMedia.insert(
                        mediaId = mediaId,
                        path = relativePathString,
                        size = size,
                        sha3Hash256 = hash
                    )
                }.onFailure {
                    FilesOnMedia.updateByMediaIdAndPath(
                        mediaId, relativePathString,
                        size = size, sha3Hash256 = hash,
                        lastSeen = System.currentTimeMillis() / 1000
                    )
                }
            }
            p to hash
        }.forEach {
            // then write all to fs attr
            it.first.writeAttr("user.oam.sha3.256", it.second)
        }
        // finally delete old files
        transaction {
            FilesOnMedia.deleteWhere {
                (FilesOnMedia.mediaId eq mediaId) and (lastSeen lessEq startTime)
            }
        }
    }
}

private object VerifyCommand : CliktCommand(
    help = "Verify a given media to ensure the data is correct"
) {
    private val path by argument("path").path(mustExist = true, canBeFile = false)
    override fun run() {
        val mediaId = MediaCommand.mediaId
        // sorted by starting block, thus we only run the tape once
        echo("[I]Listing files to verify")
        val listOfPaths = buildList { path.walkFile { add(it) } }
            .sortedBy { p ->
                // TODO: assuming it's long
                p.readAttr("ltfs.startblock")
                    ?.let { Pack.bigEndianToLong(it, 0) }
                    ?: Long.MAX_VALUE
            }
        echo("[I]Listed ${listOfPaths.size} file(s), verifying... Only issues will be shown")
        // calculate the hash, check with database and make sure it's the same from attr
        val corruptedFiles = LinkedList<String>()
        listOfPaths.forEach { p ->
            val relativePathString = p.relativeToOrNull(path)!!.pathString
            val attrHash = p.readAttr("user.oam.sha3.256")
            if (attrHash == null) {
                echo(
                    "[W]Attribute missing for $relativePathString, need rescan",
                    err = true
                )
            }
            val dbHash = transaction {
                FilesOnMedia.selectByMediaIdAndPath(mediaId, relativePathString)
                    ?.get(FilesOnMedia.sha3Hash256)
            }
            if (dbHash == null) {
                echo(
                    "[W]Index missing for $relativePathString, need rescan",
                    err = true
                )
            }
            if (attrHash == null && dbHash == null) {
                echo(
                    "[E]Can't verify $relativePathString, please verify manually before rescan",
                    err = true
                )
                corruptedFiles.add(relativePathString)
                return@forEach
            }
            if (attrHash != null && dbHash != null && !attrHash.contentEquals(dbHash)) {
                echo(
                    "[E]Can't verify $relativePathString, index and attribute conflict",
                    err = true
                )
                corruptedFiles.add(relativePathString)
                return@forEach
            }
            val actualHash = p.sha3() // TODO: option for buffer size?

            // checking the hash
            if (attrHash != null) { // we have attr
                if (!actualHash.contentEquals(attrHash)) {
                    // error
                    echo(
                        "[E]Corrupted file $relativePathString, should solve before rescan",
                        err = true
                    )
                    corruptedFiles.add(relativePathString)
                }
            } else { // we lost attr, but have db
                if (!actualHash.contentEquals(dbHash)) {
                    // error
                    echo(
                        "[E]Corrupted file $relativePathString, should solve before rescan",
                        err = true
                    )
                    corruptedFiles.add(relativePathString)
                }
            }
        }
        transaction {
            corruptedFiles.forEach {
                FilesOnMedia.deleteByMediaIdAndPath(
                    mediaId, it
                )
            }
        }
        echo("Removed ${corruptedFiles.size} corrupted file(s)")
    }
}

private object ListCommand : CliktCommand(
    help = "List the files on a given media according to the index"
) {
    private val sizeForHuman by option("-h", "--human").flag()
        .help("Print size in human readable way instead of raw numbers")

    // https://en.wikipedia.org/wiki/Binary_prefix
    private val humanSizeUnits = listOf(
        "", "K", "M", "G", "T", "P", "E", "Z", "Y", "R", "Q"
    )

    private fun Long.toHumanSize(): String {
        if (this < 1024) return this.toString()

        var unitIndex = 0
        var size = this.toBigDecimal().setScale(2)
        while (unitIndex < humanSizeUnits.size - 1 && size > 1000.0.toBigDecimal()) {
            unitIndex++
            size /= 1000.0.toBigDecimal()
        }
        return "%.1f".format(size) + humanSizeUnits[unitIndex]
    }

    override fun run() {
        val mediaId = MediaCommand.mediaId
        if (transaction { !Medias.existById(mediaId) }) {
            echo("Media id $mediaId not found", err = true)
            return
        }

        val result = transaction {
            echo(
                "total ${
                    FilesOnMedia.countByMediaId(mediaId)
                }"
            )
            FilesOnMedia.selectByMediaId(mediaId).toList()
        }
        val maxSizeLength = result.maxOf {
            if (sizeForHuman) {
                it[FilesOnMedia.size].toHumanSize().length
            } else {
                it[FilesOnMedia.size].toString().length
            }
        }

        result.forEach {
            val path = it[FilesOnMedia.path]
            val size = if (sizeForHuman) {
                it[FilesOnMedia.size].toHumanSize()
            } else {
                it[FilesOnMedia.size].toString()
            }
            val hash = it[FilesOnMedia.sha3Hash256].toHex()
            val lastSeen = it[FilesOnMedia.lastSeen]
            echo(
                hash + "  "
                        + " ".repeat(maxSizeLength - size.length) + size
                        + "  "
                        + "${Date(lastSeen * 1000)}"
                        + "  "
                        + path
            )
        }
    }
}

private object RemoveCommand : CliktCommand(
    help = "Remove a given media from the index"
) {
    override fun run() {
        val mediaId = MediaCommand.mediaId
        if (transaction { !Medias.existById(mediaId) }) {
            echo("Media $mediaId not exists!", err = true)
            return
        }

        if (terminal.prompt(
                "Remove media `${mediaId}`?",
                default = "N",
                choices = listOf("Y", "y", "N", "n")
            )?.uppercase() == "Y"
        ) {
            echo("Removing file indexes related to media $mediaId")
            transaction {
                FilesOnMedia.deleteByMediaId(mediaId)
            }.also {
                echo("Removed $it record(s)")
            }
            echo("Removing media with id: $mediaId")
            transaction {
                Medias.deleteById(mediaId)
            }.also {
                echo("Removed $it record(s)")
            }
        } else {
            echo("Cancelled. No data is removed.")
        }
    }
}

private object InfoCommand : CliktCommand(
    help = "Manage the info about a given media",
    invokeWithoutSubcommand = true
) {
    init {
        subcommands(SetInfoCommand)
    }

    override fun run() {
        val mediaId = MediaCommand.mediaId
        if (transaction { !Medias.existById(mediaId) }) {
            echo("Media id $mediaId not found", err = true)
            return
        }
        val (mediaType, generation, lastSeen) = transaction {
            Medias.selectById(mediaId)!!.let {
                Triple(
                    it[Medias.mediaType],
                    it[Medias.generation],
                    it[Medias.lastSeen]
                )
            }
        }
        echo("Media $mediaId")
        echo("\tType: $mediaType")
        echo("\tGeneration: $generation")
        echo("\tLast seen at ${Date(lastSeen * 1000)}")
    }

    private object SetInfoCommand : CliktCommand(
        name = "set",
        help = "Set media type and generation for a given media"
    ) {
        private val key: String by argument("key")
            .help("Key, must be: `type` (media type) or `gen` (generation).")

        private val value: String by argument("value")
            .help("The value of the key. Use empty string to reset")

        override fun run() {
            val mediaId = MediaCommand.mediaId
            if (transaction { !Medias.existById(mediaId) }) {
                echo("Media id $mediaId not found", err = true)
                return
            }
            when (key) {
                "type" -> {
                    val oldValue = transaction {
                        val old = Medias.selectById(mediaId)!![Medias.mediaType]
                        Medias.updateById(mediaId, mediaType = value)
                        old
                    }
                    echo("Old media type: $oldValue")
                    echo("New media type: $value")
                }

                "gen" -> {
                    val oldValue = transaction {
                        val old = Medias.selectById(mediaId)!![Medias.generation]
                        Medias.updateById(mediaId, generation = value)
                        old
                    }
                    echo("Old generation: $oldValue")
                    echo("New generation: $value")
                }

                else -> echo("Unknown key. See help for available keys")
            }
        }
    }
}
