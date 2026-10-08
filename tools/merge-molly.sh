#!/usr/bin/env bash
# Copyright 2026 Cole Munz
# SPDX-License-Identifier: AGPL-3.0-only
#
# Merge molly/main into the current branch and re-apply the Wren rebrand.
# Usage: tools/merge-molly.sh
set -euo pipefail

MOLLY_URL=https://github.com/mollyim/mollyim-android.git

die() {
  echo "error: $*" >&2
  exit 2
}

root=$(git rev-parse --show-toplevel)
cd "$root"

[[ -f "$(git rev-parse --git-path MERGE_HEAD)" ]] && die "a merge is already in progress"
[[ -z $(git status --porcelain) ]] || die "working tree is not clean, commit or stash first"

if ! git remote get-url molly > /dev/null 2>&1; then
  echo "adding remote molly -> $MOLLY_URL"
  git remote add molly "$MOLLY_URL"
fi

git config merge.ours.driver true
git config merge.theirs.driver 'cp -- %B %A'
git fetch --no-tags molly main
target=$(git rev-parse --short=12 refs/remotes/molly/main)

if git merge-base --is-ancestor refs/remotes/molly/main HEAD; then
  echo "already up to date with molly/main ($target)"
  exit 0
fi

echo "merging molly/main ($target) into $(git branch --show-current)"
merge_rc=0
git merge --no-commit --no-ff refs/remotes/molly/main || merge_rc=$?

python3 tools/rebrand.py

if [[ $merge_rc -eq 0 ]]; then
  git add -A
  git commit -q -m "Merge molly/main at $target"
  echo "merged cleanly and committed: $(git rev-parse --short HEAD)"
  exit 0
fi

translations=()
code=()
other=()
while IFS= read -r path; do
  case $path in
    */res/values*/*.xml) translations+=("$path") ;;
    *.kt | *.java | *.kts) code+=("$path") ;;
    *) other+=("$path") ;;
  esac
done < <(git diff --name-only --diff-filter=U)

print_group() {
  local title=$1
  shift
  echo
  echo "$title ($#)"
  for path in "$@"; do
    echo "  $path"
  done
}

echo
echo "conflicts in molly/main ($target):"
print_group "translations" "${translations[@]}"
print_group "kotlin-java" "${code[@]}"
print_group "other" "${other[@]}"

cat <<'EOF'

The merge is left uncommitted. To finish:
  1. Fix each file above (translations: usually keep molly's side, rebrand fixes the name).
  2. python3 tools/rebrand.py
  3. git add -A && git commit
To back out: git merge --abort
EOF
exit 1
