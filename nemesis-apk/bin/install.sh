#!/system/bin/sh
# Installs the bundled K8 ccNC home theme APK (NEMESIS root). Same method as the Seogaemo
# install-apk.sh: sys.boot_completed is set to 0 only around "pm install -r" and is always put back.
case "$0" in
    */bin/*) pkg_dir=${0%/bin/*} ;;
    *) pkg_dir=/data/nemesis/apps/k8home.install ;;
esac
log_dir=/data/nemesis/logs/k8home.install
mkdir -p "$log_dir"
log="$log_dir/install.log"
say() { echo "$1"; echo "$(date) $1" >> "$log"; }

src="$pkg_dir/apk/k8-home.apk"
tmp=/data/local/tmp/k8home-install.apk
[ -f "$src" ] || { say "apk missing: $src"; exit 1; }
old=$(getprop sys.boot_completed)
[ "$old" = "1" ] || { say "not booted yet (sys.boot_completed=$old), try again later"; exit 1; }

cp "$src" "$tmp" && chmod 644 "$tmp" || { say "copy failed"; exit 1; }
trap 'setprop sys.boot_completed 1; rm -f "$tmp"' EXIT INT TERM HUP
setprop sys.boot_completed 0
out=$(pm install -r "$tmp" 2>&1)
setprop sys.boot_completed 1
now=$(getprop sys.boot_completed)
[ "$now" = "1" ] || { setprop sys.boot_completed 1; now=$(getprop sys.boot_completed); }
rm -f "$tmp"
case "$out" in
    *Success*) say "K8 ccNC home installed ($(pm path io.github.k8home)) boot_completed=$now" ;;
    *) say "install failed: $out boot_completed=$now" ;;
esac
