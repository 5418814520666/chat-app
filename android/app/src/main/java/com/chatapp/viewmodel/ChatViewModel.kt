package com.chatapp.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.chatapp.data.api.ApiClient
import com.chatapp.data.model.*
import com.chatapp.data.prefs.AuthManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ChatViewModel(app: Application) : AndroidViewModel(app) {

    private val authMgr = AuthManager(app)

    // ---- Auth ----
    sealed class AuthState { object Checking : AuthState(); object LoggedOut : AuthState(); object LoggedIn : AuthState() }

    private val _authState = MutableStateFlow<AuthState>(AuthState.Checking)
    val authState: StateFlow<AuthState> = _authState

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    var me: User = User(); private set

    // ---- Data ----
    private val _rooms = MutableStateFlow<List<RoomInfo>>(emptyList())
    val rooms: StateFlow<List<RoomInfo>> = _rooms

    private val _friends = MutableStateFlow<List<User>>(emptyList())
    val friends: StateFlow<List<User>> = _friends

    private val _friendReqs = MutableStateFlow<FriendRequestsResponse>(FriendRequestsResponse())
    val friendReqs: StateFlow<FriendRequestsResponse> = _friendReqs

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages

    // ---- UI State ----
    private val _tab = MutableStateFlow(0)
    val tab: StateFlow<Int> = _tab

    private val _currentRoomId = MutableStateFlow("")
    private val _currentRoomName = MutableStateFlow("")

    val navigatingToChat: Boolean get() = _currentRoomId.value.isNotEmpty()

    init { checkSavedAuth() }

    private fun checkSavedAuth() {
        viewModelScope.launch {
            try {
                val t = authMgr.token.firstOrNull()
                val id = authMgr.uid.firstOrNull()?.toLongOrNull()
                val name = authMgr.uname.firstOrNull()
                if (!t.isNullOrBlank() && id != null && !name.isNullOrBlank()) {
                    ApiClient.token = t
                    me = User(id, name)
                    _authState.value = AuthState.LoggedIn
                    refresh()
                } else {
                    _authState.value = AuthState.LoggedOut
                }
            } catch (e: Exception) {
                Log.e("VM", "checkAuth failed", e)
                _authState.value = AuthState.LoggedOut
            }
        }
    }

    // ---- Login / Register ----
    fun login(username: String, password: String) {
        viewModelScope.launch {
            _isLoading.value = true; _error.value = null
            val r = withContext(Dispatchers.IO) { ApiClient.login(username, password) }
            _isLoading.value = false
            if (r.success) {
                ApiClient.token = r.token
                me = r.user!!
                authMgr.save(r.token!!, me.id, me.username)
                _authState.value = AuthState.LoggedIn
                refresh()
            } else {
                _error.value = r.error
            }
        }
    }

    fun register(username: String, password: String) {
        viewModelScope.launch {
            _isLoading.value = true; _error.value = null
            val r = withContext(Dispatchers.IO) { ApiClient.register(username, password) }
            _isLoading.value = false
            if (r.success) {
                ApiClient.token = r.token
                me = r.user!!
                authMgr.save(r.token!!, me.id, me.username)
                _authState.value = AuthState.LoggedIn
                refresh()
            } else {
                _error.value = r.error
            }
        }
    }

    fun logout() {
        viewModelScope.launch { authMgr.clear() }
        ApiClient.token = null; me = User()
        _rooms.value = emptyList(); _friends.value = emptyList()
        _friendReqs.value = FriendRequestsResponse(); _messages.value = emptyList()
        _authState.value = AuthState.LoggedOut
    }

    fun clearError() { _error.value = null }

    // ---- Navigation ----
    fun selectTab(i: Int) { _tab.value = i }

    fun enterRoom(roomId: String, roomName: String) {
        _currentRoomId.value = roomId
        _currentRoomName.value = roomName
        loadMessages(roomId)
    }

    fun leaveRoom() {
        _currentRoomId.value = ""
        _currentRoomName.value = ""
    }

    fun roomName(): String = _currentRoomName.value

    // ---- Data loading ----
    fun refresh() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                _rooms.value = ApiClient.getRooms()
                _friends.value = ApiClient.getFriends()
                _friendReqs.value = ApiClient.getFriendRequests()
            }
        }
    }

    private fun loadMessages(roomId: String) {
        viewModelScope.launch {
            _messages.value = withContext(Dispatchers.IO) {
                if (roomId.startsWith("private_")) {
                    val parts = roomId.removePrefix("private_").split("_")
                    val other = parts.firstOrNull { it != me.id.toString() }?.toLongOrNull()
                    other?.let { ApiClient.getPrivateMessages(it) } ?: emptyList()
                } else ApiClient.getMessages(roomId)
            }
        }
    }

    // ---- Friends ----
    fun searchUsers(q: String, cb: (List<SearchUser>) -> Unit) {
        viewModelScope.launch {
            cb(withContext(Dispatchers.IO) { ApiClient.searchUsers(q) })
        }
    }

    fun sendFriendReq(toUserId: Long, cb: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) { ApiClient.sendFriendRequest(toUserId) }
            cb(ok, if (ok) "已发送" else "发送失败")
        }
    }

    fun acceptReq(requestId: Long, fromUserId: Long) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { ApiClient.acceptFriend(requestId, fromUserId) }
            refresh()
        }
    }

    fun rejectReq(requestId: Long, fromUserId: Long) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { ApiClient.rejectFriend(requestId, fromUserId) }
            refresh()
        }
    }

    // ---- Room helper ----
    fun privateRoomId(other: Long): String {
        val a = me.id; val b = other
        return "private_${minOf(a, b)}_${maxOf(a, b)}"
    }
}
