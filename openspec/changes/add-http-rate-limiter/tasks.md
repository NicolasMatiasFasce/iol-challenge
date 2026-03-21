## 1. Runtime HTTP intermedio (proxy)

- [x] 1.1 Definir endpoint de entrada del rate limiter para recibir trafico cliente.
- [x] 1.2 Implementar forwarding transparente a upstream para requests permitidas.
- [x] 1.3 Preservar metodo, path, query, headers y body durante el forwarding.
- [x] 1.4 Implementar carga inicial de reglas desde archivo en disco a cache en memoria.
- [x] 1.5 Implementar worker de refresh por polling periodico con swap atomico de snapshot y fallback al ultimo snapshot valido.
- [x] 1.6 Definir y validar esquema YAML de reglas (campos obligatorios, unidades y errores de parseo).
- [x] 1.7 Configurar `refreshInterval` y exponer metrica/log de staleness y errores de refresh.
- [x] 1.8 Implementar validacion de bootstrap para abortar startup si no hay snapshot de reglas valido.

## 2. Politica de rate limiting y configuracion

- [x] 2.1 Definir propiedades (`enabled`, `capacity`, `refillRatePerSecond`) y validarlas al iniciar.
- [x] 2.2 Implementar token bucket con estado en Redis central para decision allow/reject previa al forwarding.
- [x] 2.3 Implementar evaluacion y actualizacion atomica en Redis mediante Lua Script para evitar race conditions.
- [x] 2.4 Implementar extractor de identidad configurable con clave por defecto `identity + method + normalizedRoute`.
- [x] 2.5 Implementar fallback de identidad (`api-key` -> `client-ip`) cuando falte credencial primaria.
- [x] 2.6 Responder `429` con `X-RateLimit-Limit`, `X-RateLimit-Remaining` y `X-RateLimit-Retry-After` cuando se excede el limite.
- [x] 2.7 Asegurar modo bypass cuando `enabled=false`.
- [x] 2.8 Implementar politica de degradacion configurable ante falla de Redis (default fail-open, override fail-closed por politica/ruta).
- [x] 2.9 Implementar manejo v1 de requests limitadas con drop inmediato (sin encolado diferido).
- [x] 2.10 Implementar alcance de cuotas v1 solo por identidad+endpoint (sin bucket global).
- [x] 2.11 Calcular `X-RateLimit-Retry-After` en `429` de forma dinamica segun deficit de tokens y `refillRatePerSecond`.
- [x] 2.12 Implementar normalizador de rutas a formato plantilla para construir `normalizedRoute`.

## 3. Observabilidad, compatibilidad y pruebas

- [x] 3.1 Instrumentar metricas de requests permitidas y limitadas.
- [x] 3.2 Agregar pruebas unitarias del limitador (permite y rechaza).
- [x] 3.3 Agregar pruebas de integracion del proxy para validar forwarding y rechazo `429`.
- [x] 3.4 Verificar compatibilidad con al menos dos upstreams distintos sin cambios de codigo del limitador.
- [x] 3.5 Validar compilacion y smoke test con carga controlada.
- [x] 3.6 Agregar pruebas de resiliencia para falla de Redis validando comportamiento fail-open y fail-closed.
- [x] 3.7 Agregar pruebas para verificar que requests limitadas no se encolan en v1.
- [x] 3.8 Agregar pruebas para validar independencia de cuota entre endpoints para una misma identidad.
- [x] 3.9 Agregar pruebas del contrato de headers `X-RateLimit-*` en respuestas `429`.
- [x] 3.10 Agregar pruebas para verificar que respuestas permitidas no incluyan headers `X-RateLimit-*` en v1.
- [x] 3.11 Agregar pruebas de arranque fallido cuando no exista snapshot valido de reglas.
- [x] 3.12 Agregar pruebas de continuidad con snapshot stale prolongado ante fallas sucesivas de refresh.
- [x] 3.13 Agregar pruebas para validar calculo dinamico de `X-RateLimit-Retry-After`.
- [x] 3.14 Agregar pruebas para validar que IDs de path distintos mapeen a la misma `normalizedRoute` del endpoint.

## 4. Roadmap de evolucion

- [x] 4.1 Documentar explicitamente estrategia v2 de arquitectura hibrida (cache local + Redis central) y criterios de activacion.

## 5. Documentacion y navegacion

- [x] 5.1 Crear `project.md` con guia detallada de navegacion de codigo y arquitectura.
- [x] 5.2 Crear `readme.md` con introduccion al challenge, enlaces a OpenSpec y acceso a `startup.md`.
- [x] 5.3 Crear `startup.md` con pasos de levantamiento y pruebas del proyecto.
- [x] 5.4 Agregar comentarios de documentacion en espanol sobre metodos clave del modulo de rate limiter.

