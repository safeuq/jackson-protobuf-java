#!/usr/bin/env bash
#
# release.sh — release automation for Gradle projects that declare their version
# as version = "X.Y.Z-SNAPSHOT" in the root build.gradle.kts.
#
# Two subcommands, so that publishing only ever happens from main:
#
#   release.sh prepare
#     1. stash tracked WIP (untracked files left in place)
#     2. fresh main (checkout + pull)
#     3. pre-flight build (gradle clean jar) — validate before any release commit
#     4. create release branch
#     5. set release version + commit
#     6. bump back to dev snapshot + commit
#     7. push
#     8. open PR (create only) — prints the URL
#   Restores your workspace on exit (success or failure). The PR is reviewed and
#   merged by a human.
#
#   release.sh publish
#     1. stash tracked WIP (untracked files left in place)
#     2. fetch origin, checkout the latest "Release version" commit on
#        origin/main in DETACHED HEAD
#     3. gradle publish
#   Restores your workspace on exit (success or failure).
#
set -euo pipefail

# ---- config -----------------------------------------------------------------
BUILD_FILE="build.gradle.kts"
BASE_BRANCH="main"
REMOTE="origin"

# ---- output helpers ---------------------------------------------------------
if [ -t 1 ]; then
  BOLD=$'\033[1m'; CYAN=$'\033[36m'; GREEN=$'\033[32m'; DIM=$'\033[2m'; RESET=$'\033[0m'
else
  BOLD=; CYAN=; GREEN=; DIM=; RESET=
fi

# prominent step banner — easy to spot between gradle/git noise
step() {
  printf '\n%s%s━━━ %s %s%s\n' \
    "$BOLD" "$CYAN" "$*" "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" "$RESET"
}

die() { printf '\n%serror: %s%s\n' "$BOLD" "$*" "$RESET" >&2; exit 1; }

usage() {
  sed -n '2,/^set -euo/p' "$0" | grep '^#' | sed 's/^# \{0,1\}//'
  exit "${1:-0}"
}

# confirm before mutating anything; abort cleanly otherwise
confirm() {
  if [ -t 0 ]; then
    printf '\n%sProceed? [y/N] %s' "$BOLD" "$RESET"
    read -r reply
    case "$reply" in
      y|Y|yes|YES) ;;
      *) echo "aborted."; exit 0 ;;
    esac
  else
    die "no TTY for confirmation — run interactively"
  fi
}

# run ./gradlew quietly: capture output, print it only on failure
gradle_quiet() {
  local log; log="$(mktemp -t release-gw.XXXXXX)"
  echo "${DIM}(output suppressed unless it fails)${RESET}"
  if ! ./gradlew "$@" >"$log" 2>&1; then
    echo "${BOLD}gradle $* failed — full output:${RESET}" >&2
    cat "$log" >&2
    rm -f "$log"
    exit 1
  fi
  rm -f "$log"
}

# extract X.Y.Z[-SNAPSHOT] from a build.gradle.kts on stdin
parse_version() {
  grep -oE 'version = "[0-9]+\.[0-9]+\.[0-9]+(-SNAPSHOT)?"' | head -1 \
    | sed -E 's/version = "(.*)"/\1/'
}

set_version() {
  local old="$1" new="$2"
  sed -i.bak "s|version = \"${old}\"|version = \"${new}\"|" "$BUILD_FILE"
  rm -f "${BUILD_FILE}.bak"
}

# ---- workspace restore (armed before the first mutation) --------------------
ORIG_BRANCH=""
STASH_MADE=0
cleanup() {
  git checkout -- "$BUILD_FILE" 2>/dev/null || true   # drop uncommitted version edit
  if [ -n "$ORIG_BRANCH" ] && [ "$(git rev-parse --abbrev-ref HEAD)" != "$ORIG_BRANCH" ]; then
    git checkout "$ORIG_BRANCH" 2>/dev/null || true
  fi
  if [ "$STASH_MADE" = 1 ]; then
    git stash pop 2>/dev/null || true
  fi
}

do_stash() {
  if ! git diff --quiet || ! git diff --cached --quiet; then
    step "Stashing local changes"
    git stash push -m "release.sh wip" >/dev/null
    STASH_MADE=1
  fi
}

common_preflight() {
  git rev-parse --is-inside-work-tree >/dev/null 2>&1 || die "not inside a git repository"
  cd "$(git rev-parse --show-toplevel)"   # run from repo root regardless of cwd
  [ -f "$BUILD_FILE" ] || die "$BUILD_FILE not found at repo root"
  [ -x "./gradlew" ] || die "./gradlew not found or not executable"
  git remote get-url "$REMOTE" >/dev/null 2>&1 || die "git remote '$REMOTE' not configured"
  ORIG_BRANCH="$(git rev-parse --abbrev-ref HEAD)"
}

# =============================================================================
# prepare — build the release/back-to-dev commits and open a PR
# =============================================================================
cmd_prepare() {
  [ $# -eq 0 ] || die "prepare takes no arguments"

  common_preflight
  command -v gh >/dev/null 2>&1 || die "gh (GitHub CLI) not installed"
  gh auth status >/dev/null 2>&1 || die "gh not authenticated — run 'gh auth login'"

  # read the version straight from origin/main (source of truth) before mutating
  git fetch --quiet "$REMOTE" "$BASE_BRANCH"
  local CURRENT
  CURRENT="$(git show "${REMOTE}/${BASE_BRANCH}:${BUILD_FILE}" | parse_version)"
  [ -n "$CURRENT" ] || die "no version found in ${REMOTE}/${BASE_BRANCH}:${BUILD_FILE}"
  case "$CURRENT" in
    *-SNAPSHOT) ;;
    *) die "${REMOTE}/${BASE_BRANCH} version is '$CURRENT', expected a -SNAPSHOT" ;;
  esac

  local RELEASE PREFIX PATCH NEXT RELEASE_BRANCH
  RELEASE="${CURRENT%-SNAPSHOT}"
  PREFIX="${RELEASE%.*}"
  PATCH="${RELEASE##*.}"
  NEXT="${PREFIX}.$((PATCH + 1))-SNAPSHOT"
  RELEASE_BRANCH="release/${RELEASE}"

  git rev-parse --verify --quiet "refs/heads/${RELEASE_BRANCH}" >/dev/null \
    && die "branch '${RELEASE_BRANCH}' already exists locally"
  git ls-remote --exit-code --heads "$REMOTE" "$RELEASE_BRANCH" >/dev/null 2>&1 \
    && die "branch '${RELEASE_BRANCH}' already exists on '${REMOTE}'"

  echo ">> command:       prepare"
  echo ">> current:       $CURRENT"
  echo ">> release:       $RELEASE"
  echo ">> next snapshot: $NEXT"
  echo ">> release branch: $RELEASE_BRANCH"
  echo ">> origin branch: $ORIG_BRANCH"
  confirm

  trap cleanup EXIT

  # 1. stash tracked WIP (untracked files left in place)
  do_stash

  # 2. fresh main
  step "Updating ${BASE_BRANCH}"
  git checkout "$BASE_BRANCH"
  git pull --ff-only "$REMOTE" "$BASE_BRANCH"

  # 3. pre-flight build — validate before any release commit
  step "Pre-flight build (gradle clean jar)"
  gradle_quiet clean jar

  # 4. release branch
  step "Creating branch ${RELEASE_BRANCH}"
  git checkout -b "$RELEASE_BRANCH"

  # 5. release version + commit
  step "Committing release ${RELEASE}"
  set_version "$CURRENT" "$RELEASE"
  git commit -am "Release version ${RELEASE}"

  # 6. back to development
  step "Committing back-to-dev ${NEXT}"
  set_version "$RELEASE" "$NEXT"
  git commit -am "BTD ${NEXT}"

  # 7. push
  step "Pushing ${RELEASE_BRANCH}"
  git push -u "$REMOTE" "$RELEASE_BRANCH"

  # 8. PR (create only) — capture the URL it prints
  step "Opening pull request"
  local PR_URL
  PR_URL="$(gh pr create \
    --base "$BASE_BRANCH" \
    --head "$RELEASE_BRANCH" \
    --title "Release version ${RELEASE}" \
    --body "$(printf 'Release %s, bump back to %s.\n\n- Release version %s\n- BTD %s\n' \
      "$RELEASE" "$NEXT" "$RELEASE" "$NEXT")")"

  printf '\n%s%s✓ Done — PR opened for %s%s\n' "$BOLD" "$GREEN" "$RELEASE_BRANCH" "$RESET"
  printf '%s%s  %s%s\n' "$BOLD" "$CYAN" "$PR_URL" "$RESET"
  # trap restores original branch + stash on exit
}

# =============================================================================
# publish — publish the merged release commit from main (detached)
# =============================================================================
cmd_publish() {
  [ $# -eq 0 ] || die "publish takes no arguments"

  common_preflight

  # find the latest "Release version" commit on origin/main
  git fetch --quiet "$REMOTE" "$BASE_BRANCH"
  local RELEASE_SHA RELEASE_SUBJECT RELEASE_VER
  RELEASE_SHA="$(git log "${REMOTE}/${BASE_BRANCH}" --grep='Release version' --format='%H' -n1)"
  [ -n "$RELEASE_SHA" ] || die "no 'Release version' commit found on ${REMOTE}/${BASE_BRANCH}"
  RELEASE_SUBJECT="$(git log -n1 --format='%s' "$RELEASE_SHA")"
  RELEASE_VER="$(git show "${RELEASE_SHA}:${BUILD_FILE}" | parse_version)"

  echo ">> command:      publish"
  echo ">> source:       ${REMOTE}/${BASE_BRANCH}"
  echo ">> commit:       ${RELEASE_SHA:0:12}  ${RELEASE_SUBJECT}"
  echo ">> version:      ${RELEASE_VER}"
  echo ">> origin branch: ${ORIG_BRANCH}"
  confirm

  trap cleanup EXIT

  # 1. stash tracked WIP (untracked files left in place)
  do_stash

  # 2. switch to the release commit in detached HEAD
  step "Checking out release commit ${RELEASE_SHA:0:12} (detached)"
  git checkout --detach "$RELEASE_SHA"

  # 3. publish (fails here => trap restores workspace)
  step "Publishing ${RELEASE_VER} (gradle publish)"
  ./gradlew publish

  printf '\n%s%s✓ Done — published %s from %s%s\n' \
    "$BOLD" "$GREEN" "$RELEASE_VER" "${RELEASE_SHA:0:12}" "$RESET"
  # trap restores original branch + stash on exit
}

# ---- dispatch ---------------------------------------------------------------
CMD="${1:-}"
[ $# -gt 0 ] && shift || true
case "$CMD" in
  prepare) cmd_prepare "$@" ;;
  publish) cmd_publish "$@" ;;
  -h|--help|"") usage 0 ;;
  *) echo "error: unknown command '$CMD'" >&2; usage 2 ;;
esac
