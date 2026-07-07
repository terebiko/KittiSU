#!/system/bin/sh
# Yurikey Preset: Fix Widevine L1 (re-apply boot hash and security patch)

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
for script in boot_hash.sh security_patch.sh; do
    SCRIPT="$MODPATH/Yuri/$script"
    if [ -f "$SCRIPT" ]; then
        sh "$SCRIPT" || ERR=1
    else
        echo "ERROR: $SCRIPT not found"
        ERR=1
    fi
done

exit $ERR
