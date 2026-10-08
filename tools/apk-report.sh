#!/usr/bin/env bash
# Copyright 2026 Cole Munz
# SPDX-License-Identifier: AGPL-3.0-only
#
# Prints what matters about each APK and checks it: size, SDK levels, native
# libraries, baseline profile, 16 KB page size alignment, the signature and
# whether any Google services code is inside. Exits non-zero if any check
# fails. An unsigned APK is reported, not failed, unless --require-signed is
# given. --allow-fcm reports Firebase and Play Services code instead of
# failing on it, for a build made with -PwrenFcm=true. --known-4k=a.so,b.so
# reports those libraries as warnings when they miss the 16 KB alignment,
# for prebuilt libraries that can't be rebuilt yet; every other library and
# every other check still fails.
# Usage: tools/apk-report.sh [--require-signed] [--allow-fcm] [--known-4k=lib.so,...] <apk>...
set -uo pipefail

require_signed=0
allow_fcm=0
known_4k=,
while [[ ${1:-} == --* ]]; do
  case $1 in
    --require-signed) require_signed=1 ;;
    --allow-fcm) allow_fcm=1 ;;
    --known-4k=*)
      list=${1#--known-4k=}
      if [[ ! $list =~ ^[A-Za-z0-9._+-]+\.so(,[A-Za-z0-9._+-]+\.so)*$ ]]; then
        echo "--known-4k wants a comma-separated list of .so names, got: $list" >&2
        exit 2
      fi
      known_4k=,$list,
      ;;
    *) echo "unknown option: $1" >&2; exit 2 ;;
  esac
  shift
done
if [[ $# -eq 0 ]]; then
  echo "usage: tools/apk-report.sh [--require-signed] [--allow-fcm] [--known-4k=lib.so,...] <apk>..." >&2
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
command -v python3 > /dev/null || { echo "python3 not found" >&2; exit 2; }

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
  local dir=$1 bad=0 warned=0 checked=0 skipped=0 so abi class aligns a name
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
    name=$(basename "$so")
    for a in $aligns; do
      if (($a < 0x4000)); then
        if [[ $known_4k == *,"$name",* ]]; then
          echo "  warn  $abi/$name: LOAD segment aligned to $a, needs 0x4000 (known, --known-4k)"
          warned=$((warned + 1))
        else
          fail "$abi/$name: LOAD segment aligned to $a, needs 0x4000"
          bad=$((bad + 1))
        fi
        break
      fi
    done
  done < <(find "$dir" -name '*.so' -path '*/lib/*' | sort)
  if [[ $bad -eq 0 ]]; then
    ok "ELF LOAD alignment >= 16 KB on $((checked - warned)) of $checked 64-bit libraries, $warned known exceptions ($skipped 32-bit skipped)"
  fi
}

# Reads every dex file of the APK and prints one line per finding:
#   BAD <text>    Google services code, fails the check
#   OK <text>     expected and harmless
#   INFO <text>   worth knowing, not a failure
# Class names come from the dex type table and hosts from its string table, so
# the result does not depend on how R8 treated the code.
dex_google_scan() {
  python3 -I - "$1" << 'PY'
import re
import struct
import sys
import zipfile

SHIM_GMS = (
    "com/google/android/gms/common/",
    "com/google/android/gms/tasks/",
    "com/google/android/gms/stats/",
    "com/google/android/gms/security/",
    "com/google/android/gms/maps/",
    "com/google/android/gms/R",
    "com/google/android/gms/BuildConfig",
)
REAL_GMS_UNDER_SHIM = re.compile(r"/zz?[a-z]{1,4}(\$|;|$)")
REAL_GMS_API = "com/google/android/gms/common/api/"
SHIM_GMS_API = "com/google/android/gms/common/api/internal/BackgroundDetector"
OPEN_SOURCE = {
    "com/google/common": "Guava",
    "com/google/thirdparty": "Guava",
    "com/google/gson": "Gson",
    "com/google/protobuf": "Protobuf",
    "com/google/crypto/tink": "Tink",
    "com/google/zxing": "ZXing",
    "com/google/i18n/phonenumbers": "libphonenumber",
    "com/google/android/material": "Material Components",
    "com/google/android/flexbox": "Flexbox",
    "com/google/accompanist": "Accompanist",
    "com/google/auto": "AutoValue",
    "com/google/errorprone": "Error Prone annotations",
    "com/google/j2objc": "J2ObjC annotations",
    "com/google/flatbuffers": "FlatBuffers",
    "com/google/api/client": "Google HTTP client (pulled in by Tink)",
}
SERVICES = (
    ("com/google/firebase", "Firebase client"),
    ("com/google/android/datatransport", "Firebase transport (talks to Google logging)"),
    ("com/google/mlkit", "ML Kit"),
    ("com/google/android/play", "Play Core / Play Billing"),
    ("com/google/android/libraries", "Google libraries"),
    ("com/google/android/apps", "Google apps SDK"),
    ("com/google/android/recaptcha", "reCAPTCHA"),
    ("com/google/android/gms", "Play Services client"),
)
BAD_HOST = re.compile(
    r"(^|\.)(firebaseio\.com|firebaseinstallations\.googleapis\.com|"
    r"firebase[a-z-]*\.googleapis\.com|crashlytics\.com|app-measurement\.com|"
    r"google-analytics\.com|googletagmanager\.com|gstatic\.com|"
    r"play\.googleapis\.com|android\.googleapis\.com|www\.googleapis\.com|"
    r"fcm\.googleapis\.com|mtalk\.google\.com|"
    r"firebase\.google\.com|firebase\.googleapis\.com)$"
)
GOOGLE_HOST = re.compile(r"(^|\.)(google(\.[a-z]{2,3}){1,2}|googleapis\.com|googlemail\.com|goo\.gle|gstatic\.com)$")
URL = re.compile(r"https?://([A-Za-z0-9][A-Za-z0-9.-]*)")
BARE = re.compile(
    r"\b((?:[a-z0-9-]+\.)*(?:googleapis\.com|firebaseio\.com|app-measurement\.com|"
    r"crashlytics\.com|google-analytics\.com|gstatic\.com|firebase\.google\.com))\b"
)


def uleb(b, p):
    r = 0
    s = 0
    while True:
        c = b[p]
        p += 1
        r |= (c & 0x7F) << s
        if not c & 0x80:
            return r, p
        s += 7


def read_dex(data):
    if data[:4] != b"dex\n":
        return
    ssize, soff, tsize, toff = struct.unpack_from("<IIII", data, 0x38)
    sid = struct.unpack_from("<%dI" % ssize, data, soff)
    tid = struct.unpack_from("<%dI" % tsize, data, toff)
    wanted = set(tid)

    def string(i):
        p = sid[i]
        _, p = uleb(data, p)
        return data[p:data.index(b"\0", p)].decode("utf-8", "replace")

    for i in range(ssize):
        s = string(i)
        yield (i in wanted), s


classes = set()
hosts = {}
with zipfile.ZipFile(sys.argv[1]) as z:
    for name in z.namelist():
        if re.fullmatch(r"classes\d*\.dex", name):
            for is_type, s in read_dex(z.read(name)):
                if is_type:
                    if s.startswith("Lcom/google/") or s.startswith("Lcom/android/gms"):
                        classes.add(s[1:])
                else:
                    if "http" in s:
                        for m in URL.finditer(s):
                            hosts[m.group(1).lower().rstrip(".")] = True
                    if "google" in s or "firebase" in s or "crashlytics" in s or "measurement" in s:
                        for m in BARE.finditer(s):
                            hosts[m.group(1)] = True

bad = {}
shim = 0
oss = {}
unknown = {}
for c in sorted(classes):
    if c.startswith("com/google/android/gms"):
        if c.startswith(SHIM_GMS) and not REAL_GMS_UNDER_SHIM.search(c):
            if c.startswith(REAL_GMS_API) and not c.startswith(SHIM_GMS_API):
                bad.setdefault("Play Services client", []).append(c)
            else:
                shim += 1
            continue
        bad.setdefault("Play Services client", []).append(c)
        continue
    hit = False
    for prefix, label in SERVICES:
        if c.startswith(prefix + "/"):
            bad.setdefault(label, []).append(c)
            hit = True
            break
    if hit:
        continue
    for prefix, label in OPEN_SOURCE.items():
        if c.startswith(prefix + "/"):
            oss[label] = oss.get(label, 0) + 1
            hit = True
            break
    if not hit:
        unknown.setdefault("/".join(c.split("/")[:4]), []).append(c)

for label, names in sorted(bad.items()):
    print("BAD %s: %d classes, e.g. %s" % (label, len(names), names[0].replace("/", ".")))
for pkg, names in sorted(unknown.items()):
    print("BAD unclassified com.google package %s (%d classes), review it and add it to the list in tools/apk-report.sh" % (pkg.replace("/", "."), len(names)))
if shim:
    print("OK %d classes from the core-gms shims and the OpenStreetMap maps stand-in" % shim)
if oss:
    print("OK open-source libraries: " + ", ".join("%s %d" % kv for kv in sorted(oss.items())))

bad_hosts = sorted(h for h in hosts if BAD_HOST.search(h))
other = sorted(h for h in hosts if GOOGLE_HOST.search(h) and h not in bad_hosts and h != "type.googleapis.com")
for h in bad_hosts:
    print("BAD endpoint %s" % h)
if other:
    print("INFO other Google hosts in strings: " + ", ".join(other))
PY
}

# Counts Firebase and Google Cloud Messaging entries in the manifest and the
# Firebase project values in the resource table.
manifest_google_scan() {
  local apk=$1 tree res hits
  tree=$("$aapt2" dump xmltree --file AndroidManifest.xml "$apk" 2>&1) || { echo "BAD aapt2 dump xmltree failed"; return; }
  hits=$(grep -oE 'com\.google\.firebase[A-Za-z0-9_.]*|com\.google\.android\.c2dm[A-Za-z0-9_.]*|com\.google\.android\.datatransport[A-Za-z0-9_.]*|com\.google\.android\.gms\.cloudmessaging[A-Za-z0-9_.]*|firebase_[a-z_]+' <<< "$tree" | sort -u)
  if [[ -n $hits ]]; then
    echo "BAD manifest declares $(wc -l <<< "$hits") Firebase or FCM entries, e.g. $(head -3 <<< "$hits" | tr '\n' ' ')"
  else
    echo "OK manifest has no Firebase, FCM or c2dm entries"
  fi
  res=$("$aapt2" dump resources "$apk" 2>&1 | grep -oE 'string/(google_app_id|gcm_defaultSenderId|google_api_key|google_crash_reporting_api_key|firebase_database_url|default_web_client_id)' | sort -u | tr '\n' ' ')
  if [[ -n $res ]]; then
    echo "BAD resources carry a Firebase project: ${res% }"
  else
    echo "OK resources carry no Firebase project id or API key"
  fi
}

google_check() {
  local apk=$1 line bad=0 out
  out=$({ dex_google_scan "$apk"; manifest_google_scan "$apk"; } 2>&1)
  while IFS= read -r line; do
    case $line in
      BAD\ *)
        bad=$((bad + 1))
        if [[ $allow_fcm -eq 1 ]]; then
          echo "  --    ${line#BAD } (allowed by --allow-fcm)"
        else
          fail "google: ${line#BAD }"
        fi
        ;;
      OK\ *) ok "google: ${line#OK }" ;;
      INFO\ *) echo "  --    google: ${line#INFO }" ;;
      "") ;;
      *) fail "google scan: ${line:0:200}" ;;
    esac
  done <<< "$out"
  if [[ $bad -eq 0 ]]; then
    ok "google: no Play Services, Firebase or FCM code in the APK"
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

  google_check "$apk"

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
