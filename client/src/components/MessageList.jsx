import { useRef, useEffect, useState } from 'react'

export default function MessageList({ messages, currentUserId, onLoadMore, hasMore }) {
  const bottomRef = useRef(null)
  const containerRef = useRef(null)
  const [loading, setLoading] = useState(false)

  useEffect(() => {
    if (!containerRef.current) return
    const el = containerRef.current
    const isNearBottom = el.scrollHeight - el.scrollTop - el.clientHeight < 100
    if (isNearBottom) {
      bottomRef.current?.scrollIntoView({ behavior: 'smooth' })
    }
  }, [messages])

  useEffect(() => {
    if (!containerRef.current || !hasMore || !onLoadMore) return
    const el = containerRef.current
    const handleScroll = () => {
      if (el.scrollTop < 50 && !loading && hasMore) {
        setLoading(true)
        const prevHeight = el.scrollHeight
        onLoadMore().then(() => {
          requestAnimationFrame(() => {
            el.scrollTop = el.scrollHeight - prevHeight
            setLoading(false)
          })
        })
      }
    }
    el.addEventListener('scroll', handleScroll)
    return () => el.removeEventListener('scroll', handleScroll)
  }, [hasMore, onLoadMore, loading])

  function formatTime(ts) {
    return new Date(ts).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
  }

  function formatSize(bytes) {
    if (!bytes) return ''
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
    return 'F'
  }

  return (
    <div className="message-list" ref={containerRef}>
      {hasMore && <div style={{ textAlign: 'center', padding: 8, fontSize: 12, color: '#666' }}>
        {loading ? '加载中...' : '向上滚动加载更多'}
      </div>}
      {messages.map((msg) => {
        if (msg.type === 'system') {
          return <div key={msg.id} className="system-msg">{msg.content}</div>
        }
        const isOwn = msg.senderId === currentUserId
        const initial = (msg.sender || '?')[0].toUpperCase()

        return (
          <div key={msg.id} className={`message-row ${isOwn ? 'own' : ''}`}>
            <div className="msg-avatar">{initial}</div>
            <div className="msg-bubble">
              {!isOwn && <div className="msg-sender">{msg.sender}</div>}
              {msg.type === 'text' && <div className="msg-content">{msg.content}</div>}
              {msg.type === 'file' && msg.file && (
                <div className="file-msg">
                  <span className="file-icon">{getFileIcon(msg.file.type)}</span>
                  <div className="file-info">
                    <div className="file-name" title={msg.file.name}>{msg.file.name}</div>
                    <div className="file-size">{formatSize(msg.file.size)}</div>
                  </div>
                  <a href={msg.file.url} download={msg.file.name} className="file-download">下载</a>
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
