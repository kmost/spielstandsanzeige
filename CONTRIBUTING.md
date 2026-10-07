# Mitwirken

Beiträge sind willkommen — von Fehlermeldungen über Ideen bis zu Pull Requests.

## Entwicklungsumgebung

- JDK 21 oder neuer (entwickelt mit JDK 25)
- Maven 3.9+

```sh
mvn javafx:run    # App starten
mvn test          # alle Tests (Model, Stores und UI) samt Coverage-Bericht
```

Der Coverage-Bericht (JaCoCo) liegt danach unter `target/site/jacoco/index.html`.
`mvn verify` prüft zusätzlich die Mindest-Abdeckung (Eigenschaft `jacoco.minimum` im `pom.xml`,
aktuell 85 % der Instruktionen); genau das führt auch die CI aus.

## UI-Tests

Die Tests unter `ui/` (`ControlWindowTest`, `DisplayWindowTest`, `ConfigWindowTest`, mit
`@Tag("ui")`) starten das JavaFX-Toolkit ohne sichtbares Fenster und prüfen
Zustandswechsel, Knopf-Logik und das Raster der Anzeige bei mehreren Fenstergrößen.
Modale Rückfragen laufen über die Schnittstelle `Dialogs`; die Tests setzen `FakeDialogs`
mit vorgegebenen Antworten ein.

- Sie brauchen ein Display. Auf einem Linux-Rechner ohne Bildschirm:
  `xvfb-run -a mvn verify` (so läuft auch die CI).
- Ohne Display werden sie übersprungen (kein Fehlschlag). Gezielt überspringen:
  `mvn test -Dtest.excludedGroups=ui`.
- Nur die UI-Tests: `mvn test -Dgroups=ui`; ein einzelner Test:
  `mvn test -Dtest=ControlWindowTest#endGameAsksAndOnlyEndsOnConfirmation`.

## Layout der Publikumsanzeige prüfen

`DisplayPreview` rendert die Anzeige offscreen in ein PNG — ohne dass ein
Fenster aufgeht. Praktisch, um Layout-Änderungen schnell zu kontrollieren:

```sh
mvn test-compile org.codehaus.mojo:exec-maven-plugin:3.5.0:java \
  -Dexec.mainClass=de.kmost.scoreboard.ui.display.DisplayPreview \
  -Dexec.classpathScope=test -Dpreview.out=/tmp/preview.png
```

## Layout der Konsole prüfen

`ControlSnapshots` rendert die Kampfgericht-Konsole in acht typischen Zuständen (Setup,
laufendes Spiel mit Strafen und Timeout, Halbzeitpause, Unentschieden, Verlängerung,
7-m-Werfen, Statuszeile) bei zwei Breiten als PNGs. Für Umbauten an der Oberfläche:
einmal vorher, einmal nachher laufen lassen und die Dateien vergleichen (`cmp`) —
identische Dateien heißen identische Optik.

```sh
mvn test-compile org.codehaus.mojo:exec-maven-plugin:3.5.0:java \
  -Dexec.mainClass=de.kmost.scoreboard.ui.control.ControlSnapshots \
  -Dexec.classpathScope=test -Dsnapshots.dir=/tmp/control-vorher
```

## Leitplanken

- **Model bleibt UI-frei:** Alles unter `model/` nutzt nur `javafx.base`
  (Properties/ObservableLists) und ist mit einer Fake-Zeitquelle testbar.
  Neue Spiellogik bitte dort implementieren und mit Unit-Tests abdecken.
- **Beide Fenster beobachten denselben `GameState`** über Bindings — kein
  direkter Zustandsabgleich zwischen den Fenstern.
- **Die Konsole besteht aus kleinen Bausteinen** (`ui/control/`: `SetupPane`, `GamePane`,
  `ClockPane`, `ShootoutPane`, `PenaltyList`, `StatusBar` …), die sich an Bindings des
  `GameState` hängen, statt bei Zustandswechseln neu aufgebaut zu werden. Regeln („ist
  das jetzt möglich?“) gehören ins Model, die Bausteine binden nur daran.
- **Dateien schreibt `store/` atomar** (`PropertiesFiles`: Temp-Datei, dann Umbenennen),
  damit ein Absturz nie eine halbe Datei hinterlässt. Fehler gehen an den `ProblemReporter`,
  nicht auf `System.err`.
- **Gespeicherte Dateien tragen eine Schemaversion** (`schema=<n>`, bei `teams.properties`
  `_schema`). Dateien ohne Eintrag gelten als Version 0. Ändert sich ein Format: Version
  erhöhen, die Umwandlung als Stufe in `store/migration/` ergänzen (reine
  Properties-zu-Properties-Funktion, ein Test mit einer echten Datei der Vorversion) —
  nie im Lesecode der Stores. Dateien einer *neueren* Version werden nie überschrieben:
  aktive Einstellungen landen als `<datei>.schema<n>` im Ordner (die App startet mit
  Standardwerten und meldet das), einzelne Themes werden ignoriert, eine neuere
  Spielsicherung bleibt als `game.properties.defekt` liegen. Teamnamen mit `_` am Anfang
  sind den internen Schlüsseln vorbehalten und werden nicht gespeichert.
- **Keine neuen Laufzeit-Abhängigkeiten** ohne guten Grund: Die App soll als
  selbständige EXE/App paketierbar bleiben (Hupe wird z. B. zur Laufzeit
  generiert statt als Audio-Asset mitgeliefert).
- Vor dem PR: `mvn verify` muss grün sein (Tests und Mindest-Abdeckung).

## Neue Sportarten

Vorgabewerte (Periodendauer, Strafzeit, Timeout) stehen als Konstanten in
`SportProfile.java` — eine neue Sportart ist zunächst nur eine weitere
Konstante plus Auswahl im Setup.

## Release

1. `<version>` im `pom.xml` auf die neue Version setzen (Hauptversion ≥ 1, Form `1.2.3`) und committen.
2. Tag setzen und pushen: `git tag v1.2.3 && git push origin v1.2.3`.

Der Workflow `release-windows.yml` bricht sofort ab, wenn Tag und `pom.xml`-Version nicht
übereinstimmen. Die Release-Notizen entstehen automatisch aus den gemergten PRs und
geschlossenen Issues, gegliedert nach Labels (`.github/release.yml`) — Labels an PRs also
sauber pflegen. Dependabot schlägt wöchentlich Updates für Maven-Abhängigkeiten und
GitHub Actions vor.
