#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
SERVER_DIR="$SCRIPT_DIR/server"
UPLOADS_DIR="$SERVER_DIR/uploads"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

log_info()  { echo -e "${GREEN}[INFO]${NC}  $*"; }
log_warn()  { echo -e "${YELLOW}[WARN]${NC}  $*"; }
log_error() { echo -e "${RED}[ERROR]${NC} $*"; }

trap 'log_info "服务已停止"' EXIT

log_info "开始部署 Chat 后端服务..."

# Check Node.js
if ! command -v node &>/dev/null; then
  log_error "Node.js 未安装，请先安装 Node.js 18+"
  exit 1
fi

NODE_VERSION=$(node -v | sed 's/v//' | cut -d. -f1)
log_info "Node.js 版本: $(node -v)"

# Ensure uploads directory exists
mkdir -p "$UPLOADS_DIR"

# Check if port is available
PORT="${PORT:-3001}"
if ss -tlnp 2>/dev/null | grep -q ":$PORT "; then
  log_warn "端口 $PORT 已被占用，正在尝试释放..."
  PID=$(ss -tlnp 2>/dev/null | grep ":$PORT " | grep -oP 'pid=\K[0-9]+' | head -1)
  if [ -n "$PID" ]; then
    kill "$PID" 2>/dev/null && log_info "已终止进程 PID=$PID"
    sleep 1
  fi
fi

# Install dependencies
log_info "安装后端依赖..."
cd "$SERVER_DIR"
if [ ! -d "node_modules" ] || [ package.json -nt node_modules ]; then
  npm install --production
fi

# Set environment
export PORT="$PORT"
export NODE_ENV="${NODE_ENV:-production}"

# Start server
log_info "启动服务 (端口: $PORT, 环境: $NODE_ENV)..."
node "$SERVER_DIR/index.js" &
SERVER_PID=$!
sleep 2

# Health check
if kill -0 "$SERVER_PID" 2>/dev/null; then
  log_info "服务启动成功! PID=$SERVER_PID"

  # Verify HTTP endpoint
  if command -v curl &>/dev/null; then
    RESPONSE=$(curl -s -o /dev/null -w "%{http_code}" "http://localhost:$PORT/api/rooms" 2>/dev/null || echo "000")
    if [ "$RESPONSE" = "200" ]; then
      log_info "健康检查通过 (HTTP $RESPONSE)"
    else
      log_warn "健康检查返回 $RESPONSE，服务可能仍在初始化"
    fi
  fi

  log_info "==================================="
  log_info "  后端服务已就绪"
  log_info "  地址: https://chat.yangchen.skin"
  log_info "  房间列表: https://chat.yangchen.skin/api/rooms"
  log_info "  文件目录: $UPLOADS_DIR"
  log_info "  进程 PID: $SERVER_PID"
  log_info "==================================="
else
  log_error "服务启动失败，检查日志"
  exit 1
fi

wait "$SERVER_PID"
