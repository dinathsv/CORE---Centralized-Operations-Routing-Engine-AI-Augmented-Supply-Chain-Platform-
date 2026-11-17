#!/usr/bin/env bash
# Install dependencies without regenerating or overwriting application source.
set -euo pipefail
CORE_ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
CORE_PYTHON="${CORE_PYTHON:-python3}"
command -v podman >/dev/null || { echo 'Podman 4.6+ is required.' >&2; exit 1; }
command -v podman-compose >/dev/null || { echo 'podman-compose 1.6.0+ is required. Install with: pipx install podman-compose==1.6.0' >&2; exit 1; }
command -v npm >/dev/null || { echo 'Node.js 20.9+ and npm are required.' >&2; exit 1; }
command -v "$CORE_PYTHON" >/dev/null || { echo "Python not found: $CORE_PYTHON" >&2; exit 1; }
node -e 'const [major, minor] = process.versions.node.split(".").map(Number); if (major < 20 || (major === 20 && minor < 9)) process.exit(1)' || {
  echo 'Node.js 20.9+ is required.' >&2; exit 1;
}
"$CORE_PYTHON" -c 'import sys; assert sys.version_info >= (3, 10), "Python 3.10+ is required"'
if [[ ! -f "$CORE_ROOT/.env" ]]; then
  cp "$CORE_ROOT/.env.example" "$CORE_ROOT/.env"
fi
if [[ ! -f "$CORE_ROOT/ai-agent/.env" ]]; then
  cp "$CORE_ROOT/ai-agent/.env.example" "$CORE_ROOT/ai-agent/.env"
fi
npm --prefix "$CORE_ROOT/frontend" ci --no-audit --no-fund
if [[ ! -x "$CORE_ROOT/ai-agent/venv/bin/python" ]]; then
  "$CORE_PYTHON" -m venv "$CORE_ROOT/ai-agent/venv"
fi
"$CORE_ROOT/ai-agent/venv/bin/python" -m pip install -r "$CORE_ROOT/ai-agent/requirements.txt" -r "$CORE_ROOT/ai-agent/requirements-dev.txt"
echo 'Dependencies installed. Start the stack with: podman-compose up --build -d --wait --wait-timeout 600'
echo 'For local development, see README.md. This script never replaces existing .env files.'
