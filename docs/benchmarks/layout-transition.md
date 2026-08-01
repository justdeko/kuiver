# Layout transition frame cost

One layout change on a 300 node graph, timed over the 40 frames it animates for. A frame covers
composition, layout and draw of the whole viewer. Recompositions count node and edge content calls.

Four scenarios, see `EdgeMode` in `ViewerScene`:

| scenario             | edges                                                 |
|----------------------|-------------------------------------------------------|
| 300 nodes, 300 edges | hand written `edgeContent`, full viewport canvas each  |
| 300 nodes, no edges  | none, the node baseline                               |
| 300 nodes, built-in  | `StyledEdgeContent` per edge, line plus arrow         |
| 300 nodes, batched   | the same drawing, from one canvas via `edgeStyle`     |

Only the last two draw the same thing, so only those two compare directly.

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
|       | 300 nodes, built-in  |        |     |       |              |              |
|       | 300 nodes, batched   |        |     |       |              |              |
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

## 2026-07-26, bounded edge canvases and the batched edge layer

Built-in edge composables sized to the edge instead of to the viewport, plus an opt-in layer that
draws every edge from one canvas. Apple M2 Pro, macOS 26.5.2, JDK 23.0.2, Compose Multiplatform
1.11.1, median of 2 runs.

| build | scenario             | median  | p90     | total  | node recomps | edge recomps |
|-------|----------------------|---------|---------|--------|--------------|--------------|
| after | 300 nodes, 300 edges | 1787 us | 1972 us | 74 ms  | 300          | 11400        |
| after | 300 nodes, no edges  | 494 us  | 673 us  | 21 ms  | 300          | 0            |
| after | 300 nodes, built-in  | 4972 us | 6100 us | 210 ms | 300          | 11400        |
| after | 300 nodes, batched   | 3146 us | 3607 us | 129 ms | 300          | 0            |

The batched layer takes 1.8 ms off the 5.0 ms frame for the same drawing. It removes composition
and layout of 300 edge composables per frame, 11400 recompositions over the transition. The draw
itself is what remains, and both modes pay it.

## 2026-07-26, measurement merged into the render pass

Nodes are subcomposed, measured and placed by one `SubcomposeLayout` instead of being measured in a
pass of their own and composed again to render. Apple M2 Pro, macOS 26.5.2, JDK 23.0.2, Compose
Multiplatform 1.11.1, median of 2 runs.

| build  | scenario             | median  | p90     | total  | node recomps | edge recomps |
|--------|----------------------|---------|---------|--------|--------------|--------------|
| before | 300 nodes, 300 edges | 2010 us | 2481 us | 83 ms  | 300          | 11400        |
| after  | 300 nodes, 300 edges | 1922 us | 2425 us | 81 ms  | 300          | 11400        |
| before | 300 nodes, no edges  | 507 us  | 721 us  | 22 ms  | 300          | 0            |
| after  | 300 nodes, no edges  | 510 us  | 690 us  | 23 ms  | 300          | 0            |
| before | 300 nodes, built-in  | 5075 us | 5796 us | 210 ms | 300          | 11400        |
| after  | 300 nodes, built-in  | 4736 us | 5567 us | 202 ms | 300          | 11400        |
| before | 300 nodes, batched   | 3208 us | 3828 us | 132 ms | 300          | 0            |
| after  | 300 nodes, batched   | 3364 us | 3779 us | 138 ms | 300          | 0            |

Transition frames are unchanged, within the noise of this machine: positions are still read in the
placement block only, so a frame of a transition re-places the nodes without recomposing or
re-measuring them. What this change removes is on the first frame, which the benchmark does not
time. Every node used to be composed three times before it settled - once to measure, twice to
render - and is now composed twice, one per layout generation that reaches the renderer. See
`NodeMeasurementTest.node content is composed once per layout generation`.
