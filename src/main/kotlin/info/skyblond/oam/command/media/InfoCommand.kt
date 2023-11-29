package info.skyblond.oam.command.media

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.help
import info.skyblond.oam.datastore.Media.Companion.parseMedia
import info.skyblond.oam.datastore.Medias
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.*

object InfoCommand : CliktCommand(
    help = "Manage the info about a given media",
    invokeWithoutSubcommand = true,
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
        val media = transaction {
            Medias.selectById(mediaId)!!.parseMedia()
        }
        echo("Media $mediaId")
        echo("\tType: ${media.mediaType}")
        echo("\tGeneration: ${media.generation}")
        echo("\tLast seen at ${Date(media.lastSeen * 1000)}")
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
                return
            }
            when (key) {
                "type" -> {
                    transaction { Medias.updateById(mediaId, mediaType = value) }
                    echo("set media type to: $value")
                }

                "gen" -> {
                    transaction { Medias.updateById(mediaId, generation = value) }
                    echo("set generation to: $value")
                }

                else -> echo("Unknown key. See help for available keys")
            }
        }
    }
}
