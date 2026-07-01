package com.chatapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chatapp.ui.chat.ChatScreen
import com.chatapp.ui.chat.RoomItem
import com.chatapp.ui.chat.RoomListPanel
import com.chatapp.ui.login.LoginScreen
import com.chatapp.ui.login.RegisterScreen
import com.chatapp.ui.theme.ChatAppTheme
import com.chatapp.viewmodel.ChatViewModel

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            ChatAppTheme {
                val viewModel: ChatViewModel by viewModels()
                val authState by viewModel.authState.collectAsStateWithLifecycle()
                val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
                val error by viewModel.loginError.collectAsStateWithLifecycle()

                when (authState) {
                    is ChatViewModel.AuthState.Loading -> {
                        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
                    }
                    is ChatViewModel.AuthState.LoggedOut -> AuthFlow(
                        isLoading = isLoading,
                        error = error,
                        onLogin = viewModel::login,
                        onRegister = viewModel::register,
                        onClearError = viewModel::clearError
                    )
                    is ChatViewModel.AuthState.LoggedIn -> MainApp()
                }
            }
        }
    }
}

@Composable
fun AuthFlow(
    isLoading: Boolean,
    error: String?,
    onLogin: (String, String) -> Unit,
    onRegister: (String, String) -> Unit,
    onClearError: () -> Unit
) {
    var isRegister by remember { mutableStateOf(false) }

    if (isRegister) {
        RegisterScreen(
            onRegister = onRegister,
            onBack = {
                isRegister = false
                onClearError()
            },
            error = error,
            isLoading = isLoading
        )
    } else {
        LoginScreen(
            onLogin = onLogin,
            onSwitchToRegister = {
                isRegister = true
                onClearError()
            },
            error = error,
            isLoading = isLoading
        )
    }
}

@Composable
fun MainApp() {
    val viewModel = androidx.lifecycle.viewmodel.compose.viewModel<ChatViewModel>()

    Box(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("登录成功", fontSize = 24.sp, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(16.dp))
            Text("用户: ${viewModel.username}", color = MaterialTheme.colorScheme.onSurface)
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = { viewModel.logout() }) {
                Text("退出登录")
            }
        }
    }
}
