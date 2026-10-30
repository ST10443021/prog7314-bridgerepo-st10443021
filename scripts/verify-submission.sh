#!/usr/bin/env bash
# Pre-submission gate for the PROG7314 Final PoE. Run from anywhere inside the repository:
#   bash scripts/verify-submission.sh
# It checks the things the rubric and PoE checklist mark that cannot be proven by the code alone.
# Exit code 0 = ready, 1 = something is still missing.
set -uo pipefail
cd "$(git rev-parse --show-toplevel)" || exit 1

fail=0
ok()   { echo "  [ok]   $1"; }
bad()  { echo "  [FAIL] $1"; fail=1; }
warn() { echo "  [warn] $1"; }

echo "1. Repository hygiene"
if git ls-files | grep -Eiq '\.(zip|7z|rar|tar|gz|tgz)$'; then bad "compressed archives are tracked: $(git ls-files | grep -Ei '\.(zip|7z|rar|tar|gz|tgz)$' | head -3 | tr '\n' ' ')"; else ok "no compressed archives tracked"; fi
if git ls-files | grep -Eq '(^|/)google-services\.json$|\.jks$|\.keystore$|keystore\.properties$'; then bad "a secret file (google-services.json / keystore) is tracked"; else ok "no keystore or google-services.json tracked"; fi
for f in README.md RELEASE_NOTES.md AI_USE_WRITEUP.md REFERENCES-CODE.md; do [ -s "$f" ] && ok "$f present" || bad "$f missing"; done

echo "2. Placeholders still to fill in"
hits=$(grep -rnE "REPLACE_WITH|Accessed ____|CONFIRM[: ]|\[CONFIRM" README.md RELEASE_NOTES.md AI_USE_WRITEUP.md REFERENCES-CODE.md store docs 2>/dev/null | head -20)
if [ -n "$hits" ]; then bad "placeholders remain:"; echo "$hits" | sed 's/^/           /'; else ok "no placeholders remain"; fi

echo "3. Git history (rubric: 75+ commits and multiple contributors)"
commits=$(git rev-list --count HEAD 2>/dev/null || echo 0)
authors=$(git shortlog -sne HEAD 2>/dev/null | wc -l | tr -d ' ')
[ "$commits" -ge 75 ] && ok "$commits commits" || bad "only $commits commits (need 75+); keep committing real work, do not pad history"
[ "$authors" -ge 2 ] && ok "$authors contributors" || bad "only $authors contributor(s); every team member must push under their own identity"

echo "4. Final tag"
tag=$(git tag --list | grep -Ei '^final-?poe$' | head -1)
if [ -z "$tag" ]; then bad "no Final-POE tag (git tag -a Final-POE -m 'Final PoE submission' && git push origin Final-POE)";
else ok "tag $tag exists"; [ "$(git rev-parse "$tag^{commit}")" = "$(git rev-parse HEAD)" ] && ok "tag points at HEAD" || warn "tag does not point at HEAD"; fi

echo "5. README evidence"
grep -Eq 'youtu(\.be|be\.com)/' README.md && ok "video link present" || bad "no YouTube link found in README.md"
grep -q "REPLACE_WITH_VIDEO" README.md && bad "README still has the video placeholder"
missing=""
for i in 01 02 03 04 05 06 07 08; do [ -s "docs/screenshots/play-$i.png" ] || missing="$missing play-$i"; done
[ -z "$missing" ] && ok "8 promotional screenshots present" || bad "missing screenshots:${missing}"
[ -s store/feature-graphic-1024x500.png ] && ok "feature graphic present" || bad "feature graphic missing"

echo "6. AI write-up length (max 500 words)"
words=$(wc -w < AI_USE_WRITEUP.md | tr -d ' ')
[ "$words" -le 500 ] && ok "$words words" || bad "$words words (limit 500)"

echo
if [ "$fail" -eq 0 ]; then echo "READY: all checks passed."; else echo "NOT READY: fix the [FAIL] items above."; fi
exit $fail
