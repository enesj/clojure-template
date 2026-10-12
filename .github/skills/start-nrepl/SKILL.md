---
name: start-nrepl
description: "Start or reuse an nREPL server for this project so code can be evaluated through nREPL"
disable-model-invocation: true
---
# Start an nREPL server

Start (or reuse) an nREPL server for this project so you can evaluate code with `clj-nrepl-eval`.

## 1) Prefer existing servers

- `clj-nrepl-eval --discover-ports`

If a server is already running for this directory, reuse its port.

## 2) Start a new server (deps.edn)

This repo provides a deps alias:

- `clojure -M:nrepl`

By default it starts on port **7888** (see `deps.edn`).

## 3) Verify the connection

- `clj-nrepl-eval -p 7888 "(+ 1 2 3)"`

If you started a server on a different port, substitute it for `7888`.
