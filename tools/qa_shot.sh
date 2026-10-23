#!/usr/bin/env bash
# One design state -> one screenshot: tools/qa_shot.sh S13 [extra debug cmds...]
#   tools/qa_shot.sh S15 "ui rate_remembered"
# Sends `state <id>`, waits for `CMD state done <id>` in the log (the state re-pins loci with real
# anchors: a few seconds), runs the extra commands, lets the emulator render, then captures
# .scratch/shots/<id>.png (host console capture) and prints the reached designState.
set -u
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
DEV=${DEV:-emulator-5566}
id="$1"; shift
adb -s $DEV logcat -c
bash "$ROOT/tools/dev.sh" cmd state "$id" >/dev/null
i=0
until adb -s $DEV logcat -d -s MemoryPalace:V 2>/dev/null | grep -q "CMD state done $id " || [ $i -ge 60 ]; do sleep 0.5; i=$((i+1)); done
for c in "$@"; do
  # "cmd [word] [numbers]": the word goes to s=, the numbers to a=
  read -r name w1 w2 <<<"$c"
  word=""; nums=""
  for tok in "$w1" "$w2"; do
    [ -z "$tok" ] && continue
    if [[ "$tok" =~ ^[0-9.,-]+$ ]]; then nums="$tok"; else word="$tok"; fi
  done
  bash "$ROOT/tools/dev.sh" cmd "$name" "$word" "$nums" >/dev/null
  sleep 1
done
# panels lag 3-10 s behind a state change on the emulator (factory QA): wait 10 s by default
sleep "${SETTLE:-10}"
bash "$ROOT/tools/cap.sh" "$id"
adb -s $DEV logcat -d -s MemoryPalace:V 2>/dev/null | grep -E "CMD state done|STATE" | tail -3 | cut -c1-200
