#!/usr/bin/env python3
"""Generate the offline city database bundled with the apps.

Source: GeoNames (geonames.org, CC BY 4.0; credited in the app's About screen).
Every city with population >= 15,000, each with its own IANA time zone, so a
manually chosen city resolves DST correctly and a GPS fix can borrow the zone
of the nearest city.

Output is tab-separated, one city per line, most populous first so search
results rank sensibly without an index:

    name  region  country  countryCode  latitude  longitude  timeZone  population  asciiName

asciiName is empty when it equals name. Search needs it because letters such as
ø, ł and ı are distinct letters, not accented ones, so folding alone can't match
"tromso" to "Tromsø".

Usage (inputs from https://download.geonames.org/export/dump/):
    python3 tools/gen_cities.py cities15000.txt admin1CodesASCII.txt countryInfo.txt \
        > ios/PrayerTime/Resources/cities.tsv
"""
import sys


def read_tsv(path):
    with open(path, encoding="utf-8") as f:
        for line in f:
            if line.startswith("#") or not line.strip():
                continue
            yield line.rstrip("\n").split("\t")


def main(cities_path, admin1_path, countries_path):
    regions = {row[0]: row[1] for row in read_tsv(admin1_path)}
    countries = {row[0]: row[4] for row in read_tsv(countries_path)}

    rows = []
    for c in read_tsv(cities_path):
        name, ascii, lat, lon, cc, admin1, population, tz = c[1], c[2], c[4], c[5], c[8], c[10], c[14], c[17]
        if not tz:
            continue
        rows.append((
            int(population or 0),
            [name, regions.get(f"{cc}.{admin1}", ""), countries.get(cc, cc), cc,
             f"{float(lat):.4f}", f"{float(lon):.4f}", tz, population or "0",
             "" if ascii == name else ascii],
        ))

    rows.sort(key=lambda r: (-r[0], r[1][0]))
    out = sys.stdout
    for _, fields in rows:
        assert not any("\t" in f or "\n" in f for f in fields)
        out.write("\t".join(fields) + "\n")


if __name__ == "__main__":
    if len(sys.argv) != 4:
        sys.exit(__doc__)
    main(*sys.argv[1:])
