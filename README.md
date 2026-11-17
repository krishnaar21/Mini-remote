# Mini Remote (Android)

Universal TV remote with a soft-touch charcoal design. Kotlin + Jetpack Compose.

- Control over local Wi-Fi: **Roku** (ECP), **Samsung** Tizen, **LG** webOS, **Android TV / Google TV** (remote protocol v2, pair with the code on screen). Fire TV runs Fire OS and doesn't support this protocol.
- **Home:** greeting with your TV, search, Quick Controls (TV Guide, channel list, info, input, display, audio), VOL and CH rockers around a big Home key, and a control deck below.
- **Dial, trackpad or number pad:** swipe beside the control (or on its switcher) to change it. Settings › Remote picks the one Home always opens with.
- **Channels tab:** a quick access row you pin to, apps the TV reports (Roku, Samsung, LG), popular apps (Netflix, YouTube, Prime Video, JioHotstar…), and your own TV channels by number.
- **Settings and Profile** pages, all under one floating round tab pill.
- Round clickpad: tap, hold to repeat, drag to scrub (landing zone picks the axis), hold OK for voice.
- Haptics, synthesized click sounds, accent colours, retro fader, keyboard sheet.
- Local-network only: private IPv4 addresses are enforced in code.
- Samsung and LG secure connections are pinned to the TV's certificate on first pairing. If it changes, the app refuses to connect until you remove and re-add the TV.

## Install
Every push to `main` builds an APK and publishes it here:
`https://github.com/krishnaar21/mini-remote/releases/tag/latest-build` (download `MiniRemote.apk`).
