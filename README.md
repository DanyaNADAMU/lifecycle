# lifecycle

Modern, lightweight, and rock-solid Server Lifecycle & Scale-to-Zero management plugin for **Velocity** proxy networks.

Designed specifically for containerized Minecraft infrastructures using **Podman Quadlet** and **systemd**, enabling on-demand container waking, parallel boot during authentication, and automated idle shutdowns.

---

## Key Features

- **Scale-to-Zero Infrastructure**:
  Automatically shuts down empty Minecraft servers (`Purpur`, `Paper`, `Forge 1.12.2`, `Fabric`, etc.) to free RAM and CPU, running only the proxy and lightweight NanoLimbo 24/7.
- **Parallel Warmup on Forced-Hosts**:
  Detects incoming connections via subdomains (e.g. `pvp.nada.mu`) at the earliest handshake stage (`PostOrder.FIRST`) and triggers container booting *in parallel* while the player enters their credentials in NanoLimbo.
- **Zero-Bypass Auth Security**:
  Provides a decoupled, soft-dependent bridge with `nadamu-auth`. Ensures that unauthenticated players in `PENDING_LOGIN` state can *never* be transferred to backend gameplay servers ahead of entering their password.
- **Quadlet & Systemd Native Integration**:
  Communicates with the host's Rootless Podman environment via an unprivileged `systemd --user` webhook bridge, completely independent of the Minecraft server core or Java version.
- **Interactive Waiting Room & Internationalization (i18n)**:
  Holds players in NanoLimbo with animated MiniMessage titles and action bars. Includes automatic player client locale detection, built-in dictionaries (`ru.yml`, `en.yml`), dialect support (`ru-ua`, `en-us`), and custom localization files in `languages/`.
- **Flexible Pool Management (Auto-Discovery & Defaults)**:
  Supports autopilot mode (`auto: true`) to automatically manage all servers from `velocity.toml` with default baseline parameters (`defaults`), or explicit whitelist mode (`auto: false`), with server overrides and exclusion support (`enabled: false`).
- **Configurable Idle Watchdog**:
  Monitors player counts and enforces startup grace periods alongside idle timeouts per server.

---

## Architecture Overview

```
                      Player connects to pvp.nada.mu
                                    │
                                    ▼
                     ┌─────────────────────────────┐
                     │       Velocity Proxy        │
                     └──────────────┬──────────────┘
                                    │
                    PreLogin / InitialServer (FIRST)
                                    │
                                    ▼
                     ┌─────────────────────────────┐
                     │       lifecycle      │
                     │  (Wakes mc@pvp in parallel) │
                     └──────────────┬──────────────┘
                                    │ HTTP POST
                                    ▼
       ┌────────────────────────────────────────────────────────┐
       │   webhook bridge (systemd --user on host:9000)         │
       │   systemctl --user start mc@pvp.service                │
       └────────────────────────────┬───────────────────────────┘
                                    │
                                    ▼
       ┌────────────────────────────────────────────────────────┐
       │       Podman Quadlet creates ephemeral container       │
       │       (Purpur / Forge boots in background)             │
       └────────────────────────────────────────────────────────┘
```

---

## Configuration (`config.yml`)

```yaml
bridge:
  url: "http://host.containers.internal:9000"
  timeout-seconds: 5
  parameter-name: "server"
  unit-template: "mc@{server}"
  hooks:
    start: "start"
    stop: "stop"
    status: "status"

# Fallback server to hold players while backend boots
limbo-server: "limbo"

# Default language if player client locale is not recognized
default-language: "ru"

# Automatic pool discovery:
# true  - automatically manages all servers from velocity.toml (except limbo and disabled)
# false - manages only servers explicitly declared in 'servers'
auto: true

# Baseline default settings applied to managed servers unless overridden
defaults:
  idle-timeout-minutes: 10
  startup-grace-period-seconds: 180
  poll-interval-seconds: 2
  max-startup-wait-seconds: 120

# Server-specific overrides or whitelist
servers:
  # Example: exclude lobby from Scale-to-Zero in auto mode (runs 24/7)
  # lobby:
  #   enabled: false

  # Example: heavy modpack with increased startup time
  forge:
    idle-timeout-minutes: 15
    startup-grace-period-seconds: 300
    poll-interval-seconds: 3
    max-startup-wait-seconds: 300
```

---

## Internationalization (i18n)

All messages are placed in the `plugins/lifecycle/languages/` directory:
- **Built-in:** `ru.yml` (Russian) and `en.yml` (English).
- **Auto-detection:** the plugin matches the player client locale (`ru-RU`, `en-US`, etc.).
- **Dialects:** administrators can place dialect files (e.g. `ru-ua.yml` or `en-gb.yml`). If a dialect file is missing, it smoothly falls back to the base language (`ru` or `en`), and then to `default-language`.
- **Custom languages:** simply drop a new YAML dictionary into `languages/` (e.g. `de.yml`) and run `/lifecycle reload`.

---

## Commands & Permissions

| Command | Aliases | Permission | Description |
| :--- | :--- | :--- | :--- |
| `/lifecycle reload` | `/nlc reload` | `nadamu.lifecycle.admin` | Reloads configuration files |
| `/lifecycle status` | `/nlc status` | `nadamu.lifecycle.admin` | Shows states of all managed servers |
| `/lifecycle start <server>` | `/nlc start` | `nadamu.lifecycle.admin` | Manually triggers server boot |
| `/lifecycle stop <server>` | `/nlc stop` | `nadamu.lifecycle.admin` | Manually triggers server shutdown |

---

## License

Proprietary / All Rights Reserved. NADAMU Network.
