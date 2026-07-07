#!/system/bin/sh
# Yurikey Preset: Update RKA config

YURIKEY_REPO_RAW="https://raw.githubusercontent.com/Yurii0307/yurikey/main/Module/Yuri"
TMP_DIR="/data/local/tmp/yurikey-rka-patch"

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

SCRIPT="$MODPATH/Yuri/yurirka.sh"
JSONARRAY="$MODPATH/Yuri/rka/jsonarray.sh"

# If the module is missing yurirka.sh, fetch it from GitHub into a temp directory
if [ ! -f "$SCRIPT" ]; then
    echo "Module helper $SCRIPT not found; fetching from $YURIKEY_REPO_RAW"
    rm -rf "$TMP_DIR"
    mkdir -p "$TMP_DIR/rka"
    chmod 755 "$TMP_DIR"

    SCRIPT="$TMP_DIR/yurirka.sh"
    JSONARRAY="$TMP_DIR/rka/jsonarray.sh"

    if ! curl -fsSL "$YURIKEY_REPO_RAW/yurirka.sh" -o "$SCRIPT" 2>/dev/null; then
        if ! wget -q "$YURIKEY_REPO_RAW/yurirka.sh" -O "$SCRIPT" 2>/dev/null; then
            echo "ERROR: Failed to download yurirka.sh"
            exit 1
        fi
    fi

    if ! curl -fsSL "$YURIKEY_REPO_RAW/rka/jsonarray.sh" -o "$JSONARRAY" 2>/dev/null; then
        if ! wget -q "$YURIKEY_REPO_RAW/rka/jsonarray.sh" -O "$JSONARRAY" 2>/dev/null; then
            echo "ERROR: Failed to download jsonarray.sh"
            exit 1
        fi
    fi

    chmod 755 "$SCRIPT" "$JSONARRAY"
    echo "Downloaded yurirka helper to $TMP_DIR"
fi

if [ ! -f "$SCRIPT" ]; then
    echo "ERROR: $SCRIPT not found"
    exit 1
fi

if [ ! -f "$JSONARRAY" ]; then
    echo "ERROR: $JSONARRAY not found"
    exit 1
fi

# yurirka.sh sources rka/jsonarray.sh relative to its own directory
sh "$SCRIPT"
