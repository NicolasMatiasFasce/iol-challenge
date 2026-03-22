## Why

The organization needs a shared component to control HTTP traffic rate without coupling to a specific API server. Instead of limiting on the client side or inside each server, this project must operate in the middle of the traffic as a standalone rate limiter that protects multiple upstream services with consistent behavior.

## What Changes

- The project becomes a standalone HTTP rate-limiting service, positioned between client and upstream API (proxy/interceptor pattern).
- A server-agnostic limit-policy model is defined (by client identity, route, and method).
- A standard limit-exceeded response is defined with status code `429` and quota metadata in headers.
- Transparent forwarding to upstream is added when the request is allowed.
- Operational metrics and allow/reject decision traceability are added.
- An integration contract for multiple API servers in the same organization is documented.

## Capabilities

### New Capabilities
- `http-rate-limiting`: Executes server-agnostic rate limiting as an intermediary layer between client and API server, with forwarding and observability.

### Modified Capabilities
- None.

## Impact

- Affected code: HTTP inbound/outbound pipeline, upstream routing, rate-limit policies, and configuration.
- APIs: an HTTP interface for the limiter (proxy) is introduced to receive and forward client traffic.
- Dependencies: keep implementation based on standard Java/Spring capabilities without coupling to a specific gateway/vendor.
- Operations: requires per-environment configuration (limits, identity keys, upstreams) and continuous monitoring of `allow/reject`.

