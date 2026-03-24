# IOL Challenge - Rate Limiter

## 1) Introduccion al challenge

Este repositorio implementa un **rate limiter HTTP** como middleware intermedio entre clientes y servicios upstream.

### Datos del candidato (completar)

- Nombre y apellido: `[Nicolás Matías Fasce]`
- Email: `[nicofasce1996@gmail.com]`
- GitHub: `[https://github.com/NicolasMatiasFasce]`
- LinkedIn: `[https://www.linkedin.com/in/nicolas-matias-fasce/]`
- Fecha de entrega: `[...]`

### Problema elegido (libro)

Se eligio el problema de **Rate Limiter** del capitulo 4 (middleware en el medio del trafico) para controlar caudal, responder `429` cuando corresponde y proteger a los servicios de destino.

## 2) Documento del proyecto

- Ver [project.md](project.md)
- Ver [guia.md](guia.md)

## 3) OpenSpec: que contiene cada documento

- [openspec/changes/add-http-rate-limiter/proposal.md](openspec/changes/add-http-rate-limiter/proposal.md)
  - Explica **por que** se realiza el cambio y su alcance funcional.
- [openspec/changes/add-http-rate-limiter/design.md](openspec/changes/add-http-rate-limiter/design.md)
  - Explica **como** se implementa (decisiones de arquitectura y trade-offs).
- [openspec/changes/add-http-rate-limiter/tasks.md](openspec/changes/add-http-rate-limiter/tasks.md)
  - Checklist de trabajo ejecutable, con tareas implementadas.
- [openspec/changes/add-http-rate-limiter/specs/http-rate-limiting/spec.md](openspec/changes/add-http-rate-limiter/specs/http-rate-limiting/spec.md)
  - Requisitos normativos y escenarios verificables (`WHEN/THEN`).

## 4) Como levantar y probar

- Ver [startup.md](startup.md)

## 5) Scripts de automatizacion

- `scripts/dev-up.sh`: levanta Redis, un upstream dummy en `8081` (responde `200` a metodos comunes) y la aplicacion.
- `scripts/dev-up-and-test.sh`: llama al script anterior y ejecuta pruebas con reporte.
- `scripts/dev-down.sh`: detiene app/upstream y Redis de forma idempotente; incluye fallback por proceso para app y upstream dummy (`8081`) aunque el `pid` este stale (`--purge` elimina el contenedor Redis).

