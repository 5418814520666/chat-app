import { createServer } from 'http'
import { existsSync } from 'fs'
import { join, extname, dirname } from 'path'
import { fileURLToPath } from 'url'
import crypto from 'crypto'
import express from 'express'
import cors from 'cors'
import { Server } from 'socket.io'
import multer from 'multer'
import { v4 as uuidv4 } from 'uuid'
import bcrypt from 'bcryptjs'
import jwt from 'jsonwebtoken'
import db from './db.js'

const __filename = fileURLToPath(import.meta.url)
const __dirname = dirname(__filename)

const JWT_SECRET = process.env.JWT_SECRET || crypto.randomBytes(32).toString('hex')

const app = express()
const httpServer = createServer(app)

app.use(cors())
app.use(express.json())

let CLIENT_DIST = join(__dirname, 'client-dist')
if (!existsSync(CLIENT_DIST)) {
  CLIENT_DIST = join(__dirname, '..', 'client', 'dist')
}
if (existsSync(CLIENT_DIST)) {
  app.use(express.static(CLIENT_DIST))
}

const UPLOADS_DIR = join(__dirname, 'uploads')
const MAX_FILE_SIZE = 50 * 1024 * 1024

const storage = multer.diskStorage({
  destination: (_req, _file, cb) => cb(null, UPLOADS_DIR),
  filename: (_req, file, cb) => {
    const ext = extname(file.originalname)
    cb(null, `${uuidv4()}${ext}`)
  }
})

const upload = multer({ storage, limits: { fileSize: MAX_FILE_SIZE } })

function authMiddleware(req, res, next) {
  const header = req.headers.authorization
  if (!header || !header.startsWith('Bearer ')) {
    return res.status(401).json({ error: '请先登录' })
  }
  try {
    const token = header.split(' ')[1]
    const payload = jwt.verify(token, JWT_SECRET)
    req.user = payload
    next()
  } catch {
    return res.status(401).json({ error: '登录已过期，请重新登录' })
  }
}

app.post('/api/auth/register', (req, res) => {
  const { username, password } = req.body
  if (!username || !password) {
    return res.status(400).json({ error: '用户名和密码不能为空' })
  }
  if (username.length < 2 || username.length > 20) {
    return res.status(400).json({ error: '用户名需要 2-20 个字符' })
  }
  if (password.length < 4) {
    return res.status(400).json({ error: '密码至少 4 个字符' })
  }

  const existing = db.prepare('SELECT id FROM users WHERE username = ?').get(username)
  if (existing) {
    return res.status(409).json({ error: '用户名已存在' })
  }

  const hash = bcrypt.hashSync(password, 10)
  const result = db.prepare('INSERT INTO users (username, password) VALUES (?, ?)').run(username, hash)
  const token = jwt.sign({ id: result.lastInsertRowid, username }, JWT_SECRET, { expiresIn: '7d' })

  res.json({ token, user: { id: result.lastInsertRowid, username } })
})

app.post('/api/auth/login', (req, res) => {
  const { username, password } = req.body
  if (!username || !password) {
    return res.status(400).json({ error: '用户名和密码不能为空' })
  }

  const user = db.prepare('SELECT * FROM users WHERE username = ?').get(username)
  if (!user || !bcrypt.compareSync(password, user.password)) {
    return res.status(401).json({ error: '用户名或密码错误' })
  }

  const token = jwt.sign({ id: user.id, username: user.username }, JWT_SECRET, { expiresIn: '7d' })
  res.json({ token, user: { id: user.id, username: user.username } })
})

app.post('/api/upload', authMiddleware, upload.single('file'), (req, res) => {
  if (!req.file) {
    return res.status(400).json({ error: 'No file uploaded' })
  }
  const fileInfo = {
    id: uuidv4(),
    name: req.file.originalname,
    size: req.file.size,
    type: req.file.mimetype,
    storedName: req.file.filename,
    url: `/api/files/${req.file.filename}`,
    roomId: req.body.roomId || '',
    sender: req.user.username,
    timestamp: Date.now()
  }
  res.json(fileInfo)
})

app.get('/api/files/:filename', authMiddleware, (req, res) => {
  const filePath = join(UPLOADS_DIR, req.params.filename)
  if (!existsSync(filePath)) {
    return res.status(404).json({ error: 'File not found' })
  }
  res.sendFile(filePath)
})

app.get('/api/rooms', authMiddleware, (_req, res) => {
  const rooms = db.prepare(
    `SELECT room_id, COUNT(DISTINCT user_id) as user_count, COUNT(*) as message_count
     FROM messages GROUP BY room_id ORDER BY message_count DESC`
  ).all()
  res.json(rooms)
})

app.get('/api/messages/:roomId', authMiddleware, (req, res) => {
  const { roomId } = req.params
  const limit = parseInt(req.query.limit) || 100
  const before = parseInt(req.query.before) || Date.now()

  const rows = db.prepare(
    `SELECT * FROM messages WHERE room_id = ? AND timestamp < ? ORDER BY timestamp DESC LIMIT ?`
  ).all(roomId, before, limit)

  const messages = rows.reverse().map((r) => ({
    id: r.id.toString(),
    type: r.type,
    content: r.content,
    sender: r.username,
    senderId: r.user_id.toString(),
    timestamp: r.timestamp,
    file: r.type === 'file' ? {
      id: r.id.toString(),
      name: r.file_name,
      size: r.file_size,
      type: r.file_type,
      url: r.file_url
    } : undefined
  }))

  res.json(messages)
})

function getPrivateRoomId(userId1, userId2) {
  const a = Number(userId1), b = Number(userId2)
  return `private_${Math.min(a, b)}_${Math.max(a, b)}`
}

app.get('/api/users/search', authMiddleware, (req, res) => {
  const q = req.query.q
  if (!q || q.length < 2) return res.json([])
  const rows = db.prepare(
    `SELECT id, username FROM users WHERE username LIKE ? AND id != ? LIMIT 10`
  ).all(`%${q}%`, req.user.id)
  res.json(rows.map(r => ({ id: r.id, username: r.username })))
})

app.get('/api/friends', authMiddleware, (req, res) => {
  const rows = db.prepare(
    `SELECT u.id, u.username FROM friendships f
     JOIN users u ON (CASE WHEN f.user1_id = ? THEN f.user2_id ELSE f.user1_id END) = u.id
     WHERE f.user1_id = ? OR f.user2_id = ?`
  ).all(req.user.id, req.user.id, req.user.id)
  res.json(rows)
})

app.get('/api/friend-requests', authMiddleware, (req, res) => {
  const incoming = db.prepare(
    `SELECT fr.id, fr.from_user_id, fr.to_user_id, fr.status, fr.created_at, u.username as from_username
     FROM friend_requests fr JOIN users u ON fr.from_user_id = u.id
     WHERE fr.to_user_id = ? AND fr.status = 'pending'`
  ).all(req.user.id)

  const outgoing = db.prepare(
    `SELECT fr.id, fr.from_user_id, fr.to_user_id, fr.status, fr.created_at, u.username as to_username
     FROM friend_requests fr JOIN users u ON fr.to_user_id = u.id
     WHERE fr.from_user_id = ?`
  ).all(req.user.id)

  res.json({ incoming, outgoing })
})

app.post('/api/friend-request', authMiddleware, (req, res) => {
  const { toUserId } = req.body
  if (!toUserId) return res.status(400).json({ error: '请指定用户' })

  const target = db.prepare('SELECT id FROM users WHERE id = ?').get(toUserId)
  if (!target) return res.status(404).json({ error: '用户不存在' })
  if (toUserId == req.user.id) return res.status(400).json({ error: '不能添加自己为好友' })

  const existing = db.prepare(
    'SELECT id FROM friend_requests WHERE from_user_id = ? AND to_user_id = ?'
  ).get(req.user.id, toUserId)
  if (existing) return res.status(409).json({ error: '已发送过好友请求' })

  const isFriend = db.prepare(
    'SELECT id FROM friendships WHERE (user1_id = ? AND user2_id = ?) OR (user1_id = ? AND user2_id = ?)'
  ).get(req.user.id, toUserId, toUserId, req.user.id)
  if (isFriend) return res.status(409).json({ error: '已经是好友' })

  db.prepare('INSERT INTO friend_requests (from_user_id, to_user_id) VALUES (?, ?)').run(req.user.id, toUserId)

  const targetSocketId = userSockets.get(Number(toUserId))
  if (targetSocketId) {
    io.to(targetSocketId).emit('friend-request-received', {
      fromUserId: req.user.id,
      fromUsername: req.user.username
    })
  }

  res.json({ ok: true })
})

app.post('/api/friend-accept', authMiddleware, (req, res) => {
  const { requestId, fromUserId } = req.body
  const request = db.prepare('SELECT * FROM friend_requests WHERE id = ? AND to_user_id = ? AND status = ?').get(requestId, req.user.id, 'pending')
  if (!request) return res.status(404).json({ error: '请求不存在' })

  db.prepare('UPDATE friend_requests SET status = ? WHERE id = ?').run('accepted', requestId)

  const a = Math.min(request.from_user_id, request.to_user_id)
  const b = Math.max(request.from_user_id, request.to_user_id)
  db.prepare('INSERT OR IGNORE INTO friendships (user1_id, user2_id) VALUES (?, ?)').run(a, b)

  const friendSocketId = userSockets.get(Number(request.from_user_id))
  if (friendSocketId) {
    io.to(friendSocketId).emit('friend-request-accepted', {
      byUserId: req.user.id,
      byUsername: req.user.username
    })
  }

  res.json({ ok: true })
})

app.post('/api/friend-reject', authMiddleware, (req, res) => {
  const { requestId } = req.body
  db.prepare('UPDATE friend_requests SET status = ? WHERE id = ? AND to_user_id = ?').run('rejected', requestId, req.user.id)
  res.json({ ok: true })
})

app.get('/api/private-messages/:otherUserId', authMiddleware, (req, res) => {
  const roomId = getPrivateRoomId(req.user.id, req.params.otherUserId)
  const limit = parseInt(req.query.limit) || 100
  const before = parseInt(req.query.before) || Date.now()

  const isFriend = db.prepare(
    'SELECT id FROM friendships WHERE (user1_id = ? AND user2_id = ?) OR (user1_id = ? AND user2_id = ?)'
  ).get(req.user.id, req.params.otherUserId, req.params.otherUserId, req.user.id)
  if (!isFriend) return res.status(403).json({ error: '不是好友' })

  const rows = db.prepare(
    `SELECT * FROM messages WHERE room_id = ? AND timestamp < ? ORDER BY timestamp DESC LIMIT ?`
  ).all(roomId, before, limit)

  const messages = rows.reverse().map((r) => ({
    id: r.id.toString(),
    type: r.type,
    content: r.content,
    sender: r.username,
    senderId: r.user_id.toString(),
    timestamp: r.timestamp,
    file: r.type === 'file' ? {
      id: r.id.toString(),
      name: r.file_name,
      size: r.file_size,
      type: r.file_type,
      url: r.file_url
    } : undefined
  }))

  res.json(messages)
})

const io = new Server(httpServer, {
  cors: { origin: '*', methods: ['GET', 'POST'] },
  maxHttpBufferSize: 1e8
})

const userSockets = new Map()

io.use((socket, next) => {
  const token = socket.handshake.auth.token
  if (!token) {
    return next(new Error('请先登录'))
  }
  try {
    const payload = jwt.verify(token, JWT_SECRET)
    socket.data.userId = payload.id
    socket.data.username = payload.username
    next()
  } catch {
    next(new Error('登录已过期'))
  }
})

function notifyFriend(io, userSockets, targetUserId, event, data) {
  const sid = userSockets.get(Number(targetUserId))
  if (sid) io.to(sid).emit(event, data)
}

io.on('connection', (socket) => {
  console.log(`User connected: ${socket.data.username} (${socket.id})`)
  userSockets.set(socket.data.userId, socket.id)

  socket.on('join-room', ({ roomId }) => {
    socket.join(roomId)
    socket.data.roomId = roomId

    const sockets = io.sockets.adapter.rooms.get(roomId)
    const userList = []
    if (sockets) {
      for (const sid of sockets) {
        const s = io.sockets.sockets.get(sid)
        if (s) {
          userList.push({ id: s.data.userId, username: s.data.username, sid })
        }
      }
    }

    io.to(roomId).emit('user-list', userList)

    socket.to(roomId).emit('user-joined', {
      id: socket.data.userId,
      username: socket.data.username
    })

    const rows = db.prepare(
      `SELECT * FROM messages WHERE room_id = ? ORDER BY timestamp DESC LIMIT 100`
    ).all(roomId)

    const messages = rows.reverse().map((r) => ({
      id: r.id.toString(),
      type: r.type,
      content: r.content,
      sender: r.username,
      senderId: r.user_id.toString(),
      timestamp: r.timestamp,
      file: r.type === 'file' ? {
        id: r.id.toString(),
        name: r.file_name,
        size: r.file_size,
        type: r.file_type,
        url: r.file_url
      } : undefined
    }))

    socket.emit('message-history', messages)

    io.to(roomId).emit('system-message', {
      type: 'info',
      content: `${socket.data.username} 加入了房间`
    })
  })

  socket.on('send-message', ({ roomId, message }) => {
    const result = db.prepare(
      `INSERT INTO messages (room_id, user_id, username, type, content, timestamp)
       VALUES (?, ?, ?, 'text', ?, ?)`
    ).run(roomId, socket.data.userId, socket.data.username, message, Date.now())

    const msgObj = {
      id: result.lastInsertRowid.toString(),
      type: 'text',
      content: message,
      sender: socket.data.username,
      senderId: socket.data.userId.toString(),
      timestamp: Date.now()
    }

    io.to(roomId).emit('new-message', msgObj)
  })

  socket.on('send-file-message', ({ roomId, fileInfo }) => {
    const result = db.prepare(
      `INSERT INTO messages (room_id, user_id, username, type, file_name, file_size, file_type, file_url, timestamp)
       VALUES (?, ?, ?, 'file', ?, ?, ?, ?, ?)`
    ).run(roomId, socket.data.userId, socket.data.username,
      fileInfo.name, fileInfo.size, fileInfo.type, fileInfo.url, Date.now())

    db.prepare(
      `INSERT INTO files (stored_name, original_name, size, mime_type, uploader_id, room_id, message_id)
       VALUES (?, ?, ?, ?, ?, ?, ?)`
    ).run(fileInfo.storedName, fileInfo.name, fileInfo.size, fileInfo.type,
      socket.data.userId, roomId, result.lastInsertRowid)

    const msgObj = {
      id: result.lastInsertRowid.toString(),
      type: 'file',
      file: { ...fileInfo, id: result.lastInsertRowid.toString() },
      sender: socket.data.username,
      senderId: socket.data.userId.toString(),
      timestamp: Date.now()
    }

    io.to(roomId).emit('new-message', msgObj)
  })

  socket.on('webrtc-offer', ({ to, offer }) => {
    socket.to(to).emit('webrtc-offer', { from: socket.id, offer, username: socket.data.username })
  })

  socket.on('webrtc-answer', ({ to, answer }) => {
    socket.to(to).emit('webrtc-answer', { from: socket.id, answer })
  })

  socket.on('webrtc-ice-candidate', ({ to, candidate }) => {
    socket.to(to).emit('webrtc-ice-candidate', { from: socket.id, candidate })
  })

  socket.on('call-user', ({ to, toUserId }) => {
    const targetSid = toUserId ? userSockets.get(Number(toUserId)) : to
    if (targetSid) {
      socket.to(targetSid).emit('incoming-call', { from: socket.id, username: socket.data.username, userId: socket.data.userId })
    }
  })

  socket.on('call-accepted', ({ to }) => {
    socket.to(to).emit('call-accepted', { from: socket.id, username: socket.data.username })
  })

  socket.on('call-rejected', ({ to }) => {
    socket.to(to).emit('call-rejected', { from: socket.id, username: socket.data.username })
  })

  socket.on('hang-up', ({ to }) => {
    socket.to(to).emit('hang-up', { from: socket.id })
  })

  socket.on('typing', ({ roomId }) => {
    socket.to(roomId).emit('user-typing', { userId: socket.data.userId, username: socket.data.username })
  })

  socket.on('stop-typing', ({ roomId }) => {
    socket.to(roomId).emit('user-stop-typing', { userId: socket.data.userId })
  })

  socket.on('disconnect', () => {
    userSockets.delete(socket.data.userId)
    const roomId = socket.data.roomId
    if (roomId) {
      const sockets = io.sockets.adapter.rooms.get(roomId)
      const userList = []
      if (sockets) {
        for (const sid of sockets) {
          const s = io.sockets.sockets.get(sid)
          if (s) {
            userList.push({ id: s.data.userId, username: s.data.username, sid })
          }
        }
      }
      io.to(roomId).emit('user-list', userList)
      io.to(roomId).emit('user-left', { id: socket.data.userId, username: socket.data.username })
      io.to(roomId).emit('system-message', {
        type: 'info',
        content: `${socket.data.username} 离开了房间`
      })
    }
    console.log(`User disconnected: ${socket.data.username} (${socket.id})`)
  })
})

if (existsSync(CLIENT_DIST)) {
  app.get('*', (_req, res) => {
    res.sendFile(join(CLIENT_DIST, 'index.html'))
  })
}

const PORT = process.env.PORT || 3001
httpServer.listen(PORT, '0.0.0.0', () => {
  console.log(`Server running on port ${PORT}`)
})
