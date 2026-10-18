# Audio license register

Every bundled recording, where it came from and on what terms (SPEC: "keep a
license register in the repo"). The files are built by
`tools/make_adhan_audio.sh`, which downloads each source, checks its SHA-1,
and writes the iOS and Android files. Add a row here and a `build` line there
for each new reciter, and an entry in `AdhanSound.all` (iOS) and
`BundledAdhans` (Android).

| Sound id | Reciter | Source | Author | License | SHA-1 of source |
|---|---|---|---|---|---|
| `azeez` | Aaqib Azeez | [Wikimedia Commons](https://commons.wikimedia.org/wiki/File:The_Adhan_-_Muslim_Call_to_Prayer_-_Aaqib_Azeez.mp3) | [Atcovi](https://commons.wikimedia.org/wiki/User:Atcovi), "own work", 2020-12-23 | [CC BY-SA 4.0](https://creativecommons.org/licenses/by-sa/4.0/) | `971bced4b0bf6067358afc78cac6e9d219226c19` |
| `azeez` (Fajr) | Besim Azemi | [Wikimedia Commons](https://commons.wikimedia.org/wiki/File:Eid_al-Fitr_Fajr_azan_at_Malm%C3%B6_Mosque_-_19_August_2012.webm), from the mosque's [YouTube channel](https://www.youtube.com/watch?v=0IHfm4Y82xA) | Islamic Center Malmö, 2012-08-19 (Eid al-Fitr Fajr) | [CC BY 3.0](https://creativecommons.org/licenses/by/3.0/), license reviewed on Commons | `bc75d5cee271efb67f03d3f35474a9362fce103f` |

## Fajr

The Fajr adhan adds "As-salatu khayrun min an-nawm" twice after "Hayya 'ala
al-falah", so a regular recording must never play at Fajr. Each sound id can
have a Fajr recording, `<id>_fajr_adhan.m4a`; if it has none, Fajr plays only
the takbir clip, which is the same in every adhan. `AdhanSoundTests` and
`BundledAdhans` enforce this. Aaqib Azeez has no Fajr recording on Commons, so
`azeez` uses Besim Azemi's for Fajr, and the app credits him when it plays.
Replace it with the same reciter's Fajr adhan if one becomes available.

## What we changed

From each source: downmixed to mono and loudness-normalized (−16 LUFS), then
- `<id>_clip.caf` / `<id>_clip.m4a`: the opening takbir only (first 27.5 s for
  `azeez`), with a 0.8 s fade-out. iOS notification sounds must be under 30 s.
- `<id>_adhan.m4a`: the full recording, re-encoded as AAC.
- `<id>_fajr_adhan.m4a`: the Fajr recording's audio track from 11.5 s to
  235 s, with a 0.8 s fade-out. Before 11.5 s is the room before the first
  takbir; from 241 s the muezzin recites a du'a after the adhan, which is left
  out. The adhan's last line fades out by 234.5 s.

These are adaptations, so under CC BY-SA they're shared under CC BY-SA 4.0
too. That covers the audio files only. The app's code stays MIT; bundling
the files is a collection, not an adaptation of the code.

## Attribution

The About screen credits each recording with its reciter, author, license and
a link to the source (`AdhanSound` on iOS). The Android app must show the same.

## Checked before release

- [ ] **Performer consent for `azeez`.** The uploader states the file is their
      own work and names the reciter. Confirm with the uploader that the
      reciter agreed to the CC BY-SA release, or replace it with a recording
      made under a signed release.
- [ ] **Listen to the Fajr recording** and confirm it includes "As-salatu
      khayrun min an-nawm". Its phrase structure (14 sung phrases with the
      takbirs paired) fits the Fajr adhan, but this was checked from levels,
      not by ear.
- [ ] **App Store and CC BY-SA.** CC BY-SA 4.0 forbids adding technical
      measures that restrict the licensed material. The audio is shipped as
      plain files in the bundle, but have this reviewed before release.

Rejected while searching Commons (October 2026):
- "Call to prayer by Sabah Fakhry.mp3": tagged public domain on the grounds
  that the adhan dates from 622 CE, but that covers the text, not this 1985
  performance, which was taken from YouTube.
- Field recordings ("Athan in Ramadan", "Athan during Ramadan 2", "Llamada a
  oración Mezquita Hassan II"): partial, or distant with traffic, wind and
  conversation.
