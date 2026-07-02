package com.chatapp.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chatapp.data.model.Message
import com.chatapp.data.model.RoomInfo
import com.chatapp.data.model.User

val WechatGreen = Color(0xFF07C160)
val WechatBg = Color(0xFFEDEDED)
val WechatDarkBg = Color(0xFF191919)
val WechatDarkSurface = Color(0xFF2C2C2C)

// ============ CHAT LIST (微信首页) ============
@Composable
fun ChatListScreen(
    rooms: List<RoomInfo>,
    friends: List<User>,
    onRoomClick: (String, String) -> Unit,
    onAddFriend: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().background(WechatDarkBg)) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("微信", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Row {
                IconButton(onClick = { /* search */ }) {
                    Icon(Icons.Filled.Search, "搜索", tint = Color.White)
                }
                IconButton(onClick = onAddFriend) {
                    Icon(Icons.Filled.PersonAdd, "添加", tint = Color.White)
                }
            }
        }

        LazyColumn {
            // Friend chats
            items(friends) { friend ->
                val roomId = getPrivateRoomId(friend.id.toString(), "")
                ChatListItem(
                    name = friend.username,
                    lastMsg = "",
                    time = "",
                    avatar = friend.username.first().uppercase(),
                    isGroup = false,
                    onClick = { onRoomClick(roomId, friend.username) }
                )
            }

            // Group chats
            items(rooms.filter { !it.room_id.startsWith("private_") }) { room ->
                ChatListItem(
                    name = room.room_id,
                    lastMsg = "${room.message_count} 条消息",
                    time = "",
                    avatar = room.room_id.first().uppercase(),
                    isGroup = true,
                    onClick = { onRoomClick(room.room_id, room.room_id) }
                )
            }
        }
    }
}

fun getPrivateRoomId(userId1: String, userId2: String): String {
    val a = userId1.toLongOrNull() ?: userId1.hashCode().toLong()
    val b = userId2.toLongOrNull() ?: userId2.hashCode().toLong()
    return "private_${minOf(a, b)}_${maxOf(a, b)}"
}

@Composable
fun ChatListItem(
    name: String, lastMsg: String, time: String,
    avatar: String, isGroup: Boolean, onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(48.dp).clip(CircleShape).background(if (isGroup) WechatGreen else Color(0xFF4A90D9)),
            contentAlignment = Alignment.Center
        ) {
            Text(avatar, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(name, color = Color.White, fontSize = 16.sp)
            if (lastMsg.isNotEmpty()) {
                Text(lastMsg, color = Color.Gray, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (time.isNotEmpty()) {
            Text(time, color = Color.Gray, fontSize = 12.sp)
        }
    }
}

// ============ CHAT DETAIL (聊天详情) ============
@Composable
fun ChatDetailScreen(
    roomName: String,
    messages: List<Message>,
    currentUserId: Long,
    onBack: () -> Unit,
    onSend: (String) -> Unit
) {
    var input by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize().background(WechatDarkBg)) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth().background(WechatDarkSurface).padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, "返回", tint = Color.White)
            }
            Text(roomName, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White,
                modifier = Modifier.weight(1f))
            IconButton(onClick = {}) {
                Icon(Icons.Filled.MoreVert, "更多", tint = Color.White)
            }
        }

        // Messages
        LazyColumn(
            modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
            reverseLayout = true
        ) {
            items(messages.reversed()) { msg ->
                WechatBubble(msg, msg.senderId == currentUserId.toString())
            }
        }

        // Input bar
        Row(
            modifier = Modifier.fillMaxWidth().background(WechatDarkSurface).padding(8.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            IconButton(onClick = {}) { Icon(Icons.Filled.Mic, "语音", tint = Color.Gray) }
            OutlinedTextField(
                value = input, onValueChange = { input = it },
                placeholder = { Text("", color = Color.Gray) },
                modifier = Modifier.weight(1f),
                maxLines = 4,
                shape = RoundedCornerShape(8.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White
                )
            )
            IconButton(onClick = {}) { Icon(Icons.Filled.AddCircle, "更多", tint = Color.Gray) }
            if (input.isNotBlank()) {
                Button(
                    onClick = { onSend(input.trim()); input = "" },
                    colors = ButtonDefaults.buttonColors(containerColor = WechatGreen),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.height(40.dp)
                ) { Text("发送", color = Color.White) }
            }
        }
    }
}

@Composable
fun WechatBubble(message: Message, isSelf: Boolean) {
    val bg = if (isSelf) Color(0xFF95EC69) else Color.White
    val fg = if (isSelf) Color.Black else Color.Black
    val align = if (isSelf) Alignment.End else Alignment.Start

    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalAlignment = align
    ) {
        if (message.type == "system") {
            Text(message.content, fontSize = 12.sp, color = Color.Gray,
                modifier = Modifier.padding(horizontal = 16.dp))
        } else {
            if (!isSelf) {
                Text(message.sender, fontSize = 11.sp, color = Color.Gray,
                    modifier = Modifier.padding(bottom = 2.dp, start = 8.dp))
            }
            Surface(
                shape = if (isSelf) RoundedCornerShape(12.dp, 4.dp, 12.dp, 12.dp)
                        else RoundedCornerShape(4.dp, 12.dp, 12.dp, 12.dp),
                color = bg,
                modifier = Modifier.widthIn(max = 280.dp)
            ) {
                Text(message.content, modifier = Modifier.padding(10.dp),
                    color = fg, fontSize = 15.sp)
            }
        }
    }
}

// ============ CONTACTS (通讯录) ============
@Composable
fun ContactsScreen(
    friends: List<User>,
    friendRequests: List<com.chatapp.data.model.FriendRequest>,
    onChat: (User) -> Unit,
    onAccept: (Long, Long) -> Unit,
    onReject: (Long, Long) -> Unit,
    onSearch: (String) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize().background(WechatDarkBg)) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("通讯录", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }

        // Search bar
        OutlinedTextField(
            value = searchQuery, onValueChange = { searchQuery = it; if (it.length >= 2) onSearch(it) },
            placeholder = { Text("搜索好友", color = Color.Gray) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            singleLine = true,
            leadingIcon = { Icon(Icons.Filled.Search, null, tint = Color.Gray) },
            shape = RoundedCornerShape(8.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color.Transparent,
                unfocusedBorderColor = Color.Transparent,
                focusedContainerColor = WechatDarkSurface,
                unfocusedContainerColor = WechatDarkSurface
            )
        )

        Spacer(Modifier.height(12.dp))

        LazyColumn {
            // Friend requests
            if (friendRequests.isNotEmpty()) {
                item {
                    Text("新的朋友", color = Color.Gray, fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                }
                items(friendRequests) { req ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(Modifier.size(40.dp).clip(CircleShape).background(WechatGreen),
                            contentAlignment = Alignment.Center) {
                            Text((req.fromUsername ?: "?").first().uppercase(),
                                color = Color.White, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(req.fromUsername ?: "未知", color = Color.White)
                            Text("请求添加你为好友", color = Color.Gray, fontSize = 12.sp)
                        }
                        TextButton(onClick = { onAccept(req.id, req.fromUserId ?: 0) }) {
                            Text("同意", color = WechatGreen)
                        }
                        TextButton(onClick = { onReject(req.id, req.fromUserId ?: 0) }) {
                            Text("拒绝", color = Color.Gray)
                        }
                    }
                }
                item { Divider(color = Color(0xFF333333)) }
            }

            // Friends list
            item {
                Text("好友 (${friends.size})", color = Color.Gray, fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            }
            items(friends) { friend ->
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { onChat(friend) }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(40.dp).clip(CircleShape).background(Color(0xFF4A90D9)),
                        contentAlignment = Alignment.Center) {
                        Text(friend.username.first().uppercase(), color = Color.White, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(friend.username, color = Color.White, fontSize = 16.sp)
                }
            }
        }
    }
}

// ============ DISCOVER (发现) ============
@Composable
fun DiscoverScreen() {
    Column(modifier = Modifier.fillMaxSize().background(WechatDarkBg)) {
        Text("发现", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.White,
            modifier = Modifier.padding(16.dp))
        Spacer(Modifier.height(20.dp))

        DiscoverItem(Icons.Filled.Explore, "朋友圈", onClick = {})
        DiscoverItem(Icons.Filled.Videocam, "视频号", onClick = {})
        Divider(color = Color(0xFF333333), modifier = Modifier.padding(horizontal = 16.dp))
        DiscoverItem(Icons.Filled.NearMe, "附近", onClick = {})
    }
}

@Composable
fun DiscoverItem(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = WechatGreen, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(16.dp))
        Text(label, color = Color.White, fontSize = 16.sp)
    }
}

// ============ PROFILE (我) ============
@Composable
fun ProfileScreen(username: String, onLogout: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().background(WechatDarkBg)) {
        // Avatar + Name
        Row(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(64.dp).clip(CircleShape).background(WechatGreen),
                contentAlignment = Alignment.Center) {
                Text(username.first().uppercase(), color = Color.White,
                    fontSize = 28.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(16.dp))
            Column {
                Text(username, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
        }

        Divider(color = Color(0xFF333333), modifier = Modifier.padding(horizontal = 16.dp))

        Spacer(Modifier.height(16.dp))
        ProfileItem(Icons.Filled.Settings, "设置", onClick = {})
        Divider(color = Color(0xFF333333), modifier = Modifier.padding(horizontal = 16.dp))

        Spacer(Modifier.height(40.dp))
        Button(
            onClick = onLogout,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFCC3333))
        ) { Text("退出登录") }
    }
}

@Composable
fun ProfileItem(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = WechatGreen, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(16.dp))
        Text(label, color = Color.White, fontSize = 16.sp)
    }
}
