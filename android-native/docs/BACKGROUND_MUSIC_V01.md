# Background Music V0.1

- Voice home provides Select Music / Background Play / Pause / Stop controls.
- First selection uses Android Storage Access Framework and persists read permission.
- Playback runs in a mediaPlayback foreground service, so audio can continue when the app is backgrounded or the screen is locked.
- Notification actions provide pause/resume and stop.
- Voice listening and TTS temporarily duck music volume, then restore it.
