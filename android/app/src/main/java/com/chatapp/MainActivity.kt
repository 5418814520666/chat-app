package com.chatapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
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

    private val viewModel: ChatViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            ChatAppTheme {
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
    val rooms by viewModel.rooms.collectAsStateWithLifecycle()
    val friends by viewModel.friends.collectAsStateWithLifecycle()
    val friendRequestCount by viewModel.friendRequestCount.collectAsStateWithLifecycle()
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val users by viewModel.users.collectAsStateWithLifecycle()
    val typingUsers by viewModel.typingUsers.collectAsStateWithLifecycle()
    val currentRoom by viewModel.currentRoom.collectAsStateWithLifecycle()
    val hasMore by viewModel.hasMore.collectAsStateWithLifecycle()
    val isLoadingMore by viewModel.isLoadingMore.collectAsStateWithLifecycle()
    val authState by viewModel.authState.collectAsStateWithLifecycle()
    val tokenRef = remember { mutableStateOf("") }
    val userIdRef = remember { mutableStateOf("") }
    val usernameRef = remember { mutableStateOf("") }

    var showSidebar by remember { mutableStateOf(false) }

    val roomItems = remember(rooms) {
        rooms.map { RoomItem(it.room_id, "#${it.room_id}") }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Sidebar overlay
        if (showSidebar) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background.copy(alpha = 0.5f))
                    .then(Modifier.toString().let { Modifier })
            ) {
                // Empty click to dismiss - handled below
            }
        }

        Row(modifier = Modifier.fillMaxSize()) {
            // Desktop sidebar
            if (!showSidebar) {
                Surface(
                    modifier = Modifier.width(220.dp).fillMaxHeight(),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 2.dp
                ) {
                    Column {
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(modifier = Modifier.padding(horizontal = 16.dp)) {
                            Text("Chat App", color = MaterialTheme.colorScheme.primary, fontSize = 18.sp)
                            Spacer(Modifier.weight(1f))
                        }
                        RoomListPanel(
                            rooms = roomItems,
                            friends = friends,
                            friendRequests = friendRequestCount,
                            currentRoomId = currentRoom,
                            onRoomClick = { viewModel.joinRoom(it) },
                            onTabFriends = { viewModel.loadFriends() }
                        )
                    }
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                ChatScreen(
                    roomId = currentRoom,
                    roomName = if (currentRoom.startsWith("private_")) "@私聊" else "#$currentRoom",
                    messages = messages,
                    users = users,
                    currentUserId = userIdRef.value,
                    currentUsername = usernameRef.value,
                    typingUsers = typingUsers,
                    hasMore = hasMore,
                    isLoadingMore = isLoadingMore,
                    onSendMessage = viewModel::sendMessage,
                    onSendFile = { _, _, _ -> },
                    onLoadMore = viewModel::loadMore,
                    onTyping = viewModel::sendTyping,
                    onCallUser = viewModel::callUser,
                    isPrivate = currentRoom.startsWith("private_")
                )
            }
        }
    }
}
