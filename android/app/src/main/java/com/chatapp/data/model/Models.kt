package com.chatapp.data.model

data class User(val id: Long, val username: String)

data class AuthResponse(val token: String, val user: User)

data class AuthRequest(val username: String, val password: String)

data class Message(
    val id: String = "",
    val type: String = "text",
    val content: String = "",
    val sender: String = "",
    val senderId: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val file: FileInfo? = null
)

data class FileInfo(
    val id: String = "",
    val name: String = "",
    val size: Long = 0,
    val type: String = "",
    val url: String = "",
    val roomId: String = "",
    val sender: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val storedName: String = ""
)

data class RoomInfo(
    val room_id: String,
    val user_count: Int = 0,
    val message_count: Int = 0
)

data class FriendRequest(
    val id: Long,
    val fromUserId: Long? = null,
    val toUserId: Long? = null,
    val fromUsername: String? = null,
    val toUsername: String? = null,
    val status: String = "pending",
    val createdAt: String? = null
)

data class FriendRequestsResponse(
    val incoming: List<FriendRequest> = emptyList(),
    val outgoing: List<FriendRequest> = emptyList()
)

data class SearchUser(val id: Long, val username: String)

data class ErrorResponse(val error: String)

data class TypingEvent(val userId: String, val username: String)

data class CallSignal(
    val type: String = "",
    val from: String = "",
    val to: String = "",
    val sdp: String = "",
    val candidate: String = "",
    val sdpMid: Int = 0,
    val sdpMLineIndex: Int = 0,
    val callId: String = "",
    val roomId: String = ""
)
