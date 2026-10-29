SUMMARY = "Bluetooth audio ALSA backend (BlueALSA)"
HOMEPAGE = "https://github.com/arkq/bluez-alsa"
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://LICENSE;md5=8e621f51a0d6b9221389a66da5118183"

SRC_URI = " \
    git://github.com/arkq/bluez-alsa.git;protocol=https;branch=master \
    file://org.bluealsa-root.conf \
"
SRCREV = "1935d6dcb8975f2d7a51aaafe61538d157224623"

S = "${WORKDIR}/git"

inherit autotools pkgconfig python3native systemd useradd features_check

REQUIRED_DISTRO_FEATURES = "bluetooth systemd"

# gdbus-codegen (glib-2.0-native) imports the packaging module
DEPENDS = "alsa-lib bluez5 dbus glib-2.0 glib-2.0-native python3-packaging-native sbc systemd"

# Only the A2DP source role is needed (playing to a Bluetooth speaker). SBC XQ
# (Dual Channel HD, 452 kbps) is noticeably better than the default "high"
# (345 kbps) and is cheap enough to encode on a Pi 1.
EXTRA_OECONF = " \
    --enable-systemd \
    --disable-aplay \
    --with-alsaplugindir=${libdir}/alsa-lib \
    --with-alsaconfdir=${sysconfdir}/alsa/conf.d \
    --with-dbusconfdir=${datadir}/dbus-1/system.d \
    --with-dbus-iface-xml=${datadir}/dbus-1/interfaces \
    --with-systemdsystemunitdir=${systemd_system_unitdir} \
    --with-bluealsaduser=bluealsa \
    --with-systemdbluealsadargs='-S -p a2dp-source --sbc-quality=xq' \
"

USERADD_PACKAGES = "${PN}"
USERADD_PARAM:${PN} = "--system --home-dir /var/lib/bluealsa --no-create-home --shell /bin/false --user-group bluealsa"

SYSTEMD_SERVICE:${PN} = "bluealsa.service"
SYSTEMD_AUTO_ENABLE = "enable"

RDEPENDS:${PN} = "bluez5"

# The generated D-Bus sources are written to src/dbus/ in the build directory,
# which upstream's Makefile never creates for out-of-tree builds.
do_compile:prepend() {
    mkdir -p ${B}/src/dbus
}

do_install:append() {
    rm -f ${D}${libdir}/alsa-lib/*.la

    # Installed even with --disable-aplay, but bluealsa-aplay isn't built
    rm -f ${D}${systemd_system_unitdir}/bluealsa-aplay.service

    install -m 0644 ${WORKDIR}/org.bluealsa-root.conf ${D}${datadir}/dbus-1/system.d/
}

FILES:${PN} += " \
    ${libdir}/alsa-lib/*.so \
    ${datadir}/alsa \
    ${datadir}/dbus-1 \
"
