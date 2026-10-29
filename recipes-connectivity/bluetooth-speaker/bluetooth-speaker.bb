SUMMARY = "Pair with the Bluetooth speaker in /boot/bluetooth.conf, keep it connected and use it as the default audio output"
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

SRC_URI = " \
    file://bluetooth-speaker.service \
    file://bluetooth-speaker.sh \
"

inherit systemd features_check

REQUIRED_DISTRO_FEATURES = "bluetooth systemd"

SYSTEMD_SERVICE:${PN} = "bluetooth-speaker.service"
SYSTEMD_AUTO_ENABLE = "enable"

RDEPENDS:${PN} = "bluez5 bluez-alsa"

# Driver and firmware for the Realtek RTL8761BU (TP-Link UB500) USB adapter,
# plus the ECDH implementation the Bluetooth stack requests at runtime for
# Secure Simple Pairing. Nothing depends on these modules directly, so list
# them explicitly.
RRECOMMENDS:${PN} = " \
    kernel-module-btusb \
    kernel-module-btrtl \
    linux-firmware-rtl8761 \
    kernel-module-ecdh-generic \
    kernel-module-ecc \
"

do_install() {
    install -d ${D}${systemd_system_unitdir}
    install -m 0644 ${WORKDIR}/bluetooth-speaker.service ${D}${systemd_system_unitdir}

    install -d ${D}${bindir}
    install -m 0755 ${WORKDIR}/bluetooth-speaker.sh ${D}${bindir}
}
