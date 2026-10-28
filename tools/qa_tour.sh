#!/usr/bin/env bash
# All design states in one go: tools/qa_tour.sh [S01 S05 ...]  -> .scratch/shots/<id>.png + tour.png + tour.txt
# Each state: `state <id>`, wait, console screenshot, then `dump` to check the reached designState.
set -u
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
DEV=${DEV:-emulator-5564}
ids=("$@")
[ ${#ids[@]} -eq 0 ] && ids=(S01 S02 S03 S04 S04B S05 S06 S07 S08 S09 S10 S11 S12 S13 S14 S15 S15W S16 S17 S18 S19 S20 S21 S22 S23 S24 S25 S26 S27 S28 S28B S29 S30 S30B S31)
bash "$ROOT/tools/dev.sh" cmd hold on >/dev/null
out="$ROOT/.scratch/shots/tour.txt"
mkdir -p "$ROOT/.scratch/shots"
: > "$out"
for id in "${ids[@]}"; do
  SETTLE=${SETTLE:-10} bash "$ROOT/tools/qa_shot.sh" "$id" >/dev/null 2>&1
  bash "$ROOT/tools/dev.sh" cmd dump >/dev/null
  sleep 1
  got=$(adb -s $DEV logcat -d -s MemoryPalace:V 2>/dev/null | grep "DUMP" | tail -1 | sed -E 's/.*"designState":"([^"]+)".*/\1/')
  echo "$id reached=$got" | tee -a "$out"
done
python "$ROOT/tools/contact.py" "$ROOT/.scratch/shots" "${ids[@]}"
