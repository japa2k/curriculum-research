#!/usr/bin/env bash
# PreToolUse hook (Bash): block destructive commands. Exit 2 = block; stderr goes back to Claude.
# Blocked: rm -rf, git push --force/-f, git reset --hard, git clean, git checkout -- ., git restore .
# Everything else is allowed. Tests: tests/harness/guard-bash.test.ts.
set -u
set -f # never glob-expand the words of the command being inspected

input="$(cat)"
cmd="$(printf '%s' "$input" | node -e '
  let d = ""; process.stdin.on("data", c => d += c).on("end", () => {
    try { const j = JSON.parse(d); process.stdout.write(String((j.tool_input && j.tool_input.command) || "")); }
    catch { process.stdout.write(""); }
  });' 2>/dev/null)"
[ -z "$cmd" ] && exit 0

block() {
	echo "guard-bash: blocked \`$1\`. If it is really needed, ask the engineer to run it manually." >&2
	exit 2
}

check_rm() { # $@ = arguments after rm
	local recursive=0 force=0 word
	for word in "$@"; do
		case "$word" in
			--recursive) recursive=1 ;;
			--force) force=1 ;;
			--*) ;;
			-*)
				case "$word" in *[rR]*) recursive=1 ;; esac
				case "$word" in *f*) force=1 ;; esac
				;;
		esac
	done
	[ "$recursive" = 1 ] && [ "$force" = 1 ] && block "rm -rf"
}

check_git() { # $@ = arguments after git
	while [ $# -gt 0 ]; do # skip global options such as -C <path> and -c <key=value>
		case "$1" in
			-C | -c) shift 2 || shift ;;
			-*) shift ;;
			*) break ;;
		esac
	done
	[ $# -eq 0 ] && return
	local sub="$1" word
	shift
	case "$sub" in
		push)
			for word in "$@"; do
				case "$word" in --force | -f | -[a-zA-Z]*f* | +*) block "git push --force" ;; esac
			done
			;;
		reset)
			for word in "$@"; do [ "$word" = "--hard" ] && block "git reset --hard"; done
			;;
		clean) block "git clean" ;;
		checkout)
			for word in "$@"; do [ "$word" = "." ] && block "git checkout -- ."; done
			;;
		restore)
			for word in "$@"; do [ "$word" = "." ] && block "git restore ."; done
			;;
	esac
}

# Split into simple commands on ; & | ( ) { } and backticks, and drop quotes, so subshells,
# groups, $(…) and `bash -c "rm -rf x"` are inspected too.
while IFS= read -r segment; do
	read -ra words <<<"$segment"
	set -- "${words[@]+"${words[@]}"}"
	while [ $# -gt 0 ]; do # skip wrappers and keywords: sudo, env, xargs, VAR=value, then, bash -c …
		name="${1#\\}" # `\rm` and `/bin/rm` run rm too
		name="${name##*/}"
		case "$name" in
			! | if | then | elif | else | do | while | until | command | time | nohup | eval | exec | *=*) shift ;;
			sudo | env | nice | xargs)
				shift
				while [ $# -gt 0 ]; do # skip the wrapper's options, and the value of those that take one
					case "$name:$1" in
						sudo:-[ughpCDUrtTR] | env:-[uCS] | nice:-n | xargs:-[nILPsdEa]) shift 2 || shift ;;
						*:-*) shift ;;
						*) break ;;
					esac
				done
				;;
			bash | sh | zsh) if [ "${2:-}" = "-c" ]; then shift 2; else break; fi ;;
			*) break ;;
		esac
	done
	[ $# -eq 0 ] && continue
	cmd_name="${1#\\}"
	cmd_name="${cmd_name##*/}"
	shift
	case "$cmd_name" in
		rm) check_rm "$@" ;;
		git) check_git "$@" ;;
	esac
done < <(printf '%s\n' "$cmd" | tr ';&|(){}`' '\n\n\n\n\n\n\n\n' | tr -d "\"'")

exit 0
