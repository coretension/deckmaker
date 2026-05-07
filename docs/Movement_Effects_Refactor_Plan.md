# Movement Restrictions & Effects Refactor Plan

## Goal
Create a movement pipeline that evaluates restrictions first and only executes movement effects when movement is actually applied.

## Core Design
1. **Move intent**
   - Capture requested position (`x`, `y`) and drag source.
2. **Policy evaluation**
   - Apply movement restrictions (bounds, overflow rules, container rules).
3. **Decision output**
   - Produce a structured result with final position and outcome (`applied` vs `no-op/rejected`).
4. **Effect dispatch**
   - Run movement effects only when the decision reports applied movement.

## Responsibilities
- **MovementPolicyEngine**
  - Pure movement logic: restrictions + snapping inputs.
- **MovementExecutor**
  - Applies position changes only when they differ from current values.
- **MovementEffectsCoordinator**
  - Handles visual and UI effects (snap guides, coordinate refresh, render triggers) from movement outcome.

## Event Model
- `MoveApplied(oldPos, newPos, delta, cause)`
- `MoveRejected(requestedPos, reason)`
- `MoveCompleted` (for drag-end batching/history)

Effects subscribe only to relevant outcomes:
- Snap guides and drag visuals: `MoveApplied`
- User feedback/status: `MoveRejected`
- History finalize: `MoveCompleted`

## Key Rule
If restrictions clamp movement so final position does not change, treat it as no movement and do not update snap guides.

## Incremental Implementation
1. Extract movement constraint logic from drag handler into decision methods.
2. Return a structured movement decision instead of mutating inline.
3. Gate effects behind `decision.applied`.
4. Keep existing selection, overflow exceptions, and history batching behavior.

## Validation
- Restricted/no-op drag must not move snap lines.
- Accepted drag must move element and update snap lines.
- Overflow-allowed images still bypass bounds constraints.