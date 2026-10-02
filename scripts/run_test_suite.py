"""Ejecuta los casos de prueba manuales del Sprint 1 contra el sistema levantado y registra el resultado.

Cada caso tiene su ficha (precondiciones, pasos, resultado esperado, suite) y una funcion que lo
ejecuta contra el API Gateway. Los casos que no se pueden ejecutar desde un script (por ejemplo,
apagar un servicio) figuran igual en la planilla, con el resultado de su ejecucion manual.

Uso:
    python scripts/run_test_suite.py                 # suite de regresion (todos los casos)
    python scripts/run_test_suite.py --suite smoke   # solo la suite de humo
    python scripts/run_test_suite.py --mailhog http://localhost:8025   # si los mails van a MailHog (Docker)

Deja el resultado en docs/testing/resultados-ejecucion.json (lo usa build_test_sheet.py para armar
la planilla). Solo usa la libreria estandar de Python 3.
"""
import argparse
import base64
import hashlib
import hmac
import json
import os
import quopri
import random
import re
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from datetime import datetime

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
parser.add_argument("--gateway", default="http://localhost:8090")
parser.add_argument("--users-direct", default="http://localhost:8082", help="users-service sin pasar por el gateway")
parser.add_argument("--eureka", default="http://localhost:8761")
parser.add_argument("--mail-file", default=os.path.join(ROOT, "mails.jsonl"), help="salida de scripts/fake_smtp.py")
parser.add_argument("--mailhog", default=None, help="URL de MailHog; si se indica, los mails se leen de su API")
parser.add_argument("--jwt-secret", default="dmh-dev-secret-cambiar-en-produccion-0123456789")
parser.add_argument("--suite", choices=["smoke", "regression"], default="regression")
parser.add_argument("--output", default=os.path.join(ROOT, "docs", "testing", "resultados-ejecucion.json"))
ARGS = parser.parse_args()

PASSWORD = "Clave1234"
ALIAS_WORDS = set()
with open(os.path.join(ROOT, "account-service", "src", "main", "resources", "alias-words.txt"), encoding="utf-8") as f:
    ALIAS_WORDS = {line.strip() for line in f if line.strip() and not line.startswith("#")}


# ---------------------------------------------------------------- utilidades

class Response:
    def __init__(self, status, text):
        self.status = status
        self.text = text
        try:
            self.json = json.loads(text) if text else None
        except ValueError:
            self.json = None

    @property
    def message(self):
        return self.json.get("message") if isinstance(self.json, dict) else None

    def __str__(self):
        return f"HTTP {self.status} {self.text[:160]}"


def api(method, path, body=None, token=None, headers=None, base=None, raw=None):
    data = raw if raw is not None else (json.dumps(body).encode("utf-8") if body is not None else None)
    all_headers = {"Content-Type": "application/json"}
    if token:
        all_headers["Authorization"] = "Bearer " + token
    if headers:
        all_headers.update(headers)
    request = urllib.request.Request((base or ARGS.gateway) + path, data=data, method=method, headers=all_headers)
    try:
        with urllib.request.urlopen(request, timeout=20) as response:
            return Response(response.status, response.read().decode("utf-8"))
    except urllib.error.HTTPError as error:
        return Response(error.code, error.read().decode("utf-8"))


def check(condition, problem):
    if not condition:
        raise AssertionError(problem)


def expect_status(response, status):
    check(response.status == status, f"se esperaba HTTP {status} y se obtuvo {response}")
    return response


def mails_to(email):
    """Mails recibidos por esa direccion, del mas viejo al mas nuevo: [(asunto, cuerpo), ...]."""
    time.sleep(0.7)  # el mail sale despues del commit de la transaccion
    if ARGS.mailhog:
        query = urllib.parse.urlencode({"kind": "to", "query": email})
        with urllib.request.urlopen(f"{ARGS.mailhog}/api/v2/search?{query}", timeout=10) as response:
            items = json.loads(response.read().decode("utf-8"))["items"]
        found = []
        for item in reversed(items):  # MailHog devuelve primero el mas nuevo
            body = quopri.decodestring(item["Content"]["Body"].encode("utf-8")).decode("utf-8", "replace")
            found.append((item["Content"]["Headers"].get("Subject", [""])[0], body))
        return found
    if not os.path.exists(ARGS.mail_file):
        return []
    with open(ARGS.mail_file, encoding="utf-8") as f:
        records = [json.loads(line) for line in f if line.strip()]
    return [(r["subject"], r["body"]) for r in records if email in r["to"]]


def verification_code(email):
    bodies = [body for _, body in mails_to(email) if "código de verificación" in body]
    check(bodies, f"no llegó ningún mail con código de verificación a {email}")
    return re.search(r"\b(\d{6})\b", bodies[-1]).group(1)


def reset_token(email):
    bodies = [body for _, body in mails_to(email) if "token=" in body]
    check(bodies, f"no llegó ningún mail de recupero de contraseña a {email}")
    return re.search(r"token=([\w-]+)", bodies[-1]).group(1)


RUN_ID = random.randint(100, 999)
_counter = [0]


def user_data(**overrides):
    """Datos validos y unicos (email y DNI no repetidos entre casos ni entre ejecuciones)."""
    _counter[0] += 1
    data = {
        "firstName": "Juan", "lastName": "Pérez",
        "dni": f"{RUN_ID}{_counter[0]:05d}",
        "email": f"qa{RUN_ID}n{_counter[0]}@mail.com",
        "phone": "+5491122334455", "password": PASSWORD,
    }
    data.update(overrides)
    return data


def register(**overrides):
    data = user_data(**overrides)
    response = expect_status(api("POST", "/users/register", data), 201)
    return data, response.json


def verified_user():
    data, created = register()
    expect_status(api("POST", "/auth/verify-email", {"email": data["email"], "code": verification_code(data["email"])}), 200)
    return data, created


def login(email, password=PASSWORD):
    return expect_status(api("POST", "/auth/login", {"email": email, "password": password}), 200).json["token"]


def logged_user():
    data, created = verified_user()
    return data, created, login(data["email"])


def b64url(raw):
    return base64.urlsafe_b64encode(raw).rstrip(b"=").decode()


def jwt_payload(token):
    part = token.split(".")[1]
    return json.loads(base64.urlsafe_b64decode(part + "=" * (-len(part) % 4)))


def sign_jwt(payload, secret):
    unsigned = b64url(b'{"alg":"HS256"}') + "." + b64url(json.dumps(payload).encode())
    signature = hmac.new(secret.encode(), unsigned.encode(), hashlib.sha256).digest()
    return unsigned + "." + b64url(signature)


# ---------------------------------------------------------------- casos

CASES = []
SMOKE, REGRESSION = "Smoke + Regresión", "Regresión"


def case(case_id, story, title, pre, steps, expected, kind, priority, suite, junit="", manual=None):
    """Registra un caso. Si 'manual' se indica, el caso no se ejecuta por script: manual = (estado, resultado, fecha)."""
    def decorator(function):
        CASES.append({
            "id": case_id, "historia": story, "titulo": title, "precondiciones": pre, "pasos": steps,
            "esperado": expected, "tipo": kind, "prioridad": priority, "suite": suite, "junit": junit,
            "ejecucion": "Manual" if manual else "Script", "manual": manual, "run": function,
        })
        return function
    return decorator


HU1 = "HU1 Registro"
HU2 = "HU2 Login"
HU3 = "HU3 Logout"
OP1 = "Opcional: verificación de email"
OP2 = "Opcional: recupero de contraseña"
INF = "Infraestructura"
REG_STEPS = "POST /users/register con el JSON indicado."


# ---- HU1: registro

@case("TC-001", HU1, "Registro exitoso con datos válidos", "Email y DNI no registrados.",
      "POST /users/register con nombre, apellido, DNI, email, teléfono y contraseña válidos.",
      "HTTP 201. JSON con id, firstName, lastName, dni, email, phone, cvu y alias.", "Positivo", "Alta", SMOKE,
      "UserApiTest.registroCorrectoDevuelve201ConCvuYAliasYSinLaClave")
def tc001():
    data, created = register()
    for field in ("id", "firstName", "lastName", "dni", "email", "phone", "cvu", "alias"):
        check(created.get(field) not in (None, ""), f"falta el campo {field} en la respuesta: {created}")
    check(created["email"] == data["email"] and created["dni"] == data["dni"], f"los datos devueltos no coinciden: {created}")
    return f"HTTP 201, id={created['id']}, cvu={created['cvu']}, alias={created['alias']}"


@case("TC-002", HU1, "El CVU asignado tiene 22 dígitos numéricos", "—", REG_STEPS + " Revisar el campo cvu.",
      "cvu con exactamente 22 dígitos numéricos.", "Positivo", "Alta", REGRESSION,
      "CvuGeneratorTest.generaVeintidosDigitosNumericos")
def tc002():
    _, created = register()
    check(re.fullmatch(r"\d{22}", created["cvu"]), f"CVU inválido: {created['cvu']}")
    return f"cvu={created['cvu']} ({len(created['cvu'])} dígitos)"


@case("TC-003", HU1, "El alias son 3 palabras del archivo TXT separadas por punto", "—",
      REG_STEPS + " Revisar el campo alias y comparar cada palabra con alias-words.txt.",
      "alias con formato palabra.palabra.palabra; las 3 palabras existen en el TXT y no se repiten.",
      "Positivo", "Alta", REGRESSION, "AliasGeneratorTest.generaTresPalabrasDistintasSeparadasPorPunto")
def tc003():
    _, created = register()
    words = created["alias"].split(".")
    check(len(words) == 3 and len(set(words)) == 3, f"alias inválido: {created['alias']}")
    check(all(word in ALIAS_WORDS for word in words), f"alias con palabras que no están en el TXT: {created['alias']}")
    return f"alias={created['alias']} (las 3 palabras están en alias-words.txt)"


@case("TC-004", HU1, "Cada registro recibe un CVU y un alias distintos", "—",
      "Registrar 5 usuarios distintos y comparar sus CVU y alias.", "No hay CVU ni alias repetidos.",
      "Positivo", "Media", REGRESSION, "CvuGeneratorTest.generaValoresDistintos")
def tc004():
    created = [register()[1] for _ in range(5)]
    check(len({c["cvu"] for c in created}) == 5, "hay CVU repetidos")
    check(len({c["alias"] for c in created}) == 5, "hay alias repetidos")
    return "5 registros: 5 CVU distintos y 5 alias distintos"


@case("TC-005", HU1, "La respuesta del registro no incluye la contraseña", "—", REG_STEPS + " Revisar todos los campos de la respuesta.",
      "Ningún campo contiene la contraseña ni su hash.", "Seguridad", "Alta", REGRESSION,
      "UserApiTest.registroCorrectoDevuelve201ConCvuYAliasYSinLaClave")
def tc005():
    response = api("POST", "/users/register", user_data())
    expect_status(response, 201)
    check("password" not in response.json, "la respuesta incluye el campo password")
    check(PASSWORD not in response.text and "$2a$" not in response.text, "la respuesta incluye la clave o su hash")
    return "HTTP 201 sin campo password; campos: " + ", ".join(sorted(response.json))


@case("TC-006", HU1, "No se puede registrar un email ya registrado", "Existe un usuario con ese email.",
      "Registrar un usuario y volver a registrar otro con el mismo email y otro DNI.",
      "HTTP 400 con mensaje que indica que el email ya está registrado.", "Negativo", "Alta", REGRESSION,
      "UserApiTest.registroConEmailRepetidoDevuelve400")
def tc006():
    data, _ = register()
    response = expect_status(api("POST", "/users/register", user_data(email=data["email"])), 400)
    check("email" in response.message, f"mensaje inesperado: {response.message}")
    return f"HTTP 400: {response.message}"


@case("TC-007", HU1, "El email duplicado se detecta aunque cambien mayúsculas", "Existe un usuario con ese email.",
      "Registrar un usuario y volver a registrar con el mismo email en MAYÚSCULAS.", "HTTP 400.",
      "Borde", "Media", REGRESSION, "UserApiTest.registroConEmailRepetidoDevuelve400")
def tc007():
    data, _ = register()
    response = expect_status(api("POST", "/users/register", user_data(email=data["email"].upper())), 400)
    return f"HTTP 400: {response.message}"


@case("TC-008", HU1, "No se puede registrar un DNI ya registrado", "Existe un usuario con ese DNI.",
      "Registrar un usuario y volver a registrar otro con el mismo DNI y otro email.",
      "HTTP 400 con mensaje que indica que el DNI ya está registrado.", "Negativo", "Alta", REGRESSION,
      "UserServiceTest.rechazaUnDniYaRegistrado")
def tc008():
    data, _ = register()
    response = expect_status(api("POST", "/users/register", user_data(dni=data["dni"])), 400)
    check("DNI" in response.message, f"mensaje inesperado: {response.message}")
    return f"HTTP 400: {response.message}"


@case("TC-009", HU1, "Los campos obligatorios faltantes se informan uno por uno", "—", "POST /users/register con body {}.",
      "HTTP 400 con un detalle por cada uno de los 6 campos obligatorios.", "Negativo", "Alta", REGRESSION,
      "UserApiTest.registroConDatosInvalidosDevuelve400ConElDetallePorCampo")
def tc009():
    response = expect_status(api("POST", "/users/register", {}), 400)
    fields = {detail.split(":")[0] for detail in response.json["details"]}
    check(fields == {"firstName", "lastName", "dni", "email", "phone", "password"}, f"detalles: {response.json['details']}")
    return "HTTP 400 con detalle de: " + ", ".join(sorted(fields))


def invalid_field_case(case_id, title, field, value, priority="Media", junit="UserApiTest.registroConDatosInvalidosDevuelve400ConElDetallePorCampo"):
    @case(case_id, HU1, title, "—", f"POST /users/register con {field} = {json.dumps(value, ensure_ascii=False)} y el resto válido.",
          f"HTTP 400 con un detalle sobre el campo {field}. No se crea el usuario.", "Negativo", priority, REGRESSION, junit)
    def run():
        response = expect_status(api("POST", "/users/register", user_data(**{field: value})), 400)
        details = response.json.get("details") or []
        if details:
            check(any(d.startswith(field + ":") for d in details), f"el detalle no menciona {field}: {details}")
            return f"HTTP 400: {'; '.join(d for d in details if d.startswith(field + ':'))}"
        return f"HTTP 400: {response.message}"
    return run


invalid_field_case("TC-010", "Email con formato inválido", "email", "no-es-un-email", "Alta")
invalid_field_case("TC-011", "Email sin dominio completo (a@b)", "email", "a@b",
                   junit="UserApiTest.registroRechazaEmailSinDominioCompletoNombreConSimbolosYDniNumerico")
invalid_field_case("TC-012", "Contraseña de menos de 8 caracteres", "password", "1234567", "Alta")
invalid_field_case("TC-013", "Contraseña de más de 72 caracteres", "password", "x" * 73)
invalid_field_case("TC-014", "DNI con letras", "dni", "30a23456")
invalid_field_case("TC-015", "DNI de 9 dígitos", "dni", "301234567")
invalid_field_case("TC-016", "DNI enviado como número en vez de texto", "dni", 30123456,
                   junit="UserApiTest.registroRechazaEmailSinDominioCompletoNombreConSimbolosYDniNumerico")
invalid_field_case("TC-017", "Teléfono con menos de 8 dígitos", "phone", "1234567")
invalid_field_case("TC-018", "Nombre con símbolos (<script>)", "firstName", "<script>alert(1)</script>",
                   junit="UserApiTest.registroRechazaEmailSinDominioCompletoNombreConSimbolosYDniNumerico")
invalid_field_case("TC-019", "Nombre de más de 60 caracteres", "firstName", "a" * 61)


@case("TC-020", HU1, "Se aceptan nombres con acentos, ñ, apóstrofes y guiones", "—",
      "POST /users/register con firstName = \"María José\" y lastName = \"Núñez O'Higgins-Peña\".",
      "HTTP 201 y los nombres se devuelven tal cual se enviaron.", "Borde", "Media", REGRESSION,
      "UserApiTest.registroAceptaNombresConAcentosEniesApostrofesYGuiones")
def tc020():
    _, created = register(firstName="María José", lastName="Núñez O'Higgins-Peña")
    check(created["firstName"] == "María José" and created["lastName"] == "Núñez O'Higgins-Peña", f"nombres alterados: {created}")
    return f"HTTP 201: {created['firstName']} {created['lastName']}"


@case("TC-021", HU1, "JSON mal formado", "—", "POST /users/register con el body {esto no es json.",
      "HTTP 400 con el formato de error estándar.", "Negativo", "Media", REGRESSION,
      "UserApiTest.registroConJsonMalFormadoDevuelve400")
def tc021():
    response = expect_status(api("POST", "/users/register", raw=b"{esto no es json"), 400)
    check(response.json and response.json.get("status") == 400, f"el error no tiene el formato estándar: {response}")
    return f"HTTP 400: {response.message}"


@case("TC-022", HU1, "Los campos extra del JSON no dan privilegios", "—",
      "POST /users/register agregando id=999, roles=[ROLE_ADMIN] y emailVerified=true. Luego intentar login sin verificar el email.",
      "HTTP 201 con un id propio (no 999). El usuario NO queda verificado ni como administrador: el login responde 403.",
      "Seguridad", "Alta", REGRESSION)
def tc022():
    data, created = register(id=999, roles=["ROLE_ADMIN"], emailVerified=True)
    check(created["id"] != 999, "se aceptó el id enviado por el cliente")
    response = expect_status(api("POST", "/auth/login", {"email": data["email"], "password": PASSWORD}), 403)
    return f"HTTP 201 con id={created['id']}; login sin verificar: HTTP 403 (emailVerified ignorado)"


@case("TC-023", HU1, "El registro crea la cuenta digital con saldo 0", "—",
      "Registrar, verificar el email, hacer login y consultar GET /accounts/user/{id}.",
      "HTTP 200 con el mismo CVU y alias del registro y balance 0.", "Positivo", "Alta", REGRESSION,
      "AccountApiTest.elDuenioPuedeConsultarSuCuenta")
def tc023():
    _, created, token = logged_user()
    account = expect_status(api("GET", f"/accounts/user/{created['id']}", token=token), 200).json
    check(account["cvu"] == created["cvu"] and account["alias"] == created["alias"], f"la cuenta no coincide: {account}")
    check(float(account["balance"]) == 0, f"saldo inicial distinto de 0: {account['balance']}")
    return f"HTTP 200: cvu y alias coinciden, balance={account['balance']}"


@case("TC-024", HU1, "Error 500 si falla la creación de la cuenta, sin dejar datos a medias",
      "account-service apagado.", "Apagar account-service. POST /users/register con datos válidos. Luego intentar login con ese email.",
      "HTTP 500 con mensaje genérico (sin detalles internos). El usuario NO queda guardado (login responde 404) y no se envía mail.",
      "Negativo", "Alta", REGRESSION, "UserApiTest.siFallaLaCreacionDeLaCuentaDevuelve500YNoQuedaElUsuarioGuardado",
      manual=("Pasó", "HTTP 500 \"Ocurrió un error interno. Intente nuevamente más tarde.\"; login posterior: HTTP 404; no se envió mail.", "02/10/2026"))
def tc024():
    pass


@case("TC-025", HU1, "Content-Type distinto de JSON", "—", "POST /users/register con Content-Type: text/plain.",
      "HTTP 415.", "Negativo", "Baja", REGRESSION)
def tc025():
    response = expect_status(api("POST", "/users/register", raw=b"hola", headers={"Content-Type": "text/plain"}), 415)
    return f"HTTP 415: {response.message}"


# ---- HU2: login

@case("TC-030", HU2, "Login exitoso devuelve un token", "Usuario registrado con email verificado.",
      "POST /auth/login con email y contraseña correctos.",
      "HTTP 200. JSON con token, tokenType = Bearer y expiresIn.", "Positivo", "Alta", SMOKE,
      "AuthApiTest.loginCorrectoDevuelve200ConUnTokenValido")
def tc030():
    data, _ = verified_user()
    response = expect_status(api("POST", "/auth/login", {"email": data["email"], "password": PASSWORD}), 200)
    check(response.json["token"].count(".") == 2, "el token no tiene formato JWT")
    check(response.json["tokenType"] == "Bearer" and response.json["expiresIn"] > 0, f"respuesta inesperada: {response.json}")
    return f"HTTP 200: token JWT, tokenType={response.json['tokenType']}, expiresIn={response.json['expiresIn']} s"


@case("TC-031", HU2, "El token identifica al usuario y sus roles", "Usuario logueado.",
      "Hacer login y decodificar el payload del JWT.",
      "El payload tiene sub = id del usuario, email, roles = [ROLE_USER], jti, iat y exp; no contiene la contraseña.",
      "Positivo", "Media", REGRESSION, "JwtServiceTest.elTokenLlevaElUsuarioElEmailLosRolesYUnIdentificadorUnico")
def tc031():
    data, created, token = logged_user()
    payload = jwt_payload(token)
    check(payload["sub"] == str(created["id"]) and payload["email"] == data["email"], f"payload: {payload}")
    check(payload["roles"] == ["ROLE_USER"] and payload.get("jti") and payload["exp"] > payload["iat"], f"payload: {payload}")
    check("password" not in payload, "el token contiene la contraseña")
    return f"sub={payload['sub']}, roles={payload['roles']}, vence en {(payload['exp'] - payload['iat']) // 60} min"


@case("TC-032", HU2, "Login de un usuario inexistente", "El email no está registrado.",
      "POST /auth/login con un email no registrado.", "HTTP 404 (usuario inexistente).", "Negativo", "Alta", REGRESSION,
      "AuthApiTest.loginDeUnUsuarioInexistenteDevuelve404")
def tc032():
    response = expect_status(api("POST", "/auth/login", {"email": f"nadie{RUN_ID}@mail.com", "password": PASSWORD}), 404)
    return f"HTTP 404: {response.message}"


@case("TC-033", HU2, "Login con contraseña incorrecta", "Usuario registrado con email verificado.",
      "POST /auth/login con el email correcto y otra contraseña.", "HTTP 400 (contraseña incorrecta). No se entrega token.",
      "Negativo", "Alta", REGRESSION, "AuthApiTest.loginConContraseniaIncorrectaDevuelve400")
def tc033():
    data, _ = verified_user()
    response = expect_status(api("POST", "/auth/login", {"email": data["email"], "password": "OtraClave99"}), 400)
    check("token" not in response.text, "la respuesta de error incluye un token")
    return f"HTTP 400: {response.message}"


@case("TC-034", HU2, "Login sin email ni contraseña", "—", "POST /auth/login con body {}.",
      "HTTP 400 con el detalle de los 2 campos obligatorios.", "Negativo", "Media", REGRESSION,
      "AuthApiTest.loginSinDatosDevuelve400")
def tc034():
    response = expect_status(api("POST", "/auth/login", {}), 400)
    check(len(response.json["details"]) == 2, f"detalles: {response.json['details']}")
    return "HTTP 400: " + "; ".join(response.json["details"])


@case("TC-035", HU2, "El email del login no distingue mayúsculas", "Usuario registrado con email verificado.",
      "POST /auth/login con el email en MAYÚSCULAS y la contraseña correcta.", "HTTP 200 con token.",
      "Borde", "Media", REGRESSION)
def tc035():
    data, _ = verified_user()
    expect_status(api("POST", "/auth/login", {"email": data["email"].upper(), "password": PASSWORD}), 200)
    return "HTTP 200 con token"


@case("TC-036", HU2, "La contraseña sí distingue mayúsculas", "Usuario registrado con email verificado.",
      "POST /auth/login con la contraseña en minúsculas.", "HTTP 400.", "Borde", "Media", REGRESSION)
def tc036():
    data, _ = verified_user()
    response = expect_status(api("POST", "/auth/login", {"email": data["email"], "password": PASSWORD.lower()}), 400)
    return f"HTTP 400: {response.message}"


@case("TC-037", HU2, "Con el token se accede a un recurso protegido", "Usuario logueado.",
      "GET /users/{id propio} con el header Authorization: Bearer <token>.",
      "HTTP 200 con los datos del usuario, sin la contraseña.", "Positivo", "Alta", SMOKE,
      "UserApiTest.elUsuarioPuedeConsultarSusPropiosDatos")
def tc037():
    data, created, token = logged_user()
    response = expect_status(api("GET", f"/users/{created['id']}", token=token), 200)
    check(response.json["email"] == data["email"] and "password" not in response.json, f"respuesta: {response.json}")
    return f"HTTP 200: {response.json['firstName']} {response.json['lastName']} <{response.json['email']}>"


@case("TC-038", HU2, "La sesión se mantiene entre peticiones (recarga de página)", "Usuario logueado.",
      "Con el mismo token, hacer 5 peticiones seguidas a GET /users/{id}, simulando recargas de la página.",
      "Las 5 responden HTTP 200: no hace falta volver a loguearse mientras el token no venza.", "Positivo", "Alta", REGRESSION)
def tc038():
    _, created, token = logged_user()
    statuses = [api("GET", f"/users/{created['id']}", token=token).status for _ in range(5)]
    check(statuses == [200] * 5, f"respuestas: {statuses}")
    return "5 peticiones seguidas con el mismo token: todas HTTP 200"


@case("TC-039", HU2, "Recurso protegido sin token", "—", "GET /users/1 sin header Authorization.", "HTTP 401.",
      "Seguridad", "Alta", SMOKE, "AuthenticationFilterTest.unaRutaProtegidaSinTokenDevuelve401SinLlegarAlServicio")
def tc039():
    response = expect_status(api("GET", "/users/1"), 401)
    return f"HTTP 401: {response.message}"


@case("TC-040", HU2, "Token adulterado", "Usuario logueado.",
      "Tomar un token válido, cambiar en el payload el sub por otro usuario y los roles por ROLE_ADMIN conservando la firma original, y pedir GET /users/{otro id}.",
      "HTTP 401: la firma ya no coincide.", "Seguridad", "Alta", REGRESSION, "JwtServiceTest.rechazaUnTokenAdulterado")
def tc040():
    _, created, token = logged_user()
    header, _, signature = token.split(".")
    forged_payload = dict(jwt_payload(token), sub=str(created["id"] + 1), roles=["ROLE_ADMIN"])
    forged = header + "." + b64url(json.dumps(forged_payload).encode()) + "." + signature
    response = expect_status(api("GET", f"/users/{created['id'] + 1}", token=forged), 401)
    return f"HTTP 401: {response.message}"


@case("TC-041", HU2, "Token sin firma (alg = none)", "—",
      "Armar un JWT con header {\"alg\":\"none\"}, roles ROLE_ADMIN y sin firma, y pedir GET /users/1.", "HTTP 401.",
      "Seguridad", "Alta", REGRESSION)
def tc041():
    payload = {"sub": "1", "roles": ["ROLE_ADMIN"], "jti": "x", "iat": int(time.time()), "exp": int(time.time()) + 600}
    forged = b64url(b'{"alg":"none"}') + "." + b64url(json.dumps(payload).encode()) + "."
    response = expect_status(api("GET", "/users/1", token=forged), 401)
    return f"HTTP 401: {response.message}"


@case("TC-042", HU2, "Token firmado con otro secreto", "—",
      "Armar un JWT con rol ROLE_ADMIN firmado (HS256) con el secreto \"secret\" y pedir GET /users/1.", "HTTP 401.",
      "Seguridad", "Alta", REGRESSION, "JwtServiceTest.rechazaUnTokenFirmadoConOtroSecreto")
def tc042():
    payload = {"sub": "1", "roles": ["ROLE_ADMIN"], "jti": "x", "iat": int(time.time()), "exp": int(time.time()) + 600}
    response = expect_status(api("GET", "/users/1", token=sign_jwt(payload, "secret")), 401)
    return f"HTTP 401: {response.message}"


@case("TC-043", HU2, "Token vencido", "Entorno local con el secreto JWT de desarrollo.",
      "Armar un JWT con firma válida pero con exp en el pasado y pedir GET /users/1.", "HTTP 401.",
      "Seguridad", "Alta", REGRESSION, "JwtServiceTest.rechazaUnTokenVencido")
def tc043():
    now = int(time.time())
    payload = {"sub": "1", "email": "x@mail.com", "roles": ["ROLE_USER"], "jti": "vencido", "iat": now - 7200, "exp": now - 3600}
    response = expect_status(api("GET", "/users/1", token=sign_jwt(payload, ARGS.jwt_secret)), 401)
    return f"HTTP 401: {response.message}"


@case("TC-044", HU2, "Un usuario no puede ver los datos de otro", "Dos usuarios registrados; el primero logueado.",
      "Con el token del usuario A pedir GET /users/{id de B} y GET /accounts/user/{id de B}.", "HTTP 403 en ambos.",
      "Seguridad", "Alta", REGRESSION, "UserApiTest.unUsuarioNoPuedeConsultarLosDatosDeOtro")
def tc044():
    _, _, token_a = logged_user()
    _, user_b = register()
    expect_status(api("GET", f"/users/{user_b['id']}", token=token_a), 403)
    expect_status(api("GET", f"/accounts/user/{user_b['id']}", token=token_a), 403)
    return "HTTP 403 en /users/{id ajeno} y en /accounts/user/{id ajeno}"


@case("TC-045", HU2, "Error del servidor de autenticación caído", "auth-service apagado.",
      "Apagar auth-service. Pedir un recurso protegido con un token válido.",
      "HTTP 503 con mensaje claro; no se deja pasar la petición sin validar.", "Negativo", "Media", REGRESSION,
      "AuthenticationFilterTest.siAuthServiceNoRespondeDevuelve503",
      manual=("Pasó", "HTTP 503 \"El servicio de autenticación no está disponible. Intente nuevamente más tarde.\"", "02/10/2026"))
def tc045():
    pass


# ---- HU3: logout

@case("TC-050", HU3, "Logout con token válido", "Usuario logueado.",
      "POST /user/logout con el header Authorization: Bearer <token>.", "HTTP 200.", "Positivo", "Alta", SMOKE,
      "AuthApiTest.despuesDelLogoutElTokenDejaDeSerValido")
def tc050():
    _, _, token = logged_user()
    response = expect_status(api("POST", "/user/logout", token=token), 200)
    return f"HTTP 200: {response.message}"


@case("TC-051", HU3, "Después del logout el token queda invalidado", "Usuario logueado.",
      "Hacer logout y volver a usar el mismo token en GET /users/{id}.", "HTTP 401.", "Seguridad", "Alta", SMOKE,
      "AuthApiTest.despuesDelLogoutElTokenDejaDeSerValido")
def tc051():
    _, created, token = logged_user()
    expect_status(api("GET", f"/users/{created['id']}", token=token), 200)
    expect_status(api("POST", "/user/logout", token=token), 200)
    response = expect_status(api("GET", f"/users/{created['id']}", token=token), 401)
    return f"antes del logout HTTP 200; después HTTP 401: {response.message}"


@case("TC-052", HU3, "Logout sin token", "—", "POST /user/logout sin header Authorization.", "HTTP 401.",
      "Negativo", "Media", REGRESSION, "AuthApiTest.logoutSinTokenOConTokenInvalidoDevuelve401")
def tc052():
    response = expect_status(api("POST", "/user/logout"), 401)
    return f"HTTP 401: {response.message}"


@case("TC-053", HU3, "Logout con token inválido", "—", "POST /user/logout con Authorization: Bearer no.es.un-token.", "HTTP 401.",
      "Negativo", "Media", REGRESSION, "AuthApiTest.logoutSinTokenOConTokenInvalidoDevuelve401")
def tc053():
    response = expect_status(api("POST", "/user/logout", token="no.es.un-token"), 401)
    return f"HTTP 401: {response.message}"


@case("TC-054", HU3, "El logout cierra solo esa sesión", "El mismo usuario logueado dos veces (dos dispositivos).",
      "Hacer dos logins del mismo usuario. Logout con el primer token. Usar el segundo token.",
      "El primer token responde 401 y el segundo sigue respondiendo 200.", "Positivo", "Media", REGRESSION,
      "AuthApiTest.elLogoutInvalidaSoloEseTokenYEsIdempotente")
def tc054():
    data, created, first = logged_user()
    second = login(data["email"])
    expect_status(api("POST", "/user/logout", token=first), 200)
    expect_status(api("GET", f"/users/{created['id']}", token=first), 401)
    expect_status(api("GET", f"/users/{created['id']}", token=second), 200)
    return "token 1: HTTP 401; token 2: HTTP 200"


@case("TC-055", HU3, "Se puede volver a iniciar sesión después del logout", "Usuario que acaba de cerrar sesión.",
      "Login, logout y login nuevamente. Usar el nuevo token.", "El segundo login responde 200 y su token funciona.",
      "Positivo", "Alta", REGRESSION)
def tc055():
    data, created, token = logged_user()
    expect_status(api("POST", "/user/logout", token=token), 200)
    expect_status(api("GET", f"/users/{created['id']}", token=login(data["email"])), 200)
    return "nuevo login HTTP 200 y el token nuevo accede (HTTP 200)"


@case("TC-056", HU3, "Logout con un método HTTP incorrecto", "Usuario logueado.", "GET /user/logout con token válido.",
      "HTTP 405; la sesión no se cierra.", "Negativo", "Baja", REGRESSION)
def tc056():
    _, created, token = logged_user()
    expect_status(api("GET", "/user/logout", token=token), 405)
    expect_status(api("GET", f"/users/{created['id']}", token=token), 200)
    return "HTTP 405 y el token sigue activo"


# ---- Opcional: verificacion de email

@case("TC-060", OP1, "El registro envía por mail un código de 6 dígitos", "—",
      "Registrar un usuario y revisar la casilla (MailHog o fake_smtp).", "Llega un mail al email registrado con un código de 6 dígitos.",
      "Positivo", "Alta", REGRESSION, "UserApiTest.elRegistroEnviaPorMailElCodigoDeVerificacion")
def tc060():
    data, _ = register()
    code = verification_code(data["email"])
    subject = mails_to(data["email"])[-1][0]
    return f"mail \"{subject}\" con código de 6 dígitos ({code})"


@case("TC-061", OP1, "No se puede hacer login sin verificar el email", "Usuario recién registrado (sin verificar).",
      "POST /auth/login con credenciales correctas.", "HTTP 403 indicando que debe verificar el email. No se entrega token.",
      "Negativo", "Alta", REGRESSION, "AuthApiTest.loginSinVerificarElEmailDevuelve403")
def tc061():
    data, _ = register()
    response = expect_status(api("POST", "/auth/login", {"email": data["email"], "password": PASSWORD}), 403)
    return f"HTTP 403: {response.message}"


@case("TC-062", OP1, "Verificación con el código correcto habilita el primer login", "Usuario registrado; código recibido por mail.",
      "POST /auth/verify-email con el email y el código recibido. Luego POST /auth/login.",
      "Verificación HTTP 200 y login HTTP 200 con token.", "Positivo", "Alta", SMOKE,
      "UserApiTest.verificarElEmailConElCodigoRecibido")
def tc062():
    data, _ = register()
    expect_status(api("POST", "/auth/verify-email", {"email": data["email"], "code": verification_code(data["email"])}), 200)
    login(data["email"])
    return "verificación HTTP 200; primer login HTTP 200 con token"


@case("TC-063", OP1, "Código de verificación incorrecto", "Usuario registrado sin verificar.",
      "POST /auth/verify-email con un código de 6 dígitos distinto al enviado.", "HTTP 400. El email sigue sin verificar.",
      "Negativo", "Alta", REGRESSION, "EmailVerificationServiceTest.conUnCodigoIncorrectoRechazaYCuentaElIntento")
def tc063():
    data, _ = register()
    wrong = "000000" if verification_code(data["email"]) != "000000" else "111111"
    response = expect_status(api("POST", "/auth/verify-email", {"email": data["email"], "code": wrong}), 400)
    expect_status(api("POST", "/auth/login", {"email": data["email"], "password": PASSWORD}), 403)
    return f"HTTP 400: {response.message}; el login sigue en 403"


@case("TC-064", OP1, "Código con formato inválido", "—", "POST /auth/verify-email con code = \"12ab\".",
      "HTTP 400 con detalle sobre el campo code.", "Negativo", "Media", REGRESSION,
      "AuthApiTest.verificarEmailConUnCodigoQueNoTieneSeisDigitosDevuelve400SinLlamarAUsersService")
def tc064():
    response = expect_status(api("POST", "/auth/verify-email", {"email": "alguien@mail.com", "code": "12ab"}), 400)
    return "HTTP 400: " + "; ".join(response.json["details"])


@case("TC-065", OP1, "Reenvío del código: el anterior deja de servir", "Usuario registrado sin verificar.",
      "POST /auth/verify-email/resend. Intentar verificar con el código viejo y luego con el nuevo.",
      "Reenvío HTTP 200 y llega un mail nuevo. Código viejo: HTTP 400. Código nuevo: HTTP 200.", "Positivo", "Media", REGRESSION)
def tc065():
    data, _ = register()
    old = verification_code(data["email"])
    expect_status(api("POST", "/auth/verify-email/resend", {"email": data["email"]}), 200)
    new = verification_code(data["email"])
    if new == old:
        return "el código nuevo coincidió por azar con el anterior (1 en 1.000.000): caso no concluyente"
    expect_status(api("POST", "/auth/verify-email", {"email": data["email"], "code": old}), 400)
    expect_status(api("POST", "/auth/verify-email", {"email": data["email"], "code": new}), 200)
    return "reenvío HTTP 200; código viejo HTTP 400; código nuevo HTTP 200"


@case("TC-066", OP1, "Bloqueo tras 5 intentos fallidos", "Usuario registrado sin verificar.",
      "Enviar 5 códigos incorrectos y luego el código correcto. Pedir el reenvío y verificar con el código nuevo.",
      "Tras 5 fallos, el código correcto también se rechaza (HTTP 400). Con un código reenviado se puede verificar.",
      "Seguridad", "Alta", REGRESSION, "EmailVerificationServiceTest.alSuperarLosIntentosRechazaInclusoElCodigoCorrecto")
def tc066():
    data, _ = register()
    code = verification_code(data["email"])
    wrong = "000000" if code != "000000" else "111111"
    for _ in range(5):
        expect_status(api("POST", "/auth/verify-email", {"email": data["email"], "code": wrong}), 400)
    response = expect_status(api("POST", "/auth/verify-email", {"email": data["email"], "code": code}), 400)
    expect_status(api("POST", "/auth/verify-email/resend", {"email": data["email"]}), 200)
    expect_status(api("POST", "/auth/verify-email", {"email": data["email"], "code": verification_code(data["email"])}), 200)
    return f"6.º intento con el código correcto: HTTP 400 ({response.message}); con código reenviado: HTTP 200"


@case("TC-067", OP1, "Verificar un email no registrado", "—", "POST /auth/verify-email con un email inexistente.", "HTTP 404.",
      "Negativo", "Baja", REGRESSION, "EmailVerificationServiceTest.verificarUnEmailInexistenteLanzaResourceNotFound")
def tc067():
    response = expect_status(api("POST", "/auth/verify-email", {"email": f"nadie{RUN_ID}@mail.com", "code": "123456"}), 404)
    return f"HTTP 404: {response.message}"


# ---- Opcional: recupero de contraseña

@case("TC-070", OP2, "El pedido de recupero envía un mail con el link", "Usuario registrado.",
      "POST /auth/forgot-password con el email del usuario. Revisar la casilla.",
      "HTTP 200 y llega un mail con un link a la pantalla de nueva contraseña con un token.", "Positivo", "Alta", REGRESSION,
      "PasswordResetServiceTest.alPedirElRecuperoGuardaSoloElHashDelTokenYPublicaElEvento")
def tc070():
    data, _ = verified_user()
    expect_status(api("POST", "/auth/forgot-password", {"email": data["email"]}), 200)
    token = reset_token(data["email"])
    link = re.search(r"http\S+", mails_to(data["email"])[-1][1]).group(0)
    check("/auth/reset-password.html?token=" in link, f"link inesperado: {link}")
    return f"HTTP 200; mail con link {link[:link.index('token=') + 6]}<token de {len(token)} caracteres>"


@case("TC-071", OP2, "El pedido de recupero no revela si el email existe", "—",
      "POST /auth/forgot-password con un email registrado y con uno no registrado. Comparar las respuestas.",
      "Ambas responden HTTP 200 con el mismo mensaje. Al email no registrado no se le envía nada.", "Seguridad", "Alta", REGRESSION,
      "PasswordResetServiceTest.siElEmailNoExisteNoHaceNadaYNoInformaError")
def tc071():
    data, _ = verified_user()
    unknown = f"nadie{RUN_ID}@mail.com"
    existing = expect_status(api("POST", "/auth/forgot-password", {"email": data["email"]}), 200)
    missing = expect_status(api("POST", "/auth/forgot-password", {"email": unknown}), 200)
    check(existing.text == missing.text, "las respuestas son distintas")
    check(not mails_to(unknown), "se envió un mail a un email no registrado")
    return f"misma respuesta en ambos casos (HTTP 200: {existing.message}); sin mail al no registrado"


@case("TC-072", OP2, "La pantalla de nueva contraseña está disponible", "—",
      "Abrir GET /auth/reset-password.html?token=abc (el link del mail).",
      "HTTP 200 con una página HTML que pide la contraseña nueva y repetirla.", "Positivo", "Media", REGRESSION,
      "AuthApiTest.laPantallaParaElegirLaNuevaContraseniaEstaDisponible")
def tc072():
    response = expect_status(api("GET", "/auth/reset-password.html?token=abc"), 200)
    check('id="password"' in response.text and 'id="passwordConfirm"' in response.text, "la página no tiene los dos campos de contraseña")
    return "HTTP 200: página HTML con los campos password y passwordConfirm"


@case("TC-073", OP2, "Cambio de contraseña con el token del mail", "Usuario con pedido de recupero vigente.",
      "POST /auth/reset-password con el token y la contraseña nueva repetida. Login con la clave vieja y con la nueva.",
      "Cambio HTTP 200. Login con la clave vieja: HTTP 400. Login con la nueva: HTTP 200.", "Positivo", "Alta", REGRESSION,
      "PasswordResetServiceTest.conUnTokenValidoCambiaLaClaveYElTokenQuedaInutilizado")
def tc073():
    data, _ = verified_user()
    expect_status(api("POST", "/auth/forgot-password", {"email": data["email"]}), 200)
    body = {"token": reset_token(data["email"]), "password": "ClaveNueva1", "passwordConfirm": "ClaveNueva1"}
    expect_status(api("POST", "/auth/reset-password", body), 200)
    expect_status(api("POST", "/auth/login", {"email": data["email"], "password": PASSWORD}), 400)
    login(data["email"], "ClaveNueva1")
    return "cambio HTTP 200; login clave vieja HTTP 400; login clave nueva HTTP 200"


@case("TC-074", OP2, "Las dos contraseñas deben coincidir", "Usuario con pedido de recupero vigente.",
      "POST /auth/reset-password con password y passwordConfirm distintos.", "HTTP 400. La contraseña no cambia.",
      "Negativo", "Alta", REGRESSION, "AuthApiTest.cambiarLaContraseniaConClavesDistintasDevuelve400SinLlamarAUsersService")
def tc074():
    data, _ = verified_user()
    expect_status(api("POST", "/auth/forgot-password", {"email": data["email"]}), 200)
    body = {"token": reset_token(data["email"]), "password": "ClaveNueva1", "passwordConfirm": "ClaveNueva2"}
    response = expect_status(api("POST", "/auth/reset-password", body), 400)
    login(data["email"])
    return f"HTTP 400: {response.message}; la clave original sigue funcionando"


@case("TC-075", OP2, "El link de recupero es de un solo uso", "Usuario que ya cambió su contraseña con el link.",
      "Volver a enviar POST /auth/reset-password con el mismo token.", "HTTP 400.", "Seguridad", "Alta", REGRESSION,
      "PasswordResetServiceTest.conUnTokenValidoCambiaLaClaveYElTokenQuedaInutilizado")
def tc075():
    data, _ = verified_user()
    expect_status(api("POST", "/auth/forgot-password", {"email": data["email"]}), 200)
    body = {"token": reset_token(data["email"]), "password": "ClaveNueva1", "passwordConfirm": "ClaveNueva1"}
    expect_status(api("POST", "/auth/reset-password", body), 200)
    response = expect_status(api("POST", "/auth/reset-password", body), 400)
    return f"segundo uso del link: HTTP 400 ({response.message})"


@case("TC-076", OP2, "Token de recupero inventado", "—", "POST /auth/reset-password con token = \"token-falso\".", "HTTP 400.",
      "Seguridad", "Alta", REGRESSION, "PasswordResetServiceTest.rechazaUnTokenDesconocido")
def tc076():
    body = {"token": "token-falso", "password": "ClaveNueva1", "passwordConfirm": "ClaveNueva1"}
    response = expect_status(api("POST", "/auth/reset-password", body), 400)
    return f"HTTP 400: {response.message}"


@case("TC-077", OP2, "Cambiar la contraseña cierra las sesiones abiertas", "Usuario logueado que pide recuperar su contraseña.",
      "Login (token A). Pedir recupero y cambiar la contraseña. Usar el token A. Hacer login con la clave nueva (token B) y usarlo.",
      "El token A responde 401. El token B responde 200.", "Seguridad", "Alta", REGRESSION,
      "AuthApiTest.cambiarLaContraseniaCierraLasSesionesAbiertasPeroPermiteVolverAIngresar")
def tc077():
    data, created, token_a = logged_user()
    expect_status(api("POST", "/auth/forgot-password", {"email": data["email"]}), 200)
    body = {"token": reset_token(data["email"]), "password": "ClaveNueva1", "passwordConfirm": "ClaveNueva1"}
    expect_status(api("POST", "/auth/reset-password", body), 200)
    expect_status(api("GET", f"/users/{created['id']}", token=token_a), 401)
    expect_status(api("GET", f"/users/{created['id']}", token=login(data["email"], "ClaveNueva1")), 200)
    return "token anterior al cambio: HTTP 401; token nuevo: HTTP 200"


# ---- Infraestructura

@case("TC-080", INF, "Los microservicios están registrados en Eureka", "Sistema levantado.",
      "Consultar el registro de Eureka (GET :8761/eureka/apps).",
      "Figuran API-GATEWAY, AUTH-SERVICE, USERS-SERVICE y ACCOUNT-SERVICE en estado UP.", "Positivo", "Alta", SMOKE)
def tc080():
    request = urllib.request.Request(ARGS.eureka + "/eureka/apps", headers={"Accept": "application/json"})
    with urllib.request.urlopen(request, timeout=10) as response:
        apps = json.loads(response.read().decode("utf-8"))["applications"]["application"]
    status = {app["name"]: app["instance"][0]["status"] for app in apps}
    for name in ("API-GATEWAY", "AUTH-SERVICE", "USERS-SERVICE", "ACCOUNT-SERVICE"):
        check(status.get(name) == "UP", f"{name} no está UP en Eureka: {status}")
    return "; ".join(f"{name}: {state}" for name, state in sorted(status.items()))


@case("TC-081", INF, "Los endpoints internos no son accesibles desde el gateway", "—",
      "GET /internal/users/credentials?email=... y POST /internal/accounts a través del gateway.", "HTTP 404 en ambos.",
      "Seguridad", "Alta", REGRESSION)
def tc081():
    expect_status(api("GET", "/internal/users/credentials?email=a@mail.com"), 404)
    expect_status(api("POST", "/internal/accounts", {"userId": 1}), 404)
    return "HTTP 404 en ambos"


@case("TC-082", INF, "Los endpoints internos exigen la clave interna", "Acceso directo al puerto de users-service (sin gateway).",
      "GET :8082/internal/users/credentials?email=... sin el header X-Internal-Api-Key y con una clave incorrecta.",
      "HTTP 401 en ambos.", "Seguridad", "Alta", REGRESSION, "UserApiTest.losEndpointsInternosExigenLaClaveInterna")
def tc082():
    path = "/internal/users/credentials?email=a@mail.com"
    expect_status(api("GET", path, base=ARGS.users_direct), 401)
    expect_status(api("GET", path, base=ARGS.users_direct, headers={"X-Internal-Api-Key": "clave-incorrecta"}), 401)
    return "sin clave: HTTP 401; clave incorrecta: HTTP 401"


@case("TC-083", INF, "Swagger UI unificado en el gateway", "Sistema levantado.",
      "Abrir /swagger-ui.html en el gateway y pedir /api-docs/auth, /api-docs/users y /api-docs/accounts.",
      "Swagger UI responde 200 y los 3 documentos OpenAPI listan sus endpoints públicos (no los /internal).",
      "Positivo", "Media", REGRESSION)
def tc083():
    expect_status(api("GET", "/swagger-ui.html"), 200)
    summary = []
    for name in ("auth", "users", "accounts"):
        paths = expect_status(api("GET", f"/api-docs/{name}"), 200).json["paths"]
        check(not any(path.startswith("/internal") for path in paths), f"{name} publica endpoints internos: {list(paths)}")
        summary.append(f"{name}: {len(paths)} endpoints")
    return "Swagger UI HTTP 200; " + ", ".join(summary)


@case("TC-084", INF, "Ruta inexistente", "—", "GET /no-existe en el gateway.", "HTTP 404 con el formato de error estándar.",
      "Negativo", "Baja", REGRESSION, "GatewayErrorHandlerTest.unaRutaInexistenteDevuelve404ConElFormatoComun")
def tc084():
    response = expect_status(api("GET", "/no-existe"), 404)
    check(response.message, f"el error no tiene el formato estándar: {response}")
    return f"HTTP 404: {response.message}"


@case("TC-085", INF, "CORS: solo se aceptan los orígenes configurados", "—",
      "Enviar el preflight OPTIONS /auth/login con Origin http://localhost:3000 y con Origin https://sitio-malicioso.com.",
      "El primero responde 200 con Access-Control-Allow-Origin; el segundo responde 403.", "Seguridad", "Media", REGRESSION)
def tc085():
    def preflight(origin):
        return api("OPTIONS", "/auth/login", headers={"Origin": origin, "Access-Control-Request-Method": "POST"})
    expect_status(preflight("http://localhost:3000"), 200)
    expect_status(preflight("https://sitio-malicioso.com"), 403)
    return "localhost:3000: HTTP 200; sitio-malicioso.com: HTTP 403"


@case("TC-086", INF, "El entorno completo levanta con Docker Compose", "Docker Desktop funcionando.",
      "docker compose up -d --build y esperar a que los contenedores estén sanos. Ejecutar la suite de humo.",
      "Los 7 contenedores quedan arriba y la suite de humo pasa contra MySQL y MailHog.", "Positivo", "Alta", SMOKE,
      manual=("Bloqueado", "No se pudo ejecutar: en esta máquina Docker Desktop no arranca porque Windows no tiene habilitada la característica \"Plataforma de máquina virtual\" (requiere administrador y reinicio). docker compose config valida el archivo.", "02/10/2026"))
def tc086():
    pass


@case("TC-087", INF, "Los datos persisten en MySQL al reiniciar los servicios", "Entorno levantado con Docker Compose.",
      "Registrar un usuario, reiniciar users-service y account-service, y hacer login.",
      "El usuario y su cuenta siguen existiendo (login HTTP 200).", "Positivo", "Alta", REGRESSION,
      manual=("Bloqueado", "Depende de TC-086 (Docker). La suite se ejecutó con el perfil h2 (base en memoria).", "02/10/2026"))
def tc087():
    pass


# ---------------------------------------------------------------- ejecucion

def main():
    selected = [c for c in CASES if ARGS.suite == "regression" or c["suite"] == SMOKE]
    today = datetime.now().strftime("%d/%m/%Y")
    results = []
    print(f"Suite: {ARGS.suite} - {len(selected)} casos - gateway {ARGS.gateway}\n")
    for c in sorted(selected, key=lambda item: item["id"]):
        if c["manual"]:
            status, actual, date = c["manual"]
        else:
            date = today
            try:
                actual = c["run"]()
                status = "Pasó"
            except AssertionError as failure:
                status, actual = "Falló", str(failure)
            except Exception as error:  # error de conexion, respuesta inesperada, etc.
                status, actual = "Falló", f"{type(error).__name__}: {error}"
        print(f"  [{status:^9}] {c['id']} {c['titulo']}" + ("" if status == "Pasó" else f"\n              -> {actual}"))
        record = {key: value for key, value in c.items() if key not in ("run", "manual")}
        record.update({"estado": status, "obtenido": actual, "fecha": date})
        results.append(record)

    totals = {}
    for record in results:
        totals[record["estado"]] = totals.get(record["estado"], 0) + 1
    print("\nResumen: " + ", ".join(f"{count} {state}" for state, count in sorted(totals.items())))

    os.makedirs(os.path.dirname(ARGS.output), exist_ok=True)
    with open(ARGS.output, "w", encoding="utf-8") as output:
        json.dump({"suite": ARGS.suite, "ejecutado": datetime.now().isoformat(timespec="seconds"),
                   "entorno": ARGS.gateway, "resumen": totals, "casos": results}, output, ensure_ascii=False, indent=2)
    print(f"Resultados guardados en {os.path.relpath(ARGS.output, ROOT)}")
    return 1 if totals.get("Falló") else 0


if __name__ == "__main__":
    sys.exit(main())
