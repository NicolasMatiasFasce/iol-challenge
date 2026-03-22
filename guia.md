# Guia detallada de implementacion

## 1. Objetivo del sistema

Este proyecto implementa un **rate limiter HTTP intermedio**. La aplicacion recibe trafico en un prefijo (`/rl`), decide si una request se permite o se rechaza, y si se permite la reenvia al upstream configurado.

En una frase: **cliente -> rate limiter -> upstream**.

## 2. Flujo completo de una request

### Paso 1: Entrada HTTP

Archivo principal de entrada:

- `iol-challenge/src/main/java/iolchallenge/ratelimiter/controller/RateLimiterProxyController.java`

Metodo clave:

- `proxy(HttpServletRequest request)`

Que hace:

1. Delega evaluacion de cuota al orquestador.
2. Si no se permite, responde `429` con headers `X-RateLimit-*`.
3. Si se permite, delega forwarding al servicio de transporte.

Servicio orquestador:

- `iol-challenge/src/main/java/iolchallenge/ratelimiter/service/RateLimiterOrchestratorService.java`

Servicio de forwarding:

- `iol-challenge/src/main/java/iolchallenge/ratelimiter/service/UpstreamForwardingService.java`

### Paso 2: Normalizacion de ruta

Archivo:

- `iol-challenge/src/main/java/iolchallenge/ratelimiter/service/RouteNormalizer.java`

Metodo clave:

- `normalize(String rawPath)`

Comportamiento:

- Segmentos numericos y UUID se reemplazan por `{id}`.
- Ejemplo:
  - `/users/123/orders/550e8400-e29b-41d4-a716-446655440000`
  - queda como `/users/{id}/orders/{id}`

Esto ayuda a compartir cuota entre requests del mismo endpoint funcional.

### Paso 3: Extraccion de identidad

Archivo:

- `iol-challenge/src/main/java/iolchallenge/ratelimiter/service/IdentityExtractor.java`

Metodo clave:

- `extract(HttpServletRequest request, String identityHeader)`

Orden de prioridad:

1. Header configurado (por ejemplo `X-Api-Key`).
2. Primer valor de `X-Forwarded-For`.
3. `remoteAddr`.

### Paso 4: Resolucion de reglas

Archivo:

- `iol-challenge/src/main/java/iolchallenge/ratelimiter/service/RateLimitRulesStore.java`

Responsabilidades:

- Carga inicial de `rate-limiter-rules.yaml` al arrancar.
- Refresh periodico por polling.
- Snapshot atomico en memoria.
- Fallback al ultimo snapshot valido en error de refresh.
- Fail-fast en startup si no existe snapshot valido inicial.

Metodo clave de runtime:

- `resolve(String method, String normalizedRoute)`

Busca con precedencia:

1. `METHOD::/ruta`
2. `*::/ruta`
3. `METHOD::*`
4. `*::*`

### Paso 5: Decision de cuota

Archivo:

- `iol-challenge/src/main/java/iolchallenge/ratelimiter/service/RateLimitDecisionService.java`

Metodo clave:

- `evaluate(String quotaKey, RateLimitPolicy policy)`

Comportamiento:

- Si `enabled=false`, bypass (permitir).
- Si `enabled=true`, consume token via gateway Redis.
- Si Redis falla:
  - `FAIL_OPEN`: permite.
  - `FAIL_CLOSED`: rechaza.

Tambien incrementa metricas de permitidas/limitadas.

### Paso 6: Token bucket distribuido (Redis + Lua)

Archivo:

- `iol-challenge/src/main/java/iolchallenge/ratelimiter/service/RedisTokenBucketGateway.java`

Metodo clave:

- `consume(String key, int capacity, int refillRatePerSecond)`

Internamente ejecuta un Lua script atomico que:

1. Lee tokens y timestamp actuales.
2. Recalcula tokens por refill temporal.
3. Consume si hay disponibilidad.
4. Si no hay, calcula `retryAfter` dinamico.
5. Persiste estado y TTL.

## 3. Forwarding al upstream

En `UpstreamForwardingService`:

- `forward(...)` arma la request hacia upstream.
- Preserva method/path/query/headers/body.
- Filtra algunos hop-by-hop headers (`host`, `content-length`, `transfer-encoding`, `connection`).

Importante para performance:

- El body se lee **despues** de decidir cuota. Si la request queda en `429`, no se incurre en costo de lectura del payload para forwarding.

## 4. Configuracion clave

Archivo:

- `iol-challenge/src/main/resources/application.yml`

Clase:

- `iol-challenge/src/main/java/iolchallenge/ratelimiter/config/RateLimiterProperties.java`

Propiedades importantes:

- `rate-limiter.enabled`
- `rate-limiter.endpoint-prefix`
- `rate-limiter.rules-path`
- `rate-limiter.refresh-interval`
- `rate-limiter.default-capacity`
- `rate-limiter.default-refill-rate-per-second`
- `rate-limiter.default-identity-header`
- `rate-limiter.default-fail-mode`
- `rate-limiter.default-upstream-url`

Reglas YAML de ejemplo:

- `iol-challenge/rate-limiter-rules.yaml`

## 5. Contrato HTTP de rechazo

Cuando una request supera cuota:

- Status: `429 Too Many Requests`
- Headers:
  - `X-RateLimit-Limit`
  - `X-RateLimit-Remaining`
  - `X-RateLimit-Retry-After`

## 6. Observabilidad

Metricas actuales:

- `rate_limiter_requests_allowed_total`
- `rate_limiter_requests_limited_total`
- `rate_limiter_rules_refresh_errors_total`
- `rate_limiter_rules_snapshot_age_seconds`

Logs relevantes:

- Rechazos (`429`) y forwarding en el controller.
- Degradacion fail-open/fail-closed en decision service.
- Errores de respuesta Lua y consumo en Redis gateway.

## 7. Orden recomendado para leer codigo

1. `iol-challenge/src/main/resources/application.yml`
2. `iol-challenge/src/main/java/iolchallenge/ratelimiter/config/RateLimiterProperties.java`
3. `iol-challenge/src/main/java/iolchallenge/ratelimiter/controller/RateLimiterProxyController.java`
4. `iol-challenge/src/main/java/iolchallenge/ratelimiter/service/RateLimiterOrchestratorService.java`
5. `iol-challenge/src/main/java/iolchallenge/ratelimiter/service/UpstreamForwardingService.java`
6. `iol-challenge/src/main/java/iolchallenge/ratelimiter/service/RateLimitRulesStore.java`
7. `iol-challenge/src/main/java/iolchallenge/ratelimiter/service/RateLimitDecisionService.java`
8. `iol-challenge/src/main/java/iolchallenge/ratelimiter/service/RedisTokenBucketGateway.java`
9. Tests en `iol-challenge/src/test/java/iolchallenge/ratelimiter/`

## 8. Scripts agregados

- `scripts/dev-up.sh`
  - Levanta Redis y la app.
- `scripts/dev-up-and-test.sh`
  - Llama a `dev-up.sh`, ejecuta pruebas automaticas y genera reporte.

