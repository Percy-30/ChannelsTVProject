"""
update_channels.py
==================
Script para verificar y actualizar automáticamente los canales de ChannelsTV
en Firebase Realtime Database.

Uso:
    python update_channels.py              # verificar + actualizar automáticamente
    python update_channels.py --check-only # solo verificar, sin escribir en Firebase
    python update_channels.py --dry-run    # mostrar qué cambiaría, sin aplicar

Requiere:
    pip install requests firebase-admin
"""

import argparse
import json
import sys
import time
import urllib.request
import urllib.error
import ssl
from datetime import datetime

# ─── Configuración Firebase ──────────────────────────────────────────────────

FIREBASE_URL = "https://com-streamingtv-channels-2b926-default-rtdb.firebaseio.com"
FIREBASE_SECRET = None  # Opcional: si tienes legacy secret para auth sin service account
                        # Puedes obtenerlo en Firebase Console > Configuración del proyecto >
                        # Cuentas de servicio > Secretos de base de datos

# ─── Headers HTTP (simula el dispositivo Android de la app) ──────────────────

HEADERS = {
    "User-Agent": (
        "Mozilla/5.0 (Linux; Android 14; Pixel 8) "
        "AppleWebKit/537.36 (KHTML, like Gecko) "
        "Chrome/124.0.0.0 Mobile Safari/537.36"
    ),
    "Accept": "*/*",
    "Accept-Language": "es-ES,es;q=0.9,en;q=0.8",
    "Origin": "https://www.canales-tv-en-vivo.tv",
    "Referer": "https://www.canales-tv-en-vivo.tv/",
}

TIMEOUT = 10  # segundos por request

# ─── Fuentes alternativas conocidas por canal ─────────────────────────────────
# Cuando una URL falla, el script prueba estas alternativas en orden.
# Formato: { "FIREBASE_KEY": ["url_alternativa_1", "url_alternativa_2", ...] }
#
# Cómo agregar alternativas:
#   1. Copia la Firebase key del canal (la ves en el reporte de --check-only).
#   2. Agrega la lista de URLs alternativas a probar.
#   3. Vuelve a correr el script sin --check-only para que se apliquen.
#
# Cómo conseguir nuevas URLs:
#   - Canales Amazon (cenc.mpd): las URLs rotan cada cierto tiempo.
#     Buscarlas inspeccionando el tráfico de red en sitios como:
#     futemax.cfd, bestleague.world, rojadirecta.me, etc.
#   - Canales izzigo.tv: probar distintos subdominios (vod-sev-orbit1, orbit2, live-sev-*)
#   - Canales geo-bloqueados (403): necesitan URL de otro proveedor sin restricción geo.
#   - Canales VPN Chile: solo funcionan con IP chilena, ignorados por este script.

ALTERNATIVE_URLS: dict[str, list[str]] = {

    # ── SPACE (izzigo.tv a veces inestable) ─────────────────────────────────
    "-OuQFSsP6_6IyRKKHQNA": [
        "https://zap-live2-ott.izzigo.tv/11/out/u/dash/SPACE-HD/default.mpd",
        "https://paz-live2-ott.izzigo.tv/11/out/u/dash/SPACE-HD/default.mpd",
        "https://aca-live-ott.izzigo.tv/11/out/u/dash/SPACE-HD/default.mpd",
    ],

    # ── NICKELODEON (izzigo.tv a veces inestable) ────────────────────────────
    "-OuwFwcD4gTlS0X7Uzcr": [
        "https://zap-live2-ott.izzigo.tv/12/out/u/dash/NICK-HD/default.mpd",
        "https://paz-live1-ott.izzigo.tv/12/out/u/dash/NICK-HD/default.mpd",
        "https://aca-live-ott.izzigo.tv/12/out/u/dash/NICK-HD/default.mpd",
    ],

    # ── SPORTV 1 (Amazon CDN, 403 desde fuera de Brasil) ────────────────────
    # La URL original expiró / bloqueada geográficamente.
    # Patrón: https://otte*.live.pv-cdn.net/gru-nitro/live/clients/dash/enc/XXXXX/.../cenc.mpd
    # Añade aquí una URL nueva cuando la consigas.
    "-OuQI9MLm414uRVBIBBU": [
        # Ejemplo (reemplazar con URL válida cuando esté disponible):
        # "https://otte-qw.live.pv-cdn.net/gru-nitro/live/clients/dash/enc/NUEVA_CLAVE/out/v1/NUEVO_ID/cenc.mpd",
    ],

    # ── ESPN MX (servidor izzigo.tv sin respuesta) ───────────────────────────
    "-OvGdVmr2seoMMJtQ6Ns": [
        "https://vod-sev-orbit1-s2.izzigo.tv/out/u/startover/dash/ESPN-HD/default.mpd",
        "https://live-sev-orbit2-s2.izzigo.tv/out/u/dash/ESPN-HD/default.mpd",
    ],
    "-OvGdmsaeEPGr0srQAJi": [
        "https://vod-sev-orbit1-s2.izzigo.tv/out/u/startover/dash/ESPN-2-HD/default.mpd",
        "https://live-sev-orbit2-s2.izzigo.tv/out/u/dash/ESPN-2-HD/default.mpd",
    ],
    "-OvGe2MhaW19igxgqnsf": [
        "https://vod-sev-orbit1-s2.izzigo.tv/out/u/startover/dash/ESPN-3-HD/default.mpd",
        "https://live-sev-orbit2-s2.izzigo.tv/out/u/dash/ESPN-3-HD/default.mpd",
    ],

    # ── Canales geo-bloqueados (403) ─────────────────────────────────────────
    # Estos bloquean IPs fuera de su región de origen.
    # Se necesita URL de un CDN diferente sin restricción geográfica.
    # Agregar URLs alternativas cuando estén disponibles.

    "-Ov1yeSvOUSGHahdCScb": [],   # TUDN MX        (geo-bloqueado México/EE.UU.)
    "-Ouvyejgz1SBVnBgmNDY": [],   # UNIVERSO       (geo-bloqueado EE.UU.)
    "-OuxjzeDHFEwpUGYVwdh": [],   # TELEMUNDO      (geo-bloqueado EE.UU.)
    "-Ouw2Ek4A9v7XnHNMr8J": [],   # A3CINE         (geo-bloqueado España/región)
    "-Ouw2PRB7jJXhXe6z7GA": [],   # A3SERIES       (geo-bloqueado España/región)

    # ── Canales VPN Chile (requieren IP chilena, ignorados en verificación) ──
    "-OuQ6PTPvjpxMhnzpc4U": [],   # ESPN CL
    "-OuQBNfEl7Zyy3dq7NBC": [],   # ESPN 2 CL
    "-OuQBYI4MN5BJAjsIPzL": [],   # ESPN 3 CL
    "-OuQBi3g9IXv_VbkOQGq": [],   # ESPN 4 CL
    "-OuQBqKYlwi6AQnD2AYW": [],   # ESPN 5 CL
    "-OuQCNoH0fyq2CDYruti": [],   # ESPN 6 CL
    "-Ouw0aE1tIU1q4X4mg_7": [],   # CHILEVISION
    "-Ouw0uWdFCYhKpqPsmvT": [],   # TVN
    "-OuxrlwIRT1Hm1Nnwvr3": [],   # TNT SPORTS
}

# Claves que son VPN-only (se reportan como "VPN requerida", no como error real)
VPN_ONLY_KEYS = {
    "-OuQ6PTPvjpxMhnzpc4U",  # ESPN CL
    "-OuQBNfEl7Zyy3dq7NBC",  # ESPN 2 CL
    "-OuQBYI4MN5BJAjsIPzL",  # ESPN 3 CL
    "-OuQBi3g9IXv_VbkOQGq",  # ESPN 4 CL
    "-OuQBqKYlwi6AQnD2AYW",  # ESPN 5 CL
    "-OuQCNoH0fyq2CDYruti",  # ESPN 6 CL
    "-Ouw0aE1tIU1q4X4mg_7",  # CHILEVISION
    "-Ouw0uWdFCYhKpqPsmvT",  # TVN
    "-OuxrlwIRT1Hm1Nnwvr3",  # TNT SPORTS
}


# ─── Helpers HTTP ─────────────────────────────────────────────────────────────

def check_url(url: str, timeout: int = TIMEOUT) -> tuple[bool, int | str]:
    """
    Verifica si una URL responde con 2xx/3xx.
    Retorna (ok: bool, status_code: int | "ERR").
    """
    ctx = ssl.create_default_context()
    ctx.check_hostname = False
    ctx.verify_mode = ssl.CERT_NONE

    req = urllib.request.Request(url, headers=HEADERS, method="HEAD")
    try:
        with urllib.request.urlopen(req, timeout=timeout, context=ctx) as resp:
            code = resp.status
            return (200 <= code < 400), code
    except urllib.error.HTTPError as e:
        return False, e.code
    except Exception:
        return False, "ERR"


def firebase_get(path: str) -> dict:
    """Lee un nodo de Firebase vía REST."""
    auth_suffix = f"?auth={FIREBASE_SECRET}" if FIREBASE_SECRET else ".json"
    url = f"{FIREBASE_URL}/{path}{'' if FIREBASE_SECRET else ''}.json"
    if FIREBASE_SECRET:
        url += f"?auth={FIREBASE_SECRET}"

    req = urllib.request.Request(url)
    ctx = ssl.create_default_context()
    try:
        with urllib.request.urlopen(req, timeout=15, context=ctx) as resp:
            return json.loads(resp.read().decode())
    except Exception as e:
        print(f"  [ERROR] No se pudo leer Firebase ({path}): {e}")
        sys.exit(1)


def firebase_patch(path: str, data: dict) -> bool:
    """Actualiza campos de un nodo Firebase vía PATCH REST."""
    url = f"{FIREBASE_URL}/{path}.json"
    if FIREBASE_SECRET:
        url += f"?auth={FIREBASE_SECRET}"

    payload = json.dumps(data).encode()
    req = urllib.request.Request(
        url, data=payload, method="PATCH",
        headers={"Content-Type": "application/json"}
    )
    ctx = ssl.create_default_context()
    try:
        with urllib.request.urlopen(req, timeout=15, context=ctx) as resp:
            return resp.status == 200
    except Exception as e:
        print(f"  [ERROR] No se pudo actualizar Firebase ({path}): {e}")
        return False


# ─── Lógica principal ─────────────────────────────────────────────────────────

def run(check_only: bool = False, dry_run: bool = False):
    print("=" * 60)
    print(f"  ChannelsTV - Verificador de canales")
    print(f"  {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
    print("=" * 60)

    # 1. Leer todos los canales desde Firebase
    print("\n📡 Leyendo canales desde Firebase...")
    channels_data = firebase_get("channels")
    total = len(channels_data)
    print(f"   {total} canales encontrados.\n")

    results = {
        "ok": [],
        "vpn_only": [],
        "fixed": [],
        "failed": [],
        "no_alternatives": [],
    }

    updates_to_apply: dict[str, dict] = {}  # { firebase_key: {"url": nueva_url} }

    # 2. Verificar cada canal
    for idx, (key, channel) in enumerate(channels_data.items(), 1):
        name = channel.get("name", key)
        url  = channel.get("url", "")
        scrape_url = channel.get("scrapeUrl", "")

        # Canales con modo scrape no tienen URL estática que verificar
        if scrape_url and not url:
            print(f"  [{idx:02d}/{total}] ⏭  {name} [SCRAPE MODE - skip]")
            results["ok"].append({"key": key, "name": name, "status": "scrape"})
            continue

        # Canales VPN-only
        if key in VPN_ONLY_KEYS:
            print(f"  [{idx:02d}/{total}] 🔒 {name} [VPN Chile requerida]")
            results["vpn_only"].append({"key": key, "name": name})
            continue

        # Verificar URL actual
        ok, status = check_url(url)
        time.sleep(0.1)  # pequeña pausa para no saturar servidores

        if ok:
            print(f"  [{idx:02d}/{total}] ✅  {name} [{status}]")
            results["ok"].append({"key": key, "name": name, "status": status})
            continue

        # URL caída — intentar alternativas
        print(f"  [{idx:02d}/{total}] ❌  {name} [{status}]", end="")

        alternatives = ALTERNATIVE_URLS.get(key, [])
        found_alternative = None

        for alt_url in alternatives:
            alt_ok, alt_status = check_url(alt_url)
            time.sleep(0.1)
            if alt_ok:
                found_alternative = alt_url
                print(f" → ✅ alternativa encontrada [{alt_status}]")
                break

        if found_alternative:
            results["fixed"].append({
                "key": key,
                "name": name,
                "old_url": url,
                "new_url": found_alternative,
                "status": status,
            })
            updates_to_apply[key] = {"url": found_alternative}
        elif alternatives:
            print(f" → ❌ todas las alternativas fallaron")
            results["failed"].append({"key": key, "name": name, "status": status})
        else:
            print(f" → ⚠️  sin alternativas configuradas")
            results["no_alternatives"].append({"key": key, "name": name, "status": status})

    # 3. Mostrar resumen
    print("\n" + "=" * 60)
    print("  RESUMEN")
    print("=" * 60)
    print(f"  ✅ Funcionando:           {len(results['ok'])}")
    print(f"  🔒 VPN requerida:         {len(results['vpn_only'])}")
    print(f"  🔧 Reparados (con alt.):  {len(results['fixed'])}")
    print(f"  ❌ Caídos (alt. fallida): {len(results['failed'])}")
    print(f"  ⚠️  Caídos (sin alt.):    {len(results['no_alternatives'])}")

    if results["vpn_only"]:
        print("\n🔒 Canales VPN Chile (funcionan solo con IP chilena):")
        for ch in results["vpn_only"]:
            print(f"   - {ch['name']}")

    if results["failed"] or results["no_alternatives"]:
        print("\n❌ Canales que necesitan URLs nuevas manualmente:")
        for ch in results["failed"] + results["no_alternatives"]:
            print(f"   - {ch['name']} [HTTP {ch['status']}]  key={ch['key']}")
        print("\n   👉 Agrega URLs alternativas en ALTERNATIVE_URLS dentro de este script.")

    if results["fixed"]:
        print("\n🔧 URLs que se actualizarán:")
        for ch in results["fixed"]:
            print(f"   - {ch['name']}")
            print(f"     OLD: {ch['old_url']}")
            print(f"     NEW: {ch['new_url']}")

    # 4. Aplicar actualizaciones a Firebase
    if check_only:
        print("\n[check-only] No se escribió nada en Firebase.")
        return

    if not updates_to_apply:
        print("\n✅ No hay nada que actualizar en Firebase.")
        return

    if dry_run:
        print(f"\n[dry-run] Se aplicarían {len(updates_to_apply)} actualización(es). "
              "Usa sin --dry-run para aplicar.")
        return

    print(f"\n🚀 Aplicando {len(updates_to_apply)} actualización(es) en Firebase...")
    applied = 0
    for key, patch_data in updates_to_apply.items():
        name = channels_data[key].get("name", key)
        success = firebase_patch(f"channels/{key}", patch_data)
        if success:
            print(f"   ✅ {name} actualizado")
            applied += 1
        else:
            print(f"   ❌ {name} ERROR al actualizar")

    print(f"\n✅ {applied}/{len(updates_to_apply)} canales actualizados en Firebase.")

    # 5. Guardar reporte JSON
    report_path = "channel_report.json"
    report = {
        "timestamp": datetime.now().isoformat(),
        "total": total,
        "ok": len(results["ok"]),
        "vpn_only": len(results["vpn_only"]),
        "fixed": len(results["fixed"]),
        "failed": len(results["failed"]),
        "no_alternatives": len(results["no_alternatives"]),
        "details": results,
    }
    with open(report_path, "w", encoding="utf-8") as f:
        json.dump(report, f, ensure_ascii=False, indent=2)
    print(f"📄 Reporte guardado en: {report_path}")


# ─── Entry point ─────────────────────────────────────────────────────────────

if __name__ == "__main__":
    parser = argparse.ArgumentParser(
        description="Verifica y actualiza URLs de canales en Firebase."
    )
    parser.add_argument(
        "--check-only", action="store_true",
        help="Solo verifica, no modifica Firebase."
    )
    parser.add_argument(
        "--dry-run", action="store_true",
        help="Muestra qué cambiaría sin aplicarlo."
    )
    args = parser.parse_args()
    run(check_only=args.check_only, dry_run=args.dry_run)
