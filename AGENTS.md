# AGENTS.md

## Project Context
- **Project Name**: lifecycle
- **Target Platform**: Velocity Proxy (version 4.0.0+)
- **Runtime**: Java 25
- **Build System**: Gradle with Kotlin DSL (`build.gradle.kts`)
- **Group**: `mu.nada`
- **Artifact**: `lifecycle`
- **Target OS / Environment**: Debian 12, Rootless Podman with Quadlet (`systemd --user`)

## Key Architecture Principles
1. **Separation of Concerns**:
   - `client/` provides an asynchronous HTTP client to interact with the systemd webhook bridge on the host (`SystemdBridgeClient`).
   - `model/` defines server lifecycle states (`ServerState`: `STOPPED`, `STARTING`, `RUNNING`, `STOPPING`) and runtime tracker (`ManagedServer`).
   - `service/` encapsulates server registry, on-demand waking, polling, and idle stopping (`ServerRegistry`, `WakeService`, `IdleService`).
   - `auth/` contains the soft integration bridge to `nadamu-auth` (`AuthBridge`), guaranteeing zero auth bypasses while maintaining optional coupling.
   - `i18n/` and `util/` provide multi-language dictionary management (`LanguageManager`, `MessagesConfig`) and localized MiniMessage rendering (`MessageService`) with client locale auto-detection and dialect fallbacks.
   - `listeners/` handles early wakeup detection and connection proxying (`InitialServerListener`, `PreConnectListener`, `DisconnectListener`).
   - `commands/` provides admin CLI control (`/lifecycle` or `/nlc`).
2. **Flexible Pool Discovery & Defaults**:
   - `auto: true` automatically registers all backend servers declared in `velocity.toml` (excluding `limbo-server` and servers marked `enabled: false`).
   - `auto: false` restricts management to servers explicitly declared in `servers:`.
   - `defaults:` block supplies baseline parameters (`idleTimeoutMinutes`, `startupGracePeriodSeconds`, etc.) merged cleanly into per-server overrides.
3. **Infrastructure Integration (Quadlet & Systemd)**:
   - Backend servers are deployed as declarative Quadlet templates (`mc@<name>.container`).
   - Because Quadlet uses `--rm` on container termination, containers are ephemeral. The authoritative controller is `systemctl --user`.
   - The plugin communicates with systemd via an official `webhook` daemon running under user `minecraft` on the host at `http://host.containers.internal:9000`.
4. **Security & Auth Bypass Prevention**:
   - `lifecycle` never independently teleports a player whose state in `nadamu-auth` is `PENDING_LOGIN`.
   - Parallel warmup boots the container immediately when a player joins via a forced host, but player transfers only occur after successful authentication.
   - Velocity event priorities (`PostOrder.FIRST` / `PostOrder.EARLY`) ensure that auth firewalls always retain final authority.
5. **Scale-to-Zero & Resource Optimization**:
   - Unused backend servers are automatically stopped via `systemctl --user stop mc@<server>` when `playersConnected == 0` after a configurable idle timeout.
   - Startup grace periods prevent false shutdown triggers during initial world generation and player connection handshakes.

## Code Standards & Conventions
- All code, code comments, and technical documentation must be exclusively in English.
- Russian documentation is provided alongside English in `README.ru.md` and `docs/ru/`.
