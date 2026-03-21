## 1. Runtime HTTP intermedio (proxy)

- [x] 1.1 Define the rate-limiter input endpoint to receive client traffic.
- [x] 1.2 Implement transparent forwarding to upstream for allowed requests.
- [x] 1.3 Preserve method, path, query, headers, and body during forwarding.
- [x] 1.4 Implement initial rule loading from disk file into in-memory cache.
- [x] 1.5 Implement periodic polling refresh worker with atomic snapshot swap and fallback to the last valid snapshot.
- [x] 1.6 Define and validate YAML rule schema (required fields, units, and parsing errors).
- [x] 1.7 Configure `refreshInterval` and expose staleness/error metric logs for refresh.
- [x] 1.8 Implement bootstrap validation to abort startup if there is no valid rules snapshot.

## 2. Politica de rate limiting y configuracion

- [x] 2.1 Define properties (`enabled`, `capacity`, `refillRatePerSecond`) and validate them at startup.
- [x] 2.2 Implement token bucket with central Redis state for allow/reject decisions before forwarding.
- [x] 2.3 Implement atomic evaluation and update in Redis via Lua Script to avoid race conditions.
- [x] 2.4 Implement configurable identity extractor with default key `identity + method + normalizedRoute`.
- [x] 2.5 Implement identity fallback (`api-key` -> `client-ip`) when primary credential is missing.
- [x] 2.6 Return `429` with `X-RateLimit-Limit`, `X-RateLimit-Remaining`, and `X-RateLimit-Retry-After` when limit is exceeded.
- [x] 2.7 Ensure bypass mode when `enabled=false`.
- [x] 2.8 Implement configurable degradation policy on Redis failure (default fail-open, fail-closed override per policy/route).
- [x] 2.9 Implement v1 handling of limited requests with immediate drop (no deferred queue).
- [x] 2.10 Implement v1 quota scope only by identity+endpoint (no global bucket).
- [x] 2.11 Compute `X-RateLimit-Retry-After` dynamically in `429` based on token deficit and `refillRatePerSecond`.
- [x] 2.12 Implement route normalizer to template format for building `normalizedRoute`.

## 3. Observabilidad, compatibilidad y pruebas

- [x] 3.1 Instrument metrics for allowed and limited requests.
- [x] 3.2 Add limiter unit tests (allow and reject).
- [x] 3.3 Add proxy integration tests to validate forwarding and `429` rejection.
- [x] 3.4 Verify compatibility with at least two different upstreams without code changes in the limiter.
- [x] 3.5 Validate compilation and run smoke test under controlled load.
- [x] 3.6 Add resilience tests for Redis failure validating fail-open and fail-closed behavior.
- [x] 3.7 Add tests to verify limited requests are not queued in v1.
- [x] 3.8 Add tests to validate quota independence across endpoints for the same identity.
- [x] 3.9 Add tests for `X-RateLimit-*` header contract in `429` responses.
- [x] 3.10 Add tests to verify allowed responses do not include `X-RateLimit-*` headers in v1.
- [x] 3.11 Add tests for failed startup when no valid rules snapshot exists.
- [x] 3.12 Add continuity tests with prolonged stale snapshot under successive refresh failures.
- [x] 3.13 Add tests to validate dynamic calculation of `X-RateLimit-Retry-After`.
- [x] 3.14 Add tests to validate that different path IDs map to the same endpoint `normalizedRoute`.

## 4. Roadmap de evolucion

- [x] 4.1 Explicitly document v2 hybrid architecture strategy (local cache + central Redis) and activation criteria.

## 5. Documentacion y navegacion

- [x] 5.1 Create `project.md` with detailed code-navigation and architecture guide.
- [x] 5.2 Create `README.md` with challenge introduction, OpenSpec links, and access to `startup.md`.
- [x] 5.3 Create `startup.md` with project startup and testing steps.
- [x] 5.4 Add documentation comments in Spanish on key methods of the rate-limiter module.

