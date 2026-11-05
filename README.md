# Schafkopf

Java-21-Simulator für Schafkopf mit regelkonformer Engine, taktischen Bots, konfigurierbaren Policies und Werkzeugen für reproduzierbare Evolution und Holdout-Tests.

## Voraussetzungen

- JDK 21
- Maven 3.9 oder kompatible Version
- Für `run.sh`: Bash

## Starten

Interaktive Partie starten:

```sh
./run.sh play
```

Das Skript kompiliert die Java-Dateien nach `bin/` und startet die Anwendung. Ohne `play` oder einen Analysebefehl startet das ausführliche Simulationsprogramm.

Alle Tests ausführen:

```sh
mvn clean test
```

## Spielarten

Die Engine kennt Rufspiel, Farbsolo, Wenz, Farbwenz, Geier, Bettel, Ramsch und Kreuzbock. Die Karten- und Spielregeln sowie die Auszahlung liegen im Projektcode; eine Policy kann nur unter den regelkonformen Zügen wählen.

## Policies

Das aktive Genom wird standardmäßig aus `config/policy-genome.properties` geladen. Es enthält Zahlengewichte für programmierte Merkmale, etwa Trumpfstärke, Augen, Stichgewinn und Heuristikvorschlag. Die Gewichte können Kartenwahl und Ansagen beeinflussen; der genetische Algorithmus erzeugt keine neuen Java-Regeln.

Ein anderes Genom für eine Partie laden:

```sh
JAVA_TOOL_OPTIONS="-Dschafkopf.genome=config/genomes/champion-60000.properties" ./run.sh play
```

Der Expected-Value-Schätzer liegt standardmäßig unter `config/contract-value-model.properties`. Ein separates Modell kann mit `-Dschafkopf.valueModel=<Pfad>` geladen werden.

## Evolution und Validierung

Alle Befehle akzeptieren optionale Parameter in der Reihenfolge unten. Seeds machen Läufe reproduzierbar; Trainings- und Holdout-Seeds sollten verschieden sein.

```sh
./run.sh evolve [Generationen] [Population] [PartienJeSitz] [Seed]
./run.sh evolve-farbwenz [Generationen] [Population] [DealsJePolicy] [Seed]
./run.sh evolve-matrix [Generationen] [Population] [DealsJePolicy] [Seed]
./run.sh calibrate [Deals] [Seed] [Ridge] [Modellpfad]
./run.sh validate <Kandidat.properties> [Deals] [HoldoutSeed] [TrainingsSeed] [Vergleich.properties]
./run.sh validate-farbwenz <Kandidat.properties> <Vergleich.properties> [Deals] [Seed]
./run.sh validate-matrix <Kandidat.properties> <Vergleich.properties> [Deals] [Seed]
./run.sh compose-matrix <Spezialist.properties> <Referenz.properties> <Deals> <Seed> <Ausgabe.properties>
```

`evolve` trainiert eine allgemeine Policy. `evolve-farbwenz` optimiert die Kartenwahl unter festen Farb-Wenz-Verträgen. `evolve-matrix` bewertet 16 feste Vertragsvarianten und rotiert die dafür relevanten Rollen. Die Matrix enthält drei Rufspiel-Farben, vier Farbsoli, Wenz, vier Farb-Wenze, Geier, Bettel, Ramsch und Kreuzbock.

`validate` prüft natürliche Ansagen und berichtet Ergebnisse nach Spielart und Rolle. `validate-farbwenz` isoliert die Kartenwahl bei fest angesagtem Farb-Wenz. `validate-matrix` prüft Vertrag und Rollen unter festen Ansagen. Gepaarte Tests verwenden dieselben Verteilungen für Kandidat und Vergleich; der Holdout sollte nicht zum Training oder zur Kandidatenauswahl verwendet werden.

`compose-matrix` übernimmt nur Rollen, deren Auswahldaten die Mindeststichprobe und ein positives 95%-Intervall erfüllen. Es speichert einen neuen Kandidaten, aktiviert ihn aber nicht. Diesen Komposit anschließend mit neuen Seeds durch `validate-matrix` und zusätzlich `validate` prüfen. Ein positiver Gesamtmittelwert allein beweist keine Verbesserung in jeder Spielart oder Rolle.

Beispielablauf:

```sh
./run.sh evolve-matrix 3 8 10 7788992
./run.sh compose-matrix config/genomes/matrix-champion-7788992.properties config/genomes/champion-60000.properties 1000 8900000 config/genomes/role-composite-7788992.properties
./run.sh validate-matrix config/genomes/role-composite-7788992.properties config/genomes/champion-60000.properties 1000 8920000
./run.sh validate config/genomes/role-composite-7788992.properties 1000 8930000 8900000 config/genomes/champion-60000.properties
```

## Dateien und Ausgaben

- `src/main/java/`: Spielmodell, Regeln, Bots, Simulator und Analyse
- `src/test/java/`: JUnit-Tests
- `config/schafkopf.properties`: Spiel- und Tarifkonfiguration
- `config/policy-genome.properties`: standardmäßig geladenes Genom
- `config/contract-value-model.properties`: aktives EV-Modell
- `config/genomes/`: gespeicherte Genome und Kandidaten
- `config/traces/`: JSONL-Entscheidungsprotokolle; wird nicht von Git erfasst
- `POLICY_EVOLUTION.md`: Trainingsbefehle und Evolutionsverfahren
- `POLICY_VALIDATION.md`: Holdout-Ergebnisse, Einschränkungen und Freigabehinweise

Build-Ausgaben liegen in `bin/` und `target/` und sind von Git ignoriert.
