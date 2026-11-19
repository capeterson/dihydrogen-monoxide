# Motion contract

## Choose the cause

| Change | New success cue? | Result |
| --- | --- | --- |
| Initial load, back navigation, process restore | No | Correct resting progress/scene |
| Confirmed new visible-day action | Yes | Small response even after progress caps |
| First crossing of unchanged same-day target | Yes, plus milestone | Small response and finite goal glow/fireflies |
| Target edit, correction, import, sync, undo/delete | No | Reconcile the value without a reward |
| Failed write | No | Keep confirmed data and show useful error |
| Motion off or screen inactive | No visible animation | Update state without queueing a later performance |

A render is not an event. `progress > oldProgress` alone celebrates restored data/goal edits, misses new actions after 100%, and can replay after navigation.

## Commit and publish together

Separate three kinds of state:

```text
confirmed data: domainDay, amount, target, entries, lastCelebratedDay
render cues: actionSequence, goalSequence
local animation: bounded phase 0..1 for the current event
```

1. Serialize overlapping writes or reconcile responses with the authoritative store. Two asynchronous updates must not use the same old amount.
2. Await the actual write and a consistent committed snapshot. A pending operation can show pending feedback but not success motion.
3. Crossing means `oldAmount < target && newAmount >= target`, the same domain day and unchanged target. A target edit is not a goal event.
4. Persist the day's milestone marker with storage-appropriate atomicity. Deletion/undo does not erase it.
5. Publish the confirmed quantity and new cue together; otherwise the fill snaps before the drop arrives.
6. A renderer's first observed cue, including a nonzero restored token, is its baseline. Only a later changed cue plays.

Use the product's existing transactions, IDs and conflict rules for multiple writers/devices. A local animation token is not a replacement for domain events. Do not migrate unrelated production data to match this example.

## Approved pose recipe

`assets/compose-reference/CozyMotion.kt` and `assets/motion-curves.mjs` contain matching math. Normalize time to 0..1 over 1,160 ms. Transform around planted feet/base.

| Phase | Body | Follow-through |
| --- | --- | --- |
| 0–16% | Rest, then notice | Eyes track event |
| 16–35% | Downward anticipation, slight vertical compression | Leaf tips forward |
| 35–65% | Smooth parabolic lift, light takeoff stretch | Leaf trails/catches up, face brightens |
| 65–76% | Soft landing, slightly wider body | Ground shadow returns to resting width |
| 73–94% | Much smaller rebound and damped correction | Leaf overshoots neutral and loses energy |
| 94–100% | Exact resting pose | No residual drift |

Reference hop height is 16 drawing units (normal) or 24 (goal); stretch/compression around 14–20%, body lean small. Scale with the character, not the screen. Keep the shadow grounded. Facial change and a trailing flexible part do more than moving the whole mascot farther.

Do not substitute a generic CSS bounce, a never-settling spring, or a single sine loop for these phases. Idle breathing should be almost invisible compared with deliberate action.

## Scene response

Drop/page/petal impact occurs around 32%, followed by local ripples/follow-through. Ease fill from the previous confirmed level. Initial load/correction should not rise from zero. Text reports actual quantity even if the illustration caps at 100%.

Rapid actions replace/blend the small response. Bound timers/particles and remove completed particles. Decorative movement does not require full-screen overlays or artificial input locks.

Use separate tracks for ordinary actions and milestones. A single `cancelMotion()` call before every new log is a subtle failure: the next ordinary log erases the longer goal glow and particles. A safe ownership pattern is:

```text
on confirmed action:
    replace only ordinary-action phase/timer
    if this is the first true crossing: start milestone phase/timer

ordinary-action completion: clean up only the hop/drop/ripple
milestone completion: clean up only the goal glow/particles
motion disabled or screen inactive: cancel both tracks and reset their baselines
```

Give particles/timers a track owner so one cleanup cannot accidentally remove the other's work. Add a scenario where another normal action arrives halfway through the milestone: the data increments, the small response can restart, and the original milestone still finishes on its original bounded schedule.

## Goal response

Use an approximately 3,240 ms envelope: local lamplight bloom, 12–20 small drifting particles on curved staggered paths, then normal ambience. Pair warm halos and tiny bright cores so particles read as fireflies, not confetti. Use stable seeds when deterministic screenshots matter. Keep the correct resting lit lantern/progress after temporary motion ends.

Animate particles individually rather than translating one rigid group of dots. Vary start time, horizontal bend and rise distance; a midpoint keyframe with a different horizontal offset gives even a simple CSS path a gentle bend.

Celebrate reaching a goal rather than ever-increasing health quantities. Later actions receive the small response without repeating the same domain day's milestone.

## Reduced motion and lifecycle

Observe OS preference changes live; a startup-only read is insufficient. An app switch may reduce motion further but never override OS reduced motion. Snap to correct final state, retain clear text/semantic feedback, and stop ambient clocks. Disabling mid-flight cancels it. Re-enabling/resuming establishes a baseline rather than replaying old tokens.

HTML: observe `matchMedia('(prefers-reduced-motion: reduce)')` changes and `visibilitychange`. Cancel JS/WAAPI work as well as CSS; CSS alone does not stop timers or particle allocation. Reset the previous timestamp on rAF resume so a long background gap does not become a giant frame.

Compose: gate clocks on resumed lifecycle, animator scale and the app preference. Read state in drawing/layer lambdas where practical; do not recompose the whole navigation screen per frame. Drawing does not write storage or start network work.

## Scenario checks

Use the project's existing tools. Useful tests: restore at 75%; restore already at goal; ordinary success; overshoot from below; failed commit; rapid commits; lowering target; deletion/undo then crossing again; back navigation; next domain day; disabling motion during flight; background/resume. Assert data/cue behavior rather than headings, copy, or an exact composition.
