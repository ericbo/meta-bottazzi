SUMMARY = "Audio player image: streams music and chimes from Home Assistant (Music Assistant) via squeezelite"

require bottazzi-image.inc

IMAGE_INSTALL += " \
    squeezelite \
    bluetooth-speaker \
    alsa-utils-aplay \
    alsa-utils-amixer \
"
