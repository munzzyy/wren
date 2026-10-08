# Wren

[![Test](https://github.com/munzzyy/wren/actions/workflows/test.yml/badge.svg)](https://github.com/munzzyy/wren/actions/workflows/test.yml)
[![License: AGPL-3.0-only](https://img.shields.io/badge/license-AGPL--3.0--only-blue.svg)](LICENSE)

<img src="docs/images/icon.png" alt="Wren icon" width="96" align="right">

Wren is a hardened Signal client for Android. It is a fork of
[Molly](https://github.com/mollyim/mollyim-android), which is a fork of
[Signal](https://github.com/signalapp/Signal-Android). It talks to Signal's
servers, so your contacts, groups and calls stay exactly where they are.

Molly adds a passphrase lock for the database, a RAM wiper, automatic locking,
UnifiedPush, Tor and SOCKS support, and a build with no Google code. Wren
keeps all of that and adds the things people have asked Signal and Molly for
and never got.

A duress passphrase. Type it at the lock screen instead of your real one and
Wren erases every message, key and setting on the phone, right then.

A panic button that erases. Connect a PanicKit trigger such as
[Ripple](https://guardianproject.info/apps/info.guardianproject.ripple/) and
choose whether a press locks Wren or wipes it. Molly can only lock.

Wipe after wrong unlocks. Five, ten or twenty failed passphrase attempts and
the data is gone.

Export any chat. One chat, as HTML you can open in a browser, plain text, or
JSON, with the photos, voice notes and files next to it. Signal only offers a
full backup in its own format.

A pure black theme. Dark theme with true black backgrounds for OLED screens,
asked for on Molly's tracker for years. Settings, Appearance, Theme, Black.

A device check. One screen that reads your phone's security patch date, your
screen lock, and every privacy setting that matters, says which ones are weak,
and fixes the ones Wren controls with one tap. The chat list warns you when
the phone's security updates stopped six months ago.

## Status

Wren is new. There is no release yet and no signing certificate to verify
against. The code builds, the unit tests pass, and the features above are
implemented, but they have not been through a round of real-phone testing by
people other than me. Treat the first release as a beta and keep a backup.

The first build to download will land on the
[Releases](https://github.com/munzzyy/wren/releases) page, with SHA-256
sums and the signing fingerprint written here the same day.

## How Wren compares

| | Signal | Molly | Wren |
|---|---|---|---|
| Passphrase encryption of the database | no | yes | yes |
| RAM wiper, automatic lock | no | yes | yes |
| UnifiedPush (no Google push) | no | yes | yes |
| Tor and SOCKS proxy | no | yes | yes |
| Build without Google code | no | yes | yes |
| Reproducible builds | yes | yes | yes |
| Duress passphrase that wipes | no | no | yes |
| Wipe after N failed unlocks | no | no | yes |
| PanicKit responder | lock only | lock only | lock or wipe |
| Export one chat to HTML, text or JSON | no | no | yes |
| Pure black OLED theme | no | no | yes |
| Device check with one-tap hardened defaults | no | no | yes |
| Warning when the phone's security updates are stale | no | no | yes |

Everything else Signal does, Wren does, because it is Signal underneath.

## Wren on other devices

Wren is a family. Everything talks to Signal's servers, so any Wren, Molly or
Signal app can message any other, and a desktop or tablet links to your phone
the same way Signal Desktop does.

Android, this repository. The phone app, built from Molly.
Desktop, [munzzyy/wren-desktop](https://github.com/munzzyy/wren-desktop),
  built from Signal Desktop for Linux, Windows and macOS. Signal Desktop has no
  app lock at all; Wren Desktop gets a passphrase lock with a duress passphrase,
  wipe after failed attempts, auto-lock, and the same chat export.
iOS, [munzzyy/wren-ios](https://github.com/munzzyy/wren-ios), built from
  Signal iOS. Honest status: without an Apple developer account there is no
  App Store, no TestFlight and no push notifications, because Apple ties
  pushes to Signal's own bundle id. It builds, it can be sideloaded for seven
  days at a time with a free Apple ID, and it only receives messages while
  open. That repository explains the limits and what changes the day an
  account exists.

## Install

Nothing to install yet. When the first release is out:

- Download the APK from [Releases](https://github.com/munzzyy/wren/releases)
  and check the SHA-256 sum.
- Or add the repository to [Obtainium](https://github.com/ImranR98/Obtainium)
  and let it track releases.
- An F-Droid repository at `https://munzzyy.dev/wren/fdroid/` is planned;
  see [docs/FDROID-REPO.md](docs/FDROID-REPO.md).

Wren uses the package id `io.github.munzzyy.wren`, so it installs next to
Signal and Molly without touching them. Android 8.1 or newer.

![Wren in the app drawer of an Android 16 emulator](docs/images/icon-in-drawer.png)

## Moving from Signal or Molly

Wren reads the same local backups Signal and Molly write. Make a backup in
the old app, install Wren, restore from that file, and the old app can be
removed. Backups must come from the same or an older Signal version than
the one Wren is built on.

If you want to try Wren before moving your number, link it to your existing
Signal account as a secondary device (Settings, Linked devices). That is also
the safe way to test the duress wipe: it only erases the linked device.

Molly's own guide, [Migrating From
Signal](https://github.com/mollyim/mollyim-android/wiki/Migrating-From-Signal),
applies to Wren as written.

## The duress passphrase, honestly

Settings, Privacy, Data at rest. The duress passphrase only exists when the
regular passphrase lock is on. It costs the same to try as the real
passphrase, so it cannot be brute-forced from a copy of the app's files.

What a wipe does: deletes Wren's database, keys, preferences, caches and
attachments, then the app exits. There is no undo and no confirmation.

What a wipe does not do:

- It does not touch backups or chat exports you saved elsewhere.
- It does not erase your messages from other people's phones or from your
  linked devices.
- It does not unregister your number. Turn on Signal's Registration Lock PIN
  so nobody can re-register it.
- It does not promise anything about flash storage forensics. Android's
  file-based encryption helps, Wren's passphrase helps, a wipe is not a
  guarantee.
- It does nothing if the phone was copied before the wipe.

[docs/DURESS.md](docs/DURESS.md) has the full threat model.
[docs/DEVICE-CHECK.md](docs/DEVICE-CHECK.md) covers the device check and the black theme.

## Chat export

Open a chat, tap the name, Export chat. Pick HTML, text or JSON, choose
whether to include media, pick a folder. Wren writes `chat.html` (or `.txt`,
`.json`) and a `media/` folder next to it and shows a notification when it is
done. The export is not encrypted. Disappearing messages are exported as they
are at that moment. Details in [docs/EXPORT.md](docs/EXPORT.md).

## Keeping up with Signal

Signal clients stop working about 90 days after they were built. A fork that
falls behind dies. Wren merges Molly, and Molly merges Signal, so Wren is
only as current as Molly is. Right now that is Signal 8.19.2 while Signal
ships 8.29.3, and merging Signal directly into Wren is the first job after
this release.

A daily workflow fetches both upstreams and opens an issue the moment Molly
moves, with the exact commit range and the version gap, so the lag is always
public. `tools/merge-molly.sh` does the merge and reruns the rebrand.

## Build it yourself

See [BUILDING.md](BUILDING.md). The short version: JDK 21, the Android SDK,
and

```sh
./gradlew :app:assembleProdStoreRelease
```

`prodStore` has no in-app updater and is what ships on GitHub. `prodWebsite`
checks the Wren F-Droid repository for updates. The build is reproducible;
[reproducible-builds/README.md](reproducible-builds/README.md) explains how to
check a release against the source.

Molly's build takes the app name and package id from `app/gradle.properties`,
which is what makes Wren possible without touching thousands of files. After
every merge, `tools/rebrand.py` rewrites the app name in all 135 string
resource files and leaves MollySocket alone.

## Questions people ask

Is a third-party client allowed on Signal's servers? Signal does not
support them and could block them. It has tolerated Molly since 2020. Wren
behaves the same way Molly does on the network and uses no Signal branding.
Read the [Signal Terms](https://signal.org/legal/) before you register.

Why not just add this to Molly? Molly's tracker has asked for a duress
password since 2023. I wanted it on my own phone this year, and a fork is how
you get there. Fixes that belong upstream go upstream.

Can I run Wren next to Signal or Molly? Yes, with a different number, or
as a linked device of the same account.

Which version should I install? Wren is built without Google code, like
Molly-FOSS. Push notifications come over a WebSocket or UnifiedPush.

Where is the desktop app? There is none. Link Signal Desktop to your
account the normal way.

## Contributing

Issues and pull requests are welcome. Keep changes small, match the
surrounding style, and say what you tested. Security problems go to the
address in [SECURITY.md](SECURITY.md), not the tracker.

## License

AGPL-3.0-only, the same license as Signal and Molly. [LEGAL.md](LEGAL.md)
covers copyright and trademarks. Wren is not affiliated with Signal
Messenger, LLC, the Signal Foundation or the Molly project.

## Thanks

Signal for the protocol, the app and the servers. Molly for years of
hardening work that Wren stands on.
