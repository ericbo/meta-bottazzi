SUMMARY = "Tailscale client and daemon from Tailscale's pre-built static binaries"
HOMEPAGE = "https://tailscale.com"
SECTION = "net"
LICENSE = "BSD-3-Clause"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/BSD-3-Clause;md5=550794465ba0ec5312d6919e203a55f9"

# Version to install. "latest" resolves the current release from
# TAILSCALE_BASE_URL every time recipes are parsed; set an explicit version
# (e.g. TAILSCALE_VERSION = "1.104.2") in local.conf to pin it.
TAILSCALE_VERSION ?= "latest"
TAILSCALE_BASE_URL ?= "https://pkgs.tailscale.com/stable"

# sha256 of the tarball. Fetched from TAILSCALE_BASE_URL alongside the version
# when empty; set it together with a pinned TAILSCALE_VERSION to parse offline.
TAILSCALE_SHA256 ?= ""

SRC_URI = "${TAILSCALE_BASE_URL}/tailscale_${PV}_${TAILSCALE_ARCH}.tgz"

S = "${WORKDIR}/tailscale_${PV}_${TAILSCALE_ARCH}"

# Re-parse on every run so "latest" picks up new releases.
BB_DONT_CACHE = "1"

python () {
    import json
    import urllib.request

    arches = {
        'aarch64': 'arm64',
        'arm': 'arm',
        'x86_64': 'amd64',
        'i586': '386',
        'i686': '386',
        'riscv64': 'riscv64',
    }
    arch = arches.get(d.getVar('TARGET_ARCH'))
    if not arch:
        raise bb.parse.SkipRecipe("Tailscale does not ship static binaries for %s" % d.getVar('TARGET_ARCH'))
    d.setVar('TAILSCALE_ARCH', arch)

    base_url = d.getVar('TAILSCALE_BASE_URL')
    version = d.getVar('TAILSCALE_VERSION')
    sha256 = d.getVar('TAILSCALE_SHA256')

    def fetch(url):
        if bb.utils.to_boolean(d.getVar('BB_NO_NETWORK')):
            raise bb.parse.SkipRecipe("BB_NO_NETWORK is set; pin TAILSCALE_VERSION and TAILSCALE_SHA256 to build offline")
        try:
            with urllib.request.urlopen(url, timeout=30) as response:
                return response.read().decode()
        except Exception as e:
            bb.fatal("Failed to fetch %s: %s" % (url, e))

    if version == 'latest':
        version = json.loads(fetch(base_url + '/?mode=json'))['TarballsVersion']
    d.setVar('PV', version)

    if not sha256:
        sha256 = fetch('%s/tailscale_%s_%s.tgz.sha256' % (base_url, version, arch)).split()[0]
    d.setVarFlag('SRC_URI', 'sha256sum', sha256)
}

inherit systemd

# Statically linked Go binaries: nothing to compile, strip or split.
INHIBIT_DEFAULT_DEPS = "1"
INHIBIT_PACKAGE_STRIP = "1"
INHIBIT_PACKAGE_DEBUG_SPLIT = "1"
INSANE_SKIP:${PN} = "already-stripped"

RDEPENDS:${PN} = "iptables"
RRECOMMENDS:${PN} = " \
    kernel-module-tun \
    kernel-module-xt-mark \
    kernel-module-xt-tcpudp \
    kernel-module-xt-masquerade \
"

SYSTEMD_SERVICE:${PN} = "tailscaled.service"
SYSTEMD_AUTO_ENABLE = "enable"

do_install() {
    install -d ${D}${bindir}
    install -m 0755 ${S}/tailscale ${D}${bindir}

    install -d ${D}${sbindir}
    install -m 0755 ${S}/tailscaled ${D}${sbindir}

    install -d ${D}${sysconfdir}/default
    install -m 0644 ${S}/systemd/tailscaled.defaults ${D}${sysconfdir}/default/tailscaled

    install -d ${D}${systemd_system_unitdir}
    install -m 0644 ${S}/systemd/tailscaled.service ${D}${systemd_system_unitdir}
    install -m 0644 ${S}/systemd/tailscale-wait-online.service ${D}${systemd_system_unitdir}
    install -m 0644 ${S}/systemd/tailscale-online.target ${D}${systemd_system_unitdir}
}

FILES:${PN} += " \
    ${systemd_system_unitdir}/tailscale-wait-online.service \
    ${systemd_system_unitdir}/tailscale-online.target \
"

CONFFILES:${PN} = "${sysconfdir}/default/tailscaled"
