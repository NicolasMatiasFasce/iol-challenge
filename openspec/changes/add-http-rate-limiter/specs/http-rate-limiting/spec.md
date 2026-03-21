## ADDED Requirements

### Requirement: The service SHALL act as an HTTP intermediary rate limiter
The system SHALL operate in the middle of HTTP traffic between client and upstream API, evaluating each request before forwarding.

#### Scenario: Request permitida y reenviada
- **WHEN** a request arrives and the rate-limiting policy allows execution
- **THEN** the system forwards the request to the configured upstream and returns its response

#### Scenario: Request rechazada por limite
- **WHEN** a request arrives and the rate-limiting policy does not allow execution
- **THEN** the system responds with `429 Too Many Requests` without forwarding upstream

### Requirement: Rate limiting SHALL be server-agnostic
The system SHALL apply the same limiting rules regardless of the destination API server type.

#### Scenario: Integracion con multiples upstreams
- **WHEN** two or more destination API servers exist with different technologies
- **THEN** the system applies rate-limiting policies with the same configuration and response contract

#### Scenario: Politica desacoplada del vendor
- **WHEN** the upstream API server is changed while keeping the same policy configuration
- **THEN** allow/reject limiter behavior does not change due to vendor differences

### Requirement: Rate limiting SHALL be configurable by environment
The system SHALL allow limiter parameters to be configured per environment to control behavior and capacity.

#### Scenario: Configuración válida aplicada al iniciar
- **WHEN** the service starts with valid rate-limiting properties
- **THEN** the limiter uses `enabled`, `capacity`, and `refillRatePerSecond` defined in configuration

#### Scenario: Feature deshabilitada
- **WHEN** the rate-limiting enable property is false
- **THEN** requests are forwarded without limit evaluation

### Requirement: Rate limiting rules SHALL be loaded from disk and cached
The system SHALL load rate-limiting rules from a disk file and keep them in in-memory cache with periodic refresh.

#### Scenario: Carga inicial de reglas
- **WHEN** the service starts and the YAML rules file is valid
- **THEN** the middleware loads rules into cache and uses them to evaluate requests

#### Scenario: Fallback a ultimo snapshot valido
- **WHEN** the periodic refresh process finds invalid rules
- **THEN** the middleware keeps the last valid rules snapshot and logs the error

#### Scenario: Arranque sin snapshot valido
- **WHEN** the service starts and no valid YAML rules snapshot exists
- **THEN** startup fails explicitly and the service does not accept traffic

#### Scenario: Refresh por polling configurable
- **WHEN** the worker runs the polling cycle based on its configured interval
- **THEN** the middleware refreshes the rules snapshot only when the new load is valid

#### Scenario: Snapshot stale prolongado en runtime
- **WHEN** consecutive refresh failures occur after a valid initialization
- **THEN** the middleware keeps evaluating requests with the last valid snapshot without stopping the service due to TTL

### Requirement: The service SHALL support configurable identity keys for quotas
The system SHALL allow selecting the identity key used for quotas (for example client IP, API key, or route combination).

#### Scenario: Clave compuesta por defecto
- **WHEN** the identity policy does not define an explicit override
- **THEN** quota is calculated with composite key `identity + method + normalizedRoute`

#### Scenario: Fallback de identidad
- **WHEN** the policy uses `api-key` as primary identity and the header is missing
- **THEN** the system uses `client-ip` as fallback to build the quota key

#### Scenario: Limite por API key
- **WHEN** the identity policy is configured for API key header
- **THEN** quota is calculated by the value of that header

#### Scenario: Limite por IP cliente
- **WHEN** the identity policy is configured for client IP
- **THEN** quota is calculated by source IP address

### Requirement: Quota scope SHALL be identity-plus-endpoint in v1
The system SHALL evaluate limits in v1 using only identity-plus-endpoint scope (`method + normalizedRoute`), without an aggregated global bucket.

#### Scenario: Dos endpoints independientes
- **WHEN** the same identity consumes quota on two different endpoints
- **THEN** each endpoint keeps its own independent quota counter

#### Scenario: Ruta templada para cuota
- **WHEN** two requests share the same functional endpoint but differ in path IDs
- **THEN** both use the same templated `normalizedRoute` to calculate quota

### Requirement: Rate limit rejections SHALL expose standard quota metadata
The system SHALL include quota metadata in response headers for requests rejected by limit.

#### Scenario: Rechazo con informacion de cuota
- **WHEN** a request is rejected by rate limiting
- **THEN** the `429` response includes `X-RateLimit-Limit`, `X-RateLimit-Remaining`, and `X-RateLimit-Retry-After` dynamically calculated from token deficit and refill rate

#### Scenario: Respuesta permitida sin headers de cuota
- **WHEN** a request is allowed and forwarded upstream
- **THEN** the response does not include `X-RateLimit-*` headers generated by the rate-limiting middleware

### Requirement: Rate-limited requests SHALL be dropped in v1
The system SHALL drop rate-limited requests immediately in v1, without deferred queueing.

#### Scenario: Exceso de limite sin encolado
- **WHEN** a request exceeds the configured limit
- **THEN** the system responds with `429` and does not enqueue the request for later processing

#### Scenario: Hard rate limiting en v1
- **WHEN** a request exceeds the configured limit in v1
- **THEN** the system does not apply soft mode and keeps immediate rejection

### Requirement: The service SHALL provide fault-tolerant degradation modes
The system SHALL define a configurable policy for when the rate-limiting backend (Redis) is unavailable.

#### Scenario: Degradacion por defecto fail-open
- **WHEN** Redis is unavailable and the effective policy is `fail-open`
- **THEN** the request is allowed and forwarded upstream

#### Scenario: Degradacion sensible fail-closed
- **WHEN** Redis is unavailable and the effective route/policy is `fail-closed`
- **THEN** the request is rejected with an explicit response without forwarding upstream

### Requirement: Rate limiting SHALL produce operational metrics
The system SHALL record metrics to observe limiter behavior and support operational tuning.

#### Scenario: Solicitud permitida incrementa métrica
- **WHEN** a request is allowed and forwarded
- **THEN** the allowed-requests counter is incremented

#### Scenario: Solicitud rechazada incrementa métrica
- **WHEN** a request is rejected by rate-limiting policy
- **THEN** the limited-requests counter is incremented


