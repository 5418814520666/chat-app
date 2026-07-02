package com.chatapp.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chatapp.data.model.RoomInfo
import com.chatapp.data.model.User
import com.chatapp.ui.theme.WxDarkBg
import com.chatapp.ui.theme.WxDarkSurface
import com.chatapp.ui.theme.WxGray
import com.chatapp.ui.theme.WxGreen

@Composable
fun ChatListScreen(
    rooms: List<RoomInfo>,
    friends: List<User>,
    onFriendClick: (User) -> Unit,
    onRoomClick: (RoomInfo) -> Unit,
    onAddFriend: () -> Unit
) {
    Column(Modifier.fillMaxSize().background(WxDarkBg)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("微信", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Row {
                IconButton(onClick = {}) { Icon(Icons.Filled.Search, null, tint = Color.White) }
                IconButton(onClick = onAddFriend) { Icon(Icons.Filled.PersonAdd, null, tint = Color.White) }
            }
        }

        LazyColumn {
            items(friends) { f ->
                ChatItem(
                    name = f.username,
                    subtitle = "",
                    avatar = f.username.first().uppercase(),
                    color = Color(0xFF4A90D9),
                    onClick = { onFriendClick(f) }
                )
            }
            items(rooms.filter { !it.room_id.startsWith("private_") }) { r ->
                ChatItem(
                    name = r.room_id,
                    subtitle = "${r.message_count} 条消息",
                    avatar = r.room_id.first().uppercase(),
                    color = WxGreen,
                    onClick = { onRoomClick(r) }
                )
            }
        }
    }
}

@Composable
fun ChatItem(name: String, subtitle: String, avatar: String, color: Color, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(48.dp).clip(CircleShape).background(color), contentAlignment = Alignment.Center) {
            Text(avatar, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(name, color = Color.White, fontSize = 16.sp)
            if (subtitle.isNotEmpty())
                Text(subtitle, color = WxGray, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
