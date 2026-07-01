package com.chatapp.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.chatapp.data.model.Message
import com.chatapp.data.model.User

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    roomId: String,
    roomName: String,
    messages: List<Message>,
    users: List<User>,
    currentUserId: String,
    currentUsername: String,
    typingUsers: Map<String, String>,
    hasMore: Boolean,
    isLoadingMore: Boolean,
    onSendMessage: (String) -> Unit,
    onSendFile: (ByteArray, String, String) -> Unit,
    onLoadMore: () -> Unit,
    onTyping: (Boolean) -> Unit,
    onCallUser: (User) -> Unit,
    isPrivate: Boolean = false
) {
    var inputText by remember { mutableStateOf("") }
    var showCallMenu by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val firstVisibleIndex = remember { derivedStateOf { listState.firstVisibleItemIndex } }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    // Load more when scrolling to top
    LaunchedEffect(firstVisibleIndex.value) {
        if (firstVisibleIndex.value == 0 && hasMore && !isLoadingMore && messages.isNotEmpty()) {
            onLoadMore()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = roomName,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )

            if (!isPrivate && users.isNotEmpty()) {
                IconButton(onClick = { showCallMenu = !showCallMenu }) {
                    Icon(Icons.Filled.Videocam, "通话", tint = MaterialTheme.colorScheme.primary)
                }
            }
        }

        // Typing indicator
        if (typingUsers.isNotEmpty()) {
            Text(
                text = "${typingUsers.values.joinToString(", ")} 正在输入...",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }

        // Call menu dropdown
        if (showCallMenu) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column {
                    users.forEach { user ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onCallUser(user)
                                    showCallMenu = false
                                }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    user.username.first().uppercase(),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    fontSize = 14.sp
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(user.username, color = MaterialTheme.colorScheme.onSurface)
                        }
                        if (user != users.last()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(1.dp)
                                    .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                            )
                        }
                    }
                }
            }
        }

        // Message list
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
            reverseLayout = false
        ) {
            if (isLoadingMore) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    }
                }
            }

            items(messages) { msg ->
                MessageBubble(
                    message = msg,
                    isOwn = msg.senderId == currentUserId,
                    baseUrl = "https://chat.yangchen.skin"
                )
            }
        }

        // Input area
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(8.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            // File button
            IconButton(onClick = { /* TODO: file picker */ }, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Filled.AttachFile, "文件", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            OutlinedTextField(
                value = inputText,
                onValueChange = {
                    inputText = it
                    if (it.isNotEmpty()) onTyping(true)
                },
                placeholder = { Text("输入消息...", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                modifier = Modifier.weight(1f),
                maxLines = 4,
                shape = RoundedCornerShape(20.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                )
            )

            IconButton(
                onClick = {
                    val text = inputText.trim()
                    if (text.isNotEmpty()) {
                        onSendMessage(text)
                        inputText = ""
                        onTyping(false)
                    }
                },
                modifier = Modifier.size(40.dp),
                enabled = inputText.isNotBlank()
            ) {
            Icon(
                Icons.AutoMirrored.Filled.Send,
                "发送",
                tint = if (inputText.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
            }
        }
    }
}

@Composable
fun MessageBubble(message: Message, isOwn: Boolean, baseUrl: String) {
    val align = if (isOwn) Alignment.End else Alignment.Start
    val bg = if (isOwn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val fg = if (isOwn) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp),
        horizontalAlignment = align
    ) {
        if (!isOwn) {
            Text(
                message.sender,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 12.dp, bottom = 2.dp)
            )
        }

        if (message.type == "system") {
            Text(
                message.content,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
        } else if (message.type == "file") {
            Card(
                modifier = Modifier.widthIn(max = 280.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = bg)
            ) {
                if (message.file != null) {
                    val isImage = message.file.type.startsWith("image/")
                    val isVideo = message.file.type.startsWith("video/")

                    if (isImage) {
                        AsyncImage(
                            model = baseUrl + message.file.url,
                            contentDescription = message.file.name,
                            modifier = Modifier.fillMaxWidth().heightIn(max = 200.dp)
                        )
                    }

                    Column(modifier = Modifier.padding(8.dp)) {
                        Text(message.file.name, fontSize = 13.sp, color = fg)
                        Text(
                            "${message.file.size / 1024}KB",
                            fontSize = 11.sp,
                            color = fg.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        } else {
            Card(
                modifier = Modifier.widthIn(max = 280.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = bg)
            ) {
                Text(
                    message.content,
                    modifier = Modifier.padding(12.dp),
                    color = fg,
                    fontSize = 15.sp
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoomListPanel(
    rooms: List<RoomItem>,
    friends: List<User>,
    friendRequests: Int,
    currentRoomId: String,
    onRoomClick: (String) -> Unit,
    onTabFriends: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }

    Column(modifier = Modifier.fillMaxSize()) {
        // Tabs
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary
        ) {
            Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("房间") })
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1; onTabFriends() },
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("好友")
                        if (friendRequests > 0) {
                            Badge(containerColor = MaterialTheme.colorScheme.primary) {
                                Text("$friendRequests")
                            }
                        }
                    }
                }
            )
        }

        when (selectedTab) {
            0 -> {
                LazyColumn {
                    items(rooms) { room ->
                        RoomListItem(
                            name = room.name,
                            isSelected = room.id == currentRoomId,
                            onClick = { onRoomClick(room.id) }
                        )
                    }
                }
            }
            1 -> {
                LazyColumn {
                    items(friends) { friend ->
                        FriendListItem(
                            user = friend,
                            onClick = { onRoomClick("private_${friend.id}") }
                        )
                    }
                }
            }
        }
    }
}

data class RoomItem(val id: String, val name: String)

@Composable
fun RoomListItem(name: String, isSelected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(if (isSelected) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Filled.Tag, null, tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.width(12.dp))
        Text(name, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal, fontSize = 15.sp)
    }
}

@Composable
fun FriendListItem(user: User, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center
        ) {
            Text(user.username.first().uppercase(), color = MaterialTheme.colorScheme.onPrimary)
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(user.username, fontSize = 15.sp)
    }
}
