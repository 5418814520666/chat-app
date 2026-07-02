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
import java.util.UUID

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val authManager = AuthManager(application)

    private val _authState = MutableStateFlow<AuthState>(AuthState.Loading)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val _loginError = MutableStateFlow<String?>(null)
    val loginError = _loginError.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _rooms = MutableStateFlow<List<RoomInfo>>(emptyList())
    val rooms = _rooms.asStateFlow()

    private val _friends = MutableStateFlow<List<User>>(emptyList())
    val friends = _friends.asStateFlow()

    private val _friendRequests = MutableStateFlow<FriendRequestsResponse?>(null)
    val friendRequests = _friendRequests.asStateFlow()

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages = _messages.asStateFlow()

    private val _currentRoom = MutableStateFlow("general")
    val currentRoom: String get() = _currentRoom.value

    private val _selectedTab = MutableStateFlow(0)
    val selectedTab = _selectedTab.asStateFlow()

    var userId: Long = 0; private set
    var username: String = ""; private set

    init { checkExistingAuth() }

    private fun checkExistingAuth() {
        viewModelScope.launch {
            try {
                val t = authManager.tokenFlow.firstOrNull()
                val uid = authManager.userIdFlow.firstOrNull()
                val uname = authManager.usernameFlow.firstOrNull()
                if (t != null && uid != null && uname != null) {
                    ApiClient.token = t
                    userId = uid.toLong(); username = uname
                    _authState.value = AuthState.LoggedIn
                    loadAll()
                } else _authState.value = AuthState.LoggedOut
            } catch (e: Exception) {
                Log.e("ChatVM", "Auth check: ${e.message}")
                _authState.value = AuthState.LoggedOut
            }
        }
    }

    fun login(username: String, password: String) {
        viewModelScope.launch {
            _isLoading.value = true; _loginError.value = null
            try {
                val res = withContext(Dispatchers.IO) {
                    ApiClient.apiLogin(username, password)
                }
                if (res != null) {
                    ApiClient.token = res.token
                    userId = res.user.id; this@ChatViewModel.username = res.user.username
                    authManager.saveAuth(res.token, userId, username)
                    _authState.value = AuthState.LoggedIn
                    loadAll()
                } else {
                    _loginError.value = "连接失败，请检查网络"
                }
            } catch (e: Exception) {
                Log.e("ChatVM", "Login: ${e.message}", e)
                _loginError.value = "连接失败: ${e.localizedMessage}"
            } finally { _isLoading.value = false }
        }
    }

    fun register(username: String, password: String) {
        viewModelScope.launch {
            _isLoading.value = true; _loginError.value = null
            try {
                val res = withContext(Dispatchers.IO) {
                    ApiClient.apiRegister(username, password)
                }
                if (res != null) {
                    ApiClient.token = res.token
                    userId = res.user.id; this@ChatViewModel.username = res.user.username
                    authManager.saveAuth(res.token, userId, username)
                    _authState.value = AuthState.LoggedIn
                    loadAll()
                } else {
                    _loginError.value = "连接失败，请检查网络"
                }
            } catch (e: Exception) {
                Log.e("ChatVM", "Register: ${e.message}", e)
                _loginError.value = "连接失败: ${e.localizedMessage}"
            } finally { _isLoading.value = false }
        }
    }

    fun logout() {
        viewModelScope.launch { authManager.clear() }
        ApiClient.token = null; userId = 0; username = ""
        _authState.value = AuthState.LoggedOut
    }

    fun selectTab(index: Int) { _selectedTab.value = index }

    fun joinRoom(roomId: String) {
        _currentRoom.value = roomId
        _messages.value = emptyList()
        viewModelScope.launch {
            val msgs = withContext(Dispatchers.IO) {
                if (roomId.startsWith("private_")) {
                    val parts = roomId.removePrefix("private_").split("_")
                    val other = parts.firstOrNull { it != userId.toString() }?.toLongOrNull()
                    other?.let { ApiClient.apiGetPrivateMessages(it) }
                } else ApiClient.apiGetMessages(roomId)
            }
            if (msgs != null) _messages.value = msgs
        }
    }

    fun loadMessages() {
        viewModelScope.launch {
            val room = _currentRoom.value
            val msgs = withContext(Dispatchers.IO) {
                if (room.startsWith("private_")) {
                    val parts = room.removePrefix("private_").split("_")
                    val other = parts.firstOrNull { it != userId.toString() }?.toLongOrNull()
                    other?.let { ApiClient.apiGetPrivateMessages(it) }
                } else ApiClient.apiGetMessages(room)
            }
            if (msgs != null) _messages.value = msgs
        }
    }

    fun loadAll() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                _rooms.value = ApiClient.apiGetRooms() ?: emptyList()
                _friends.value = ApiClient.apiGetFriends() ?: emptyList()
                _friendRequests.value = ApiClient.apiGetFriendRequests()
            }
        }
    }

    fun searchUsers(query: String, onResult: (List<SearchUser>) -> Unit) {
        viewModelScope.launch {
            val r = withContext(Dispatchers.IO) { ApiClient.apiSearchUsers(query) }
            onResult(r ?: emptyList())
        }
    }

    fun sendFriendRequest(toUserId: Long, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) { ApiClient.apiSendFriendRequest(toUserId) }
            onResult(ok, if (ok) "已发送" else "发送失败")
        }
    }

    fun acceptFriend(requestId: Long, fromUserId: Long) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { ApiClient.apiAcceptFriend(requestId, fromUserId) }
            loadAll()
        }
    }

    fun rejectFriend(requestId: Long, fromUserId: Long) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { ApiClient.apiRejectFriend(requestId, fromUserId) }
            loadAll()
        }
    }

    fun clearError() { _loginError.value = null }

    sealed class AuthState {
        object Loading : AuthState()
        object LoggedOut : AuthState()
        object LoggedIn : AuthState()
    }
}
