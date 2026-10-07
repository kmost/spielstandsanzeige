# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

Spielstandsanzeige für Handball: JavaFX-Desktop-App mit zwei Fenstern (Kampfgericht-Konsole und Publikumsanzeige). Sprache von UI, Doku, Kommentaren und Commit-Messages ist Deutsch.

## Befehle

```sh
mvn javafx:run                 # App starten
mvn test                       # alle Tests inkl. UI-Tests (@Tag("ui")) und JaCoCo-Bericht (target/site/jacoco)
mvn verify                     # wie CI: zusätzlich JaCoCo-Mindestabdeckung prüfen
mvn test -Dtest.excludedGroups=ui   # ohne UI-Tests (brauchen ein Display; Linux headless: xvfb-run -a mvn test)
mvn test -Dtest=GameStateTest#penaltyCanBeExtendedToFourMinutesOnce   # einzelner Test

# Layout der Publikumsanzeige offscreen in ein PNG rendern (kein Fenster)
mvn test-compile org.codehaus.mojo:exec-maven-plugin:3.5.0:java \
  -Dexec.mainClass=de.kmost.scoreboard.ui.display.DisplayPreview \
  -Dexec.classpathScope=test -Dpreview.out=/tmp/preview.png
```

JDK 25 (`maven.compiler.release=25`, CI nutzt Temurin 25), JavaFX 25. Es gibt keinen Linter.

## Tests

- `model/`, `store/`: reine Unit-Tests. `ui/`-Tests (`@Tag("ui")`) starten das JavaFX-Toolkit über `FxTestSupport` (ohne sichtbare Fenster, überspringen sich ohne Display); Layout wird mit `FxTestSupport.layout(scene)` berechnet, Knöpfe per Text gesucht.
- Modale Rückfragen laufen über `ui/Dialogs` (echt: `FxDialogs`, Tests: `FakeDialogs`); neue Alerts nie direkt im Fenster-Code öffnen.

## Architektur

- **`model/` ist UI-frei**: nur `javafx.base` (Properties/ObservableLists), Zeitquelle ist per `LongSupplier` injizierbar (`FakeNanoTime` in den Tests). Neue Spiellogik gehört hierher und bekommt Unit-Tests.
- **Ein `GameState`** hält Config, `GameClock`, Tore, Zeitstrafen (`PenaltyTimer`), Team-Timeouts und 7-m-Werfen (`Shootout`). `ControlWindow` und `DisplayWindow` beobachten dasselbe Objekt per Bindings/Listener — kein direkter Abgleich zwischen den Fenstern.
- **Zeitstrafen** speichern nur die Spielzeit-Marke ihres Starts; die Restzeit ergibt sich aus der verbrauchten Spielzeit. Sie pausieren dadurch mit der Uhr und laufen über die Halbzeitpause weiter. `GameState.tick()` (vom `AnimationTimer` in `ScoreboardApp` aufgerufen) aktualisiert Uhr und Strafen und entfernt abgelaufene.
- **Sportart-Vorgaben** (Periodendauer, Strafzeit, Timeout) sind Konstanten in `SportProfile`. Handball ist fest, es gibt keine Auswahl und viele Texte/Regeln setzen es voraus (Liste in CONTRIBUTING.md, „Sportarten“); nicht so tun, als wäre eine zweite Sportart nur eine Konstante.
- **`store/`** persistiert alles unter `~/.spielstandsanzeige/`: Teams (`TeamRepository`), den Spielstand (`GameSnapshotStore`) und die Anzeige-Einstellungen, aufgeteilt in `ThemeStore`, `DisplaySettingsStore`, `HornSettingsStore`, `BannerImageStore` (gebündelt als `SettingsStores`). Geschrieben wird immer atomar über `PropertiesFiles` (Temp-Datei + Umbenennen), Fehler laufen über den `ProblemReporter`. Alle Dateien tragen `schema=<n>` (`SchemaVersion`); Formatwechsel sind Stufen in `store/migration/`, neuere Versionen werden nie überschrieben (Details in CONTRIBUTING.md). `LogoDownloader` lädt Bilder von URLs.
- **`ui/control/ControlWindow`** hält nur Spielzustand, Fortsetzen/Sichern und Schließen zusammen; die Oberfläche steckt in kleinen Bausteinen (`SetupPane`, `GamePane` → `ClockPane`, `ShootoutPane`, `PenaltyList`, `TeamControls`, `CornerColumn`, `StatusBar`). Bausteine binden an `GameState`-Properties statt neu aufgebaut zu werden; vor/nach Oberflächen-Umbauten `ControlSnapshots` (siehe CONTRIBUTING.md) rendern und per `cmp` vergleichen.
- **`ui/display/DisplayWindow`** hat ein festes Raster (Header : Spielstand : Footer = 10 : 80 : 10, Zeilengewichte, em-basierte Schriftgrößen). Formeln und Konstanten stehen in `docs/layout.md` — vor Layout-Änderungen lesen und bei Änderungen nachziehen.
- **`ui/config/`** ist das Konfigurationsfenster (Banner, Farben via `ThemeColor`, Schriftgrößen via `FontScale`, Hupe); Änderungen wirken live auf beide Fenster. CSS liegt in `src/main/resources/de/kmost/scoreboard/{control,display}.css`.
- **`Launcher`** erbt bewusst nicht von `Application` (nötig für `java -jar` mit JavaFX auf dem Classpath); Main-Class für `javafx:run` ist `ScoreboardApp`.
- Keine neuen Laufzeit-Abhängigkeiten ohne guten Grund: Die App soll als selbständige EXE paketierbar bleiben (die Hupe wird z. B. zur Laufzeit generiert statt als Audio-Asset mitgeliefert).

## Dokumentation aktuell halten

Bei jeder Änderung an Bedienung, Verhalten oder Layout wird die Doku im selben Commit angepasst: `docs/anleitung.md` ist die einzige Quelle für die Bedienung (die README verweist nur darauf und beschreibt keine Funktionen mehr), bei Layout-Änderungen zusätzlich `docs/layout.md`, die README nur, wenn Überblick, Installation, Release oder Entwicklung betroffen sind. Ein Issue gilt erst als erledigt, wenn die Doku stimmt. Reine Refactorings ohne sichtbare Wirkung brauchen keine Doku-Änderung.

## Release

- CI (`ci.yml`) führt bei jedem Push und PR `xvfb-run -a mvn verify` aus: Tests inkl. UI-Tests plus JaCoCo-Mindestabdeckung (`jacoco.minimum` im `pom.xml`, nur in `verify`, nicht in `test`). Dependabot (`.github/dependabot.yml`) schlägt wöchentlich Updates vor.
- Ein Windows-Release (Installer-EXE, Portable-ZIP, Anleitungs-PDF) entsteht nur durch einen Versions-Tag, nicht durch Commits auf `main`: `git tag v1.2.3 && git push origin v1.2.3`. Die Hauptversion muss ≥ 1 sein (jpackage), die Version im `pom.xml` muss zum Tag passen (der Release-Workflow bricht sonst ab) — erst `pom.xml` anheben und committen, dann taggen. Release-Notizen entstehen automatisch, gegliedert nach Labels (`.github/release.yml`).
- Die EXE ist nicht signiert (SmartScreen-Warnung). Die Bedienungsanleitung `docs/anleitung.md` wird beim Release zum PDF gebaut; bei Funktionsänderungen nur sie aktualisieren (die README verweist darauf).
