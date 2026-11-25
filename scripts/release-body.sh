#!/usr/bin/env bash

# Prints the notes of a GitHub release: the CHANGELOG.md section of the version, with each paragraph and each list item
# on one line. GitHub shows each line break of the notes, so a wrapped line of CHANGELOG.md ends in the middle of a
# sentence.

set -euo pipefail

script_dir="$(dirname -- "${BASH_SOURCE[0]}")"

if [[ "$#" -ne 1 ]]; then
  echo 'Usage: scripts/release-body.sh VERSION' >&2
  exit 1
fi
section="$("$script_dir/changelog-section.sh" "$1")"

awk '
  function flush() {
    if (open) print text
    open = 0
  }
  /^[[:space:]]*(```|~~~)/ {
    flush()
    print
    fenced = !fenced
    next
  }
  fenced {
    print
    next
  }
  # A blank line, a heading, an HTML comment, a table row, a block quote, and a horizontal rule end the paragraph.
  /^[[:space:]]*$/ || /^[[:space:]]*(#+ |<!--|\||>)/ || /^[[:space:]]*(---+|===+|___+)[[:space:]]*$/ {
    flush()
    print
    next
  }
  # An indented code block.
  !open && /^(    |\t)/ {
    print
    next
  }
  # A list item, or the line after a hard line break (two spaces or a backslash at the end of a line).
  /^[[:space:]]*([-*+]|[0-9]+[.)])[[:space:]]/ || (open && text ~ /(  |\\)$/) {
    flush()
  }
  {
    if (open) {
      sub(/^[[:space:]]+/, "")
      text = text " " $0
    } else {
      text = $0
      open = 1
    }
  }
  END { flush() }
' <<< "$section"
