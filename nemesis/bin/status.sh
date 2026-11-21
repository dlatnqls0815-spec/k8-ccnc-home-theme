#!/system/bin/sh
# Shows the install result and the last helper log line (NEMESIS Manager shows script output as a toast).
log=/data/nemesis/logs/k8home.install/install.log
last=
if [ -f "$log" ]; then
    while read -r line; do
        last="$line"
    done < "$log"
fi
app=$(pm path io.github.k8home 2>/dev/null)
keys=/data/nemesis/logs/k8home.keys/keys.log
klast=
if [ -f "$keys" ]; then
    while read -r line; do
        klast="$line"
    done < "$keys"
fi
echo "app: ${app:-not installed}"
echo "install: ${last:-no log yet}"
echo "keys: ${klast:-no log yet}"
