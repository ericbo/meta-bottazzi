SUMMARY = "Nginx reverse proxy for subnet services"
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

require conf/distro/include/first-boot-config.inc

SRC_URI = " \
    file://nginx-proxy.conf.in \
    file://nginx.service.d/override.conf \
"

RDEPENDS:${PN} = "nginx first-boot-config"

python () {
    cert_file = d.getVar('FIRST_BOOT_CERT_FILE')
    key_file = d.getVar('FIRST_BOOT_KEY_FILE')
    
    if not cert_file or not key_file:
        bb.fatal("FIRST_BOOT_CERT_FILE and FIRST_BOOT_KEY_FILE must be set by first-boot-config recipe")
}

do_configure() {
    sed -e 's|@CERT_FILE@|${FIRST_BOOT_CERT_FILE}|g' \
        -e 's|@KEY_FILE@|${FIRST_BOOT_KEY_FILE}|g' \
        ${WORKDIR}/nginx-proxy.conf.in > ${WORKDIR}/nginx-proxy.conf
}

do_install() {
    install -d ${D}${sysconfdir}/nginx/sites-available
    install -d ${D}${sysconfdir}/nginx/sites-enabled

    install -m 0644 ${WORKDIR}/nginx-proxy.conf ${D}${sysconfdir}/nginx/sites-available/subnet-proxy

    ln -sf ../sites-available/subnet-proxy ${D}${sysconfdir}/nginx/sites-enabled/subnet-proxy

    # Install systemd override
    install -d ${D}${systemd_system_unitdir}/nginx.service.d
    install -m 0644 ${WORKDIR}/nginx.service.d/override.conf ${D}${systemd_system_unitdir}/nginx.service.d/
}

FILES:${PN} += " \
    ${sysconfdir}/nginx/sites-available/subnet-proxy \
    ${sysconfdir}/nginx/sites-enabled/subnet-proxy \
    ${systemd_system_unitdir}/nginx.service.d/override.conf \
"

pkg_postinst:${PN}() {
    if [ -z "$D" ]; then
        systemctl reload-or-restart nginx || true
    fi
}

