SUMMARY = "Lightweight headless Squeezebox (Slimproto) player"
HOMEPAGE = "https://github.com/ralph-irving/squeezelite"
LICENSE = "GPL-3.0-or-later"
LIC_FILES_CHKSUM = "file://LICENSE.txt;md5=013fe310a8f662351e6b109cc615fe9d"

SRC_URI = " \
    git://github.com/ralph-irving/squeezelite.git;protocol=https;branch=master \
    file://squeezelite.service \
    file://squeezelite.default \
"
SRCREV = "ab20df3dff76122b9e4f426cffd0346e752b93da"
PV = "2.0.0.1609+git"

S = "${WORKDIR}/git"

inherit systemd features_check useradd

REQUIRED_DISTRO_FEATURES = "alsa systemd"

DEPENDS = "alsa-lib flac libogg libvorbis mpg123"

# Link codecs directly instead of dlopen() so their runtime dependencies are
# picked up automatically. FLAC, PCM, Vorbis and MP3 (mpg123) are enough for
# Music Assistant / Lyrion streams; AAC (faad) and libmad are disabled.
EXTRA_OEMAKE = "OPTS='-DLINKALL -DNO_FAAD -DNO_MAD'"

# A fixed user rather than DynamicUser: dbus-daemon drops connections from
# dynamic users here, which breaks the BlueALSA output.
USERADD_PACKAGES = "${PN}"
USERADD_PARAM:${PN} = "--system --home-dir /nonexistent --no-create-home --shell /bin/false --groups audio --user-group squeezelite"

SYSTEMD_SERVICE:${PN} = "squeezelite.service"
SYSTEMD_AUTO_ENABLE = "enable"

do_install() {
    install -d ${D}${bindir}
    install -m 0755 ${S}/squeezelite ${D}${bindir}

    install -d ${D}${systemd_system_unitdir}
    install -m 0644 ${WORKDIR}/squeezelite.service ${D}${systemd_system_unitdir}

    install -d ${D}${sysconfdir}/default
    install -m 0644 ${WORKDIR}/squeezelite.default ${D}${sysconfdir}/default/squeezelite
}

CONFFILES:${PN} = "${sysconfdir}/default/squeezelite"
