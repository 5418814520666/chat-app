package com.chatapp.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chatapp.data.model.FriendRequest
import com.chatapp.data.model.User
import com.chatapp.ui.theme.WxDarkBg
import com.chatapp.ui.theme.WxDarkSurface
import com.chatapp.ui.theme.WxGray
import com.chatapp.ui.theme.WxGreen

@Composable
fun ContactsScreen(
    friends: List<User>,
    requests: List<FriendRequest>,
    onChat: (User) -> Unit,
    onAccept: (Long, Long) -> Unit,
    onReject: (Long, Long) -> Unit,
    onSearch: (String) -> Unit
) {
    var q by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize().background(WxDarkBg)) {
        Text("通讯录", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.White,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp))

        OutlinedTextField(
            q, { q = it; if (it.length >= 2) onSearch(it) },
            placeholder = { Text("搜索好友", color = WxGray) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            singleLine = true,
            leadingIcon = { Icon(Icons.Filled.Search, null, tint = WxGray) },
            shape = RoundedCornerShape(8.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color.Transparent, unfocusedBorderColor = Color.Transparent,
                focusedContainerColor = WxDarkSurface, unfocusedContainerColor = WxDarkSurface,
                focusedTextColor = Color.White, unfocusedTextColor = Color.White
            )
        )

        Spacer(Modifier.height(12.dp))

        LazyColumn {
            if (requests.isNotEmpty()) {
                item { Text("新的朋友", color = WxGray, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) }
                items(requests) { req ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(40.dp).clip(CircleShape).background(WxGreen), contentAlignment = Alignment.Center) {
                            Text((req.fromUsername ?: "?").first().uppercase(), color = Color.White, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(req.fromUsername ?: "未知", color = Color.White)
                            Text("请求添加你为好友", color = WxGray, fontSize = 12.sp)
                        }
                        TextButton(onClick = { onAccept(req.id, req.fromUserId) }) { Text("同意", color = WxGreen) }
                        TextButton(onClick = { onReject(req.id, req.fromUserId) }) { Text("拒绝", color = WxGray) }
                    }
                }
                item { Divider(color = Color(0xFF333333)) }
            }

            item { Text("好友 (${friends.size})", color = WxGray, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) }
            items(friends) { f ->
                Row(
                    Modifier.fillMaxWidth().clickable { onChat(f) }.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(40.dp).clip(CircleShape).background(Color(0xFF4A90D9)), contentAlignment = Alignment.Center) {
                        Text(f.username.first().uppercase(), color = Color.White, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(f.username, color = Color.White, fontSize = 16.sp)
                }
            }
        }
    }
}
