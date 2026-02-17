#!/usr/bin/env python3

import sys
import struct
import time
import os
import tempfile
import subprocess
import logging
import asyncio

logging.disable(logging.CRITICAL)

try:
    from pymobiledevice3.lockdown import create_using_usbmux
    from pymobiledevice3.services.screenshot import ScreenshotService
    USE_PYMOBILEDEVICE3 = True
except ImportError:
    USE_PYMOBILEDEVICE3 = False


def write_frame(png_data: bytes) -> None:
    sys.stdout.buffer.write(struct.pack(">I", len(png_data)))
    sys.stdout.buffer.write(png_data)
    sys.stdout.buffer.flush()


def convert_tiff_to_png(tiff_data: bytes) -> bytes | None:
    try:
        from PIL import Image
        import io
        img = Image.open(io.BytesIO(tiff_data))
        output = io.BytesIO()
        img.save(output, format="PNG")
        return output.getvalue()
    except ImportError:
        pass

    tiff_path = None
    png_path = None
    try:
        with tempfile.NamedTemporaryFile(suffix=".tiff", delete=False) as tiff_file:
            tiff_file.write(tiff_data)
            tiff_path = tiff_file.name

        png_path = tiff_path.replace(".tiff", ".png")
        result = subprocess.run(
            ["convert", tiff_path, png_path],
            capture_output=True,
            timeout=10,
        )
        if result.returncode == 0 and os.path.exists(png_path):
            with open(png_path, "rb") as f:
                return f.read()
    except Exception:
        pass
    finally:
        for p in [tiff_path, png_path]:
            if p:
                try:
                    os.unlink(p)
                except Exception:
                    pass

    return None


def screenshot_loop(screenshot_service) -> None:
    while True:
        try:
            png_data = screenshot_service.take_screenshot()
            write_frame(png_data)
            time.sleep(0.05)
        except (ConnectionError, BrokenPipeError, OSError) as e:
            print(f"MIRROR_ERROR: Dispositivo desconectado: {e}", file=sys.stderr, flush=True)
            sys.exit(1)
        except KeyboardInterrupt:
            sys.exit(0)
        except Exception as e:
            print(f"MIRROR_ERROR: {e}", file=sys.stderr, flush=True)
            time.sleep(1)


def dvt_screenshot_loop(dvt) -> None:
    from pymobiledevice3.services.dvt.instruments.screenshot import Screenshot
    ss = Screenshot(dvt)
    print("INFO: DVT Screenshot conectado", file=sys.stderr, flush=True)
    while True:
        try:
            png_data = ss.get_screenshot()
            write_frame(png_data)
            time.sleep(0.05)
        except (ConnectionError, BrokenPipeError, OSError) as e:
            print(f"MIRROR_ERROR: Dispositivo desconectado: {e}", file=sys.stderr, flush=True)
            sys.exit(1)
        except KeyboardInterrupt:
            sys.exit(0)
        except Exception as e:
            print(f"MIRROR_ERROR: {e}", file=sys.stderr, flush=True)
            time.sleep(1)


def try_screenshot_service(lockdown) -> bool:
    try:
        screenshot_service = ScreenshotService(lockdown=lockdown)
        print("INFO: ScreenshotService conectado", file=sys.stderr, flush=True)
        screenshot_loop(screenshot_service)
        return True
    except Exception:
        return False


def try_auto_mount(udid: str) -> bool:
    venv_bin = os.path.dirname(sys.executable)
    pmd3 = os.path.join(venv_bin, "pymobiledevice3")

    if not os.path.isfile(pmd3):
        return False

    print("INFO: Tentando auto-mount da imagem de desenvolvedor...", file=sys.stderr, flush=True)
    try:
        result = subprocess.run(
            [pmd3, "mounter", "auto-mount", "--udid", udid],
            capture_output=True,
            timeout=30,
        )
        if result.returncode == 0:
            print("INFO: Auto-mount concluído", file=sys.stderr, flush=True)
            return True
        print(f"INFO: Auto-mount retornou código {result.returncode}", file=sys.stderr, flush=True)
        return False
    except Exception as e:
        print(f"INFO: Auto-mount falhou: {e}", file=sys.stderr, flush=True)
        return False


def get_tunnel_info(udid: str):
    try:
        import urllib.request
        import json
        req = urllib.request.urlopen("http://127.0.0.1:49151/", timeout=5)
        data = json.loads(req.read())
    except Exception:
        return None

    if not data or not isinstance(data, dict):
        return None

    for tunnel_udid, tunnels in data.items():
        if tunnel_udid == udid or udid in tunnel_udid or tunnel_udid.replace("-", "") == udid:
            if isinstance(tunnels, list) and tunnels:
                return tunnels[0]

    all_tunnels = [t for tunnels in data.values() if isinstance(tunnels, list) for t in tunnels]
    if all_tunnels:
        return all_tunnels[0]

    return None


def try_tunneld_screenshot(udid: str) -> bool:
    try:
        from pymobiledevice3.remote.remote_service_discovery import RemoteServiceDiscoveryService
    except ImportError:
        return False

    target = get_tunnel_info(udid)
    if target is None:
        return False

    host = target.get("tunnel-address", target.get("address", ""))
    port = target.get("tunnel-port", target.get("port", 0))

    if not host or not port:
        return False

    print(f"INFO: Conectando via tunneld {host}:{port}", file=sys.stderr, flush=True)

    try:
        rsd = RemoteServiceDiscoveryService((host, port))
        asyncio.run(rsd.connect())
        print(f"INFO: RSD conectado", file=sys.stderr, flush=True)
    except Exception as e:
        print(f"INFO: Falha ao conectar RSD: {e}", file=sys.stderr, flush=True)
        return False

    try:
        ss = ScreenshotService(rsd)
        print("INFO: ScreenshotService via tunnel conectado", file=sys.stderr, flush=True)
        screenshot_loop(ss)
        return True
    except Exception as e:
        print(f"INFO: ScreenshotService indisponível ({e}), usando DVT...", file=sys.stderr, flush=True)

    try:
        from pymobiledevice3.services.dvt.dvt_secure_socket_proxy import DvtSecureSocketProxyService
        dvt = DvtSecureSocketProxyService(rsd)
        dvt.__enter__()
        dvt_screenshot_loop(dvt)
        return True
    except Exception as e:
        print(f"INFO: Falha no DVT screenshot via tunnel: {e}", file=sys.stderr, flush=True)
        return False


def stream_idevicescreenshot(udid: str) -> None:
    print("INFO: Tentando idevicescreenshot como fallback...", file=sys.stderr, flush=True)

    tmp_fd, tmp_path = tempfile.mkstemp(suffix=".png")
    os.close(tmp_fd)

    try:
        result = subprocess.run(
            ["idevicescreenshot", "-u", udid, tmp_path],
            capture_output=True,
            timeout=10,
        )
        if result.returncode != 0:
            stderr_text = result.stderr.decode("utf-8", errors="replace")
            if "Developer" in stderr_text or "screenshotr" in stderr_text:
                print("MIRROR_ERROR: TUNNEL_REQUIRED", file=sys.stderr, flush=True)
                sys.exit(1)
    except Exception:
        pass
    finally:
        try:
            os.unlink(tmp_path)
        except Exception:
            pass

    print("MIRROR_ERROR: TUNNEL_REQUIRED", file=sys.stderr, flush=True)
    sys.exit(1)


def stream_pymobiledevice3(udid: str) -> None:
    print("INFO: Usando pymobiledevice3", file=sys.stderr, flush=True)

    try:
        lockdown = create_using_usbmux(serial=udid)
    except Exception as e:
        print(f"MIRROR_ERROR: Falha ao conectar: {e}", file=sys.stderr, flush=True)
        sys.exit(1)

    ios_version = 0
    try:
        ios_version = int(lockdown.product_version.split(".")[0])
    except Exception:
        pass

    if try_screenshot_service(lockdown):
        return

    print("INFO: ScreenshotService direto falhou, tentando alternativas...", file=sys.stderr, flush=True)

    if ios_version >= 17:
        if try_tunneld_screenshot(udid):
            return

        try_auto_mount(udid)

        try:
            lockdown2 = create_using_usbmux(serial=udid)
            if try_screenshot_service(lockdown2):
                return
        except Exception:
            pass

    stream_idevicescreenshot(udid)


def main() -> None:
    if len(sys.argv) < 2:
        print("Uso: mirror_stream.py <UDID>", file=sys.stderr, flush=True)
        sys.exit(1)

    udid = sys.argv[1]
    print(f"INFO: Iniciando stream para dispositivo {udid}", file=sys.stderr, flush=True)

    if USE_PYMOBILEDEVICE3:
        stream_pymobiledevice3(udid)
    else:
        stream_idevicescreenshot(udid)


if __name__ == "__main__":
    main()
