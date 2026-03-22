# Project - Guia detallada de arquitectura y navegacion

## 1. Mapa rapido del repositorio

- `iol-challenge/`
  - Aplicacion Spring Boot principal.
- `openspec/changes/add-http-rate-limiter/`
  - Trazabilidad funcional del cambio (proposal/design/spec/tasks/decisiones).
- `README.md`
  - Introduccion del challenge y enlaces clave.
- `guia.md`
  - Recorrido detallado del flujo de implementacion.
- `startup.md`
  - Pasos concretos para ejecutar y validar.

## 2. Que implementa este proyecto

Este proyecto funciona como **rate limiter HTTP intermedio**:

1. Recibe requests en un prefijo de entrada (`/rl` por defecto).
2. Busca la politica de cuota aplicable por metodo + ruta normalizada.
3. Calcula identidad (`api-key` y fallback a IP).
4. Evalua cuota en Redis con token bucket atomico (Lua).
5. Si excede, responde `429` con headers `X-RateLimit-*`.
6. Si permite, reenvia request al upstream preservando metodo/path/query/headers/body.

## 3. Flujo principal de runtime

### 3.1 Entrada HTTP

- Archivo: `iol-challenge/src/main/java/iolchallenge/ratelimiter/controller/RateLimiterProxyController.java`
- Responsabilidad:
  - Punto de entrada del middleware.
  - Delegar evaluacion de cuota al orquestador.
  - Construir respuesta `429` o delegar forwarding.

### 3.2 Orquestacion de cuota

- Archivo: `iol-challenge/src/main/java/iolchallenge/ratelimiter/service/RateLimiterOrchestratorService.java`
- Responsabilidad:
  - Resolver `forwardedPath` y `normalizedRoute`.
  - Resolver politica, identidad y clave de cuota.
  - Ejecutar decision final de allow/reject.

### 3.3 Forwarding HTTP

- Archivo: `iol-challenge/src/main/java/iolchallenge/ratelimiter/service/UpstreamForwardingService.java`
- Responsabilidad:
  - Reenviar requests permitidas al upstream.
  - Preservar method/path/query/headers/body.
  - Mapear respuesta upstream a `ResponseEntity`.

### 3.4 Resolucion de reglas

- Archivo: `iol-challenge/src/main/java/iolchallenge/ratelimiter/service/RateLimitRulesStore.java`
- Responsabilidad:
  - Cargar `rate-limiter-rules.yaml` al inicio.
  - Mantener snapshot atomico en memoria.
  - Refrescar por polling periodico.
  - Fallback al ultimo snapshot valido en errores de refresh.
  - Abort startup si no existe snapshot valido inicial.

### 3.5 Decision de cuota

- Archivo: `iol-challenge/src/main/java/iolchallenge/ratelimiter/service/RateLimitDecisionService.java`
- Responsabilidad:
  - Aplicar bypass si `enabled=false`.
  - Consumir token via gateway Redis.
  - Resolver degradacion fail-open/fail-closed ante error de backend.
  - Emitir decision final con `allowed`, `remaining`, `retryAfter`.

### 3.6 Token bucket distribuido

- Archivo: `iol-challenge/src/main/java/iolchallenge/ratelimiter/service/RedisTokenBucketGateway.java`
- Responsabilidad:
  - Ejecutar script Lua atomico en Redis.
  - Devolver permit/reject, remaining y retry-after dinamico.

### 3.7 Identidad y ruta normalizada

- Archivos:
  - `iol-challenge/src/main/java/iolchallenge/ratelimiter/service/IdentityExtractor.java`
  - `iol-challenge/src/main/java/iolchallenge/ratelimiter/service/RouteNormalizer.java`
- Responsabilidad:
  - Identity por header configurado (ej. `X-Api-Key`) con fallback a IP.
  - Ruta templada para evitar explosion de cardinalidad.

## 4. Configuracion

### 4.1 Propiedades

- Archivo: `iol-challenge/src/main/resources/application.yml`
- Clase mapeada: `iol-challenge/src/main/java/iolchallenge/ratelimiter/config/RateLimiterProperties.java`

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

### 4.2 Reglas YAML

- Archivo ejemplo: `iol-challenge/rate-limiter-rules.yaml`

Cada regla define:

- `method`, `route`, `upstreamUrl`
- `capacity`, `refillRatePerSecond`
- `identityHeader`, `failMode`

## 5. Contrato HTTP de rechazo

Cuando una request excede cuota:

- Status: `429 Too Many Requests`
- Headers:
  - `X-RateLimit-Limit`
  - `X-RateLimit-Remaining`
  - `X-RateLimit-Retry-After`

No se emiten headers `X-RateLimit-*` en respuestas permitidas.

## 6. Observabilidad

Metricas principales:

- `rate_limiter_requests_allowed_total`
- `rate_limiter_requests_limited_total`
- `rate_limiter_rules_refresh_errors_total`
- `rate_limiter_rules_snapshot_age_seconds`

## 7. Como leer el codigo por primera vez (orden recomendado)

1. `iol-challenge/src/main/resources/application.yml`
2. `iol-challenge/src/main/java/iolchallenge/ratelimiter/config/RateLimiterProperties.java`
3. `iol-challenge/src/main/java/iolchallenge/ratelimiter/controller/RateLimiterProxyController.java`
4. `iol-challenge/src/main/java/iolchallenge/ratelimiter/service/RateLimiterOrchestratorService.java`
5. `iol-challenge/src/main/java/iolchallenge/ratelimiter/service/UpstreamForwardingService.java`
6. `iol-challenge/src/main/java/iolchallenge/ratelimiter/service/RateLimitRulesStore.java`
7. `iol-challenge/src/main/java/iolchallenge/ratelimiter/service/RateLimitDecisionService.java`
8. `iol-challenge/src/main/java/iolchallenge/ratelimiter/service/RedisTokenBucketGateway.java`
9. Tests en `iol-challenge/src/test/java/iolchallenge/ratelimiter/`

## 8. Referencias de especificacion

- `openspec/changes/add-http-rate-limiter/proposal.md`
- `openspec/changes/add-http-rate-limiter/design.md`
- `openspec/changes/add-http-rate-limiter/tasks.md`
- `openspec/changes/add-http-rate-limiter/specs/http-rate-limiting/spec.md`
- `openspec/changes/add-http-rate-limiter/decisiones.md`

