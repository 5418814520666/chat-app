import { useState, useEffect, useRef, useCallback } from 'react'
import MessageList from './MessageList'
import ChatInput from './ChatInput'
import UserList from './UserList'
import VideoCall from './VideoCall'

function sendNotification(title, body) {
  if (!('Notification' in window)) return
  if (Notification.permission === 'granted') {
    new Notification(title, { body, icon: '/icon-192.png' })
  }
}

function requestNotificationPermission() {
  if ('Notification' in window && Notification.permission === 'default') {
    Notification.requestPermission()
  }
}

export default function ChatRoom({ roomId, username, token, socketRef, localStream, remoteStream, isCaller, activeCall, showVideo, mutedRooms, onToggleRoomMute, privateFriend, onStartCall, onCallFromInput, onHangUp, onToggleMute, onCloseVideo }) {
  const [users, setUsers] = useState([])
  const [messages, setMessages] = useState([])
  const [typingUsers, setTypingUsers] = useState(new Map())
  const [hasMore, setHasMore] = useState(true)
  const typingTimers = useRef(new Map())
  const isMuted = mutedRooms.has(roomId)

  useEffect(() => {
    requestNotificationPermission()
  }, [])

  useEffect(() => {
    const s = socketRef.current
    if (!s) return

    setMessages([])
    setHasMore(true)

    s.emit('join-room', { roomId })

    const onUserList = (list) => setUsers(list)
    const onUserJoined = (user) => setUsers((prev) => [...prev, user])
    const onUserLeft = (user) => setUsers((prev) => prev.filter((u) => u.id !== user.id))
    const onMsgHistory = (msgs) => setMessages(msgs.reverse())
    const onNewMsg = (msg) => {
      setMessages((prev) => [...prev, msg])
      if (msg.senderId !== s.data?.userId?.toString() && !mutedRooms.has(roomId) && document.visibilityState !== 'visible') {
        const sender = msg.sender || '新消息'
        const content = msg.type === 'text' ? msg.content : (msg.type === 'file' ? `[文件] ${msg.file?.name || ''}` : '新消息')
        sendNotification(sender, content)
      }
    }
    const onSystemMsg = (msg) => {
      setMessages((prev) => [...prev, { ...msg, id: Date.now() + Math.random(), type: 'system' }])
      if (!mutedRooms.has(roomId) && document.visibilityState !== 'visible') {
        sendNotification('Chat App', msg.content)
      }
    }
    const onTyping = ({ userId, username: uname }) => {
      setTypingUsers((prev) => {
        const next = new Map(prev)
        next.set(userId, uname)
        return next
      })
      if (typingTimers.current.has(userId)) clearTimeout(typingTimers.current.get(userId))
      typingTimers.current.set(userId, setTimeout(() => {
        setTypingUsers((prev) => {
          const next = new Map(prev)
          next.delete(userId)
          return next
        })
      }, 3000))
    }
    const onStopTyping = ({ userId }) => {
      setTypingUsers((prev) => {
        const next = new Map(prev)
        next.delete(userId)
        return next
      })
    }

    s.on('user-list', onUserList)
    s.on('user-joined', onUserJoined)
    s.on('user-left', onUserLeft)
    s.on('message-history', onMsgHistory)
    s.on('new-message', onNewMsg)
    s.on('system-message', onSystemMsg)
    s.on('user-typing', onTyping)
    s.on('user-stop-typing', onStopTyping)

    return () => {
      s.off('user-list', onUserList)
      s.off('user-joined', onUserJoined)
      s.off('user-left', onUserLeft)
      s.off('message-history', onMsgHistory)
      s.off('new-message', onNewMsg)
      s.off('system-message', onSystemMsg)
      s.off('user-typing', onTyping)
      s.off('user-stop-typing', onStopTyping)
    }
  }, [roomId, socketRef, mutedRooms])

  const sendMessage = useCallback((text) => {
    const s = socketRef.current
    if (!s || !text.trim()) return
    s.emit('send-message', { roomId, message: text.trim() })
  }, [roomId, socketRef])

  const sendFileMessage = useCallback((fileInfo) => {
    const s = socketRef.current
    if (!s) return
    s.emit('send-file-message', { roomId, fileInfo })
  }, [roomId, socketRef])

  const emitTyping = useCallback((isTyping) => {
    const s = socketRef.current
    if (!s) return
    s.emit(isTyping ? 'typing' : 'stop-typing', { roomId })
  }, [roomId, socketRef])

  const loadMore = useCallback(async () => {
    if (!hasMore || messages.length === 0) return
    const oldest = messages[0]
    const res = await fetch(`/api/messages/${roomId}?before=${oldest.timestamp}&limit=50`, {
      headers: { Authorization: `Bearer ${token}` }
    })
    if (res.ok) {
      const older = await res.json()
      if (older.length === 0) { setHasMore(false); return }
      setMessages((prev) => [...older, ...prev])
    }
  }, [roomId, token, hasMore, messages, socketRef])

  const otherUsers = users.filter((u) => u.id !== socketRef.current?.data?.userId)

  const handleCall = useCallback((user) => {
    const s = socketRef.current
    if (!s) return
    s.emit('call-user', { toUserId: user.id })
    onStartCall(user)
  }, [socketRef, onStartCall])

  const isPrivate = roomId.startsWith('private_')
  const muteLabel = isMuted ? '已免打扰' : '消息通知已开启'

  return (
    <div style={{ display: 'flex', flex: 1, minHeight: 0 }}>
      <div className="sidebar desktop-only">
        <div className="sidebar-header">
          <h2>Chat App</h2>
          <span className="room-name">{isPrivate ? '@私聊' : '#' + roomId}</span>
        </div>
        <UserList users={otherUsers} currentUsername={username} onCall={handleCall} activeCall={activeCall} />
      </div>
      <div className="main-area">
        <div className="chat-header">
          <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <h3>{isPrivate ? '@私聊' : '#' + roomId}</h3>
            <button
              className={`mute-toggle-btn ${isMuted ? 'muted' : ''}`}
              onClick={() => onToggleRoomMute(roomId)}
              title={muteLabel}
            >
              {isMuted ? 'M' : 'N'}
            </button>
          </div>
        </div>
        <MessageList messages={messages} currentUserId={socketRef.current?.data?.userId?.toString()} onLoadMore={loadMore} hasMore={hasMore} />
        <div className="typing-indicator">
          {typingUsers.size > 0 && Array.from(typingUsers.values()).join(', ') + ' 正在输入...'}
        </div>
        <ChatInput
          onSend={sendMessage} onFileSend={sendFileMessage} onTyping={emitTyping}
          username={username} roomId={roomId} token={token}
          isPrivate={isPrivate} privateFriend={privateFriend}
          users={otherUsers} onCall={onCallFromInput} />
      </div>
      {(showVideo || activeCall) && (
        <VideoCall localStream={localStream} remoteStream={remoteStream} isCaller={isCaller}
          peerUsername={activeCall?.username} onHangUp={onHangUp} onClose={onCloseVideo} onToggleMute={onToggleMute} />
      )}
    </div>
  )
}
