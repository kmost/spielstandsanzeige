# Spielstandsanzeige

> **English summary:** A JavaFX desktop scoreboard for handball (extensible to
> other sports) with two windows — an operator console for the timekeeper's desk
> and a spectator display for a projector or second screen (full screen).
> Features: game clock (count up or down, single period or two halves, automatic
> stop with horn), scores, 2-minute penalties with optional player number, team
> timeouts with quota display, and a persistent team-name database. Run with `mvn javafx:run`; GitHub releases include a
> self-contained Windows installer (no Java required). The documentation below
> is in German.

JavaFX-Desktopanwendung für Handball (erweiterbar für andere Sportarten) mit zwei Fenstern:

- **Kampfgericht-Konsole** — Spiel-Setup und Steuerung (Uhr, Tore, Zeitstrafen,
  Team-Timeouts, Hupe)
- **Publikumsanzeige** — große Anzeige für Beamer/zweiten Monitor (Vollbild)

![Publikumsanzeige](docs/screenshot-anzeige.png)

Eine Schritt-für-Schritt-Anleitung für Anwender (Installation, Anzeige auf den
zweiten Bildschirm bringen, Konfiguration) steht in
[docs/anleitung.md](docs/anleitung.md).

## Starten

Voraussetzungen: JDK 21+ (entwickelt mit JDK 25) und Maven.

```sh
mvn javafx:run
```

## Bedienung

Die Bedienung (Spiel anlegen, Anzeige auf den zweiten Bildschirm bringen, Uhr, Tore,
Zeitstrafen, Team-Timeouts, Verlängerung und 7-m-Werfen, Spielstand-Sicherung,
Konfiguration der Anzeige) steht in der Anleitung:
**[docs/anleitung.md](docs/anleitung.md)**. Sie ist die einzige Quelle für die
Bedienung und wird bei jedem Release als PDF an das Release gehängt.

Wie sich das Raster der Anzeige zusammensetzt (Zonen, Zeilengewichte,
Schriftgrößen-Formeln), beschreibt [docs/layout.md](docs/layout.md).

## Tests

```sh
mvn test
```

Das Model (Uhr, Zeitstrafen, Spielstand) ist UI-frei und vollständig per Unit-Tests
mit einer Fake-Zeitquelle abgedeckt. Dazu prüfen UI-Tests ohne sichtbares Fenster die
Knopf-Logik der Konsole und das Raster der Anzeige bei mehreren Fenstergrößen; sie brauchen
ein Display (Linux ohne Bildschirm: `xvfb-run -a mvn test`) und werden sonst übersprungen.
`mvn test` erzeugt außerdem einen Coverage-Bericht unter `target/site/jacoco/index.html`;
`mvn verify` prüft zusätzlich die Mindest-Abdeckung (so läuft die CI).
Details in [CONTRIBUTING.md](CONTRIBUTING.md).

## Windows-Release (EXE)

Ein Release entsteht, wenn ein Versions-Tag gepusht wird
(`git tag v1.2.3 && git push origin v1.2.3`; die Version im `pom.xml` muss zum Tag
passen). Dann baut der Workflow `.github/workflows/release-windows.yml` zwei
Windows-Artefakte mit eingebetteter Java-Runtime (es muss **kein Java installiert**
sein), erstellt das GitHub-Release mit automatisch erzeugten Notizen und hängt an:

- `Spielstandsanzeige-<version>.exe` — Installer (ohne Adminrechte, mit Startmenü-Eintrag)
- `Spielstandsanzeige-<version>-windows-portable.zip` — entpacken und
  `Spielstandsanzeige.exe` direkt starten (z. B. vom USB-Stick)
- `Spielstandsanzeige-Anleitung.pdf` — die Anleitung aus `docs/anleitung.md`
- `SHA256SUMS.txt` — Prüfsummen von Installer und ZIP

Tags im Format `v1.2.3` mit Hauptversion ≥ 1 verwenden (Vorgabe von jpackage). Der
Workflow lässt sich auch manuell starten; dann entstehen die Dateien nur als
Workflow-Artifact, ohne Release. Ablauf im Detail: [CONTRIBUTING.md](CONTRIBUTING.md).

### Windows SmartScreen und Virenscanner

Die EXE-Dateien sind noch nicht digital signiert. Windows SmartScreen oder Defender
zeigen deshalb beim ersten Start gelegentlich eine Warnung oder blockieren die Datei,
weil neue, wenig verbreitete Programme keine „Reputation“ haben. Was hilft:

- **SmartScreen-Warnung:** „Weitere Informationen“ → „Trotzdem ausführen“.
- **Portable ZIP:** Vor dem Entpacken Rechtsklick auf die ZIP-Datei → „Eigenschaften“ →
  „Zulassen“ anhaken → „OK“. Sonst erben alle entpackten Dateien die Download-Markierung
  und werden einzeln geprüft.
- **Installer statt ZIP:** Der Installer wird erfahrungsgemäß seltener blockiert als die
  lose, portable EXE.
- **Echtheit prüfen:** Die Prüfsumme der heruntergeladenen Datei mit `SHA256SUMS.txt`
  vergleichen:

  ```powershell
  Get-FileHash .\Spielstandsanzeige-1.2.0-windows-portable.zip -Algorithm SHA256
  # oder: certutil -hashfile Spielstandsanzeige-1.2.0-windows-portable.zip SHA256
  ```

  Der Hash muss mit dem Eintrag in `SHA256SUMS.txt` übereinstimmen (Groß-/Kleinschreibung
  egal).

### Code-Signing-Policy

*Entwurf — die Signierung ist noch nicht aktiv.* Geplant ist, die Windows-Dateien über
die SignPath Foundation signieren zu lassen:

> Free code signing provided by [SignPath.io](https://signpath.io), certificate by
> [SignPath Foundation](https://signpath.org).

- **Was signiert wird:** ausschließlich Dateien, die der GitHub-Actions-Workflow
  [`release-windows.yml`](.github/workflows/release-windows.yml) aus dem öffentlichen
  Quellcode dieses Repositories baut (Installer-EXE und `Spielstandsanzeige.exe`).
- **Rollen:** Committer, Reviewer und Approver ist der Projektinhaber
  ([Knut Most](https://github.com/kmost)). Jede Signierung wird einzeln freigegeben.
- **Datenschutz:** Die Anwendung überträgt keine Daten an Dritte. Die einzige
  Netzwerkverbindung ist der Bild-Download für Banner, den Sie selbst über „Bild aus dem
  Internet“ auslösen. Alle Einstellungen bleiben lokal unter `~/.spielstandsanzeige/`.

## Andere Sportarten

Vorgabewerte (Periodendauer, Strafzeitlänge) stehen in
`src/main/java/de/kmost/scoreboard/model/SportProfile.java` — weitere Sportarten
werden dort als zusätzliche Konstanten ergänzt.

## Gespeicherte Daten

Die App legt Teamnamen unter `~/.spielstandsanzeige/` ab (`teams.properties`),
den laufenden Spielstand (`game.properties`), die Logdatei
(`spielstandsanzeige.log`), dazu die aktive Anzeige-Konfiguration (`display.properties`: Farben +
Header/Footer), Banner-Bilder (`banners/`), gespeicherte Farb-Themes
(`themes/`) und die Hupen-Auswahl (`horn.properties`). Der Ordner kann
gefahrlos gelöscht werden, um alles zurückzusetzen.

## Mitwirken

Fehlermeldungen, Ideen und Pull Requests sind willkommen — Details in
[CONTRIBUTING.md](CONTRIBUTING.md).

## Lizenz

Dieses Projekt steht unter der [MIT-Lizenz](LICENSE).

Verwendete Abhängigkeiten:

- [OpenJFX (JavaFX)](https://openjfx.io) — GPLv2 mit Classpath Exception
- [JUnit 5](https://junit.org) — EPL 2.0 (nur für Tests)

Die Windows-Pakete aus dem Release-Workflow bündeln eine
[Eclipse-Temurin](https://adoptium.net)-Java-Runtime (GPLv2 mit Classpath
Exception). Die zugehörigen Lizenztexte liegen den Paketen als
`app/THIRD-PARTY-NOTICES.txt` bei (Quelle: [THIRD-PARTY-NOTICES.txt](THIRD-PARTY-NOTICES.txt)).
