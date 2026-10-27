SUMMARY = "Remote backup image: schedules IPMI power on/off of a backup server and proxies its web interface over Tailscale"

require bottazzi-image.inc

IMAGE_INSTALL += " \
    network-config \
    ipmi-scheduler \
    tailscale-proxy \
"
