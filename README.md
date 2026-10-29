# meta-bottazzi

Yocto (Scarthgap) layer for Bottazzi Raspberry Pi appliances. The layer provides
a common base image and one or more purpose-built images on top of it.

## Images

| Image | Description |
| --- | --- |
| `remote-backup-image` | Powers a backup server on and off over IPMI on a schedule, and exposes the server's IPMI web/KVM interface through an nginx reverse proxy over Tailscale. |
| `audio-player-image` | A squeezelite network audio player that streams music, TTS and chimes from Home Assistant via Music Assistant. It plays through the onboard audio or a Bluetooth speaker. |

All images share `recipes-core/images/bottazzi-image.inc`, which provides:

- **first-boot-config**: sets the hostname from `/boot/hostname.conf` and generates a self-signed TLS certificate (`/etc/ssl/certs/server.crt`, `/etc/ssl/private/server.key`).
- **network-dhcp**: a DHCP client on the onboard Ethernet (`eth0`).
- **wifi-config**: joins the Wi-Fi network in `/boot/wifi.conf` with a USB adapter (tested hardware: Ralink RT5370, `rt2800usb` driver). The adapter is always named `wlan0` and gets its address over DHCP. When both are connected, Ethernet takes priority.
- **tailscale** + **tailscale-connect**: joins the tailnet on first boot using the auth key in `/boot/tailscale.conf`, with Tailscale SSH enabled.
  The `tailscale` recipe installs Tailscale's official static binaries from [pkgs.tailscale.com](https://pkgs.tailscale.com/stable/#static), using the latest stable release by default (see [Tailscale version](#tailscale-version)).

### remote-backup-image

On top of the base, it adds:

- **network-config**: the `r8152` USB Ethernet adapter is `eth1`, with a static `192.168.69.1/24` address and a DHCP server that hands the backup server's BMC (`00:25:90:d9:45:ae`) the fixed address `192.168.69.102`.
- **ipmi-scheduler**: systemd timers that power the server on at 04:50 UTC and soft power it off at 06:00 UTC.
- **tailscale-proxy**: nginx on port 443 that proxies to `https://192.168.69.102`, with WebSocket support and long timeouts for KVM sessions.

### audio-player-image

On top of the base, it adds:

- **squeezelite**: a Slimproto player that runs as a systemd service, outputs through ALSA and uses the hostname as its player name. It supports FLAC, PCM, Ogg Vorbis and MP3.
- **alsa-utils** (`aplay`, `amixer`): for listing output devices and setting levels.
- **bluetooth-speaker** + **bluez-alsa**: pairs with the Bluetooth speaker in `/boot/bluetooth.conf`, reconnects whenever it drops, and makes it the default audio output. It streams A2DP with SBC XQ (452 kbps). Tested with the TP-Link UB500 (`2357:0604`, Realtek RTL8761BU).

## Setup

### 1. Fetch the layers

```sh
mkdir -p ~/yocto && cd ~/yocto
git clone -b scarthgap https://git.yoctoproject.org/poky
git clone -b scarthgap https://git.openembedded.org/meta-openembedded
git clone -b scarthgap https://git.yoctoproject.org/meta-raspberrypi
git clone <this repo URL> meta-bottazzi
```

### 2. Create the build directory and add the layers

```sh
cd ~/yocto
source poky/oe-init-build-env build

bitbake-layers add-layer ../meta-openembedded/meta-oe
bitbake-layers add-layer ../meta-openembedded/meta-webserver
bitbake-layers add-layer ../meta-raspberrypi
bitbake-layers add-layer ../meta-bottazzi
```

Add the layers before you set a Raspberry Pi `MACHINE` in `local.conf`. Until
meta-raspberrypi is in `bblayers.conf`, the sanity checker rejects the machine
as invalid, and that also makes `bitbake-layers` fail.

### 3. Configure `conf/local.conf`

Add the following, with `MACHINE` set to match your board (for example
`raspberrypi` for the original Pi 1 / 512 MB, `raspberrypi3-64`,
`raspberrypi4-64`, `raspberrypi5`):

```conf
MACHINE = "raspberrypi4-64"

# The layer's services are all systemd units (networkd, resolved, timers)
INIT_MANAGER = "systemd"
```

The image recipes define all of the packages, so you don't need
`IMAGE_INSTALL:append` lines in `local.conf`. Remove any left over from
earlier builds, or those packages will end up in every image.

#### Tailscale version

By default, the `tailscale` recipe looks up the latest stable release and its
sha256 on pkgs.tailscale.com every time bitbake parses recipes. A new
Tailscale release is picked up automatically on the next build. Parsing needs
network access for this.

To pin a version, for reproducible builds:

```conf
TAILSCALE_VERSION = "1.104.2"
```

To build without network access at parse time, also pin the checksum. Take it
from `https://pkgs.tailscale.com/stable/tailscale_<version>_<arch>.tgz.sha256`,
where `<arch>` is `arm64` for 64-bit machines and `arm` for 32-bit ones:

```conf
TAILSCALE_SHA256 = "5776aaa901b5ef1916e66be5bb54b9f481cd399c9c28db123a8cab620efa237b"
```

Optional, for development only:

```conf
# Allow root login on the serial/HDMI console without a password
EXTRA_IMAGE_FEATURES += "empty-root-password allow-root-login"
ENABLE_UART = "1"
```

### 4. Build

```sh
bitbake remote-backup-image
# or
bitbake audio-player-image
```

The images are written to `tmp/deploy/images/<MACHINE>/`. The file you flash is
`<image>-<MACHINE>.rootfs.wic.bz2`. It comes with a matching `.wic.bmap` file.

## Flashing

Insert the SD card and find its device (for example `/dev/sdX` or
`/dev/mmcblk0`) with `lsblk`. **Make sure it's the right device, because
everything on it will be overwritten.** Unmount any partitions that were
mounted automatically.

Using `bmaptool` (faster, since it only writes used blocks):

```sh
cd tmp/deploy/images/<MACHINE>
sudo bmaptool copy <image>-<MACHINE>.rootfs.wic.bz2 /dev/sdX
```

Or using `dd`:

```sh
bzcat <image>-<MACHINE>.rootfs.wic.bz2 | sudo dd of=/dev/sdX bs=4M status=progress conv=fsync
```

## First boot configuration

Before the first boot, mount the SD card's FAT `boot` partition (it's mounted
at `/boot` on the device) and add the provisioning files. The first-boot
services read each file once and then delete it.

| File | Contents | Used by |
| --- | --- | --- |
| `hostname.conf` | The device hostname on one line, e.g. `raspberrypi-ipmi-02` | first-boot-config |
| `tailscale.conf` | A Tailscale auth key on one line, e.g. `tskey-auth-...` | tailscale-connect |
| `wifi.conf` | Optional. The Wi-Fi SSID on line 1 and the passphrase on line 2. Leave line 2 empty for an open network. | wifi-config |
| `bluetooth.conf` | audio-player-image only, optional. The speaker's Bluetooth address on one line, e.g. `AA:BB:CC:DD:EE:FF`. | bluetooth-speaker |

```sh
sudo mount /dev/sdX1 /mnt
echo "raspberrypi-ipmi-02" | sudo tee /mnt/hostname.conf
echo "tskey-auth-XXXXXXXX" | sudo tee /mnt/tailscale.conf
printf '%s\n%s\n' "My SSID" "my passphrase" | sudo tee /mnt/wifi.conf
sudo umount /mnt
```

Without `hostname.conf`, the device keeps its default hostname. Either way,
the TLS certificate is generated for whatever the hostname is at that point.
Generate the auth key from the Tailscale admin console. Use a pre-approved key
if device approval is enabled on your tailnet.

`wifi.conf` is read on every boot, not just the first. To move the device to
another network, put a new `wifi.conf` on the boot partition and reboot. The
passphrase is stored on the device as a hashed PSK in
`/etc/wpa_supplicant/wpa_supplicant-wlan0.conf`. The device counts as online
when either Ethernet or Wi-Fi is up. This means a Wi-Fi-only device doesn't
wait for the unplugged Ethernet port before services such as tailscale-connect
start.

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

### audio-player-image: Home Assistant

Squeezelite works with Home Assistant through
[Music Assistant](https://music-assistant.io/):

1. In Music Assistant, enable the **Squeezelite** (Slimproto) player provider.
2. Boot the device on the same subnet. Squeezelite finds the Music Assistant
   server automatically and shows up as a player named after the device's
   hostname.
3. The player then appears in Home Assistant as a `media_player` entity. You
   can use it for music, TTS and announcements/chimes
   (`music_assistant.play_announcement`).

To change the player's options, connect with `tailscale ssh root@<hostname>`,
edit `/etc/default/squeezelite` and run `systemctl restart squeezelite`. The
options you'll most likely need:

| Option | Purpose |
| --- | --- |
| `-o <device>` | ALSA output device. List the devices with `squeezelite -l` or `aplay -L`. Defaults to `default` (card 0). |
| `-s <server>` | Music Assistant server address. Use this if auto-discovery doesn't work, for example when the server is on a different subnet or VLAN. |
| `-V <control>` | Hardware mixer control for volume (see `amixer scontrols`), e.g. `-V PCM`. |
| `-n <name>` | Player name. Defaults to the hostname. |

The onboard audio is turned on by default (`dtparam=audio=on`). To use a DAC
HAT, add its overlay to `config.txt` (for example, in `local.conf` add
`RPI_EXTRA_CONFIG = "dtoverlay=hifiberry-dacplus"`). Then select the HAT's
device with `-o`.

### audio-player-image: Bluetooth speaker

1. Find the speaker's Bluetooth address. It's usually shown in your phone's
   Bluetooth settings after you pair with it once. You can also find it on the
   Pi: put the speaker in pairing mode and run
   `bluetoothctl --timeout 20 scan on`.
2. Put the address in `bluetooth.conf` on the boot partition, then reboot:

   ```sh
   echo "AA:BB:CC:DD:EE:FF" | sudo tee /mnt/bluetooth.conf
   ```

3. Put the speaker in pairing mode. Every 15 seconds the Pi tries to pair until
   it succeeds, then marks the speaker as trusted. After that it reconnects
   whenever the speaker is on and in range.

Once paired, `/etc/asound.conf` points the default ALSA device at the speaker,
so squeezelite needs no `-o` option. Leave `-C` unset in
`/etc/default/squeezelite`. Holding the stream open keeps chimes from getting
their first second cut off while the Bluetooth link wakes up. If the speaker is
off, squeezelite retries the output every few seconds and resumes once it
reconnects.

To switch speakers, put the new address in `bluetooth.conf` and reboot. The old
speaker is forgotten. Follow the pairing log with
`journalctl -fu bluetooth-speaker`.

