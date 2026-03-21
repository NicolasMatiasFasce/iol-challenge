# Decisiones del cambio `add-http-rate-limiter`

## D1. Topologia de despliegue
| Opcion | Comparacion breve |
|---|---|
| A. Gateway central | Menor complejidad inicial, operacion unificada, mayor blast radius. |
| B. Sidecar por servicio | Menor latencia local, mayor complejidad operativa. |
| C. Hibrido | Maxima flexibilidad, costo/operacion mas altos. |
**Decision tomada:** A para v1, con camino a C en v2.
**Justificacion corta:** maximize simplicidad y time-to-market del challenge.

## D2. Algoritmo principal
| Opcion | Comparacion breve |
|---|---|
| A. Token bucket | Simple, memory-efficient, soporta bursts cortos. |
| B. Sliding window counter | Mas preciso, mas complejo. |
| C. Sliding/log u otros | Mayor precision en algunos casos, costo superior. |
**Decision tomada:** A (Token bucket).
**Justificacion corta:** mejor balance para v1 distribuido.

## D3. Estado de cuotas
| Opcion | Comparacion breve |
|---|---|
| A. Memoria local | Muy simple, inconsistente entre nodos. |
| B. Redis central | Consistente y distribuido, agrega dependencia externa. |
| C. Cache local + Redis | Mejor performance, mas complejidad. |
**Decision tomada:** B en v1, C en v2.
**Justificacion corta:** coherencia distribuida primero, optimizacion despues.

## D4. Atomicidad en concurrencia
| Opcion | Comparacion breve |
|---|---|
| A. Lua Script en Redis | Atomico, evita race conditions, buen rendimiento. |
| B. WATCH/MULTI/EXEC | Valido pero con retries y mayor friccion bajo carga. |
| C. Locks distribuidos | Mas lento y complejo. |
**Decision tomada:** A.
**Justificacion corta:** robustez concurrente con bajo overhead.

## D5. Degradacion ante falla de Redis
| Opcion | Comparacion breve |
|---|---|
| A. Fail-open global | Alta disponibilidad, menor control de cuota. |
| B. Fail-closed global | Proteccion maxima, peor disponibilidad. |
| C. Configurable por politica/ruta | Balancea disponibilidad y proteccion, mas configuracion. |
**Decision tomada:** C (default fail-open, override fail-closed).
**Justificacion corta:** cumple fault tolerance sin perder control en rutas criticas.

## D6. Clave de cuota
| Opcion | Comparacion breve |
|---|---|
| A. Solo IP | Simple, fairness limitado por NAT/proxies. |
| B. Solo API key | Mejor por cliente autenticado, depende de credencial. |
| C. `identity + method + normalizedRoute` | Buen control y fairness, mayor cardinalidad. |
**Decision tomada:** C, con fallback `api-key -> client-ip`.
**Justificacion corta:** evita que un endpoint consuma cuota de otro.

## D7. Hard vs soft rate limiting
| Opcion | Comparacion breve |
|---|---|
| A. Hard | Estricto y predecible. |
| B. Soft | Mejor UX en picos, menor rigor. |
| C. Mixto por politica | Flexibilidad con mayor complejidad. |
**Decision tomada:** A en v1, con camino a C en v2.
**Justificacion corta:** v1 prioriza simplicidad y comportamiento determinista; v2 puede agregar flexibilidad por politica.

## D8. Requests excedidas
| Opcion | Comparacion breve |
|---|---|
| A. Drop inmediato | Simple y de baja latencia. |
| B. Encolar para reproceso | Mejor recuperacion, mucha complejidad. |
| C. Mixto | Flexible, complejidad intermedia/alta. |
**Decision tomada:** A en v1.
**Justificacion corta:** evitar sobreingenieria en challenge.

## D9. Fuente de reglas
| Opcion | Comparacion breve |
|---|---|
| A. Disco + worker + cache | Simple, alineado al capitulo 4/fig 4-13. |
| B. Servicio central de config | Mas dinamico, mas infraestructura. |
| C. Hibrido | Resiliente, mas complejo. |
**Decision tomada:** A.
**Justificacion corta:** cumple requerimiento con bajo costo operativo.

## D10. Alcance de limites
| Opcion | Comparacion breve |
|---|---|
| A. Solo identidad+endpoint | Simple y justo por endpoint. |
| B. Solo global | Protege sistema, fairness bajo. |
| C. Jerarquico global+fino | Mas completo, mayor complejidad. |
**Decision tomada:** A en v1.
**Justificacion corta:** reduce complejidad y mantiene control util.

## D11. Contrato de headers de cuota
| Opcion | Comparacion breve |
|---|---|
| A. `X-RateLimit-*` minimo | Claro y simple para clientes. |
| B. Solo `Retry-After` | Menos informacion operativa. |
| C. Doble estandar completo | Mayor compatibilidad, mas trabajo. |
**Decision tomada:** A como base.
**Justificacion corta:** contrato claro sin inflar implementacion.

## D12. Headers en respuestas permitidas
| Opcion | Comparacion breve |
|---|---|
| A. Solo en `429` | Menor overhead y simplicidad. |
| B. En todas las respuestas | Mayor visibilidad, mas costo. |
| C. Mixto | Balance, complejidad extra. |
**Decision tomada:** A.
**Justificacion corta:** mantener v1 minimalista.

## D13. Formato de reglas
| Opcion | Comparacion breve |
|---|---|
| A. YAML | Legible para operaciones, alineado al capitulo. |
| B. JSON | Estricto, menos amigable para edicion manual. |
| C. Ambos | Flexible, mas codigo y validacion. |
**Decision tomada:** A.
**Justificacion corta:** mejor DX/operacion en challenge.

## D14. Mecanismo de refresh
| Opcion | Comparacion breve |
|---|---|
| A. Polling periodico | Portable y simple. |
| B. File watcher | Mas reactivo, mas fragil por entorno. |
| C. Hibrido | Robusto, mayor complejidad. |
**Decision tomada:** A.
**Justificacion corta:** confiable y facil de operar.

## D15. Startup sin snapshot valido
| Opcion | Comparacion breve |
|---|---|
| A. Fail-open inicial | Disponibilidad alta, sin control inicial. |
| B. Fail-closed inicial | Proteccion alta, puede bloquear negocio. |
| C. Abort startup | Determinista y auditable, menor disponibilidad ante config mala. |
**Decision tomada:** C.
**Justificacion corta:** evita ejecutar sin reglas validas.

## D16. Snapshot stale en runtime
| Opcion | Comparacion breve |
|---|---|
| A. Aceptar indefinidamente | Continuidad maxima, reglas potencialmente viejas. |
| B. TTL + fail-open | Disponibilidad con control temporal. |
| C. TTL + fail-closed selectivo | Mas control, mas complejidad. |
**Decision tomada:** A en v1.
**Justificacion corta:** continuidad operativa durante incidentes de refresh.

## D17. `maxWait` en v1
| Opcion | Comparacion breve |
|---|---|
| A. Eliminar | Coherente con drop inmediato, menos complejidad. |
| B. Mantener inactivo | Compatibilidad futura, ruido actual. |
| C. Mantener activo | Contradice decisiones de simplicidad en v1. |
**Decision tomada:** A.
**Justificacion corta:** simplificacion y consistencia funcional.

## D18. `X-RateLimit-Retry-After`
| Opcion | Comparacion breve |
|---|---|
| A. Valor fijo | Muy simple, poca precision. |
| B. Dinamico | Mejor guia de reintento, algo mas de logica. |
| C. Por politica/ruta | Flexible, complejidad mayor. |
**Decision tomada:** B.
**Justificacion corta:** reduce retry storms con buena precision.

## D19. `normalizedRoute`
| Opcion | Comparacion breve |
|---|---|
| A. Ruta literal | Cardinalidad alta de claves. |
| B. Ruta templada | Balance entre precision y memoria. |
| C. Prefijo | Menos cardinalidad, mezcla endpoints. |
**Decision tomada:** B.
**Justificacion corta:** evita explosion de buckets por IDs dinamicos.

## D20. Header estandar de reintento
| Opcion | Comparacion breve |
|---|---|
| A. Solo `X-RateLimit-Retry-After` | Simple, menor interoperabilidad estandar. |
| B. Ambos (`X-RateLimit-Retry-After` + `Retry-After`) | Mayor compatibilidad de clientes/proxies. |
| C. Solo `Retry-After` | Estandar puro, pierde contrato `X-*` definido. |
**Decision tomada:** A.
**Justificacion corta:** reduce complejidad de contrato en v1 y mantiene coherencia con el set minimo `X-RateLimit-*`.

