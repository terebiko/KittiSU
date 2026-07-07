#!/system/bin/sh
# Yurikey Preset: Set HMA OSS config
MODPATH="/data/adb/modules/Yurikey"
if [ ! -d "$MODPATH" ]; then
    echo "ERROR: Yurikey module not found"
    exit 1
fi
sh "$MODPATH/Yuri/hma.sh"
