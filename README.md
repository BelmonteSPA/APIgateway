# Belmonte SpA - API Gateway (api-gateway)

Punto de entrada único del backend. La app móvil llama solo a este servicio y él reenvía cada petición al microservicio
que corresponde. Además valida el token JWT y comprueba que el rol tenga permiso para el endpoint, antes de reenviar.

## Tecnologías

- Java 25 / Spring Boot 4.0.6
- Spring Cloud Gateway (WebFlux)
- JWT (jjwt) para validar los tokens que emite `ms-auth`

No usa base de datos.

## Puerto

`http://localhost:8090`

## Rutas

| Ruta | Microservicio | URL por defecto | Variable |
|---|---|---|---|
| `/api/v1/auth/**`, `/api/v1/usuarios/**` | ms-auth | `http://localhost:8081` | `MS_AUTH_URL` |
| `/api/v1/pasajeros/**` | ms-pasajeros | `http://localhost:8082` | `MS_PASAJEROS_URL` |
| `/api/v1/conductores/**` | ms-conductores | `http://localhost:8083` | `MS_CONDUCTORES_URL` |
| `/api/v1/viajes/**` | ms-viajes | `http://localhost:8084` | `MS_VIAJES_URL` |

Las rutas están en `src/main/resources/application.yml`. Las mismas URLs del microservicio se mantienen, solo cambia
el puerto: por ejemplo `POST http://localhost:8090/api/v1/auth/login` en lugar de `http://localhost:8081/...`.

## Seguridad

1. `POST /api/v1/auth/login` y `POST /api/v1/auth/register` son públicos.
2. Todo lo demás exige `Authorization: Bearer <token>`. Sin token, o con un token inválido o vencido, responde 401.
3. Con token válido, si el rol no tiene permiso para ese endpoint responde 403. Un endpoint que no esté en la tabla
   también se deniega.
4. Si todo está bien, reenvía la petición añadiendo las cabeceras `X-User-Email`, `X-User-Rol`, `X-User-Id` y
   `X-User-Ref` (id de la ficha de pasajero o conductor). Las que envíe el cliente se descartan.

La clave para validar los tokens es `jwt.secret` y debe ser la misma que usa `ms-auth` (variable `JWT_SECRET`).

### Permisos por rol

| Endpoint | ADMIN | PASAJERO | CONDUCTOR |
|---|:-:|:-:|:-:|
| `GET /auth/me` | sí | sí | sí |
| `/usuarios/**` | sí | | |
| `GET /pasajeros/{id}` | sí | sí | |
| resto de `/pasajeros/**` | sí | | |
| `GET /conductores/{id}` | sí | | sí |
| resto de `/conductores/**` | sí | | |
| `POST /viajes` (crear solicitud) | sí | sí | |
| `GET /viajes/pasajero/{id}` | sí | sí | |
| `DELETE /viajes/{id}` (cancelar) | sí | sí | |
| `GET /viajes/{id}` | sí | sí | sí |
| `GET /viajes/conductor/**` | sí | | sí |
| `PATCH /viajes/{id}/iniciar` y `/finalizar` | sí | | sí |
| resto de `/viajes/**` (listar, pendientes, por fecha, asignar) | sí | | |

Los permisos se definen en `security/ReglasAcceso.java`. Gana la primera regla que coincide.

## Limitaciones

- Controla el **rol**, no a quién pertenece el dato: un pasajero con token válido podría consultar los viajes de otro
  pasajero si conoce su id.
- Los microservicios siguen abiertos si se llaman directo por su puerto (8081 a 8084). Para un entorno real solo el
  gateway debe quedar expuesto.
- Aún no usa Eureka: las URLs son fijas.

## Ejecución local

Levantar primero los microservicios (y MySQL en Laragon) y luego el gateway:

```bash
./mvnw spring-boot:run
```

Prueba rápida con el administrador de prueba:

```
POST http://localhost:8090/api/v1/auth/login
{ "email": "admin@belmonte.test", "password": "Admin1234!" }

GET http://localhost:8090/api/v1/viajes/pendientes
Authorization: Bearer <token>
```

## Nota

Si falta el wrapper de Maven (`mvnw`, carpeta `.mvn`), generarlo con `mvn wrapper:wrapper` o crearlo desde el IDE.
