#!/usr/bin/env sh
if [ -z "$husky_skip_init" ]; then
  debug () {
    if [ "$HUSKY_DEBUG" = "1" ]; then
      echo "husky (debug) - $1"
    fi
  }

  readonly hook_name="$(basename -- "$0")"
  debug "starting $hook_name..."

  if [ "$HUSKY" = "0" ]; then
    debug "HUSKY env variable is set to 0, skipping hook"
    exit 0
  fi

  if [ -f "$HOME/.huskyrc" ]; then
    debug "sourcing $HOME/.huskyrc"
    . "$HOME/.huskyrc"
  fi

  readonly husky_skip_init=1
  export husky_skip_init
  sh -e "$0" "$@"
  exitcode="$?"

  if [ $exitcode != 0 ]; then
    echo "husky - $hook_name hook exited with code $exitcode (error)"
  fi

  if [ $exitcode = 127 ]; then
    echo "husky - command not found in PATH=$PATH"
  fi

  exit $exitcode
fi