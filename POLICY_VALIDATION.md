# Holdout-Validierung der Policy

## Kandidat und Training

- Champion: `config/genomes/champion-9999.properties` (`generation-2-2`)
- Training: `./run.sh evolve 2 4 50 9999`
- Trainings-Deal-Seeds: `9999` bis `10048`
- Trainingsfitness des Champions: `0,85 ct/Spiel`
- Der frühere `champion-8888` wurde mit verzerrter Startspielerrotation trainiert und ist kein gültiger Kandidat für diesen Nachweis.

## Verfahren

Für jeden Holdout-Deal wurden zwei Arme mit demselben Deal-Seed gespielt:

1. Behandlung: Champion plus drei Baseline-Agenten.
2. Kontrolle: vier Baseline-Agenten.

In beiden Armen wurde der Startspieler pro Deal identisch gesetzt. Der Champion wurde für jede Dealposition einmal auf jeden der vier Sitze gesetzt. Dadurch umfasst ein unabhängiger Deal vier gepaarte Sitzspiele je Policy. Die Auswertung vergleicht den Netto-Cent-Ertrag des Champions mit dem Baseline-Spieler auf demselben Sitz. Das 95%-Intervall wird über die unabhängigen Deal-Differenzen berechnet, nicht über die vier Sitzspiele als vermeintlich unabhängige Stichproben.

## Ergebnisse

| Holdout-Seeds | Deals | Sitzspiele je Policy | Champion ct/Spiel | Baseline ct/Spiel | Gepaarte Differenz | 95%-Intervall | Deals mit positivem Delta |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 20261101–20262100 | 1.000 | 4.000 | 4,97 | 0,00 | +4,97 | [+2,95; +6,99] | 364/1.000 |
| 20262101–20265100 | 3.000 | 12.000 | 2,91 | 0,00 | +2,91 | [+1,64; +4,18] | 1.061/3.000 |
| 20265101–20266100 | 1.000 | 4.000 | 3,14 | 0,00 | +3,14 | [+0,90; +5,37] | 356/1.000 |

Alle Seedbereiche sind disjunkt zum Training und voneinander disjunkt. In allen drei liegt die 95%-Untergrenze über null. Damit gibt es replizierte Evidenz, dass dieser Champion unter der getesteten Konfiguration und Fitnessdefinition besser abschneidet als die Baseline.

## Risiko und Rollen

Im dritten Holdout betrug die Standardabweichung der Deal-Differenz 36,14 ct, der Median 0,00 ct, das 10%-Quantil -30,00 ct und der mittlere schlechteste Dezil -60,98 ct. Der positive Mittelwert ist also kein typischer Gewinn pro Deal; die Erträge sind deutlich schief verteilt.

Ausgewählte Kandidatenwerte im dritten Holdout:

| Spielart/Rolle | Partien | Ø Netto ct/Partie | P10 | CVaR10 |
| --- | ---: | ---: | ---: | ---: |
| Bettel / Soloist | 55 | -35,45 | -300,00 | -300,00 |
| Farbsolo / Soloist | 69 | -82,17 | -480,00 | -608,57 |
| Farbwenz / Soloist | 126 | -168,33 | -540,00 | -632,31 |
| Farbsolo / Verteidiger | 377 | +36,31 | -100,00 | -158,95 |
| Farbwenz / Verteidiger | 406 | +44,78 | -100,00 | -127,32 |
| Rufspiel / Verteidiger | 1.374 | -32,68 | -160,00 | -256,23 |
| Rufspiel / Rufer | 807 | +32,22 | -120,00 | -183,21 |
| Rufspiel / Mitspieler | 690 | +40,43 | -80,00 | -148,12 |

Die Gesamtrate verdeckt erhebliche Rollenschwächen: als Farbsolo-/Farbwenz-Soloist und als Rufspiel-Verteidiger verliert der Kandidat im Mittel. Der nächste Policy-Schritt sollte die Vertragswahl und die Solisten-Handbewertung verbessern; bloß mehr Mutation wäre hier kein sinnvoller nächster Schritt.

## Mittelwert- gegen Risiko-Fitness

Auf den Seeds `20270101–20271100` wurden zwei eingefrorene Genome mit derselben Baseline und denselben Deals verglichen:

| Policy | Training | Netto ct/Spiel | SD der Deal-Differenz | Median | P10 | CVaR10 | 95%-Intervall der Differenz |
| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: |
| `champion-9999` | Mittelwert-Fitness | 5,78 | 34,52 | 0,00 | -20,00 | -48,53 | [+3,64; +7,92] |
| `champion-12345` | Risiko-Fitness | 3,22 | 22,78 | 0,00 | 0,00 | -13,65 | [+1,80; +4,63] |

Die Risiko-Fitness reduziert die Streuung und den Verlust im schlechtesten Dezil deutlich, kostet aber 2,56 ct/Spiel im Mittel. Im Rollenvergleich verbessert sich der Farbwenz-Soloist (`-178,51` auf `-135,39` ct/Partie), der Farbsolo-Soloist verschlechtert sich (`-71,08` auf `-104,18` ct/Partie). Die Änderungen sind daher noch kein allgemein besserer Regelsatz; das Ergebnis hängt von der gewünschten Risiko-/Ertragsabwägung ab.

## Soloisten-Profile

Die Startpopulation wurde um Profile für Trumpfkontrolle, Nebenfarben und sichere Bettel-Hände erweitert. Danach wurde `champion-56789` mit Seed `56789` trainiert und mit dem vorherigen `champion-34567` auf denselben Holdout-Seeds `20300101–20301100` verglichen:

| Policy | Netto ct/Spiel | 95%-Intervall gegen Baseline | SD | P10 | CVaR10 |
| --- | ---: | ---: | ---: | ---: | ---: |
| `champion-34567` | 1,24 | [+0,56; +1,92] | 10,98 | 0,00 | -1,28 |
| `champion-56789` | 17,03 | [+14,10; +19,95] | 47,24 | -20,00 | -49,65 |

Deskriptiv verbesserte sich der Farbsolo-Soloist von `-192,83` (159 Partien) auf `-129,57` ct/Partie (69 Partien); der Farbwenz-Soloist von `-161,27` (173 Partien) auf `-59,02` ct/Partie (61 Partien). Beide Rollen bleiben negativ. Die neue Policy erzielt deutlich mehr Gesamtertrag, aber auch erheblich höheres Tail-Risiko. Die Rollenzahlen sind wegen der verschiedenen Ansagehäufigkeiten nicht als gepaarter Kausalnachweis zu verstehen; das Intervall oben vergleicht jede Policy separat mit der Baseline.

Die Genomprofile helfen somit, den Trade-off sichtbar zu machen, lösen die Soloistenentscheidung aber noch nicht. Als nächster fachlicher Schritt sollte eine bessere Schätzung des erwarteten Spielwerts pro Vertrag entwickelt werden, bevor weitere Gewichte aggressiv optimiert werden.

## Kalibrierter Expected Value

Das Modell `config/contract-value-model.properties` wurde aus 300 Deals mit Seed `314159` und 3.522 vollständigen Rollout-Partien kalibriert. Die Trainingsgegner waren die Baseline-Bots; die Holdout-Seeds sind davon unabhängig.

Der risk-adjustierte Lauf `./run.sh evolve 2 9 20 901234` erzeugte `champion-901234`. Auf 1.000 Holdout-Deals ab Seed `20400101` erzielte er `8,44 ct/Spiel`, 95%-Intervall `[+6,46; +10,42]`, SD `31,97`, P10 `0,00` und CVaR10 `-15,75`.

Der feature-only `champion-56789` wurde auf exakt denselben Seeds mit `6,95 ct/Spiel`, SD `40,95`, P10 `-30,00` und CVaR10 `-62,75` gemessen. Deskriptiv ist der EV-kalibrierte Kandidat damit besser und hat weniger Tail-Risiko. Ein direktes, gepaartes Kandidat-gegen-Kandidat-Konfidenzintervall wird noch nicht berechnet; die getrennten Intervalle gegen Baseline beweisen diesen Unterschied nicht allein.

Der mittlere Nettoertrag verbesserte sich im Holdout für Farbsolo-Soloist von `-147,74` auf `-40,91 ct/Partie` und für Farbwenz-Soloist von `-67,87` auf `-39,17 ct/Partie`. Beide Werte bleiben negativ. Das EV-Modell ist somit ein ermutigender Zwischenschritt, noch keine fertige Solo-Strategie.

## Direkter Genomvergleich

Der Holdout-CLI kann zwei Genome direkt paaren. Auf 1.000 neuen Deals (`20500101–20501100`) spielte `champion-60000` gegen `champion-901234`, jeweils mit denselben Baseline-Mitspielern, Sitzpositionen und Startspielern:

- `champion-60000`: `13,37 ct/Spiel`
- `champion-901234`: `9,76 ct/Spiel`
- Gepaarter Vorteil: `+3,60 ct/Spiel`, 95%-Intervall `[+1,53; +5,68]`, 374/1.000 Deals positiv

Das ist ein direkter, unabhängiger Nachweis für die Verbesserung durch die letzte Evolutionsrunde auf diesem Seedbereich. Der Rollenbericht zeigt weiterhin ein hohes Farbwenz-Solo-Risiko; dieser Champion wird deshalb nicht automatisch aktiviert.

```sh
./run.sh validate config/genomes/champion-60000.properties 1000 20500101 60000 config/genomes/champion-901234.properties
```

## Farb-Wenz-Spezialisierung

Die Farbwenz-Features unterscheiden jetzt die vier Unter von den zusätzlichen Trümpfen der gewählten Farbe. Der Ober der Trumpffarbe ist Trumpf und hat dort Stärke 3; im normalen Wenz bleibt er eine Fehlkarte. Tests sichern Rangfolge, Trumpfzählung und Wahl der Trumpffarbe ab.

Der gezielt Farbwenz-orientierte `champion-345678` bestand den direkten Holdout gegen `champion-60000` nicht: auf denselben 1.000 Deals lag er `-21,00 ct/Spiel` zurück, 95%-Intervall `[-24,29; -17,70]`. Seine Spielart-/Rollenwerte zeigten außerdem übermäßige Bettel-Ansagen. Der spätere Kandidat mit höherer Bettel-Schwelle verringerte dieses Verhalten, war im Holdout aber ebenfalls schlechter als `champion-60000`. Beide Kandidaten werden nicht übernommen.

Zusätzlich wurde eine EV-Untergrenze für kalibrierte Soloansagen ergänzt. `champion-888888` wurde auf 1.000 neuen Deals direkt gegen `champion-60000` geprüft. Insgesamt lag er `-4,37 ct/Spiel` zurück, 95%-Intervall `[-6,90; -1,84]`; deshalb wird er nicht übernommen. Im Farbwenz-Soloisten-Split war der Kandidat deskriptiv positiv (`+36,43 ct`, 14 Partien), der Vergleich negativ (`-67,14 ct`, 21 Partien). Diese kleinen Stichproben sind nur ein Hinweis und kein Rollennachweis.

Damit war die erste Farbwenz-Iteration fachlich präziser, aber noch nicht besser. Anschließend wurde die Kartenpolicy separat evolviert: Jeder Kandidat spielte ausschließlich feste Farbwenz-Verträge als Soloist gegen drei Baseline-Agenten. Trumpffarbe, Caller-Sitz und Startspieler wurden rotiert. Die Fitness kombinierte den mittleren Nettoertrag mit einer Strafe für negative CVaR10-Dealwerte. Der Speziallauf ergänzt fehlende Farbwenz-Feature-Gene älterer Genome und evolviert nur die bei diesem Vertrag wirksamen Kartenkoeffizienten; beim Speichern bleiben die übrigen Gewichte des Ausgangsgenoms erhalten.

Der Speziallauf `./run.sh evolve-farbwenz 5 12 100 5567000` erzeugte `config/genomes/farbwenz-champion-5567000.properties`. Auf 1.000 unabhängigen Deals (Holdout-Seed `5570000`) gegen `champion-60000` lag der gepaarte Vorteil bei `+33,79 ct/Spiel`, 95%-Intervall `[+31,36; +36,21]`; gegen die frische allgemeine `champion-4456789`-Policy waren es auf weiteren 1.000 Deals `+42,76 ct/Spiel`, Intervall `[+40,30; +45,22]`. Beide Tests rotierten Trumpffarbe und Soloist-Sitz und verwendeten denselben gemischten Gegnerpool pro Vergleichsarm. Das ist Evidenz für die feste Farbwenz-Soloisten-Kartenpolicy, nicht für bessere Ansagen oder eine generell bessere Gesamtpolicy. Der Kandidat wird deshalb nicht automatisch zum allgemeinen Champion.

Reproduktion des unabhängigen Holdouts:

```sh
./run.sh validate-farbwenz config/genomes/farbwenz-champion-3456793.properties config/genomes/champion-60000.properties 1000 3456794
```

## Ausgewogene Vertragsmatrix

Der frühere Matrix-Holdout mit 13 Varianten ist historisch und enthält weder Ramschwertung noch balancierte Rollen. Der aktuelle Matrix-Kandidat `matrix-champion-7788992` wurde mit 16 Varianten und Rollen-Szenarien trainiert. Rollen mit mindestens 30 unabhängigen Deals und positivem Auswahlintervall wurden als `role-composite-7788992` komponiert.

Auf einem getrennten Holdout mit 1.000 Verteilungen je Variante (220.000 gepaarte Spiele pro Policy) lag das Komposit `+0,81 ct/Spiel` vor `champion-60000`, 95%-Intervall `[+0,67; +0,95]`. Der Vorteil kam fast vollständig aus Geier: Soloist `+21,11 ct/Spiel`, Verteidiger `+4,02 ct/Spiel`. Farbsolo war klein, Rufspiel/Wenz/Farbwenz/Bettel/Ramsch/Kreuzbock lagen nahe null. Die Rolle Geier-Soloist hatte im natürlichen Spiel aber nur fünf Ansagen; die feste Vertragsmessung belegt daher die Kartenwahl, nicht die Geier-Ansagequalität.

Im separaten natürlichen 1.000-Deal-Holdout gegen denselben Vergleichschampion lag das Komposit bei `-0,38 ct/Spiel`, 95%-Intervall `[-1,10; +0,35]`. Das ist kein belastbarer Gesamtvorteil und auch kein eindeutiger Beleg einer Verschlechterung. Die Policy wird nicht als Gesamtstrategie freigegeben.

Reproduktion:

```sh
./run.sh evolve-matrix 3 8 10 7788992
./run.sh compose-matrix config/genomes/matrix-champion-7788992.properties config/genomes/champion-60000.properties 250 8900000 config/genomes/role-composite-7788992.properties
./run.sh validate-matrix config/genomes/role-composite-7788992.properties config/genomes/champion-60000.properties 1000 8920000
./run.sh validate config/genomes/role-composite-7788992.properties 1000 8930000 8900000 config/genomes/champion-60000.properties
```

## Ramschwertung

Ramsch wertet jetzt den höchsten Augenstand als Verlierer; bei gleichen Augen entscheidet zunächst die höhere Stichzahl, bei weiterem Gleichstand zahlen alle noch gebundenen Verlierer an die verbleibenden Gewinner. Ein Spieler mit mehr als 90 Augen gewinnt den Durchmarsch allein und erhält von jedem der drei anderen den Solo-Tarif. In diesem Fall werden Jungfrauen nicht berücksichtigt. Im normalen Ramsch verdoppelt jede Jungfrau den Einsatz erneut (Faktor `2^AnzahlJungfrauen`); die Abrechnung bleibt nullsummig. Die Regel- und Tariftests decken auch genau 90 Augen sowie Gleichstände ab.

## Verbleibende Freigabepunkte

Das separate Geier-Modell `config/contract-value-model-geier-20261104.properties` stammt aus 500 Deals (500 Geier-Rollouts). Im natürlichen 1.000-Deal-Vergleich auf identischen Seeds betrug der Kandidatenvorteil gegen `champion-60000` mit dem aktiven Modell `+0,12 ct/Spiel` (95%-Intervall `[-0,74; +0,97]`) und mit dem Geier-Schattenmodell `+0,07 ct/Spiel` (`[-0,04; +0,17]`). Keiner ist statistisch belastbar. Geier wurde beim Kandidaten 9-mal mit aktivem Modell und 1-mal mit Schattenmodell angesagt; diese seltenen Ereignisse reichen nicht zur Bewertung der Geier-Ansage. Das Schattenmodell bleibt deshalb inaktiv.

1. Geier-Kalibrierung mit deutlich mehr unabhängigen Deals und Gegnerstilen wiederholen; danach erneut denselben natürlichen Holdout-Vergleich fahren.
2. Natürliche Holdouts auf weitere unabhängige Seed-Blöcke erweitern und Mindeststichproben für seltene Vertrags-/Rollen-Paare festlegen.
3. Vor Freigabe Akzeptanzgrenzen für Gesamtmittelwert, Tail-Risiko und jede wichtige Rolle vereinbaren. Keine Policy automatisch aktivieren.

Rollen-Komposition und abschließender Holdout sind getrennte Schritte:

```sh
./run.sh compose-matrix config/genomes/matrix-champion-<Seed>.properties config/genomes/champion-60000.properties 1000 <AuswahlSeed> config/genomes/role-composite-<Seed>.properties
./run.sh validate-matrix config/genomes/role-composite-<Seed>.properties config/genomes/champion-60000.properties 1000 <neuerHoldoutSeed>
```

## Grenzen und Entscheidung

Das ist Evidenz, kein universeller Beweis: getestet wurden ein Champion, eine Baseline, die aktuelle Tarif-/Regelkonfiguration und die Schafkopf-Simulation. Die Intervalle verwenden eine Normalapproximation auf Deal-Ebene. Die Auszahlung ist schief verteilt: rund 35% der Deals hatten ein positives Delta, obwohl der mittlere Nettoertrag positiv war.

Keiner der Champions wird automatisch aktiviert. Vor Übernahme muss die Runde festlegen, ob mittlerer Ertrag oder geringeres Verlustrisiko Vorrang hat. Die Solo-Regeln benötigen anschließend gezielte Features und neue, unangetastete Holdouts. Reproduzierbare Aufrufe sind:

```sh
./run.sh validate config/genomes/champion-9999.properties 1000 20261101 9999
./run.sh validate config/genomes/champion-12345.properties 1000 20270101 12345
```