# Production roadmap for Subnet

The current ZIP is a buildable foundation, not a finished Google Assistant replacement.

## Phase 1 – Stabilize command understanding
- Add a formal intent/slot model and Marathi-English test corpus.
- Add phonetic/fuzzy matching for contact aliases.
- Keep pending dialog state across speech turns and app recreation.
- Add automated tests for Marathi number words, contact names and command variants.

## Phase 2 – Wake word
- Select and license an on-device keyword spotter (evaluate Picovoice Porcupine or an open-source alternative).
- Add a foreground microphone service with visible notification and explicit user start/stop controls.
- Test screen-off behaviour, battery consumption, Android 13–15 restrictions and OEM battery managers.
- Never record or upload ambient audio; process wake-word audio locally.

## Phase 3 – Owner authentication
- Enroll multiple owner voice samples in a speaker-verification model.
- Store encrypted speaker embeddings locally and synchronize only with end-to-end encryption.
- Add anti-replay/liveness checks and fallback device biometric/PIN.
- Require step-up authentication for calls to premium numbers, messages, payments and security changes.

## Phase 4 – Central knowledge sync
- Create Firebase or Supabase project.
- Authenticate each device to the same owner account.
- Store only user-approved command aliases and preferences, not raw audio.
- Add versioning, conflict resolution, encryption, device revocation and backup/restore.
- Add offline queue and sync status.

## Phase 5 – Self-improvement with approval
- Record failed utterance text only after user consent.
- Ask the owner to confirm a proposed phrase-to-intent mapping.
- Keep a versioned knowledge base with undo/delete.
- Never let the model invent or execute a new system action without explicit confirmation.
