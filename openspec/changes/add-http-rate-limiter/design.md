## Context

Este proyecto se redefine como un componente de infraestructura que se ubica entre clientes y API servers, interceptando trafico HTTP para aplicar politicas de rate limiting antes de reenviar al upstream. El objetivo es tener una solucion unica, reutilizable y no acoplada a un servidor o gateway especifico.

## Goals / Non-Goals

**Goals:**
- Exponer un endpoint/proxy HTTP intermedio que reciba requests de clientes y las reenvie a upstreams.
- Aplicar rate limiting por identidad de cliente y criterio de ruta/metodo de forma configurable.
- Responder de manera estandar con `429` y headers de cuota cuando se excede el limite.
- Mantener compatibilidad con multiples API servers sin dependencias de vendor.
- Exponer metricas para observabilidad y ajuste de politicas.

**Non-Goals:**
- Reemplazar gateways corporativos completos (auth, transformaciones complejas, WAF).
- Implementar billing/cuotas comerciales por tenant en esta primera version.
- Resolver HA/distribucion global de estado de cuotas en esta primera etapa.

## Decisions

1. Arquitectura proxy/interceptor en el medio del trafico.
   - Rationale: permite integrar clientes y API servers heterogeneos sin cambios profundos en cada servicio.
   - Alternativas: limitar en cliente o en cada API server se descarta por duplicacion y acople.

2. Politica de rate limiting desacoplada del upstream.
   - Rationale: la decision de permitir/rechazar depende de una clave de identidad y del descriptor de request, no del tipo de server de destino.
   - Alternativas: reglas ad hoc por API server, descartadas por baja reutilizacion.

3. Algoritmo token bucket con estado centralizado en Redis para v1.
   - Rationale: mantiene simplicidad del token bucket y soporta despliegue distribuido en gateway central.
   - Parametros: `enabled`, `capacity`, `refillRatePerSecond`.
   - Nota de versionado: para v2 se planifica opcion hibrida con cache local + Redis central.

4. Atomicidad de decisiones en Redis mediante Lua Script para v1.
   - Rationale: evita race conditions en escenarios concurrentes sin costo de locks distribuidos.
   - Alcance: lectura de contador/estado, evaluacion de limite y actualizacion en una operacion atomica.

5. Contrato HTTP de throttling en v1: opcion A (headers minimos `X-RateLimit-*`).
   - Rationale: entrega informacion clara al cliente con baja complejidad de implementacion para el challenge.
   - Decision: en respuestas `429`, incluir `X-RateLimit-Limit`, `X-RateLimit-Remaining` y `X-RateLimit-Retry-After`.

6. Forwarding transparente para requests permitidas.
   - Rationale: el componente agrega control de tasa sin alterar innecesariamente semantica de negocio del upstream.

7. Exponer metricas de permitidas/rechazadas con instrumentacion existente.
   - Rationale: permite tuning operativo y deteccion temprana de politicas inadecuadas.

8. Politica de degradacion configurable ante falla de Redis/rate-limiter backend.
   - Rationale: balancea alta disponibilidad y proteccion de upstreams segun criticidad de cada ruta/politica.
   - Decision: default global `fail-open`, con override `fail-closed` por politica/endpoint sensible.

9. Clave de cuota por defecto compuesta (`identity + method + normalizedRoute`).
   - Rationale: evita que una ruta consuma cuota de otra y mejora fairness en escenarios multi-endpoint.
   - Decision: `identity` usa `api-key` cuando existe y fallback a `client-ip`; se soportan modos alternativos por configuracion.

10. Manejo de requests rate-limited en v1: opcion A (drop inmediato).
   - Rationale: minimiza latencia y complejidad operativa para el challenge.
   - Decision: ante limite excedido, responder `429` y descartar la request inmediatamente, sin espera ni encolado diferido en v1.
   - Nota de versionado: v1 aplica hard rate limiting; v2 puede evolucionar a esquema mixto por politica.

11. Fuente de reglas en v1: opcion A (archivo en disco + worker de refresh a cache).
   - Rationale: mantiene simplicidad operativa para challenge y sigue el flujo del capitulo 4 (reglas en disco, carga periodica a cache).
   - Decision: cargar reglas desde archivo local versionado, refrescarlas periodicamente en cache en memoria y usar ultimo snapshot valido ante error de parseo.

12. Alcance de cuotas en v1: opcion A (solo identidad+endpoint).
   - Rationale: simplifica configuracion inicial y prioriza fairness por cliente/ruta.
   - Decision: no se aplica limite global agregado en v1; cada decision usa clave `identity + method + normalizedRoute`.

13. Emision de headers de cuota en v1: opcion A (solo en respuestas `429`).
   - Rationale: reduce complejidad y overhead de serializacion en requests permitidas.
   - Decision: `X-RateLimit-Limit`, `X-RateLimit-Remaining` y `X-RateLimit-Retry-After` se emiten solo cuando la request es throttled.

14. Formato de reglas en disco para v1: opcion A (YAML).
   - Rationale: mejora legibilidad operativa y mantiene alineacion con ejemplos del capitulo 4.
   - Decision: las politicas se definen en archivos YAML versionados en disco y se validan al cargar.

15. Mecanismo de refresh de reglas en v1: opcion A (polling periodico).
   - Rationale: es portable, simple y suficientemente predecible para el challenge.
   - Decision: workers actualizan cache en intervalos configurables; ante error de lectura/parseo se conserva el ultimo snapshot valido.

16. Comportamiento de arranque sin reglas validas en v1: opcion C (abort startup).
   - Rationale: garantiza estado inicial determinista y evita exponer el sistema sin politica de control definida.
   - Decision: si no existe snapshot valido al iniciar, el servicio no levanta; el fallback al ultimo snapshot aplica solo despues de una inicializacion exitosa.

17. Manejo de snapshot stale en runtime para v1: opcion A (aceptar indefinidamente).
   - Rationale: prioriza continuidad del servicio ante fallas prolongadas del mecanismo de refresh.
   - Decision: si fallan refresh sucesivos, el middleware continua operando con el ultimo snapshot valido sin TTL de bloqueo automatico.

18. Calculo de `X-RateLimit-Retry-After` en v1: opcion B (dinamico).
   - Rationale: brinda una senal mas precisa para reintentos del cliente y reduce retry storms.
   - Decision: en cada `429`, `X-RateLimit-Retry-After` se calcula segun el deficit de tokens y `refillRatePerSecond` de la politica aplicada.

19. Normalizacion de ruta para clave de cuota en v1: opcion B (ruta templada).
   - Rationale: reduce cardinalidad de claves sin perder granularidad por endpoint.
   - Decision: `normalizedRoute` usa formato plantilla (por ejemplo `/users/{id}/orders/{orderId}`) en lugar de ruta literal completa.

20. Header de reintento en v1: opcion A (solo `X-RateLimit-Retry-After`).
   - Rationale: reduce complejidad de contrato y mantiene coherencia con el set minimo `X-RateLimit-*`.
   - Decision: en `429` se emite `X-RateLimit-Retry-After` con valor dinamico.

## Risks / Trade-offs

- [Riesgo] Dependencia de Redis central puede impactar disponibilidad/latencia -> Mitigacion: timeouts, politicas de degradacion y monitoreo activo del backend.
- [Riesgo] `fail-open` puede permitir trafico por encima del limite en incidentes -> Mitigacion: usar `fail-closed` en rutas sensibles y alertas de degradacion.
- [Riesgo] Alta cardinalidad de claves (`identity + method + route`) puede aumentar uso de memoria en Redis -> Mitigacion: normalizar rutas y aplicar TTL/monitoring por dominio.
- [Trade-off] Normalizar a ruta templada requiere un normalizador consistente para evitar colisiones o fragmentacion de buckets.
- [Trade-off] Sin encolado de requests limitadas en v1 -> menor complejidad, pero algunas operaciones recuperables no se reprocesan automaticamente.
- [Riesgo] Reglas desactualizadas por fallo de refresh -> Mitigacion: fallback al ultimo snapshot valido, metricas de staleness y alertas.
- [Trade-off] Arranque fail-fast sin snapshot valido reduce disponibilidad ante errores de configuracion inicial.
- [Trade-off] Aceptar snapshot stale indefinidamente puede demorar aplicacion de cambios de politicas.
- [Trade-off] Sin bucket global en v1 -> una rafaga distribuida entre muchas identidades puede aumentar presion total sobre upstream.
- [Riesgo] Mapeo de identidad incorrecto (IP/header) genera injusticia de cuota -> Mitigacion: extractor de identidad configurable por entorno.
- [Trade-off] v1 prioriza consistencia distribuida con Redis central por sobre minima latencia local.
- [Trade-off] v2 hibrido (cache local + Redis) incrementa complejidad para ganar performance en hot paths.

## Migration Plan

1. Introducir runtime HTTP del proxy con forwarding basico a upstream.
2. Integrar decision de rate limiting previa al forwarding usando Redis central (v1).
3. Configurar politica por defecto, extractor de identidad y estrategia de degradacion (`fail-open`/`fail-closed`).
4. Activar metricas y validar bajo carga controlada.
5. Habilitar progresivo por entorno y ajustar limites.
6. Definir roadmap v2 para cache local + Redis central sin romper contrato HTTP.
7. Rollback: modo bypass (`enabled=false`) para reenviar sin limitar.

## Open Questions


