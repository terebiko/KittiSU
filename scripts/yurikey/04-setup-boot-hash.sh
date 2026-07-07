#!/system/bin/sh
# Yurikey Preset: Setup verified boot hash
MODPATH="/data/adb/modules/Yurikey"
if [ ! -d "$MODPATH" ]; then
    echo "ERROR: Yurikey module not found"
    exit 1
fi
sh "$MODPATH/Yuri/boot_hash.sh"
