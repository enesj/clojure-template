# Setup Scripts

Scripts for initial project setup, configuration, and development environment preparation.

## Scripts

### `install-clj-kondo-imports.sh`

Downloads and installs community clj-kondo import configurations for popular Clojure libraries.

**Usage:**

```bash
./install-clj-kondo-imports.sh
```

**Features:**

- Fetches latest clj-kondo community configs
- Installs library-specific linting rules
- Idempotent - safe to run multiple times
- Only installs missing configurations
- Improves linting accuracy for third-party libraries

### `install-completions.sh`

Installs shell autocompletion for Babashka (bb) tasks based on your shell type.

**Usage:**

```bash
./install-completions.sh
```

**Supports:**

- Fish shell completion
- Zsh completion (with oh-my-zsh support)
- Autodetects shell type and installs appropriate completion files

**Features after installation:**

- Tab completion for bb tasks
- Complete library names for dependency commands
- Complete command options
