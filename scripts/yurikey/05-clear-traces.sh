#!/system/bin/sh
# Yurikey Preset: Clear all detection traces
MODPATH="/data/adb/modules/Yurikey"
if [ ! -d "$MODPATH" ]; then
    echo "ERROR: Yurikey module not found"
    exit 1
fi
sh "$MODPATH/Yuri/clear_all_detection_traces.sh"
