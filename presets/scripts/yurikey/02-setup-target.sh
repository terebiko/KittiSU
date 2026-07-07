#!/system/bin/sh
# Yurikey Preset: Set up target.txt
MODPATH="/data/adb/modules/Yurikey"
if [ ! -d "$MODPATH" ]; then
    echo "ERROR: Yurikey module not found"
    exit 1
fi
sh "$MODPATH/Yuri/target_txt.sh"
