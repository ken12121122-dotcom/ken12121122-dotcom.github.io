#!/usr/bin/env bash
# Reassemble anna_ipati_animated.glb from its parts and verify the result.
#
# The model is 344 MB. GitHub hard-rejects any single file over 100 MiB on a
# normal push, and both alternatives were unavailable: release-asset upload is
# blocked for the session that committed this, and Git LFS could not reach its
# storage host. So the file is committed as four plain parts instead.
set -euo pipefail

cd "$(dirname "$0")"

OUT=anna_ipati_animated.glb
WANT=0eb5a6bd0fce27759bcfcea1ac894063f5431bade4742e12e4a80212b2b18aab

shopt -s nullglob
parts=("$OUT".part*)
if [ ${#parts[@]} -eq 0 ]; then
  echo "no $OUT.part* files found in $PWD" >&2
  exit 1
fi

# Sort so part10 would follow part9 rather than part1, in case the split ever grows.
mapfile -t parts < <(printf '%s\n' "${parts[@]}" | sort -V)
echo "joining ${#parts[@]} parts -> $OUT"
cat "${parts[@]}" > "$OUT"

got=$(sha256sum "$OUT" | cut -d' ' -f1)
if [ "$got" != "$WANT" ]; then
  echo "CHECKSUM MISMATCH" >&2
  echo "  expected $WANT" >&2
  echo "  got      $got" >&2
  echo "The parts are incomplete or corrupt. A likely cause is a partial clone." >&2
  exit 1
fi

echo "ok  $OUT  $(wc -c < "$OUT") bytes  sha256 $got"
