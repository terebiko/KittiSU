#!/system/bin/sh
# Yurikey Preset: Fix detect recovery file (kill/clear integrity checker apps)
MODPATH="/data/adb/modules/Yurikey"
if [ ! -d "$MODPATH" ]; then
    echo "ERROR: Yurikey module not found"
    exit 1
fi
sh "$MODPATH/Yuri/kill_all.sh"
