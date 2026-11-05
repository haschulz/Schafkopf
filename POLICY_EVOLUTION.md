# Genetische Policy-Evolution

## Starten

```sh
./run.sh evolve [Generationen] [Population] [PartienJeSitz] [Seed]
./run.sh evolve-farbwenz [Generationen] [Population] [DealsJePolicy] [Seed]
./run.sh evolve-matrix [Generationen] [Population] [DealsJePolicy] [Seed]
./run.sh calibrate [Deals] [Seed] [Ridge] [Modellpfad]
./run.sh validate <Kandidat.properties> [Deals] [HoldoutSeed] [TrainingsSeed] [Vergleich.properties]
./run.sh validate-matrix <Kandidat.properties> <Vergleich.properties> [Deals] [Seed]
./run.sh compose-matrix <Spezialist.properties> <Referenz.properties> <Auswahldeals> <Seed> <Output.properties>
```
Ein optionaler Modellpfad erlaubt eine Schattenkalibrierung, ohne das aktive Modell zu überschreiben. Dieses Modell wird erst mit `-Dschafkopf.valueModel=<Pfad>` verwendet. Beispiel: `./run.sh calibrate 500 20261104 10 config/contract-value-model-geier-20261104.properties`.

Ohne Argumente werden 2 Generationen, 4 Genome, 1 Partie je Sitz und Seed `20261101` verwendet. Zum Beispiel:

```sh
./run.sh evolve 2 4 1 20261101
```

Der beste Kandidat wird unter `config/genomes/champion-<Seed>.properties` gespeichert. Entscheidungen landen zeilenweise in `config/traces/evolution-<Seed>.jsonl`. Das aktive Baseline-Genom wird dabei nicht überschrieben.

`calibrate` simuliert für zufällig verteilte Hände mehrere mögliche Ansagen als vollständige Partien gegen einen festen Pool aus defensiven, ausgewogenen und aggressiven Policies. Eine ridge-regularisierte Regression schätzt daraus den erwarteten Netto-Cent-Wert getrennt nach Spielart. Standardmäßig werden 1.000 Deals ausgewertet. Ohne Modellpfad wird `config/contract-value-model.properties` ersetzt und automatisch geladen. Mit einem eigenen Pfad bleibt das aktive Modell erhalten; das Schattenmodell wird nur über `-Dschafkopf.valueModel=<Pfad>` verwendet. Die Modell-ID speichert Seed, Stichprobenzahl und Poolgröße.

Einen Champion im Training ausprobieren:

```sh
JAVA_TOOL_OPTIONS="-Dschafkopf.genome=config/genomes/champion-20261101.properties" ./run.sh play
```

## Modell

Das Genom enthält versionierte numerische Gewichte. Es bewertet Merkmale für Klopfen, Rufspiel- und Soloansagen sowie legale Kartenkandidaten. Der genetische Algorithmus verwendet Turnierselektion, uniformes Crossover, Gauß-Mutation und Elitismus. Die Debatte ist nicht Bestandteil der Fitnessberechnung; die bisherige Manöverkritik läuft nur noch mit `--debate`.

`evolve-farbwenz` optimiert die Kartenwahl getrennt von der Ansage: Es spielt feste Farbwenz-Verträge mit rotierter Trumpffarbe, Soloist-Sitz und Startspieler gegen einen festen Pool aus vorsichtigen, offensiven und trumpforientierten Policies. Der Fitnesswert berücksichtigt Mittelwert und negatives CVaR10 der Deal-Renditen. Der Speziallauf evolviert nur `card.FARBWENZ.*` und übernimmt die resultierenden Werte in das Ausgangsgenom, ohne dessen übrige Spielarten zu überschreiben. Jeder Kandidat muss anschließend mit `validate-farbwenz` auf neuen Deals gegen ein Vergleichsgenom geprüft werden; der allgemeine Champion wird nicht automatisch ersetzt.

`evolve-matrix` optimiert Kartenwahl-Gewichte über 16 feste Varianten: drei Rufspiel-Farben, vier Farbsoli, Wenz, Geier, vier Farb-Wenze, Bettel, Ramsch und Kreuzbock. Soloverträge werden als Soloist und mit jedem der drei Verteidigersitze getestet; Rufspiel wird als Rufer, Partner und Verteidiger geprüft. Kreuzbock rotiert Team A/B, Ramsch alle vier Sitze. `validate-matrix` liefert gepaarte Kennzahlen je Vertragsart und je Rolle.

`compose-matrix` übernimmt nur Rollen mit mindestens 30 unabhängigen Deals und positivem 95%-Intervall. Ramschrollen hängen vom Spielergebnis ab und werden nicht als vorhersagbare Kartenrollen komponiert. Den daraus gespeicherten Kandidaten anschließend mit neuen Seeds durch `validate-matrix` prüfen.

Die Startpopulation enthält neben der Baseline gezielte Profile für konservative Ansagen, Trumpfkontrolle im Solo, Nebenfarbenstruktur, sichere Bettel-Hände, stärkeren Expected-Value-Einfluss und Farbwenz-Kontrolle der gewählten Trumpffarbe. Diese Profile sind Startpunkte, keine festgeschriebenen Ergebnisse.

Harte Regeln bleiben außerhalb des Genoms: Bedienpflicht, Trumpf- und Kartenrangfolge, erlaubte Spielzüge sowie Ergebnis- und Tarifberechnung. Eine Policy darf nur legale Karten auswählen.

Jede Generation spielt alle Genome auf denselben Deal-Seeds; die nächste Generation erhält einen reproduzierbaren, disjunkten Seed-Block. Sitz und Startspieler werden unabhängig rotiert. Die rohe Fitness ist der durchschnittliche Nettoertrag in Cent pro Partie. Für die allgemeine Selektion kommen Tail-Risiken hinzu: 10% des negativen Gesamt-CVaR10, 50% des schlechtesten Soloisten-CVaR10 und 50% eines negativen Farbwenz-Soloisten-Mittelwerts werden abgezogen. Rohwerte bleiben im Turnierergebnis sichtbar. Stock, Klopfen, Kontra/Re, Tarif, Schneider/Schwarz und Laufende beeinflussen den tatsächlichen Ertrag.

## Entscheidungsprotokoll

Jeder Trace enthält Genom-ID, Entscheidungsphase, Spieltyp, Spieler, legale Aktionen, gewählte Aktion, Kandidatenscores, Merkmale und die vorhandene Heuristikbegründung. Der JSONL-Writer wird im Turnierlauf getestet und die ausgegebenen Zeilen werden als JSON validiert.

## Aktuelle Grenzen

- Das Startgenom bildet die bisherigen Rufspiel- und Klopfentscheidungen ab; andere Spielarten starten mit einfachen Featuregewichten.
- Die Trainingsfitness ist verrauscht und kein Freigabekriterium; Konfidenzintervalle und Rollenwerte kommen aus den separaten Holdout-Befehlen.
- Der erste Lauf ist absichtlich klein. Für aussagekräftige Regeln müssen `PartienJeSitz` und die Zahl der Seeds deutlich erhöht werden.
- Neue Featuretypen werden im Code ergänzt; der genetische Algorithmus erfindet keine beliebigen Java-Regeln.
- Ein Champion wird gespeichert, aber nicht automatisch als aktive Policy gesetzt. Erst nach Holdout-Vergleich sollte er übernommen werden.
- Die Expected-Value-Kalibrierung ist nur so repräsentativ wie ihre Rollout-Gegner und Deal-Anzahl; sie ist keine perfekte Gewinnwahrscheinlichkeit.
- Für den direkten Nachweis zwischen zwei Genomen dieselben Holdout-Deals, Sitze und Startspieler verwenden; bloße Mittelwertvergleiche aus separaten Läufen sind nicht gepaart.
- Der genetische Algorithmus verändert Gewichte vorhandener Merkmale. Neue taktische Regeln oder Merkmale muss weiterhin jemand im Code entwerfen und testen.