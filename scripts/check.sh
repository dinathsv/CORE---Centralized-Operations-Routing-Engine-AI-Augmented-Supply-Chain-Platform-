#!/usr/bin/env bash
set -euo pipefail
CORE_ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
command -v podman >/dev/null || { echo 'Podman 4.6+ is required.' >&2; exit 1; }
command -v podman-compose >/dev/null || { echo 'podman-compose 1.6.0+ is required. Install with: pipx install podman-compose==1.6.0' >&2; exit 1; }
if [[ -n "${JAVA_HOME:-}" && ! -x "$JAVA_HOME/bin/java" ]]; then
  unset JAVA_HOME
fi
for service in gateway backend/inventory-service backend/orders-service; do
  (cd "$CORE_ROOT/$service" && ./mvnw -B test)
done
npm --prefix "$CORE_ROOT/frontend" run typecheck
npm --prefix "$CORE_ROOT/frontend" run build
npm --prefix "$CORE_ROOT/frontend" run test:proxy
(cd "$CORE_ROOT/ai-agent" && ./venv/bin/python -m pytest -q)
podman-compose -f "$CORE_ROOT/compose.yml" config --quiet
