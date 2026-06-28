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

const io = new Server(httpServer, {
  cors: { origin: '*', methods: ['GET', 'POST'] },
  maxHttpBufferSize: 1e8
})

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

io.on('connection', (socket) => {
  console.log(`User connected: ${socket.data.username} (${socket.id})`)

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

  socket.on('call-user', ({ to }) => {
    socket.to(to).emit('incoming-call', { from: socket.id, username: socket.data.username })
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
