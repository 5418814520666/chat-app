package com.chatapp.data.db

import androidx.room.*
import com.chatapp.data.model.FileInfo
import com.chatapp.data.model.Message

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey val id: String,
    val type: String,
    val content: String,
    val sender: String,
    val senderId: String,
    val timestamp: Long,
    val roomId: String,
    val fileName: String? = null,
    val fileSize: Long? = null,
    val fileType: String? = null,
    val fileUrl: String? = null
)

fun Message.toEntity(roomId: String) = MessageEntity(
    id = id,
    type = type,
    content = content,
    sender = sender,
    senderId = senderId,
    timestamp = timestamp,
    roomId = roomId,
    fileName = file?.name,
    fileSize = file?.size,
    fileType = file?.type,
    fileUrl = file?.url
)

fun MessageEntity.toMessage() = Message(
    id = id,
    type = type,
    content = content,
    sender = sender,
    senderId = senderId,
    timestamp = timestamp,
    file = if (fileUrl != null) FileInfo(
        name = fileName ?: "",
        size = fileSize ?: 0,
        type = fileType ?: "",
        url = fileUrl
    ) else null
)

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE roomId = :roomId ORDER BY timestamp ASC")
    suspend fun getMessages(roomId: String): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE roomId = :roomId AND timestamp < :before ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getMessagesBefore(roomId: String, before: Long, limit: Int): List<MessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: MessageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(messages: List<MessageEntity>)

    @Query("DELETE FROM messages WHERE roomId = :roomId")
    suspend fun deleteByRoom(roomId: String)
}

@Database(entities = [MessageEntity::class], version = 1, exportSchema = false)
abstract class ChatDatabase : RoomDatabase() {
    abstract fun messageDao(): MessageDao
}
