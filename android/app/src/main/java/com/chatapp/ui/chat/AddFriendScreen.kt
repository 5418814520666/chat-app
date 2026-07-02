package com.chatapp.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chatapp.data.model.SearchUser
import com.chatapp.ui.theme.WxDarkBg
import com.chatapp.ui.theme.WxDarkSurface
import com.chatapp.ui.theme.WxGray
import com.chatapp.ui.theme.WxGreen

@Composable
fun AddFriendScreen(
    onBack: () -> Unit,
    onSearch: (String, (List<SearchUser>) -> Unit) -> Unit,
    onAdd: (Long, (Boolean, String) -> Unit) -> Unit
) {
    var q by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<SearchUser>>(emptyList()) }
    var msg by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize().background(WxDarkBg)) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White) }
            Text("添加好友", fontSize = 18.sp, color = Color.White)
        }

        OutlinedTextField(
            q, {
                q = it
                if (it.length >= 2) onSearch(it) { results = it }
            },
            placeholder = { Text("搜索用户名", color = WxGray) },
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = WxGreen, unfocusedBorderColor = Color(0xFF555555),
                focusedTextColor = Color.White, unfocusedTextColor = Color.White
            )
        )

        if (msg.isNotEmpty()) Text(msg, color = WxGreen, modifier = Modifier.padding(horizontal = 16.dp))

        results.forEach { u ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(40.dp).clip(CircleShape).background(Color(0xFF4A90D9)), contentAlignment = Alignment.Center) {
                    Text(u.username.first().uppercase(), color = Color.White)
                }
                Text(u.username, color = Color.White, modifier = Modifier.weight(1f).padding(start = 12.dp))
                Button(
                    onClick = { onAdd(u.id) { ok, m -> msg = m } },
                    colors = ButtonDefaults.buttonColors(containerColor = WxGreen)
                ) { Text("添加", fontSize = 13.sp) }
            }
        }
    }
}
