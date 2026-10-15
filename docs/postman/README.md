# Colección de Postman

Pruebas de la API (sprints 1 a 4) con scripts que validan cada respuesta. Es el testing manual que
pide el Sprint 4 ("utilizar Postman para las pruebas y crear los scripts necesarios").

## Uso

1. Levantar el sistema con Docker: `docker compose up -d --build` (hace falta MailHog para leer el código de verificación).
2. En Postman: **Import** → `DigitalMoneyHouse.postman_collection.json` y `DigitalMoneyHouse-local.postman_environment.json`.
3. Elegir el entorno *Digital Money House - local*.
4. Botón derecho sobre la colección → **Run collection** → ejecutar en orden.

Cada corrida crea un usuario y un destinatario nuevos (email y DNI con la hora actual), así que se
puede repetir cuantas veces se quiera.

## Qué hace

| Carpeta | Requests | Qué valida |
|---|---|---|
| 01 Registro y acceso | 8 | Registro con CVU y alias, email repetido, login sin verificar (403), lectura del código desde MailHog, verificación, login, contraseña incorrecta (400), usuario inexistente (404) |
| 02 Cuenta y perfil | 6 | Saldo, perfil, edición, cambio de alias, últimos movimientos, acceso sin token (401) |
| 03 Tarjetas | 8 | Alta (solo últimos 4 dígitos), duplicada (409), listado, detalle, vencida (400), baja y baja repetida (404) |
| 04 Ingreso y actividad | 7 | Ingreso desde tarjeta (201), monto inválido, saldo, actividad, filtros, detalle, inexistente (404) |
| 05 Transferencias | 10 | Por alias y por CVU, fondos insuficientes (410), destino inexistente (400), idempotencia, destinatarios, comprobante PDF, saldo final |
| 06 Cierre de sesión | 2 | Logout y token invalidado (401) |

Los datos que necesita cada paso (token, ids, alias) quedan en variables de la colección.

## Verificación

La colección se ejecutó completa contra el sistema levantado: 41 de 41 requests con todos sus tests
aprobados. Como en la máquina de desarrollo no hay Postman, se usó un ejecutor equivalente que corre
los mismos scripts.
