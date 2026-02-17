#!/usr/bin/env python3

import sys
import struct
import time
import os
import tempfile
import subprocess
import logging
import asyncio
import threading
import queue as _queue

logging.disable(logging.CRITICAL)

try:
    from pymobiledevice3.lockdown import create_using_usbmux
    from pymobiledevice3.services.screenshot import ScreenshotService
    USE_PYMOBILEDEVICE3 = True
except ImportError:
    USE_PYMOBILEDEVICE3 = False

try:
    from PIL import Image as PILImage
    import io as _io
    HAS_PILLOW = True
except ImportError:
    HAS_PILLOW = False

JPEG_QUALITY = 65
MAX_LONG_SIDE = 1280


def optimize_frame(raw_data: bytes) -> tuple[bytes, int, int]:
    if not HAS_PILLOW:
        w = h = 0
        if len(raw_data) >= 24 and raw_data[:4] == b'\x89PNG':
            w = struct.unpack(">I", raw_data[16:20])[0]
            h = struct.unpack(">I", raw_data[20:24])[0]
        return raw_data, w, h

    img = PILImage.open(_io.BytesIO(raw_data))
    orig_w, orig_h = img.size

    long_side = max(orig_w, orig_h)
    if long_side > MAX_LONG_SIDE:
        scale = MAX_LONG_SIDE / long_side
        img = img.resize((int(orig_w * scale), int(orig_h * scale)), PILImage.BILINEAR)

    if img.mode != 'RGB':
        img = img.convert('RGB')

    buf = _io.BytesIO()
    img.save(buf, format='JPEG', quality=JPEG_QUALITY)
    return buf.getvalue(), orig_w, orig_h


def write_frame(image_data: bytes, orig_width: int, orig_height: int) -> None:
    total = 8 + len(image_data)
    sys.stdout.buffer.write(struct.pack(">I", total))
    sys.stdout.buffer.write(struct.pack(">II", orig_width, orig_height))
    sys.stdout.buffer.write(image_data)
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


def parse_jpeg_dimensions(data: bytes) -> tuple[int, int]:
    i = 2
    while i < len(data) - 9:
        if data[i] != 0xFF:
            return 0, 0
        marker = data[i + 1]
        if marker in (0xC0, 0xC2):
            h = struct.unpack(">H", data[i + 5:i + 7])[0]
            w = struct.unpack(">H", data[i + 7:i + 9])[0]
            return w, h
        elif marker == 0xDA:
            break
        elif 0xD0 <= marker <= 0xD9:
            i += 2
        elif marker in (0x00, 0x01):
            i += 2
        else:
            if i + 3 < len(data):
                seg_len = struct.unpack(">H", data[i + 2:i + 4])[0]
                i += 2 + seg_len
            else:
                break
    return 0, 0


def stream_airplay() -> None:
    print("INFO: Iniciando servidor AirPlay via uxplay...", file=sys.stderr, flush=True)

    subprocess.run(["pkill", "-f", "uxplay"], capture_output=True)
    time.sleep(0.5)

    r_fd, w_fd = os.pipe()

    vc = (f"videoconvert ! tee name=t ! queue ! jpegenc quality=70 ! "
          f"fdsink fd={w_fd} t. ! queue ! videoconvert")

    try:
        proc = subprocess.Popen(
            ["uxplay", "-nh", "-n", "Mirror", "-vc", vc, "-vs", "fakesink", "-as", "0"],
            stderr=subprocess.PIPE,
            stdout=subprocess.DEVNULL,
            pass_fds=(w_fd,),
        )
    except FileNotFoundError:
        print("MIRROR_ERROR: uxplay não encontrado. Instale com: sudo apt install uxplay",
              file=sys.stderr, flush=True)
        os.close(r_fd)
        os.close(w_fd)
        sys.exit(1)

    os.close(w_fd)

    time.sleep(1)
    if proc.poll() is not None:
        err = ""
        try:
            err = proc.stderr.read().decode('utf-8', errors='replace').strip()
        except Exception:
            pass
        if "DNS-SD" in err or "DNSService" in err:
            print("MIRROR_ERROR: Serviço DNS-SD (Avahi) não está rodando. "
                  "Execute: sudo systemctl start avahi-daemon",
                  file=sys.stderr, flush=True)
        else:
            print(f"MIRROR_ERROR: uxplay falhou: {err}", file=sys.stderr, flush=True)
        os.close(r_fd)
        sys.exit(1)

    uxplay_error = []

    def monitor_stderr():
        try:
            for raw_line in proc.stderr:
                text = raw_line.decode('utf-8', errors='replace').strip()
                if text:
                    print(f"INFO: uxplay: {text}", file=sys.stderr, flush=True)
                    if "ERROR" in text or "error" in text:
                        uxplay_error.append(text)
        except Exception:
            pass

    stderr_thread = threading.Thread(target=monitor_stderr, daemon=True)
    stderr_thread.start()

    print("MIRROR_AIRPLAY_READY", file=sys.stderr, flush=True)

    frame_count = 0
    cached_w, cached_h = 0, 0
    buf = b''

    try:
        while True:
            if proc.poll() is not None and not buf:
                break

            try:
                chunk = os.read(r_fd, 131072)
            except OSError:
                break
            if not chunk:
                break

            buf += chunk

            while True:
                soi = buf.find(b'\xff\xd8')
                if soi == -1:
                    buf = b''
                    break
                if soi > 0:
                    buf = buf[soi:]

                eoi = buf.find(b'\xff\xd9', 2)
                if eoi == -1:
                    break

                frame = buf[:eoi + 2]
                buf = buf[eoi + 2:]

                if cached_w == 0:
                    cached_w, cached_h = parse_jpeg_dimensions(frame)

                write_frame(frame, cached_w, cached_h)
                frame_count += 1

        if frame_count == 0:
            err_detail = "; ".join(uxplay_error) if uxplay_error else "nenhum frame recebido"
            print(f"MIRROR_ERROR: AirPlay encerrou sem enviar vídeo ({err_detail})",
                  file=sys.stderr, flush=True)
    except (BrokenPipeError, KeyboardInterrupt):
        pass
    finally:
        os.close(r_fd)
        if proc.poll() is None:
            proc.terminate()
            try:
                proc.wait(timeout=5)
            except subprocess.TimeoutExpired:
                proc.kill()
        sys.exit(0 if frame_count > 0 else 1)


def _start_capture_worker(fq, capture_fn, label="primary"):
    def worker():
        while True:
            try:
                raw = capture_fn()
                fq.put(raw)
            except (ConnectionError, BrokenPipeError, OSError) as e:
                print(f"MIRROR_ERROR: Dispositivo desconectado ({label}): {e}", file=sys.stderr, flush=True)
                fq.put(None)
                return
            except KeyboardInterrupt:
                fq.put(None)
                return
            except Exception as e:
                print(f"MIRROR_ERROR: ({label}) {e}", file=sys.stderr, flush=True)
                time.sleep(1)

    t = threading.Thread(target=worker, daemon=True)
    t.start()
    return t


def _consume_frames(fq) -> None:
    try:
        while True:
            raw = fq.get()
            if raw is None:
                sys.exit(1)
            while not fq.empty():
                try:
                    latest = fq.get_nowait()
                    if latest is None:
                        sys.exit(1)
                    raw = latest
                except _queue.Empty:
                    break
            data, w, h = optimize_frame(raw)
            write_frame(data, w, h)
    except KeyboardInterrupt:
        sys.exit(0)


def screenshot_loop(screenshot_service, udid=None) -> None:
    fq = _queue.Queue()
    _start_capture_worker(fq, screenshot_service.take_screenshot, "worker-1")

    if udid and USE_PYMOBILEDEVICE3:
        for i in range(2):
            try:
                lockdown = create_using_usbmux(serial=udid)
                ss = ScreenshotService(lockdown=lockdown)
                ss.take_screenshot()
                _start_capture_worker(fq, ss.take_screenshot, f"worker-{i+2}")
                print(f"INFO: Captura paralela worker-{i+2} ativa", file=sys.stderr, flush=True)
            except Exception as e:
                print(f"INFO: Captura paralela worker-{i+2} não disponível: {e}", file=sys.stderr, flush=True)
                break

    _consume_frames(fq)


def dvt_screenshot_loop(dvt) -> None:
    from pymobiledevice3.services.dvt.instruments.screenshot import Screenshot
    ss = Screenshot(dvt)
    print("INFO: DVT Screenshot conectado", file=sys.stderr, flush=True)
    fq = _queue.Queue()
    _start_capture_worker(fq, ss.get_screenshot)
    _consume_frames(fq)


def try_screenshot_service(lockdown, udid=None) -> bool:
    try:
        screenshot_service = ScreenshotService(lockdown=lockdown)
        print("INFO: ScreenshotService conectado", file=sys.stderr, flush=True)
        screenshot_loop(screenshot_service, udid)
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

    if try_screenshot_service(lockdown, udid):
        return

    print("INFO: ScreenshotService direto falhou, tentando alternativas...", file=sys.stderr, flush=True)

    if ios_version >= 17:
        if try_tunneld_screenshot(udid):
            return

        try_auto_mount(udid)

        try:
            lockdown2 = create_using_usbmux(serial=udid)
            if try_screenshot_service(lockdown2, udid):
                return
        except Exception:
            pass

    stream_idevicescreenshot(udid)


def main() -> None:
    if "--airplay" in sys.argv:
        stream_airplay()
        return

    if len(sys.argv) < 2:
        print("Uso: mirror_stream.py <UDID> | --airplay", file=sys.stderr, flush=True)
        sys.exit(1)

    udid = sys.argv[1]
    print(f"INFO: Iniciando stream para dispositivo {udid}", file=sys.stderr, flush=True)

    if USE_PYMOBILEDEVICE3:
        stream_pymobiledevice3(udid)
    else:
        stream_idevicescreenshot(udid)


if __name__ == "__main__":
    main()
