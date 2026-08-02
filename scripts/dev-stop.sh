#!/bin/bash
# dev-stop.sh — Stop any running AnotherViewer Web dev processes
#
# Usage: ./scripts/dev-stop.sh
#
# Reads the PID file written by dev-run.sh and kills recorded processes.
# Falls back to searching for processes by JAR name if no PID file exists.

set -euo pipefail

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

info()  { echo -e "${GREEN}[INFO]${NC}  $*"; }
warn()  { echo -e "${YELLOW}[WARN]${NC}  $*"; }
error() { echo -e "${RED}[ERROR]${NC} $*" >&2; }

PROJECT_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
PID_FILE="${PROJECT_ROOT}/.dev-data/.dev-pids"

STOPPED=0

# ---------------------------------------------------------------------------
# Kill a PID if it's alive
# ---------------------------------------------------------------------------
kill_pid() {
    local pid="$1"
    local label="$2"

    if [[ -z "$pid" ]]; then
        return
    fi

    if kill -0 "$pid" 2>/dev/null; then
        info "Stopping ${label} (PID ${pid})..."
        kill "$pid" 2>/dev/null || true

        # Wait up to 5 seconds for graceful shutdown
        local waited=0
        while kill -0 "$pid" 2>/dev/null && (( waited < 5 )); do
            sleep 1
            waited=$((waited + 1))
        done

        # Force kill if still alive
        if kill -0 "$pid" 2>/dev/null; then
            warn "${label} (PID ${pid}) did not stop gracefully; force killing..."
            kill -9 "$pid" 2>/dev/null || true
        fi

        STOPPED=$((STOPPED + 1))
    else
        warn "${label} (PID ${pid}) is not running."
    fi
}

# ---------------------------------------------------------------------------
# 1. Try PID file first
# ---------------------------------------------------------------------------
if [[ -f "$PID_FILE" ]]; then
    info "Reading PID file: ${PID_FILE}"

    while IFS='=' read -r key pid; do
        # Skip empty lines and comments
        [[ -z "$key" || "$key" == \#* ]] && continue
        kill_pid "$pid" "$key"
    done < "$PID_FILE"

    rm -f "$PID_FILE"
fi

# ---------------------------------------------------------------------------
# 2. Fallback: search for orphaned processes by pattern
# ---------------------------------------------------------------------------

# Backend: java -jar anotherviewer-web-*.jar
BACKEND_PIDS=$(pgrep -f 'java.*anotherviewer-web-.*\.jar' 2>/dev/null || true)
if [[ -n "$BACKEND_PIDS" ]]; then
    for pid in $BACKEND_PIDS; do
        kill_pid "$pid" "backend (orphan)"
    done
fi

# Frontend: vite dev server (node process running vite)
FRONTEND_PIDS=$(pgrep -f 'vite.*--port\|node.*vite' 2>/dev/null || true)
if [[ -n "$FRONTEND_PIDS" ]]; then
    for pid in $FRONTEND_PIDS; do
        # Only kill if it's under our project directory
        local_cwd=$(lsof -p "$pid" -Fn 2>/dev/null | grep '^n/' | head -1 | sed 's/^n//' || true)
        if [[ "$local_cwd" == *"${PROJECT_ROOT}"* ]] || [[ -z "$local_cwd" ]]; then
            kill_pid "$pid" "frontend/vite (orphan)"
        fi
    done
fi

# ---------------------------------------------------------------------------
# Summary
# ---------------------------------------------------------------------------
echo ""
if (( STOPPED > 0 )); then
    info "Stopped ${STOPPED} process(es)."
else
    info "No running dev processes found."
fi
