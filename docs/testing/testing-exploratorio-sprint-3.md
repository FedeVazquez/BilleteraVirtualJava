# Testing exploratorio — Sprint 3

**Fecha:** 15/10/2026 · **Build:** rama `dev` · **Entorno:** local sin Docker (perfil `h2`, mails con
`scripts/fake_smtp.py`), los 5 microservicios levantados y todas las peticiones entrando por el API
Gateway (`http://localhost:8090`).

**Alcance:** lo agregado en el sprint: ingreso de dinero desde una tarjeta (`POST /accounts/{id}/transferences`),
actividad (`GET /accounts/{id}/activity`), detalle (`GET /accounts/{id}/activity/{transactionId}`) y los
filtros opcionales por monto, período e ingreso/egreso.

## Organización

Igual que en el Sprint 1: sesiones con charter, tours y notas tomadas durante la prueba (ver
`testing-exploratorio.md`). Las pruebas se armaron con scripts de Python para poder enviar
peticiones simultáneas y repetir escenarios.

| Sesión | Charter | Tours | Pruebas |
|---|---|---|---|
| 1 | Explorar el **ingreso de dinero** con montos límite, tipos de dato incorrectos, tarjetas eliminadas y envíos repetidos, para descubrir formas de alterar el saldo indebidamente. | Tour de los datos (valores límite), tour del saboteador (tipos incorrectos), tour del impaciente (doble clic) | 27 |
| 2 | Explorar la **actividad y sus filtros** con combinaciones, bordes de los rangos y volumen, para descubrir resultados incorrectos o mensajes confusos. | Tour de los datos, tour del volumen (200 movimientos), tour del curioso (parámetros desconocidos) | 21 |

## Sesión 1 — Ingreso de dinero

**Notas**

- Montos: `0.01` y `1000000` se aceptan; `0`, `-5`, `10.555` (3 decimales), `1000000.01` y `null` se rechazan con 400 y mensaje claro.
- `1e3` (notación científica) se acepta como 1000: es un número JSON válido.
- **`"amount": "100"` (texto) se acepta y se acredita.** Lo mismo con `"cardId": "1"`. → DEF-006.
- `"amount": "cien"` y `"amount": true` se rechazan con 400.
- Tarjeta inexistente, negativa, de otra cuenta o recién eliminada → 404, y el saldo no cambia.
- El detalle de un ingreso hecho con una tarjeta ya eliminada sigue mostrando sus últimos 4 dígitos (se guardan como copia al momento de la operación).
- Ingresar en una cuenta ajena → 403; sin token → 401; `Content-Type: text/plain` → 415.
- Precisión: 10 ingresos de `0.10` dan un saldo de exactamente `1.00` (se usa `BigDecimal`, no `double`).
- **Doble clic:** dos envíos idénticos al mismo tiempo generan dos ingresos. El saldo queda bien (no se pisan), pero el usuario podría acreditar dos veces sin querer. → O-6.
- No hay tope de saldo ni de ingresos diarios: tres ingresos de 1.000.000 seguidos se aceptan. → O-9.

## Sesión 2 — Actividad, filtros y detalle

**Notas**

- La actividad viene ordenada del movimiento más reciente al más antiguo, también con 200 movimientos.
- Volumen: 200 movimientos en la actividad = 37 KB y 22 ms de respuesta. No hay paginación. → O-8.
- Los últimos movimientos (`/transactions`) son siempre 5.
- Rangos: `minAmount` y `maxAmount` son inclusivos, así que **$1.000 aparece en "$0 a $1.000" y en "$1.000 a $5.000"**: los rangos de la consigna se superponen en los bordes. → O-7.
- Período: `from` = `to` = hoy devuelve los movimientos de hoy; `from` = mañana devuelve una lista vacía. Las fechas se interpretan en hora de Argentina.
- **`direction=income` (en minúscula) se rechaza con 400**; solo funciona `INCOME`. → DEF-008.
- **Los errores de conversión muestran nombres internos**: con `minAmount=abc` el detalle dice `Failed to convert value of type 'java.lang.String' to required type 'java.math.BigDecimal'...`, y lo mismo con una fecha imposible (`2026-02-30`) o un tipo inválido. → DEF-007.
- `minAmount` negativo, `from` posterior a `to` y `minAmount` mayor que `maxAmount` → 400 con mensaje claro.
- Parámetros desconocidos (`foo=bar`) se ignoran.
- Detalle: id `0` o de otra cuenta → 404; id que excede el rango de `Long` → 400; cuenta inexistente → 403 (no revela si existe).

## Defectos

| ID | Severidad | Descripción | Cómo se reproduce | Estado |
|---|---|---|---|---|
| DEF-006 | Media | El ingreso de dinero acepta el monto y la tarjeta como texto y los convierte en silencio. En una operación con dinero, el contrato de tipos tiene que ser estricto. | `{"cardId": 1, "amount": "100"}` → 201 (esperado 400). | **Corregido y verificado** (TC-153) |
| DEF-007 | Baja | Los errores de conversión exponen nombres de clases internas de Java. | `GET /accounts/{id}/activity?minAmount=abc` → el detalle menciona `java.math.BigDecimal`. | **Corregido y verificado** en los tres servicios (TC-163) |
| DEF-008 | Baja | El filtro `direction` distingue mayúsculas. | `?direction=income` → 400 (esperado: igual que `INCOME`). | **Corregido y verificado** (TC-161) |

## Observaciones

| ID | Observación | Recomendación |
|---|---|---|
| O-6 | No hay protección contra el doble envío: el mismo ingreso enviado dos veces se acredita dos veces. | Aceptar un header `Idempotency-Key` en las operaciones de dinero. Prioritario para las transferencias del Sprint 4. |
| O-7 | Los rangos de monto de la consigna se superponen en los bordes ($1.000, $5.000, $20.000, $100.000). | Definir con el negocio si los bordes son "hasta" o "desde". El frontend puede usar `maxAmount=999.99`. |
| O-8 | La actividad no está paginada. Con 200 movimientos responde rápido, pero una cuenta con años de uso devolvería miles. | Agregar `page` y `size` cuando el frontend lo necesite. |
| O-9 | No hay límites de monto diario ni de saldo. | Definir límites con el negocio (prevención de fraude y lavado). |

## Cierre

Dos sesiones, 48 pruebas, 3 defectos (1 de severidad media y 2 baja), todos corregidos y verificados
con tests automatizados y casos de regresión. Ninguna prueba logró alterar el saldo de forma indebida
ni acceder a datos de otra cuenta.
