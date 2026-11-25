# Verba

*Ein ruhiger Vokabel-Sammler für alle, die Bücher, Novels und Manga in einer anderen Sprache lesen.*

[English](README.md) · **Deutsch**

Markiere in einer beliebigen App ein Wort, sieh die Übersetzung in deine
Sprache und speichere es zusammen mit dem Satz, in dem du es gefunden hast –
später wiederholst du es mit genau diesem Satz. Ob englische Fantasy,
chinesische Web-Novels oder japanische Manga: Verba funktioniert für jedes
Sprachpaar gleich. Die Wörterbücher funktionieren offline, die App hat
keine Werbung, kein Tracking und braucht keine Google-Play-Dienste.
Ein E-Ink-Modus macht sie auf E-Readern wie dem Boox Go 7 angenehm.

> **Stand: frühe Entwicklung.** Verba sammelt Wörter aus jeder App – auch
> aus Manga-Seiten und Screenshots –, schlägt sie in Offline-Wörterbüchern
> nach, die du in der App lädst, und lässt dich sie mit verteilter
> Wiederholung lernen. Die Funktionen unten
> kommen Meilenstein für Meilenstein, siehe
> [Roadmap](../../issues?q=label%3Amilestone). Screenshots folgen bald.

## Funktionen

Schon da:

- **Wörter aus jeder App sammeln:** Text markieren und im Auswahlmenü
  Verba wählen, an Verba teilen oder aus der Wörterbuch-Funktion eines
  Readers schicken. Im Satz das gesuchte Wort antippen – Chinesisch und
  Japanisch werden auch in Wörter zerlegt – und mit Satz, Buch und
  Herkunfts-App speichern.
- **Offline-Wörterbücher pro Sprachpaar**, in der App geladen und danach
  ohne Internet nutzbar: bisher Englisch → Deutsch, Japanisch → Deutsch,
  Japanisch → Englisch und Chinesisch → Englisch, gebaut aus Wiktionary,
  Ding, JMdict und CC-CEDICT. Verba findet Grundformen („running“ → „run“,
  „gave up“ → „give up“), japanische Beugungen (食べた → 食べる) und den
  längsten Treffer in Sprachen ohne Leerzeichen; gibt es noch kein
  Wörterbuch in deine Sprache, zeigt es das englische und sagt das dazu.
- **Vokabelliste** mit Suche (Wörter, Übersetzungen, Sätze, Bücher),
  Filtern nach Sprache, Buch und Lernstand (neu, lernend, bekannt) und
  einer Seite pro Wort: bearbeiten, alle Sätze und den Lernfortschritt
  sehen, ein Wort oder einen einzelnen Satz löschen (mit Rückgängig) und
  Duplikate zusammenführen – Verba weist auf wahrscheinliche hin
  („running“ und „run“).
- **Wiederholen mit verteilter Wiederholung** (FSRS-6, der Planer, den
  auch Anki nutzt): Die Karte zeigt einen deiner Sätze mit einer Lücke, wo
  das Wort stand – „Hinweis zeigen“ blendet die Übersetzung ein. Bewerte
  mit Nochmal, Schwer, Gut oder Einfach; jeder Knopf sagt, wann das Wort
  wiederkommt. Wörter mit mehreren Sätzen zeigen sie abwechselnd.
  Tageslimits für neue Wörter und Wiederholungen, und die letzte Bewertung
  lässt sich zurücknehmen.
- **Text in Bildern und auf dem Bildschirm:** Manga-Seite oder Screenshot
  an Verba teilen, ein Bild auswählen oder die Schnelleinstellungs-Kachel
  „Bildschirm lesen“ nutzen. Verba erkennt den Text – Chinesisch, Japanisch
  (auch senkrechte Sprechblasen) und lateinische Schrift – und rahmt jede
  Sprechblase ein; antippen und ihre Wörter mit der Sprechblase als Satz
  speichern. Die Erkennung läuft auf deinem Gerät (PaddleOCRs
  PP-OCRv5-Modelle auf ncnn, einmalig 11 MB Download).
- **Sicherung und Export:** eine JSON-Sicherung von allem
  (Wiederherstellen führt zusammen, bei jedem Wort gewinnt die neuere
  Änderung), eine CSV-Tabelle und ein Anki-Stapel (.apkg) für AnkiDroid
  oder Anki mit deinem Satz als Lückentext-Karte – alles über die
  Dateiauswahl des Systems, ohne Speicher-Berechtigung.
- **Vokabel des Tages** auf dem Startbildschirm: ein Widget mit einem
  deiner Wörter, seiner Übersetzung und seinem Satz, jeden Tag ein anderes;
  antippen öffnet das Wort. Einmal am Tag über Androids Wecker neu
  gezeichnet, im Hintergrund läuft nichts; im E-Ink-Modus schwarz-weiß.
- **Helles und dunkles Design**, wie im System oder von Hand gewählt.
- **E-Ink-Modus:** reines Schwarz-Weiß, keine Animationen, größere
  Tippflächen. Auf Boox-E-Readern schlägt Verba ihn beim ersten Start vor.
- **Englisch und Deutsch**, die Sprache lässt sich pro App einstellen.

Geplant:

- [ ] **Satzübersetzung** (optional; offline mit Bergamot ist machbar, aber
  ein größeres Stück Arbeit und vorerst zurückgestellt – siehe Issue).
- [ ] Ein schnelleres Erfassen-Fenster und Beispielsätze für Wörter, die
  ohne Satz gespeichert wurden (siehe Issues).

## Verba benutzen

Verba taucht an drei Stellen auf:

- **Auswahlmenü:** Text markieren und im Menü „Verba“ wählen (oft unter ⋮).
  Das klappt in Apps, die nach Text-Werkzeugen suchen, z. B. Chrome oder
  Firefox; manche Apps bieten es nicht an.
- **Teilen:** Text markieren → Teilen → „In Verba speichern“. Geht in fast
  jeder App.
- **Lese-Apps mit externem Wörterbuch:** Kann dein Reader Wörter an
  ColorDict oder Aard 2 schicken, wähle eines davon als Wörterbuch – das
  Wort öffnet sich dann in Verba. In Readest das System-Wörterbuch
  einschalten (Einstellungen → Sprache → Wörterbücher) und Verba als
  Nachschlage-App wählen; markierst du einen ganzen Satz, bleibt er beim
  Wort.
- **Bilder:** ein Bild an „Text finden mit Verba“ teilen oder über der
  Wortliste auf das Bild-Symbol tippen. Für Manga und alles andere auf dem
  Bildschirm die Kachel „Bildschirm lesen“ hinzufügen (Einstellungen →
  Texterkennung → Bildschirm lesen): Android fragt jedes Mal, Verba nimmt
  ein Bild des Bildschirms auf und hört wieder auf.

Deine eigene Sprache wählst du einmal unter Einstellungen → Übersetzen in,
die nötigen Wörterbücher lädst du unter Einstellungen → Wörterbücher.

Zum Wiederholen tippst du auf der Startseite auf **Starten**, sobald Wörter
fällig sind. Neue Wörter kommen in der Reihenfolge, in der du sie gespeichert
hast, 20 pro Tag, außer du änderst das unter Einstellungen → Wiederholen. Ein
Lerntag beginnt um 4 Uhr, spätes Lesen in der Nacht zählt also noch zum
Abend.

## Datenschutz

- **Internetzugriff** wird nur genutzt, wenn du ein Wörterbuch oder die
  Modelle der Texterkennung lädst – aus den Releases dieses Projekts auf
  GitHub, sonst nichts.
- **Vordergrunddienst für die Bildschirmaufnahme**, nur für die Kachel
  „Bildschirm lesen“: Nachdem Android dich gefragt hat, läuft er, während
  ein Bild des Bildschirms aufgenommen wird, und hört dann auf. Das Bild
  bleibt im Arbeitsspeicher, wird nie gespeichert und verlässt dein Gerät
  nie.
- Die CI schlägt fehl, wenn eine Bibliothek eine andere Berechtigung
  hinzufügen will (`tools/check-permissions.sh`).
- Keine Telemetrie, keine Werbung, keine Google-Play-Dienste.
- Deine Wörter sind vom Google-Cloud-Backup ausgenommen; der Umzug auf ein
  neues Handy per Kabel oder WLAN (Gerät-zu-Gerät-Übertragung) funktioniert
  trotzdem.

## Installation

Verba erscheint auf [GitHub Releases](../../releases). Am einfachsten
installierst und aktualisierst du die App mit
[Obtainium](https://github.com/ImranR98/Obtainium):

1. Obtainium installieren (aus dessen GitHub-Releases oder von F-Droid).
2. In Obtainium **App hinzufügen** antippen, `https://github.com/lenkr0/verba`
   einfügen und **Hinzufügen** antippen.
3. **Installieren** antippen. Android fragt einmal, ob Obtainium Apps
   installieren darf.

Danach sucht Obtainium im Hintergrund nach neuen Versionen und meldet sich.
Du kannst `verba-<version>.apk` auch direkt aus dem neuesten Release laden und
öffnen. Jedes Release nennt die SHA-256-Prüfsumme der APK und des
Signaturzertifikats.

Benötigt Android 13 oder neuer. Gemacht für Android 13 (Boox Go 7) und
Android 16 (Poco F6 Pro, HyperOS).

## Selbst bauen

Benötigt JDK 21 und das Android SDK (Android Studio oder die
Kommandozeilen-Tools).

```
./gradlew assembleDebug               # Debug-APK, installiert sich neben dem Release als „Verba Debug“
./gradlew testDebugUnitTest           # Unit-Tests
./gradlew connectedDebugAndroidTest   # Tests auf Gerät oder Emulator
./gradlew lintDebug                   # Android Lint
```

Die CI baut bei jedem Push, führt Unit-Tests, Lint und die
Berechtigungsprüfung aus; Pull Requests und `main` laufen zusätzlich auf
Emulatoren mit Android 13 und Android 16 ohne Google Play. Ein Tag `v1.2.3` –
passend zu `verba.version` in `gradle.properties` – baut die signierte APK
und veröffentlicht das Release. Wie der Signierschlüssel erstellt und
hinterlegt wird, steht in [docs/release-signing.md](docs/release-signing.md).

## Übersetzungen

Die Oberfläche gibt es auf Englisch und Deutsch. Die Texte liegen in
`app/src/main/res/values-<sprache>/strings.xml`; eine neue Sprache braucht
außerdem einen Eintrag in `AppLanguage.supported`. Korrekturen und neue
Sprachen sind willkommen.

## Lizenz

GPL-3.0-or-later, siehe [LICENSE](LICENSE). Hinweise zu Drittanbietern in
[NOTICE.md](NOTICE.md), Änderungen in [CHANGELOG.md](CHANGELOG.md). Die
Wörterbücher sind eigene Downloads unter den Lizenzen ihrer Quellen
(CC BY-SA 4.0, GPL-2.0-or-later); die App zeigt sie zu jedem Wörterbuch an.
