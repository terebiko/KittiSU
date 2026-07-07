#!/system/bin/sh
# Yurikey Preset: Setup yuri keybox
MODPATH="/data/adb/modules/Yurikey"
if [ ! -d "$MODPATH" ]; then
    echo "ERROR: Yurikey module not found"
    exit 1
fi
sh "$MODPATH/Yuri/yuri_keybox.sh"
