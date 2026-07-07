#!/system/bin/sh
# Yurikey Preset: Clear all detection traces

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

for script in clear_all_detection_traces.sh kill_google_process.sh; do
    SCRIPT="$MODPATH/Yuri/$script"
    if [ -f "$SCRIPT" ]; then
        sh "$SCRIPT"
        exit $?
    fi
done

echo "ERROR: No clear/kill script found in $MODPATH/Yuri"
exit 1
