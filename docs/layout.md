# Layout der Publikumsanzeige

Dieses Dokument beschreibt das Raster der Publikumsanzeige (`ui/display`):
welche Zonen es gibt, wie sich ihre Größen berechnen und wo die Werte im Code
stehen. Stand: 2026-07-21.

## Überblick

```
┌────────────────────────────────────────────────────────────┐
│  HEADER (Banner)                        10 % · Faktor      │
├────────────────────────────────────────────────────────────┤
│  Abstand 2 %                                               │
│┌──────────────────────────────────────────────────────────┐│
││ SPIELSTAND                              Resthöhe         ││
││                                                          ││
││  Zeile 1 (38*): Strafen ─┬─ UHR + Timeout ─┬─ Strafen    ││
││                 Heim 25 %│      50 %       │Gast 25 %    ││
││  Zeile 2 (38*): TORE ────┼───── Phase ─────┼─ TORE       ││
││                     42 % │      16 %       │    42 %     ││
││  Zeile 3 (24*): Teamname + Timeout-Punkte je Seite       ││
││                 (gleiche Spalten wie Zeile 2)            ││
│└──────────────────────────────────────────────────────────┘│
│  Abstand 2 %                                               │
├────────────────────────────────────────────────────────────┤
│  FOOTER (Banner)                        10 % · Faktor      │
└────────────────────────────────────────────────────────────┘
     * Standardgewichte, skalieren mit den Größenfaktoren
```

## Äußeres Raster: Header : Spielstand : Footer

Standardverhältnis **10 : 80 : 10** der Fensterhöhe. Die Zonen sind fest —
unabhängig von Schriftart, Schriftgröße und Banner-Inhalt.

- Ein sichtbarer Banner belegt `10 % × Größenfaktor` der Fensterhöhe
  (`Banner.SHARE = 0.10`, Faktor aus dem Theme: `FontScale.HEADER` /
  `FontScale.FOOTER`, im Konfigurationsfenster 50–250 %).
- Zwischen sichtbarem Banner und Spielstand liegt ein Abstand von **2 %**
  der Fensterhöhe (`DisplayWindow.BANNER_GAP`). Er geht zulasten des
  Spielstands, damit die konfigurierten Banner-Anteile exakt stimmen.
- **Ausgeblendete Banner** (ohne anzeigbaren Inhalt) erzeugen weder Zone noch
  Abstand — ihr Anteil fällt an den Spielstand.
- Der Spielstand bekommt die Resthöhe:
  `Fensterhöhe × (1 − Headeranteil − Footeranteil − Abstände)`.

## Banner (Header/Footer)

Eine Instanz der Klasse `Banner` je Seite; mittige `HBox` mit den Slots der
`BannerConfig` in Rasterreihenfolge (Text 1, Bild 1, Text 2, …, Bild 5,
Text 6), leere Slots rücken zusammen.

- **Schriftgröße** hängt direkt an der Zonenhöhe: `45 %` der Bannerhöhe
  (`Banner.FONT_SHARE = 0.45`). Bei Standardgröße also 4,5 % der Fensterhöhe.
  Sie ist bewusst *nicht* an die em-Basisgröße des Spielstands gekoppelt —
  wächst der Banner, wächst seine Schrift im gleichen Verhältnis.
- **Bilder** werden proportional auf `Bannerhöhe − 6 px` skaliert.
- Innenabstand `2/15 px`, Slot-Abstand `1,5 %` der Fensterbreite.
- **Einpassung:** Ist der Inhalt breiter als das Fenster, wird die komplette
  Zeile (Texte und Bilder gemeinsam) proportional verkleinert, bis sie passt —
  es wird nie etwas mit „…“ abgeschnitten (`Banner.updateFit`).

## Spielstand

### Basis-Schriftgröße

Alle Größen im Spielstand sind in `em` einer gemeinsamen Basis. Die Basis wird
in `DisplayWindow` berechnet:

```
Basis = min( Spielstandhöhe × 0.0625 , Fensterbreite × 0.029 ) / Gewichtssumme
        (mindestens 10 px)
```

- `0.0625` entspricht den historischen 5 % der Fensterhöhe bei zwei sichtbaren
  Standard-Bannern (0.05 / 0.8).
- Die **Breiten-Deckelung** (`0.029`) sorgt dafür, dass die breiteste Zeile
  (Strafen-Chip in der 25-%-Spalte, bei Standardgröße) auch in schmalen
  Fenstern (z. B. 4:3) vollständig bleibt; bei 16:9 und breiter greift die
  Höhe. Per Faktor vergrößerte Strafen-Chips werden zusätzlich in ihre
  Spalte **eingepasst** (proportional verkleinert, sobald sie breiter wären
  als die Spalte, `DisplayWindow.fitToWidth`) — der Faktor wirkt also bis
  zur Spaltenbreite, nie auf Kosten der übrigen Elemente.
- Die **Gewichtssumme** normalisiert die Größenfaktoren der Zeilen
  (siehe unten); bei Standardfaktoren ist sie 1.

### Zeilen

Drei Prozent-Zeilen, deren Standardanteile mit den Größenfaktoren des Themes
gewichtet und auf 100 % normalisiert werden (`DisplayWindow.weightedRow`):

| Zeile | Inhalt | Standardanteil | Größenfaktor |
|---|---|---|---|
| 1 | Strafen Heim · Uhr + Timeout-Chip · Strafen Gast | 38 % | `FontScale.CLOCK` |
| 2 | Tore Heim · Phase („1. HZ“/„Pause“/„Ende“) · Tore Gast | 38 % | `FontScale.SCORE` |
| 3 | Teamname + Timeout-Punkte je Seite | 24 % | `FontScale.TEAM_NAME` |

Gewichtssumme = `0.38·Uhr + 0.38·Tore + 0.24·Namen`. Weil die Basis-Schrift
durch dieselbe Summe geteilt wird, behält jedes Element sein Verhältnis zur
eigenen Zeilenhöhe — **kein Regler kann etwas aus seiner Zeile drängen**, und
ein gleichmäßiges Vergrößern aller drei Faktoren ändert nichts: Innerhalb des
Spielstands definieren die Faktoren nur die Verhältnisse zueinander.

### Spalten

- Zeile 1: `25 % | 50 % | 25 %` — Strafen außen (Heim linksbündig, Gast
  rechtsbündig, jeweils **oben bündig**, damit die Chips nicht springen, wenn
  Strafen dazukommen oder auslaufen), Uhr mittig, darunter der Timeout-Chip.
- Zeilen 2 und 3: `42 % | 16 % | 42 %` — identische Spalten, damit die
  Teamnamen exakt unter ihren Toren stehen.
- Teamnamen brechen ab `40 %` der Fensterbreite um.
- Außenabstand des Spielstand-Rasters: `10/15 px`.

### Schriftgrößen (in em der Basis)

| Element | Größe | definiert in |
|---|---|---|
| Spieluhr | `5.2 em × Faktor Uhr` | `DisplayWindow.CLOCK_EM` |
| Tore | `6.0 em × Faktor Tore` | `DisplayWindow.SCORE_EM` |
| Teamnamen | `1.1 em × Faktor Teamnamen` | `DisplayWindow.TEAM_NAME_EM` |
| Strafen-Chips | `1.4 em × Faktor Zeitstrafen` | `DisplayWindow.PENALTY_EM` |
| Phase | `1.1 em × Faktor Statuszeile` | `DisplayWindow.PHASE_EM` |
| Timeout-Chip | `1 em × Faktor Timeout` | `DisplayWindow.TIMEOUT_EM` |
| Timeout-Punkte | `0.85 em × Faktor Timeout` | `DisplayWindow.TIMEOUT_DOTS_EM` |

Nur die Faktoren von Uhr, Toren und Teamnamen gewichten zusätzlich ihre
Zeilen (siehe oben) — Zeitstrafen, Timeout und Statuszeile skalieren rein
die Schrift innerhalb der bestehenden Zeilen.

Die **Spieluhr** ist kein einzelnes Label, sondern eine Zeile fester
Ziffern-Zellen: Jede Ziffer sitzt in einer Zelle mit der Breite der
breitesten Ziffer der aktuellen Schrift (per Probe-Text gemessen), der
Doppelpunkt behält seine natürliche Breite. Beim Sekundenzählen bewegt sich
dadurch nichts — auch bei Schriften ohne gleich breite Ziffern
(`DisplayWindow.buildClockDisplay`).

## Wo steht was im Code?

| Bereich | Ort |
|---|---|
| Äußeres Raster, Basis-Schrift, Zeilen/Spalten | `ui/display/DisplayWindow.java` |
| Banner-Zone, Banner-Schrift, Slot-Aufbau | `ui/display/Banner.java` |
| feste em-Größen, Farben (Fallbacks) | `resources/…/display.css` |
| Größenfaktoren (Enum, Teil des Themes) | `ui/FontScale.java`, `ui/Theme.java` |
| Slot-Modell der Banner | `ui/BannerConfig.java` |

Zum visuellen Prüfen von Layout-Änderungen gibt es die Offscreen-Vorschau
(`DisplayPreview`, siehe CONTRIBUTING.md) mit `-Dpreview.width/height/font`
und `-Dpreview.<faktor>` (z. B. `-Dpreview.clockScale=1.5`).
