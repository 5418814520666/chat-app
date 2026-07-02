package com.chatapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chatapp.ui.chat.*
import com.chatapp.ui.login.LoginScreen
import com.chatapp.ui.login.RegisterScreen
import com.chatapp.ui.theme.ChatTheme
import com.chatapp.ui.theme.WxDarkBg
import com.chatapp.ui.theme.WxDarkSurface
import com.chatapp.ui.theme.WxGreen
import com.chatapp.viewmodel.ChatViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ChatTheme {
                val vm: ChatViewModel by viewModels()
                val auth = vm.authState.collectAsStateWithLifecycle().value

                when (auth) {
                    is ChatViewModel.AuthState.Checking -> Box(Modifier.fillMaxSize().background(WxDarkBg))
                    is ChatViewModel.AuthState.LoggedOut -> AuthGate(vm)
                    is ChatViewModel.AuthState.LoggedIn -> MainApp(vm)
                }
            }
        }
    }
}

// ---- Auth Gate (Login / Register) ----
@Composable
fun AuthGate(vm: ChatViewModel) {
    val err by vm.error.collectAsStateWithLifecycle()
    val loading by vm.isLoading.collectAsStateWithLifecycle()
    var showRegister by remember { mutableStateOf(false) }

    if (showRegister) {
        RegisterScreen(
            onRegister = vm::register,
            onToLogin = { showRegister = false; vm.clearError() },
            error = err, loading = loading
        )
    } else {
        LoginScreen(
            onLogin = vm::login,
            onToRegister = { showRegister = true; vm.clearError() },
            error = err, loading = loading
        )
    }
}

// ---- Main App (WeChat 4-tab) ----
data class Tab(val label: String, val icon: ImageVector)

val tabs = listOf(
    Tab("微信", Icons.AutoMirrored.Filled.Chat),
    Tab("通讯录", Icons.Filled.People),
    Tab("发现", Icons.Filled.Explore),
    Tab("我", Icons.Filled.Person)
)

@Composable
fun MainApp(vm: ChatViewModel) {
    val tabIdx by vm.tab.collectAsStateWithLifecycle()
    val rooms by vm.rooms.collectAsStateWithLifecycle()
    val friends by vm.friends.collectAsStateWithLifecycle()
    val friendReqs by vm.friendReqs.collectAsStateWithLifecycle()
    val messages by vm.messages.collectAsStateWithLifecycle()

    var showChat by remember { mutableStateOf(false) }
    var showAddFriend by remember { mutableStateOf(false) }

    val isFullScreen = showChat || showAddFriend

    Scaffold(
        bottomBar = {
            if (!isFullScreen) {
                NavigationBar(containerColor = WxDarkSurface) {
                    tabs.forEachIndexed { i, t ->
                        NavigationBarItem(
                            selected = tabIdx == i,
                            onClick = { vm.selectTab(i) },
                            icon = { Icon(t.icon, t.label) },
                            label = { Text(t.label, fontSize = 11.sp) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = WxGreen, selectedTextColor = WxGreen,
                                unselectedIconColor = Color.Gray, unselectedTextColor = Color.Gray,
                                indicatorColor = WxDarkSurface
                            )
                        )
                    }
                }
            }
        }
    ) { padding ->
        Box(Modifier.padding(padding)) {
            when {
                showChat -> ChatDetailScreen(
                    roomName = vm.roomName(), messages = messages, myId = vm.me.id,
                    onBack = { showChat = false; vm.leaveRoom() },
                    onSend = { /* TODO: Socket.IO 发送消息 */ }
                )
                showAddFriend -> AddFriendScreen(
                    onBack = { showAddFriend = false },
                    onSearch = vm::searchUsers,
                    onAdd = vm::sendFriendReq
                )
                else -> when (tabIdx) {
                    0 -> ChatListScreen(
                        rooms = rooms,
                        friends = friends,
                        onFriendClick = { f ->
                            vm.enterRoom(vm.privateRoomId(f.id), f.username)
                            showChat = true
                        },
                        onRoomClick = { r ->
                            vm.enterRoom(r.room_id, r.room_id)
                            showChat = true
                        },
                        onAddFriend = { showAddFriend = true }
                    )
                    1 -> ContactsScreen(
                        friends = friends,
                        requests = friendReqs.incoming,
                        onChat = { f ->
                            vm.enterRoom(vm.privateRoomId(f.id), f.username)
                            showChat = true
                        },
                        onAccept = vm::acceptReq,
                        onReject = vm::rejectReq,
                        onSearch = { vm.searchUsers(it) {} }
                    )
                    2 -> DiscoverScreen()
                    3 -> ProfileScreen(vm.me.username, vm::logout)
                }
            }
        }
    }
}
