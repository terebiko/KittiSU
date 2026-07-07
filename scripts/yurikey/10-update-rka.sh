#!/system/bin/sh
# Yurikey Preset: Update RKA config

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

SCRIPT="$MODPATH/Yuri/yurirka.sh"
JSONARRAY="$MODPATH/Yuri/rka/jsonarray.sh"

if [ ! -f "$SCRIPT" ]; then
    echo "ERROR: $SCRIPT not found"
    exit 1
fi

if [ ! -f "$JSONARRAY" ]; then
    echo "ERROR: $JSONARRAY not found"
    exit 1
fi

# yurirka.sh sources rka/jsonarray.sh relative to its own directory
sh "$SCRIPT"
