package info.skyblond.oam.command

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.inputStream
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import info.skyblond.oam.datastore.FilesOnMedia
import info.skyblond.oam.datastore.Medias
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.*

object ImportCommand : CliktCommand(
    help = "Import records/indexes from json file."
) {
    private val forceOverwrite by option("-f", "--force").flag()
        .help("Overwrite existing record")


    private val verbose by option("-v", "--verbose").flag()
        .help("Print what is skipped or overwrote")

    private val target by argument("target").inputStream()

    private fun parseMedias(jsonReader: JsonReader) {
        var importCounter = 0
        var overwriteCounter = 0
        var skipCounter = 0
        jsonReader.beginArray()
        while (jsonReader.hasNext() && jsonReader.peek() == JsonToken.BEGIN_OBJECT) {
            jsonReader.beginObject()
            val map = mutableMapOf<String, Any>()
            while (jsonReader.hasNext() && jsonReader.peek() == JsonToken.NAME) {
                when (val name = jsonReader.nextName()) {
                    "id" -> map[name] = jsonReader.nextString()
                    "mediaType" -> map[name] = jsonReader.nextString()
                    "generation" -> map[name] = jsonReader.nextString()
                    "lastSeen" -> map[name] = jsonReader.nextLong()
                }
            }
            val id = map["id"] as String? ?: error("Media object missing id")
            transaction {
                if (Medias.existById(id)) {
                    if (forceOverwrite) {
                        overwriteCounter++
                        if (verbose) echo("Media $id exists, overwriting...", err = true)
                        Medias.updateById(
                            id,
                            mediaType = map["mediaType"] as String?,
                            generation = map["generation"] as String?,
                            lastSeen = map["lastSeen"] as Long?
                        )
                    } else {
                        skipCounter++
                        if (verbose) echo("Media $id exists, skipping...", err = true)
                        return@transaction
                    }
                } else { // no existing, just insert
                    Medias.insert {
                        it[Medias.id] = id
                        it[mediaType] = map["mediaType"] as String? ?: ""
                        it[generation] = map["generation"] as String? ?: ""
                        it[lastSeen] = map["lastSeen"] as Long? ?: error("Media object missing lastSeen")
                    }
                }
                importCounter++
            }
            jsonReader.endObject()
        }
        jsonReader.endArray()
        echo(buildList {
            add("Imported $importCounter media(s)")
            if (overwriteCounter > 0)
                add("overwrote $overwriteCounter existing record(s)")
            if (skipCounter > 0)
                add("skipped $skipCounter record(s)")
        }.joinToString(", "))
    }

    private fun parseFiles(jsonReader: JsonReader) {
        var importCounter = 0
        var overwriteCounter = 0
        var skipCounter = 0
        jsonReader.beginArray()
        while (jsonReader.hasNext() && jsonReader.peek() == JsonToken.BEGIN_OBJECT) {
            jsonReader.beginObject()
            val map = mutableMapOf<String, Any>()
            while (jsonReader.hasNext() && jsonReader.peek() == JsonToken.NAME) {
                when (val name = jsonReader.nextName()) {
                    "mediaId" -> map[name] = jsonReader.nextString()
                    "path" -> map[name] = jsonReader.nextString()
                    "size" -> map[name] = jsonReader.nextLong()
                    "sha3Hash256" -> map[name] = Base64.getDecoder().decode(jsonReader.nextString())
                    "lastSeen" -> map[name] = jsonReader.nextLong()
                }
            }
            val mediaId = map["mediaId"] as String? ?: error("File object missing mediaId")
            val path = map["path"] as String? ?: error("File object missing path")

            transaction {
                if (FilesOnMedia.existByMediaIdAndPath(mediaId, path)) {
                    if (forceOverwrite) {
                        overwriteCounter++
                        if (verbose) echo("File $path on $mediaId exists, overwriting...", err = true)
                        FilesOnMedia.updateByMediaIdAndPath(
                            mediaId, path,
                            size = map["size"] as Long?,
                            sha3Hash256 = map["sha3Hash256"] as ByteArray?,
                            lastSeen = map["lastSeen"] as Long?
                        )
                    } else {
                        skipCounter++
                        if (verbose) echo("File $path on $mediaId exists, skipping...", err = true)
                        return@transaction
                    }
                } else { // no existing, just insert
                    FilesOnMedia.insert {
                        it[FilesOnMedia.mediaId] = mediaId
                        it[FilesOnMedia.path] = path
                        it[FilesOnMedia.size] = map["size"] as Long? ?: error("File object missing size")
                        it[FilesOnMedia.sha3Hash256] =
                            map["sha3Hash256"] as ByteArray? ?: error("File object missing sha3Hash256")
                        it[lastSeen] = map["lastSeen"] as Long? ?: error("File object missing lastSeen")
                    }
                }
                importCounter++
            }
            jsonReader.endObject()
        }
        jsonReader.endArray()
        echo(buildList {
            add("Imported $importCounter media(s)")
            if (overwriteCounter > 0)
                add("overwrote $overwriteCounter existing record(s)")
            if (skipCounter > 0)
                add("skipped $skipCounter record(s)")
        }.joinToString(", "))
    }

    override fun run() {
        val reader = target.reader()
        val jsonReader = JsonReader(reader)

        jsonReader.beginObject()
        while (jsonReader.hasNext() && jsonReader.peek() == JsonToken.NAME) {
            when (jsonReader.nextName()) {
                "medias" -> parseMedias(jsonReader)
                "files" -> parseFiles(jsonReader)
            }
        }
        jsonReader.endObject()
        reader.close()
    }
}
