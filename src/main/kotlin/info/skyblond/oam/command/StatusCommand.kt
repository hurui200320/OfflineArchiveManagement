package info.skyblond.oam.command

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.*
import com.github.ajalt.clikt.parameters.types.int
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
    private val olderThan by option("-o", "--older-than").int()
        .default(0)
        .help("showing medias that haven't been seen in N months (30 days/month)")
        .check("older-than must be positive or zero") { it >= 0 }

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

        val currentTimestamp = System.currentTimeMillis() / 1000
        echo("\nMedias:")
        transaction {
            Medias.selectAll().map { it.parseMedia() }
        }.filter {
            // warning if time not correct
            if (it.lastSeen > currentTimestamp)
                echo("[E]Last seen in future! Last seen of ${it.id}: ${Date(it.lastSeen * 1000)}", err = true)
            // 30 days * 86400s/day = 2592000 s
            olderThan <= 0 // no need to calculate
                    || it.lastSeen > currentTimestamp // in the future
                    || currentTimestamp - it.lastSeen >= olderThan * 2592000L
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
