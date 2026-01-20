#!/bin/sh

CONFIG_TAILSCALE_FILE="/boot/tailscale.conf"
RESOLVED_CONF="/etc/systemd/resolved.conf.d/tailscale.conf"

echo "First boot detected, initializing system...."

if [ ! -f "$CONFIG_TAILSCALE_FILE" ]; then
    echo "No Tailscale config detected. Existing..."
    exit 0
fi

echo "$CONFIG_TAILSCALE_FILE detected!"

AUTH_KEY=$(head -n 1 "$CONFIG_TAILSCALE_FILE" | tr -d '[:space:]')

# Connect using auth key
echo "Connecting to Tailscale..."
tailscale up --ssh --authkey="$AUTH_KEY"

# Verify connection
if tailscale status &> /dev/null; then
    echo "Successfully connected to Tailscale"
    tailscale status
    rm "$CONFIG_TAILSCALE_FILE"
else
    echo "Failed to connect to Tailscale"
    exit 1
fi

# Configure Resolved
mkdir -p "$(dirname "$RESOLVED_CONF")"

cat > "$RESOLVED_CONF" << 'EOF'
[Resolve]
DNSStubListener=yes
EOF

# Fix resolv.conf symlink
rm -f /etc/resolv.conf
ln -sf /run/systemd/resolve/stub-resolv.conf /etc/resolv.conf

# Restart systemd-resolved
systemctl restart systemd-resolved

# Disable service
echo "Disabling tailscale connect service..."
systemctl disable tailscale-connect.service

echo "Done!"
