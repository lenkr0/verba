# Verba

*Ein ruhiger Vokabel-Sammler für alle, die Bücher, Novels und Manga in einer anderen Sprache lesen.*

[English](README.md) · **Deutsch**

Markiere in einer beliebigen App ein Wort, sieh die Übersetzung in deine
Sprache und speichere es zusammen mit dem Satz, in dem du es gefunden hast –
später wiederholst du es mit genau diesem Satz. Ob englische Fantasy,
chinesische Web-Novels oder japanische Manga: Verba funktioniert für jedes
Sprachpaar gleich. Die App läuft komplett offline, hat keine Werbung, kein
Tracking, nicht einmal Internetzugriff und braucht keine Google-Play-Dienste.
Ein E-Ink-Modus macht sie auf E-Readern wie dem Boox Go 7 angenehm.

> **Stand: frühe Entwicklung.** Verba sammelt Wörter aus jeder App;
> Offline-Wörterbücher folgen mit der nächsten Version. Die Funktionen unten
> kommen Meilenstein für Meilenstein, siehe
> [Roadmap](../../issues?q=label%3Amilestone). Screenshots folgen bald.

## Funktionen

Schon da:

- **Wörter aus jeder App sammeln:** Text markieren und im Auswahlmenü
  Verba wählen, an Verba teilen oder aus der Wörterbuch-Funktion eines
  Readers schicken. Im Satz das gesuchte Wort antippen – Chinesisch und
  Japanisch werden auch in Wörter zerlegt – und mit Satz, Buch und
  Herkunfts-App speichern. Englische Wörter bekommen ihre Grundform
  („running“ → „run“). Bis die Wörterbücher kommen, tippst du die
  Übersetzung selbst ein.
- **Vokabelliste** mit dem letzten Satz und der Quelle jedes Worts.
- **Helles und dunkles Design**, wie im System oder von Hand gewählt.
- **E-Ink-Modus:** reines Schwarz-Weiß, keine Animationen, größere
  Tippflächen. Auf Boox-E-Readern schlägt Verba ihn beim ersten Start vor.
- **Englisch und Deutsch**, die Sprache lässt sich pro App einstellen.

Geplant, in dieser Reihenfolge:

- [ ] **Offline-Wörterbücher pro Sprachpaar** aus Wiktionary und anderen
  freien Wörterbüchern – zuerst Englisch → Deutsch, weitere Paare folgen.
- [ ] **Vokabelliste** mit Suche, Filtern, Bearbeiten und Zusammenführen von
  Duplikaten über die Grundform.
- [ ] **Lernen** mit Karteikarten und FSRS-Wiederholungsplanung; die Karte
  zeigt deinen Satz mit einer Lücke.
- [ ] **Manga und Bilder:** Texterkennung auf Screenshots (Teilen oder
  Schnelleinstellungs-Kachel); ein erkanntes Wort antippen und nachschlagen.
- [ ] **Satzübersetzung** (optional, offline wenn machbar).
- [ ] **Export und Backup:** CSV, JSON, AnkiDroid.
- [ ] **Startbildschirm-Widget** „Vokabel des Tages“.

## Verba benutzen

Verba taucht an drei Stellen auf:

- **Auswahlmenü:** Text markieren und im Menü „Verba“ wählen (oft unter ⋮).
  Das klappt in Apps, die nach Text-Werkzeugen suchen, z. B. Chrome oder
  Firefox; manche Apps bieten es nicht an.
- **Teilen:** Text markieren → Teilen → „In Verba speichern“. Geht in fast
  jeder App.
- **Lese-Apps mit externem Wörterbuch:** Kann dein Reader Wörter an
  ColorDict oder Aard 2 schicken, wähle eines davon als Wörterbuch – das
  Wort öffnet sich dann in Verba.

Deine eigene Sprache wählst du einmal unter Einstellungen → Übersetzen in.

## Datenschutz

- Verba fordert **keine Berechtigungen** an – nicht einmal Internetzugriff.
  Die CI schlägt fehl, wenn eine Bibliothek eine hinzufügen will
  (`tools/check-permissions.sh`).
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
[NOTICE.md](NOTICE.md), Änderungen in [CHANGELOG.md](CHANGELOG.md).
