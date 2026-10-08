#!/usr/bin/env bash
# Copyright 2026 Cole Munz
# SPDX-License-Identifier: AGPL-3.0-only
#
# Prints what matters about each APK and checks it: size, SDK levels, native
# libraries, baseline profile, 16 KB page size alignment and the signature.
# Exits non-zero if any check fails. An unsigned APK is reported, not failed,
# unless --require-signed is given.
# Usage: tools/apk-report.sh [--require-signed] <apk>...
set -uo pipefail

require_signed=0
if [[ ${1:-} == --require-signed ]]; then
  require_signed=1
  shift
fi
if [[ $# -eq 0 ]]; then
  echo "usage: tools/apk-report.sh [--require-signed] <apk>..." >&2
  exit 2
fi

sdk=${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}}
tools_dir=${BUILD_TOOLS_DIR:-}
if [[ -z $tools_dir ]]; then
  if [[ -d $sdk/build-tools/36.0.0 ]]; then
    tools_dir=$sdk/build-tools/36.0.0
  else
    tools_dir=$(find "$sdk/build-tools" -mindepth 1 -maxdepth 1 -type d 2> /dev/null | sort -V | tail -1)
  fi
fi
aapt2=$tools_dir/aapt2
zipalign=$tools_dir/zipalign
apksigner=$tools_dir/apksigner
readelf=$(command -v readelf || command -v llvm-readelf || true)
for t in "$aapt2" "$zipalign" "$apksigner"; do
  [[ -x $t ]] || { echo "missing build tool: $t (set ANDROID_HOME or BUILD_TOOLS_DIR)" >&2; exit 2; }
done
[[ -n $readelf ]] || { echo "readelf not found" >&2; exit 2; }
command -v unzip > /dev/null || { echo "unzip not found" >&2; exit 2; }

work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT

failures=0
fail() {
  echo "  FAIL  $*"
  failures=$((failures + 1))
}
ok() {
  echo "  ok    $*"
}

human() {
  numfmt --to=iec --suffix=B "$1" 2> /dev/null || echo "$1 B"
}

# LOAD segments of 64-bit libraries need 16 KB alignment (0x4000 or more).
# 32-bit ARM runs in 4 KB page mode, so it is reported but not enforced.
elf_check() {
  local dir=$1 bad=0 checked=0 skipped=0 so abi class aligns a
  while IFS= read -r so; do
    abi=$(basename "$(dirname "$so")")
    class=$("$readelf" -h "$so" 2> /dev/null | sed -n 's/^ *Class: *//p')
    if [[ $class != ELF64 ]]; then
      skipped=$((skipped + 1))
      continue
    fi
    checked=$((checked + 1))
    aligns=$("$readelf" -lW "$so" 2> /dev/null | awk '$1 == "LOAD" { print $NF }')
    if [[ -z $aligns ]]; then
      fail "$abi/$(basename "$so"): no LOAD segments found"
      bad=$((bad + 1))
      continue
    fi
    for a in $aligns; do
      if (($a < 0x4000)); then
        fail "$abi/$(basename "$so"): LOAD segment aligned to $a, needs 0x4000"
        bad=$((bad + 1))
        break
      fi
    done
  done < <(find "$dir" -name '*.so' -path '*/lib/*' | sort)
  if [[ $bad -eq 0 ]]; then
    ok "ELF LOAD alignment >= 16 KB on $checked 64-bit libraries ($skipped 32-bit skipped)"
  fi
}

report() {
  local apk=$1 badging size sdkmin sdktarget pkg ver code abis debuggable
  echo "== $apk"
  if [[ ! -f $apk ]]; then
    fail "not a file"
    return
  fi
  if ! badging=$("$aapt2" dump badging "$apk" 2>&1); then
    fail "aapt2 dump badging: ${badging:0:200}"
    return
  fi

  size=$(stat -c %s "$apk")
  pkg=$(sed -n "s/^package: name='\([^']*\)'.*/\1/p" <<< "$badging")
  code=$(sed -n "s/^package:.*versionCode='\([^']*\)'.*/\1/p" <<< "$badging")
  ver=$(sed -n "s/^package:.*versionName='\([^']*\)'.*/\1/p" <<< "$badging")
  sdkmin=$(sed -n "s/^minSdkVersion:'\([0-9]*\)'.*/\1/p" <<< "$badging")
  sdktarget=$(sed -n "s/^targetSdkVersion:'\([0-9]*\)'.*/\1/p" <<< "$badging")
  abis=$(sed -n "s/^native-code: *//p" <<< "$badging" | tr -d "'")
  debuggable=0
  grep -q "^application-debuggable" <<< "$badging" && debuggable=1

  echo "  package   $pkg $ver (versionCode $code)"
  echo "  size      $(human "$size") ($size bytes)"
  echo "  sdk       minSdk $sdkmin, targetSdk $sdktarget"
  echo "  abis      ${abis:-none}"

  local entries libs
  entries=$(unzip -Z1 "$apk" 2> /dev/null)
  libs=$(grep -E '^lib/[^/]+/[^/]+\.so$' <<< "$entries" | sort)
  if [[ -n $libs ]]; then
    local abi
    for abi in $(sed -E 's#^lib/([^/]+)/.*#\1#' <<< "$libs" | sort -u); do
      echo "  libs/$abi  $(grep "^lib/$abi/" <<< "$libs" | sed -E 's#.*/##' | tr '\n' ' ')"
      grep -qx "lib/$abi/libsignal_jni.so" <<< "$libs" || fail "lib/$abi has no libsignal_jni.so, devices of that ABI would crash at startup"
    done
    local methods
    methods=$(unzip -Z "$apk" 'lib/*' 2> /dev/null | awk '$1 ~ /^[-d]/ { print ($6 ~ /^stor/ ? "stored" : "compressed") }' | sort | uniq -c | tr '\n' ' ')
    echo "  lib zip   $methods"
  else
    echo "  libs      none"
  fi

  if grep -qx 'assets/dexopt/baseline.prof' <<< "$entries"; then
    ok "baseline profile present (assets/dexopt/baseline.prof)"
  elif [[ $debuggable -eq 1 ]]; then
    echo "  --    no baseline profile (debuggable build)"
  else
    fail "baseline profile missing (assets/dexopt/baseline.prof)"
  fi

  local za
  if za=$("$zipalign" -c -P 16 -v 4 "$apk" 2>&1); then
    ok "zipalign -P 16 -v 4"
  else
    fail "zipalign -P 16 -v 4: $(grep -m3 -i 'bad\|mis' <<< "$za" | tr '\n' ';')"
  fi

  if [[ -n $libs ]]; then
    local dir=$work/$(basename "$apk")
    mkdir -p "$dir"
    unzip -q -o "$apk" 'lib/*' -d "$dir"
    elf_check "$dir"
    rm -rf "$dir"
  fi

  local sig
  if sig=$("$apksigner" verify --verbose --print-certs "$apk" 2>&1); then
    local schemes cert
    schemes=$(sed -n 's/^Verified using \(v[0-9.]*\) .*: true/\1/p' <<< "$sig" | tr '\n' ' ')
    cert=$(sed -n 's/^Signer #1 certificate SHA-256 digest: //p' <<< "$sig")
    ok "signature verified (${schemes% }), cert sha256 ${cert:0:16}"
  elif grep -q 'Missing META-INF/MANIFEST.MF\|No JAR signatures\|not signed\|Missing.*signature' <<< "$sig"; then
    if [[ $require_signed -eq 1 ]]; then
      fail "unsigned"
    else
      echo "  --    unsigned"
    fi
  else
    fail "apksigner verify: $(head -c 200 <<< "$sig" | tr '\n' ' ')"
  fi
}

for apk in "$@"; do
  report "$apk"
  echo
done

if [[ $failures -gt 0 ]]; then
  echo "$failures check(s) failed"
  exit 1
fi
echo "all checks passed"
