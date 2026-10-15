"""Verifica que los datos sobrevivan a un reinicio de los servicios (caso TC-087).

Uso (con el entorno de Docker levantado):
    python scripts/check_persistence.py prepare   # crea un usuario verificado con saldo y guarda sus datos
    docker compose restart users-service account-service auth-service
    python scripts/check_persistence.py verify    # vuelve a entrar y comprueba usuario, cuenta y saldo

Solo usa la libreria estandar de Python 3.
"""
import http.client
import json
import quopri
import re
import sys
import time
import urllib.error
import urllib.parse
import urllib.request

GATEWAY = "http://localhost:8090"
MAILHOG = "http://localhost:8025"
STATE = "persistence-check.json"
PASSWORD = "Clave1234"


def call(method, path, body=None, token=None):
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = "Bearer " + token
    data = json.dumps(body).encode() if body is not None else None
    request = urllib.request.Request(GATEWAY + path, data=data, method=method, headers=headers)
    try:
        with urllib.request.urlopen(request, timeout=20) as response:
            return response.status, json.loads(response.read() or b"null")
    except urllib.error.HTTPError as error:
        return error.code, error.read().decode()
    except (urllib.error.URLError, http.client.HTTPException, OSError) as error:
        # conexion rechazada o cortada: el servicio esta reiniciando
        return 0, f"{type(error).__name__}: {error}"


def call_until_ready(method, path, body=None, token=None, seconds=180):
    """Repite el request mientras el servicio este reiniciando (sin conexion o 5xx del gateway)."""
    deadline = time.time() + seconds
    while True:
        status, result = call(method, path, body, token)
        if (status != 0 and status < 500) or time.time() > deadline:
            return status, result
        print(f"  {method} {path}: {status}, reintentando...", flush=True)
        time.sleep(3)


def verification_code(email):
    for _ in range(30):
        query = urllib.parse.urlencode({"kind": "to", "query": email})
        with urllib.request.urlopen(f"{MAILHOG}/api/v2/search?{query}", timeout=10) as response:
            items = json.loads(response.read())["items"]
        if items:
            body = quopri.decodestring(items[0]["Content"]["Body"].encode()).decode("utf-8", "replace")
            return re.search(r"\b(\d{6})\b", body).group(1)
        time.sleep(1)
    raise SystemExit(f"No llegó el mail de verificación a {email}")


def expect(condition, message):
    if not condition:
        print(f"::error title=TC-087 persistencia::{message}", flush=True)
        raise SystemExit("FALLÓ: " + message)


def prepare():
    stamp = int(time.time())
    email = f"persistencia.{stamp}@mail.com"
    status, user = call("POST", "/users/register", {"firstName": "Persis", "lastName": "Tencia", "dni": f"{stamp % 100000000:08d}",
                                                   "email": email, "phone": "+5491100000000", "password": PASSWORD})
    expect(status == 201, f"registro: {status} {user}")
    expect(call("POST", "/auth/verify-email", {"email": email, "code": verification_code(email)})[0] == 200, "verificación")
    _, login = call("POST", "/auth/login", {"email": email, "password": PASSWORD})
    token = login["token"]
    card = {"number": "4111111111111111", "holderName": "Persis Tencia", "expiration": "12/39", "cvv": "123", "type": "DEBIT"}
    status, created_card = call("POST", f"/accounts/{user['accountId']}/cards", card, token=token)
    expect(status in (201, 409), f"tarjeta: {status} {created_card}")
    if status == 409:  # la tarjeta de prueba ya existe de una corrida anterior: se usa otra
        card["number"] = "5555555555554444"
        status, created_card = call("POST", f"/accounts/{user['accountId']}/cards", card, token=token)
        expect(status == 201, f"tarjeta: {status} {created_card}")
    expect(call("POST", f"/accounts/{user['accountId']}/transferences", {"cardId": created_card["id"], "amount": 1234.5},
                token=token)[0] == 201, "ingreso de dinero")
    with open(STATE, "w", encoding="utf-8") as f:
        json.dump({"email": email, "userId": user["id"], "accountId": user["accountId"], "cvu": user["cvu"]}, f)
    print(f"Preparado: usuario {user['id']}, cuenta {user['accountId']}, saldo 1234.50")


def verify():
    with open(STATE, encoding="utf-8") as f:
        state = json.load(f)
    # despues del reinicio cada servicio tarda en arrancar y en volver a registrarse en Eureka:
    # mientras tanto el gateway responde 503 o corta la conexion, por eso cada paso reintenta
    status, login = call_until_ready("POST", "/auth/login", {"email": state["email"], "password": PASSWORD})
    expect(status == 200, f"login después del reinicio: {status} {login}")
    print("  login OK", flush=True)
    status, account = call_until_ready("GET", f"/accounts/{state['accountId']}", token=login["token"])
    expect(status == 200, f"cuenta: {status} {account}")
    expect(account["cvu"] == state["cvu"], "el CVU cambió")
    expect(float(account["balance"]) == 1234.5, f"saldo después del reinicio: {account['balance']}")
    print(f"  cuenta OK: CVU {account['cvu']}, saldo {account['balance']}", flush=True)
    status, activity = call_until_ready("GET", f"/accounts/{state['accountId']}/activity", token=login["token"])
    expect(status == 200 and len(activity) == 1, f"actividad: {status} {activity}")
    status, user = call_until_ready("GET", f"/users/{state['userId']}", token=login["token"])
    expect(status == 200 and user["email"] == state["email"], f"usuario: {status} {user}")
    print("OK: después del reinicio el usuario puede entrar y conserva su cuenta, saldo y movimientos.")


if __name__ == "__main__":
    {"prepare": prepare, "verify": verify}[sys.argv[1]]()
