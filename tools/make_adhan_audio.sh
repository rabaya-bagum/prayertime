#!/bin/sh
# Builds the bundled adhan audio from its Wikimedia Commons source. Licenses and
# provenance are recorded in docs/AUDIO_LICENSES.md; add a row there for any new reciter.
#
# For each reciter it writes:
#   ios/PrayerTime/Resources/Sounds/<id>_clip.caf    notification sound, under 30 s (iOS limit)
#   ios/PrayerTime/Resources/Sounds/<id>_adhan.m4a   full recording, played when a notification is opened
#   android/notifications/src/main/res/raw/<id>_clip.m4a, <id>_adhan.m4a
#
# Needs ffmpeg and macOS's afconvert. Usage: tools/make_adhan_audio.sh
set -eu

root=$(cd "$(dirname "$0")/.." && pwd)
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT

ios="$root/ios/PrayerTime/Resources/Sounds"
android="$root/android/notifications/src/main/res/raw"
mkdir -p "$ios" "$android"

# id | Commons file URL | SHA-1 | clip end (s): the end of the opening takbir
build() {
    id=$1 url=$2 sha1=$3 clip_end=$4
    src="$work/$id.source"
    curl -fsSL -A "PrayerTime build script (https://github.com/rabaya-bagum/prayertime)" -o "$src" "$url"
    echo "$sha1  $src" | shasum -a 1 -c - >/dev/null

    # Mono, loudness-normalized so reciters play at the same level.
    ffmpeg -v error -y -i "$src" -ac 1 -af loudnorm=I=-16:TP=-1.5 -ar 44100 "$work/$id.wav"

    fade_start=$(echo "$clip_end - 0.8" | bc)
    ffmpeg -v error -y -i "$work/$id.wav" -t "$clip_end" -af "afade=t=out:st=$fade_start:d=0.8" -ar 22050 "$work/${id}_clip.wav"
    afconvert -f caff -d ima4 "$work/${id}_clip.wav" "$ios/${id}_clip.caf"
    afconvert -f m4af -d aac -b 48000 "$work/${id}_clip.wav" "$android/${id}_clip.m4a"

    afconvert -f m4af -d aac -b 96000 "$work/$id.wav" "$ios/${id}_adhan.m4a"
    cp "$ios/${id}_adhan.m4a" "$android/${id}_adhan.m4a"
    echo "$id: clip $(afinfo "$ios/${id}_clip.caf" | awk '/estimated duration/ {print $3}') s"
}

build azeez \
    "https://upload.wikimedia.org/wikipedia/commons/7/7d/The_Adhan_-_Muslim_Call_to_Prayer_-_Aaqib_Azeez.mp3" \
    971bced4b0bf6067358afc78cac6e9d219226c19 \
    27.5
