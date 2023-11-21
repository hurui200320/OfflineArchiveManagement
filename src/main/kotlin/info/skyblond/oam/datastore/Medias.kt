package info.skyblond.oam.datastore

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.like

object Medias : Table("t_media") {
    val id: Column<String> = varchar("id", 20)
    private val mediaType = varchar("media_type", length = 50)
    private val generation = varchar("generation", length = 50)
    private val lastSeen = long("last_seen")

    override val primaryKey = PrimaryKey(id, name = "PK_Media_ID")

    /**
     * Create a new Media record.
     * */
    fun insert(
        id: String,
        mediaType: String = "",
        generation: String = ""
    ) = Medias.insert {
        it[Medias.id] = id
        it[Medias.mediaType] = mediaType
        it[Medias.generation] = generation
        it[lastSeen] = System.currentTimeMillis() / 1000
    }

    fun removeById(id: String) = Medias.deleteWhere { Medias.id like id }

    /**
     * Updated by id. Null parameter means no update on this field.
     * */
    fun updateById(
        id: String,
        mediaType: String? = null,
        generation: String? = null,
        lastSeen: Long? = null
    ) = Medias.update({ Medias.id like id }) {
        mediaType?.let { f -> it[Medias.mediaType] = f }
        generation?.let { f -> it[Medias.generation] = f }
        lastSeen?.let { f -> it[Medias.lastSeen] = f }
    }

    fun findById(id: String) = Medias.select { Medias.id like id }.firstOrNull()
}


