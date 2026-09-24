# nadamu-lifecycle

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
- **Interactive Waiting Room (Limbo Holding)**:
  Seamlessly holds players in a designated fallback server (e.g. NanoLimbo) with animated MiniMessage titles and action bars until the target backend reports healthy via TCP ping.
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
                     │       nadamu-lifecycle      │
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

# Fallback server to hold players while backend boots
limbo-server: "limbo"

# Messages displayed during server startup
messages:
  server-starting: "<gold><b>Server <yellow>{server}</yellow> is starting up...</b></gold><newline><gray>Please wait a moment</gray>"
  server-ready: "<green>Server is ready! Connecting...</green>"
  server-failed: "<red>Failed to start {server}. Please contact an administrator.</red>"

# Managed backend servers
servers:
  pvp:
    idle-timeout-minutes: 10
    startup-grace-period-seconds: 180
    poll-interval-seconds: 2
    max-startup-wait-seconds: 120
  pillar:
    idle-timeout-minutes: 10
    startup-grace-period-seconds: 180
    poll-interval-seconds: 2
    max-startup-wait-seconds: 120
  forge:
    idle-timeout-minutes: 15
    startup-grace-period-seconds: 300
    poll-interval-seconds: 3
    max-startup-wait-seconds: 300
```

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
