# Native / Compose integration

## Source studies

Bundled `assets/compose-reference/` files are from the approved native implementation. They depend on its resources, palette and Compose libraries. Study/adapt them; they are not a drop-in SDK for unrelated projects.

- `CozyMotion.kt`: live motion/lifecycle policy, event clock, note arrival and pose functions.
- `WaterSpirit.kt`: cel-painted body, independent face/leaf, grounded transforms and bounded fireflies.
- `WeeklyGarden.kt`: real fractions mapped to soil, stems, leaves and blooms.

See [provenance.md](provenance.md) for the original source. Future use does not require that repository.

## Separate ownership

The feature/ViewModel owns committed data, action sequencing and the persistent daily marker. The design system owns drawing, phases and material/timing. A useful API shape is:

```kotlin
@Composable
fun RitualScene(
    progress: Float,
    actionEvent: Long = 0L,
    goalEvent: Long = 0L,
    animated: Boolean = false,
)
```

The static default supports deterministic screenshots/previews. Production deliberately enables it, then the shared policy combines it with lifecycle, OS preference and saved app preference. Publish quantity/cue coherently, as described in the motion contract.

Remember the first current token as baseline, even when nonzero. A later changed token plays. Falling below the goal after deletion does not clear the stored milestone. One `animateFloatAsState(progress)` cannot infer these behaviors.

## Drawing and performance

Cache paths and stable particle seeds. Transform articulated parts around physical pivots: feet/base stay grounded while compressing, shadow stays on the surface. Separate body lean, squash/stretch, flexible part, eyes and mouth.

Crossfade a stable palette value/data object. A reference-identity object allocated every frame can restart a crossfade. Do not crossfade the full scene to change a quantity; change state and play the local event. Keep grain static, clips bounded and particles finite.

Put note arrival on its graphics layer: short travel, gentle corner pivot, settled opacity. Keep its text accessible. Decorative canvases do not duplicate spoken labels. The garden host supplies localized weekdays and real amounts; do not invent medical thresholds when no goal exists.

## Checks and handoff

Test pose endpoints separately from event semantics. Exercise initial/fresh tokens, live disable and re-enable without replay; successful/failed writes, overshoot, target changes, rapid actions and persistent markers with existing fake repositories.

Update screenshot references only for intentional visual changes. Inspect both themes and large fonts. Screenshots alone cannot prove motion: use the actual app/emulator or a recording when available. If the user requests a fresh emulator, clear it only with authorization and keep demo data there. Prepare it hidden before opening if that is their requested handoff.

Use the target app's existing animation/drawing framework. Do not add dependencies merely because they appear in these reference files.
