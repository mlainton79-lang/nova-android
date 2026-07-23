# codex review — bb0aec7

**Commit:** `bb0aec7 revert(ui): restore Auto and Local Tony to brain picker (codex P2 on ed78861)`
**Base:** `HEAD~1` (`46e4c9b`)
**Date:** 2026-07-23

## Verdict

The revert itself is a one-line restoration of prior behaviour. Codex passed the picker change but flagged a **pre-existing latent bug** that this revert re-exposes to new users.

## Comments

### P2 — Avoid exposing Local Tony before fixing duplicates
**File:** `app/src/main/kotlin/com/mlainton/nova/MainActivity.kt:533`

> When users select `Local Tony` from this expanded picker and the on-device model is ready, `sendCurrentMessage()` has already appended the user's message, and `processOnDevice()` appends the same user message again before generating a reply. This makes every Local Tony exchange show duplicate user bubbles; either keep `LOCAL_TONY` out of this picker or remove the extra append in the on-device path.

**Assessment: valid, and it's not caused by this revert.** The bug exists at `HEAD^` and every commit before — the revert just re-exposes the path.

Trace:

1. `sendCurrentMessage()` at `MainActivity.kt:736` — `ChatHistoryStore.appendMessage(this, "user", message)`.
2. Same function at `:741` — `processThroughBroker(message)`.
3. `processThroughBroker()` at `:932-935` — when `currentBrainMode == BrainMode.LOCAL_TONY` and `OnDeviceModel.isReady()`, delegates to `processOnDevice(message)`.
4. `processOnDevice()` at `:2388` — `ChatHistoryStore.appendMessage(this, "user", message)` **again**.

`processOnDevice` has exactly one caller (`processThroughBroker` at `:933`), so the duplicate-append is unconditional whenever the user is in `LOCAL_TONY` and the model has finished initialising. Same message written twice → renders as two identical user bubbles.

The fix is one line — delete `MainActivity.kt:2388`. (The `renderChatHistory()` at `:2389` is redundant with `sendCurrentMessage`'s render at `:739` but harmless; can stay.)

**Forks:**
- **A. Fix the duplicate append in a small follow-up.** One line deleted, safe, matches codex's suggestion. Puts the picker fully back to a clean state.
- **B. Leave the pre-existing bug for a later cycle.** The revert already ships value (three good TARGETs + working AUTO/LOCAL_TONY picker entries). Local Tony users see duplicate bubbles today just as they did before the daily-loop branch — no *new* regression.

Stopping for direction — this bug pre-dates the current session, and the natural next step is deciding whether to burn a commit on it now or park it.

## What passed clean

The revert diff itself. Codex ran the whole `showBrainPicker` path back through the callers and only flagged the on-device duplicate — no comment on the revert mechanics or on the three good TARGETs still in place from `ed78861`.
