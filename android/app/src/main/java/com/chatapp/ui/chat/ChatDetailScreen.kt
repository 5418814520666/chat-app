package com.chatapp.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chatapp.data.model.Message
import com.chatapp.ui.theme.WxDarkBg
import com.chatapp.ui.theme.WxDarkSurface
import com.chatapp.ui.theme.WxGray
import com.chatapp.ui.theme.WxGreen

@Composable
fun ChatDetailScreen(
    roomName: String,
    messages: List<Message>,
    myId: Long,
    onBack: () -> Unit,
    onSend: (String) -> Unit
) {
    var input by remember { mutableStateOf("") }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()

    Column(Modifier.fillMaxSize().background(WxDarkBg)) {
        Row(
            Modifier.fillMaxWidth().background(WxDarkSurface).padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White) }
            Text(roomName, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.weight(1f))
            IconButton(onClick = {}) { Icon(Icons.Filled.MoreVert, null, tint = Color.White) }
        }

        LazyColumn(Modifier.weight(1f).padding(horizontal = 12.dp), state = listState) {
            items(messages) { msg -> MessageBubble(msg, msg.senderId == myId.toString()) }
        }

        Row(Modifier.fillMaxWidth().background(WxDarkSurface).padding(8.dp), verticalAlignment = Alignment.Bottom) {
            OutlinedTextField(
                input, { input = it }, Modifier.weight(1f),
                placeholder = {
                    // SendMessage 待集成 Socket.IO
                    Text("输入消息...", color = WxGray)
                },
                maxLines = 4,
                shape = RoundedCornerShape(8.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.Transparent, unfocusedBorderColor = Color.Transparent,
                    focusedContainerColor = Color.White, unfocusedContainerColor = Color.White,
                    focusedTextColor = Color.Black, unfocusedTextColor = Color.Black
                )
            )
            Spacer(Modifier.width(8.dp))
            if (input.isNotBlank()) {
                Button(
                    onClick = { onSend(input.trim()); input = "" },
                    colors = ButtonDefaults.buttonColors(containerColor = WxGreen),
                    shape = RoundedCornerShape(4.dp), modifier = Modifier.height(40.dp)
                ) { Text("发送", color = Color.White) }
            }
        }
    }
}

@Composable
fun MessageBubble(msg: Message, isSelf: Boolean) {
    val bg = if (isSelf) Color(0xFF95EC69) else Color.White
    val align = if (isSelf) Alignment.End else Alignment.Start
    val shape = if (isSelf) RoundedCornerShape(12.dp, 4.dp, 12.dp, 12.dp)
    else RoundedCornerShape(4.dp, 12.dp, 12.dp, 12.dp)

    Column(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalAlignment = align) {
        if (!isSelf) {
            Text(msg.sender, fontSize = 11.sp, color = WxGray, modifier = Modifier.padding(start = 8.dp, bottom = 2.dp))
        }
        Surface(shape = shape, color = bg, modifier = Modifier.widthIn(max = 280.dp)) {
            Text(msg.content, Modifier.padding(10.dp), color = Color.Black, fontSize = 15.sp)
        }
    }
}
