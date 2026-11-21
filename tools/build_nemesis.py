#!/usr/bin/env python3
"""Build K8 Gen5W packages for NEMESIS Manager."""
import argparse
import hashlib
import io
from pathlib import Path
import zipfile

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "private" / "nemesis"
PACKAGE_ID = "k8home.shortcuts"
PACKAGE_VERSION = "0.4.13"
HOME_PACKAGE_VERSION = "0.4.44"
HOME_PACKAGE_ID = "k8home.install"
HOME_APP_ID = "io.github.k8home"
ZIP_TIME = (2026, 11, 21, 0, 0, 0)

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


def build_home(out, apk):
    apk_path = "app/k8-home.apk"
    package_name = f"{HOME_PACKAGE_ID}-{HOME_PACKAGE_VERSION}.zip"
    usb_path = f"packages/{package_name}"
    package_descriptor = "\n".join([
        "[nemesis]",
        "version=1.1",
        f"id={HOME_PACKAGE_ID}",
        "name=K8 ccNC Home Theme",
        "type=application",
        f"package-version={HOME_PACKAGE_VERSION}",
        "short-desc=K8 ccNC home with stock navigation",
        "os=any",
        "models=any",
        "",
        "[app.0]",
        "kind=apk",
        f"path={apk_path}",
        f"package={HOME_APP_ID}",
        f"component={HOME_APP_ID}/.HomeActivity",
        "",
    ])
    package = io.BytesIO()
    with zipfile.ZipFile(package, "w", allowZip64=False) as archive:
        archive.writestr(entry("NEMESIS.data"), package_descriptor)
        archive.writestr(entry(apk_path, compress=zipfile.ZIP_STORED), apk.read_bytes())
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
    args = parser.parse_args()
    if args.home_apk:
        if not args.home_apk.is_file() or not zipfile.is_zipfile(args.home_apk):
            parser.error("--home-apk must point to a signed K8 Home APK")
        with zipfile.ZipFile(args.home_apk) as apk:
            required = {
                "AndroidManifest.xml", "classes.dex", "assets/index.html", "assets/navbar.html",
                "assets/playback_panel.html", "assets/route_panel.html", "assets/corners.html",
            }
            if not required.issubset(apk.namelist()) or apk.testzip() is not None:
                parser.error("--home-apk is missing K8 Home contents or is corrupted")
    args.out.mkdir(parents=True, exist_ok=True)
    usb = build_home(args.out, args.home_apk) if args.home_apk else build_shortcuts(args.out)
    print(f"usb={usb}")
    print(f"sha256={hashlib.sha256(usb.read_bytes()).hexdigest()}")


if __name__ == "__main__":
    main()
