#!/system/bin/sh
# Yurikey Preset: Fix detect PIF

find_mod() {
    for base in /data/adb/modules /data/adb/modules_update; do
        for name in Yurikey yurikey; do
            [ -d "$base/$name" ] && { echo "$base/$name"; return; }
        done
    done
}

MODPATH=$(find_mod)
if [ -z "$MODPATH" ]; then
    echo "ERROR: Yurikey module not found in /data/adb/modules or /data/adb/modules_update"
    exit 1
fi

ERR=0

# Primary: PIF helper in Yuri/
PIF="$MODPATH/Yuri/pif.sh"
if [ -f "$PIF" ]; then
    echo "Running $PIF"
    sh "$PIF" || ERR=1
else
    echo "Note: $PIF not found, skipping primary PIF fix"
fi

# Secondary: remove pihook/pixelprops props
PIF2="$MODPATH/webroot/common/pif2.sh"
if [ -f "$PIF2" ]; then
    echo "Running $PIF2"
    sh "$PIF2" || ERR=1
else
    echo "Note: $PIF2 not found, skipping pihook prop cleanup"
fi

exit $ERR
