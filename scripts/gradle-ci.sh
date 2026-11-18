#!/usr/bin/env bash
# Keep full reports as artifacts and surface actionable errors through Checks API.
# This is useful even when the runner log storage is unavailable to the client.
set -uo pipefail
mkdir -p artifacts/diagnostics
./gradlew --no-daemon --console=plain "$@" 2>&1 | tee artifacts/diagnostics/gradle.log
result=${PIPESTATUS[0]}
if [ "$result" -ne 0 ]; then
  python3 - <<'PY'
from pathlib import Path
lines = Path('artifacts/diagnostics/gradle.log').read_text(errors='replace').splitlines()
errors = [line for line in lines if line.startswith('e: ') or ' Error:' in line or line.startswith('> ') and not line.startswith('> Task')]
message = '\n'.join(errors[:20] + ['--- tail ---'] + lines[-55:])[-15000:]
value = message.replace('%', '%25').replace('\r', '%0D').replace('\n', '%0A')
print(f'::error title=Android build::{value}')
PY
fi
exit "$result"
