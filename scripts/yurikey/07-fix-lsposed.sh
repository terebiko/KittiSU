#!/system/bin/sh
# Yurikey Preset: Fix detected LSPosed (configure Zygisk Next)

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

SCRIPT="$MODPATH/Yuri/znctl.sh"
if [ ! -f "$SCRIPT" ]; then
    echo "ERROR: $SCRIPT not found"
    exit 1
fi

sh "$SCRIPT"
