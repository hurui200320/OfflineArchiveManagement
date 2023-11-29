package info.skyblond.oam.command

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.option
import info.skyblond.oam.datastore.FilesOnMedia
import info.skyblond.oam.datastore.Media.Companion.parseMedia
import info.skyblond.oam.datastore.Medias
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.*

object StatusCommand : CliktCommand(
    help = "Query the status of current setup"
) {

    private val listAll by option("-l", "--list").flag()
        .help("List medias and files")

    // TODO: when list all medias, show only media older than (Current - last seen >= ) X month

    override fun run() {
        val mediaCount = transaction { Medias.selectAll().count() }
        val filesCount = transaction { FilesOnMedia.selectAll().count() }
        val filesUniqueCount = transaction {
            FilesOnMedia.slice(FilesOnMedia.sha3Hash256, FilesOnMedia.size)
                .selectAll().withDistinct().count()
        }
        echo("There are $filesCount index(es) from $mediaCount media(s).")
        echo("By hash and size, there are $filesUniqueCount unique file(s).")
        if (!listAll) return

        echo("\nMedias:")
        transaction {
            Medias.selectAll().map { it.parseMedia() }
        }.forEach {
            val typeString = if (it.mediaType.isNotBlank()) {
                if (it.generation.isNotBlank()) {
                    "(${it.mediaType}, ${it.generation})"
                } else "(${it.mediaType})"
            } else ""

            echo("\t" + it.id + " " + typeString + ", last seen: ${Date(it.lastSeen * 1000)}")
        }

        echo("\nPlease use command `media <id> list` to list files on a given media.")
    }
}
