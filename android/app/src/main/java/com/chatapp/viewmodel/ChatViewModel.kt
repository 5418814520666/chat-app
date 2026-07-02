package com.chatapp.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.chatapp.data.api.ApiClient
import com.chatapp.data.model.AuthRequest
import com.chatapp.data.model.User
import com.chatapp.data.prefs.AuthManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val authManager = AuthManager(application)
    private val api = ApiClient.apiService

    private val _authState = MutableStateFlow<AuthState>(AuthState.Loading)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val _loginError = MutableStateFlow<String?>(null)
    val loginError: StateFlow<String?> = _loginError.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    var userId: Long = 0
        private set
    var username: String = ""
        private set
    var token: String = ""
        private set

    init { checkExistingAuth() }

    private fun checkExistingAuth() {
        viewModelScope.launch {
            try {
                val t = authManager.tokenFlow.firstOrNull()
                val uid = authManager.userIdFlow.firstOrNull()
                val uname = authManager.usernameFlow.firstOrNull()
                if (t != null && uid != null && uname != null) {
                    token = t; userId = uid.toLong(); username = uname
                    ApiClient.setToken(token)
                    _authState.value = AuthState.LoggedIn
                    Log.d("ChatVM", "Auto-login SUCCESS uid=$uid")
                } else {
                    _authState.value = AuthState.LoggedOut
                }
            } catch (e: Exception) {
                Log.e("ChatVM", "Auto-login error: ${e.message}", e)
                _authState.value = AuthState.LoggedOut
            }
        }
    }

    fun login(username: String, password: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _loginError.value = null
            try {
                // SKIP NETWORK - just simulate success for debug
                Log.d("ChatVM", "Login SIMULATED user=$username")
                userId = System.currentTimeMillis()
                this@ChatViewModel.username = username
                token = "debug-token"
                _authState.value = AuthState.LoggedIn
            } catch (e: Exception) {
                Log.e("ChatVM", "Login EXCEPTION: ${e.message}", e)
                _loginError.value = "错误: ${e.localizedMessage}"
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
                Log.d("ChatVM", "Register START user=$username")
                val res = withContext(Dispatchers.IO) {
                    api.register(AuthRequest(username, password))
                }
                Log.d("ChatVM", "Register HTTP ${res.code()} ${res.isSuccessful}")
                if (res.isSuccessful && res.body() != null) {
                    val body = res.body()!!
                    token = body.token
                    userId = body.user.id
                    this@ChatViewModel.username = body.user.username
                    ApiClient.setToken(token)
                    authManager.saveAuth(token, userId, username)
                    Log.d("ChatVM", "Register OK userId=$userId")
                    _authState.value = AuthState.LoggedIn
                } else {
                    val err = res.errorBody()?.string()
                    _loginError.value = try { JSONObject(err ?: "{}").optString("error", "请求失败") } catch (_: Exception) { "请求失败" }
                    Log.e("ChatVM", "Register FAIL HTTP ${res.code()} err=$err")
                }
            } catch (e: Exception) {
                Log.e("ChatVM", "Register EXCEPTION: ${e.message}", e)
                _loginError.value = "连接失败: ${e.localizedMessage}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            authManager.clear()
            ApiClient.setToken(null)
        }
        token = ""; userId = 0; username = ""
        _authState.value = AuthState.LoggedOut
    }

    fun clearError() { _loginError.value = null }

    sealed class AuthState {
        object Loading : AuthState()
        object LoggedOut : AuthState()
        object LoggedIn : AuthState()
    }
}
