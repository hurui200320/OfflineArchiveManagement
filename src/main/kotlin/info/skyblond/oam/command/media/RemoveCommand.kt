package info.skyblond.oam.command.media

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.terminal
import info.skyblond.oam.datastore.FilesOnMedia
import info.skyblond.oam.datastore.Medias
import org.jetbrains.exposed.sql.transactions.transaction

object RemoveCommand : CliktCommand(
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
