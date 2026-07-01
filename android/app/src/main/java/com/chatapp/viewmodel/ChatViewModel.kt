package com.chatapp.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.chatapp.data.api.ApiClient
import com.chatapp.data.api.SocketManager
import com.chatapp.data.db.ChatDatabase
import com.chatapp.data.db.toEntity
import com.chatapp.data.db.toMessage
import com.chatapp.data.model.*
import com.chatapp.data.prefs.AuthManager
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val authManager = AuthManager(application)
    val socketManager = SocketManager()
    private val db by lazy {
        androidx.room.Room.databaseBuilder(application, ChatDatabase::class.java, "chat.db").build()
    }
    private val api = ApiClient.apiService

    private val _authState = MutableStateFlow<AuthState>(AuthState.Loading)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val _rooms = MutableStateFlow<List<RoomInfo>>(emptyList())
    val rooms: StateFlow<List<RoomInfo>> = _rooms.asStateFlow()

    private val _friends = MutableStateFlow<List<User>>(emptyList())
    val friends: StateFlow<List<User>> = _friends.asStateFlow()

    private val _friendRequests = MutableStateFlow(0)
    val friendRequestCount: StateFlow<Int> = _friendRequests.asStateFlow()

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages.asStateFlow()

    private val _users = MutableStateFlow<List<User>>(emptyList())
    val users: StateFlow<List<User>> = _users.asStateFlow()

    private val _typingUsers = MutableStateFlow<Map<String, String>>(emptyMap())
    val typingUsers: StateFlow<Map<String, String>> = _typingUsers.asStateFlow()

    private val _currentRoom = MutableStateFlow("general")
    val currentRoom: StateFlow<String> = _currentRoom.asStateFlow()

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore.asStateFlow()

    private val _hasMore = MutableStateFlow(true)
    val hasMore: StateFlow<Boolean> = _hasMore.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _loginError = MutableStateFlow<String?>(null)
    val loginError: StateFlow<String?> = _loginError.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private var token: String = ""
    private var userId: Long = 0
    private var username: String = ""
    private var typingTimeout: kotlinx.coroutines.Job? = null

    init {
        checkExistingAuth()
        ApiClient.setTokenProvider { authManager.tokenFlow.firstOrNull() }
    }

    private fun checkExistingAuth() {
        viewModelScope.launch {
            val savedToken = authManager.tokenFlow.firstOrNull()
            val savedUserId = authManager.userIdFlow.firstOrNull()
            val savedUsername = authManager.usernameFlow.firstOrNull()

            if (savedToken != null && savedUserId != null && savedUsername != null) {
                token = savedToken
                userId = savedUserId.toLong()
                username = savedUsername
                _authState.value = AuthState.LoggedIn
                connectSocket()
                loadInitialData()
            } else {
                _authState.value = AuthState.LoggedOut
            }
        }
    }

    fun login(username: String, password: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _loginError.value = null
            try {
                val res = api.login(AuthRequest(username, password))
                if (res.isSuccessful && res.body() != null) {
                    val body = res.body()!!
                    token = body.token
                    userId = body.user.id
                    this@ChatViewModel.username = body.user.username
                    authManager.saveAuth(token, userId, username)
                    _authState.value = AuthState.LoggedIn
                    connectSocket()
                    loadInitialData()
                } else {
                    _loginError.value = parseError(res.errorBody()?.string())
                }
            } catch (e: Exception) {
                _loginError.value = "连接失败: ${e.localizedMessage}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun register(username: String, password: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _loginError.value = null
            try {
                val res = api.register(AuthRequest(username, password))
                if (res.isSuccessful && res.body() != null) {
                    val body = res.body()!!
                    token = body.token
                    userId = body.user.id
                    this@ChatViewModel.username = body.user.username
                    authManager.saveAuth(token, userId, username)
                    _authState.value = AuthState.LoggedIn
                    connectSocket()
                    loadInitialData()
                } else {
                    _loginError.value = parseError(res.errorBody()?.string())
                }
            } catch (e: Exception) {
                _loginError.value = "连接失败: ${e.localizedMessage}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            socketManager.disconnect()
            authManager.clear()
            token = ""
            userId = 0
            username = ""
            _authState.value = AuthState.LoggedOut
            _messages.value = emptyList()
            _rooms.value = emptyList()
            _friends.value = emptyList()
        }
    }

    private fun connectSocket() {
        socketManager.connect(token, userId.toString(), username)
        setupSocketListeners()
    }

    private fun setupSocketListeners() {
        socketManager.on("user-list") { args ->
            if (args.isNotEmpty() && args[0] is JSONArray) {
                val arr = args[0] as JSONArray
                val list = mutableListOf<User>()
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    list.add(User(obj.getLong("id"), obj.getString("username")))
                }
                _users.value = list
            }
        }

        socketManager.on("user-joined") { args ->
            if (args.isNotEmpty() && args[0] is JSONObject) {
                val obj = args[0] as JSONObject
                val user = User(obj.getLong("id"), obj.getString("username"))
                _users.value = _users.value + user
            }
        }

        socketManager.on("user-left") { args ->
            if (args.isNotEmpty() && args[0] is JSONObject) {
                val obj = args[0] as JSONObject
                val id = obj.getLong("id")
                _users.value = _users.value.filter { it.id != id }
            }
        }

        socketManager.on("message-history") { args ->
            if (args.isNotEmpty() && args[0] is JSONArray) {
                val arr = args[0] as JSONArray
                val list = mutableListOf<Message>()
                for (i in arr.length() - 1 downTo 0) {
                    list.add(parseMessage(arr.getJSONObject(i)))
                }
                _messages.value = list
                // Cache to database
                viewModelScope.launch {
                    val roomId = _currentRoom.value
                    list.forEach { db.messageDao().insert(it.toEntity(roomId)) }
                }
            }
        }

        socketManager.on("new-message") { args ->
            if (args.isNotEmpty() && args[0] is JSONObject) {
                val msg = parseMessage(args[0] as JSONObject)
                _messages.value = _messages.value + msg
                viewModelScope.launch {
                    db.messageDao().insert(msg.toEntity(_currentRoom.value))
                }
            }
        }

        socketManager.on("system-message") { args ->
            if (args.isNotEmpty() && args[0] is JSONObject) {
                val obj = args[0] as JSONObject
                val msg = Message(
                    id = (System.currentTimeMillis() + Math.random()).toString(),
                    type = "system",
                    content = obj.optString("content", ""),
                    timestamp = System.currentTimeMillis()
                )
                _messages.value = _messages.value + msg
            }
        }

        socketManager.on("user-typing") { args ->
            if (args.isNotEmpty() && args[0] is JSONObject) {
                val obj = args[0] as JSONObject
                val map = _typingUsers.value.toMutableMap()
                map[obj.optString("userId", "")] = obj.optString("username", "")
                _typingUsers.value = map
            }
        }

        socketManager.on("user-stop-typing") { args ->
            if (args.isNotEmpty() && args[0] is JSONObject) {
                val obj = args[0] as JSONObject
                val map = _typingUsers.value.toMutableMap()
                map.remove(obj.optString("userId", ""))
                _typingUsers.value = map
            }
        }

        socketManager.on("call-incoming") { /* TODO: handle calls */ }
        socketManager.on("call-signal") { /* TODO: handle WebRTC */ }
        socketManager.on("friend-request-received") {
            _friendRequests.value = _friendRequests.value + 1
        }
    }

    fun joinRoom(roomId: String) {
        _currentRoom.value = roomId
        _messages.value = emptyList()
        _hasMore.value = true
        socketManager.joinRoom(roomId)

        viewModelScope.launch {
            val cached = db.messageDao().getMessages(roomId)
            if (cached.isNotEmpty()) {
                _messages.value = cached.map { it.toMessage() }
            }
        }
    }

    fun sendMessage(text: String) {
        socketManager.sendMessage(_currentRoom.value, text)
    }

    fun sendTyping(isTyping: Boolean) {
        socketManager.sendTyping(_currentRoom.value, isTyping)

        if (isTyping) {
            typingTimeout?.cancel()
            typingTimeout = viewModelScope.launch {
                kotlinx.coroutines.delay(2000)
                socketManager.sendTyping(_currentRoom.value, false)
            }
        }
    }

    fun loadMore() {
        val msgs = _messages.value
        if (msgs.isEmpty() || !_hasMore.value || _isLoadingMore.value) return

        viewModelScope.launch {
            _isLoadingMore.value = true
            try {
                val roomId = _currentRoom.value
                val oldest = msgs.first()
                val res = if (roomId.startsWith("private_")) {
                    val otherId = roomId.removePrefix("private_").split("_")
                        .firstOrNull { it != userId.toString() }?.toLongOrNull() ?: return@launch
                    api.getPrivateMessages(otherId, 50, oldest.timestamp)
                } else {
                    api.getMessages(roomId, 50, oldest.timestamp)
                }

                if (res.isSuccessful && res.body() != null) {
                    val older = res.body()!!
                    if (older.isEmpty()) {
                        _hasMore.value = false
                    } else {
                        _messages.value = older + _messages.value
                    }
                }
            } catch (e: Exception) {
                Log.e("ChatVM", "loadMore error: ${e.message}")
            } finally {
                _isLoadingMore.value = false
            }
        }
    }

    fun loadRooms() {
        viewModelScope.launch {
            try {
                val res = api.getRooms()
                if (res.isSuccessful && res.body() != null) {
                    _rooms.value = res.body()!!
                }
            } catch (e: Exception) {
                Log.e("ChatVM", "loadRooms error: ${e.message}")
            }
        }
    }

    fun loadFriends() {
        viewModelScope.launch {
            try {
                val res = api.getFriends()
                if (res.isSuccessful && res.body() != null) {
                    _friends.value = res.body()!!
                }

                val reqRes = api.getFriendRequests()
                if (reqRes.isSuccessful && reqRes.body() != null) {
                    _friendRequests.value = reqRes.body()!!.incoming.size
                }
            } catch (e: Exception) {
                Log.e("ChatVM", "loadFriends error: ${e.message}")
            }
        }
    }

    fun callUser(user: User) {
        socketManager.callUser(user.id.toString())
    }

    private fun loadInitialData() {
        loadRooms()
        loadFriends()
        joinRoom("general")
    }

    private fun parseMessage(obj: JSONObject): Message {
        val fileObj = obj.optJSONObject("file")
        return Message(
            id = obj.optString("id", ""),
            type = obj.optString("type", "text"),
            content = obj.optString("content", ""),
            sender = obj.optString("sender", ""),
            senderId = obj.optString("senderId", ""),
            timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
            file = if (fileObj != null) FileInfo(
                id = fileObj.optString("id", ""),
                name = fileObj.optString("name", ""),
                size = fileObj.optLong("size", 0),
                type = fileObj.optString("type", ""),
                url = fileObj.optString("url", "")
            ) else null
        )
    }

    private fun parseError(errorBody: String?): String {
        if (errorBody == null) return "未知错误"
        return try {
            JSONObject(errorBody).optString("error", "请求失败")
        } catch (e: Exception) {
            "请求失败"
        }
    }

    fun clearError() { _loginError.value = null; _error.value = null }

    sealed class AuthState {
        object Loading : AuthState()
        object LoggedOut : AuthState()
        object LoggedIn : AuthState()
    }
}
