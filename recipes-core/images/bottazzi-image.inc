# Common base for all meta-bottazzi images.
#
# Provides first boot provisioning (hostname + self signed certificate) and
# Tailscale connectivity. Images require this file and append their own
# features and services to IMAGE_INSTALL.

LICENSE = "MIT"

inherit core-image features_check

REQUIRED_DISTRO_FEATURES = "systemd"

IMAGE_INSTALL = " \
    packagegroup-core-boot \
    ${CORE_IMAGE_EXTRA_INSTALL} \
    first-boot-config \
    tailscale \
    tailscale-connect \
"
