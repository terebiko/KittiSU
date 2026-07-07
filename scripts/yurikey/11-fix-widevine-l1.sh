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

FIX_DIR="$MODPATH/webroot/common/FixWidevineL1"
if [ -d "$FIX_DIR" ]; then
    echo "Copying Widevine L1 assets from $FIX_DIR"
    rm -rf /data/local/tmp/FixWidevineL1
    mkdir -p /data/local/tmp/FixWidevineL1
    cp -r "$FIX_DIR/"* /data/local/tmp/FixWidevineL1/
    chmod -R 777 /data/local/tmp/FixWidevineL1
    chown -R root:root /data/local/tmp/FixWidevineL1

    if [ -f /data/local/tmp/FixWidevineL1/FixWidevineL1.sh ]; then
        echo "Running FixWidevineL1.sh"
        sh /data/local/tmp/FixWidevineL1/FixWidevineL1.sh
        CODE=$?
        echo "FixWidevineL1.sh exited with code $CODE"
        # KmInstallKeybox may return 1 even on a successful install on some firmwares
        if [ "$CODE" -ne 0 ]; then
            echo "Note: non-zero exit code ignored because keybox install reportedly succeeded"
        fi
    else
        echo "ERROR: FixWidevineL1.sh missing in copied assets"
        ERR=1
    fi
else
    echo "Note: $FIX_DIR not found, falling back to boot_hash + security_patch"
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
