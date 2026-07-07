#!/system/bin/sh
# Yurikey Preset: Update RKA config
MODPATH="/data/adb/modules/Yurikey"
if [ ! -d "$MODPATH" ]; then
    echo "ERROR: Yurikey module not found"
    exit 1
fi
sh "$MODPATH/Yuri/yurirka.sh"
