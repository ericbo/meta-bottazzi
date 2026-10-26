SUMMARY = "Systemd timers for remote server power management"
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

inherit systemd

SRC_URI = " \
    file://ipmi-startup.service \
    file://ipmi-startup.timer \
    file://ipmi-shutdown.service \
    file://ipmi-shutdown.timer \
    file://ipmi-startup.sh \
    file://ipmi-shutdown.sh \
"

SYSTEMD_SERVICE:${PN} = "ipmi-startup.timer ipmi-shutdown.timer"
SYSTEMD_AUTO_ENABLE = "enable"

RDEPENDS:${PN} = "ipmitool bash systemd"

do_install() {
    install -d ${D}${systemd_system_unitdir}
    install -m 0644 ${WORKDIR}/ipmi-startup.service ${D}${systemd_system_unitdir}
    install -m 0644 ${WORKDIR}/ipmi-startup.timer ${D}${systemd_system_unitdir}
    install -m 0644 ${WORKDIR}/ipmi-shutdown.service ${D}${systemd_system_unitdir}
    install -m 0644 ${WORKDIR}/ipmi-shutdown.timer ${D}${systemd_system_unitdir}
    
    install -d ${D}${bindir}
    install -m 0755 ${WORKDIR}/ipmi-startup.sh ${D}${bindir}
    install -m 0755 ${WORKDIR}/ipmi-shutdown.sh ${D}${bindir}    
}

FILES:${PN} += "${systemd_system_unitdir}/*"
