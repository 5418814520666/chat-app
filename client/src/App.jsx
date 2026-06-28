import { useState } from 'react'
import ChatRoom from './components/ChatRoom'
import './App.css'

export default function App() {
  const [joined, setJoined] = useState(false)
  const [roomId, setRoomId] = useState('general')
  const [username, setUsername] = useState('')

  const handleJoin = (e) => {
    e.preventDefault()
    if (!username.trim()) return
    setJoined(true)
  }

  if (!joined) {
    return (
      <div className="join-screen">
        <div className="join-card">
          <h1>Chat App</h1>
          <p className="join-subtitle">视频聊天室</p>
          <form onSubmit={handleJoin}>
            <div className="form-group">
              <label htmlFor="username">用户名</label>
              <input
                id="username"
                type="text"
                value={username}
                onChange={(e) => setUsername(e.target.value)}
                placeholder="输入你的用户名"
                maxLength={20}
                autoFocus
              />
            </div>
            <div className="form-group">
              <label htmlFor="room">聊天室</label>
              <input
                id="room"
                type="text"
                value={roomId}
                onChange={(e) => setRoomId(e.target.value)}
                placeholder="输入聊天室名称"
                maxLength={30}
              />
            </div>
            <button type="submit" className="join-btn" disabled={!username.trim()}>
              加入聊天室
            </button>
          </form>
        </div>
      </div>
    )
  }

  return <ChatRoom roomId={roomId} username={username} onLeave={() => setJoined(false)} />
}
