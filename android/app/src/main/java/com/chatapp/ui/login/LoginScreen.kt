package com.chatapp.ui.login

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chatapp.ui.theme.WxDarkBg
import com.chatapp.ui.theme.WxGreen

@Composable
fun LoginScreen(
    onLogin: (String, String) -> Unit,
    onToRegister: () -> Unit,
    error: String?,
    loading: Boolean
) {
    var u by remember { mutableStateOf("") }
    var p by remember { mutableStateOf("") }
    var showPwd by remember { mutableStateOf(false) }
    val fm = LocalFocusManager.current

    Column(
        Modifier.fillMaxSize().background(WxDarkBg).padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Chat App", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = WxGreen)
        Spacer(Modifier.height(4.dp))
        Text("登录你的账号", color = Color.Gray, fontSize = 14.sp)
        Spacer(Modifier.height(36.dp))

        OutlinedTextField(
            u, { u = it }, label = { Text("用户名") }, singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = { fm.moveFocus(FocusDirection.Down) }),
            colors = fieldColors()
        )
        Spacer(Modifier.height(14.dp))

        OutlinedTextField(
            p, { p = it }, label = { Text("密码") }, singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            visualTransformation = if (showPwd) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { showPwd = !showPwd }) {
                    Icon(if (showPwd) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, null, tint = Color.Gray)
                }
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = {
                fm.clearFocus()
                if (u.isNotBlank() && p.isNotBlank()) onLogin(u.trim(), p)
            }),
            colors = fieldColors()
        )
        Spacer(Modifier.height(8.dp))

        if (error != null) Text(error, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
        Spacer(Modifier.height(20.dp))

        Button(
            onClick = { onLogin(u.trim(), p) },
            modifier = Modifier.fillMaxWidth().height(48.dp),
            enabled = u.isNotBlank() && p.isNotBlank() && !loading,
            colors = ButtonDefaults.buttonColors(containerColor = WxGreen)
        ) {
            if (loading) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
            else Text("登录", fontSize = 16.sp)
        }
        Spacer(Modifier.height(14.dp))

        TextButton(onClick = onToRegister) { Text("没有账号？注册", color = WxGreen) }
    }
}

@Composable
fun RegisterScreen(
    onRegister: (String, String) -> Unit,
    onToLogin: () -> Unit,
    error: String?,
    loading: Boolean
) {
    var u by remember { mutableStateOf("") }
    var p by remember { mutableStateOf("") }
    var showPwd by remember { mutableStateOf(false) }
    val fm = LocalFocusManager.current

    Column(
        Modifier.fillMaxSize().background(WxDarkBg).padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Chat App", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = WxGreen)
        Spacer(Modifier.height(4.dp))
        Text("创建新账号", color = Color.Gray, fontSize = 14.sp)
        Spacer(Modifier.height(36.dp))

        OutlinedTextField(
            u, { u = it }, label = { Text("用户名") }, singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = { fm.moveFocus(FocusDirection.Down) }),
            colors = fieldColors()
        )
        Spacer(Modifier.height(14.dp))

        OutlinedTextField(
            p, { p = it }, label = { Text("密码") }, singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            visualTransformation = if (showPwd) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { showPwd = !showPwd }) {
                    Icon(if (showPwd) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, null, tint = Color.Gray)
                }
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = {
                fm.clearFocus()
                if (u.isNotBlank() && p.isNotBlank()) onRegister(u.trim(), p)
            }),
            colors = fieldColors()
        )
        Spacer(Modifier.height(8.dp))

        if (error != null) Text(error, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
        Spacer(Modifier.height(20.dp))

        Button(
            onClick = { onRegister(u.trim(), p) },
            modifier = Modifier.fillMaxWidth().height(48.dp),
            enabled = u.isNotBlank() && p.isNotBlank() && !loading,
            colors = ButtonDefaults.buttonColors(containerColor = WxGreen)
        ) {
            if (loading) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
            else Text("注册", fontSize = 16.sp)
        }
        Spacer(Modifier.height(14.dp))

        TextButton(onClick = onToLogin) { Text("已有账号？登录", color = WxGreen) }
    }
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = WxGreen,
    unfocusedBorderColor = Color(0xFF555555),
    focusedTextColor = Color.White,
    unfocusedTextColor = Color.White,
    focusedLabelColor = WxGreen,
    unfocusedLabelColor = Color.Gray,
    cursorColor = WxGreen
)
