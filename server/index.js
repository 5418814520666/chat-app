import { createServer } from 'http'
import { readFileSync, existsSync } from 'fs'
import { join, extname, dirname } from 'path'
import { fileURLToPath } from 'url'
import crypto from 'crypto'
import express from 'express'
import cors from 'cors'
import { Server } from 'socket.io'
import multer from 'multer'
import { v4 as uuidv4 } from 'uuid'

const __filename = fileURLToPath(import.meta.url)
const __dirname = dirname(__filename)

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
    const name = `${uuidv4()}${ext}`
    cb(null, name)
  }
})

const upload = multer({
  storage,
  limits: { fileSize: MAX_FILE_SIZE }
})

const rooms = new Map()

function getRoom(roomId) {
  if (!rooms.has(roomId)) {
    rooms.set(roomId, {
      users: new Map(),
      messages: []
    })
  }
  return rooms.get(roomId)
}

const io = new Server(httpServer, {
  cors: {
    origin: '*',
    methods: ['GET', 'POST']
  },
  maxHttpBufferSize: 1e8
})

app.post('/api/upload', upload.single('file'), (req, res) => {
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
    sender: req.body.sender || 'Anonymous',
    timestamp: Date.now()
  }
  res.json(fileInfo)
})

app.get('/api/files/:filename', (req, res) => {
  const filePath = join(UPLOADS_DIR, req.params.filename)
  if (!existsSync(filePath)) {
    return res.status(404).json({ error: 'File not found' })
  }
  res.sendFile(filePath)
})

app.get('/api/rooms', (_req, res) => {
  const roomList = []
  for (const [id, room] of rooms) {
    roomList.push({
      id,
      userCount: room.users.size
    })
  }
  res.json(roomList)
})

if (existsSync(CLIENT_DIST)) {
  app.get('*', (_req, res) => {
    res.sendFile(join(CLIENT_DIST, 'index.html'))
  })
}

io.on('connection', (socket) => {
  console.log(`User connected: ${socket.id}`)

  socket.on('join-room', ({ roomId, username }) => {
    socket.join(roomId)
    const room = getRoom(roomId)

    room.users.set(socket.id, {
      id: socket.id,
      username: username || 'Anonymous',
      joinedAt: Date.now()
    })

    socket.data.roomId = roomId
    socket.data.username = username || 'Anonymous'

    const userList = Array.from(room.users.values())

    io.to(roomId).emit('user-list', userList)

    socket.to(roomId).emit('user-joined', {
      id: socket.id,
      username: username || 'Anonymous'
    })

    if (room.messages.length > 0) {
      socket.emit('message-history', room.messages.slice(-100))
    }

    io.to(roomId).emit('system-message', {
      type: 'info',
      content: `${username || 'Anonymous'} joined the room`
    })
  })

  socket.on('send-message', ({ roomId, message }) => {
    const room = getRoom(roomId)
    const msgObj = {
      id: uuidv4(),
      type: 'text',
      content: message,
      sender: socket.data.username || 'Anonymous',
      senderId: socket.id,
      timestamp: Date.now()
    }

    room.messages.push(msgObj)
    if (room.messages.length > 200) {
      room.messages = room.messages.slice(-200)
    }

    io.to(roomId).emit('new-message', msgObj)
  })

  socket.on('send-file-message', ({ roomId, fileInfo }) => {
    const room = getRoom(roomId)
    const msgObj = {
      id: uuidv4(),
      type: 'file',
      file: fileInfo,
      sender: socket.data.username || 'Anonymous',
      senderId: socket.id,
      timestamp: Date.now()
    }

    room.messages.push(msgObj)
    if (room.messages.length > 200) {
      room.messages = room.messages.slice(-200)
    }

    io.to(roomId).emit('new-message', msgObj)
  })

  socket.on('webrtc-offer', ({ to, offer }) => {
    socket.to(to).emit('webrtc-offer', {
      from: socket.id,
      offer,
      username: socket.data.username
    })
  })

  socket.on('webrtc-answer', ({ to, answer }) => {
    socket.to(to).emit('webrtc-answer', {
      from: socket.id,
      answer
    })
  })

  socket.on('webrtc-ice-candidate', ({ to, candidate }) => {
    socket.to(to).emit('webrtc-ice-candidate', {
      from: socket.id,
      candidate
    })
  })

  socket.on('call-user', ({ to }) => {
    socket.to(to).emit('incoming-call', {
      from: socket.id,
      username: socket.data.username
    })
  })

  socket.on('call-accepted', ({ to }) => {
    socket.to(to).emit('call-accepted', {
      from: socket.id,
      username: socket.data.username
    })
  })

  socket.on('call-rejected', ({ to }) => {
    socket.to(to).emit('call-rejected', {
      from: socket.id,
      username: socket.data.username
    })
  })

  socket.on('hang-up', ({ to }) => {
    socket.to(to).emit('hang-up', {
      from: socket.id
    })
  })

  socket.on('typing', ({ roomId }) => {
    socket.to(roomId).emit('user-typing', {
      userId: socket.id,
      username: socket.data.username
    })
  })

  socket.on('stop-typing', ({ roomId }) => {
    socket.to(roomId).emit('user-stop-typing', {
      userId: socket.id
    })
  })

  socket.on('disconnect', () => {
    const roomId = socket.data.roomId
    if (roomId && rooms.has(roomId)) {
      const room = rooms.get(roomId)
      room.users.delete(socket.id)

      io.to(roomId).emit('user-list', Array.from(room.users.values()))
      io.to(roomId).emit('user-left', {
        id: socket.id,
        username: socket.data.username
      })
      io.to(roomId).emit('system-message', {
        type: 'info',
        content: `${socket.data.username || 'Anonymous'} left the room`
      })

      if (room.users.size === 0) {
        rooms.delete(roomId)
      }
    }
    console.log(`User disconnected: ${socket.id}`)
  })
})

const PORT = process.env.PORT || 3001
httpServer.listen(PORT, '0.0.0.0', () => {
  console.log(`Server running on port ${PORT}`)
})
