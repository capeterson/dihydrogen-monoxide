# Forward-test evidence

Tested on 2026-11-19 with two independent `gpt-6.1-terra` agents at medium reasoning effort. They received the skill path, a realistic user request, and only the raw input needed for that task. They were not given the expected implementation. Luna was not an available model in this session.

## Trials

1. **New-domain design:** build an offline desktop/mobile reading-room tracker starting at 20/40 pages, logging 10 pages, with an original companion, success motion, Day/Night, and reduced motion. The output adapted the material/scene vocabulary to books and a paper companion rather than copying hydration UI. A version is retained as `assets/reading-example.html`.
2. **Behavior repair:** improve a supplied hydration prototype while preserving its DOM IDs and asynchronous data adapter. The input incorrectly animated on initial render, updated optimistically before a failed save, and lacked working theme/motion controls. Terra delivered a functioning repair with serialized commits, quiet failures, persistent milestone state, and live motion cancellation.

The evaluator separately exercised the actual generated HTML in jsdom; it did not just accept the agents' own scenario reports. Headless Firefox rendered the desktop outputs and the reading prototype at 390 × 900. The evaluator visually inspected those images.

## Findings and revisions

The first independent run passed **14/16 behavior checks**. Both agents coupled the ordinary-action cleanup to the milestone lifetime, so logging again immediately erased an active goal glow/fireflies. The skill now explicitly defines separate ordinary and milestone tracks and their cleanup ownership. After re-reading the revised skill, both agents corrected their artifacts. The final run passed **16/16**.

Mobile visual review also found the reading scene stacked above all task controls, pushing the main button mostly below the initial viewport. The visual guide gained a compact-screen composition note. Terra revised the layout; the evaluator rendered it again and confirmed that quantity, status and the full primary action appear before the illustration.

## Final behavior checks

- Reading: correct initial data without a reward; ordinary logs and one goal; subsequent log preserves the milestone; saved/applied night mode; live OS reduced motion still records data; re-enable does not replay; restored completed goal stays quiet and preserves theme.
- Repair: quiet initial state; failed commit keeps confirmed quantity and shows error; rapid asynchronous actions keep every write and one daily milestone; later success preserves that milestone; undo/re-crossing does not celebrate twice; saved/applied theme; background commit without replay; live OS reduced motion with correct data; target lowering without a reward.

The bundled curve check also passed endpoints, anticipation, takeoff, landing, rebound, goal-vs-normal height, and 1,001 intermediate frames. Skill metadata and local reference paths were validated.

## Limits

These are two synthetic web trials, not proof of all future outcomes. jsdom checks state and DOM behavior, not frame pacing. Static browser renders establish layout/readability, not real-time smoothness. The native reference came from the separately built and emulator-checked original implementation; this skill trial did not ask Terra to rebuild an Android app. Native changes still need the target project's compile/tests and actual motion review.

The repair fixture's adapter exposes separate quantity and marker saves, so the generated repair cannot make them a single atomic transaction. The skill requires storage-appropriate atomicity and does not claim an adapter can provide guarantees its interface lacks.
