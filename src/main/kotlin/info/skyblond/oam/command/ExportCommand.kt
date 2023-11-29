package info.skyblond.oam.command

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.types.outputStream
import com.google.gson.stream.JsonWriter
import info.skyblond.oam.datastore.FileOnMedia.Companion.parseFileOnMedia
import info.skyblond.oam.datastore.FilesOnMedia
import info.skyblond.oam.datastore.Media.Companion.parseMedia
import info.skyblond.oam.datastore.Medias
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.*

object ExportCommand : CliktCommand(
    help = "Export database into a json file"
) {

    private val target by argument("target").outputStream(
        createIfNotExist = true, truncateExisting = true
    )

    override fun run() {
        val writer = target.writer()
        val jsonWriter = JsonWriter(writer)
            .also { it.setIndent("  ") }
        // the file is a big object
        jsonWriter.beginObject()

        // media
        jsonWriter.name("medias")
        jsonWriter.beginArray()
        transaction { Medias.selectAll().toList() }.forEach {
            val media = it.parseMedia()
            jsonWriter.beginObject()
            jsonWriter.name("id")
            jsonWriter.value(media.id)
            jsonWriter.name("mediaType")
            jsonWriter.value(media.mediaType)
            jsonWriter.name("generation")
            jsonWriter.value(media.generation)
            jsonWriter.name("lastSeen")
            jsonWriter.value(media.lastSeen)
            jsonWriter.endObject()
        }
        jsonWriter.endArray()


        // files
        jsonWriter.name("files")
        jsonWriter.beginArray()
        transaction { FilesOnMedia.selectAll().toList() }.forEach {
            val file = it.parseFileOnMedia()
            jsonWriter.beginObject()
            jsonWriter.name("mediaId")
            jsonWriter.value(file.mediaId)
            jsonWriter.name("path")
            jsonWriter.value(file.path)
            jsonWriter.name("size")
            jsonWriter.value(file.size)
            jsonWriter.name("sha3Hash256")
            jsonWriter.value(Base64.getEncoder().encodeToString(file.sha3Hash256))
            jsonWriter.name("lastSeen")
            jsonWriter.value(file.lastSeen)
            jsonWriter.endObject()
        }
        jsonWriter.endArray()

        // the end of file
        jsonWriter.endObject()

        writer.flush()
        writer.close()
    }
}
