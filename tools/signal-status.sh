#!/usr/bin/env bash
# Copyright 2026 Cole Munz
# SPDX-License-Identifier: AGPL-3.0-only
#
# Rewrites the Signal status line between the signal-status markers in the
# README so the gap to Signal's stable release is always public.
# Usage: signal-status.sh <wren_signal> <signal_stable> <signal_tag_date|unknown> <molly_signal|unknown> [README]
set -euo pipefail

wren=${1:?wren Signal version}
signal=${2:?Signal stable version}
tagdate=${3:-unknown}
molly=${4:-unknown}
readme=${5:-README.md}

for v in "$wren" "$signal"; do
  [[ $v =~ ^[0-9]+(\.[0-9]+){1,3}$ ]] || { echo "bad version: $v" >&2; exit 1; }
done

line="Wren is on Signal $wren."
if [[ $signal == "$wren" ]]; then
  line+=" That is Signal's current stable release"
else
  line+=" Signal's stable release is $signal"
fi
if [[ $tagdate =~ ^[0-9]{4}-[0-9]{2}-[0-9]{2}$ ]]; then
  line+=" (tagged $tagdate)"
fi
line+="."
if [[ $molly =~ ^[0-9]+(\.[0-9]+){1,3}$ ]]; then
  line+=" Molly's main branch is on Signal $molly."
fi
line+=" The [daily sync workflow](https://github.com/munzzyy/wren/actions/workflows/sync-upstream.yml) rewrites this line whenever one of those changes."

python3 - "$readme" "$line" <<'PY'
import re, sys, pathlib
path = pathlib.Path(sys.argv[1])
text = path.read_text()
new, count = re.subn(
    r"(<!-- signal-status -->)\n.*?\n(<!-- /signal-status -->)",
    lambda m: f"{m.group(1)}\n{sys.argv[2]}\n{m.group(2)}",
    text, count=1, flags=re.S)
if count != 1:
    sys.exit(f"signal-status markers not found in {path}")
path.write_text(new)
PY
echo "$line"
