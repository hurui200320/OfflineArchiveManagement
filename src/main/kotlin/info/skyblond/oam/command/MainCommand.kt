package info.skyblond.oam.command

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.file
import info.skyblond.oam.command.media.MediaCommand
import info.skyblond.oam.datastore.FilesOnMedia
import info.skyblond.oam.datastore.Medias
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import java.io.File

object MainCommand : CliktCommand() {

    init {
        subcommands(
            MediaCommand,
            FileCommand,
            StatusCommand,
            ExportCommand,
            ImportCommand,
        )
    }

    private val defaultDBFile =
        File(System.getProperty("user.home") + File.separator + ".OfflineArchiveManagement/app.db")
    private val dbFile: File by option("--db").file(canBeDir = false)
        .default(defaultDBFile)
        .help("Database path. Default: $defaultDBFile")

    override fun run() {
        dbFile.parentFile?.mkdirs()
        Database.connect(
            "jdbc:sqlite:$dbFile",
            driver = "org.sqlite.JDBC",
        )
        transaction {
            SchemaUtils.create(Medias, FilesOnMedia)
        }
    }
}
