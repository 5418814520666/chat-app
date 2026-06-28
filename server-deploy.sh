#!/usr/bin/env bash
set -e

REPO_URL="https://github.com/5418814520666/chat-app.git"
APP_DIR="/opt/chat-app"
PORT="${PORT:-3001}"
GREEN='\033[0;32m'
NC='\033[0m'

log() { echo -e "${GREEN}[$(date +%H:%M:%S)]${NC} $*"; }

# ---------- one-liner mode ----------
if [ "$1" = "--one-liner" ]; then
  command -v node &>/dev/null || { log "ERROR: Node.js required"; exit 1; }

  mkdir -p /opt/chat-app
  cp /workspace/server/index.js /opt/chat-app/ 2>/dev/null || true
  cp /workspace/server/package.json /opt/chat-app/ 2>/dev/null || true
  cd /opt/chat-app && npm install --production --silent 2>&1
  mkdir -p uploads
  nohup node index.js > /var/log/chat-app.log 2>&1 &
  sleep 1
  curl -s "http://localhost:${PORT}/api/rooms" >/dev/null 2>&1 && log "OK - http://8.138.224.117:${PORT}" || log "WARN: check /var/log/chat-app.log"
  exit 0
fi

# ---------- full deploy mode ----------
log "Chat App 服务端部署"

if ! command -v node &>/dev/null; then
  log "安装 Node.js..."
  export DEBIAN_FRONTEND=noninteractive
  curl -fsSL https://deb.nodesource.com/setup_20.x | bash - >/dev/null 2>&1
  apt-get install -y nodejs >/dev/null 2>&1
fi

log "Node.js $(node -v)"

if [ -d "$APP_DIR/.git" ]; then
  log "更新代码..."
  cd "$APP_DIR" && git pull --ff-only 2>&1
else
  log "克隆仓库..."
  git clone "$REPO_URL" "$APP_DIR" 2>&1
fi

cd "$APP_DIR/server"

log "安装依赖..."
npm install --production --silent

mkdir -p uploads

# Stop existing instance
if [ -f /var/run/chat-app.pid ]; then
  OLD_PID=$(cat /var/run/chat-app.pid)
  kill "$OLD_PID" 2>/dev/null && log "已停止旧进程 PID=$OLD_PID"
  rm -f /var/run/chat-app.pid
  sleep 1
fi

log "启动服务 (端口 $PORT)..."
export PORT="$PORT"
nohup node index.js > /var/log/chat-app.log 2>&1 &
PID=$!
echo "$PID" > /var/run/chat-app.pid

sleep 2
if kill -0 "$PID" 2>/dev/null; then
  log "部署成功 PID=$PID"
  log "地址: http://8.138.224.117:${PORT}"
  log "日志: /var/log/chat-app.log"
else
  log "启动失败，查看: /var/log/chat-app.log"
  exit 1
fi
