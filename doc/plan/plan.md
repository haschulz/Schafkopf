# Schafkopf: Umsetzungs- und Qualitätsplan

**Stand:** 15. November 2026  
**Zweck:** Reviewbarer Plan für eine separate Implementierungskonversation. Dieses Dokument ist die fortzuschreibende Quelle der Wahrheit für Status, Entscheidungen und Abnahmekriterien.

## Ziel

Eine offlinefähige Android-App für Schafkopf, die sich visuell am bayerischen Blatt orientiert, Regeln und Ansagefolge korrekt umsetzt, nachvollziehbar spielt und ihre Qualität nicht nur mit Gesamtstatistiken, sondern auch mit fachlich prüfbaren Situationen belegt.

## Geprüfter bisheriger Stand

Die Projekt-Konversationen vom 5. bis 15. November 2026 wurden mit dem aktuellen Arbeitsstand abgeglichen. Der Git-Arbeitsbaum enthält bereits Änderungen und ungetrackte Dateien. Vor jeder Implementierung müssen diese inventarisiert und erhalten werden; kein Reset, Restore oder pauschales Aufräumen.

### Richtig oder grundsätzlich vorhanden

- Der Regelkern stellt legale Züge, Bedienpflicht, Trumpf- und Kartenrangfolgen sowie Ergebnis- und Tarifberechnung bereit. Die Kartenhand kann nach Spielart sortiert und nicht legale Karten können abgedunkelt werden.
- Die Android-App ist lokal/offline spielbar. Sitzplätze, Augen, Stiche, letzter Stich, echte Bietrunde und Bot-Kartenspiel sind vorhanden.
- Der Ansageruf ist von der Spielwahl getrennt: „Spiele!/Auch!/Weiter“, danach Auswahl einer höher zulässigen Spielart. Die aktuell implementierte Grundrangfolge ist Sauspiel < Farbwenz < Wenz < Farbsolo; Wenz Tout und Farbsolo Tout liegen höher. Nur die zuvor freigegebenen Tout-Varianten dürfen angeboten werden.
- Ansagerunden werden als Ereignisse gespeichert und mit etwa 700 ms langen Sprechblasen an den vier Sitzen gezeigt. Die Partie wartet währenddessen mit der Kartenanimation.
- Rückhand-Abheben ist an die Sitzfolge gebunden; wenn ein Bot abhebt, wird ein zufälliger Schnitt simuliert. Die vier ersten Karten und eine menschliche Klopfen-Entscheidung sind im Android-Ablauf vorhanden.
- Ausgespielte Karten fliegen zum Tisch; ein abgeschlossener Stich wandert zum Gewinner.
- Spielstände können nach dem Start der Session gespeichert und wiederhergestellt werden. Nachkarteln bewertet Kartenentscheidungen mit gesampelten, zum bisherigen Spielverlauf passenden Kartenverteilungen.

### Teilweise vorhanden oder nicht belegt

- Die Kartenfronten sind weiterhin handgezeichnete Compose-Grafiken und entsprechen nicht dem Referenzblatt. `IMG_2692.heic` zeigt einen schräg fotografierten App-Bildschirm mit acht Karten; es ist eine Stilreferenz, aber kein vollständiger Kartensatz.
- Die Ansage- und Ausspielstrategie beruht wesentlich auf Genomgewichten und Heuristiken. Das Genom kann vorhandene Merkmale gewichten, aber keine fehlenden taktischen Regeln erfinden. Positive Gesamt-Holdouts beweisen keine gute Leistung jeder Spielart und Rolle; die Rollendokumentation weist weiterhin deutliche Schwächen aus.
- Die Merkmale und Trainingsziele haben fundamentale Fehlbewertungen zugelassen. Insbesondere kann eine gemeinsame Trumpf-/Spitzenzahl die Ober-Unter-Rangfolge verwässern; die endgültige Policy kann außerdem eine einfache Heuristik überstimmen.
- Das aktuelle Spiel speichert für die Bots nicht ausreichend, warum ein konkretes Spiel oder eine Karte gewählt wurde. Die gespeicherten Ansageblasen zeigen den Ablauf, nicht die Bewertungsmerkmale oder Kandidatenscores.
- Der Nachkarteln-Prototyp prüft Kartenentscheidungen, nicht die Qualität der ursprünglichen Spielansage. Die Android-Oberfläche verwendet dafür nur 40 Stichproben; das ist keine belastbare Gewinnwahrscheinlichkeitsmessung.
- Der Einsatzfaktor wird als Gesamtwert angezeigt, aber die Zahl und Identität der Klopfer sowie Klopfen, Stock, Kontra und Retour werden nicht sichtbar getrennt. Stock bis maximal 32-fach ist nicht in den Android-Spielablauf integriert; „alle passen“ führt dort derzeit direkt zu Ramsch.
- Ein versehentlich gestartetes „Auch!“ kann in der nachfolgenden Spielwahl nicht mit einer expliziten Zurück-Aktion aufgehoben werden. „Weiter“ ist kein gleichwertiges Zurück, weil es die Bietrunde abschließt.
- Ein Abbruch während Abheben oder nach den vier Klopfkarten wird nicht wie ein laufender Spielstand wiederhergestellt; die Zwischenhände liegen vor Session-Erstellung nur im UI-Speicher.
- Eine Kamera-/Kartenerkennung ist nicht implementiert. Die App erzeugt virtuelle Karten; sie scannt kein physisches Blatt.

## Arbeitsregeln

1. Vor dem ersten Code-Edit den bestehenden Git-Status, relevante Tests und ungetrackte Dateien protokollieren. Bestehende Änderungen niemals verwerfen.
2. Regelentscheidung, Strategieempfehlung und tatsächlicher Spielausgang getrennt dokumentieren. Eine verlorene einzelne Partie beweist nicht, dass die Gewinnchance exakt null war.
3. Fachliche Grundregeln als lesbare, testbare Regeln implementieren. Lernen/Optimierung darf diese Regeln nicht umgehen oder illegale Züge erzeugen.
4. Kein Genom und kein Modell wird automatisch zum Standard. Kandidaten werden erst nach Szenariotests und unabhängiger, rollenbezogener Validierung aktiviert.
5. Dieses Dokument nach jedem Arbeitspaket aktualisieren: Status, geänderte Dateien, Testnachweis, offene Punkte und gegebenenfalls neue Entscheidungen.

## Agent-Workflow

- `/plan-review` prüft diesen Plan und den nächsten angefragten Arbeitsschritt lesend. Bei unklaren Anforderungen stellt der Agent gezielte Rückfragen und stoppt, statt Annahmen zu treffen.
- `/plan-execute` implementiert genau ein vom Nutzer benanntes oder eindeutig nächstes Arbeitspaket, führt die passenden Tests aus und aktualisiert dieses Dokument mit den tatsächlichen Nachweisen.
- Kein automatischer Commit, Branch, Worktree, PR, Jira-Vorgang oder Team-/Nutzungsprotokoll. Bestehende Änderungen bleiben unangetastet.
- Empfohlener Start in einem neuen Chat: `/plan-review` für den konkreten nächsten Schritt, danach `/plan-execute Arbeitspaket 0`.

## Arbeitspakete

### 0. Bestand und Regelvertrag einfrieren

- Den aktuellen schmutzigen Arbeitsbaum sichern und Änderungen nach bereits umgesetzt, offen und nicht zugehörig sortieren.
- Die bestätigten Regeln aus den früheren Gesprächen in einer kompakten, verbindlichen Tabelle festhalten: Ansagerangfolge, erlaubte Varianten, Vorhand/Rückhand, Klopfen, Stock, Kontra/Re, Tarif und regionale Optionen.
- Die Grundrangfolge gegen `GameBiddingRules` und die Android-Angebote testen. Keine neuen Spielarten oder Tout-Varianten ohne Freigabe hinzufügen.
- Offene regionale Regeln als explizite Einstellungen behandeln; Turnier-/Schafkopfschul-Regeln bleiben die vereinbarte Basis.

**Abnahme:** Regelvertrag und aktueller Code widersprechen sich nicht; bestehende Tests laufen; keine vorhandenen Änderungen wurden entfernt.

### 1. Ansage, Undo, Verdopplung und Wiederherstellung

- Eine explizite `Zurück`-Aktion für die noch unbestätigte menschliche „Spiele!/Auch!“-Entscheidung ergänzen. Sie setzt nur diese Aktion zurück, bewahrt frühere Ansagen und lässt dieselbe Person weiter am Zug.
- Einsatzbestandteile als strukturierte Daten speichern: einzelne Klopfer, Stock-Faktor, Kontra/Re und daraus berechneter Gesamtfaktor.
- Den vereinbarten Stock-Ablauf bei ausbleibender Spielansage in Android integrieren und die vereinbarte Obergrenze von 32-fach durchsetzen.
- Den Einsatz am Tisch als angefragte Münz-/Punktdarstellung sowie als eindeutige Faktoraufschlüsselung anzeigen. Beispiel: „3 Klopfer · Stock ×4 · Gesamt ×32“.
- Zwischenzustände für Mischen, Abheben, erste vier Karten/Klopfen, Bietrunde, Spielwahl und Kartenspiel in den Persistenzvertrag aufnehmen.

**Abnahme:** Tests für „Zurück“ nach „Auch!“, „Weiter“ nach „Zurück“, Mehrfach-Klopfen, Stockgrenze, Einsatzberechnung und Prozess-Wiederherstellung; Pixel-Test für die Einsatzanzeige.

### 2. Bayerisches Kartendesign

- Vor der Umsetzung entscheiden: echte Bildassets aus einem freigegebenen Kartensatz oder eigens illustrierte Karten nach der Referenz.
- Für originalgetreue Bildassets einen vollständigen, rechtmäßig nutzbaren Satz mit 32 Vorderseiten und passender Rückseite beschaffen. Geeignet sind gerade, scharfe, reflexionsfreie Einzelbilder oder Scans mit sichtbarem Kartenrand und einheitlichem Maßstab.
- Der vorhandene Screenshot mit acht Karten reicht zur Bewertung von Farben, Indizes und Stil, aber nicht zur originalgetreuen Erstellung der übrigen Karten. Kartengrafiken aus dem Internet nur verwenden, wenn Nutzungsrecht/Lizenz geklärt sind.
- Kartenindizes, bayerische Farben, Ober/Unter/König und Zahlenkarten als zusammengehöriges Set prüfen; Lesbarkeit auch in der gefächerten Hand und bei ausgegrauten Karten sichern.
- Physisches Scannen per Kamera getrennt spezifizieren: Aufnahmeführung, Kartenerkennung, Korrektur vor Spielstart und Tests. Es ist nicht Teil dieses UI-Kartenassets-Arbeitspakets.

**Abnahme:** vollständiger Satz ohne Platzhalter, visuell auf dem Pixel geprüft, alle 32 Karten eindeutig erkennbar, korrekte Zuordnung zu den Engine-Karten und dokumentierte Asset-Rechte.

### 3. Strategie: Regeln zuerst, Lernen danach

- Eine Strategiematrix nach Spielart, Rolle, Sitzposition und Phase erstellen: Klopfen, Spielansage, Ausspiel, Zugeben, Schmieren/Stechen und Endspiel.
- Die Fachregeln aus den vom Nutzer genannten Strategiequellen und Beispielen in gezielte Tests überführen. Konventionen sind spielart- und situationsabhängig, keine universellen Verbote.
- Kartenrang explizit modellieren: Ober und Unter getrennt zählen und bewerten; Trumpfhöhe, Trumpflänge, Trumpffarbe, Beikarten, Augen, Einzellkarten und mögliche Verlierer getrennt erfassen.
- Die festen Grundlinien pro Rolle testen: zum Beispiel hoher Trumpf des Soloisten zum Ziehen gegnerischer Trümpfe, Fehlfarben-Eröffnung der Verteidigung, wenn legal und sinnvoll; im Sauspiel andere Such-, Partner- und Signalsituationen.
- Für Ansagen nicht nur einen gewichteten Handscore verwenden. Alle zulässigen unbekannten Verteilungen aus den verbleibenden Karten bilden, Sichtinformationen berücksichtigen und Kandidaten gegen mehrere nachvollziehbare Gegner-Policies ausspielen.
- Gewinnwahrscheinlichkeit und erwartete Auszahlung einschließlich Einsatz, Schneider/Schwarz und Laufenden ausweisen. Die Wahrscheinlichkeits-PDF liefert Verteilungswissen (unter anderem den Erwartungswert von 3,5 Trümpfen bei acht Karten), aber allein keine Gewinnchance für ein konkretes Solo.
- Dasselbe belief-/rolloutbasierte Verfahren nach jeder Karte aktualisieren: gespielte Karten, Bedienpflicht, bekannte Fehlfarben, Sitzposition und Ansagen begrenzen mögliche Gegnerhände.
- Genetische Optimierung erst danach einsetzen, um klar definierte, bereits getestete Merkmale zu optimieren. Sie darf weder die Grundregeln noch die vereinbarten Konventionen umgehen.

**Pflichtszenarien:** Hans’ gespeichertes Gras-Solo vom 15. November; ein Farbsolo ohne Ober; ein Blatt mit vielen niedrigen gegenüber wenigen hohen Trümpfen; das vom Nutzer beschriebene Gras-Ober-Signal im Sauspiel; Verteidiger-Ausspiel; Sauspiel-Suche/Partnerverhalten; Klopfen nach vier Karten. Für jedes Szenario erwartete Aktion und fachliche Begründung vorab festhalten.

**Abnahme:** Tests sichern Merkmale und finale Aktionswahl ab (nicht nur Zwischenwerte); ein Bot kann seine Ansage und Karte mit den entscheidenden Merkmalen erklären; kein einzelnes Gesamt-Fitnessmaß kann eine klar schlechte Rolle verdecken.

### 4. Trainings- und Freigabeprozess

- Trainings-, Entwicklungs- und Holdout-Deals strikt trennen; für direkte Policyvergleiche dieselben Deals, Sitze und Startspieler verwenden.
- Gegen mehrere Gegnerstile und nach Spielart/Rolle auswerten. Gesamtmittel, Gewinnrate, erwartete Auszahlung, Verlust-Tail und Unsicherheit separat darstellen.
- Für seltene Spielarten Mindeststichproben festlegen; kleine Rollensplits ausdrücklich als unsicher markieren.
- Keine Verbesserung allein mit „mehr Spiele“ behaupten. Zuerst prüfen, ob der betreffende Fehler durch vorhandene Tests/Szenarien verhindert wird; danach erst die Statistik vergrößern.
- Nachkarteln um Ansagebewertung und Entscheidungsspur erweitern. Mehr Stichproben verwenden und Unsicherheit der Schätzung anzeigen, statt 40 Samples als belastbares Urteil auszugeben.
- Kandidaten nur nach vereinbarten Freigabekriterien übernehmen; Modell-ID, Seeds, Gegnerpool und Ergebnisse dokumentieren.

**Abnahme:** reproduzierbarer Bericht pro Spielart/Rolle, Szenariotests grün, unabhängiger gepaarter Holdout bestanden und keine automatische Aktivierung ohne Review.

## Noch benötigte Entscheidung/Vorlage

Für authentische Kartenbilder wird ein kompletter, rechtmäßig nutzbarer Satz aus 32 Kartenfronten benötigt oder die ausdrückliche Freigabe, ein neues eigenständiges Set nach der vorhandenen Referenz zu gestalten. Der vorhandene Screenshot ist dafür allein nicht ausreichend. Die übrigen offenen Regeln und die genannten Online-Quellen sollen in Arbeitspaket 0 aus den bisherigen Projektnachrichten übernommen werden, nicht erneut stillschweigend neu interpretiert werden.

## Verifizierte Projektunterlagen

- [POLICY_EVOLUTION.md](../../POLICY_EVOLUTION.md): Genom- und Trainingsverfahren samt bekannten Grenzen.
- [POLICY_VALIDATION.md](../../POLICY_VALIDATION.md): Holdouts und Rollenresultate; positive Gesamtwerte bedeuten keine Freigabe aller Rollen.
- [GameBiddingRules.java](../../src/main/java/schafkopf/rules/GameBiddingRules.java): aktuelle Ansagerangfolge.
- [GameBiddingAdvisor.java](../../src/main/java/schafkopf/ai/GameBiddingAdvisor.java), [SimpleBot.java](../../src/main/java/schafkopf/ai/SimpleBot.java): heutige Ansage- und Kartenwahl.
- [GameSession.java](../../src/main/java/schafkopf/state/GameSession.java), [BettingState.java](../../src/main/java/schafkopf/model/BettingState.java): Sitz-, Ansage-, Speicher- und Einsatzlogik.
- [PostGameAnalyzer.java](../../src/main/java/schafkopf/analysis/PostGameAnalyzer.java): aktueller Karten-Nachkarteln-Prototyp.
- [MainActivity.kt](../../android-app/app/src/main/java/schafkopf/android/MainActivity.kt), [CardArt.kt](../../android-app/app/src/main/java/schafkopf/android/CardArt.kt): Android-Ablauf und Kartenillustration.