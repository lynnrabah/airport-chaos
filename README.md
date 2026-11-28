# Airport Chaos: Flight Scheduling & Crisis Recovery Engine

CSC310 Algorithms and Data Structures, CP3 final project (Dr. Mohamed Watfa)

**Team:** Lynn Rabah (202502872), Hasan Shour (202508340)
**Language:** Java (no external libraries)

A command-line simulator that loads a synthetic flight file, keeps each runway's schedule conflict-free, handles crises (runway closure, emergency insert, mass cancellation), and reports the operational impact. This is an educational simulation, not real aviation software.

## Requirements

- JDK 17 or newer (the help text uses a Java text block, which needs Java 15+)
- No other dependencies

## Build and run

From the repository root:

```
javac -d out src/*.java
java -cp out Main                          # interactive numbered menu
java -cp out Main scripts/scenarioA.txt    # batch mode: run a command file
java -cp out TestRunner                    # automated tests
```

On Windows Command Prompt, use `src\*.java` in the `javac` line, or compile from your IDE.

## Repository layout

```
data/airport_flights.csv   official input, unchanged
scripts/                   scenario and benchmark command files
src/                       all source files (default package)
README.md
```

| Class | Role |
|---|---|
| `Main` | Entry point: batch mode (script file) or numbered menu |
| `CommandProcessor`, `Tokenizer` | Parses a command line and runs the matching command |
| `TimeUtil` | Converts `YYYY-MM-DD HH:MM` to minutes and back |
| `Flight`, `CsvLoader` | Flight record and validated CSV loading |
| `FlightRegistry` | Own hash table (universal hashing, linear probing, tombstones, rehashing) |
| `RunwayBST` | One unbalanced BST per runway, keyed by (start time, flight ID) |
| `Scheduler` | Keeps the registry and the five BSTs synchronized |
| `CrisisEngine`, `CrisisReport` | The three crisis scenarios and their reports |
| `SortUtil` | Stable counting sort (priority) and LSD radix sort (flight ID) |
| `DisruptionAnalyzer` | Divide-and-conquer maximum subarray plus O(n^2) reference check |
| `MatrixOps` | Flight-count matrix, classical and Strassen multiplication |
| `PerfectHash` | Two-level FKS perfect hashing for the 20 fixed rule IDs |
| `TestRunner`, `Benchmarks` | Automated tests and timing experiments |

## Commands

Command names are not case-sensitive. Flight IDs, airline codes and airport codes are case-sensitive. Timestamps must be in double quotes and written `"YYYY-MM-DD HH:MM"`. Every command except `LOAD`, `HELP` and `TRACE` requires a loaded file.

| Command | Syntax | What it does |
|---|---|---|
| `LOAD` | `LOAD <file>` | Resets all state, loads the CSV, reports flights loaded and rows rejected |
| `LIST_RUNWAY` | `LIST_RUNWAY <R1..R5>` | Lists a runway's flights in time order |
| `FIND` | `FIND <flight_id>` | Looks up a flight and shows the probe count |
| `ADD` | `ADD <id> <airline> <origin> <dest> <runway> "<start>" "<end>" <priority>` | Adds a flight if the interval is conflict-free |
| `CANCEL` | `CANCEL <flight_id>` | Cancels a flight (kept in the registry as CANCELLED) |
| `RESCHEDULE` | `RESCHEDULE <flight_id> "<new start>"` | Moves a flight, keeping its duration |
| `CHECK_CONFLICT` | `CHECK_CONFLICT <runway> "<start>" "<end>"` | Lists flights that conflict with the interval |
| `RUNWAY_CLOSURE` | `RUNWAY_CLOSURE <runway> "<from>" "<to>"` | Scenario A |
| `EMERGENCY_INSERT` | `EMERGENCY_INSERT <id> <airline> <origin> <dest> <runway> "<start>" "<end>" <priority>` | Scenario B |
| `MASS_CANCEL` | `MASS_CANCEL "<from>" "<to>"` | Scenario C |
| `SORT_PRIORITY` | `SORT_PRIORITY` | All flights, stable counting sort by priority |
| `SORT_ID` | `SORT_ID` | All flights, LSD radix sort by flight ID |
| `ANALYZE` | `ANALYZE` | Contiguous 5-minute bucket range with maximum disruption load |
| `BENCHMARK` | `BENCHMARK` | Runs the timing experiments |
| `STATS` | `STATS` | Counts by status and runway, hash load factor, probes, collisions |
| `TRACE` | `TRACE ON` or `TRACE OFF` | Shows BST paths, hash probes and sorting passes |
| `HELP` | `HELP` | Lists all commands |
| `MATRIX` | `MATRIX` | Builds the airport flight-count matrix, runs classical and Strassen, compares results |
| `PERFECT_HASH` | `PERFECT_HASH` | Demo of the FKS table: storage, present and absent lookups |

Errors (unknown command, wrong number of arguments, bad timestamp, unknown runway, and so on) are printed as `ERROR: ...` and never crash the program.

## Official scenarios

Each scenario script starts with its own `LOAD`, so they are independent and must not be chained.

Scenario A, runway closure (`scripts/scenarioA.txt`):
```
LOAD data/airport_flights.csv
STATS
RUNWAY_CLOSURE R1 "2026-11-27 09:00" "2026-11-27 11:00"
STATS
```

Scenario B, emergency insert (`scripts/scenarioB.txt`):
```
LOAD data/airport_flights.csv
EMERGENCY_INSERT EM90001 EM JFK BOS R1 "2026-11-27 09:00" "2026-11-27 09:05" 1
STATS
```

Scenario C, mass cancellation (`scripts/scenarioC.txt`):
```
LOAD data/airport_flights.csv
STATS
MASS_CANCEL "2026-11-27 14:00" "2026-11-27 15:00"
STATS
```

Run any of them with `java -cp out Main scripts/scenarioA.txt` (and so on).

## Recovery rule (Scenario A)

Affected flights are processed by priority (1 first), then original start time, then flight ID. Each gets the earliest free start at or after the end of the closure, in 5-minute steps, on its original runway, keeping its duration. A delay of more than 120 minutes from the original start means the flight is cancelled. Unaffected flights are never moved. This greedy rule is a teaching heuristic, not an optimal policy.

## Tests

```
java -cp out TestRunner
```

Covers CSV parsing and duplicate IDs, malformed timestamps, hash collisions/tombstones/resizing, BST root deletion, predecessor/successor, exact-boundary non-overlap, cross-runway independence, conflict insertion, the three scenarios (each from a fresh load), sorting stability, maximum-subarray reference match, Strassen on odd sizes, and registry/BST consistency after updates.

## Benchmarks

```
java -cp out Main scripts/benchmark.txt
```

where `scripts/benchmark.txt` contains `LOAD data/airport_flights.csv` followed by `BENCHMARK`. The output lists the environment (Java version, OS, processors), input sizes, timings and repeat counts for: hash lookups at different load factors, BST search/insert with sorted versus shuffled keys, counting/radix sort versus a comparison-sort baseline, and classical versus Strassen multiplication at several matrix sizes. Larger inputs are synthetic and generated with a fixed random seed; the official CSV is only the correctness baseline.

## Design notes

- Every time is stored as a `long` number of minutes since 1970-01-01 (the Unix epoch). Only differences matter, so conflicts, delays and 5-minute steps are plain arithmetic. Times are read and printed as `YYYY-MM-DD HH:MM`.
- Intervals are half-open: `[start, end)`. A flight ending at 09:20 does not conflict with one starting at 09:20. Different runways never conflict.
- Hash function: `h(k) = ((a*k + b) mod p) mod m` with `p = 2147483647` (2^31 - 1). `encode` is a polynomial over the ID's characters (base 131), reduced modulo `p` at every step. `a` and `b` are fixed constants, so runs are deterministic. Lookups compare the full ID strings, so encoding collisions cannot cause false matches.
- The registry resizes (doubles) when `(size + tombstones) / m` exceeds 0.5.
- Cancelled flights are removed from the BSTs but kept in the registry with status `CANCELLED`.
- Results are deterministic: no unseeded randomness anywhere.

## AI / tool-use disclosure

See the "AI/tool-use disclosure" section of the PDF report.
