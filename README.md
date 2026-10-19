# ms-andesstay-bff

Backend for Frontend del proyecto **AndesStay** (caso DSY1107 – Desarrollo Cloud Native I, Duoc UC). Es el único punto de entrada público de la plataforma: se ubica detrás de **AWS API Gateway**, valida el JWT de Azure AD y reenvía cada petición al microservicio de dominio correspondiente.

## Rol en la arquitectura

```
Angular (MSAL) → AWS API Gateway → ms-andesstay-bff → { reservations | catalog | audit | report | rabbit-admin }
```

- Valida firma, `issuer`, `audience`, vigencia y rol del JWT emitido por Azure AD.
- Reenvía la petición (mismo path, query, body y el mismo Bearer token) al microservicio de dominio que corresponda, según el primer segmento de la ruta (`/api/{domain}/...`).
- Cada microservicio de dominio vuelve a validar el JWT de forma independiente (defensa en profundidad: JWT → API Gateway → BFF → microservicio).
- Resuelve CORS para el frontend Angular (no hay CORS entre el frontend y los microservicios de dominio, porque nunca se les llama directo).

`ms-andesstay-notify` no tiene ruta en el BFF: es un consumidor de mensajería, no expone API para el frontend.

## Stack

- Spring Boot 4.1.1 / Java 21
- Spring Security OAuth2 Resource Server (validación de JWT contra Azure AD v2.0)
- `RestClient` (Spring 6.1+) para el proxy hacia los microservicios de dominio

## Configuración

| Variable | Descripción | Default |
|---|---|---|
| `SERVER_PORT` | Puerto HTTP | `8081` |
| `AZURE_ISSUER_URI` | Issuer del tenant de Azure AD (`.../v2.0`) | tenant del caso |
| `AZURE_API_AUDIENCE` | `aud` esperado en el access token (acepta `api://<id>` o `<id>` a secas) | App ID URI del caso |
| `ALLOWED_ORIGINS` | Orígenes permitidos por CORS, separados por coma | `http://localhost:4200` |
| `RESERVATIONS_SERVICE_URL` | URL base de `ms-andesstay-reservations` | `http://localhost:8082` |
| `CATALOG_SERVICE_URL` | URL base de `ms-andesstay-catalog` | `http://localhost:8083` |
| `AUDIT_SERVICE_URL` | URL base de `ms-andesstay-audit` | `http://localhost:8084` |
| `REPORT_SERVICE_URL` | URL base de `ms-andesstay-report` | `http://localhost:8085` |
| `RABBIT_ADMIN_SERVICE_URL` | URL base de `ms-andesstay-rabbit-admin` (`/api/rabbit/**`, solo rol Admin) | `http://localhost:8086` |

## Cómo ejecutarlo en local

Requiere Java 21. No depende de ninguna base de datos ni broker propio, solo de que los microservicios de dominio estén levantados (ver [andesstay-infra](https://github.com/MarCOCO999/andesstay-infra) para la infraestructura local).

```bash
./mvnw spring-boot:run
```

## Roles (App Roles de Azure AD)

`Admin`, `Operador`, `Cliente`, `Auditor` — el BFF no impone reglas de autorización propias por rol (eso lo decide cada microservicio de dominio); solo exige un JWT válido.

## Repos relacionados

[frontend-andesstay](https://github.com/MarCOCO999/frontend-andesstay) · [ms-andesstay-reservations](https://github.com/MarCOCO999/ms-andesstay-reservations) · [ms-andesstay-catalog](https://github.com/MarCOCO999/ms-andesstay-catalog) · [ms-andesstay-notify](https://github.com/MarCOCO999/ms-andesstay-notify) · [ms-andesstay-audit](https://github.com/MarCOCO999/ms-andesstay-audit) · [ms-andesstay-report](https://github.com/MarCOCO999/ms-andesstay-report) · [ms-andesstay-rabbit-admin](https://github.com/MarCOCO999/ms-andesstay-rabbit-admin) · [andesstay-infra](https://github.com/MarCOCO999/andesstay-infra)
