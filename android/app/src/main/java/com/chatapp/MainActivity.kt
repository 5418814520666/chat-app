package com.chatapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chatapp.data.model.User
import com.chatapp.ui.chat.*
import com.chatapp.ui.login.LoginScreen
import com.chatapp.ui.login.RegisterScreen
import com.chatapp.ui.theme.ChatAppTheme
import com.chatapp.viewmodel.ChatViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ChatAppTheme {
                val vm: ChatViewModel by viewModels()
                when (val s = vm.authState.collectAsStateWithLifecycle().value) {
                    is ChatViewModel.AuthState.Loading -> {}
                    is ChatViewModel.AuthState.LoggedOut -> LoginGate(vm)
                    is ChatViewModel.AuthState.LoggedIn -> WechatApp(vm)
                }
            }
        }
    }
}

@Composable
fun LoginGate(vm: ChatViewModel) {
    val isLoading by vm.isLoading.collectAsStateWithLifecycle()
    val error by vm.loginError.collectAsStateWithLifecycle()
    var isRegister by remember { mutableStateOf(false) }
    if (isRegister) {
        RegisterScreen(vm::register, { isRegister = false; vm.clearError() }, error, isLoading)
    } else {
        LoginScreen(vm::login, { isRegister = true; vm.clearError() }, error, isLoading)
    }
}

data class TabItem(val label: String, val icon: ImageVector)

val tabs = listOf(
    TabItem("微信", Icons.Filled.Chat),
    TabItem("通讯录", Icons.Filled.People),
    TabItem("发现", Icons.Filled.Explore),
    TabItem("我", Icons.Filled.Person)
)

@Composable
fun WechatApp(vm: ChatViewModel) {
    val tab by vm.selectedTab.collectAsStateWithLifecycle()
    val rooms by vm.rooms.collectAsStateWithLifecycle()
    val friends by vm.friends.collectAsStateWithLifecycle()
    val friendReqs = vm.friendRequests.collectAsStateWithLifecycle().value
    val messages by vm.messages.collectAsStateWithLifecycle()
    val currentRoom = vm.currentRoom
    val currentUserId = vm.userId

    var showChat by remember { mutableStateOf(false) }
    var showAddFriend by remember { mutableStateOf(false) }
    var chatTitle by remember { mutableStateOf("") }

    Scaffold(
        bottomBar = {
            if (!showChat && !showAddFriend) {
                NavigationBar(containerColor = WechatDarkSurface, contentColor = WechatGreen) {
                    tabs.forEachIndexed { i, t ->
                        NavigationBarItem(
                            selected = tab == i,
                            onClick = { vm.selectTab(i) },
                            icon = { Icon(t.icon, t.label) },
                            label = { Text(t.label, fontSize = 11.sp) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = WechatGreen,
                                selectedTextColor = WechatGreen,
                                unselectedIconColor = Color.Gray,
                                unselectedTextColor = Color.Gray,
                                indicatorColor = WechatDarkSurface
                            )
                        )
                    }
                }
            }
        }
    ) { padding ->
        Box(Modifier.padding(padding)) {
            if (showChat) {
                ChatDetailScreen(
                    roomName = chatTitle, messages = messages,
                    currentUserId = currentUserId,
                    onBack = { showChat = false; vm.loadAll() },
                    onSend = { /* TODO: socket send */ }
                )
            } else if (showAddFriend) {
                AddFriendScreen(
                    vm = vm,
                    onBack = { showAddFriend = false }
                )
            } else {
                when (tab) {
                    0 -> ChatListScreen(rooms, friends,
                        onRoomClick = { id, title ->
                            chatTitle = title; showChat = true; vm.joinRoom(id)
                        },
                        onAddFriend = { showAddFriend = true }
                    )
                    1 -> ContactsScreen(
                        friends = friends,
                        friendRequests = friendReqs?.incoming ?: emptyList(),
                        onChat = { f ->
                            val roomId = getPrivateRoomId(f.id.toString(), currentUserId.toString())
                            chatTitle = f.username; showChat = true; vm.joinRoom(roomId)
                        },
                        onAccept = vm::acceptFriend,
                        onReject = vm::rejectFriend,
                        onSearch = { q -> vm.searchUsers(q) {} }
                    )
                    2 -> DiscoverScreen()
                    3 -> ProfileScreen(vm.username, vm::logout)
                }
            }
        }
    }
}

@Composable
fun AddFriendScreen(vm: ChatViewModel, onBack: () -> Unit) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<com.chatapp.data.model.SearchUser>>(emptyList()) }
    var msg by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize().background(WechatDarkBg)) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, null, tint = Color.White) }
            Text("添加好友", fontSize = 18.sp, color = Color.White)
        }

        OutlinedTextField(
            value = query, onValueChange = {
                query = it
                if (it.length >= 2) vm.searchUsers(it) { results = it }
            },
            placeholder = { Text("搜索用户名", color = Color.Gray) },
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = WechatGreen, unfocusedBorderColor = Color.Gray,
                focusedTextColor = Color.White, unfocusedTextColor = Color.White
            )
        )

        if (msg.isNotEmpty()) {
            Text(msg, color = WechatGreen, modifier = Modifier.padding(horizontal = 16.dp))
        }

        results.forEach { user ->
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                Box(Modifier.size(40.dp).background(Color(0xFF4A90D9), androidx.compose.foundation.shape.CircleShape),
                    contentAlignment = androidx.compose.ui.Alignment.Center) {
                    Text(user.username.first().uppercase(), color = Color.White)
                }
                Text(user.username, color = Color.White, modifier = Modifier.weight(1f).padding(start = 12.dp))
                Button(
                    onClick = { vm.sendFriendRequest(user.id) { ok, m -> msg = m } },
                    colors = ButtonDefaults.buttonColors(containerColor = WechatGreen)
                ) { Text("添加", fontSize = 13.sp) }
            }
        }
    }
}
