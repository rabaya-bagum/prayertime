#!/bin/sh
# Builds the bundled adhan audio from its Wikimedia Commons source. Licenses and
# provenance are recorded in docs/AUDIO_LICENSES.md; add a row there for any new reciter.
#
# For each reciter it writes:
#   ios/PrayerTime/Resources/Sounds/<id>_clip.caf    notification sound, under 30 s (iOS limit)
#   ios/PrayerTime/Resources/Sounds/<id>_adhan.m4a   full recording, played when a notification is opened
#   android/notifications/src/main/res/raw/<id>_clip.m4a, <id>_adhan.m4a
#
# and, where a Fajr recording exists (Fajr adds "As-salatu khayrun min an-nawm",
# so the regular recording must never stand in for it):
#   ios/PrayerTime/Resources/Sounds/<id>_fajr_adhan.m4a
#   android/notifications/src/main/res/raw/<id>_fajr_adhan.m4a
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

# id | Commons file URL | SHA-1 | start (s), skipping anything before the first takbir
build_fajr() {
    id=$1 url=$2 sha1=$3 start=$4
    src="$work/${id}_fajr.source"
    curl -fsSL -A "PrayerTime build script (https://github.com/rabaya-bagum/prayertime)" -o "$src" "$url"
    echo "$sha1  $src" | shasum -a 1 -c - >/dev/null

    # The source can be a video; keep the audio only, normalized like the others.
    ffmpeg -v error -y -ss "$start" -i "$src" -vn -ac 1 -af loudnorm=I=-16:TP=-1.5 -ar 44100 "$work/${id}_fajr_full.wav"
    duration=$(ffprobe -v error -show_entries format=duration -of csv=p=0 "$work/${id}_fajr_full.wav")
    fade_start=$(echo "$duration - 0.8" | bc)
    ffmpeg -v error -y -i "$work/${id}_fajr_full.wav" -af "afade=t=out:st=$fade_start:d=0.8" "$work/${id}_fajr.wav"

    afconvert -f m4af -d aac -b 96000 "$work/${id}_fajr.wav" "$ios/${id}_fajr_adhan.m4a"
    cp "$ios/${id}_fajr_adhan.m4a" "$android/${id}_fajr_adhan.m4a"
    echo "$id: Fajr $(afinfo "$ios/${id}_fajr_adhan.m4a" | awk '/estimated duration/ {print $3}') s"
}

build azeez \
    "https://upload.wikimedia.org/wikipedia/commons/7/7d/The_Adhan_-_Muslim_Call_to_Prayer_-_Aaqib_Azeez.mp3" \
    971bced4b0bf6067358afc78cac6e9d219226c19 \
    27.5

# Aaqib Azeez has no Fajr recording on Commons; this is Besim Azemi at Malmö Mosque.
build_fajr azeez \
    "https://upload.wikimedia.org/wikipedia/commons/b/b1/Eid_al-Fitr_Fajr_azan_at_Malm%C3%B6_Mosque_-_19_August_2012.webm" \
    bc75d5cee271efb67f03d3f35474a9362fce103f \
    11.5
