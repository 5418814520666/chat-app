import { useState, useEffect } from 'react'
import ChatRoom from './components/ChatRoom'
import './App.css'

function getStoredAuth() {
  try {
    const data = localStorage.getItem('chat_auth')
    return data ? JSON.parse(data) : null
  } catch { return null }
}

export default function App() {
  const [auth, setAuth] = useState(getStoredAuth)
  const [mode, setMode] = useState('login')
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')
  const [roomId, setRoomId] = useState('general')

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
    } catch (err) {
      setError(err.message)
    }
  }

  const handleLogout = () => {
    localStorage.removeItem('chat_auth')
    setAuth(null)
    setPassword('')
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
              <input
                type="text"
                value={username}
                onChange={(e) => setUsername(e.target.value)}
                placeholder="2-20 个字符"
                maxLength={20}
                autoFocus
              />
            </div>
            <div className="form-group">
              <label>密码</label>
              <input
                type="password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                placeholder="至少 4 个字符"
              />
            </div>
            {error && <p style={{ color: '#e94560', fontSize: 13, marginBottom: 12 }}>{error}</p>}
            <button type="submit" className="join-btn" disabled={!username.trim() || !password.trim()}>
              {mode === 'login' ? '登录' : '注册'}
            </button>
          </form>
          <p style={{ textAlign: 'center', marginTop: 16, fontSize: 13, color: '#aaa' }}>
            {mode === 'login' ? '没有账号？' : '已有账号？'}
            <button
              onClick={() => { setMode(mode === 'login' ? 'register' : 'login'); setError('') }}
              style={{ background: 'none', border: 'none', color: '#e94560', cursor: 'pointer', fontSize: 13 }}
            >
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
      <div style={{ display: 'flex', alignItems: 'center', gap: 8, padding: '8px 16px', background: '#16213e' }}>
        <label style={{ fontSize: 13, color: '#aaa' }}>房间:</label>
        <input
          type="text"
          value={roomId}
          onChange={(e) => setRoomId(e.target.value)}
          style={{
            flex: 1, padding: '6px 10px', border: '1px solid #2a2a4a', borderRadius: 6,
            background: '#1a1a2e', color: '#eee', fontSize: 13, outline: 'none'
          }}
          placeholder="输入房间名后回车"
          onKeyDown={(e) => e.key === 'Enter' && e.target.blur()}
        />
      </div>
      <ChatRoom roomId={roomId} username={auth.user.username} token={auth.token} />
    </div>
  )
}
