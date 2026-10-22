# Controlled dispatch candidate — P1_PREPARATORY_ONLY

This module is not imported by the live scene. It does not enable microphone,
storage, network access, 3D interaction, or any release gate.

`InteractionDispatcher` binds a trusted `ActionRegistry`, one
`TargetInteractionSession`, and a copied explicit local-action allowlist. Pass the
actual opaque context returned by `resolveVoiceResult`; copied objects fail.
Use one dispatcher instance per session. Each context is consumed once before
the first await, including disabled, mismatched, cancelled, and failed attempts.
This prevents replay of possibly partial effects; a fresh interaction is needed.

The registry's optional fourth `invoke` argument is a trusted synchronous
checkpoint. It can only deny and runs after the existing async action guard,
with no await before handler entry. Dispatcher checkpoints revalidate target
identity, session age and cancellation before every handler, not just on match.
The existing platform, scope, input, output and reentry checks still apply.

Handlers are trusted application code, not card-provided JS. They must use the
bound context target, not an arbitrary payload target; asynchronous handlers
must recheck context themselves immediately before later mutations. This
checkpoint cannot undo effects already started or protect arbitrary code after
its own await. A handler that invalidates the grab stops the remaining sequence;
the receipt reports partial failure and never invents rollback. Actual release-
grab / return-to-shelf lifecycle integration is deliberately deferred to the
P0-gated scene adapter; these synthetic effects are not a playable return spell.

Preserved: live HTML, public catalog, 3D/book/avatar/joystick/camera/card behavior,
GBA/GEN2, permissions and player saves are unchanged. Added: unloaded adapter,
deny-only registry checkpoint and synthetic Node/DOM tests. Removed: nothing.

Verification: Node suite, existing schema cases and browser CI fixture.
No staging URL or Android device evidence is supplied by these tests.
Rollback: parent candidate `64f40d335928906e08dc4b5fb406077b9473bfc3`.
