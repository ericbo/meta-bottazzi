SUMMARY = "Apply configuration from config files and generate self signed certificates on first boot"
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

require conf/distro/include/first-boot-config.inc

RDEPENDS:${PN} = "openssl-bin"

SRC_URI = " \
    file://first-boot-config.service \
    file://first-boot-config.sh.in \
"

inherit systemd

SYSTEMD_SERVICE:${PN} = "first-boot-config.service"
SYSTEMD_AUTO_ENABLE = "enable"

do_install() {
    install -d ${D}${systemd_system_unitdir}
    install -m 0644 ${WORKDIR}/first-boot-config.service ${D}${systemd_system_unitdir}
    
    install -d ${D}${bindir}
    install -m 0755 ${WORKDIR}/first-boot-config.sh ${D}${bindir}    
}

do_install:prepend() {
    sed -e 's|@FIRST_BOOT_CERT_FILE@|${FIRST_BOOT_CERT_FILE}|g' \
        -e 's|@FIRST_BOOT_KEY_FILE@|${FIRST_BOOT_KEY_FILE}|g' \
        ${WORKDIR}/first-boot-config.sh.in > ${WORKDIR}/first-boot-config.sh
}
