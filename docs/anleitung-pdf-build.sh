#!/bin/sh
# Setzt die Anleitung (anleitung.md) mit pandoc und WeasyPrint als PDF.
# Wird auch vom Release-Workflow verwendet — Änderungen hier wirken sich
# direkt auf das Release-Artefakt aus.
#
# Benötigt: pandoc und weasyprint
#   macOS:  brew install pandoc weasyprint
#   Ubuntu: sudo apt-get install pandoc weasyprint
#
# Aufruf: docs/anleitung-pdf-build.sh [ausgabedatei]
# Ausgabe: target/Spielstandsanzeige-Anleitung.pdf (falls kein Argument)
set -eu
DOCS=$(cd "$(dirname "$0")" && pwd)
OUT=${1:-"$DOCS/../target/Spielstandsanzeige-Anleitung.pdf"}

mkdir -p "$(dirname "$OUT")"
# Zwei Schritte statt pandoc-interner PDF-Erzeugung: Das Zwischen-HTML liegt
# bewusst im docs-Ordner, damit WeasyPrint relative Bildpfade dort auflöst —
# pandocs eigener Umweg über ein Temp-HTML verliert sie je nach Version.
TMP_HTML="$DOCS/.anleitung-tmp.html"
trap 'rm -f "$TMP_HTML"' EXIT
pandoc "$DOCS/anleitung.md" \
  --standalone \
  --embed-resources \
  --css "$DOCS/anleitung-pdf.css" \
  --resource-path="$DOCS" \
  --metadata pagetitle='Spielstandsanzeige – Anleitung' \
  --metadata lang=de \
  -o "$TMP_HTML"
weasyprint "$TMP_HTML" "$OUT"
echo "Fertig: $OUT"
