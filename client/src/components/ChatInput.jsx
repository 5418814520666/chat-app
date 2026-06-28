import { useState, useRef, useCallback } from 'react'

export default function ChatInput({ onSend, onFileSend, onTyping, username, roomId }) {
  const [text, setText] = useState('')
  const [uploading, setUploading] = useState(false)
  const fileInputRef = useRef(null)
  const typingTimeoutRef = useRef(null)

  const handleSend = useCallback(() => {
    if (!text.trim()) return
    onSend(text)
    setText('')
    onTyping(false)
    if (typingTimeoutRef.current) {
      clearTimeout(typingTimeoutRef.current)
      typingTimeoutRef.current = null
    }
  }, [text, onSend, onTyping])

  const handleKeyDown = (e) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault()
      handleSend()
    }
  }

  const handleChange = (e) => {
    setText(e.target.value)
    onTyping(true)
    if (typingTimeoutRef.current) {
      clearTimeout(typingTimeoutRef.current)
    }
    typingTimeoutRef.current = setTimeout(() => {
      onTyping(false)
      typingTimeoutRef.current = null
    }, 2000)
  }

  const handleFileChange = async (e) => {
    const file = e.target.files?.[0]
    if (!file) return

    setUploading(true)
    try {
      const formData = new FormData()
      formData.append('file', file)
      formData.append('roomId', roomId)
      formData.append('sender', username)

      const res = await fetch('/api/upload', {
        method: 'POST',
        body: formData
      })

      if (!res.ok) throw new Error('Upload failed')

      const fileInfo = await res.json()
      onFileSend(fileInfo)
    } catch (err) {
      console.error('File upload error:', err)
    } finally {
      setUploading(false)
      if (fileInputRef.current) {
        fileInputRef.current.value = ''
      }
    }
  }

  return (
    <div className="chat-input-area">
      <div className="input-row">
        <textarea
          value={text}
          onChange={handleChange}
          onKeyDown={handleKeyDown}
          placeholder="输入消息... (Enter 发送, Shift+Enter 换行)"
          rows={1}
        />
        <label className="file-upload-btn" title="发送文件">
          {uploading ? '...' : '+'}
          <input
            ref={fileInputRef}
            type="file"
            onChange={handleFileChange}
            accept="*"
          />
        </label>
        <button
          className="send-btn"
          onClick={handleSend}
          disabled={!text.trim() || uploading}
          title="发送"
        >
          &gt;
        </button>
      </div>
    </div>
  )
}
