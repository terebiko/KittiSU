#!/system/bin/sh
# Yurikey Preset: Fix detected LSPosed (configure Zygisk Next)
MODPATH="/data/adb/modules/Yurikey"
if [ ! -d "$MODPATH" ]; then
    echo "ERROR: Yurikey module not found"
    exit 1
fi
sh "$MODPATH/Yuri/znctl.sh"
