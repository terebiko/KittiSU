#!/system/bin/sh
# Yurikey Preset: Fix Widevine L1 (re-apply boot hash and security patch)
MODPATH="/data/adb/modules/Yurikey"
if [ ! -d "$MODPATH" ]; then
    echo "ERROR: Yurikey module not found"
    exit 1
fi
sh "$MODPATH/Yuri/boot_hash.sh"
sh "$MODPATH/Yuri/security_patch.sh"
