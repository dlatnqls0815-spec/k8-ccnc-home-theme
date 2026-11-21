#!/system/bin/sh
# The key helper runs as the system uid and keeps its dalvik cache in a 0700 private directory.
# NEMESIS root has no CAP_CHOWN, so the parent directory is made world-writable instead.
data=/data/nemesis/logs/k8home.keys
mkdir -p "$data"
chmod 777 "$data"
exit 0
