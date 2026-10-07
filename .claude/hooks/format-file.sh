#!/usr/bin/env bash
# PostToolUse hook (Edit|Write): run Biome (format + safe lint fixes) on the file just touched.
set -u

input="$(cat)"
file="$(printf '%s' "$input" | node -e '
  let d = ""; process.stdin.on("data", c => d += c).on("end", () => {
    try { const j = JSON.parse(d); process.stdout.write(String((j.tool_input && j.tool_input.file_path) || "")); }
    catch { process.stdout.write(""); }
  });' 2>/dev/null)"
[ -z "$file" ] && exit 0
[ -f "$file" ] || exit 0

# Only what Biome handles here (see biome.json); Markdown, YAML and HTML stay untouched.
case "$file" in
	*.ts | *.js | *.mjs | *.cjs | *.json | *.jsonc | *.css | *.svelte) ;;
	*) exit 0 ;;
esac

# Walk up from the file's own directory, not from the project root: in a worktree session the
# dependencies live in the worktree while CLAUDE_PROJECT_DIR may still point at the main checkout.
dir="$(cd "$(dirname "$file")" && pwd)"
biome=""
while :; do
	if [ -x "$dir/node_modules/.bin/biome" ]; then
		biome="$dir/node_modules/.bin/biome"
		break
	fi
	# Stop at the repository root (`.git` is a directory in a clone, a file in a worktree).
	[ -e "$dir/.git" ] && break
	parent="$(dirname "$dir")"
	[ "$parent" = "$dir" ] && break
	dir="$parent"
done

if [ -z "$biome" ] && [ -n "${CLAUDE_PROJECT_DIR:-}" ] &&
	[ -x "$CLAUDE_PROJECT_DIR/node_modules/.bin/biome" ]; then
	biome="$CLAUDE_PROJECT_DIR/node_modules/.bin/biome"
fi

# No Biome installed yet (fresh clone before `pnpm install`): nothing to do.
[ -z "$biome" ] && exit 0

(cd "$(dirname "$biome")/../.." && "$biome" check --write --log-level=error "$file") >/dev/null 2>&1 || true
exit 0
