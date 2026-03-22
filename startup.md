# Startup y pruebas rapidas

> Esta guia describe el **levantamiento manual** paso a paso.
>
> Si queres levantar todo en un solo paso, usa:
>
> - `./scripts/dev-up.sh` para levantar entorno + app.
> - `./scripts/dev-up-and-test.sh` para levantar, probar y apagar automaticamente.

## Prerrequisitos

- Java 21
- Maven 3.8+
- Tener disponible el puerto `6379` para Redis

## 1) Levantar Redis

```bash
docker run --name rl-redis -p 6379:6379 -d redis:7
```

## 2) Compilar y correr el proyecto

```bash
cd iol-challenge
mvn clean test
mvn spring-boot:run
```

## 3) Probar forwarding permitido

> Asegurate de tener un upstream corriendo en `http://localhost:8081` o ajusta `rate-limiter-rules.yaml`.

```bash
curl -i "http://localhost:8080/rl/users/123" \
  -H "X-Api-Key: demo-client"
```

## 4) Forzar limite y validar `429`

Enviar varias requests rapidas con la misma identidad para consumir tokens:

```bash
for i in {1..150}; do
  curl -s -o /dev/null -w "%{http_code}\n" "http://localhost:8080/rl/users/123" -H "X-Api-Key: demo-client"
done
```

Cuando limite, la respuesta debe incluir:

- `X-RateLimit-Limit`
- `X-RateLimit-Remaining`
- `X-RateLimit-Retry-After`

## 5) Apagar Redis de prueba

```bash
docker rm -f rl-redis
```

