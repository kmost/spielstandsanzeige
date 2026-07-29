#!/bin/sh
# Erzeugt das App-Logo (LED-Anzeigetafel: gelbe Dot-Matrix-Ziffern „7:6“ auf
# blauer Kachel) vollständig aus ImageMagick-Zeichenbefehlen — das Skript ist
# die Quelle des Logos.
#
# Ausgaben:
#   app-icon-512.png        Detailfassung (Dot-Matrix „7:6“), Master
#   app-icon-klein-512.png  vereinfachte Fassung (Score-Blöcke + Doppelpunkt)
#   Spielstandsanzeige.ico  Multi-Size-Icon für jpackage (16–32 px vereinfacht)
#   ../src/main/resources/de/kmost/scoreboard/app-icon.png        (256 px)
#   ../src/main/resources/de/kmost/scoreboard/app-icon-small.png  (32 px)
set -eu
cd "$(dirname "$0")"

BLAU_HELL='#1e46a8'
BLAU_DUNKEL='#0c1f52'
BLAU_LED_AUS='#17315e'
GELB='#ffd54a'
TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT

# Kachel: blauer Verlauf, als abgerundetes Quadrat maskiert
magick -size 512x512 gradient:"$BLAU_HELL"-"$BLAU_DUNKEL" \
  \( -size 512x512 xc:none -fill white -draw "roundrectangle 8,8 503,503 112,112" \) \
  -alpha off -compose CopyOpacity -composite "$TMP/tile.png"

# Dot-Matrix „7:6“ als 9×5-Bitmap (3 Spalten je Ziffer, 1 Spalte Doppelpunkt,
# je 1 Leerspalte); Raster 44 px, zentriert. Unbeleuchtete Dots bleiben als
# dunkle Punkte sichtbar — wie bei einer echten LED-Tafel.
BITMAP='111000111 001010100 001000111 001010101 001000111'
dots() { # $1 = Dot-Radius, $2 = "1" für beleuchtete Dots, "" für alle
  awk -v rows="$BITMAP" -v radius="$1" -v only_lit="$2" 'BEGIN {
    split(rows, a, " ")
    for (r = 1; r <= 5; r++)
      for (c = 1; c <= 9; c++) {
        if (only_lit == "1" && substr(a[r], c, 1) != "1") continue
        x = 80 + (c - 1) * 44; y = 168 + (r - 1) * 44
        printf "circle %d,%d %d,%d ", x, y, x + radius, y
      }
  }'
}
magick "$TMP/tile.png" \
  -fill "$BLAU_LED_AUS" -draw "$(dots 9 '')" \
  -fill "$GELB" -draw "$(dots 17 1)" app-icon-512.png

# Vereinfachte Fassung für kleine Größen: zwei Score-Blöcke mit Doppelpunkt
magick "$TMP/tile.png" -fill "$GELB" \
  -draw "roundrectangle 96,180 218,332 24,24" \
  -draw "roundrectangle 294,180 416,332 24,24" \
  -draw "circle 256,225 256,241" \
  -draw "circle 256,287 256,303" app-icon-klein-512.png

# Fenster-Icons für die App
magick app-icon-512.png -resize 256x256 ../src/main/resources/de/kmost/scoreboard/app-icon.png
magick app-icon-klein-512.png -resize 32x32 ../src/main/resources/de/kmost/scoreboard/app-icon-small.png

# Windows-ICO: große Größen aus der Detailfassung, kleine aus der vereinfachten
for s in 256 128 64 48; do magick app-icon-512.png -resize ${s}x${s} "$TMP/d$s.png"; done
for s in 32 24 16; do magick app-icon-klein-512.png -resize ${s}x${s} "$TMP/s$s.png"; done
magick "$TMP/d256.png" "$TMP/d128.png" "$TMP/d64.png" "$TMP/d48.png" \
       "$TMP/s32.png" "$TMP/s24.png" "$TMP/s16.png" Spielstandsanzeige.ico

echo "Fertig: app-icon-512.png, app-icon-klein-512.png, Spielstandsanzeige.ico + Resources"
