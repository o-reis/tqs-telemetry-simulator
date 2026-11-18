#!/usr/bin/env bash
# Prints the CHANGELOG.md section of one simulator version, to use as the release notes.
# Fails when the section is missing or empty, so no release goes out without a changelog entry.
# Usage: .github/scripts/release-notes.sh 1.0.0 [path/to/CHANGELOG.md]
set -euo pipefail

version="${1:?usage: release-notes.sh VERSION [CHANGELOG]}"
changelog="${2:-CHANGELOG.md}"

# The section starts at "## [VERSION]" and ends before the next "## " heading.
notes="$(awk -v heading="## [${version}]" '
  index($0, heading) == 1 { found = 1; next }
  found && /^## / { exit }
  found { print }
' "$changelog")"

if ! grep -q '[^[:space:]]' <<<"$notes"; then
  echo "No changelog entry for ${version} in ${changelog}: add a \"## [${version}]\" section" >&2
  exit 1
fi

printf '%s\n' "$notes"
