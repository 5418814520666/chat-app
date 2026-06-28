import { useRef, useEffect } from 'react'

export default function MessageList({ messages, currentUserId }) {
  const bottomRef = useRef(null)

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [messages])

  function formatTime(ts) {
    const d = new Date(ts)
    return d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
  }

  function formatSize(bytes) {
    if (bytes < 1024) return bytes + ' B'
    if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
    return (bytes / (1024 * 1024)).toFixed(1) + ' MB'
  }

  function getFileIcon(mimeType) {
    if (!mimeType) return 'F'
    if (mimeType.startsWith('image/')) return 'I'
    if (mimeType.startsWith('video/')) return 'V'
    if (mimeType.startsWith('audio/')) return 'A'
    if (mimeType.includes('pdf')) return 'P'
    if (mimeType.includes('zip') || mimeType.includes('rar') || mimeType.includes('tar')) return 'Z'
    return 'F'
  }

  return (
    <div className="message-list">
      {messages.map((msg) => {
        if (msg.type === 'system') {
          return (
            <div key={msg.id} className="system-msg">
              {msg.content}
            </div>
          )
        }

        const isOwn = msg.senderId === currentUserId
        const initial = (msg.sender || '?')[0].toUpperCase()

        return (
          <div key={msg.id} className={`message-row ${isOwn ? 'own' : ''}`}>
            <div className="msg-avatar">{initial}</div>
            <div className="msg-bubble">
              {!isOwn && <div className="msg-sender">{msg.sender}</div>}
              {msg.type === 'text' && (
                <div className="msg-content">{msg.content}</div>
              )}
              {msg.type === 'file' && msg.file && (
                <div className="file-msg">
                  <span className="file-icon">{getFileIcon(msg.file.type)}</span>
                  <div className="file-info">
                    <div className="file-name" title={msg.file.name}>
                      {msg.file.name}
                    </div>
                    <div className="file-size">{formatSize(msg.file.size)}</div>
                  </div>
                  <a href={msg.file.url} download={msg.file.name} className="file-download">
                    下载
                  </a>
                </div>
              )}
              <div className="msg-time">{formatTime(msg.timestamp)}</div>
            </div>
          </div>
        )
      })}
      <div ref={bottomRef} />
    </div>
  )
}
