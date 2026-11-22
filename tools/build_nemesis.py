#!/usr/bin/env python3
"""Build K8 Gen5W packages for NEMESIS Manager."""
import argparse
import hashlib
import io
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import zipfile

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "private" / "nemesis"
PACKAGE_ID = "k8home.shortcuts"
PACKAGE_VERSION = "0.4.13"
HOME_PACKAGE_VERSION = "0.5.0-test19"
HOME_PACKAGE_ID = "k8home.install"
HOME_APP_ID = "io.github.k8home"
ZIP_TIME = (2026, 11, 21, 0, 0, 0)
# 곡 넘김 도우미(system uid NEMESIS 서비스). 일반 앱은 핸들 키(166/167/91)를 순정 모드 관리자에 넣을 수 없어
# 도우미가 대신 보낸다. K8 Now Playing 의 k8np.keys 와 같은 코드(2026-11-16 이 차에서 확인).
HELPER_SRC = ROOT / "helper" / "src"
HELPER_MAIN = b"Lio/github/k8home/helper/KeyHelper;"
NEMESIS_DIR = ROOT / "nemesis"
DEFAULT_ANDROID_JAR = ROOT.parent / "k8-ambient-sync" / ".tools" / "android19" / "android-4.4.2" / "android.jar"
DEFAULT_D8 = Path.home() / ".local/share/android-sdk/build-tools/35.0.1/lib/d8.jar"

# Launcher components observed on the K8 head unit.
SHORTCUTS = (
    ("map", "Navigation", "com.mnsoft.navi/.NaviApp"),
    ("media", "Media", "com.hkmc.av.app.mediagate/.MediaGateActivity"),
    ("phone", "Bluetooth Phone", "com.hkmc.bluetooth/com.android.contacts.DialtactsActivity"),
    ("radio", "Radio", "com.hkmc.av.app.radioge/.RadioMainActivity"),
    ("bt-audio", "Bluetooth Audio", "com.daudio.av.app.btaudio.ui/.BtMusicMainActivity"),
    ("usb-audio", "USB Music", "com.daudio.av.app.usbmusic/.ui.MusicActivity"),
    ("nature", "Sounds of Nature", "com.hkmc.av.app.naturesound/.ui.NatureSoundActivity"),
    ("projection", "Phone Projection", "com.daudio.app.phoneprojection/.settings.ui.DeviceConnectionSettings"),
    ("settings", "Settings", "com.android.settings/.Settings"),
    ("vehicle", "Vehicle Settings", "com.android.vehiclesettings/.VehicleSetting"),
    ("profile", "Driver Profile", "com.hkmc.system.app.userprofile/.UserProfileMainActivity"),
)


def descriptor(package_id, label):
    return "\n".join([
        "[nemesis]",
        "version=1.1",
        f"id={package_id}",
        f"name=K8 {label}",
        "type=application",
        f"package-version={PACKAGE_VERSION}",
        f'short-desc="Open stock {label} on K8 Gen5W"',
        f'detailed-desc="Opens the stock {label} screen from NEMESIS Manager."',
        "os=any",
        "models=any",
        "",
        "[app.0]",
        "kind=sh",
        "path=bin/open.sh",
        "uid=system",
        "",
    ])


def launch(component):
    return (
        "exec /system/bin/am start -a android.intent.action.MAIN "
        f"-c android.intent.category.LAUNCHER -n '{component}'\n"
    )


def script(key, component):
    lines = ["#!/system/bin/sh\n"]
    if key == "usb-audio":
        lines.extend([
            "while read -r device mountpoint options; do\n",
            '  case "$mountpoint" in\n',
            "    /storage/usb[0-2]|/mnt/media_rw/usb[0-2])\n",
            "      " + launch(component),
            "      ;;\n",
            "  esac\n",
            "done < /proc/mounts\n",
            'echo "USB media is not mounted. Opening Media."\n',
        ])
        component = "com.hkmc.av.app.mediagate/.MediaGateActivity"
    lines.append(launch(component))
    return "".join(lines)


def entry(name, mode=0o644, compress=zipfile.ZIP_DEFLATED):
    info = zipfile.ZipInfo(name, ZIP_TIME)
    info.create_system = 3
    info.compress_type = compress
    info.external_attr = mode << 16
    return info


def package_bytes(package_id, key, label, component):
    buffer = io.BytesIO()
    with zipfile.ZipFile(buffer, "w", allowZip64=False) as archive:
        archive.writestr(entry("NEMESIS.data"), descriptor(package_id, label))
        archive.writestr(entry("bin/open.sh", 0o755), script(key, component))
    return buffer.getvalue()


def shell_script(path):
    text = path.read_bytes().decode("utf-8")
    if not text.startswith("#!/system/bin/sh\n") or "\r" in text:
        raise ValueError(f"{path}: must be an LF-only /system/bin/sh script")
    return text


def compile_helper(jdk, d8_jar, android_jar):
    """helper/src → API 19 DEX (javac 8 → D8 --min-api 19)."""
    suffix = ".exe" if os.name == "nt" else ""
    javac = str(Path(jdk) / "bin" / ("javac" + suffix))
    java = str(Path(jdk) / "bin" / ("java" + suffix))
    sources = sorted(str(p) for p in HELPER_SRC.rglob("*.java"))
    with tempfile.TemporaryDirectory(prefix="k8home-keys-") as stage:
        stage = Path(stage)
        classes, dex_dir = stage / "classes", stage / "dex"
        classes.mkdir()
        dex_dir.mkdir()
        jar = stage / "android.jar"
        shutil.copyfile(android_jar, jar)
        subprocess.run([javac, "-encoding", "UTF-8", "-source", "8", "-target", "8", "-Xlint:-options",
                        "-bootclasspath", str(jar), "-d", str(classes)] + sources, check=True)
        class_files = sorted(str(p) for p in classes.rglob("*.class"))
        subprocess.run([java, "-cp", str(d8_jar), "com.android.tools.r8.D8", "--min-api", "19",
                        "--lib", str(jar), "--output", str(dex_dir)] + class_files, check=True)
        data = (dex_dir / "classes.dex").read_bytes()
    if not data.startswith(b"dex\n035\x00") or HELPER_MAIN not in data:
        raise SystemExit("unexpected helper DEX output")
    return data


def build_home(out, apk, helper_dex):
    apk_path = "app/k8-home.apk"
    package_name = f"{HOME_PACKAGE_ID}-{HOME_PACKAGE_VERSION}.zip"
    usb_path = f"packages/{package_name}"
    package_descriptor = "\n".join([
        "[nemesis]",
        "version=1.1",
        f"id={HOME_PACKAGE_ID}",
        "name=K8 ccNC Home Theme",
        "type=application,service",
        f"package-version={HOME_PACKAGE_VERSION}",
        "short-desc=K8 ccNC home with stock navigation",
        "os=any",
        "models=any",
        "postinstall=scripts/postinstall.sh",
        "",
        "[app.0]",
        "kind=apk",
        f"path={apk_path}",
        f"package={HOME_APP_ID}",
        f"component={HOME_APP_ID}/.HomeActivity",
        "",
        "[service.0]",
        "name=keys",
        "kind=sh",
        "path=svc/keys.sh",
        "uid=system",
        "mode=restart",
        "",
    ])
    package = io.BytesIO()
    with zipfile.ZipFile(package, "w", allowZip64=False) as archive:
        archive.writestr(entry("NEMESIS.data"), package_descriptor)
        archive.writestr(entry(apk_path, compress=zipfile.ZIP_STORED), apk.read_bytes())
        archive.writestr(entry("svc/keys.sh", 0o755), shell_script(NEMESIS_DIR / "svc" / "keys.sh"))
        archive.writestr(entry("scripts/postinstall.sh", 0o755),
                         shell_script(NEMESIS_DIR / "scripts" / "postinstall.sh"))
        archive.writestr(entry("bin/k8home-keys.dex"), helper_dex)
    usb_descriptor = "\n".join([
        "[nemesis]",
        "version=1.1",
        "type=list",
        "",
        "[package.0]",
        f"path={usb_path}",
        "name=K8 ccNC Home Theme",
        "short-desc=K8 ccNC home with stock navigation",
        "published=2026-11-21",
        "",
    ])
    usb = out / f"K8-ccNC-Home-NEMESIS-{HOME_PACKAGE_VERSION}-USB.zip"
    with zipfile.ZipFile(usb, "w", allowZip64=False) as archive:
        archive.writestr(entry("NEMESIS.data"), usb_descriptor)
        archive.writestr(entry(usb_path, compress=zipfile.ZIP_STORED), package.getvalue())
    return usb


def build_shortcuts(out):
    rows = ["[nemesis]", "version=1.1", "type=list", ""]
    usb = out / f"K8-NEMESIS-Shortcuts-{PACKAGE_VERSION}-USB.zip"
    with zipfile.ZipFile(usb, "w", allowZip64=False) as archive:
        for index, (key, label, component) in enumerate(SHORTCUTS):
            package_id = PACKAGE_ID if key == "map" else f"{PACKAGE_ID}.{key}"
            package = package_bytes(package_id, key, label, component)
            package_name = f"{package_id}-{PACKAGE_VERSION}.zip"
            (out / package_name).write_bytes(package)
            usb_path = f"packages/{package_name}"
            archive.writestr(entry(usb_path, compress=zipfile.ZIP_STORED), package)
            rows.extend([
                f"[package.{index}]",
                f"path={usb_path}",
                f"name=K8 {label}",
                f"short-desc=Open stock {label} on K8 Gen5W",
                "published=2026-11-21",
                "",
            ])
        archive.writestr(entry("NEMESIS.data"), "\n".join(rows))
    return usb


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--out", type=Path, default=OUT)
    parser.add_argument("--home-apk", type=Path, help="Signed K8 Home APK to package instead of shortcuts")
    parser.add_argument("--jdk", default=os.environ.get("JAVA_HOME"), help="JDK for the key helper DEX")
    parser.add_argument("--d8", type=Path, default=DEFAULT_D8)
    parser.add_argument("--android-jar", type=Path, default=DEFAULT_ANDROID_JAR)
    args = parser.parse_args()
    # 차량 WebView(Chrome 30) 호환 검사. 걸린 것이 있으면 APK 를 만들지 않는다.
    for check in ("legacy_clamp.py", "c30_check.py"):
        script = Path(__file__).resolve().parent / check
        targets = [str(ROOT / "assets" / name) for name in ("index.html", "navbar.html")]
        cmd = [sys.executable, str(script)] + targets
        if check == "legacy_clamp.py":
            cmd += ["--check"]
        env = dict(os.environ, PYTHONIOENCODING="utf-8")  # 검사 출력이 한글이다(Windows 기본 코드페이지로는 못 읽는다)
        result = subprocess.run(cmd, cwd=str(ROOT), capture_output=True, env=env)
        out = result.stdout.decode("utf-8", "replace")
        err = result.stderr.decode("utf-8", "replace")
        sys.stdout.buffer.write(out.encode("utf-8")) if hasattr(sys.stdout, "buffer") else sys.stdout.write(out)
        sys.stdout.flush()
        sys.stderr.write(err)
        if result.returncode != 0:
            sys.stderr.write("빌드를 멈춘다: %s 검사 실패\n" % check)
            sys.exit(1)
    if args.home_apk:
        if not args.home_apk.is_file() or not zipfile.is_zipfile(args.home_apk):
            parser.error("--home-apk must point to a signed K8 Home APK")
        with zipfile.ZipFile(args.home_apk) as apk:
            required = {
                "AndroidManifest.xml", "classes.dex", "assets/index.html", "assets/navbar.html",
                "assets/route_panel.html", "assets/corners.html",
            }
            if not required.issubset(apk.namelist()) or apk.testzip() is not None:
                parser.error("--home-apk is missing K8 Home contents or is corrupted")
    args.out.mkdir(parents=True, exist_ok=True)
    if args.home_apk:
        if not args.jdk:
            parser.error("--jdk (or JAVA_HOME) is needed to build the key helper")
        usb = build_home(args.out, args.home_apk, compile_helper(args.jdk, args.d8, args.android_jar))
    else:
        usb = build_shortcuts(args.out)
    print(f"usb={usb}")
    print(f"sha256={hashlib.sha256(usb.read_bytes()).hexdigest()}")


if __name__ == "__main__":
    main()
