SUMMARY = "Using a Tailscale auth token, connect to Tailnet on first boot"
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

SRC_URI = " \
    file://tailscale-connect.service \
    file://tailscale-connect.sh \
"

inherit systemd

SYSTEMD_SERVICE:${PN} = "tailscale-connect.service"
SYSTEMD_AUTO_ENABLE = "enable"

do_install() {
    install -d ${D}${systemd_system_unitdir}
    install -m 0644 ${WORKDIR}/tailscale-connect.service ${D}${systemd_system_unitdir}
    
    install -d ${D}${bindir}
    install -m 0755 ${WORKDIR}/tailscale-connect.sh ${D}${bindir}    
}
