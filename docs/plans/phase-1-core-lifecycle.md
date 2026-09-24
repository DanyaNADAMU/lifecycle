# Phase 1 Plan: Core Lifecycle & Scale-to-Zero

## Objectives
Implement the complete end-to-end server lifecycle management plugin for Velocity:
1. Systemd Webhook Client (`client/SystemdBridgeClient.java`).
2. Config & Server Settings (`config/LifecycleConfig.java`, `config/ConfigManager.java`).
3. State Machine & Registry (`model/ServerState.java`, `model/ManagedServer.java`, `service/ServerRegistry.java`).
4. On-Demand Waking, Warmup & Polling (`service/WakeService.java`).
5. Scale-to-Zero Idle Watchdog (`service/IdleService.java`).
6. Soft Auth Bridge (`auth/AuthBridge.java`).
7. Event Listeners (`listeners/InitialServerListener.java`, `listeners/PreConnectListener.java`, `listeners/DisconnectListener.java`).
8. Admin Commands (`commands/LifecycleCommand.java`).

## Milestones & Task Breakdown

### Task 1: Foundation & HTTP Client
- [x] Configure Gradle build with Java 25 toolchain, ShadowJar, and Velocity API 4.0.0.
- [ ] Implement `LifecycleConfig` using SpongePowered Configurate (`config.yml`).
- [ ] Implement `SystemdBridgeClient` using standard Java `HttpClient`:
  - `startServer(String server)`
  - `stopServer(String server)`
  - `isServerActive(String server)`

### Task 2: State Tracking & Wake Management
- [ ] Implement `ManagedServer` model tracking `ServerState` (`STOPPED`, `STARTING`, `RUNNING`, `STOPPING`), `startupTime`, `emptySince`, and `queuedPlayers`.
- [ ] Implement `ServerRegistry` loading configuration and mapping names to Velocity `RegisteredServer` instances.
- [ ] Implement `WakeService` handling:
  - Background startup triggering (`startWarmup`).
  - Asynchronous TCP ping polling with configurable interval and maximum timeout.
  - Interactive UI notices (Titles and Actionbars using Kyori Adventure MiniMessage).
  - Bulk player transfer upon successful boot.

### Task 3: Soft Auth Bridge & Velocity Event Listeners
- [ ] Implement `AuthBridge` providing soft runtime inspection of `nadamu-auth` `AuthState` without hard classpath dependencies.
- [ ] Implement `InitialServerListener` (`PostOrder.FIRST`) to initiate parallel warmup on forced-hosts.
- [ ] Implement `PreConnectListener` (`PostOrder.EARLY`) to intercept connection attempts to offline servers and route to `limbo`.
- [ ] Implement `DisconnectListener` to clean up queued players on proxy exit.

### Task 4: Idle Watchdog Loop & Administrative CLI
- [ ] Implement `IdleService` running periodically to check player counts, honor grace periods, and dispatch shutdown requests.
- [ ] Implement `/lifecycle` command (`/nlc`) with subcommands: `status`, `reload`, `start <server>`, `stop <server>`.
- [ ] Verify test suite and clean compilation via `./gradlew build`.
