#!/system/bin/sh
# K8 home theme media key helper (NEMESIS service, uid=system, mode=restart).
# Listens on the abstract socket k8home.keys, accepts only the io.github.k8home app and only the
# previous / play-pause / next keys, and replays them like the steering-wheel switch.

case "$0" in
    /*) pkg_dir=${0%/svc/*} ;;
    *) pkg_dir=/data/nemesis/apps/k8home.install ;;
esac
dex="$pkg_dir/bin/k8home-keys.dex"
data=/data/nemesis/logs/k8home.keys
[ -f "$dex" ] || { echo "dex missing"; exit 1; }

while [ "$(getprop sys.boot_completed)" != "1" ]; do
    sleep 5
done

priv="$data/private"
mkdir -p "$priv/dalvik-cache" 2>/dev/null
chmod 700 "$priv" 2>/dev/null
[ -w "$priv" ] || { echo "private dir not writable: $priv"; sleep 60; exit 1; }

ANDROID_DATA="$priv" CLASSPATH="$dex" exec app_process /system/bin io.github.k8home.helper.KeyHelper
