# Layout transition frame cost

One layout change on a 300 node graph, timed over the 40 frames it animates for. A frame covers
composition, layout and draw of the whole viewer. Recompositions count node and edge content calls.

## Running it

```
./gradlew :core:benchmark
```

Writes to `core/build/reports/benchmarks/layout-transition.txt`

## Recording a run

Numbers only compare within one machine. Append a section at the end of this file:

```markdown
## YYYY-MM-DD, what changed

Machine, OS, JDK, Compose version, runs taken.

| build | scenario             | median | p90 | total | node recomps | edge recomps |
|-------|----------------------|--------|-----|-------|--------------|--------------|
|       | 300 nodes, 300 edges |        |     |       |              |              |
|       | 300 nodes, no edges  |        |     |       |              |              |
```

## 2026-07-26, shared layout transition

One animation for the graph with nodes placed from a deferred read, replacing one
`animateOffsetAsState` per node and two per edge. Apple M2 Pro, macOS 26.5.2, JDK 23.0.2, Compose
Multiplatform 1.11.1, median of 2 runs.

| build  | scenario             | median   | p90      | total  | node recomps | edge recomps |
|--------|----------------------|----------|----------|--------|--------------|--------------|
| before | 300 nodes, 300 edges | 10795 us | 12386 us | 440 ms | 300          | 11609        |
| after  | 300 nodes, 300 edges | 2064 us  | 2613 us  | 86 ms  | 300          | 11400        |
| before | 300 nodes, no edges  | 3622 us  | 4029 us  | 144 ms | 300          | 0            |
| after  | 300 nodes, no edges  | 478 us   | 557 us   | 20 ms  | 300          | 0            |

Edge recompositions are unchanged by design: `edgeContent` takes endpoints by value, so a moving
edge resolves them in composition.
