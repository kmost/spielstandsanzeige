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
# --embed-resources: Bilder als Daten-URIs einbetten — ohne das findet
# WeasyPrint sie je nach pandoc-Version nicht (Zwischen-HTML liegt im Temp-Ordner).
pandoc "$DOCS/anleitung.md" \
  --standalone \
  --embed-resources \
  --pdf-engine=weasyprint \
  --css "$DOCS/anleitung-pdf.css" \
  --resource-path="$DOCS" \
  --metadata pagetitle='Spielstandsanzeige – Anleitung' \
  --metadata lang=de \
  -o "$OUT"
echo "Fertig: $OUT"
