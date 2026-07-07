#!/system/bin/sh
# Yurikey Preset: Fix Widevine L1

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

ERR=0

# Primary: dedicated Widevine L1 helper
WIDEVINE="$MODPATH/webroot/common/widevinel1.sh"
if [ -f "$WIDEVINE" ]; then
    echo "Running $WIDEVINE"
    sh "$WIDEVINE" || ERR=1
else
    echo "Note: $WIDEVINE not found, falling back to boot_hash + security_patch"
fi

# Fallback / supplement: re-apply boot hash and security patch
for script in boot_hash.sh security_patch.sh; do
    SCRIPT="$MODPATH/Yuri/$script"
    if [ -f "$SCRIPT" ]; then
        echo "Running $SCRIPT"
        sh "$SCRIPT" || ERR=1
    else
        echo "Note: $SCRIPT not found"
    fi
done

exit $ERR
