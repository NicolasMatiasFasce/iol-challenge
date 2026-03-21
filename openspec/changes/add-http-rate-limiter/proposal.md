## Why

La organizacion necesita un componente comun para controlar caudal HTTP sin acoplarse a un API server especifico. En lugar de limitar en cliente o en servidor, este proyecto debe actuar en el medio del trafico como un rate limiter standalone que proteja a multiples servicios upstream con un comportamiento consistente.

## What Changes

- El proyecto pasa a ser un servicio de rate limiting HTTP standalone, ubicado entre cliente y API upstream (patron proxy/interceptor).
- Se define un modelo de politicas de limite agnostico del servidor de destino (por identidad de cliente, ruta y metodo).
- Se estandariza la respuesta de limite excedido con codigo `429` y metadatos de cuota en headers.
- Se incorpora forwarding transparente hacia upstream cuando la solicitud es permitida.
- Se agregan metricas operativas del limitador y trazabilidad de decisiones de allow/reject.
- Se documenta contrato de integracion para multiples API servers de la misma organizacion.

## Capabilities

### New Capabilities
- `http-rate-limiting`: Ejecuta rate limiting server-agnostico como capa intermedia entre cliente y API server, con forwarding y observabilidad.

### Modified Capabilities
- Ninguna.

## Impact

- Codigo afectado: pipeline HTTP de entrada/salida, enrutamiento a upstream, politicas de rate limit y configuracion.
- APIs: se incorpora interfaz HTTP del limitador (proxy) para recibir trafico cliente y reenviarlo.
- Dependencias: mantener implementacion con capacidades estandar de Java/Spring sin acople a un gateway/vendor especifico.
- Operacion: requiere configuracion por entorno (limites, claves de identidad, upstreams) y monitoreo continuo de `allow/reject`.

