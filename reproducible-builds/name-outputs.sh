#!/usr/bin/env bash
# Copyright 2026 Cole Munz
# SPDX-License-Identifier: AGPL-3.0-only
#
# Copies the release APKs from outputs/ into <dest> under the asset names
# Wren publishes: Wren-<tag>.apk and Wren-unsigned-<tag>.apk for prodStore,
# Wren-website-<tag>.apk and Wren-website-unsigned-<tag>.apk for prodWebsite.
# Usage: name-outputs.sh <tag> <dest>
set -euo pipefail

tag=${1:?usage: name-outputs.sh <tag> <dest>}
dest=${2:?usage: name-outputs.sh <tag> <dest>}
base=${CI_APP_FILENAME:-Wren}
here=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)

mkdir -p "$dest"
count=0
for apk in "$here"/outputs/apk/*/release/*.apk; do
  [[ -e $apk ]] || continue
  flavor=$(basename "$(dirname "$(dirname "$apk")")")
  case $flavor in
    prodStore) infix="" ;;
    prodWebsite) infix="-website" ;;
    stagingWebsite) infix="-staging" ;;
    *) echo "unknown flavor directory: $flavor" >&2; exit 1 ;;
  esac
  unsigned=""
  [[ $apk == *unsigned* ]] && unsigned="-unsigned"
  cp -v "$apk" "$dest/${base}${infix}${unsigned}-${tag}.apk"
  count=$((count + 1))
done

if [[ $count -eq 0 ]]; then
  echo "no release APKs found under $here/outputs/apk" >&2
  exit 1
fi
