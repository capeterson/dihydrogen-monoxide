# Visual language

## Why the approved design worked

The original brief asked for a “studio-ghibli-meets-lofi-girl” feeling across a real app. The useful translation was an original quiet room at dusk: paper, arched wooden window, sky layers, plants, water, small lamp, and a leaf-hatted companion. It did not require copying a recognizable character. The approved HTML and native UI paired expressive poses with a garden, paper-note reminders, reduced motion, and saved Day/Night selection.

Transfer this principle: **a practical action changes something small in an inhabited world**. Preserve the new task's purpose. In a reading app, a page can settle on a desk and a paper companion can perk up. In a focus timer, a lamp can warm when a session ends. A water jar is one example, not a required motif.

## Composition

Use three attention layers: the real task (quantity/time/control/state), one illustrated anchor (room/window/desk), and peripheral life (cloud, plant, cat, light). Secondary details do not compete with the anchor. Keep navigation and practical controls prominent.

Let the scene breathe. Paper surfaces can hold functional lists/settings, but every phrase does not need a rounded card. Neon outlines, glass panels, decorative metric tiles, huge gradients and confetti cannons pull away from this language.

In the original phone UI, a 230 dp window with a roughly 35–45 dp character worked. Check its face at that size; fine detail that reads only in a large editor is wasted. Do not let the scene hide essential controls/navigation.

For a compact web view, check an actual 390 × 844–900 viewport. Stacking the full desktop illustration above the heading, quantity and action often pushes the primary button mostly off screen. Put the task controls before the full illustration, or cap the small-screen scene height until the primary action is comfortably reachable. Preserve the intended scroll behavior of an existing product rather than forcing every screen into one viewport.

## Palette roles

| Role | Day | Night |
| --- | --- | --- |
| Paper | `#ECEEE3` | `#161A2C` |
| Inner surface | `#F8F7EF` | `#1F243B` |
| Ink | `#22302B` | `#EDE6D3` |
| Secondary ink | `#56625B` | `#A8A9C2` |
| Water/action | `#2D6A76` | `#82C6CC` |
| Plant/growth | `#3F6834` | `#9FCB8C` |
| Fine separator | `#C3C9B6` | `#3B4163` |
| Sky top | `#9DBFD3` | `#2B2F5A` |
| Sky horizon | `#F6D6A8` | `#C77F6A` |
| Wood | `#B49A74` | `#6B5646` |
| Lamp | `#E8A54B` | `#F2B661` |

Use these as a coordinated starting palette, not values that override an existing brand. Reserve gradients mostly for sky, water and emitted light. Check secondary text on the actual background rather than assuming muted green is readable.

## Drawing and material

- Use gently imperfect clean contours. Vary shapes rather than applying animated noisy wobble everywhere.
- Paint a few cel-like light/shade regions, a narrow rim highlight, and a soft ground shadow. Avoid a shiny 3D gummy mascot.
- Separate body, face, leaf/ear/tail, shadow and particles into local coordinate systems. The flexible part can lag the body while feet stay planted during compression.
- Use a static low-opacity grain overlay, clipped appropriately. Never regenerate expensive noise each frame or obscure text.
- Repeat paper edges, ink weight, wood and lamplight across screens without duplicating the entire hero.

## Type and language

Fraunces gives headings a soft expressive voice, Nunito Sans keeps controls approachable, and DM Mono makes quantities feel like notebook annotations. Bundled fonts include licenses. Keep the product's fonts when changing branding would be out of scope. Practical text remains readable UI (roughly 14–16 px/sp or larger), with font scaling supported.

Use brief kind copy tied to an action: “A little pause. A little water.” or “A week of showing up.” Avoid guilt, broken-streak threats, praise for exceeding health targets, or characters that become ill or sad when the user is away. An empty day is soil, not punishment. Actual errors/permissions stay plain and useful; cute copy must not hide a failed save.

## Real day/night

Switch the illustration and surrounding surfaces together. Night keeps warm text, cool water, indigo depth, stars and amber lamps. Crossfade a stable full-scene palette instead of changing a sky stop while leaving the room in daylight. Preserve text contrast, save the choice, update the accessible control label, and retain Follow system if already present.
