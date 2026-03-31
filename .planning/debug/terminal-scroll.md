---
status: awaiting_human_verify
trigger: "terminal scroll doesn't work in the Android app's terminal emulator view"
created: 2026-03-31T00:00:00Z
updated: 2026-03-31T00:00:00Z
---

## Current Focus

hypothesis: The `clickable` modifier on the parent Box in TerminalScreen.kt consumes all touch DOWN events before they reach the Canvas's `pointerInput`, preventing scroll gestures from ever being detected
test: Inspect the compose touch propagation chain - clickable uses `detectTapGestures` internally which calls `awaitFirstDown(requireUnconsumed = true)` and consumes the down event; TerminalRenderer's `awaitFirstDown(requireUnconsumed = false)` does NOT consume but also can't scroll because the clickable takes exclusive ownership of gesture recognition via the gesture filter pipeline
next_action: Confirm root cause and apply fix - remove clickable from the Box parent, use pointerInput on the Box directly for focus/keyboard show on tap, while letting TerminalRenderer's own gesture handling take over all pointer events

## Symptoms

expected: Dragging finger up/down on the terminal view should scroll through terminal output history (scrollback buffer)
actual: Scroll gesture does nothing. Only pinch-to-zoom works.
errors: No crash or error messages
reproduction: Open app -> tap a tmux session -> terminal view opens -> try to drag up/down -> nothing happens. Pinch zoom works fine.
started: Never worked. Multiple implementations attempted (detectVerticalDragGestures, pointerInteropFilter, awaitEachGesture) all failed.

## Eliminated

- hypothesis: The scrollback buffer is empty so there's nothing to scroll to
  evidence: TerminalEmulator.scrollbackSize returns scrollback.size, which is populated by scrollUp() on every linefeed past the bottom. Sessions with output will have a populated buffer. Pinch-to-zoom also works meaning the gesture system reaches the Canvas, so it is not a buffer emptiness problem.
  timestamp: 2026-03-31T00:00:00Z

- hypothesis: scrollOffset logic in TerminalRenderer is inverted or clamped wrong
  evidence: Scroll logic at line 75: `scrollOffset = (scrollOffset + lines).coerceIn(0, maxScroll)`. A drag upward produces negative dy, so lines is negative, which would DECREASE scrollOffset toward 0. This is actually inverted - dragging UP should increase scrollOffset (show older lines). However this cannot be the primary bug because scrollOffset never changes at all (gesture is consumed first).
  timestamp: 2026-03-31T00:00:00Z

## Evidence

- timestamp: 2026-03-31T00:00:00Z
  checked: TerminalScreen.kt Box modifier chain (lines 62-68)
  found: The Box wrapping TerminalRenderer has `.clickable(interactionSource = ..., indication = null) { focusRequester.requestFocus(); keyboard?.show() }`
  implication: Compose's `clickable` modifier internally installs a `detectTapGestures` coroutine in the pointer input pipeline. It calls `awaitFirstDown(requireUnconsumed = true)` - when a finger goes DOWN, it claims the gesture. All child pointerInput handlers that attempt their own gesture recognition lose the race for pointer event ownership.

- timestamp: 2026-03-31T00:00:00Z
  checked: TerminalRenderer.kt gestureModifier (lines 40-82)
  found: Uses `awaitFirstDown(requireUnconsumed = false)` which means it DOES receive the down event even if already consumed. However, the pointer event stream itself - subsequent MOVE events - gets claimed/consumed by the parent clickable's internal gesture detector, which is looking for a tap (press then release without moving). The `positionChanged()` check at line 68 will still fire, BUT the critical issue is that `clickable` on the PARENT Box runs AFTER (outer) the Canvas's pointerInput (inner) in the event dispatch chain.
  implication: In Compose, modifiers on a parent are OUTER in the gesture arena. The `clickable` on the Box is OUTSIDE the TerminalRenderer's pointerInput. Compose dispatches pointer events from leaf to root - inner composables get events first. So TerminalRenderer should actually get events first, NOT the Box clickable. This flips the hypothesis.

- timestamp: 2026-03-31T00:00:00Z
  checked: Compose pointer event propagation order - inner vs outer
  found: In Compose, pointer events travel from OUTERMOST modifier to INNERMOST in the initial pass (hit testing going down), then back out. The `pointerInput` of the CHILD (TerminalRenderer's Canvas) is actually inner - it gets the event BEFORE the parent Box's clickable. The TerminalRenderer's `awaitFirstDown(requireUnconsumed = false)` will fire, then calls `down.consume()` at line 43, which marks it consumed. The parent `clickable` uses `requireUnconsumed = true` by default in detectTapGestures, so it should NOT receive the event. But this would break the keyboard-show tap too.
  implication: The current code should theoretically work for gestures, but it doesn't. Something else is wrong.

- timestamp: 2026-03-31T00:00:00Z
  checked: The scroll direction math in TerminalRenderer lines 69-76
  found: `val dy = pointers[0].position.y - pointers[0].previousPosition.y`. When user drags FINGER UP (to scroll content up = see older history), position.y DECREASES, so dy is NEGATIVE. `scrollAcc += dy` becomes more negative. `val lines = (scrollAcc / charHeightPx).toInt()` - toInt() truncates toward zero for negative values (-0.8 -> 0, -1.3 -> -1). When lines becomes -1, `scrollOffset = (scrollOffset + (-1)).coerceIn(0, maxScroll)` - this DECREASES scrollOffset. But scrollOffset=0 means "show current screen", and higher scrollOffset means "show older history". So dragging UP should INCREASE scrollOffset. The sign is BACKWARDS.
  implication: Even if gestures reach the handler, scroll would go the wrong direction AND would immediately clamp to 0, making it appear as if nothing happens. A user dragging down (lines positive) would increase scrollOffset... but dragging down means "scroll content down" = show NEWER content = DECREASE scrollOffset. Both directions are inverted.

- timestamp: 2026-03-31T00:00:00Z
  checked: The `awaitEachGesture` + `awaitFirstDown(requireUnconsumed = false)` combined with `down.consume()` pattern
  found: After `awaitFirstDown` returns, the code immediately calls `down.consume()`. This DOES mark the pointer as consumed going up the parent chain. But `awaitEachGesture` wraps the entire block - it waits for ALL pointers to be up before starting again. The gesture loop at lines 47-80 uses `awaitPointerEvent()` and manually checks `positionChanged()`. The `pointerInput` key is `Unit` (line 40) which means it never restarts. This is fine. But the `changedToUp()` check breaks out of the while loop. This all looks mechanically correct for capturing gestures.
  implication: The gesture capture logic in TerminalRenderer is structurally sound. The DOWN event IS consumed preventing parent from stealing it. The real bug is the INVERTED SCROLL DIRECTION causing apparent no-op: dragging up produces negative lines -> scrollOffset decreases but is already clamped at 0. User cannot observe any change.

## Resolution

root_cause: Two bugs compound to make scroll appear broken:
  1. PRIMARY: Scroll direction is inverted. In TerminalRenderer.kt lines 69-76, dragging finger UP produces negative dy, leading to negative `lines`, which DECREASES scrollOffset. But scrollOffset=0 is the bottom (current view) and increasing it shows older history. So dragging UP (user intent: see older content) DECREASES the offset and hits the 0 clamp immediately - nothing visible happens. Dragging DOWN would increase the offset (show older content, wrong direction).
  2. The scrollOffset inversion means every scroll attempt clamps to 0 immediately, giving a complete no-op effect.

fix: Negate the `lines` delta when updating `scrollOffset` in TerminalRenderer.kt. Change `scrollOffset = (scrollOffset + lines).coerceIn(0, maxScroll)` to `scrollOffset = (scrollOffset - lines).coerceIn(0, maxScroll)`. Dragging up (negative dy, negative lines) minus negative = add positive = increase scrollOffset = show older lines. Dragging down (positive dy, positive lines) minus positive = decrease scrollOffset = show newer lines.

verification: Fix applied. Changed `scrollOffset + lines` to `scrollOffset - lines` in TerminalRenderer.kt line 75. Awaiting human confirmation in device.
files_changed:
  - android/app/src/main/kotlin/com/remotecontrol/terminal/TerminalRenderer.kt
