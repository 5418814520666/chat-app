package com.chatapp.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chatapp.ui.theme.WxDarkBg
import com.chatapp.ui.theme.WxGreen

@Composable
fun DiscoverScreen() {
    Column(Modifier.fillMaxSize().background(WxDarkBg)) {
        Text("发现", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.White,
            modifier = Modifier.padding(16.dp))
        Spacer(Modifier.height(16.dp))
        DiscoverItem(Icons.Filled.Explore, "朋友圈")
        DiscoverItem(Icons.Filled.Videocam, "视频号")
        Divider(color = Color(0xFF333333), modifier = Modifier.padding(horizontal = 16.dp))
        DiscoverItem(Icons.Filled.NearMe, "附近")
    }
}

@Composable
private fun DiscoverItem(icon: ImageVector, label: String) {
    Row(
        Modifier.fillMaxWidth().clickable {}.padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = WxGreen, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(16.dp))
        Text(label, color = Color.White, fontSize = 16.sp)
    }
}

@Composable
fun ProfileScreen(username: String, logout: () -> Unit) {
    Column(Modifier.fillMaxSize().background(WxDarkBg)) {
        Row(Modifier.fillMaxWidth().padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(64.dp).clip(CircleShape).background(WxGreen), contentAlignment = Alignment.Center) {
                Text(username.first().uppercase(), color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(16.dp))
            Text(username, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        Divider(color = Color(0xFF333333), modifier = Modifier.padding(horizontal = 16.dp))
        Spacer(Modifier.height(16.dp))
        ProfileItem(Icons.Filled.Settings, "设置")
        Divider(color = Color(0xFF333333), modifier = Modifier.padding(horizontal = 16.dp))
        Spacer(Modifier.height(40.dp))
        Button(
            onClick = logout, modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFCC3333))
        ) { Text("退出登录") }
    }
}

@Composable
private fun ProfileItem(icon: ImageVector, label: String) {
    Row(
        Modifier.fillMaxWidth().clickable {}.padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = WxGreen, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(16.dp))
        Text(label, color = Color.White, fontSize = 16.sp)
    }
}
