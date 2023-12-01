package info.skyblond.oam.command.media

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.terminal
import info.skyblond.oam.datastore.FilesOnMedia
import info.skyblond.oam.datastore.Medias
import org.jetbrains.exposed.sql.transactions.transaction

object PurgeCommand : CliktCommand(
    help = "Purge the file indexes related to a given media"
) {

    override fun run() {
        val mediaId = MediaCommand.mediaId
        if (transaction { !Medias.existById(mediaId) }) {
            echo("Media $mediaId not exists!", err = true)
            return
        }

        if (terminal.prompt(
                "Remove files in media `${mediaId}`?",
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
        } else {
            echo("Cancelled. No data is removed.")
        }
    }
}
