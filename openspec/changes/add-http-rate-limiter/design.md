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

1. Arquitectura proxy/interceptor en el medio del trafico.
   - Rationale: enables integration with heterogeneous clients and API servers without deep changes in each service.
   - Alternativas: limiting in the client or in each API server is discarded due to duplication and coupling.

2. Politica de rate limiting desacoplada del upstream.
   - Rationale: allow/reject decisions depend on an identity key and request descriptor, not on the destination server type.
   - Alternativas: ad-hoc rules per API server are discarded because of low reusability.

3. Algoritmo token bucket con estado centralizado en Redis para v1.
   - Rationale: keeps token-bucket simplicity and supports distributed deployment in a central gateway.
   - Parametros: `enabled`, `capacity`, `refillRatePerSecond`.
   - Nota de versionado: a hybrid option with local cache + central Redis is planned for v2.

4. Atomicidad de decisiones en Redis mediante Lua Script para v1.
   - Rationale: avoids race conditions in concurrent scenarios without the cost of distributed locks.
   - Alcance: counter/state read, limit evaluation, and update in one atomic operation.

5. Contrato HTTP de throttling en v1: opcion A (headers minimos `X-RateLimit-*`).
   - Rationale: provides clear information to clients with low implementation complexity for the challenge.
   - Decision: include `X-RateLimit-Limit`, `X-RateLimit-Remaining`, and `X-RateLimit-Retry-After` in `429` responses.

6. Forwarding transparente para requests permitidas.
   - Rationale: the component adds rate control without unnecessarily changing upstream business semantics.

7. Exponer metricas de permitidas/rechazadas con instrumentacion existente.
   - Rationale: enables operational tuning and early detection of inadequate policies.

8. Politica de degradacion configurable ante falla de Redis/rate-limiter backend.
   - Rationale: balances high availability and upstream protection based on each route/policy criticality.
   - Decision: global default `fail-open`, with `fail-closed` override per sensitive policy/endpoint.

9. Clave de cuota por defecto compuesta (`identity + method + normalizedRoute`).
   - Rationale: prevents one route from consuming another route's quota and improves fairness in multi-endpoint scenarios.
   - Decision: `identity` uses `api-key` when present with `client-ip` fallback; alternative modes are supported by configuration.

10. Manejo de requests rate-limited en v1: opcion A (drop inmediato).
   - Rationale: minimizes latency and operational complexity for the challenge.
   - Decision: when the limit is exceeded, return `429` and drop the request immediately, with no waiting or deferred queue in v1.
   - Nota de versionado: v1 uses hard rate limiting; v2 may evolve to a mixed policy-based model.

11. Fuente de reglas en v1: opcion A (archivo en disco + worker de refresh a cache).
   - Rationale: keeps operations simple for the challenge and follows chapter 4 flow (rules on disk, periodic load into cache).
   - Decision: load rules from a versioned local file, refresh them periodically into in-memory cache, and use the last valid snapshot on parse errors.

12. Alcance de cuotas en v1: opcion A (solo identidad+endpoint).
   - Rationale: simplifies initial configuration and prioritizes fairness by client/route.
   - Decision: no aggregated global limit is applied in v1; each decision uses key `identity + method + normalizedRoute`.

13. Emision de headers de cuota en v1: opcion A (solo en respuestas `429`).
   - Rationale: reduces complexity and serialization overhead for allowed requests.
   - Decision: `X-RateLimit-Limit`, `X-RateLimit-Remaining`, and `X-RateLimit-Retry-After` are emitted only when the request is throttled.

14. Formato de reglas en disco para v1: opcion A (YAML).
   - Rationale: improves operational readability and stays aligned with chapter 4 examples.
   - Decision: policies are defined in versioned YAML files on disk and validated on load.

15. Mecanismo de refresh de reglas en v1: opcion A (polling periodico).
   - Rationale: it is portable, simple, and predictable enough for the challenge.
   - Decision: workers update cache at configurable intervals; on read/parse errors, the last valid snapshot is kept.

16. Comportamiento de arranque sin reglas validas en v1: opcion C (abort startup).
   - Rationale: guarantees a deterministic initial state and avoids exposing the system without a defined control policy.
   - Decision: if no valid snapshot exists at startup, the service does not start; fallback to the last snapshot applies only after successful initialization.

17. Manejo de snapshot stale en runtime para v1: opcion A (aceptar indefinidamente).
   - Rationale: prioritizes service continuity during prolonged refresh mechanism failures.
   - Decision: if successive refreshes fail, the middleware keeps operating with the last valid snapshot without an automatic blocking TTL.

18. Calculo de `X-RateLimit-Retry-After` en v1: opcion B (dinamico).
   - Rationale: provides a more precise retry signal for clients and reduces retry storms.
   - Decision: for each `429`, `X-RateLimit-Retry-After` is calculated from token deficit and `refillRatePerSecond` of the applied policy.

19. Normalizacion de ruta para clave de cuota en v1: opcion B (ruta templada).
   - Rationale: reduces key cardinality without losing endpoint-level granularity.
   - Decision: `normalizedRoute` uses templated format (for example `/users/{id}/orders/{orderId}`) instead of full literal route.

20. Header de reintento en v1: opcion A (solo `X-RateLimit-Retry-After`).
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


