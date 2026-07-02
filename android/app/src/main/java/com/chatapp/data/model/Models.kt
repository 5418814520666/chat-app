package com.chatapp.data.model

// ---------- Auth ----------
data class AuthResponse(
    val token: String = "",
    val user: User? = null
)

data class User(
    val id: Long = 0,
    val username: String = ""
)

// ---------- Chat ----------
data class Message(
    val id: String = "",
    val type: String = "text",
    val content: String = "",
    val sender: String = "",
    val senderId: String = "",
    val timestamp: Long = 0,
    val file: FileInfo? = null
)

data class FileInfo(
    val name: String = "",
    val size: Long = 0,
    val url: String = ""
)

data class RoomInfo(
    val room_id: String = "",
    val user_count: Int = 0,
    val message_count: Int = 0
)

// ---------- Friends ----------
data class FriendRequest(
    val id: Long = 0,
    val fromUserId: Long = 0,
    val toUserId: Long = 0,
    val fromUsername: String? = null,
    val toUsername: String? = null,
    val status: String = "pending"
)

data class FriendRequestsResponse(
    val incoming: List<FriendRequest> = emptyList(),
    val outgoing: List<FriendRequest> = emptyList()
)

data class SearchUser(
    val id: Long = 0,
    val username: String = ""
)
