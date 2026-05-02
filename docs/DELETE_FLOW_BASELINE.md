# Delete Flow Baseline

This document records the current stable baseline for the photo delete flow.

## Current status

The app is back to a working baseline where swipe-up can queue photos for deletion on a real device.

## Non-negotiable contract

Swipe-up delete is a business-state transition, not an animation result.

When the UI accepts a swipe-up gesture, it must call `PhotoViewModel.swipeUp()` immediately. The queued photo must be added to `deleteQueue`, removed from `visiblePhotos`, and reflected in the trash badge without waiting for any animation to finish.

Animations may be added only as visual feedback around this state transition. They must not decide whether a photo enters the delete queue.

## Minimal manual regression

Before changing gesture, permission, or delete code, verify this flow on a real device:

1. Fresh install.
2. Grant photo permission.
3. Confirm photos load.
4. Swipe up one photo.
5. Confirm the trash badge increments immediately.
6. Open the trash page.
7. Confirm the queued photo is listed.
8. Start deletion.
9. Confirm the system delete dialog appears.
10. Confirm deletion and verify the photo disappears after refresh.

## Frozen areas

Do not reintroduce complex coordinated swipe animations until this baseline is protected by UI/instrumentation tests. The previous regression happened because the business call was executed after animation completion.

## Deferred work

- Rebuild swipe animations on top of immediate queueing.
- Validate MediaStore multi-volume support on real devices before changing URI construction again.
- Add UI/instrumentation tests for swipe gesture acceptance and trash badge updates.
