<!-- Copyright 2026 Cole Munz -->
<!-- SPDX-License-Identifier: AGPL-3.0-only -->

# Threat model

This is what Wren is meant to hold up against, what it is not meant to hold up
against, and where I know it leaks. Everything below was checked against this
tree by reading the code, and I give the file for each claim so you can check
me. Where I have not verified something I say so. Nobody outside the project
has reviewed Wren yet, and the wipe paths have not been run on a real phone
(see Known gaps in the [README](../README.md)). If a sentence here is wrong,
that is a bug in the document and I want to hear about it at the address in
[SECURITY.md](../SECURITY.md).

[AUDIT-GUIDE.md](AUDIT-GUIDE.md) is the map of where each protection lives in
the code. [DURESS.md](DURESS.md), [DEVICE-CHECK.md](DEVICE-CHECK.md),
[EXPORT.md](EXPORT.md) and [PANIC.md](PANIC.md) cover single features in more
detail.

## Who Wren is for

Wren is a Signal client. It talks to Signal's servers and speaks Signal's
protocol, so the protocol's own guarantees (end-to-end encryption, forward
secrecy) are Signal's, not mine. What Wren adds is about the phone and the
network path, for three kinds of people:

- People whose phone can be taken, searched or pushed open: journalists,
  activists, people leaving someone dangerous, travelers at borders.
- People who need messages and calls to go through a proxy or Tor, because
  their network blocks Signal or because they do not want Signal to see their
  IP address.
- People who cannot or will not run Google services on their phone.

It is not for someone whose phone is already running an attacker's code, and
it does not hide that you use Signal from anyone who can watch your traffic
with no proxy.

## Assets

- The message database and the attachments next to it. These are what most
  adversaries want.
- Keys: Signal's identity and session keys inside the database, the database
  and attachment secrets, and Molly's master secret that wraps them.
- The passphrase, and the duress passphrase if you set one.
- The Signal account: the registered phone number, the Signal PIN, and the
  linked-device list.
- Contacts and the social graph that falls out of the chat list.
- The fact that Wren is installed. Its package id is `io.github.munzzyy.wren`
  and the launcher label is Wren, both visible to anyone who opens the app
  list or Android's app settings. The alternate launcher icons Signal ships are still
  in the manifest (the `RoutingActivityAlt*` aliases in
  `app/src/main/AndroidManifest.xml`) and change the launcher entry only.
- Exports and backups you made outside the app. Wren cannot protect those once
  they exist.

## Adversaries, by what they can do

Each of these is a capability, not a person. A border guard might be any of the
first three.

### Someone with your unlocked phone for a minute

They can open apps, read what is on screen, change settings, and connect or
install things. Against this, Wren has the lock on Wren itself (passphrase
lock, auto lock, the biometric screen lock), screen security, and notification
privacy. They can also do harm in that minute: connect a panic trigger, link a
new device, or turn off a protection. The device check shows what is off but
does not stop anyone from turning it off.

### Someone with your locked phone and time

A thief, a lab. They can copy storage, try to read it offline, and try guesses
on the device. Against this the passphrase lock, the KeyStore HMAC and the
Argon2 cost are the defense, together with whatever Android's own disk
encryption does. Wren's limit is the phone: if the attacker has a way to
extract or use the hardware-backed KeyStore, or a flash image taken while the
phone was unlocked and running, Wren gets weaker (see Residual risks).

### Someone who can coerce a passphrase

They are standing next to you. The duress passphrase and wipe-after-N exist for
this. They only help if the attacker has not already copied the storage, and
they do nothing if the attacker does not believe the fresh-install screen.

### A hostile app on the same phone

Android's sandbox keeps it out of Wren's files unless the phone is rooted. It
can still talk to Wren's exported components: it can lock Wren, it can try to
become the connected panic trigger, and it can read whatever notifications
Android shows it if it has notification access. Accessibility services can read
the screen as text even when screenshots are blocked.

### A network observer

Your ISP, the coffee shop, a state network. They can see that you connect to Signal's
hosts and when. A proxy changes who sees that and where it comes from; it does
not make it invisible.

### Signal's servers

They know the account exists, the phone number, when the app connects, from
which IP address (unless you use a proxy), your push settings, and the timing
and size of what is delivered. Signal publishes what it keeps and I have not
changed any of it. Wren cannot hide that your number is registered.

### A compromised linked device

A desktop or tablet linked to your account sees every new message and whatever
history was synced when it was linked. A wipe on the phone does nothing to it.

## Protections

Each protection below says what it is for, what it does not do, and where it
lives. Paths are relative to the repository root. Molly's code is marked as
Molly's; Wren's own code is under `app/src/main/java/io/github/munzzyy/wren/`.

### Passphrase lock at rest (Molly)

With the lock on, the database and attachment secrets cannot be
read from a copy of Wren's files. The secure preferences file
(`app/src/main/java/org/thoughtcrime/securesms/util/SecurePreferenceManager.java`)
encrypts every key except a short allow-list of harmless ones (theme,
language, the lock flags and a few more), and the database and attachment
secrets are not on the list. Those preferences are encrypted with a 64-byte
master secret kept in a separate `MasterKeys` file. The master secret is
wrapped with AES-GCM under a key made like this: Argon2id of the passphrase
and a random 16-byte salt, then HMAC-SHA256 under an Android KeyStore key
(`app/src/main/java/org/thoughtcrime/securesms/crypto/PassphraseBasedKdf.java`,
`app/src/main/java/org/thoughtcrime/securesms/crypto/MasterSecretUtil.java`).
The HMAC key is created by `core/util/src/main/java/org/signal/core/util/crypto/KeyStoreHelper.java`,
in StrongBox when the phone has it. Argon2 cost is benchmarked on the phone
when you set the passphrase and aims for about three seconds
(`app/src/main/java/org/thoughtcrime/securesms/crypto/Argon2Benchmark.java`).

Because of the HMAC step, a guess cannot be tested from the files alone: the
attacker also needs to run the HMAC inside the KeyStore.

With the lock off, the master secret is wrapped with a
fixed all-zero key (`MasterSecretUtil.getUnencryptedKey`), so that layer adds
nothing and only the KeyStore layer under Signal's own secrets and Android's
disk encryption remain. While Wren is unlocked the master secret sits in
memory (`app/src/main/java/org/thoughtcrime/securesms/service/KeyCachingService.java`),
and anything that can read Wren's memory or run as Wren's user gets it. The
KeyStore keys are not tied to your screen lock or biometrics, so code running
as Wren's user on a rooted phone can ask the KeyStore to compute the HMAC and
guess at the speed of one Argon2 run per guess. A short passphrase is still a
short passphrase.

### RAM wipe (Molly)

When Wren locks, it shuts down the job manager and clears the
cached master secret, then starts a service that exits Wren's process and
overwrites free memory pages with a native routine
(`app/src/main/java/org/thoughtcrime/securesms/service/WipeMemoryService.java`,
called from `onLock` in `app/src/main/java/org/thoughtcrime/securesms/ApplicationContext.java`).
The aim is a memory image taken after a lock that no longer holds the master
secret or message text.

It is best effort. The pass stops early when the system
runs low on memory, it cannot reach swapped or compressed pages, other
processes' memory, or the kernel's, and it does not run at all on a duress or
panic wipe, which kill the process without it. I have not watched the pass
finish on a real phone on every Android version. It will not help against an
image taken while Wren was unlocked.

### Auto lock (Molly)

After the screen turns off, an exact alarm locks Wren after the
timeout you chose, or at once if the timeout is zero. The lock button in the
main toolbar and the Lock action in the ongoing notification lock it manually
(`KeyCachingService.java`, `app/src/main/java/org/thoughtcrime/securesms/preferences/widgets/PassphraseLockTriggerPreference.java`).

It never locks while the screen stays on, so a phone left
unlocked on a desk stays open. It refuses to lock during an app migration or a
device transfer (`handleClearKey` in `KeyCachingService.java`). It does
nothing when the passphrase lock is off.

### Biometric screen lock (Molly)

After Wren has been in the background for a few seconds, its
screen is blanked and a fingerprint or face check is needed to see it again
(`app/src/main/java/org/thoughtcrime/securesms/ScreenLockController.kt`,
`app/src/main/java/org/thoughtcrime/securesms/biometric/BiometricDialogFragment.java`).
Strong biometrics are required unless the phone has no usable biometric
hardware, in which case a weaker biometric or the device credential is
accepted.

It is a screen cover and not a lock on the keys: the master
secret stays in memory behind it. If the phone's biometrics are removed, Wren
turns the biometric lock off and carries on to the passphrase prompt
(`onNotEnrolled` in `app/src/main/java/org/thoughtcrime/securesms/PassphrasePromptActivity.java`).
That downgrades the outer layer only.

### Duress passphrase (Wren)

A second passphrase that, typed at the lock screen, wipes the
app instead of unlocking it. Wren stores a random 16-byte salt and a SHA-256 hash of a key derived from
the duress passphrase. The derivation is the same Argon2id at the same cost as
the real passphrase, followed by an HMAC under a second KeyStore key with its
own alias
(`app/src/main/java/io/github/munzzyy/wren/duress/DuressVerifier.kt`,
`DuressManager.kt`, `DuressStore.kt`). It is checked only after the real
passphrase is rejected, so the real passphrase path is the one Molly wrote.
Wren refuses to save a duress passphrase equal to the real one and refuses
to change the real one into the duress one
(`ChangePassphraseDialogFragment.java`, `DuressPassphraseDialogFragment.kt`).
Turning the passphrase lock off clears the duress passphrase and deletes its
KeyStore key (`PrivacySettingsViewModel.kt`).

The settings file `wren-duress` is ordinary
SharedPreferences, not encrypted with the master secret, so anyone who can
read Wren's data directory can see that a duress passphrase, a failed-attempt
limit and a panic trigger are configured. They cannot see the passphrase.
The check fails closed against accidents: if the duress check throws, the
attempt counts as an ordinary wrong passphrase and nothing is wiped. It does
not help if the attacker imaged the phone before you typed it, and a fresh
install after a wipe looks like what it is.

### Wipe after N failed unlocks (Wren)

After 5, 10 or 20 wrong passphrases in a row, Wren wipes. The
count is stored on disk, so killing the app between guesses does not reset it
(`FailedAttemptPolicy.kt`, `DuressManager.onWrongPassphrase`, both under the
Wren duress folder).

Only an attempt that reaches the passphrase check is
counted: wrong fingerprints are not, and neither is a KeyStore failure
(`UnrecoverableKeyException` in `PassphrasePromptActivity.java`). The counter
is a plain preference, so root can reset it. If Android refuses to write the
counter, `DuressStore.commit` throws, `DuressManager` logs it and the guess
goes uncounted; the protection stops applying rather than the app wiping on a
storage error. It is off by default, and with the passphrase lock off there is
no prompt for it to count.

### PanicKit wipe and the connected-trigger rule (Wren)

Wren answers the PanicKit intents TRIGGER, CONNECT and
DISCONNECT in `PanicResponderActivity.kt`. Only the app that is connected as
the trigger can make Wren erase, and a newly connected trigger starts out set
to Lock. The caller is identified with `getCallingPackage`, which Android
fills in only for `startActivityForResult`. Molly's broadcast receiver
(`app/src/main/java/org/thoughtcrime/securesms/service/PanicResponderListener.kt`)
is still registered for triggers that only broadcast; it can lock and never
erase. [PANIC.md](PANIC.md) walks through the handshake.

The trigger is identified by package name, not by its
signing certificate. If the trigger app is uninstalled and a different app
with the same package name is installed, Wren will treat it as the trigger.
The first app to send CONNECT wins, and Wren shows no confirmation prompt for
it, because the activity has no UI. The safeguard is that CONNECT can never
arm Erase: the user has to switch the action in Settings after seeing which
app is connected. Any app can still lock Wren with the broadcast, which is a
nuisance and nothing worse, and that was already true in Molly.

### Screen security (Molly)

It blocks screenshots, screen recording and the app-switcher
thumbnail for Wren's windows (`FLAG_SECURE` through
`app/src/main/java/org/thoughtcrime/securesms/components/TemporaryScreenshotSecurity.kt`,
and `setRecentsScreenshotEnabled` on Android 13 and later in
`app/src/main/java/org/thoughtcrime/securesms/BaseActivity.java`). It is on by
default.

It does not stop a camera pointed at the screen, and it
does not stop an accessibility service from reading the text on screen.

### Notification privacy (Signal and Molly)

With the notification setting at "no name or message", Wren
posts notifications that carry neither, so there is nothing on the lock screen
to read. The device check treats only that setting as hardened (`app/src/main/java/io/github/munzzyy/wren/devicecheck/HardeningPlan.kt`).

A notification still appears, so anyone looking at the
phone can tell a message arrived and that Wren got it. A hostile app with
notification access reads whatever Wren posts, so the setting is the only
thing keeping content away from it.

### Block unknown (Molly)

With it on, incoming messages from people who are not saved in
your contacts, have not had your profile shared with them, and are not in a
group whose profile you share are dropped when the app processes them
(`shouldBlockSender` in `app/src/main/java/org/thoughtcrime/securesms/messages/MessageContentProcessor.kt`).

The phone still receives the message from the server; it
is thrown away after delivery. Blocking is not a way to hide your account.

### Proxy and Orbot (Molly)

Wren supports SOCKS5 and Orbot proxy settings
(`app/src/main/java/org/thoughtcrime/securesms/net/NetworkManager.java`,
`app/src/main/java/org/thoughtcrime/securesms/preferences/NetworkPreferenceFragment.java`,
Orbot control in `lib/netcipher/src/main/java/info/guardianproject/netcipher/proxy/OrbotHelper.java`).
When a proxy is set, the shared socket factory refuses to connect until the
proxy address is known, and falls back to a black-hole address rather than a
direct connection (`DUMMY_PROXY` in `app/src/main/java/org/thoughtcrime/securesms/net/Networking.kt`).
DNS goes through DNS-over-HTTPS requests that themselves use the proxy. Molly's
builds of libsignal and RingRTC never fall back to a direct connection when a
proxy is set, and calls take the proxy too
([SIGNAL-MERGE-LOG.md](SIGNAL-MERGE-LOG.md) explains why keeping those two
forks is what holds Wren at Signal 8.20.5).

Signal's servers still see the traffic, just from the
exit's address, and a Tor exit or SOCKS server sees that you are talking to
Signal. With a proxy on, the DNS-over-HTTPS resolvers (1.1.1.1 and 9.9.9.9)
see the hostnames Wren looks up. With no proxy, the system resolver sees
them. I have not audited every network path in the app (maps, WebView, link
previews, the in-app updater) for proxy leaks; that is on the reviewer list
below.

### UnifiedPush (Molly)

It lets notifications arrive through a distributor you choose
instead of Google's push service. The hand-off to a MollySocket server works
by registering that server as a linked device named "MollySocket"
(`createDevice` in `app/src/main/java/im/molly/unifiedpush/MollySocketRepository.kt`).

The MollySocket server and the distributor can both see
when you receive messages. From reading `createDevice`, the server is given a
device id and a password, not your keys, so I do not expect it can read
messages, but I have not audited MollySocket. It shows up in your linked
devices list. If you host it yourself, you are trusting your own host.

### No Google code (Molly)

The build has no Play Services dependency. Firebase's messaging
client is still compiled in for inherited code paths, with its Google Play
Services modules swapped for the stubs under `core-gms/`
(`app/build.gradle.kts`).

I have not captured traffic to prove the app never talks
to a Google host on a phone without Play Services, so "no Google code" means
"no Google dependency", not a measured claim about the network. The Android
Backup Service key-value agent is also still enabled
(`app/src/main/java/org/thoughtcrime/securesms/absbackup/SignalBackupAgent.kt`);
it backs up Signal's SVR auth tokens only, and on a phone with Google's backup
transport that goes to Google. I did not change it.

### Reproducible builds (Molly)

It lets you rebuild a tagged release in Docker and compare it to the
published APK ([reproducible-builds/README.md](../reproducible-builds/README.md),
[BUILDING.md](../BUILDING.md)). Gradle dependency verification is on
(`gradle/verification-metadata.xml`).

It proves the APK matches the source at the tag, not
that the source is free of mistakes. There is no Wren release yet, so no
comparison against a published Wren APK has been made. Building on the same
machine twice shows the build is deterministic, not that the machine is clean.

### Device check and hardened defaults (Wren)

It is one screen that reads the security patch date, the Android
version, whether the phone has a screen lock, and the privacy settings above,
and flips the ones Wren controls in one step
(`app/src/main/java/io/github/munzzyy/wren/devicecheck/`, see
[DEVICE-CHECK.md](DEVICE-CHECK.md)). It never sets a passphrase, a duress
passphrase or Registration Lock, because each needs a secret only you can pick.

It reads settings and does not stop anything from changing them, so it is a
mirror and not a guard. A passing score does not mean the phone
is safe: it cannot see malware, a rooted state, a malicious accessibility
service or a bad keyboard. The patch date is whatever the phone's maker put in
`Build.VERSION.SECURITY_PATCH`.

### Black theme (Wren)

Not a protection. It moves colors toward `#000000` for OLED screens
(`app/src/main/java/io/github/munzzyy/wren/theme/BlackTheme.kt`). I list it
here so nobody reads it as one.

## Residual risks

These are the things I know are not solved, in rough order of how much they
could matter.

- Flash forensics. Deleting files on flash storage does not reliably erase
  them. The wipe deletes the KeyStore keys first, so leftover database and
  attachment files are encrypted to a key that no longer exists. That covers
  what is keyed from the KeyStore-sealed secrets
  (`app/src/main/java/org/thoughtcrime/securesms/crypto/DatabaseSecretProvider.java`).
  It does not cover anything Wren wrote to disk unencrypted, such as the
  allow-listed preferences and the `wren-duress` file.
- A copy made before the wipe. If the storage was imaged first, the wipe
  does not touch the image. Its protection is the passphrase and the KeyStore
  key of the phone it came from.
- Backups and exports outside the app. Signal backups you saved, chat exports
  (written unencrypted on purpose, see [EXPORT.md](EXPORT.md)), files you
  shared to other apps and photos saved to the gallery survive a wipe.
- Linked devices. A wipe on one device does not touch the others, and Wren
  does not tell the server a linked device is gone. The entry stays in the
  primary phone's list until you remove it there.
- The other end of every chat. Everyone you talked to keeps their copy, and a
  screenshot or a forward can leave their phone.
- The server knows the account exists. A wiped phone leaves the number
  registered. Registration Lock with a Signal PIN is what stops someone else
  taking it over.
- Two native libraries are built with 4 KB page alignment. `libargon2.so`
  and `libnative-utils.so` come from prebuilt Molly artifacts, and on phones
  with 16 KB pages they run in a compatibility mode. I know of no security
  effect, only a compatibility one, but it is the thing
  `tools/apk-report.sh` flags until they are rebuilt.
- Timing when duress is on. A wrong passphrase costs two key derivations
  instead of one, so a rejection takes about twice as long as an unlock.
  Someone who has watched you do both can tell the feature is on.
- Biometric before passphrase. When the biometric screen lock is active at
  the prompt, the fingerprint or face check runs before the passphrase is
  looked at (`PassphrasePromptActivity.unlock`). That helps against anyone who
  cannot pass the biometric, since they never get to try a passphrase and
  nothing is counted against the unlock limit. It also means a forced finger gets the attacker to the
  passphrase prompt, and a duress passphrase only fires after that check has
  passed.
- The first panic trigger to connect wins, with no prompt, and is trusted by
  package name (see above).
- What survives a wipe outside the data directory. The wipe removes the app's
  databases, preferences, files, caches and external app folders and then asks
  Android to clear the app's data. I have not listed what Android leaves
  behind (launcher shortcuts, the contact-sync account entry, notification
  history on some systems).
- Android's own limits. A rooted phone, a malicious keyboard, a hostile
  accessibility service or a compromised OS are out of reach for an app.
- Wren is on Signal 8.20.5 and Signal ships newer releases. Fixes in those
  releases are not in Wren, and client builds expire about 90 days after they
  are built. The reason and the plan are in the README and
  [SIGNAL-MERGE-LOG.md](SIGNAL-MERGE-LOG.md).

## Non-goals

Wren does not try to hide that you use Signal from your network, to defend
against a compromised phone OS, to make a coerced person safe from a coercer
who does not stop, or to change Signal's protocol or what Signal's servers
learn. Nothing here is medical, legal or physical-safety advice: a wipe
button can be pressed by the wrong person, and in some places an empty phone
is itself a reason for suspicion.

## What I would test first

If you are reviewing Wren, this is the order I would go in, most important
first. [AUDIT-GUIDE.md](AUDIT-GUIDE.md) tells you where to look for each.

1. Pull Wren's data directory from a test phone with the passphrase lock on
   and Wren locked. Check the database does not open, that only the
   allow-listed preference keys are readable, and that guessing the
   passphrase offline fails without the phone's KeyStore.
2. The panic handshake. CONNECT from two different test apps, TRIGGER from an
   app that is not connected while Erase is armed (expect a lock, not a
   wipe), TRIGGER sent with `adb shell am start` (no calling package, expect
   no wipe), and a broadcast-only trigger (expect a lock at most).
3. The duress path from the prompt: wrong, duress and right passphrases, with
   and without the biometric lock, timed. Then after the wipe, list the data
   directory and the KeyStore aliases and everything outside them I have not
   enumerated.
4. A packet capture with a SOCKS proxy set, on a phone with no Play Services:
   look for any direct connection, any DNS outside the proxy, any traffic to
   Google, in messaging, calls, link previews, maps and the updater.
5. The failed-attempt counter across process kills, and with the preference
   file made unwritable.
6. An HTML export of a chat holding hostile markup and odd file names, opened
   in a browser (see `docs/EXPORT.md` and the writer in
   `app/src/main/java/io/github/munzzyy/wren/export/ChatExportWriter.kt`).
7. `tools/apk-report.sh` on a built APK, and a Docker rebuild compared with
   `reproducible-builds/apkdiff/apkdiff.py`.
8. Whether the key-value backup agent should stay on at all.
