#!/usr/bin/env bash
# Fails if an APK requests a permission that isn't allowed below.
# Verba promises minimal permissions; this catches libraries that quietly add
# some (e.g. INTERNET). Usage: tools/check-permissions.sh path/to/app.apk
set -euo pipefail

apk="${1:?usage: $0 <apk>}"
sdk="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
aapt2="$(ls -d "$sdk"/build-tools/*/aapt2* 2>/dev/null | sort -V | tail -1)"
[ -x "$aapt2" ] || { echo "aapt2 not found under $sdk/build-tools" >&2; exit 2; }

# One pattern per line; each needs a reason.
allowed=(
  # androidx.core: protects receivers registered with RECEIVER_NOT_EXPORTED,
  # declared and used only by the app itself
  '^io\.github\.lenkr0\.verba(\.debug)?\.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION$'
)

requested="$("$aapt2" dump permissions "$apk" | tr -d '\r' | sed -n "s/^uses-permission: name='\([^']*\)'.*/\1/p")"
status=0
while IFS= read -r permission; do
  [ -n "$permission" ] || continue
  ok=0
  for pattern in "${allowed[@]}"; do
    if [[ "$permission" =~ $pattern ]]; then ok=1; break; fi
  done
  if [ "$ok" = 1 ]; then
    echo "allowed:    $permission"
  else
    echo "NOT ALLOWED: $permission" >&2
    status=1
  fi
done <<< "$requested"

[ "$status" = 0 ] && echo "Permissions OK: $(basename "$apk")"
exit "$status"
