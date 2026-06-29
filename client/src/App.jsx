import { useState, useEffect, useRef, useCallback } from 'react'
import { io } from 'socket.io-client'
import ChatRoom from './components/ChatRoom'
import VideoCall from './components/VideoCall'
import './App.css'

const S = 'http://localhost:3001'

function getStoredAuth() {
  try {
    const data = localStorage.getItem('chat_auth')
    return data ? JSON.parse(data) : null
  } catch { return null }
}

function getStoredMutes() {
  try {
    const data = localStorage.getItem('chat_mutes')
    return data ? new Set(JSON.parse(data)) : new Set()
  } catch { return new Set() }
}

function saveMutes(mutes) {
  localStorage.setItem('chat_mutes', JSON.stringify([...mutes]))
}

export default function App() {
  const [auth, setAuth] = useState(getStoredAuth)
  const [mode, setMode] = useState('login')
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')
  const [roomId, setRoomId] = useState('general')
  const [activeRoom, setActiveRoom] = useState('general')
  const [friends, setFriends] = useState([])
  const [friendRequests, setFriendRequests] = useState({ incoming: [], outgoing: [] })
  const [searchResults, setSearchResults] = useState([])
  const [searchQuery, setSearchQuery] = useState('')
  const [sidebarTab, setSidebarTab] = useState('room')
  const [sidebarOpen, setSidebarOpen] = useState(false)

  const [mutedRooms, setMutedRooms] = useState(getStoredMutes)
  const [privateFriend, setPrivateFriend] = useState(null)
  const privateRoomRef = useRef(null)

  const [incomingCall, setIncomingCall] = useState(null)
  const [activeCall, setActiveCall] = useState(null)
  const [showVideo, setShowVideo] = useState(false)
  const [isCaller, setIsCaller] = useState(false)
  const [localStream, setLocalStream] = useState(null)
  const [remoteStream, setRemoteStream] = useState(null)
  const peerRef = useRef(null)
  const socketRef = useRef(null)

  const token = auth?.token

  const cleanupCall = useCallback(() => {
    if (peerRef.current) { peerRef.current.close(); peerRef.current = null }
    if (localStream) { localStream.getTracks().forEach((t) => t.stop()); setLocalStream(null) }
    setRemoteStream(null)
    setActiveCall(null)
    setShowVideo(false)
    setIsCaller(false)
    setIncomingCall(null)
  }, [localStream])

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
    const s = socketRef.current
    if (!s) return
    setIsCaller(true)
    const targetId = targetUser.sid
    setActiveCall({ peerId: targetId, username: targetUser.username })
    setShowVideo(true)

    const pc = createPeerConnection(s, targetId)
    peerRef.current = pc

    const stream = await getLocalStream()
    if (stream) {
      stream.getTracks().forEach((t) => pc.addTrack(t, stream))
    }

    const offer = await pc.createOffer()
    await pc.setLocalDescription(offer)
    s.emit('webrtc-offer', { to: targetId, offer })
  }, [createPeerConnection, getLocalStream])

  const acceptCall = useCallback(async () => {
    const s = socketRef.current
    if (!incomingCall) return
    s.emit('call-accepted', { to: incomingCall.from })
    setActiveCall({ peerId: incomingCall.from, username: incomingCall.username })
    setShowVideo(true)

    const stream = await getLocalStream()
    if (!stream) { setIncomingCall(null); return }

    const pc = createPeerConnection(s, incomingCall.from)
    peerRef.current = pc
    stream.getTracks().forEach((t) => pc.addTrack(t, stream))

    const offer = await pc.createOffer()
    await pc.setLocalDescription(offer)

    setIncomingCall(null)
    s.emit('webrtc-offer', { to: incomingCall.from, offer })
  }, [incomingCall, createPeerConnection, getLocalStream])

  const rejectCall = useCallback(() => {
    if (!incomingCall) return
    socketRef.current?.emit('call-rejected', { to: incomingCall.from })
    setIncomingCall(null)
  }, [incomingCall])

  const hangUp = useCallback(() => {
    if (activeCall) socketRef.current?.emit('hang-up', { to: activeCall.peerId })
    cleanupCall()
  }, [activeCall, cleanupCall])

  const toggleMute = useCallback((kind) => {
    localStream?.getTracks().forEach((t) => { if (t.kind === kind) t.enabled = !t.enabled })
  }, [localStream])

  useEffect(() => {
    if (!token) return
    const s = io('/', {
      transports: ['websocket', 'polling'],
      auth: { token }
    })
    socketRef.current = s

    s.on('connect', () => {
      loadFriends()
      loadFriendRequests()
    })

    s.on('incoming-call', ({ from, username }) => {
      setIncomingCall({ from, username })
    })

    s.on('call-accepted', ({ from, username }) => {
      setActiveCall({ peerId: from, username })
      setShowVideo(true)
    })

    s.on('call-rejected', () => { cleanupCall() })
    s.on('hang-up', () => { cleanupCall() })

    s.on('webrtc-answer', async ({ answer }) => {
      if (peerRef.current) await peerRef.current.setRemoteDescription(new RTCSessionDescription(answer))
    })

    s.on('webrtc-offer', async ({ from, offer }) => {
      const pc = createPeerConnection(s, from)
      peerRef.current = pc
      await pc.setRemoteDescription(new RTCSessionDescription(offer))
      const stream = await getLocalStream()
      if (stream) stream.getTracks().forEach((t) => pc.addTrack(t, stream))
      const answer = await pc.createAnswer()
      await pc.setLocalDescription(answer)
      s.emit('webrtc-answer', { to: from, answer })
    })

    s.on('webrtc-ice-candidate', async ({ candidate }) => {
      if (peerRef.current) {
        try { await peerRef.current.addIceCandidate(new RTCIceCandidate(candidate)) } catch {}
      }
    })

    s.on('friend-request-received', () => { loadFriendRequests() })
    s.on('friend-request-accepted', () => { loadFriends(); loadFriendRequests() })

    return () => { cleanupCall(); s.disconnect() }
  }, [token])

  const handleAuth = async (e) => {
    e.preventDefault()
    setError('')
    const endpoint = mode === 'login' ? '/api/auth/login' : '/api/auth/register'
    try {
      const res = await fetch(endpoint, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ username, password })
      })
      const data = await res.json()
      if (!res.ok) throw new Error(data.error)
      const authData = { token: data.token, user: data.user }
      localStorage.setItem('chat_auth', JSON.stringify(authData))
      setAuth(authData)
    } catch (err) { setError(err.message) }
  }

  const handleLogout = () => {
    localStorage.removeItem('chat_auth')
    setAuth(null)
    setPassword('')
  }

  const loadFriends = async () => {
    try {
      const res = await fetch('/api/friends', { headers: { Authorization: `Bearer ${token}` } })
      if (res.ok) setFriends(await res.json())
    } catch {}
  }

  const loadFriendRequests = async () => {
    try {
      const res = await fetch('/api/friend-requests', { headers: { Authorization: `Bearer ${token}` } })
      if (res.ok) setFriendRequests(await res.json())
    } catch {}
  }

  const searchUsers = async (q) => {
    setSearchQuery(q)
    if (q.length < 2) { setSearchResults([]); return }
    try {
      const res = await fetch(`/api/users/search?q=${encodeURIComponent(q)}`, {
        headers: { Authorization: `Bearer ${token}` }
      })
      if (res.ok) setSearchResults(await res.json())
    } catch {}
  }

  const sendFriendRequest = async (toUserId) => {
    try {
      const res = await fetch('/api/friend-request', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
        body: JSON.stringify({ toUserId })
      })
      if (res.ok) { loadFriendRequests(); setSearchQuery(''); setSearchResults([]) }
      else {
        const d = await res.json()
        alert(d.error)
      }
    } catch {}
  }

  const acceptFriendRequest = async (requestId, fromUserId) => {
    try {
      const res = await fetch('/api/friend-accept', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
        body: JSON.stringify({ requestId, fromUserId })
      })
      if (res.ok) { loadFriends(); loadFriendRequests() }
    } catch {}
  }

  const rejectFriendRequest = async (requestId) => {
    try {
      await fetch('/api/friend-reject', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
        body: JSON.stringify({ requestId })
      })
      loadFriendRequests()
    } catch {}
  }

  const toggleRoomMute = useCallback((roomId) => {
    setMutedRooms((prev) => {
      const next = new Set(prev)
      if (next.has(roomId)) next.delete(roomId)
      else next.add(roomId)
      saveMutes(next)
      return next
    })
  }, [])

  const callFromInput = useCallback((targetUser) => {
    const s = socketRef.current
    if (!s) return
    s.emit('call-user', { toUserId: targetUser.id })
    startCall(targetUser)
  }, [startCall])

  const enterPrivateChat = (friend) => {
    const a = Math.min(auth.user.id, friend.id)
    const b = Math.max(auth.user.id, friend.id)
    const room = `private_${a}_${b}`
    privateRoomRef.current = room
    setPrivateFriend(friend)
    setActiveRoom(room)
    setSidebarOpen(false)
  }

  if (!auth) {
    return (
      <div className="join-screen">
        <div className="join-card">
          <h1>Chat App</h1>
          <p className="join-subtitle">{mode === 'login' ? '登录' : '注册'}账号</p>
          <form onSubmit={handleAuth}>
            <div className="form-group">
              <label>用户名</label>
              <input type="text" value={username} onChange={(e) => setUsername(e.target.value)}
                placeholder="2-20 个字符" maxLength={20} autoFocus />
            </div>
            <div className="form-group">
              <label>密码</label>
              <input type="password" value={password} onChange={(e) => setPassword(e.target.value)}
                placeholder="至少 4 个字符" />
            </div>
            {error && <p style={{ color: '#e94560', fontSize: 13, marginBottom: 12 }}>{error}</p>}
            <button type="submit" className="join-btn" disabled={!username.trim() || !password.trim()}>
              {mode === 'login' ? '登录' : '注册'}
            </button>
          </form>
          <p style={{ textAlign: 'center', marginTop: 16, fontSize: 13, color: '#aaa' }}>
            {mode === 'login' ? '没有账号？' : '已有账号？'}
            <button onClick={() => { setMode(mode === 'login' ? 'register' : 'login'); setError('') }}
              style={{ background: 'none', border: 'none', color: '#e94560', cursor: 'pointer', fontSize: 13 }}>
              {mode === 'login' ? '注册' : '登录'}
            </button>
          </p>
        </div>
      </div>
    )
  }

  return (
    <div>
      <div style={{ background: '#16213e', padding: '6px 16px', display: 'flex', alignItems: 'center', justifyContent: 'space-between', borderBottom: '1px solid #2a2a4a' }}>
        <span style={{ fontSize: 13, color: '#aaa' }}>{auth.user.username}</span>
        <button className="leave-btn" onClick={handleLogout}>退出</button>
      </div>
      <div className="chat-container" style={{ height: 'calc(100vh - 38px)' }}>
        <div className={`sidebar-overlay ${sidebarOpen ? 'open' : ''}`} onClick={() => setSidebarOpen(false)} />
        <div className={`sidebar ${sidebarOpen ? 'open' : ''}`}>
          <div className="sidebar-header">
            <h2>Chat App</h2>
          </div>
          <div className="sidebar-tabs">
            <button className={`sidebar-tab ${sidebarTab === 'room' ? 'active' : ''}`} onClick={() => setSidebarTab('room')}>房间</button>
            <button className={`sidebar-tab ${sidebarTab === 'friends' ? 'active' : ''}`} onClick={() => setSidebarTab('friends')}>
              好友{friendRequests.incoming.length > 0 ? ` (${friendRequests.incoming.length})` : ''}
            </button>
          </div>

          {sidebarTab === 'room' && (
            <div className="sidebar-section">
              <div style={{ padding: '0 12px 8px' }}>
                <label style={{ fontSize: 12, color: '#aaa' }}>公开房间</label>
                <input type="text" value={roomId} onChange={(e) => setRoomId(e.target.value)}
                  onKeyDown={(e) => { if (e.key === 'Enter') { setActiveRoom(roomId); setSidebarOpen(false) } }}
                  style={{ width: '100%', padding: '6px 10px', marginTop: 4, border: '1px solid #2a2a4a', borderRadius: 6, background: '#1a1a2e', color: '#eee', fontSize: 13, outline: 'none' }}
                  placeholder="输入房间名后回车" />
              </div>
              <div className="room-list">
                <div className={`room-item ${activeRoom === 'general' ? 'active' : ''}`} onClick={() => { setActiveRoom('general'); setRoomId('general'); setSidebarOpen(false) }}>
                  <span className="room-hash">#</span> general
                </div>
                {['random', 'tech', 'games'].map((r) => (
                  <div key={r} className={`room-item ${activeRoom === r ? 'active' : ''}`} onClick={() => { setActiveRoom(r); setRoomId(r); setSidebarOpen(false) }}>
                    <span className="room-hash">#</span> {r}
                  </div>
                ))}
              </div>
            </div>
          )}

          {sidebarTab === 'friends' && (
            <div className="sidebar-section">
              <div style={{ padding: '0 12px 8px' }}>
                <label style={{ fontSize: 12, color: '#aaa' }}>添加好友</label>
                <input type="text" value={searchQuery} onChange={(e) => searchUsers(e.target.value)}
                  style={{ width: '100%', padding: '6px 10px', marginTop: 4, border: '1px solid #2a2a4a', borderRadius: 6, background: '#1a1a2e', color: '#eee', fontSize: 13, outline: 'none' }}
                  placeholder="搜索用户名..." />
                {searchResults.length > 0 && (
                  <div className="search-results">
                    {searchResults.map((u) => (
                      <div key={u.id} className="search-result-item">
                        <span>{u.username}</span>
                        <button className="add-friend-btn" onClick={() => sendFriendRequest(u.id)}>添加</button>
                      </div>
                    ))}
                  </div>
                )}
              </div>

              {friendRequests.incoming.length > 0 && (
                <div style={{ padding: '8px 12px' }}>
                  <div style={{ fontSize: 12, color: '#e94560', marginBottom: 6 }}>收到的请求</div>
                  {friendRequests.incoming.map((r) => (
                    <div key={r.id} className="friend-request-item">
                      <span>{r.from_username}</span>
                      <div style={{ display: 'flex', gap: 4 }}>
                        <button className="accept-friend-btn" onClick={() => acceptFriendRequest(r.id, r.from_user_id)}>V</button>
                        <button className="reject-friend-btn" onClick={() => rejectFriendRequest(r.id)}>X</button>
                      </div>
                    </div>
                  ))}
                </div>
              )}

              <div style={{ padding: '8px 12px' }}>
                <div style={{ fontSize: 12, color: '#aaa', marginBottom: 6 }}>好友列表</div>
                {friends.length === 0 && <div style={{ fontSize: 12, color: '#666' }}>暂无好友</div>}
                {friends.map((f) => (
                  <div key={f.id} className={`room-item ${activeRoom === `private_${Math.min(auth.user.id, f.id)}_${Math.max(auth.user.id, f.id)}` ? 'active' : ''}`}
                    onClick={() => enterPrivateChat(f)}>
                    <span className="user-avatar-sm">{f.username[0].toUpperCase()}</span>
                    {f.username}
                  </div>
                ))}
              </div>
            </div>
          )}
        </div>

        <div className="main-area">
          <div className="chat-header">
            <button className="mobile-menu-btn" onClick={() => setSidebarOpen(true)}>&#9776;</button>
            <h3>{activeRoom.startsWith('private_') ? '@私聊' : '#' + activeRoom}</h3>
          </div>
          <ChatRoom roomId={activeRoom} username={auth.user.username} token={token}
            socketRef={socketRef} localStream={localStream} remoteStream={remoteStream}
            isCaller={isCaller} activeCall={activeCall} showVideo={showVideo}
            mutedRooms={mutedRooms} onToggleRoomMute={toggleRoomMute}
            privateFriend={privateFriend} users={[]}
            onStartCall={startCall} onCallFromInput={callFromInput}
            onHangUp={hangUp} onToggleMute={toggleMute}
            onCloseVideo={() => setShowVideo(false)} />
        </div>
      </div>

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
