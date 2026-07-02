package com.chatapp.data.api

import com.chatapp.data.model.*
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object ApiClient {

    const val BASE = "https://chat.yangchen.skin"

    @Volatile var token: String? = null

    private val gson = Gson()

    // ---- raw HTTP ----
    private fun request(method: String, path: String, body: String? = null): HttpResponse {
        return try {
            val conn = (URL(BASE + path).openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = 10000
                readTimeout = 10000
                setRequestProperty("Content-Type", "application/json")
                token?.let { setRequestProperty("Authorization", "Bearer $it") }
                if (body != null) {
                    doOutput = true
                    outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                }
            }
            val code = conn.responseCode
            val text = try {
                (if (code in 200..299) conn.inputStream else conn.errorStream)
                    ?.bufferedReader()?.readText()
            } catch (_: Exception) { null }
            conn.disconnect()
            HttpResponse(code, text)
        } catch (e: Exception) {
            android.util.Log.e("ApiClient", "$method $path failed: ${e.message}")
            HttpResponse(-1, null)
        }
    }

    // ---- Auth ----
    fun login(username: String, password: String): LoginResult {
        val body = gson.toJson(mapOf("username" to username, "password" to password))
        val r = request("POST", "/api/auth/login", body)
        if (!r.ok) return LoginResult.error(extractError(r) ?: "网络连接失败")
        return parseAuth(r.body)
    }

    fun register(username: String, password: String): LoginResult {
        val body = gson.toJson(mapOf("username" to username, "password" to password))
        val r = request("POST", "/api/auth/register", body)
        if (!r.ok) return LoginResult.error(extractError(r) ?: "网络连接失败")
        return parseAuth(r.body)
    }

    private fun parseAuth(json: String?): LoginResult {
        if (json == null) return LoginResult.error("服务器响应为空")
        return try {
            val resp = gson.fromJson(json, AuthResponse::class.java)
            val u = resp.user
            if (resp.token.isBlank()) LoginResult.error("认证令牌为空")
            else if (u == null) LoginResult.error("用户信息缺失")
            else LoginResult.success(resp.token, u)
        } catch (e: Exception) {
            LoginResult.error("数据解析失败")
        }
    }

    // ---- Rooms / Messages ----
    fun getRooms(): List<RoomInfo> {
        val r = request("GET", "/api/rooms")
        if (!r.ok || r.body == null) return emptyList()
        return try { gson.fromJson(r.body, object : TypeToken<List<RoomInfo>>() {}.type) } catch (_: Exception) { emptyList() }
    }

    fun getMessages(roomId: String): List<Message> {
        val r = request("GET", "/api/messages/$roomId")
        if (!r.ok || r.body == null) return emptyList()
        return try { gson.fromJson(r.body, object : TypeToken<List<Message>>() {}.type) } catch (_: Exception) { emptyList() }
    }

    fun getPrivateMessages(otherUserId: Long): List<Message> {
        val r = request("GET", "/api/private-messages/$otherUserId")
        if (!r.ok || r.body == null) return emptyList()
        return try { gson.fromJson(r.body, object : TypeToken<List<Message>>() {}.type) } catch (_: Exception) { emptyList() }
    }

    // ---- Friends ----
    fun getFriends(): List<User> {
        val r = request("GET", "/api/friends")
        if (!r.ok || r.body == null) return emptyList()
        return try { gson.fromJson(r.body, object : TypeToken<List<User>>() {}.type) } catch (_: Exception) { emptyList() }
    }

    fun getFriendRequests(): FriendRequestsResponse {
        val r = request("GET", "/api/friend-requests")
        if (!r.ok || r.body == null) return FriendRequestsResponse()
        return try { gson.fromJson(r.body, FriendRequestsResponse::class.java) } catch (_: Exception) { FriendRequestsResponse() }
    }

    fun searchUsers(q: String): List<SearchUser> {
        val encoded = URLEncoder.encode(q, "UTF-8")
        val r = request("GET", "/api/users/search?q=$encoded")
        if (!r.ok || r.body == null) return emptyList()
        return try { gson.fromJson(r.body, object : TypeToken<List<SearchUser>>() {}.type) } catch (_: Exception) { emptyList() }
    }

    fun sendFriendRequest(toUserId: Long): Boolean {
        val body = gson.toJson(mapOf("toUserId" to toUserId))
        val r = request("POST", "/api/friend-request", body)
        return r.ok
    }

    fun acceptFriend(requestId: Long, fromUserId: Long): Boolean {
        val body = gson.toJson(mapOf("requestId" to requestId, "fromUserId" to fromUserId))
        val r = request("POST", "/api/friend-accept", body)
        return r.ok
    }

    fun rejectFriend(requestId: Long, fromUserId: Long): Boolean {
        val body = gson.toJson(mapOf("requestId" to requestId, "fromUserId" to fromUserId))
        val r = request("POST", "/api/friend-reject", body)
        return r.ok
    }

    // ---- helpers ----
    private fun extractError(r: HttpResponse): String? {
        if (r.body == null) return null
        return try { JSONObject(r.body).optString("error", "").ifEmpty { null } } catch (_: Exception) { null }
    }

    data class HttpResponse(val code: Int, val body: String?) {
        val ok get() = code in 200..299
    }

    data class LoginResult(
        val success: Boolean,
        val token: String? = null,
        val user: User? = null,
        val error: String? = null
    ) {
        companion object {
            fun success(t: String, u: User) = LoginResult(true, t, u, null)
            fun error(msg: String) = LoginResult(false, null, null, msg)
        }
    }
}
