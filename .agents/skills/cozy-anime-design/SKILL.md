---
name: cozy-anime-design
description: Design and implement warm, anime-inspired lo-fi interfaces and expressive character animation for web previews or native apps. Use for cozy room, paper-and-moss, gentle companion, day/night, or comforting progress interactions. Covers visual composition and working motion behavior; not a general anime illustration or unrelated UI redesign skill.
---

# Cozy Anime Design

Make useful software feel like a small, inhabited place. The approved direction combines warm paper, moss and water colors, a drawn environment, and a companion with deliberate animation poses. The character notices an action; the interface still does its job immediately.

## Start with the right reference

1. Inspect the existing product and keep its identity, navigation, data model, and working interactions. Translate the style to the requested domain; a reading tracker does not need hydration copy or a water jar.
2. Read [visual-language.md](references/visual-language.md). Inspect `assets/day-reference.png` and `assets/night-reference.png` with an image viewer when available. The runnable `assets/web-reference/index.html` shows the approved interaction ideas; its text and quantities are sample content.
3. For animated work, read [motion-contract.md](references/motion-contract.md) before implementing. For Android/Compose, also read [native-integration.md](references/native-integration.md).

Do not load every source asset as instructions. Reference code is a study of poses and layering, not a mandate to import its dependencies or reproduce its screens.

## Choose five things, then build

Make a small implementation brief using these choices. Infer ordinary choices from the product instead of stopping to ask.

| Choice | Useful default | Purpose |
| --- | --- | --- |
| Focal place | Arched window, desk, greenhouse, reading nook | One coherent world |
| Primary ritual | The real action the user repeats | A meaningful cause for delight |
| Small response | Grounded companion hop plus local ripple/leaf/page movement | Feedback for ordinary success |
| Milestone | Warm lamplight and a few drifting fireflies | A finite reward for a real crossing |
| Still state | Correct quantity, clear confirmation, resting character | Complete functionality without motion |

For a new UI, one illustrated focal area plus quiet practical surfaces is a strong starting point. For existing UI, add that area only if the task calls for it. A requested microinteraction is not authorization for a redesign.

## Concrete visual recipe

- Begin with sage paper `#ECEEE3`, warm inner paper `#F8F7EF`, dark green ink `#22302B`, river teal `#2D6A76`, wood `#B49A74`, and restrained lamp amber `#E8A54B`. Night uses indigo `#161A2C`, paper ink `#EDE6D3`, water `#82C6CC`, and amber `#F2B661`. The visual reference maps these to roles.
- Combine a soft expressive serif for headings, approachable sans for controls, and restrained monospaced quantities. The approved examples use Fraunces, Nunito Sans, and DM Mono. Fonts and licenses are bundled with the web reference; keep existing branding when replacing it is outside scope.
- Draw layered shapes, soft shadow, and a small amount of static grain. Keep text/data crisp. Native vector/Canvas/SVG drawing suits articulated characters; generate bitmap art only when it contributes to the task.
- Give an original companion a simple silhouette, readable face, cel-like light/shadow, grounded base, and one flexible part (leaf, ear, tail). Check it at actual display size. A familiar copyrighted character or emoji mascot is not the design system.
- Use amber as emitted light, teal as water/action, and moss as growth. Keep large areas calm. Night is an indigo room, not a black inversion. Check text contrast against the actual surfaces in both modes.

## Concrete animation recipe

Use **anticipation → launch → hang → landing → small rebound → settle**. Shape each phase; scaling the whole card in a loop is not expressive character animation.

| Event | Starting timing | Scope |
| --- | --- | --- |
| Press/release | 90 ms press; 180–280 ms release | 2–5% compression of touched object |
| Confirmed ritual | About 1,160 ms | Drop/page/leaf impact near 32%; companion follows impact |
| Goal reached | About 3,240 ms | Local glow and bounded drifting particles, no modal interruption |
| Day/night choice | About 520 ms | Scene palette crossfade, saved choice, updated accessible label |
| New growth | About 820 ms | Ease from previous actual value, not zero on every render |
| Paper note | About 660 ms | Roughly 9 dp travel, under 2° initial tilt, gentle settle |

These are starting points, not a requirement to use every effect. Keep useful controls responsive. Repeat actions replace or blend the current small response rather than creating an unbounded queue.

Keep ordinary-action and milestone animation lifetimes separate. A later sip/page log may restart the small hop, but must not clear the fireflies or cut a goal glow short. Reserve cancelling both tracks for reduced motion, leaving the active screen, or reconciling state that invalidates the scene.

## Working behavior is part of the style

- Separate restored/corrected state from new successful actions. Progress changing on load, goal edit, undo, sync, or navigation is not a success event.
- Commit first. Publish the new quantity and an explicit monotonic action cue together after success. Failed writes show an error and emit no success cue. Handle rapid asynchronous actions without losing updates.
- For once-per-day goals, compare old/new amount against the same target and persist the last celebrated domain day. Deleting then logging again must not create another milestone. Use the product's actual day boundary.
- Day/night actually switches scene and interface, stays readable, and persists where the deliverable supports persistence. Preserve an existing Follow system choice. A decorative sun icon without behavior is incomplete.
- Honor OS reduced motion and an app preference when present. Stop frame clocks/particles in the background. Re-enabling motion establishes a baseline and never plays accumulated events. Static UI still updates and confirms the action.

## Validate the actual result

For prototypes, deliver an interactive artifact rather than only a mood board. For native work, integrate real actions/settings rather than a demo counter. Follow the requested branch/preview workflow. Do not open an emulator early if the user wants it only when ready; do not wipe state unless authorized.

Check as relevant:

1. Character/expression reads at small size; controls, values and both themes remain legible.
2. A successful ordinary action has distinct poses and a settled ending.
3. First true crossing celebrates once, including overshoot. Restore, repeated renders, target edits, undo and failures stay quiet.
4. Rapid actions preserve committed data. Timers/particles are bounded and cancelled/rebased appropriately.
5. Reduced motion works when changed live. Background/resume does not queue rewards.
6. Primary actions support keyboard/screen reader use; progress has text or semantics.

Run the project's appropriate checks and inspect the rendered UI when a renderer is available. Report unavailable visual verification candidly. Source inspection or syntax checks alone cannot establish attractive, smooth movement.

## Bundled material

- [visual-language.md](references/visual-language.md): palette, composition, drawing, typography and supportive copy.
- [motion-contract.md](references/motion-contract.md): poses, successful-event sequencing, lifecycle and failure scenarios.
- [native-integration.md](references/native-integration.md): Compose ownership, state, performance and screenshots.
- `assets/web-reference/`: approved playable HTML study, local fonts and licenses. Adapt its drawing, not its demo state logic, for production.
- `assets/compose-reference/`: original native drawing/timing source; read only what the task needs.
- `assets/reading-example.html`: a tested synthetic example of transferring the style to another domain.
- `scripts/check-motion-curves.mjs`: dependency-free checks of the bundled pose recipe; run with Node when adapting it.
- [evaluation.md](references/evaluation.md): Terra forward-test findings, corrections and verification limits; consult when maintaining this skill.
