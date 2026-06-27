"""
firebase_import_scrape.py
=========================
Actualiza scrapeUrl en Firebase usando un Service Account (recomendado)
o con credenciales de usuario (email/password).

OPCION A - Service Account (recomendado):
    1. Ve a Firebase Console → Configuración del proyecto → Cuentas de servicio
    2. Clic en "Generar nueva clave privada" → descarga el JSON
    3. Guárdalo como "service_account.json" en esta misma carpeta
    4. Ejecuta: python firebase_import_scrape.py --service-account

OPCION B - Email/Password (más simple):
    1. Asegúrate de tener un usuario con permiso de escritura en Firebase Auth
    2. Ejecuta: python firebase_import_scrape.py --email TU@EMAIL.COM --password TU_PASSWORD

Requiere: pip install requests
"""

import argparse
import json
import sys
import urllib.request
import urllib.error
import ssl

FIREBASE_URL   = "https://com-streamingtv-channels-2b926-default-rtdb.firebaseio.com"
FIREBASE_API_KEY = "AIzaSyBEfX_7CexOC0_5qo7Y_x9SprUAqpp2yqA"

# ── Canales a actualizar a modo scrape ────────────────────────────────────────
# key → { scrapeUrl, url (vaciar la URL fija porque ya no se usa) }
SCRAPE_CHANNELS = {
    # SPORTV 1 (URL Amazon expirada → scrape desde futemax.boston)
    "-OuQI9MLm414uRVBIBBU": {
        "scrapeUrl": "https://futemax.boston/sportv-ao-vivo-assista-esportes-online-em-hd/",
        "url": ""
    },
    # ESPN MX (servidor izzigo.tv caído → scrape desde futemax.boston)
    "-OvGdVmr2seoMMJtQ6Ns": {
        "scrapeUrl": "https://futemax.boston/espn-ao-vivo-assista-esportes-online-em-hd/",
        "url": ""
    },
    # ESPN 2 MX
    "-OvGdmsaeEPGr0srQAJi": {
        "scrapeUrl": "https://futemax.boston/espn-2-ao-vivo-assista-esportes-em-hd/",
        "url": ""
    },
    # ESPN 3 MX
    "-OvGe2MhaW19igxgqnsf": {
        "scrapeUrl": "https://futemax.boston/espn-3-ao-vivo-assista-esportes-em-hd/",
        "url": ""
    },
    # NICKELODEON (izzigo.tv inestable → scrape)
    "-OuwFwcD4gTlS0X7Uzcr": {
        "scrapeUrl": "https://www.canales-tv-en-vivo.tv/nickelodeon-en-vivo/",
        "url": ""
    },
    # TELEMUNDO (geo-bloqueado 403 → scrape desde su propia web)
    "-OuxjzeDHFEwpUGYVwdh": {
        "scrapeUrl": "https://www.telemundo.com/en-vivo",
        "url": ""
    },
    # UNIVERSO (geo-bloqueado 403 → scrape)
    "-Ouvyejgz1SBVnBgmNDY": {
        "scrapeUrl": "https://www.universo.com/en-vivo",
        "url": ""
    },
    # TUDN MX (geo-bloqueado 403 → scrape)
    "-Ov1yeSvOUSGHahdCScb": {
        "scrapeUrl": "https://www.tudn.com/en-vivo",
        "url": ""
    },
    # A3CINE (geo-bloqueado 403 → scrape)
    "-Ouw2Ek4A9v7XnHNMr8J": {
        "scrapeUrl": "https://www.canales-tv-en-vivo.tv/a3cine-en-vivo/",
        "url": ""
    },
    # A3SERIES (geo-bloqueado 403 → scrape)
    "-Ouw2PRB7jJXhXe6z7GA": {
        "scrapeUrl": "https://www.canales-tv-en-vivo.tv/atreseries-en-vivo/",
        "url": ""
    },
}


def get_ssl_ctx():
    ctx = ssl.create_default_context()
    ctx.check_hostname = False
    ctx.verify_mode = ssl.CERT_NONE
    return ctx


def firebase_patch(path: str, data: dict, token: str) -> bool:
    url = f"{FIREBASE_URL}/{path}.json?auth={token}"
    payload = json.dumps(data).encode()
    req = urllib.request.Request(
        url, data=payload, method="PATCH",
        headers={"Content-Type": "application/json"}
    )
    try:
        with urllib.request.urlopen(req, timeout=15, context=get_ssl_ctx()) as resp:
            return resp.status == 200
    except urllib.error.HTTPError as e:
        print(f"  HTTP {e.code}: {e.read().decode()}")
        return False
    except Exception as e:
        print(f"  Error: {e}")
        return False


def auth_with_email(email: str, password: str) -> str:
    """Obtiene un ID token de Firebase Auth vía REST."""
    url = f"https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key={FIREBASE_API_KEY}"
    payload = json.dumps({
        "email": email,
        "password": password,
        "returnSecureToken": True
    }).encode()
    req = urllib.request.Request(
        url, data=payload, method="POST",
        headers={"Content-Type": "application/json"}
    )
    try:
        with urllib.request.urlopen(req, timeout=15, context=get_ssl_ctx()) as resp:
            data = json.loads(resp.read().decode())
            return data["idToken"]
    except urllib.error.HTTPError as e:
        body = e.read().decode()
        print(f"Error de autenticación: {body}")
        sys.exit(1)


def auth_with_service_account(sa_path: str) -> str:
    """Obtiene un access token usando un Service Account JSON."""
    try:
        import google.oauth2.service_account as sa
        import google.auth.transport.requests as ga_requests
    except ImportError:
        print("Instala las dependencias: pip install google-auth")
        sys.exit(1)

    creds = sa.Credentials.from_service_account_file(
        sa_path,
        scopes=["https://www.googleapis.com/auth/firebase.database",
                "https://www.googleapis.com/auth/userinfo.email"]
    )
    creds.refresh(ga_requests.Request())
    return creds.token


def run(token: str):
    print(f"\n🔥 Actualizando {len(SCRAPE_CHANNELS)} canales en Firebase...\n")
    ok = 0
    for key, data in SCRAPE_CHANNELS.items():
        name = data.get("name", key)
        success = firebase_patch(f"channels/{key}", data, token)
        if success:
            print(f"  ✅ {key}  scrapeUrl={data['scrapeUrl'][:60]}...")
            ok += 1
        else:
            print(f"  ❌ {key}  ERROR")

    print(f"\n{'✅' if ok == len(SCRAPE_CHANNELS) else '⚠️'} {ok}/{len(SCRAPE_CHANNELS)} canales actualizados.")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--service-account", metavar="PATH",
                        nargs="?", const="service_account.json",
                        help="Ruta al JSON del Service Account (default: service_account.json)")
    parser.add_argument("--email",    help="Email del usuario Firebase")
    parser.add_argument("--password", help="Password del usuario Firebase")
    args = parser.parse_args()

    if args.service_account:
        print(f"Usando Service Account: {args.service_account}")
        token = auth_with_service_account(args.service_account)
    elif args.email and args.password:
        print(f"Autenticando como {args.email}...")
        token = auth_with_email(args.email, args.password)
    else:
        print("Uso:")
        print("  python firebase_import_scrape.py --service-account [ruta.json]")
        print("  python firebase_import_scrape.py --email TU@EMAIL.COM --password PASSWORD")
        sys.exit(1)

    run(token)
