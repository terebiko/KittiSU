#!/system/bin/sh
# Yurikey Preset: Fix detected LSPosed

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

# Primary: Zygisk Next config helper
ZNCTL="$MODPATH/Yuri/znctl.sh"
if [ -f "$ZNCTL" ]; then
    echo "Running $ZNCTL"
    sh "$ZNCTL" || ERR=1
else
    echo "Note: $ZNCTL not found, skipping Zygisk Next config"
fi

# Secondary: clear LSPosed odex traces
LSPOSED2="$MODPATH/webroot/common/lsposed2.sh"
if [ -f "$LSPOSED2" ]; then
    echo "Running $LSPOSED2"
    sh "$LSPOSED2" || ERR=1
else
    echo "Note: $LSPOSED2 not found, skipping odex cleanup"
fi

exit $ERR
