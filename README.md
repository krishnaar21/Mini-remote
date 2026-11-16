# Mini Remote (Android)

Universal TV remote with a soft-touch charcoal design. Kotlin + Jetpack Compose.

- Control over local Wi-Fi: **Roku** (ECP), **Samsung** Tizen, **LG** webOS. Android / Fire TV pairing is a stub.
- Round clickpad: tap, hold to repeat, drag to scrub (landing zone picks the axis), hold OK for voice.
- Haptics, synthesized click sounds, accent colours, retro fader, keyboard sheet.
- Local-network only: private IPv4 addresses are enforced in code.
- Samsung and LG secure connections are pinned to the TV's certificate on first pairing. If it changes, the app refuses to connect until you remove and re-add the TV.

## Install
Every push to `main` builds an APK and publishes it here:
`https://github.com/krishnaar21/mini-remote/releases/tag/latest-build` (download `MiniRemote.apk`).
