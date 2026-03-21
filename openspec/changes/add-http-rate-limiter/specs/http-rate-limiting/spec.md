## ADDED Requirements

### Requirement: The service SHALL act as an HTTP intermediary rate limiter
El sistema SHALL operar en el medio del trafico HTTP entre cliente y API upstream, evaluando cada request antes del forwarding.

#### Scenario: Request permitida y reenviada
- **WHEN** llega una request y la politica de rate limiting permite su ejecucion
- **THEN** el sistema reenvia la request al upstream configurado y retorna su respuesta

#### Scenario: Request rechazada por limite
- **WHEN** llega una request y la politica de rate limiting no permite su ejecucion
- **THEN** el sistema responde `429 Too Many Requests` sin reenviar al upstream

### Requirement: Rate limiting SHALL be server-agnostic
El sistema SHALL aplicar las mismas reglas de limitacion independientemente del tipo de API server de destino.

#### Scenario: Integracion con multiples upstreams
- **WHEN** existen dos o mas API servers de destino con tecnologias diferentes
- **THEN** el sistema aplica politicas de rate limiting con el mismo contrato de configuracion y respuesta

#### Scenario: Politica desacoplada del vendor
- **WHEN** se cambia el API server upstream manteniendo la misma configuracion de politicas
- **THEN** el comportamiento de allow/reject del limitador no cambia por el vendor

### Requirement: Rate limiting SHALL be configurable by environment
El sistema SHALL permitir configurar por ambiente los parámetros del limitador para controlar comportamiento y capacidad.

#### Scenario: Configuración válida aplicada al iniciar
- **WHEN** el servicio inicia con propiedades validas de rate limiting
- **THEN** el limitador usa `enabled`, `capacity` y `refillRatePerSecond` definidos en configuracion

#### Scenario: Feature deshabilitada
- **WHEN** la propiedad de habilitacion de rate limiting esta en falso
- **THEN** las requests se reenvian sin evaluacion de limite

### Requirement: Rate limiting rules SHALL be loaded from disk and cached
El sistema SHALL cargar reglas de rate limiting desde archivo en disco y mantenerlas en cache en memoria con refresco periodico.

#### Scenario: Carga inicial de reglas
- **WHEN** el servicio inicia y el archivo YAML de reglas es valido
- **THEN** el middleware carga reglas en cache y las usa para evaluar requests

#### Scenario: Fallback a ultimo snapshot valido
- **WHEN** el proceso de refresh periodico encuentra reglas invalidas
- **THEN** el middleware mantiene el ultimo snapshot de reglas valido y registra el error

#### Scenario: Arranque sin snapshot valido
- **WHEN** el servicio inicia y no existe ningun snapshot valido de reglas YAML
- **THEN** el proceso de startup falla de forma explicita y el servicio no acepta trafico

#### Scenario: Refresh por polling configurable
- **WHEN** el worker ejecuta el ciclo de polling segun su intervalo configurado
- **THEN** el middleware refresca el snapshot de reglas solo si la nueva carga es valida

#### Scenario: Snapshot stale prolongado en runtime
- **WHEN** ocurren fallas consecutivas de refresh luego de una inicializacion valida
- **THEN** el middleware sigue evaluando requests con el ultimo snapshot valido sin detener el servicio por TTL

### Requirement: The service SHALL support configurable identity keys for quotas
El sistema SHALL permitir seleccionar la clave de identidad usada para cuotas (por ejemplo IP cliente, API key o combinacion con ruta).

#### Scenario: Clave compuesta por defecto
- **WHEN** la politica de identidad no define override explicito
- **THEN** la cuota se calcula con clave compuesta `identity + method + normalizedRoute`

#### Scenario: Fallback de identidad
- **WHEN** la politica usa `api-key` como identidad primaria y el header no esta presente
- **THEN** el sistema usa `client-ip` como fallback para construir la clave de cuota

#### Scenario: Limite por API key
- **WHEN** la politica de identidad esta configurada para header de API key
- **THEN** la cuota se calcula por valor de ese header

#### Scenario: Limite por IP cliente
- **WHEN** la politica de identidad esta configurada para IP cliente
- **THEN** la cuota se calcula por direccion IP de origen

### Requirement: Quota scope SHALL be identity-plus-endpoint in v1
El sistema SHALL evaluar limites en v1 usando solo alcance por identidad y endpoint (`method + normalizedRoute`), sin bucket global agregado.

#### Scenario: Dos endpoints independientes
- **WHEN** una misma identidad consume cuota en dos endpoints distintos
- **THEN** cada endpoint mantiene su propio contador de cuota independiente

#### Scenario: Ruta templada para cuota
- **WHEN** dos requests comparten el mismo endpoint funcional pero difieren en IDs de path
- **THEN** ambas usan la misma `normalizedRoute` templada para calcular la cuota

### Requirement: Rate limit rejections SHALL expose standard quota metadata
El sistema SHALL incluir metadatos de cuota en headers de respuesta para requests rechazadas por limite.

#### Scenario: Rechazo con informacion de cuota
- **WHEN** una request es rechazada por rate limiting
- **THEN** la respuesta `429` incluye `X-RateLimit-Limit`, `X-RateLimit-Remaining` y `X-RateLimit-Retry-After` calculado dinamicamente segun deficit de tokens y refill rate

#### Scenario: Respuesta permitida sin headers de cuota
- **WHEN** una request es permitida y reenviada al upstream
- **THEN** la respuesta no incluye headers `X-RateLimit-*` generados por el middleware de rate limiting

### Requirement: Rate-limited requests SHALL be dropped in v1
El sistema SHALL descartar en forma inmediata las requests rate-limited en v1, sin encolado diferido.

#### Scenario: Exceso de limite sin encolado
- **WHEN** una request supera el limite configurado
- **THEN** el sistema responde `429` y no encola la request para procesamiento posterior

#### Scenario: Hard rate limiting en v1
- **WHEN** una request supera el limite configurado en v1
- **THEN** el sistema no aplica modo soft y mantiene rechazo inmediato

### Requirement: The service SHALL provide fault-tolerant degradation modes
El sistema SHALL definir una politica configurable para cuando el backend de rate limiting (Redis) no este disponible.

#### Scenario: Degradacion por defecto fail-open
- **WHEN** Redis no esta disponible y la politica efectiva es `fail-open`
- **THEN** la request se permite y se reenvia al upstream

#### Scenario: Degradacion sensible fail-closed
- **WHEN** Redis no esta disponible y la politica efectiva de la ruta/politica es `fail-closed`
- **THEN** la request se rechaza con respuesta explicita sin reenviar al upstream

### Requirement: Rate limiting SHALL produce operational metrics
El sistema SHALL registrar métricas para observar el comportamiento del limitador y facilitar tuning operativo.

#### Scenario: Solicitud permitida incrementa métrica
- **WHEN** una solicitud es permitida y reenviada
- **THEN** se incrementa el contador de solicitudes permitidas

#### Scenario: Solicitud rechazada incrementa métrica
- **WHEN** una solicitud es rechazada por politica de rate limiting
- **THEN** se incrementa el contador de solicitudes limitadas


