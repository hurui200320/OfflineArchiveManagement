package info.skyblond.oam.datastore

import info.skyblond.oam.toHumanSize
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

    fun existByMediaIdAndPath(mediaId: String, path: String) =
        FilesOnMedia.select { (FilesOnMedia.mediaId eq mediaId) and (FilesOnMedia.path eq path) }.any()


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

    fun insertOrUpdate(
        mediaId: String,
        path: String,
        size: Long,
        sha3Hash256: ByteArray
    ) {
        runCatching {
            insert(
                mediaId = mediaId,
                path = path,
                size = size,
                sha3Hash256 = sha3Hash256
            )
        }.onFailure {
            updateByMediaIdAndPath(
                mediaId, path,
                size = size, sha3Hash256 = sha3Hash256,
                lastSeen = System.currentTimeMillis() / 1000
            )
        }
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

data class FileOnMedia(
    val mediaId: String,
    val path: String,
    val size: Long,
    val sha3Hash256: ByteArray,
    val lastSeen: Long
) {
    val humanSize: String = size.toHumanSize()

    fun getSizeString(forHuman: Boolean): String = if (forHuman) humanSize else size.toString()

    companion object {
        fun ResultRow.parseFileOnMedia(): FileOnMedia = FileOnMedia(
            mediaId = this[FilesOnMedia.mediaId],
            path = this[FilesOnMedia.path],
            size = this[FilesOnMedia.size],
            sha3Hash256 = this[FilesOnMedia.sha3Hash256],
            lastSeen = this[FilesOnMedia.lastSeen],
        )
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is FileOnMedia) return false

        if (mediaId != other.mediaId) return false
        if (path != other.path) return false
        if (size != other.size) return false
        if (!sha3Hash256.contentEquals(other.sha3Hash256)) return false
        if (lastSeen != other.lastSeen) return false

        return true
    }

    override fun hashCode(): Int {
        var result = mediaId.hashCode()
        result = 31 * result + path.hashCode()
        result = 31 * result + size.hashCode()
        result = 31 * result + sha3Hash256.contentHashCode()
        result = 31 * result + lastSeen.hashCode()
        return result
    }
}
