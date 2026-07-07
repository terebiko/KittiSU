#!/system/bin/sh
# Yurikey Preset: Fix detect recovery file (TWRP folder / integrity checker cleanup)

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

# Primary: delete TWRP folder from internal storage
TWRP="$MODPATH/webroot/common/twrp.sh"
if [ -f "$TWRP" ]; then
    echo "Running $TWRP"
    sh "$TWRP" || ERR=1
else
    echo "Note: $TWRP not found, skipping TWRP cleanup"
fi

# Fallback: kill integrity checker / Google processes
for script in kill_all.sh kill_google_process.sh; do
    KILL="$MODPATH/Yuri/$script"
    if [ -f "$KILL" ]; then
        echo "Running $KILL"
        sh "$KILL" || ERR=1
        break
    fi
done

exit $ERR
