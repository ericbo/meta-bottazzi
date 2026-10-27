# meta-bottazzi

Yocto (Scarthgap) layer for Bottazzi Raspberry Pi appliances. The layer provides
a common base image and one or more purpose-built images on top of it.

## Images

| Image | Description |
| --- | --- |
| `remote-backup-image` | Powers a backup server on and off over IPMI on a schedule, and exposes the server's IPMI web/KVM interface through an nginx reverse proxy over Tailscale. |

All images share `recipes-core/images/bottazzi-image.inc`, which provides:

- **first-boot-config**: sets the hostname from `/boot/hostname.conf` and generates a self-signed TLS certificate (`/etc/ssl/certs/server.crt`, `/etc/ssl/private/server.key`).
- **tailscale** + **tailscale-connect**: joins the tailnet on first boot using the auth key in `/boot/tailscale.conf`, with Tailscale SSH enabled.

### remote-backup-image

On top of the base, it adds:

- **network-config**: `eth0` (onboard) is a DHCP client for the uplink. The `r8152` USB Ethernet adapter is `eth1`, with a static `192.168.69.1/24` address and a DHCP server that hands the backup server's BMC (`00:25:90:d9:45:ae`) the fixed address `192.168.69.102`.
- **ipmi-scheduler**: systemd timers that power the server on at 04:50 UTC and soft power it off at 06:00 UTC.
- **tailscale-proxy**: nginx on port 443 that proxies to `https://192.168.69.102`, with WebSocket support and long timeouts for KVM sessions.

## Setup

### 1. Fetch the layers

```sh
mkdir -p ~/yocto && cd ~/yocto
git clone -b scarthgap https://git.yoctoproject.org/poky
git clone -b scarthgap https://git.openembedded.org/meta-openembedded
git clone -b scarthgap https://git.yoctoproject.org/meta-raspberrypi
git clone <meta-tailscale repo URL> meta-tailscale
git clone <this repo URL> meta-bottazzi
```

### 2. Create the build directory and add the layers

```sh
cd ~/yocto
source poky/oe-init-build-env build

bitbake-layers add-layer ../meta-openembedded/meta-oe
bitbake-layers add-layer ../meta-openembedded/meta-webserver
bitbake-layers add-layer ../meta-raspberrypi
bitbake-layers add-layer ../meta-tailscale
bitbake-layers add-layer ../meta-bottazzi
```

### 3. Configure `conf/local.conf`

Add the following, with `MACHINE` set to match your board (for example
`raspberrypi4-64`, `raspberrypi5`, `raspberrypi3-64`):

```conf
MACHINE = "raspberrypi4-64"

# The layer's services are all systemd units (networkd, resolved, timers)
INIT_MANAGER = "systemd"
```

The image recipes define all of the packages, so you don't need
`IMAGE_INSTALL:append` lines in `local.conf`. Remove any left over from
earlier builds, or those packages will end up in every image.

Optional, for development only:

```conf
# Allow root login on the serial/HDMI console without a password
EXTRA_IMAGE_FEATURES += "empty-root-password allow-root-login"
ENABLE_UART = "1"
```

### 4. Build

```sh
bitbake remote-backup-image
```

The images are written to `tmp/deploy/images/<MACHINE>/`. The file you flash is
`remote-backup-image-<MACHINE>.rootfs.wic.bz2`. It comes with a matching
`.wic.bmap` file.

## Flashing

Insert the SD card and find its device (for example `/dev/sdX` or
`/dev/mmcblk0`) with `lsblk`. **Make sure it's the right device, because
everything on it will be overwritten.** Unmount any partitions that were
mounted automatically.

Using `bmaptool` (faster, since it only writes used blocks):

```sh
cd tmp/deploy/images/<MACHINE>
sudo bmaptool copy remote-backup-image-<MACHINE>.rootfs.wic.bz2 /dev/sdX
```

Or using `dd`:

```sh
bzcat remote-backup-image-<MACHINE>.rootfs.wic.bz2 | sudo dd of=/dev/sdX bs=4M status=progress conv=fsync
```

## First boot configuration

Before the first boot, mount the SD card's FAT `boot` partition (it's mounted
at `/boot` on the device) and add the provisioning files. The first-boot
services read each file once and then delete it.

| File | Contents | Used by |
| --- | --- | --- |
| `hostname.conf` | The device hostname on one line, e.g. `raspberrypi-ipmi-02` | first-boot-config |
| `tailscale.conf` | A Tailscale auth key on one line, e.g. `tskey-auth-...` | tailscale-connect |

```sh
sudo mount /dev/sdX1 /mnt
echo "raspberrypi-ipmi-02" | sudo tee /mnt/hostname.conf
echo "tskey-auth-XXXXXXXX" | sudo tee /mnt/tailscale.conf
sudo umount /mnt
```

Without `hostname.conf`, the device keeps its default hostname. Either way,
the TLS certificate is generated for whatever the hostname is at that point.
Generate the auth key from the Tailscale admin console. Use a pre-approved key
if device approval is enabled on your tailnet.

### remote-backup-image: IPMI credentials

The IPMI scripts read `/etc/ipmi.conf`, which the image does not provide. After
the device joins the tailnet, connect with `tailscale ssh root@<hostname>` and
create the file:

```sh
cat > /etc/ipmi.conf << 'EOF'
IPMI_HOST=192.168.69.102
IPMI_USER=ADMIN
IPMI_PASS=changeme
EOF
chmod 600 /etc/ipmi.conf
```

Test it with `systemctl start ipmi-startup.service`, then check
`systemctl list-timers ipmi-*` to confirm the schedule.
