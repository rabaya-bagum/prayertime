# Shared test data

Both `android/core` and `ios/PrayerCore` run every file here, so the two apps
are held to the same numbers.

| Path | What it is | Tolerance |
|---|---|---|
| `adhan/` | Published official timetables from [adhan-testdata](https://github.com/batoulapps/adhan-testdata) (MIT). See `adhan/README.md`. | Per file (`variance`, default 0 min) |
| `reference-praytimes.json` | Times from [PrayTimes](https://praytimes.org), an implementation independent of Adhan, for cases the spec calls out: Toronto at 12°/15°/18°, Hanafi Asr, both DST changeover days, +5:30 and +5:45 zones, southern hemisphere, Umm al-Qura. | 2 min |

Regenerate the reference file with:

```sh
python3 -m venv .venv && .venv/bin/pip install praytimes==2.3.2
.venv/bin/python tools/gen_reference.py > shared/testdata/reference-praytimes.json
```

High-latitude, polar, date-line and Ramadan behaviour has no external reference
that agrees with our chosen rules, so it is covered by property tests in each
platform's suite (`PrayerCalculatorTest.kt` / `PrayerCalculatorTests.swift`).

Still to add before release: three or more Toronto/GTA masjid timetables (see
the launch checklist in `docs/SPEC.md`). Add them as `masjid/<name>-<year>.json`
using the adhan-testdata format, with the source recorded in `source`.
