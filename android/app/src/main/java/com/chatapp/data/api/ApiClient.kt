package com.chatapp.data.api

import com.chatapp.data.model.*
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.json.JSONObject
import java.io.*
import java.net.HttpURLConnection
import java.net.URL

object ApiClient {

    const val BASE = "https://chat.yangchen.skin"
    private val gson = Gson()

    @Volatile
    var token: String? = null

    fun apiLogin(username: String, password: String): AuthResponse? {
        val body = JSONObject().apply {
            put("username", username)
            put("password", password)
        }.toString()
        val json = post("$BASE/api/auth/login", body)
        return json?.let { gson.fromJson(it, AuthResponse::class.java) }
    }

    fun apiRegister(username: String, password: String): AuthResponse? {
        val body = JSONObject().apply {
            put("username", username)
            put("password", password)
        }.toString()
        val json = post("$BASE/api/auth/register", body)
        return json?.let { gson.fromJson(it, AuthResponse::class.java) }
    }

    fun apiGetRooms(): List<RoomInfo>? {
        val json = get("$BASE/api/rooms")
        return json?.let { gson.fromJson(it, object : TypeToken<List<RoomInfo>>() {}.type) }
    }

    fun apiGetMessages(roomId: String, limit: Int = 50, before: Long = System.currentTimeMillis()): List<Message>? {
        val json = get("$BASE/api/messages/$roomId?limit=$limit&before=$before")
        return json?.let { gson.fromJson(it, object : TypeToken<List<Message>>() {}.type) }
    }

    fun apiGetPrivateMessages(otherUserId: Long, limit: Int = 50, before: Long = System.currentTimeMillis()): List<Message>? {
        val json = get("$BASE/api/private-messages/$otherUserId?limit=$limit&before=$before")
        return json?.let { gson.fromJson(it, object : TypeToken<List<Message>>() {}.type) }
    }

    fun apiGetFriends(): List<User>? {
        val json = get("$BASE/api/friends")
        return json?.let { gson.fromJson(it, object : TypeToken<List<User>>() {}.type) }
    }

    fun apiGetFriendRequests(): FriendRequestsResponse? {
        val json = get("$BASE/api/friend-requests")
        return json?.let { gson.fromJson(it, FriendRequestsResponse::class.java) }
    }

    fun apiSearchUsers(q: String): List<SearchUser>? {
        val json = get("$BASE/api/users/search?q=$q")
        return json?.let { gson.fromJson(it, object : TypeToken<List<SearchUser>>() {}.type) }
    }

    fun apiSendFriendRequest(toUserId: Long): Boolean {
        val body = """{"toUserId":$toUserId}"""
        val json = post("$BASE/api/friend-request", body)
        return json != null
    }

    fun apiAcceptFriend(requestId: Long, fromUserId: Long): Boolean {
        val body = """{"requestId":$requestId,"fromUserId":$fromUserId}"""
        val json = post("$BASE/api/friend-accept", body)
        return json != null
    }

    fun apiRejectFriend(requestId: Long, fromUserId: Long): Boolean {
        val body = """{"requestId":$requestId,"fromUserId":$fromUserId}"""
        val json = post("$BASE/api/friend-reject", body)
        return json != null
    }

    fun getError(json: String?): String {
        if (json == null) return "网络连接失败"
        return try { JSONObject(json).optString("error", "请求失败") } catch (_: Exception) { "请求失败" }
    }

    private fun get(url: String): String? {
        try {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 15000
                readTimeout = 15000
                token?.let { setRequestProperty("Authorization", "Bearer $it") }
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            return stream?.bufferedReader()?.readText()?.also { conn.disconnect() }
        } catch (e: Exception) {
            android.util.Log.e("ApiClient", "GET $url failed: ${e.message}")
            return null
        }
    }

    private fun post(url: String, body: String): String? {
        try {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 15000
                readTimeout = 15000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                token?.let { setRequestProperty("Authorization", "Bearer $it") }
            }
            conn.outputStream.write(body.toByteArray())
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            return stream?.bufferedReader()?.readText()?.also { conn.disconnect() }
        } catch (e: Exception) {
            android.util.Log.e("ApiClient", "POST $url failed: ${e.message}")
            return null
        }
    }
}
