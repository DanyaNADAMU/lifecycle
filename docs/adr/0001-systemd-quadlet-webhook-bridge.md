# ADR 0001: Systemd Quadlet Control via Host Webhook Bridge

## Status
Accepted

## Context
The Minecraft network is hosted on Debian 12 using Rootless Podman managed by Quadlet (`~/.config/containers/systemd/`).
In this setup:
- Servers are defined as templated systemd services (`mc@<name>.container`).
- Podman Quadlet generates service units with the `--rm` flag, meaning stopped containers are automatically deleted from Podman engine memory.
- Traditional Docker socket proxies (`tecnativa/docker-socket-proxy`) cannot start stopped Quadlet containers because `POST /containers/<name>/start` fails with `404 Not Found` once the container has exited.
- The authoritative lifecycle manager is `systemd --user`.

Directly interacting with systemd from inside the Java container (`amazoncorretto:25`) via D-Bus (`/run/user/1001/bus`) introduces severe complexity: D-Bus socket path escaping, JNI transports, complex serialization, and user-namespace credential mapping issues.

## Decision
We deploy the official, mature `webhook` daemon (by Adnan Hajdarević, packaged directly in Debian 12) as a native `systemd --user` service under the `minecraft` user on the host.
`lifecycle` communicates with this webhook bridge over standard HTTP REST (`http://host.containers.internal:8012`).

The bridge executes:
- Start: `systemctl --user start mc@<server>`
- Stop: `systemctl --user stop mc@<server>`
- Status: `systemctl --user is-active mc@<server>`

Hook endpoints, query parameter names, and unit name templates (`unit-template: "mc@{server}"`) are fully configurable in `config.yml`.
Parameters are strictly sanitized in `hooks.json` using regex (`^mc@[a-zA-Z0-9_-]+$`) to prevent command injection.
For status checks (`systemctl is-active`), when a service is inactive, systemctl exits with code 3. `hooks.json` must include `"include-command-output-in-response-on-error": true` so `adnanh/webhook` passes the output (`inactive`) back in the response body.

## Consequences
### Positive
- Zero external native dependencies in the Velocity Java plugin (pure `java.net.http.HttpClient`).
- 100% compatibility with Quadlet's declarative lifecycle management.
- Robust execution in the unprivileged user session without root escalation risks.

### Negative
- Requires the `webhook` service to be running as a systemd user unit on the host machine.
