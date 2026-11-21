"""Build a signed Gen5W APK with the local SDK, JDK and no Gradle."""

import argparse
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import zipfile

ROOT = Path(__file__).resolve().parent


def run(*args):
    subprocess.run([str(arg) for arg in args], check=True)


def build(sdk, jdk, output):
    exe = ".exe" if os.name == "nt" else ""
    tools = sdk / "build-tools" / "35.0.1"
    android = sdk / "platforms" / "android-19" / "android.jar"
    java = jdk / "bin" / ("java" + exe)
    javac = jdk / "bin" / ("javac" + exe)
    keytool = jdk / "bin" / ("keytool" + exe)
    aapt = tools / ("aapt" + exe)
    aapt2 = tools / ("aapt2" + exe)
    zipalign = tools / ("zipalign" + exe)
    d8 = tools / "lib" / "d8.jar"
    signer = tools / "lib" / "apksigner.jar"
    key = ROOT / ".build" / "k8-home.p12"
    key.parent.mkdir(exist_ok=True)
    output.parent.mkdir(parents=True, exist_ok=True)

    with tempfile.TemporaryDirectory(prefix="k8-home-") as temp:
        stage = Path(temp)
        classes = stage / "classes"
        classes.mkdir()
        jar = stage / "android.jar"
        shutil.copyfile(android, jar)
        # Compile-only copies of the firmware's optional automotive library; never packaged.
        stubs = stage / "stubs"
        stubs.mkdir()
        run(javac, "-encoding", "UTF-8", "-source", "8", "-target", "8", "-Xlint:-options",
            "-bootclasspath", jar, "-d", stubs, *sorted((ROOT / "stubs").rglob("*.java")))
        run(javac, "-encoding", "UTF-8", "-source", "8", "-target", "8", "-Xlint:-options",
            "-bootclasspath", jar, "-classpath", stubs, "-d", classes, *sorted((ROOT / "src").rglob("*.java")))
        run(java, "-cp", d8, "com.android.tools.r8.D8", "--min-api", "19", "--lib", jar,
            "--output", stage, *sorted(classes.rglob("*.class")))
        manifest = stage / "AndroidManifest.xml"
        shutil.copyfile(ROOT / "AndroidManifest.xml", manifest)
        resources = stage / "resources.zip"
        shutil.copytree(ROOT / "res", stage / "res")
        run(aapt2, "compile", "--dir", stage / "res", "-o", resources)
        unsigned = stage / "unsigned.apk"
        run(aapt2, "link", "-o", unsigned, "--manifest", manifest, "-I", jar, resources)
        with zipfile.ZipFile(unsigned, "a", zipfile.ZIP_DEFLATED) as apk:
            apk.write(stage / "classes.dex", "classes.dex")
            apk.write(ROOT / "assets" / "index.html", "assets/index.html")
            apk.write(ROOT / "assets" / "navbar.html", "assets/navbar.html")
            apk.write(ROOT / "assets" / "corners.html", "assets/corners.html")
            apk.write(ROOT / "assets" / "playback_panel.html", "assets/playback_panel.html")
            apk.write(ROOT / "assets" / "route_panel.html", "assets/route_panel.html")
            for icon in sorted((ROOT / "assets" / "icons").glob("*.png")):
                apk.write(icon, "assets/icons/" + icon.name)
        aligned = stage / "aligned.apk"
        run(zipalign, "-f", "4", unsigned, aligned)
        stage_key = stage / "k8-home.p12"
        if key.exists():
            shutil.copyfile(key, stage_key)
        else:
            run(keytool, "-genkeypair", "-noprompt", "-storetype", "PKCS12", "-keystore", stage_key,
                "-storepass", "android", "-alias", "k8home", "-dname", "CN=K8 Home Test",
                "-keyalg", "RSA", "-keysize", "2048", "-validity", "10000")
            shutil.copyfile(stage_key, key)
        signed = stage / "signed.apk"
        run(java, "-jar", signer, "sign", "--ks", stage_key, "--ks-key-alias", "k8home",
            "--ks-pass", "pass:android", "--v1-signing-enabled", "true", "--out", signed, aligned)
        run(java, "-jar", signer, "verify", "--min-sdk-version", "19", "--verbose", signed)
        run(zipalign, "-c", "4", signed)
        run(aapt, "dump", "badging", signed)
        shutil.copyfile(signed, output)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--sdk", type=Path, required=True)
    parser.add_argument("--jdk", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    build(args.sdk, args.jdk, args.output)
