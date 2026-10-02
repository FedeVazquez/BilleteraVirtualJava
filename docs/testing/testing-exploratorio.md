# Testing exploratorio — Sprint 1

**Fecha:** 02/10/2026 · **Build:** rama `dev` · **Entorno:** local sin Docker (perfil `h2`, mails
con `scripts/fake_smtp.py`), los 5 microservicios levantados y todas las peticiones entrando por el
API Gateway (`http://localhost:8090`), salvo donde se indica.

## Cómo se organizó

Se trabajó con **testing basado en sesiones** (*session-based test management*): bloques cortos y
acotados, cada uno con un **charter** (misión: qué se explora, con qué y buscando qué). Dentro de
cada sesión se siguieron **tours** (recorridos con una idea guía) y se tomaron notas a medida que
se probaba. Los casos escritos de la planilla verifican lo que ya se sabía que había que
verificar; estas sesiones buscaron lo que no estaba previsto.

| Sesión | Charter | Tours usados | Pruebas |
|---|---|---|---|
| 1 | Explorar el **registro** con datos límite, tipos incorrectos y entradas maliciosas, para descubrir validaciones faltantes y datos inconsistentes. | Tour de los datos (valores límite), tour del saboteador (entradas hostiles) | 35 |
| 2 | Explorar **login, token y logout** manipulando el JWT y los headers, para descubrir formas de entrar sin credenciales o de ver datos ajenos. | Tour del intruso (seguridad), tour de la sesión (varias sesiones a la vez) | 30 |
| 3 | Explorar la **verificación de email** y el **recupero de contraseña** siguiendo el flujo completo y sus desvíos, para descubrir códigos o links reutilizables. | Tour del flujo de punta a punta (workflow), tour del impaciente (repetir, reintentar) | 26 |
| 4 | Explorar el **gateway y la resiliencia** apagando servicios y saltándose el gateway, para descubrir datos a medias y errores mal informados. | Tour del saboteador (apagar componentes), tour de la puerta trasera (accesos directos) | 13 |

Herramientas: `curl` y scripts de Python (`urllib`) para armar las peticiones, incluidas las
concurrentes y los tokens adulterados; el archivo `mails.jsonl` para leer los mails; los logs de
cada servicio; `Stop-Process` para apagar servicios.

## Sesión 1 — Registro

**Escenarios recorridos y notas**

- Largos: nombre de 60 caracteres aceptado y de 61 rechazado; contraseña de 8 y de 72 aceptadas,
  de 7 y de 73 rechazadas; email de más de 120 rechazado. Los límites coinciden con lo definido.
- Espacios: nombre de solo espacios y contraseña de solo espacios rechazados. Un email con
  espacios alrededor se rechaza (400) en lugar de recortarse: aceptable, el cliente debe enviarlo limpio.
- DNI: 7 y 8 dígitos aceptados; 9 dígitos, con puntos o con letras, rechazados.
- Teléfono: con espacios y guiones (`+54 9 11 2233-4455`) se rechaza. Es estricto; el frontend
  tendrá que normalizarlo antes de enviarlo (observación O-1).
- Unicode: acentos, ñ y apóstrofes se guardan y se devuelven bien.
- Campos extra (`id`, `roles: ["ROLE_ADMIN"]`, `emailVerified: true`): se ignoran. El usuario se
  crea con su propio id, con rol de usuario y sin verificar. No hay asignación masiva de campos.
- Inyección SQL en el nombre (`Robert'); DROP TABLE users;--`): se guarda como texto. Las
  consultas son parametrizadas (JPA).
- Tipos y formato: body vacío, un array, JSON roto → 400; `Content-Type: text/plain` → 415.
- Concurrencia: 8 registros simultáneos con el mismo email → 1 creado y 7 rechazados con 400; lo
  mismo con el mismo DNI. La restricción de unicidad de la base cubre la carrera que la validación
  previa no puede cubrir.
- Tamaño: un nombre de 1 MB y un body de 12 MB se rechazan con 400 sin afectar al servicio.
- La respuesta nunca incluye la contraseña ni su hash, y lleva `Cache-Control: no-store`.

**Defectos encontrados:** DEF-001, DEF-002, DEF-003.

## Sesión 2 — Login, token y logout

**Escenarios recorridos y notas**

- El email no distingue mayúsculas; la contraseña sí, y un espacio de más la invalida.
- El login responde solo `token`, `tokenType` y `expiresIn`. El token lleva `sub`, `email`,
  `roles`, `jti`, `iat` y `exp`; dura 60 minutos.
- Token adulterado: payload cambiado conservando la firma, `alg: none` sin firma, firmado con un
  secreto adivinado (`secret`) y con el vencimiento alterado → 401 en los cuatro casos.
- Header: `bearer` en minúscula, sin prefijo, `Basic`, token vacío y token por query string → 401.
- Permisos: pedir `/users/{id}` o `/accounts/user/{id}` de otro usuario → 403. Un id que no
  existe y no es el propio también responde 403, así no se revela qué ids existen.
- Sesiones: dos logins del mismo usuario dan tokens distintos; el logout de uno no afecta al otro
  ni a los demás usuarios; después del logout se puede volver a entrar.
- Métodos incorrectos (`GET /auth/login`, `GET /user/logout`) → 405.

**Defectos encontrados:** ninguno. **Observaciones:** O-2, O-3 y O-4.

## Sesión 3 — Verificación de email y recupero de contraseña

**Escenarios recorridos y notas**

- El registro envía un mail con un código de 6 dígitos; sin verificar, el login responde 403.
- Código con letras o de 5 dígitos → 400 de formato; email inexistente → 404.
- Reenvío: llega un código nuevo y el anterior deja de servir.
- Fuerza bruta: tras 5 códigos incorrectos, el código correcto también se rechaza hasta pedir uno
  nuevo. Con un reenvío se puede verificar.
- Verificar dos veces no es un error (200); reenviar a un email ya verificado → 400.
- Recupero: la respuesta es idéntica exista o no el email, y al inexistente no se le manda nada.
- El link apunta a la pantalla `reset-password.html` con el token; la pantalla pide la contraseña dos veces.
- Contraseñas distintas, clave corta, token inventado, falta de confirmación → 400.
- El link es de un solo uso; si se piden dos links seguidos, solo sirve el último.
- Recuperar la contraseña no saltea la verificación de email: un usuario sin verificar cambia su
  clave pero sigue sin poder entrar hasta verificar.
- Si el servidor de correo no responde, el registro igual se completa (queda un aviso en el log) y
  el usuario puede pedir el reenvío del código. Se comprobó por accidente: el SMTP falso estaba mal
  configurado en la primera corrida.

**Defectos encontrados:** DEF-004.

## Sesión 4 — Gateway y resiliencia

**Escenarios recorridos y notas**

- `/internal/**` a través del gateway → 404: los endpoints internos no están publicados.
- `/internal/**` directo al puerto del servicio, sin clave o con clave incorrecta → 401.
- `/actuator/health` responde; `/actuator/env` no está expuesto.
- CORS: el preflight desde `http://localhost:3000` se acepta; desde otro origen responde 403.
- **account-service apagado** y registro: 500 con mensaje genérico. El usuario **no** queda
  guardado (el login posterior responde 404) y **no** se envía el mail: la transacción hace
  rollback completo.
- **auth-service apagado** y petición con token válido: 503 con mensaje claro. El gateway no deja
  pasar peticiones que no pudo validar (falla cerrado).

**Defectos encontrados:** DEF-005.

## Defectos

| ID | Severidad | Descripción | Cómo se reproduce | Estado |
|---|---|---|---|---|
| DEF-001 | Media | El registro acepta emails sin dominio completo. | `POST /users/register` con `"email": "a@b"` → 201 (esperado 400). | **Corregido y verificado** (TC-011) |
| DEF-002 | Baja | El registro acepta el DNI como número JSON y lo convierte a texto en silencio. | `"dni": 30123456` (sin comillas) → 201 (esperado 400: el contrato dice texto). | **Corregido y verificado** (TC-016) |
| DEF-003 | Media | El nombre y el apellido aceptan cualquier carácter, incluido HTML. | `"firstName": "<script>alert(1)</script>"` → 201. La API no lo ejecuta, pero un frontend que lo muestre sin escapar sí. | **Corregido y verificado** (TC-018, TC-020) |
| DEF-004 | Alta | Cambiar la contraseña no cierra las sesiones abiertas. | Login (token A) → recuperar contraseña → el token A sigue respondiendo 200. Quien recupera su cuenta porque se la robaron no logra echar al intruso. | **Corregido y verificado** (TC-077) |
| DEF-005 | Baja | Cuando el servicio destino está caído, el gateway responde 500 con un JSON distinto al del resto de la API y sin mensaje. | Apagar account-service y pedir `GET /accounts/user/1` con token. | **Corregido y verificado**: ahora 503 con el formato común (TC-084 y prueba manual) |

Las cinco correcciones quedaron cubiertas por tests JUnit y por casos de la suite de regresión.

## Observaciones y riesgos (no son defectos)

| ID | Observación | Recomendación |
|---|---|---|
| O-1 | El teléfono solo acepta dígitos con `+` opcional. | Que el frontend lo normalice, o relajar la validación si el negocio lo pide. |
| O-2 | El login distingue "usuario inexistente" (404) de "contraseña incorrecta" (400), como pide la consigna. Eso permite averiguar qué emails están registrados. | Evaluar con el negocio unificar ambas respuestas en un 401 genérico. |
| O-3 | No hay límite de intentos de login por usuario ni por IP. | Agregar *rate limiting* en el gateway. |
| O-4 | Un token invalidado por logout sigue siendo aceptado si se llama **directo** al puerto de un servicio, porque la lista de tokens invalidados solo la consulta el gateway. Con Docker Compose no es alcanzable: el único puerto publicado es el del gateway. | Mantener los servicios sin puertos publicados. Si alguna vez se exponen, validar el token contra auth-service también en cada servicio. |
| O-5 | Estas sesiones se hicieron sobre H2. | Repetir una sesión corta sobre MySQL cuando Docker esté disponible (ver TC-086 y TC-087). |

## Cierre

Cuatro sesiones, alrededor de 100 pruebas, 5 defectos encontrados (1 de severidad alta, 2 media,
2 baja), todos corregidos y verificados en el mismo sprint. No quedan defectos abiertos. Quedan
cuatro observaciones para conversar con el negocio y una tarea pendiente (O-5).
