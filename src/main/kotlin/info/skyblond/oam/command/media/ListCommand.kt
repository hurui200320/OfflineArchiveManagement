package info.skyblond.oam.command.media

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.option
import info.skyblond.oam.datastore.FileOnMedia.Companion.parseFileOnMedia
import info.skyblond.oam.datastore.FilesOnMedia
import info.skyblond.oam.datastore.Medias
import info.skyblond.oam.toHex
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.*

object ListCommand : CliktCommand(
    help = "List the files on a given media according to the index"
) {
    private val sizeForHuman by option("-h", "--human").flag()
        .help("Print size in human readable way instead of raw numbers")

    override fun run() {
        val mediaId = MediaCommand.mediaId
        if (transaction { !Medias.existById(mediaId) }) {
            echo("Media id $mediaId not found", err = true)
            return
        }

        val result = transaction {
            echo("total ${FilesOnMedia.countByMediaId(mediaId)}")
            FilesOnMedia.selectByMediaId(mediaId).map { it.parseFileOnMedia() }
        }
        val maxSizeLength = result.maxOf { it.getSizeString(sizeForHuman).length }

        result.forEach {
            val path = it.path
            val size = it.getSizeString(sizeForHuman)
            val hash = it.sha3Hash256.toHex()
            val lastSeen = it.lastSeen
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
