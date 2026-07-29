# Mitwirken

Beiträge sind willkommen — von Fehlermeldungen über Ideen bis zu Pull Requests.

## Entwicklungsumgebung

- JDK 21 oder neuer (entwickelt mit JDK 25)
- Maven 3.9+

```sh
mvn javafx:run    # App starten
mvn test          # Unit-Tests (Model, ohne UI)
```

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
