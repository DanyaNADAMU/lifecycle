# Architecture Overview: lifecycle

## Component Diagram

```
                 Player Connection Request
                            │
                            ▼
          ┌───────────────────────────────────┐
          │      Velocity Proxy Core          │
          └─────────────────┬─────────────────┘
                            │
             ServerPreConnectEvent / InitialServer
                            │
                            ▼
          ┌───────────────────────────────────┐
          │     lifecycle Plugin       │
          │                                   │
          │  ┌─────────────────────────────┐  │
          │  │     AuthBridge (Soft)       │  │──────┐ (Queries AuthState)
          │  └──────────────┬──────────────┘  │      │
          │                 ▼                 │      ▼
          │  ┌─────────────────────────────┐  │  ┌───────────────────────┐
          │  │       WakeService           │  │  │      nadamu-auth      │
          │  │  (Warmup, Poller, Holding)  │  │  │  (Session & Gatekeeper)│
          │  └──────────────┬──────────────┘  │  └───────────────────────┘
          │                 ▼                 │
          │  ┌─────────────────────────────┐  │
          │  │       IdleService           │  │
          │  │     (Auto-Stop Loop)        │  │
          │  └──────────────┬──────────────┘  │
          │                 ▼                 │
          │  ┌─────────────────────────────┐  │
          │  │    SystemdBridgeClient      │  │
          │  └──────────────┬──────────────┘  │
          └─────────────────┼─────────────────┘
                            │ HTTP REST
                            ▼
          ┌───────────────────────────────────┐
          │   Host Webhook Bridge (:9000)     │
          └─────────────────┬─────────────────┘
                            │ CLI execvp
                            ▼
          ┌───────────────────────────────────┐
          │    systemctl --user [start|stop]  │
          └─────────────────┬─────────────────┘
                            │
                            ▼
          ┌───────────────────────────────────┐
          │   Podman Quadlet (mc@<name>)      │
          │   Ephemeral Backend Containers    │
          └───────────────────────────────────┘
```

## State Machine: ManagedServer

```
         [Initial / Idle]
                │
                │ Trigger: Player Connect / Forced Host
                ▼
          ┌───────────┐
          │  STOPPED  │
          └─────┬─────┘
                │ startServer()
                ▼
          ┌───────────┐
          │ STARTING  │◄───┐
          └─────┬─────┘    │ TCP Ping fails
                │          │ (Wait & Poll)
                │ TCP Ping OK
                ▼
          ┌───────────┐
          │  RUNNING  │
          └─────┬─────┘
                │
                │ online == 0 && idleTime > timeout
                ▼
          ┌───────────┐
          │ STOPPING  │
          └─────┬─────┘
                │ stopServer() finishes
                ▼
          ┌───────────┐
          │  STOPPED  │
          └───────────┘
```

## Core Workflows

### 1. Warmup Workflow (Forced-Host)
1. `InitialServerListener` captures `PlayerChooseInitialServerEvent(PostOrder.FIRST)`.
2. Inspects `event.getInitialServer()` against `ServerRegistry`.
3. If server state is `STOPPED`:
   - Transitions state to `STARTING`.
   - Sends asynchronous `POST /hooks/start-server?server=<name>`.
   - Starts periodic TCP ping task (e.g. every 2 seconds).
4. `nadamu-auth` executes subsequent authentication in NanoLimbo.
5. Once target server reports ping success, state transitions to `RUNNING`.

### 2. Connection Proxying & Limbo Holding
1. `PreConnectListener` intercepts `ServerPreConnectEvent(PostOrder.EARLY)`.
2. If target server is `STARTING` or `STOPPED`:
   - Checks `AuthBridge`:
     - If player is `PENDING_LOGIN`, connection to gameplay server is disallowed; player remains in `limbo`.
     - If player is authorized (authenticated / guest):
       - Redirects event to `limbo`.
       - Queues player into `WakeService` waiting set.
       - Dispatches Title/Actionbar messages: *"Server is starting..."*.
3. When server transitions to `RUNNING`:
   - `WakeService` dispatches `createConnectionRequest(target).connectWithIndication()` for all authorized queued players.

### 3. Scale-to-Zero Auto-Stop Loop
1. Every 20 seconds, `IdleService` iterates over all managed servers.
2. For each server in `RUNNING` state:
   - Queries `registeredServer.getPlayersConnected().size()`.
   - If count > 0: resets `emptySince = null`.
   - If count == 0:
     - If `emptySince == null`: records `emptySince = Instant.now()`.
     - Checks if current time is within `startup-grace-period` (ignores shutdown).
     - If `now - emptySince >= idleTimeout`:
       - Dispatches `POST /hooks/stop-server?server=<name>`.
       - Transitions state to `STOPPED`.
