package com.chatapp.data.api

import com.chatapp.BuildConfig
import io.socket.client.IO
import io.socket.client.Socket
import io.socket.emitter.Emitter
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI

class SocketManager {

    private var socket: Socket? = null
    private var currentToken: String? = null
    private var currentUserId: String? = null
    private var listeners = mutableMapOf<String, MutableList<Emitter.Listener>>()

    val isConnected: Boolean get() = socket?.connected() == true

    fun connect(token: String, userId: String, username: String) {
        disconnect()
        currentToken = token
        currentUserId = userId

        val opts = IO.Options().apply {
            query = "token=$token"
            timeout = 15000
            reconnection = true
            reconnectionAttempts = Int.MAX_VALUE
            reconnectionDelay = 1000
            reconnectionDelayMax = 5000
            transports = arrayOf("websocket")
        }

        socket = IO.socket(URI.create(BuildConfig.SOCKET_URL), opts).apply {
            on(Socket.EVENT_CONNECT) {
                emit("register", JSONObject().apply {
                    put("userId", userId)
                    put("username", username)
                })
            }

            on(Socket.EVENT_DISCONNECT) {}
            on(Socket.EVENT_CONNECT_ERROR) {}

            connect()
        }

        // Re-register listeners
        for ((event, eventListeners) in listeners) {
            eventListeners.forEach { socket?.on(event, it) }
        }
    }

    fun disconnect() {
        socket?.disconnect()
        socket?.off()
        socket = null
    }

    fun on(event: String, listener: Emitter.Listener) {
        listeners.getOrPut(event) { mutableListOf() }.add(listener)
        socket?.on(event, listener)
    }

    fun off(event: String) {
        listeners.remove(event)
        socket?.off(event)
    }

    fun emit(event: String, vararg args: Any) {
        socket?.emit(event, *args)
    }

    fun joinRoom(roomId: String) {
        emit("join-room", JSONObject().put("roomId", roomId))
    }

    fun sendMessage(roomId: String, text: String) {
        emit("send-message", JSONObject().apply {
            put("roomId", roomId)
            put("message", text)
        })
    }

    fun sendFileMessage(roomId: String, fileInfo: JSONObject) {
        emit("send-file-message", JSONObject().apply {
            put("roomId", roomId)
            put("fileInfo", fileInfo)
        })
    }

    fun sendTyping(roomId: String, isTyping: Boolean) {
        if (isTyping) {
            emit("typing", JSONObject().put("roomId", roomId))
        } else {
            emit("stop-typing", JSONObject().put("roomId", roomId))
        }
    }

    fun callUser(toUserId: String) {
        emit("call-user", JSONObject().put("toUserId", toUserId))
        emit("call-user", JSONObject().put("toUserId", toUserId))
    }

    fun sendCallSignal(signal: JSONObject) {
        emit("call-signal", signal)
    }
}
