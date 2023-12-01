package info.skyblond.oam.command

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.*
import com.github.ajalt.clikt.parameters.types.int
import info.skyblond.oam.command.CheckCommand.Entry.Companion.parseEntry
import info.skyblond.oam.datastore.FileOnMedia
import info.skyblond.oam.datastore.FileOnMedia.Companion.parseFileOnMedia
import info.skyblond.oam.datastore.FilesOnMedia
import info.skyblond.oam.toHex
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.upperCase
import java.util.*

object FileCommand : CliktCommand(
    help = "Operations related to files"
) {
    init {
        subcommands(
            FindCommand,
            CheckCommand,
        )
    }

    override fun run() = Unit
}

private object FindCommand : CliktCommand(
    help = "Find a file based on a given name"
) {
    private val sizeForHuman by option("-h", "--human").flag()
        .help("Print size in human readable way instead of raw numbers")
    private val name: String by argument("name")
    override fun run() {
        transaction {
            // use uppercase for case-insensitive search
            FilesOnMedia.select { FilesOnMedia.path.upperCase() like "%${name.uppercase()}%" }.map { it.parseFileOnMedia() }
        }.forEach {
            val size = it.getSizeString(sizeForHuman)
            val hash = it.sha3Hash256.toHex()
            val lastSeen = it.lastSeen
            echo(
                it.mediaId + ":" + it.path + " (${size})\n\t"
                        + "last seen: ${Date(lastSeen * 1000)}\n\t"
                        + "sha3-256: " + hash
            )
        }
    }
}

private object CheckCommand : CliktCommand(
    help = "Check all files and show the replications info"
) {
    private val mediaIdList by option("-m", "--media")
        .multiple()
        .help("showing files related to the given media ids")

    private val count by option("-n", "--count").int()
        .default(Int.MAX_VALUE)
        .help("showing files that has less than N replicates")
        .check("count must be positive") { it > 0 }


    private data class Entry(
        val size: Long,
        val hash: ByteArray
    ) {
        companion object {
            fun FileOnMedia.parseEntry() = Entry(
                size = this.size,
                hash = this.sha3Hash256
            )
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Entry) return false

            if (size != other.size) return false
            if (!hash.contentEquals(other.hash)) return false

            return true
        }

        override fun hashCode(): Int {
            var result = size.hashCode()
            result = 31 * result + hash.contentHashCode()
            return result
        }
    }

    override fun run() {
        transaction {
            FilesOnMedia.selectAll().asSequence()
                .map { it.parseFileOnMedia() }
                .filter { mediaIdList.isEmpty() || it.mediaId in mediaIdList }
                .toList()
        }.groupBy { it.parseEntry() }
            .filter { it.value.size < count }
            .forEach { (entry, files) ->
                echo("sha3-256: ${entry.hash.toHex()}, size: ${entry.size}")
                files.forEach {
                    echo(
                        "\t" + it.mediaId + ":" + it.path + ", last seen: ${Date(it.lastSeen * 1000)}"
                    )
                }
                echo("\tcount: ${files.size}")
            }
    }
}
