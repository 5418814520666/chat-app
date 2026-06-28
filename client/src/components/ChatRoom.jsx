import { useState, useEffect, useRef, useCallback } from 'react'
import { io } from 'socket.io-client'
import MessageList from './MessageList'
import ChatInput from './ChatInput'
import UserList from './UserList'
import VideoCall from './VideoCall'

export default function ChatRoom({ roomId, username, token }) {
  const [socket, setSocket] = useState(null)
  const [users, setUsers] = useState([])
  const [messages, setMessages] = useState([])
  const [typingUsers, setTypingUsers] = useState(new Map())
  const [showVideo, setShowVideo] = useState(false)
  const [incomingCall, setIncomingCall] = useState(null)
  const [activeCall, setActiveCall] = useState(null)
  const [isCaller, setIsCaller] = useState(false)
  const [localStream, setLocalStream] = useState(null)
  const [remoteStream, setRemoteStream] = useState(null)
  const [sidebarOpen, setSidebarOpen] = useState(false)
  const [hasMore, setHasMore] = useState(true)
  const peerRef = useRef(null)
  const typingTimers = useRef(new Map())
  const roomRef = useRef(roomId)

  useEffect(() => {
    if (roomRef.current === roomId && socket) {
      return
    }
    roomRef.current = roomId

    setHasMore(true)

    const s = io('/', {
      transports: ['websocket', 'polling'],
      auth: { token }
    })

    s.on('connect', () => {
      s.emit('join-room', { roomId })
    })

    s.on('connect_error', (err) => {
      console.error('Connection error:', err.message)
    })

    s.on('user-list', setUsers)
    s.on('user-joined', (user) => {
      setUsers((prev) => [...prev, user])
    })
    s.on('user-left', (user) => {
      setUsers((prev) => prev.filter((u) => u.id !== user.id))
    })

    s.on('message-history', (msgs) => {
      setMessages(msgs.reverse())
    })

    s.on('new-message', (msg) => {
      setMessages((prev) => [...prev, msg])
    })

    s.on('system-message', (msg) => {
      setMessages((prev) => [...prev, { ...msg, id: Date.now() + Math.random(), type: 'system' }])
    })

    s.on('user-typing', ({ userId, username }) => {
      setTypingUsers((prev) => {
        const next = new Map(prev)
        next.set(userId, username)
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
    })

    s.on('user-stop-typing', ({ userId }) => {
      setTypingUsers((prev) => {
        const next = new Map(prev)
        next.delete(userId)
        return next
      })
    })

    s.on('incoming-call', ({ from, username }) => {
      setIncomingCall({ from, username })
    })

    s.on('call-accepted', ({ from, username }) => {
      setActiveCall({ peerId: from, username })
      setShowVideo(true)
    })

    s.on('call-rejected', () => {
      setIncomingCall(null)
      setActiveCall(null)
      setIsCaller(false)
      cleanupCall()
    })

    s.on('hang-up', () => { cleanupCall() })

    s.on('webrtc-offer', async ({ from, offer, username }) => {
      const pc = createPeerConnection(s, from)
      peerRef.current = pc
      await pc.setRemoteDescription(new RTCSessionDescription(offer))

      const stream = await getLocalStream()
      if (stream) stream.getTracks().forEach((t) => pc.addTrack(t, stream))

      const answer = await pc.createAnswer()
      await pc.setLocalDescription(answer)
      s.emit('webrtc-answer', { to: from, answer })
    })

    s.on('webrtc-answer', async ({ answer }) => {
      if (peerRef.current) await peerRef.current.setRemoteDescription(new RTCSessionDescription(answer))
    })

    s.on('webrtc-ice-candidate', async ({ candidate }) => {
      if (peerRef.current) {
        try { await peerRef.current.addIceCandidate(new RTCIceCandidate(candidate)) } catch {}
      }
    })

    setSocket(s)

    return () => {
      cleanupCall()
      s.disconnect()
    }
  }, [roomId, token])

  const getLocalStream = useCallback(async () => {
    try {
      const stream = await navigator.mediaDevices.getUserMedia({ video: true, audio: true })
      setLocalStream(stream)
      return stream
    } catch { return null }
  }, [])

  const createPeerConnection = useCallback((s, targetId) => {
    const pc = new RTCPeerConnection({
      iceServers: [
        { urls: 'stun:stun.l.google.com:19302' },
        { urls: 'stun:stun1.l.google.com:19302' }
      ]
    })
    pc.onicecandidate = (e) => {
      if (e.candidate) s.emit('webrtc-ice-candidate', { to: targetId, candidate: e.candidate })
    }
    pc.addEventListener('track', (e) => {
      if (e.streams && e.streams[0] && e.streams[0].getTracks().length > 0) {
        setRemoteStream(e.streams[0])
      }
    })
    pc.onconnectionstatechange = () => {
      console.log('WebRTC state:', pc.connectionState)
    }
    return pc
  }, [])

  const startCall = useCallback(async (targetUser) => {
    setIsCaller(true)
    const targetId = targetUser.sid || targetUser.id
    setActiveCall({ peerId: targetId, username: targetUser.username })
    setShowVideo(true)

    const pc = createPeerConnection(socket, targetId)
    peerRef.current = pc

    const stream = await getLocalStream()
    if (stream) {
      stream.getTracks().forEach((t) => pc.addTrack(t, stream))
    }

    const offer = await pc.createOffer()
    await pc.setLocalDescription(offer)
    socket.emit('webrtc-offer', { to: targetId, offer })
  }, [socket, getLocalStream, createPeerConnection])

  const acceptCall = useCallback(async () => {
    if (!incomingCall) return
    socket.emit('call-accepted', { to: incomingCall.from })
    setActiveCall({ peerId: incomingCall.from, username: incomingCall.username })
    setShowVideo(true)

    const stream = await getLocalStream()
    if (!stream) { setIncomingCall(null); return }

    const pc = createPeerConnection(socket, incomingCall.from)
    peerRef.current = pc
    stream.getTracks().forEach((t) => pc.addTrack(t, stream))

    const offer = await pc.createOffer()
    await pc.setLocalDescription(offer)

    setIncomingCall(null)
    socket.emit('webrtc-offer', { to: incomingCall.from, offer })
  }, [incomingCall, socket, getLocalStream, createPeerConnection])

  const rejectCall = useCallback(() => {
    if (!incomingCall) return
    socket.emit('call-rejected', { to: incomingCall.from })
    setIncomingCall(null)
  }, [incomingCall, socket])

  const hangUp = useCallback(() => {
    if (activeCall) socket.emit('hang-up', { to: activeCall.peerId })
    cleanupCall()
  }, [activeCall, socket])

  const cleanupCall = useCallback(() => {
    if (peerRef.current) { peerRef.current.close(); peerRef.current = null }
    if (localStream) { localStream.getTracks().forEach((t) => t.stop()); setLocalStream(null) }
    setRemoteStream(null)
    setActiveCall(null)
    setShowVideo(false)
    setIsCaller(false)
    setIncomingCall(null)
  }, [localStream])

  const toggleMute = useCallback((kind) => {
    localStream?.getTracks().forEach((t) => { if (t.kind === kind) t.enabled = !t.enabled })
  }, [localStream])

  const sendMessage = useCallback((text) => {
    if (!socket || !text.trim()) return
    socket.emit('send-message', { roomId, message: text.trim() })
  }, [socket, roomId])

  const sendFileMessage = useCallback((fileInfo) => {
    if (!socket) return
    socket.emit('send-file-message', { roomId, fileInfo })
  }, [socket, roomId])

  const emitTyping = useCallback((isTyping) => {
    if (!socket) return
    socket.emit(isTyping ? 'typing' : 'stop-typing', { roomId })
  }, [socket, roomId])

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
  }, [roomId, token, hasMore, messages])

  const otherUsers = users.filter((u) => u.id !== socket?.data?.userId)

  return (
    <div className="chat-container" style={{ height: 'calc(100vh - 76px)' }}>
      <div className={`sidebar-overlay ${sidebarOpen ? 'open' : ''}`} onClick={() => setSidebarOpen(false)} />
      <div className={`sidebar ${sidebarOpen ? 'open' : ''}`}>
        <div className="sidebar-header">
          <h2>Chat App</h2>
          <span className="room-name">#{roomId}</span>
        </div>
        <UserList users={otherUsers} currentUsername={username} onCall={(user) => { startCall(user); setSidebarOpen(false) }} activeCall={activeCall} />
      </div>
      <div className="main-area">
        <div className="chat-header">
          <button className="mobile-menu-btn" onClick={() => setSidebarOpen(true)}>&#9776;</button>
          <h3>#{roomId}</h3>
        </div>
        <MessageList messages={messages} currentUserId={socket?.data?.userId?.toString()} onLoadMore={loadMore} hasMore={hasMore} />
        <div className="typing-indicator">
          {typingUsers.size > 0 && Array.from(typingUsers.values()).join(', ') + ' 正在输入...'}
        </div>
        <ChatInput onSend={sendMessage} onFileSend={sendFileMessage} onTyping={emitTyping} username={username} roomId={roomId} token={token} />
      </div>
      {(showVideo || activeCall) && (
        <VideoCall localStream={localStream} remoteStream={remoteStream} isCaller={isCaller}
          peerUsername={activeCall?.username} onHangUp={hangUp} onClose={() => setShowVideo(false)} onToggleMute={toggleMute} />
      )}
      {incomingCall && (
        <div className="incoming-call-overlay">
          <div className="incoming-call-card">
            <h2>视频通话</h2>
            <p>{incomingCall.username} 邀请你视频通话</p>
            <div className="incoming-buttons">
              <button className="accept-btn" onClick={acceptCall}>接受</button>
              <button className="reject-btn" onClick={rejectCall}>拒绝</button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
