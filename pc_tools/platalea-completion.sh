# LocalManager platalea shell completion for bash and zsh.
#
# Usage (interactive shell only):
#   source /path/to/platalea-completion.sh
#
# Optional:
#   export PLATALEA_HOME=/path/to/.localmanager

_platalea_completion_loaded=0

_platalea_warn() {
    printf 'platalea-completion: %s\n' "$1" >&2
}

_platalea_app_dir() {
    if [[ -n "${PLATALEA_HOME:-}" ]]; then
        printf '%s' "$PLATALEA_HOME"
    else
        printf '%s/.localmanager' "$HOME"
    fi
}

_platalea_boards_root() {
    local app_dir config
    app_dir="$(_platalea_app_dir)"
    config="$app_dir/config.json"
    if [[ -f "$config" ]] && command -v python3 >/dev/null 2>&1; then
        python3 - "$config" "$app_dir/boards" <<'PY' 2>/dev/null || printf '%s/boards' "$app_dir"
import json, pathlib, sys
cfg_path = pathlib.Path(sys.argv[1])
fallback = pathlib.Path(sys.argv[2])
if not cfg_path.exists():
    print(fallback)
    raise SystemExit(0)
raw = json.loads(cfg_path.read_text(encoding="utf-8"))
board_root = raw.get("board_root", "boards")
path = pathlib.Path(board_root)
if not path.is_absolute():
    path = cfg_path.parent / path
print(path.resolve())
PY
    else
        printf '%s/boards' "$app_dir"
    fi
}

_platalea_list_board_ids() {
    local root="$(_platalea_boards_root)"
    local entry name
    [[ -d "$root" ]] || return 0
    for entry in "$root"/*; do
        [[ -d "$entry" && -f "$entry/meta.json" ]] || continue
        name="${entry##*/}"
        [[ -n "$name" ]] && printf '%s\n' "$name"
    done
}

_platalea_default_board() {
    local app_dir config
    app_dir="$(_platalea_app_dir)"
    config="$app_dir/config.json"
    [[ -f "$config" && -x "$(command -v python3)" ]] || { printf 'default'; return; }
    python3 - "$config" <<'PY' 2>/dev/null || printf 'default'
import json, pathlib, sys
path = pathlib.Path(sys.argv[1])
try:
    raw = json.loads(path.read_text(encoding="utf-8"))
except (OSError, json.JSONDecodeError):
    print("default")
else:
    board = str(raw.get("default_board", "default")).strip()
    print(board or "default")
PY
}

_platalea_effective_board() {
    if [[ -n "${_PLATALEA_BOARD:-}" ]]; then
        printf '%s' "$_PLATALEA_BOARD"
    else
        _platalea_default_board
    fi
}
_platalea_list_message_ids() {
    local board_id=$1
    local root msg_file
    root="$(_platalea_boards_root)"
    msg_file="$root/$board_id/messages.json"
    [[ -f "$msg_file" ]] || return 0
    python3 - "$msg_file" <<'PY' 2>/dev/null
import json, pathlib, sys
path = pathlib.Path(sys.argv[1])
try:
    messages = json.loads(path.read_text(encoding="utf-8"))
except (OSError, json.JSONDecodeError):
    raise SystemExit(0)
if not isinstance(messages, list):
    raise SystemExit(0)
for item in messages:
    if not isinstance(item, dict) or item.get("deleted"):
        continue
    msg_id = str(item.get("id", "")).strip()
    if msg_id:
        print(msg_id)
PY
}

_platalea_list_attachment_ids() {
    local board_id=$1
    local root msg_file
    root="$(_platalea_boards_root)"
    msg_file="$root/$board_id/messages.json"
    [[ -f "$msg_file" ]] || return 0
    python3 - "$msg_file" <<'PY' 2>/dev/null
import json, pathlib, sys
path = pathlib.Path(sys.argv[1])
try:
    messages = json.loads(path.read_text(encoding="utf-8"))
except (OSError, json.JSONDecodeError):
    raise SystemExit(0)
if not isinstance(messages, list):
    raise SystemExit(0)
seen = set()
for item in messages:
    if not isinstance(item, dict) or item.get("deleted"):
        continue
    for attachment in item.get("attachments") or []:
        if not isinstance(attachment, dict):
            continue
        att_id = str(attachment.get("id", "")).strip()
        if att_id and att_id not in seen:
            seen.add(att_id)
            print(att_id)
PY
}

_platalea_commands() {
    printf '%s\n' \
        start stop status help \
    gpg file config service discover power \
        list-boards get-agent get-messages \
        post modify delete download-attachment \
        create-board delete-board \
        export-boardpack import-boardpack
}

_platalea_gpg_subcommands() {
    printf '%s\n' list-keys pass-encrypt pass-decrypt quick-encrypt quick-decrypt
}

_platalea_file_subcommands() {
    printf '%s\n' obfuscate deobfuscate
}

_platalea_config_subcommands() {
    printf '%s\n' init init-tls import
}

_platalea_service_subcommands() {
    printf '%s\n' install uninstall status
}

_platalea_power_subcommands() {
    printf '%s\n' status shutdown
}

_platalea_help_topics() {
    printf '%s\n' serve board gpg file config service discover power
}

_platalea_is_group_cmd() {
    case "$1" in
        gpg|file|config|service|power|help) return 0 ;;
    esac
    return 1
}

_platalea_group_subcommands() {
    case "$1" in
        gpg) _platalea_gpg_subcommands ;;
        file) _platalea_file_subcommands ;;
        config) _platalea_config_subcommands ;;
        service) _platalea_service_subcommands ;;
        power) _platalea_power_subcommands ;;
        help) _platalea_help_topics ;;
    esac
}

_platalea_global_flags() {
    printf '%s\n' --help -h --version -V --host --port --board --password --host-password --config --ca-cert --tls-fingerprint --json
}

_platalea_api_flags() {
    _platalea_global_flags
    printf '%s\n' --author --attach --name --role-ids --file -o --output
}

_platalea_flag_expects_value() {
    case "$1" in
        --host|--port|--board|--password|--host-password|--config|--ca-cert|--tls-fingerprint|--attach|--author|--name|--role-ids|--file|-o|--output|-p)
            return 0
            ;;
    esac
    return 1
}

_platalea_is_server_cmd() {
    case "$1" in
        start|stop|status) return 0 ;;
    esac
    return 1
}

_platalea_is_board_cmd() {
    case "$1" in
        list-boards|get-agent|get-messages|post|modify|delete|download-attachment|create-board|delete-board|export-boardpack|import-boardpack)
            return 0
            ;;
    esac
    return 1
}

# Words after "platalea" and before the word being completed.
_platalea_split_argv() {
    _PLATALEA_CMD=""
    _PLATALEA_SUB=""
    _PLATALEA_POS=()
    _PLATALEA_BOARD=""
    local -a positionals=()
    while (( $# > 0 )); do
        case "$1" in
            --board)
                shift
                if (( $# > 0 )); then
                    _PLATALEA_BOARD="$1"
                    shift
                fi
                ;;
            --host|--port|--password|--host-password|--config|--ca-cert|--tls-fingerprint|--attach|--author|--name|--role-ids|--file|-o|--output|-p)
                shift
                (( $# > 0 )) && shift
                ;;
            --json|-h|--help|-V|--version|--force)
                shift
                ;;
            *)
                positionals+=("$1")
                shift
                ;;
        esac
    done
    if (( ${#positionals[@]} == 0 )); then
        return 0
    fi
    if _platalea_is_group_cmd "${positionals[0]}"; then
        _PLATALEA_CMD="${positionals[0]}"
        if (( ${#positionals[@]} > 1 )); then
            _PLATALEA_SUB="${positionals[1]}"
            if (( ${#positionals[@]} > 2 )); then
                _PLATALEA_POS=("${positionals[@]:2}")
            fi
        fi
    else
        _PLATALEA_CMD="${positionals[0]}"
        if (( ${#positionals[@]} > 1 )); then
            _PLATALEA_POS=("${positionals[@]:1}")
        fi
    fi
}

if [[ -n "${BASH_VERSION:-}" ]]; then

_platalea_completion_bash() {
    local cur prev
    cur="${COMP_WORDS[COMP_CWORD]}"
    prev="${COMP_WORDS[COMP_CWORD - 1]}"

    if _platalea_flag_expects_value "$prev"; then
        case "$prev" in
            --attach|--config|--ca-cert|-o|--output)
                COMPREPLY=( $(compgen -f -- "$cur") )
                return 0
                ;;
            --host)
                COMPREPLY=( $(compgen -W '127.0.0.1 localhost ::1' -- "$cur") )
                return 0
                ;;
            --port)
                COMPREPLY=( $(compgen -W '8765' -- "$cur") )
                return 0
                ;;
            --board)
                COMPREPLY=( $(compgen -W "$( _platalea_list_board_ids )" -- "$cur") )
                return 0
                ;;
        esac
        return 0
    fi

    local -a prior=()
    local i
    for (( i = 1; i < COMP_CWORD; i++ )); do
        prior+=("${COMP_WORDS[i]}")
    done
    _platalea_split_argv "${prior[@]}"

    if [[ -z "$_PLATALEA_CMD" ]]; then
        if [[ "$cur" == -* ]]; then
            COMPREPLY=( $(compgen -W "$( _platalea_global_flags )" -- "$cur") )
        else
            COMPREPLY=( $(compgen -W "$( _platalea_commands )" -- "$cur") )
        fi
        return 0
    fi

    if _platalea_is_server_cmd "$_PLATALEA_CMD"; then
        if [[ "$cur" == -* ]]; then
            case "$_PLATALEA_CMD" in
                start|status|stop) COMPREPLY=( $(compgen -W '--config --help -h' -- "$cur") ) ;;
            esac
        elif [[ "$prev" == "--config" ]]; then
            COMPREPLY=( $(compgen -f -- "$cur") )
        fi
        return 0
    fi

    if _platalea_is_group_cmd "$_PLATALEA_CMD"; then
        if [[ "$cur" == -* ]]; then
            return 0
        fi
        if [[ -z "$_PLATALEA_SUB" ]]; then
            COMPREPLY=( $(compgen -W "$( _platalea_group_subcommands "$_PLATALEA_CMD" )" -- "$cur") )
        elif [[ "$_PLATALEA_CMD" == "file" && "$_PLATALEA_SUB" == "obfuscate" || "$_PLATALEA_CMD" == "file" && "$_PLATALEA_SUB" == "deobfuscate" ]]; then
            if (( ${#_PLATALEA_POS[@]} == 0 )); then
                COMPREPLY=( $(compgen -f -- "$cur") )
            fi
        elif [[ "$_PLATALEA_CMD" == "config" && "$_PLATALEA_SUB" == "import" ]]; then
            if (( ${#_PLATALEA_POS[@]} == 0 )); then
                COMPREPLY=( $(compgen -f -- "$cur") )
            fi
        elif [[ "$_PLATALEA_CMD" == "config" && "$_PLATALEA_SUB" == "init" || "$_PLATALEA_CMD" == "config" && "$_PLATALEA_SUB" == "init-tls" ]]; then
            if [[ "$cur" == -* ]]; then
                COMPREPLY=( $(compgen -W '--config --force --help -h' -- "$cur") )
            elif [[ "$prev" == "--config" ]]; then
                COMPREPLY=( $(compgen -f -- "$cur") )
            fi
        fi
        return 0
    fi

    if ! _platalea_is_board_cmd "$_PLATALEA_CMD"; then
        return 0
    fi

    if [[ "$cur" == -* ]]; then
        case "$_PLATALEA_CMD" in
            post|modify|import-boardpack|download-attachment) COMPREPLY=( $(compgen -W "$( _platalea_api_flags )" -- "$cur") ) ;;
            *) COMPREPLY=( $(compgen -W "$( _platalea_global_flags )" -- "$cur") ) ;;
        esac
        return 0
    fi

    local pos_count=${#_PLATALEA_POS[@]}
    local effective_board
    effective_board="$(_platalea_effective_board)"
    case "$_PLATALEA_CMD" in
        export-boardpack)
            if (( pos_count == 0 )); then
                COMPREPLY=( $(compgen -f -- "$cur") )
            fi
            ;;
        modify|delete)
            if (( pos_count == 0 )); then
                COMPREPLY=( $(compgen -W "$( _platalea_list_message_ids "$effective_board" )" -- "$cur") )
            fi
            ;;
        download-attachment)
            if (( pos_count == 0 )); then
                COMPREPLY=( $(compgen -W "$( _platalea_list_attachment_ids "$effective_board" )" -- "$cur") )
            fi
            ;;
        import-boardpack)
            if (( pos_count == 0 )); then
                COMPREPLY=( $(compgen -f -- "$cur") )
            fi
            ;;
    esac
}

_platalea_register_bash() {
    if ! command -v compgen >/dev/null 2>&1; then
        _platalea_warn "当前 bash 缺少 compgen，无法启用补全。"
        return 1
    fi
    complete -r platalea 2>/dev/null || true
    complete -o bashdefault -o default -o nospace -F _platalea_completion_bash platalea 2>/dev/null || {
        _platalea_warn "bash complete 注册失败。"
        return 1
    }
    return 0
}

fi

if [[ -n "${ZSH_VERSION:-}" ]]; then

#compdef platalea

_platalea_zsh() {
    emulate -L zsh
    setopt localoptions extendedglob noshwordsplit

    local curcontext="$curcontext" state line
    local -a args cmd_pos
    local cmd pos_count

    if (( CURRENT == 1 )); then
        return 1
    fi

    local -a args
    local i
    for (( i = 2; i < CURRENT; i++ )); do
        args+=("${words[i]}")
    done
    _platalea_split_argv "${args[@]}"

    cmd="$_PLATALEA_CMD"
    sub="$_PLATALEA_SUB"
    cmd_pos=("${_PLATALEA_POS[@]}")
    pos_count=${#cmd_pos[@]}

    if _platalea_flag_expects_value "${words[CURRENT - 1]}"; then
        case "${words[CURRENT - 1]}" in
            --attach|--config|--ca-cert|-o|--output)
                _files
                return 0
                ;;
            --host)
                compadd -S ' ' -- 127.0.0.1 localhost ::1
                return 0
                ;;
            --port)
                compadd -S ' ' -- 8765
                return 0
                ;;
            --board)
                compadd -S ' ' -- ${(f)"$( _platalea_list_board_ids )"}
                return 0
                ;;
        esac
        return 0
    fi

    if [[ -z "$cmd" ]]; then
        if [[ "${words[CURRENT]}" == -* || "${words[CURRENT]}" == --* ]]; then
            compadd -S ' ' -- ${(f)"$( _platalea_global_flags )"}
        else
            # compadd 自动按当前词前缀过滤；sta → start/status
            compadd -S ' ' -- ${(f)"$( _platalea_commands )"}
        fi
        return 0
    fi

    if _platalea_is_server_cmd "$cmd"; then
        if [[ "${words[CURRENT]}" == -* ]]; then
            case "$cmd" in
                start|status|stop) compadd -S ' ' -- --config --help -h ;;
                *) compadd -S ' ' -- --help -h ;;
            esac
        elif [[ "${words[CURRENT - 1]}" == "--config" ]]; then
            _files
        fi
        return 0
    fi

    if _platalea_is_group_cmd "$cmd"; then
        if [[ "${words[CURRENT]}" == -* ]]; then
            return 0
        fi
        if [[ -z "$sub" ]]; then
            compadd -S ' ' -- ${(f)"$( _platalea_group_subcommands "$cmd" )"}
        elif [[ "$cmd" == "file" ]]; then
            if (( pos_count == 0 )); then
                _files
            fi
        elif [[ "$cmd" == "config" && "$sub" == "import" ]]; then
            if (( pos_count == 0 )); then
                _files
            fi
        elif [[ "$cmd" == "config" && ( "$sub" == "init" || "$sub" == "init-tls" ) ]]; then
            if [[ "${words[CURRENT]}" == -* ]]; then
                compadd -S ' ' -- --config --force --help -h
            elif [[ "${words[CURRENT - 1]}" == "--config" ]]; then
                _files
            fi
        fi
        return 0
    fi

    if ! _platalea_is_board_cmd "$cmd"; then
        return 0
    fi

    if [[ "${words[CURRENT]}" == -* ]]; then
        case "$cmd" in
            post|modify|import-boardpack|download-attachment)
                compadd -S ' ' -- ${(f)"$( _platalea_api_flags )"}
                ;;
            *)
                compadd -S ' ' -- ${(f)"$( _platalea_global_flags )"}
                ;;
        esac
        return 0
    fi

    local effective_board
    effective_board="$(_platalea_effective_board)"
    case "$cmd" in
        export-boardpack)
            if (( pos_count == 0 )); then
                _files
            fi
            ;;
        modify|delete)
            if (( pos_count == 0 )); then
                compadd -S ' ' -- ${(f)"$( _platalea_list_message_ids "$effective_board" )"}
            fi
            ;;
        download-attachment)
            if (( pos_count == 0 )); then
                compadd -S ' ' -- ${(f)"$( _platalea_list_attachment_ids "$effective_board" )"}
            fi
            ;;
        import-boardpack)
            if (( pos_count == 0 )); then
                _files
            fi
            ;;
    esac
}

_platalea_register_zsh() {
    if ! whence compdef >/dev/null 2>&1; then
        _platalea_warn "当前 zsh 未启用补全系统（compdef 不可用）。"
        return 1
    fi
    compdef -d platalea 2>/dev/null || true
    compdef _platalea_zsh platalea
    return 0
}

fi

_platalea_register_completion() {
    case $- in
        *i*) ;;
        *)
            _platalea_warn "非交互式 shell，已跳过（请写入 ~/.bashrc 或 ~/.zshrc）。"
            return 0
            ;;
    esac

    if [[ -n "${ZSH_VERSION:-}" ]]; then
        if _platalea_register_zsh; then
            _platalea_completion_loaded=1
        fi
        return 0
    fi

    if [[ -n "${BASH_VERSION:-}" ]]; then
        if _platalea_register_bash; then
            _platalea_completion_loaded=1
        fi
        return 0
    fi

    _platalea_warn "仅支持 bash 与 zsh，当前 shell 无法加载。"
    return 1
}

_platalea_register_completion
