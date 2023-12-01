package info.skyblond.oam.command.media

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.arguments.argument

object MediaCommand : CliktCommand(
    help = "Operations related to a given media"
) {
    private val _mediaId: String by argument("media_id")

    lateinit var mediaId: String
        private set

    init {
        subcommands(
            WalkCommand,
            PurgeCommand,
            ListCommand,
            RemoveCommand,
            InfoCommand
        )
    }

    override fun run() {
        mediaId = _mediaId.trim()
    }
}
