# Flower spirit launcher icon

`flower-spirit.png` is the approved, unmodified ImageGen master: a water spirit in
a glass with a pink flower. The flower variant was approved on 2026-11-19.

Run `python3 artwork/launcher/generate.py` from any directory (Python 3 + Pillow)
to regenerate the phone/watch lossless WebP layers and `mask-preview.png`.
The artwork occupies the central 72 dp of Android's 108 dp layer. Edge pixels
extend into the outer parallax margin; the flower, face and glass remain visible
under circular and rounded masks. Resources are 432px (xxxhdpi equivalent), stored
in `drawable-nodpi` so Android scales the complete layer to the drawable bounds.

The separate `ic_launcher_monochrome.xml` vector is a simplified glass, spirit
and flower for Android's user-tinted themed icons. Notification, tile and
complication glyphs retain their existing water-drop symbol.

Geometry reference: [Android adaptive icons](https://developer.android.com/develop/ui/compose/system/icon_design_adaptive).
The master is intentionally retained so future exports do not require generation
or depend on a personal Codex cache.
