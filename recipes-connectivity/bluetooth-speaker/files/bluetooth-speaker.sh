#!/bin/sh

CONFIG_BLUETOOTH_FILE="/boot/bluetooth.conf"
SPEAKER_CONF="/etc/bluetooth-speaker.conf"
ASOUND_CONF="/etc/asound.conf"

if [ -f "$CONFIG_BLUETOOTH_FILE" ]; then
    echo "$CONFIG_BLUETOOTH_FILE detected!"

    MAC=$(head -n 1 "$CONFIG_BLUETOOTH_FILE" | tr -d '[:space:]' | tr 'abcdef' 'ABCDEF')
    if ! echo "$MAC" | grep -Eq '^([0-9A-F]{2}:){5}[0-9A-F]{2}$'; then
        echo "Invalid Bluetooth address in $CONFIG_BLUETOOTH_FILE: $MAC"
        exit 1
    fi

    # Forget the previous speaker when switching to a new one
    if [ -f "$SPEAKER_CONF" ]; then
        . "$SPEAKER_CONF"
        if [ -n "$SPEAKER_MAC" ] && [ "$SPEAKER_MAC" != "$MAC" ]; then
            echo "Removing previous speaker $SPEAKER_MAC"
            bluetoothctl remove "$SPEAKER_MAC"
        fi
    fi

    echo "Configuring Bluetooth speaker $MAC"
    echo "SPEAKER_MAC=$MAC" > "$SPEAKER_CONF"

    # Route the default ALSA device (used by squeezelite) to the speaker
    cat > "$ASOUND_CONF" << CONF
pcm.!default {
    type plug
    slave.pcm "bluealsa:DEV=$MAC,PROFILE=a2dp"
}
CONF

    rm -f "$CONFIG_BLUETOOTH_FILE"
fi

if [ ! -f "$SPEAKER_CONF" ]; then
    echo "No Bluetooth speaker configured. Exiting..."
    exit 0
fi

. "$SPEAKER_CONF"

while true; do
    if bluetoothctl show | grep -q "Powered: no"; then
        bluetoothctl power on
    fi

    INFO=$(bluetoothctl info "$SPEAKER_MAC" 2>/dev/null)

    if ! echo "$INFO" | grep -q "Paired: yes"; then
        echo "Pairing with $SPEAKER_MAC, make sure the speaker is in pairing mode..."
        bluetoothctl --timeout 15 scan on > /dev/null
        if bluetoothctl --agent NoInputNoOutput pair "$SPEAKER_MAC"; then
            bluetoothctl trust "$SPEAKER_MAC"
            bluetoothctl connect "$SPEAKER_MAC"
        fi
    elif ! echo "$INFO" | grep -q "Connected: yes"; then
        bluetoothctl connect "$SPEAKER_MAC" > /dev/null
    fi

    sleep 15
done
