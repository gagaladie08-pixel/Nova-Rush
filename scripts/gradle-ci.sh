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
errors = [line for line in lines if line.startswith('e: ') or ' Error:' in line or line.startswith('> ') or line.startswith('* What went wrong:')]
if not errors:
    errors = lines[-45:]
for line in errors[:30]:
    value = line.replace('%', '%25').replace('\r', '%0D').replace('\n', '%0A')
    print(f'::error title=Android build::{value[:3500]}')
PY
fi
exit "$result"
