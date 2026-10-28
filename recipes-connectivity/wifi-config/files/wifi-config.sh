#!/bin/sh

CONFIG_WIFI_FILE="/boot/wifi.conf"
WPA_CONF="/etc/wpa_supplicant/wpa_supplicant-wlan0.conf"

if [ ! -f "$CONFIG_WIFI_FILE" ]; then
    echo "No WiFi config detected. Exiting..."
    exit 0
fi

echo "$CONFIG_WIFI_FILE detected!"

# Line 1: SSID, line 2: passphrase (empty for an open network)
SSID=$(sed -n 1p "$CONFIG_WIFI_FILE" | tr -d '\r')
PASSPHRASE=$(sed -n 2p "$CONFIG_WIFI_FILE" | tr -d '\r')

if [ -z "$SSID" ]; then
    echo "No SSID found in $CONFIG_WIFI_FILE"
    exit 1
fi

if [ -n "$PASSPHRASE" ]; then
    # Store the derived PSK rather than the plaintext passphrase
    if ! NETWORK=$(wpa_passphrase "$SSID" "$PASSPHRASE"); then
        echo "Invalid passphrase: $NETWORK"
        exit 1
    fi
    NETWORK=$(echo "$NETWORK" | sed '/^[[:space:]]*#psk=/d')
else
    NETWORK=$(printf 'network={\n\tssid="%s"\n\tkey_mgmt=NONE\n}' "$SSID")
fi

echo "Configuring WiFi for SSID $SSID"
mkdir -p "$(dirname "$WPA_CONF")"
umask 077
cat > "$WPA_CONF" << CONF
ctrl_interface=/run/wpa_supplicant

$NETWORK
CONF

rm -f "$CONFIG_WIFI_FILE"

# Enable on future boots and (re)start now with the new configuration
systemctl enable wpa_supplicant@wlan0.service
systemctl restart --no-block wpa_supplicant@wlan0.service

echo "Done!"
