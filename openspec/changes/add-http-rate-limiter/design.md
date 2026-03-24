## Context

This project is redefined as an infrastructure component that sits between clients and API servers, intercepting HTTP traffic to apply rate-limiting policies before forwarding upstream. The goal is to provide a single, reusable solution that is not coupled to any specific server or gateway.

## Goals / Non-Goals

**Goals:**
- Expose an intermediary HTTP endpoint/proxy that receives client requests and forwards them to upstreams.
- Apply configurable rate limiting by client identity and route/method criteria.
- Return a standard `429` response with quota headers when the limit is exceeded.
- Maintain compatibility with multiple API servers without vendor dependencies.
- Expose metrics for observability and policy tuning.

**Non-Goals:**
- Replace full enterprise gateways (auth, complex transformations, WAF).
- Implement commercial billing/tenant quotas in this first version.
- Solve global HA/distributed quota state in this first stage.

## Decisions

1. Proxy/interceptor architecture in the traffic middle layer.
   - Rationale: enables integration with heterogeneous clients and API servers without deep changes in each service.
   - Alternatives: limiting in the client or in each API server is discarded due to duplication and coupling.

2. Upstream-decoupled rate-limiting policy.
   - Rationale: allow/reject decisions depend on an identity key and request descriptor, not on the destination server type.
   - Alternatives: ad-hoc rules per API server are discarded because of low reusability.

3. Token bucket algorithm with centralized Redis state for v1.
   - Rationale: keeps token-bucket simplicity and supports distributed deployment in a central gateway.
   - Parameters: `enabled`, `capacity`, `refillRatePerSecond`.
   - Versioning note: a hybrid option with local cache + central Redis is planned for v2.

4. Atomic decisioning in Redis using Lua Script for v1.
   - Rationale: avoids race conditions in concurrent scenarios without the cost of distributed locks.
   - Scope: counter/state read, limit evaluation, and update in one atomic operation.

5. HTTP throttling contract in v1: option A (minimal `X-RateLimit-*` headers).
   - Rationale: provides clear information to clients with low implementation complexity for the challenge.
   - Decision: include `X-RateLimit-Limit`, `X-RateLimit-Remaining`, and `X-RateLimit-Retry-After` in `429` responses.

6. Transparent forwarding for allowed requests.
   - Rationale: the component adds rate control without unnecessarily changing upstream business semantics.

7. Expose allowed/rejected metrics with existing instrumentation.
   - Rationale: enables operational tuning and early detection of inadequate policies.

8. Configurable degradation policy on Redis/rate-limiter backend failure.
   - Rationale: balances high availability and upstream protection based on each route/policy criticality.
   - Decision: global default `fail-open`, with `fail-closed` override per sensitive policy/endpoint.

9. Default composite quota key (`identity + method + normalizedRoute`).
   - Rationale: prevents one route from consuming another route's quota and improves fairness in multi-endpoint scenarios.
   - Decision: `identity` uses `api-key` when present with `client-ip` fallback; alternative modes are supported by configuration.

10. Handling of rate-limited requests in v1: option A (immediate drop).
    - Rationale: minimizes latency and operational complexity for the challenge.
    - Decision: when the limit is exceeded, return `429` and drop the request immediately, with no waiting or deferred queue in v1.
    - Versioning note: v1 uses hard rate limiting; v2 may evolve to a mixed policy-based model.

11. Rule source in v1: option A (disk file + cache-refresh worker).
    - Rationale: keeps operations simple for the challenge and follows chapter 4 flow (rules on disk, periodic load into cache).
    - Decision: load rules from a versioned local file, refresh them periodically into in-memory cache, and use the last valid snapshot on parse errors.

12. Quota scope in v1: option A (identity+endpoint only).
    - Rationale: simplifies initial configuration and prioritizes fairness by client/route.
    - Decision: no aggregated global limit is applied in v1; each decision uses key `identity + method + normalizedRoute`.

13. Quota header emission in v1: option A (only in `429` responses).
    - Rationale: reduces complexity and serialization overhead for allowed requests.
    - Decision: `X-RateLimit-Limit`, `X-RateLimit-Remaining`, and `X-RateLimit-Retry-After` are emitted only when the request is throttled.

14. Rule format on disk for v1: option A (YAML).
    - Rationale: improves operational readability and stays aligned with chapter 4 examples.
    - Decision: policies are defined in versioned YAML files on disk and validated on load.

15. Rule refresh mechanism in v1: option A (periodic polling).
    - Rationale: it is portable, simple, and predictable enough for the challenge.
    - Decision: workers update cache at configurable intervals; on read/parse errors, the last valid snapshot is kept.

16. Startup behavior without valid rules in v1: option C (abort startup).
    - Rationale: guarantees a deterministic initial state and avoids exposing the system without a defined control policy.
    - Decision: if no valid snapshot exists at startup, the service does not start; fallback to the last snapshot applies only after successful initialization.

17. Runtime stale-snapshot handling in v1: option A (accept indefinitely).
    - Rationale: prioritizes service continuity during prolonged refresh mechanism failures.
    - Decision: if successive refreshes fail, the middleware keeps operating with the last valid snapshot without an automatic blocking TTL.

18. `X-RateLimit-Retry-After` calculation in v1: option B (dynamic).
    - Rationale: provides a more precise retry signal for clients and reduces retry storms.
    - Decision: for each `429`, `X-RateLimit-Retry-After` is calculated from token deficit and `refillRatePerSecond` of the applied policy.

19. Route normalization for quota key in v1: option B (templated route).
    - Rationale: reduces key cardinality without losing endpoint-level granularity.
    - Decision: `normalizedRoute` uses templated format (for example `/users/{id}/orders/{orderId}`) instead of full literal route.

20. Retry header in v1: option A (only `X-RateLimit-Retry-After`).
    - Rationale: reduces contract complexity and keeps consistency with the minimum `X-RateLimit-*` set.
    - Decision: on `429`, emit `X-RateLimit-Retry-After` with a dynamic value.

## Risks / Trade-offs

- [Risk] Dependency on central Redis can impact availability/latency -> Mitigation: timeouts, degradation policies, and active backend monitoring.
- [Risk] `fail-open` can allow traffic above the limit during incidents -> Mitigation: use `fail-closed` on sensitive routes and degradation alerts.
- [Risk] High key cardinality (`identity + method + route`) can increase Redis memory usage -> Mitigation: normalize routes and apply TTL/domain monitoring.
- [Trade-off] Templated route normalization requires a consistent normalizer to avoid collisions or bucket fragmentation.
- [Trade-off] No queue for limited requests in v1 -> lower complexity, but some recoverable operations are not automatically reprocessed.
- [Risk] Rules can become outdated due to refresh failures -> Mitigation: fallback to last valid snapshot, staleness metrics, and alerts.
- [Trade-off] Fail-fast startup without a valid snapshot reduces availability under initial configuration errors.
- [Trade-off] Accepting stale snapshots indefinitely can delay policy updates.
- [Trade-off] No global bucket in v1 -> a burst distributed across many identities can increase total pressure on upstream.
- [Risk] Incorrect identity mapping (IP/header) can create unfair quotas -> Mitigation: environment-configurable identity extractor.
- [Trade-off] v1 prioritizes distributed consistency with central Redis over minimum local latency.
- [Trade-off] v2 hybrid mode (local cache + Redis) increases complexity to gain performance on hot paths.

## Migration Plan

1. Introduce proxy HTTP runtime with basic upstream forwarding.
2. Integrate pre-forwarding rate-limit decision using central Redis (v1).
3. Configure default policy, identity extractor, and degradation strategy (`fail-open`/`fail-closed`).
4. Enable metrics and validate under controlled load.
5. Roll out progressively by environment and tune limits.
6. Define v2 roadmap for local cache + central Redis without breaking the HTTP contract.
7. Rollback: bypass mode (`enabled=false`) to forward without limiting.

## Open Questions


