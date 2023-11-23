package info.skyblond.oam.datastore

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq


object FilesOnMedia : Table("t_file_on_media") {
    val mediaId: Column<String> = reference(
        "media_id", Medias.id,
        // delete when media get deleted
        ReferenceOption.CASCADE,
        // update when media changes its id
        ReferenceOption.CASCADE,
    )

    // the path of this file on this media
    val path = varchar("path_on_media", length = 4096)

    // size in byte
    val size = long("size_of_file")

    // hash, sha3
    val sha3Hash256 = binary("sha3_256", length = 256 / 8)

    // last seen timestamp, second.
    val lastSeen = long("last_seen")


    override val primaryKey = PrimaryKey(
        arrayOf(mediaId, path), name = "PK_Media_ID"
    )


    /**
     * Create a new FilesOnMedia record.
     * */
    fun insert(
        mediaId: String,
        path: String,
        size: Long,
        sha3Hash256: ByteArray
    ) = FilesOnMedia.insert {
        it[FilesOnMedia.mediaId] = mediaId
        it[FilesOnMedia.path] = path
        it[FilesOnMedia.size] = size
        it[FilesOnMedia.sha3Hash256] = sha3Hash256
        it[lastSeen] = System.currentTimeMillis() / 1000
    }

    fun deleteByMediaIdAndPath(mediaId: String, path: String) =
        FilesOnMedia.deleteWhere { (FilesOnMedia.mediaId eq mediaId) and (FilesOnMedia.path eq path) }

    fun deleteByMediaId(mediaId: String) =
        FilesOnMedia.deleteWhere { FilesOnMedia.mediaId eq mediaId }


    /**
     * Updated by id. Null parameter means no update on this field.
     * */
    fun updateByMediaIdAndPath(
        mediaId: String,
        path: String,
        size: Long? = null,
        sha3Hash256: ByteArray? = null,
        lastSeen: Long? = null
    ) = FilesOnMedia.update({ (FilesOnMedia.mediaId eq mediaId) and (FilesOnMedia.path eq path) }) {
        size?.let { f -> it[FilesOnMedia.size] = f }
        sha3Hash256?.let { f -> it[FilesOnMedia.sha3Hash256] = f }
        lastSeen?.let { f -> it[FilesOnMedia.lastSeen] = f }
    }

    fun selectByMediaIdAndPath(mediaId: String, path: String) =
        FilesOnMedia.select { (FilesOnMedia.mediaId eq mediaId) and (FilesOnMedia.path eq path) }.firstOrNull()

    fun selectByMediaId(mediaId: String) =
        FilesOnMedia.select { FilesOnMedia.mediaId eq mediaId }
            .orderBy(path)

    fun countByMediaId(mediaId: String) =
        FilesOnMedia.select { FilesOnMedia.mediaId eq mediaId }
            .count()

}
