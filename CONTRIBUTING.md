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

## UI-Tests

Die Tests unter `ui/` (`ControlWindowTest`, `DisplayWindowTest`, `ConfigWindowTest`, mit
`@Tag("ui")`) starten das JavaFX-Toolkit ohne sichtbares Fenster und prüfen
Zustandswechsel, Knopf-Logik und das Raster der Anzeige bei mehreren Fenstergrößen.
Modale Rückfragen laufen über die Schnittstelle `Dialogs`; die Tests setzen `FakeDialogs`
mit vorgegebenen Antworten ein.

- Sie brauchen ein Display. Auf einem Linux-Rechner ohne Bildschirm:
  `xvfb-run -a mvn test` (so läuft auch die CI).
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

## Leitplanken

- **Model bleibt UI-frei:** Alles unter `model/` nutzt nur `javafx.base`
  (Properties/ObservableLists) und ist mit einer Fake-Zeitquelle testbar.
  Neue Spiellogik bitte dort implementieren und mit Unit-Tests abdecken.
- **Beide Fenster beobachten denselben `GameState`** über Bindings — kein
  direkter Zustandsabgleich zwischen den Fenstern.
- **Keine neuen Laufzeit-Abhängigkeiten** ohne guten Grund: Die App soll als
  selbständige EXE/App paketierbar bleiben (Hupe wird z. B. zur Laufzeit
  generiert statt als Audio-Asset mitgeliefert).
- Vor dem PR: `mvn test` muss grün sein.

## Neue Sportarten

Vorgabewerte (Periodendauer, Strafzeit, Timeout) stehen als Konstanten in
`SportProfile.java` — eine neue Sportart ist zunächst nur eine weitere
Konstante plus Auswahl im Setup.
