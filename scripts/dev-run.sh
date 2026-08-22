#!/bin/bash
# dev-run.sh — One-command development environment for AnotherViewer Web
#
# Usage: ./scripts/dev-run.sh [options]
#   --backend-only    Only start backend
#   --frontend-only   Only start frontend dev server (Vite, port 3000)
#   --skip-build      Skip build steps (assume artifacts exist)
#   --port PORT       Backend port (default: 8080)
#   --dev             Dev mode: Vite dev server + backend simultaneously
#   --help            Show this help message
#
# Examples:
#   ./scripts/dev-run.sh                  # Build everything, start backend
#   ./scripts/dev-run.sh --dev            # Build, start backend + Vite dev server
#   ./scripts/dev-run.sh --skip-build     # Start without rebuilding
#   ./scripts/dev-run.sh --port 9090      # Backend on port 9090

set -euo pipefail

# ---------------------------------------------------------------------------
# Colors
# ---------------------------------------------------------------------------
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

info()  { echo -e "${GREEN}[INFO]${NC}  $*"; }
warn()  { echo -e "${YELLOW}[WARN]${NC}  $*"; }
error() { echo -e "${RED}[ERROR]${NC} $*" >&2; }
step()  { echo -e "${CYAN}[STEP]${NC}  $*"; }

# ---------------------------------------------------------------------------
# Project root (resolve relative to this script)
# ---------------------------------------------------------------------------
PROJECT_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
DATA_DIR="${PROJECT_ROOT}/.dev-data"
PID_FILE="${DATA_DIR}/.dev-pids"

# ---------------------------------------------------------------------------
# Defaults
# ---------------------------------------------------------------------------
PORT=8080
BACKEND_ONLY=false
FRONTEND_ONLY=false
SKIP_BUILD=false
DEV_MODE=false

# Child PIDs for cleanup
BACKEND_PID=""
FRONTEND_PID=""

# ---------------------------------------------------------------------------
# Usage
# ---------------------------------------------------------------------------
usage() {
    cat <<'EOF'
dev-run.sh — One-command development environment for AnotherViewer Web

Usage: ./scripts/dev-run.sh [options]

Options:
  --backend-only    Only start the backend (Spring Boot JAR)
  --frontend-only   Only start the frontend Vite dev server (port 3000)
  --skip-build      Skip build steps (assume artifacts already exist)
  --port PORT       Backend HTTP port (default: 8080)
  --dev             Dev mode: start Vite dev server AND backend simultaneously
                    (Vite proxies /api and /ws to the backend)
  --help            Show this help message

Examples:
  ./scripts/dev-run.sh                  # Build all, start backend on :8080
  ./scripts/dev-run.sh --dev            # Build all, backend :8080 + Vite :3000
  ./scripts/dev-run.sh --skip-build     # Reuse existing artifacts, start backend
  ./scripts/dev-run.sh --port 9090      # Backend on :9090
  ./scripts/dev-run.sh --frontend-only  # Just Vite dev server (backend elsewhere)
EOF
    exit 0
}

# ---------------------------------------------------------------------------
# Argument parsing
# ---------------------------------------------------------------------------
while [[ $# -gt 0 ]]; do
    case "$1" in
        --backend-only)  BACKEND_ONLY=true;  shift ;;
        --frontend-only) FRONTEND_ONLY=true; shift ;;
        --skip-build)    SKIP_BUILD=true;    shift ;;
        --dev)           DEV_MODE=true;      shift ;;
        --port)
            if [[ -z "${2:-}" ]]; then
                error "--port requires a value"
                exit 1
            fi
            PORT="$2"
            shift 2
            ;;
        --help|-h) usage ;;
        *)
            error "Unknown option: $1"
            echo "Run with --help for usage."
            exit 1
            ;;
    esac
done

# Validate mutually exclusive flags
if $BACKEND_ONLY && $FRONTEND_ONLY; then
    error "--backend-only and --frontend-only are mutually exclusive"
    exit 1
fi
if $BACKEND_ONLY && $DEV_MODE; then
    error "--backend-only and --dev are mutually exclusive"
    exit 1
fi
if $FRONTEND_ONLY && $DEV_MODE; then
    warn "--frontend-only implies dev server only; ignoring --dev"
    DEV_MODE=false
fi

# ---------------------------------------------------------------------------
# Graceful shutdown
# ---------------------------------------------------------------------------
cleanup() {
    echo ""
    info "Shutting down..."

    # Kill frontend first (if any)
    if [[ -n "$FRONTEND_PID" ]] && kill -0 "$FRONTEND_PID" 2>/dev/null; then
        info "Stopping frontend (PID $FRONTEND_PID)..."
        kill "$FRONTEND_PID" 2>/dev/null || true
        wait "$FRONTEND_PID" 2>/dev/null || true
    fi

    # Kill backend
    if [[ -n "$BACKEND_PID" ]] && kill -0 "$BACKEND_PID" 2>/dev/null; then
        info "Stopping backend (PID $BACKEND_PID)..."
        kill "$BACKEND_PID" 2>/dev/null || true
        wait "$BACKEND_PID" 2>/dev/null || true
    fi

    # Clean up PID file
    rm -f "$PID_FILE"

    info "All processes stopped."
}

trap cleanup SIGINT SIGTERM EXIT

# ---------------------------------------------------------------------------
# 1. Data directory setup
# ---------------------------------------------------------------------------
setup_data_dirs() {
    step "Setting up data directories under .dev-data/"
    mkdir -p "${DATA_DIR}"/{downloads,cache,db,enhanced}
    info "Data directories ready: downloads, cache, db, enhanced"
}

# ---------------------------------------------------------------------------
# 2. Frontend build
# ---------------------------------------------------------------------------
build_frontend() {
    step "Building frontend (npm install + npm run build)..."
    cd "${PROJECT_ROOT}/web-frontend"

    if ! command -v npm &>/dev/null; then
        error "npm not found. Install Node.js first: https://nodejs.org/"
        exit 1
    fi

    npm install
    npm run build

    info "Frontend build complete → anotherviewer-web/src/main/resources/static/"
    cd "${PROJECT_ROOT}"
}

# ---------------------------------------------------------------------------
# 3. Backend build
# ---------------------------------------------------------------------------
build_backend() {
    step "Building backend (gradlew --configure-on-demand :anotherviewer-web:bootJar -x test)..."
    cd "${PROJECT_ROOT}"

    if [[ ! -x "./gradlew" ]]; then
        error "gradlew not found or not executable in ${PROJECT_ROOT}"
        exit 1
    fi

    # --configure-on-demand: skip configuring :app so no Android SDK is needed
    ./gradlew --configure-on-demand :anotherviewer-web:bootJar -x test

    info "Backend build complete."
}

# ---------------------------------------------------------------------------
# 4. Locate the built JAR
# ---------------------------------------------------------------------------
find_jar() {
    local jar_dir="${PROJECT_ROOT}/anotherviewer-web/build/libs"
    local jar

    # Find the bootJar artifact (anotherviewer-web-<version>.jar, not -plain.jar)
    # Pick the newest by mtime — libs/ may hold several versions after rebuilds
    jar=$(ls -t "${jar_dir}"/anotherviewer-web-*.jar 2>/dev/null | grep -v -- '-plain\.jar$' | head -1 || true)

    if [[ -z "$jar" ]]; then
        error "No JAR found in ${jar_dir}. Run without --skip-build first."
        exit 1
    fi

    echo "$jar"
}

# ---------------------------------------------------------------------------
# 5. Health check — wait for backend to respond
# ---------------------------------------------------------------------------
wait_for_backend() {
    local url="http://localhost:${PORT}/api/v1/auth/status"
    local max_attempts=60
    local attempt=0

    step "Waiting for backend to become ready on port ${PORT}..."

    while (( attempt < max_attempts )); do
        # Any HTTP response (even 401/403) means the server is up
        local http_code
        http_code=$(curl -s -o /dev/null -w '%{http_code}' --connect-timeout 2 "$url" 2>/dev/null || echo "000")

        if [[ "$http_code" != "000" ]]; then
            info "Backend is ready! (HTTP ${http_code} on ${url})"
            return 0
        fi

        attempt=$((attempt + 1))
        printf "."
        sleep 1
    done

    echo ""
    error "Backend did not become ready within ${max_attempts}s."
    error "Check logs above for errors."
    return 1
}

# ---------------------------------------------------------------------------
# 6. Start backend
# ---------------------------------------------------------------------------
start_backend() {
    local jar
    jar=$(find_jar)

    step "Starting backend: $(basename "$jar") on port ${PORT}"

    # Use ANOTHERVIEWER_DATA_DIR env var for SQLite DB path (see application.yml)
    # Use command-line args for download/cache/security paths
    ANOTHERVIEWER_DATA_DIR="${DATA_DIR}/db" \
    java -jar "$jar" \
        --server.port="${PORT}" \
        --anotherviewer.download.path="${DATA_DIR}/downloads" \
        --anotherviewer.download.cache-path="${DATA_DIR}/cache" \
        --anotherviewer.security.encryption-key-path="${DATA_DIR}/db/security.key" \
        &
    BACKEND_PID=$!

    # Record PID
    echo "backend=${BACKEND_PID}" >> "$PID_FILE"

    info "Backend starting (PID ${BACKEND_PID})..."
}

# ---------------------------------------------------------------------------
# 7. Start frontend Vite dev server
# ---------------------------------------------------------------------------
start_frontend_dev() {
    step "Starting Vite dev server on port 3000 (proxying /api → localhost:${PORT})..."

    cd "${PROJECT_ROOT}/web-frontend"

    # Update proxy target if non-default port
    if [[ "$PORT" != "8080" ]]; then
        warn "Vite proxy is hardcoded to localhost:8080 in vite.config.ts."
        warn "Backend is on port ${PORT} — proxy may not work. Consider updating vite.config.ts."
    fi

    npm run dev &
    FRONTEND_PID=$!

    # Record PID
    echo "frontend=${FRONTEND_PID}" >> "$PID_FILE"

    info "Vite dev server starting (PID ${FRONTEND_PID})..."
    info "Frontend: http://localhost:3000"

    cd "${PROJECT_ROOT}"
}

# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------
main() {
    echo ""
    info "AnotherViewer Web — Development Environment"
    info "Project root: ${PROJECT_ROOT}"
    echo ""

    # Always set up data dirs
    setup_data_dirs

    # Clean stale PID file
    rm -f "$PID_FILE"
    touch "$PID_FILE"

    # ----- Frontend-only mode -----
    if $FRONTEND_ONLY; then
        if ! $SKIP_BUILD; then
            cd "${PROJECT_ROOT}/web-frontend"
            step "Installing frontend dependencies..."
            npm install
            cd "${PROJECT_ROOT}"
        fi
        start_frontend_dev
        info "Frontend-only mode. Press Ctrl+C to stop."
        wait "$FRONTEND_PID"
        return
    fi

    # ----- Build phase -----
    if ! $SKIP_BUILD; then
        # Build frontend (unless backend-only — the JAR embeds static assets,
        # so we still build frontend for backend-only mode)
        build_frontend
        build_backend
    else
        warn "Skipping builds (--skip-build)."
    fi

    # ----- Start backend -----
    start_backend

    # ----- Wait for backend health -----
    if ! wait_for_backend; then
        error "Backend failed to start. Exiting."
        exit 1
    fi

    # ----- Dev mode: also start Vite -----
    if $DEV_MODE; then
        start_frontend_dev
        echo ""
        info "=========================================="
        info "  Backend:  http://localhost:${PORT}"
        info "  Frontend: http://localhost:3000 (Vite dev, proxies /api)"
        info "  Data:     ${DATA_DIR}"
        info "=========================================="
        info "Press Ctrl+C to stop all processes."
        echo ""
        # Wait for all background processes (bash 3.2 compatible — no wait -n)
        wait
    else
        echo ""
        info "=========================================="
        info "  Backend:  http://localhost:${PORT}"
        info "  Data:     ${DATA_DIR}"
        info "=========================================="
        if $BACKEND_ONLY; then
            info "  (backend-only mode)"
        fi
        info "Press Ctrl+C to stop."
        echo ""
        wait "$BACKEND_PID"
    fi
}

main
