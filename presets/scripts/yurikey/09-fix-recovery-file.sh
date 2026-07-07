#!/system/bin/sh
# Yurikey Preset: Fix detect recovery file (delete TWRP folder)

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

TWRP="$MODPATH/webroot/common/twrp.sh"
if [ -f "$TWRP" ]; then
    echo "Running $TWRP"
    sh "$TWRP"
else
    echo "ERROR: $TWRP not found"
    exit 1
fi
