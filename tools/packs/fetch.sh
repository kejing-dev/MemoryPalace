#!/usr/bin/env bash
# Download the source datasets into .cache/src and create the venv. Idempotent.
set -euo pipefail
cd "$(dirname "$0")"
mkdir -p .cache/src/ci
B=https://raw.githubusercontent.com/chinese-poetry/chinese-poetry/master
get() { [ -s "$2" ] || curl -sfL "$1" -o "$2"; }
get "$B/LICENSE" .cache/src/LICENSE
get "$B/%E5%85%A8%E5%94%90%E8%AF%97/%E5%94%90%E8%AF%97%E4%B8%89%E7%99%BE%E9%A6%96.json" ".cache/src/全唐诗_唐诗三百首.json"
get "$B/%E5%AE%8B%E8%AF%8D/%E5%AE%8B%E8%AF%8D%E4%B8%89%E7%99%BE%E9%A6%96.json" ".cache/src/宋词_宋词三百首.json"
get "$B/%E8%92%99%E5%AD%A6/tangshisanbaishou.json" ".cache/src/蒙学_tangshisanbaishou.json"
get "$B/%E8%92%99%E5%AD%A6/sanzijing-traditional.json" ".cache/src/蒙学_sanzijing-traditional.json"
get "$B/%E8%92%99%E5%AD%A6/qianziwen.json" ".cache/src/蒙学_qianziwen.json"
get "$B/%E8%AE%BA%E8%AF%AD/lunyu.json" ".cache/src/论语_lunyu.json"
for n in 0 1000 2000 3000 4000 5000 6000 7000 8000 9000 10000 11000 12000 13000 14000 15000 16000 17000 18000 19000 20000 21000 2019y; do
  get "$B/%E5%AE%8B%E8%AF%8D/ci.song.$n.json" ".cache/src/ci/ci.song.$n.json"
done
get https://raw.githubusercontent.com/skywind3000/ECDICT/master/ecdict.csv .cache/src/ecdict.csv
get https://raw.githubusercontent.com/skywind3000/ECDICT/master/LICENSE .cache/src/ECDICT_LICENSE
if [ ! -x .cache/venv/Scripts/python.exe ] && [ ! -x .cache/venv/bin/python ]; then
  python -I -m venv .cache/venv
fi
PY=.cache/venv/Scripts/python.exe; [ -x "$PY" ] || PY=.cache/venv/bin/python
"$PY" -I -m pip install -q opencc-python-reimplemented mpmath
echo "sources ready; run: $PY -I build.py && $PY -I validate.py"
