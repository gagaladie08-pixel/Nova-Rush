#!/usr/bin/env bash
# Exécute une commande ; en cas d'échec, publie la fin du journal en annotation
# (les journaux bruts d'Actions ne sont pas lisibles depuis l'outil de suivi).
log=$(mktemp)
"$@" 2>&1 | tee "$log"
code=${PIPESTATUS[0]}
if [ "$code" -ne 0 ]; then
  msg=$(grep -v '^::' "$log" | tail -n 25 | sed 's/%/%25/g' | awk '{printf "%s%%0A", $0}')
  echo "::error title=Échec ($code) $(basename "$2" 2>/dev/null) $3::$msg"
fi
exit "$code"
