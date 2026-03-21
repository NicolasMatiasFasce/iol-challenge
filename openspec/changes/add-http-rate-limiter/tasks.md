## 1. Runtime HTTP intermedio (proxy)

- [ ] 1.1 Definir endpoint de entrada del rate limiter para recibir trafico cliente.
- [ ] 1.2 Implementar forwarding transparente a upstream para requests permitidas.
- [ ] 1.3 Preservar metodo, path, query, headers y body durante el forwarding.
- [ ] 1.4 Implementar carga inicial de reglas desde archivo en disco a cache en memoria.
- [ ] 1.5 Implementar worker de refresh por polling periodico con swap atomico de snapshot y fallback al ultimo snapshot valido.
- [ ] 1.6 Definir y validar esquema YAML de reglas (campos obligatorios, unidades y errores de parseo).
- [ ] 1.7 Configurar `refreshInterval` y exponer metrica/log de staleness y errores de refresh.
- [ ] 1.8 Implementar validacion de bootstrap para abortar startup si no hay snapshot de reglas valido.

## 2. Politica de rate limiting y configuracion

- [ ] 2.1 Definir propiedades (`enabled`, `capacity`, `refillRatePerSecond`) y validarlas al iniciar.
- [ ] 2.2 Implementar token bucket con estado en Redis central para decision allow/reject previa al forwarding.
- [ ] 2.3 Implementar evaluacion y actualizacion atomica en Redis mediante Lua Script para evitar race conditions.
- [ ] 2.4 Implementar extractor de identidad configurable con clave por defecto `identity + method + normalizedRoute`.
- [ ] 2.5 Implementar fallback de identidad (`api-key` -> `client-ip`) cuando falte credencial primaria.
- [ ] 2.6 Responder `429` con `X-RateLimit-Limit`, `X-RateLimit-Remaining` y `X-RateLimit-Retry-After` cuando se excede el limite.
- [ ] 2.7 Asegurar modo bypass cuando `enabled=false`.
- [ ] 2.8 Implementar politica de degradacion configurable ante falla de Redis (default fail-open, override fail-closed por politica/ruta).
- [ ] 2.9 Implementar manejo v1 de requests limitadas con drop inmediato (sin encolado diferido).
- [ ] 2.10 Implementar alcance de cuotas v1 solo por identidad+endpoint (sin bucket global).
- [ ] 2.11 Calcular `X-RateLimit-Retry-After` en `429` de forma dinamica segun deficit de tokens y `refillRatePerSecond`.
- [ ] 2.12 Implementar normalizador de rutas a formato plantilla para construir `normalizedRoute`.

## 3. Observabilidad, compatibilidad y pruebas

- [ ] 3.1 Instrumentar metricas de requests permitidas y limitadas.
- [ ] 3.2 Agregar pruebas unitarias del limitador (permite y rechaza).
- [ ] 3.3 Agregar pruebas de integracion del proxy para validar forwarding y rechazo `429`.
- [ ] 3.4 Verificar compatibilidad con al menos dos upstreams distintos sin cambios de codigo del limitador.
- [ ] 3.5 Validar compilacion y smoke test con carga controlada.
- [ ] 3.6 Agregar pruebas de resiliencia para falla de Redis validando comportamiento fail-open y fail-closed.
- [ ] 3.7 Agregar pruebas para verificar que requests limitadas no se encolan en v1.
- [ ] 3.8 Agregar pruebas para validar independencia de cuota entre endpoints para una misma identidad.
- [ ] 3.9 Agregar pruebas del contrato de headers `X-RateLimit-*` en respuestas `429`.
- [ ] 3.10 Agregar pruebas para verificar que respuestas permitidas no incluyan headers `X-RateLimit-*` en v1.
- [ ] 3.11 Agregar pruebas de arranque fallido cuando no exista snapshot valido de reglas.
- [ ] 3.12 Agregar pruebas de continuidad con snapshot stale prolongado ante fallas sucesivas de refresh.
- [ ] 3.13 Agregar pruebas para validar calculo dinamico de `X-RateLimit-Retry-After`.
- [ ] 3.14 Agregar pruebas para validar que IDs de path distintos mapeen a la misma `normalizedRoute` del endpoint.

## 4. Roadmap de evolucion

- [ ] 4.1 Documentar explicitamente estrategia v2 de arquitectura hibrida (cache local + Redis central) y criterios de activacion.

