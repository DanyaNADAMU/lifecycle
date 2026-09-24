# ADR 0002: Soft Auth Integration & Zero-Bypass Security Model

## Status
Accepted

## Context
In a proxy network utilizing an authentication gate (NanoLimbo + `nadamu-auth`), an independent lifecycle management plugin could inadvertently introduce authentication bypass vulnerabilities (Race Conditions):
1. A malicious player connects under an administrative username via a forced host (e.g. `pvp.nada.mu`).
2. The lifecycle plugin wakes the target server.
3. If the lifecycle plugin automatically transfers any player waiting for that server upon boot completion, the unauthenticated player could bypass the password prompt and gain unauthorized access to the gameplay server.

Additionally, `lifecycle` should remain decoupled from `nadamu-auth`, functioning seamlessly in standalone mode or with alternative auth setups.

## Decision
1. **Optional Soft Dependency**:
   `lifecycle` declares an optional dependency on `nadamu-auth` (`@Dependency(id = "nadamu-auth", optional = true)`).
   At runtime, `AuthBridge` checks whether `nadamu-auth` is loaded. If absent, connection requests proceed directly. If present, it checks the player's `AuthState`.
2. **Explicit Transfer Invariant**:
   `lifecycle` never executes a server transfer (`connectWithIndication`) for any player whose state is `PENDING_LOGIN`.
3. **Defense-in-Depth via Velocity PostOrder**:
   - `lifecycle` hooks into `PlayerChooseInitialServerEvent` and `ServerPreConnectEvent` at `PostOrder.FIRST` or `PostOrder.EARLY`.
   - `nadamu-auth` maintains its terminal firewall in `RestrictionListener` at `PostOrder.LAST`. Even if a lifecycle defect occurred, `nadamu-auth` denies unauthorized outbound connections.
4. **Parallel Warmup Without Premature Routing**:
   Waking the container starts immediately on forced-host detection, but routing remains strictly blocked until credential verification succeeds.

## Consequences
### Positive
- Elimination of authentication bypass attack vectors.
- Completely decoupled codebase: `lifecycle` can be deployed independently.
- Optimal player experience through parallel container initialization.
