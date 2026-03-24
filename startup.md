# Startup y pruebas rapidas

> Esta guia describe el **levantamiento manual** paso a paso.
>
> Si queres levantar todo en un solo paso, usa:
>
> - `./scripts/dev-up.sh` para levantar entorno + app + upstream dummy (`8081`).
> - `./scripts/dev-up-and-test.sh` para levantar, ejecutar pruebas y apagar automaticamente.
> - `./scripts/dev-down.sh` para apagar app + upstream dummy + Redis.

## Prerrequisitos

- Java 21
- Maven 3.8+
- Tener disponible el puerto `6379` para Redis
- Tener disponible el puerto `8080` para la app
- (Recomendado) Tener disponible el puerto `8081` para un upstream dummy (o un upstream real ya escuchando en ese puerto)

## Cambiar comportamiento sin escribir configuracion nueva

- Properties base y presets comentados: `iol-challenge/src/main/resources/application.yml`
- Reglas y presets comentados: `iol-challenge/rate-limiter-rules.yaml`
- Mecanica: comentar el bloque activo y descomentar el bloque alternativo deseado.

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

> Si levantaste con `./scripts/dev-up.sh`, ya hay un upstream dummy en `http://localhost:8081`.
>
> Ese upstream dummy responde `200` para metodos HTTP comunes, para simplificar pruebas y evitar errores ruidosos de forwarding.
>
> Si levantaste manualmente, asegurate de tener un upstream corriendo en `http://localhost:8081` o ajusta `rate-limiter-rules.yaml`.

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

## 5) Apagar Redis de prueba

```bash
docker rm -f rl-redis
```

> Si levantaste con scripts, es mejor usar `./scripts/dev-down.sh`.

