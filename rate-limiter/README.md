# Rate Limiter Challenge Submission

## 1) Challenge overview

This repository implements an HTTP **rate limiter middleware** that sits between clients and upstream services.

Candidate data:

- Name: `Nicolas Matias Fasce`
- Email: `nicofasce1996@gmail.com`
- GitHub: `https://github.com/NicolasMatiasFasce`
- LinkedIn: `https://www.linkedin.com/in/nicolas-matias-fasce/`

Chosen problem:

- **Rate Limiter**: Implement a rate-limiting middleware that can be applied to any HTTP server, allowing configurable limits per client and route.

## 2) Mandatory challenge structure

- Problem folder: `rate-limiter/`
- Mandatory design file: `rate-limiter/DESIGN.md`

`DESIGN.md` points to `../openspec/changes/add-http-rate-limiter/design.md`
as single source of truth, so there is no divergence between docs.

## 3) Project documents

- Project overview: `../project.md`
- Detailed implementation guide: `../guia.md`
- Startup and local run guide: `../startup.md`

OpenSpec documents:

- Proposal: `../openspec/changes/add-http-rate-limiter/proposal.md`
- Design (source): `../openspec/changes/add-http-rate-limiter/design.md`
- Tasks: `../openspec/changes/add-http-rate-limiter/tasks.md`
- Spec: `../openspec/changes/add-http-rate-limiter/specs/http-rate-limiting/spec.md`

## 4) Implementation location

- Main application code: `../iol-challenge/`

## 5) Automation scripts

- `../scripts/dev-up.sh`: starts Redis + dummy upstream (`8081`) + app
- `../scripts/dev-up-and-test.sh`: starts stack, runs script-level tests, and shuts down
- `../scripts/dev-down.sh`: idempotent stop for app/upstream/Redis

