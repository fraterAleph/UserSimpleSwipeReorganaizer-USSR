# USSR — User Simple Swipe Reorganaizer

A photo library cleaner for Android. It looks at what is actually in your gallery, deals the
most obviously disposable things first, and lets you sort them one swipe at a time. Nothing
leaves the device, and nothing is destroyed on a swipe.

Not a fork of anything. The swipe-to-triage idea is old — [PhotoSwooper](https://codeberg.org/Loowiz/PhotoSwooper)
and [CleanSweep](https://github.com/loopotto/CleanSweep) both do it well — but no open-source
cleaner looks at the *content* of a picture, which is the part this one is built around.

## What it does

**Reads the library.** One MediaStore pass for metadata, then one small thumbnail per item.
No full-size bitmap is ever decoded, so the first sweep is seconds, not minutes.

**Finds the cheap wins with plain arithmetic.** Duplicates by perceptual hash (dHash with
banded candidate lookup, so the sweep does not go quadratic), burst runs by timestamp and
shape, out-of-focus frames by the variance of the Laplacian, blank frames by luma, plus the
folder a file came from — screenshots, messengers, social apps, downloads.

**Then reads what is in the picture.** On-device OCR and image labelling through ML Kit tell
a screenshot of a receipt from a screenshot of a meme. This runs in a background worker,
only while charging, and never blocks the swiping — every item it finishes just sharpens a
later deck.

**Deals the cards in an order that survives a tired human.** Cards are not sorted by "most
likely junk". They are sorted by likely junk **and** cheap to get wrong, so the opening run
is duplicates and blank frames, while anything expensive to lose — a document, a one-time
code, a face — sinks to the bottom or never appears.

**Two modes, and they never mix.** *Normal* deals the clutter and will not show a favourite
at any price. *Hardcore* deals exactly the complement — only favourites, album photos and
shots you edited yourself — because that pile grows too and nothing else in the app can
reach it. Hardcore is deliberately slower: no one-tap batch grid, a brake that trips at half
the speed, and a checkpoint every twelve cards instead of every fifty.

**Pushes back when the hand outruns the eye.** Triage always fails the same way: the first
fifty cards really are junk, the gesture becomes a reflex, and card fifty-one goes unseen.
So the combo counter rewards *how much* you sorted, and the single thing that breaks it is
swiping faster than you can look. Every fiftieth card stops the deck to show you the queue.
Reclaimed megabytes survive a broken combo — the work was still done.

**Never deletes.** Swiping left only queues. The review screen shows the whole queue with
the total it frees, and confirming hands it to `MediaStore.createTrashRequest` — the system
trash, which Android keeps for 30 days and you can restore from in your own gallery.

## Layout

    core/   pure Kotlin, no Android dependency: hashing, focus measure, grouping,
            scoring, deck ordering, the pacing state machine. All unit-tested.
    app/    Android: MediaStore, Room cache, ML Kit, WorkManager, Compose UI.

The split is deliberate. Everything worth being wrong about lives in `core`, where it can be
tested on a laptop:

    ./gradlew :core:test

`:app` is only included in the build when an Android SDK is configured, so that command
works on a machine that has none.

## Requirements

Android 11 (API 30) or newer. The 30-day system trash arrived in API 30, and "nothing is
destroyed today" is the whole safety story, so the app does not ship to versions that cannot
honour it.

## Honest limits

- **There is no "have I looked at this photo" signal.** Neither MediaStore nor PhotoKit
  exposes a last-viewed time to a third-party app; the available fields are `DATE_ADDED`,
  `DATE_MODIFIED`, `DATE_TAKEN` and `IS_FAVORITE`. What the app uses instead are proxies:
  edited after import, favourited, or old and never touched.
- **MediaStore gives no album membership** to a third-party app, so that protection leans on
  the favourite flag and the edit timestamp.
- **Face detection is not wired up yet.** The scorer already treats faces as a reason to keep
  and the field is plumbed through; the ML Kit face model is simply not a dependency yet, so
  the count is always zero today.
- **The first content pass is slow** on a large library — thousands of images through OCR is
  minutes of work. That is why it waits for a charger and why the heuristics alone are enough
  to start.
- **The Android module has not been run on a device yet.** `core` is tested; `app` is written
  but unproven.

## Licence

MIT. See [LICENSE](LICENSE).
