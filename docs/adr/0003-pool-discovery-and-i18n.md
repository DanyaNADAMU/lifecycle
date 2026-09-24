# ADR 0003: Dynamic Pool Discovery and Internationalization (i18n)

## Status
Accepted

## Context
Initial iterations of `lifecycle` required manual registration of every target backend server in `config.yml`, requiring redundant configuration of timing parameters (`idle-timeout-minutes`, `startup-grace-period-seconds`, `poll-interval-seconds`, `max-startup-wait-seconds`). Furthermore, messages were hardcoded inside `config.yml` in Russian, preventing localization for multi-lingual player networks and creating configuration clutter.

Two core requirements emerged:
1. **Pool Discovery Strategy**:
   - Autopilot: Automatically discover and manage all servers declared in Velocity's `velocity.toml`, while allowing per-server overrides and explicit exclusions (`enabled: false`).
   - Whitelist: Allow restrictive management where only explicitly listed servers are affected.
   - Clean Defaults: Eliminate configuration boilerplate by defining baseline defaults that cascade onto individual servers.
2. **Internationalization (i18n)**:
   - Match the standard established in `nadamu-auth`: auto-generate default dictionaries (`ru.yml`, `en.yml`), detect player client locale, support regional dialects (`ru-ua`, `en-us`), and permit custom user-added dictionaries in `languages/`.

## Decision
1. **Boolean Pool Discovery (`auto: true | false`)**:
   - `auto: true`: `ServerRegistry` queries `proxyServer.getAllServers()`, excludes the technical holding server (`limbo-server`), skips servers with `enabled: false`, and applies baseline `defaults` merged with individual overrides.
   - `auto: false`: `ServerRegistry` restricts management to servers explicitly present in `servers:` with `enabled != false`.
2. **Hierarchical Merging (`defaults.mergeWith(override)`)**:
   - All fields in `ServerSettings` are modeled as nullable wrappers.
   - Any property omitted in a server-specific block seamlessly inherits the value defined in `defaults:`.
3. **Decoupled i18n Architecture**:
   - Messages are extracted from `config.yml` into `plugins/lifecycle/languages/*.yml`.
   - `LanguageManager` dynamically creates `ru.yml` and `en.yml` upon initialization and scans the `languages/` directory for additional files.
   - Dialect resolution prioritizes exact matches (e.g. `ru-ua`), falls back to the base language code (`ru`), and finally to `default-language`.
   - `MessageService` encapsulates MiniMessage parsing, placeholder injection, and audience dispatching.

## Consequences
### Positive
- Zero duplication for network administrators managing multiple backend servers.
- Dynamic network scalability: newly added Velocity servers are automatically managed without restarting or editing `lifecycle`.
- Multi-lingual player experience with seamless client locale detection and dialect support.
- Architectural consistency across the `mu.nada` plugin suite (`nadamu-auth` and `lifecycle`).

### Negative
- Minor disk I/O overhead during initialization to verify and load language files from disk.
