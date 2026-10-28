SUMMARY = "Connect to a WiFi access point using the credentials in /boot/wifi.conf"
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

SRC_URI = " \
    file://wifi-config.service \
    file://wifi-config.sh \
    file://10-wlan0.link \
    file://30-wlan0.network \
    file://systemd-networkd-wait-online.service.d/any.conf.in \
"

inherit systemd features_check

REQUIRED_DISTRO_FEATURES = "systemd wifi"

SYSTEMD_SERVICE:${PN} = "wifi-config.service"
SYSTEMD_AUTO_ENABLE = "enable"

RDEPENDS:${PN} = "wpa-supplicant wpa-supplicant-passphrase"

# Driver and firmware for the Ralink RT5370 USB adapter, plus the ciphers
# mac80211 requests at runtime for WPA2 (CCMP), WPA3/PMF (GCMP, BIP-CMAC) and
# TKIP. Nothing depends on these modules directly, so list them explicitly.
RRECOMMENDS:${PN} = " \
    kernel-module-rt2800usb \
    linux-firmware-ralink \
    kernel-module-aes-generic \
    kernel-module-ccm \
    kernel-module-ctr \
    kernel-module-ecb \
    kernel-module-gcm \
    kernel-module-ghash-generic \
    kernel-module-seqiv \
    kernel-module-cmac \
    kernel-module-michael-mic \
"

do_configure() {
    sed -e 's|@SYSTEMD_UNITDIR@|${systemd_unitdir}|g' \
        ${WORKDIR}/systemd-networkd-wait-online.service.d/any.conf.in > ${WORKDIR}/any.conf
}

do_install() {
    install -d ${D}${systemd_system_unitdir}
    install -m 0644 ${WORKDIR}/wifi-config.service ${D}${systemd_system_unitdir}

    install -d ${D}${bindir}
    install -m 0755 ${WORKDIR}/wifi-config.sh ${D}${bindir}

    install -d ${D}${sysconfdir}/systemd/network
    install -m 0644 ${WORKDIR}/10-wlan0.link ${D}${sysconfdir}/systemd/network/
    install -m 0644 ${WORKDIR}/30-wlan0.network ${D}${sysconfdir}/systemd/network/

    install -d ${D}${systemd_system_unitdir}/systemd-networkd-wait-online.service.d
    install -m 0644 ${WORKDIR}/any.conf ${D}${systemd_system_unitdir}/systemd-networkd-wait-online.service.d/
}

FILES:${PN} += " \
    ${sysconfdir}/systemd/network/* \
    ${systemd_system_unitdir}/systemd-networkd-wait-online.service.d/any.conf \
"
