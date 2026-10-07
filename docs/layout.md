# Layout der Publikumsanzeige

Dieses Dokument beschreibt das Raster der Publikumsanzeige (`ui/display`):
welche Zonen es gibt, wie sich ihre Größen berechnen und wo die Werte im Code
stehen. Maßgeblich sind die Konstanten in `ui/display/DisplayLayout.java` (dort steht
zu jedem Wert, woher er kommt); die Zonenaufteilung prüft `DisplayWindowTest`. Dieses
Dokument nennt die Konstanten beim Namen und wiederholt ihre Werte nur dort, wo sie
zum Verständnis der Formel nötig sind. Ändert sich eine Formel, wird es im selben
Commit angepasst.

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
  (`DisplayLayout.BANNER_SHARE`, Faktor aus dem Theme: `FontScale.HEADER` /
  `FontScale.FOOTER`, im Konfigurationsfenster 50–250 %).
- Zwischen sichtbarem Banner und Spielstand liegt ein Abstand von **2 %**
  der Fensterhöhe (`DisplayLayout.BANNER_GAP`). Er geht zulasten des
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
  (`DisplayLayout.BANNER_FONT_SHARE`). Bei Standardgröße also 4,5 % der Fensterhöhe.
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
Basis = min( Spielstandhöhe × BASE_FONT_HEIGHT_SHARE ,
             Fensterbreite × BASE_FONT_WIDTH_SHARE ) / Gewichtssumme
        (mindestens BASE_FONT_MIN_PX)
```

- `BASE_FONT_HEIGHT_SHARE` (0.0625) entspricht den historischen 5 % der Fensterhöhe bei zwei sichtbaren
  Standard-Bannern (0.05 / 0.8).
- Die **Breiten-Deckelung** (`BASE_FONT_WIDTH_SHARE`) sorgt dafür, dass die breiteste Zeile
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
| 1 | Strafen Heim · Uhr + Timeout-Chip (beim 7-m-Werfen darunter die Wurf-Liste, siehe unten) · Strafen Gast | 38 % | `FontScale.CLOCK` |
| 2 | Tore Heim · Phase („1. HZ“/„Pause“/„Ende“; in der Verlängerung zweizeilig „1. Verlängerung“ + „2. HZ“, eingepasst statt gekürzt) · Tore Gast | 38 % | `FontScale.SCORE` |
| 3 | Teamname + Timeout-Punkte je Seite | 24 % | `FontScale.TEAM_NAME` |

Gewichtssumme = `ROW_CLOCK·Uhr + ROW_SCORE·Tore + ROW_NAMES·Namen` (Standardanteile 38/38/24 %). Weil die Basis-Schrift
durch dieselbe Summe geteilt wird, behält jedes Element sein Verhältnis zur
eigenen Zeilenhöhe — **kein Regler kann etwas aus seiner Zeile drängen**, und
ein gleichmäßiges Vergrößern aller drei Faktoren ändert nichts: Innerhalb des
Spielstands definieren die Faktoren nur die Verhältnisse zueinander.

### Spalten

- Zeile 1 (`CLOCK_ROW_COLUMNS`): `25 % | 50 % | 25 %` — Strafen außen (Heim linksbündig, Gast
  rechtsbündig, jeweils **oben bündig**, damit die Chips nicht springen, wenn
  Strafen dazukommen oder auslaufen), Uhr mittig, darunter der Timeout-Chip.
- Zeilen 2 und 3 (`SCORE_ROW_COLUMNS`): `42 % | 16 % | 42 %` — identische Spalten, damit die
  Teamnamen exakt unter ihren Toren stehen.
- **Beim 7-m-Werfen** wird die Tore-Zeile (Zeile 2) um 33 % kleiner (`SHOOTOUT_SCORE_SHARE` =
  0.67: Zeilenhöhe und Schrift der Torzahlen); die frei werdende Höhe (0.33 · `ROW_SCORE`) geht
  an Zeile 1 (`DisplayWindow.clockRow`/`scoreRow`). Die Summe der Zeilenhöhen und die Basisschrift
  bleiben gleich, Zeile 3 und die Banner ändern sich nicht. In Zeile 1 teilt sich die Mittelspalte:
  oben das Uhr-Panel (Uhr + Timeout-Chip) mit der halben **bisherigen** Zeilenhöhe
  (`SHOOTOUT_CLOCK_SHARE` = 0.5, Uhr-Schrift im selben Verhältnis), darunter die Wurf-Liste mit
  der gesamten übrigen, nun größeren Höhe. Das gilt, sobald ein 7-m-Werfen existiert — auch nach
  feststehendem Sieger, damit das Layout bis zum nächsten Spiel stabil bleibt. Die Strafen-Spalten
  außen behalten Spalte und Chip-Größe; ihr Block sitzt mittig in der höheren Zeile und verschiebt
  sich dabei um wenige Pixel (`DisplayWindow.arrangeCenter`). Die Liste ist dieselbe Tabelle wie in
  der Kampfgericht-Konsole (`ui/ShootoutTable`): Rundennummern, Zeilen „Heim“ und „Gast“,
  ⚽ Tor / ✋ Fehlwurf. Die ersten fünf Runden (die regulären Schützen) stehen von Anfang an
  darin, ihre noch leeren Zellen reservieren den Platz der Symbole; höchstens 10 Runden (ältere
  verlassen sie per „…“, die jüngsten bleiben sichtbar). Schrift: `SHOOTOUT_TABLE_EM` (1.6 em der Basis × Faktor Statuszeile); ist die Liste
  breiter oder höher als ihr Platz, wird sie proportional eingepasst (`FitBox`), nie gekürzt —
  bei den üblichen Fenstern deckelt die Höhe der Liste ihre Größe.
- Teamnamen brechen ab `TEAM_NAME_MAX_WIDTH_SHARE` (40 %) der Fensterbreite um.
- Außenabstand des Spielstand-Rasters: `GRID_PADDING_VERTICAL/HORIZONTAL` (10/15 px).

### Schriftgrößen (in em der Basis)

| Element | Größe | definiert in |
|---|---|---|
| Spieluhr | `5.2 em × Faktor Uhr` | `DisplayLayout.CLOCK_EM` |
| Tore | `6.0 em × Faktor Tore` | `DisplayLayout.SCORE_EM` |
| Teamnamen | `1.1 em × Faktor Teamnamen` | `DisplayLayout.TEAM_NAME_EM` |
| Strafen-Chips | `1.4 em × Faktor Zeitstrafen` | `DisplayLayout.PENALTY_EM` |
| Phase | `1.1 em × Faktor Statuszeile` | `DisplayLayout.PHASE_EM` |
| Timeout-Chip | `1 em × Faktor Timeout` | `DisplayLayout.TIMEOUT_EM` |
| Timeout-Punkte | `0.85 em × Faktor Timeout` | `DisplayLayout.TIMEOUT_DOTS_EM` |

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
| alle Layout-Konstanten (Anteile, Spalten, Abstände, em-Größen) | `ui/display/DisplayLayout.java` |
| Aufbau des Rasters, Basis-Schrift, Zeilen/Spalten | `ui/display/DisplayWindow.java` |
| Banner-Zone, Banner-Schrift, Slot-Aufbau | `ui/display/Banner.java` |
| feste em-Größen, Farben (Fallbacks) | `resources/…/display.css` |
| Größenfaktoren (Enum, Teil des Themes) | `ui/FontScale.java`, `ui/Theme.java` |
| Slot-Modell der Banner | `ui/BannerConfig.java` |

Zum visuellen Prüfen von Layout-Änderungen gibt es die Offscreen-Vorschau
(`DisplayPreview`, siehe CONTRIBUTING.md) mit `-Dpreview.width/height/font`
und `-Dpreview.<faktor>` (z. B. `-Dpreview.clockScale=1.5`).
