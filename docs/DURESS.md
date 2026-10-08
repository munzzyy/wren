<!-- Copyright 2026 Cole Munz -->
<!-- SPDX-License-Identifier: AGPL-3.0-only -->

# Duress passphrase, unlock limits, panic button and lock triggers

Wren can erase itself. There are four ways to make that happen, and all of
them are off until you turn them on in Settings > Privacy. A wipe happens right
away and there is no undo, so read this before you enable any of them.

All of them need database encryption (the passphrase lock) to be on, except
the panic button's erase action, which works either way.

Further down: asking for the passphrase again before sensitive changes, locking
when the phone opens a USB data connection, and routing Wren through Orbot.

## Duress passphrase

This is a second passphrase you set next to your real one. If someone makes you
open Wren, you type the duress passphrase instead. Wren spins for the usual
moment, then deletes everything it keeps on the phone and closes. When you open it again it looks like a fresh install.

Turn it on under Data at rest > Duress passphrase. Wren refuses a duress
passphrase that matches your real one, and it refuses to change your real
passphrase to one that matches your duress passphrase. The change dialog
checks your current passphrase before it compares the new one with the duress
passphrase, so someone holding your unlocked phone can't use it to test
guesses at the duress passphrase. The duress dialog closes after "this is your
real passphrase", so testing guesses at the real one there costs a fresh
passphrase check each time, and that check counts toward the limit below.

How it's stored: I keep a SHA-256 of an Argon2 key derived from the duress
passphrase with the same cost settings as your real passphrase, and that key
is also run through an HMAC whose key lives in the Android KeyStore. So a guess
at the duress passphrase costs as much as a guess at the real one, and copying
the settings file off the phone is not enough to test guesses offline.

Checking the duress passphrase costs a second key derivation. So that the
time a rejection takes doesn't tell anyone whether a duress passphrase is set,
Wren runs a second derivation on every wrong passphrase while the lock is on:
the duress check when there is one, and otherwise a decoy with the same cost
settings against a random value nothing can match. A wrong passphrase takes
about twice as long to be rejected either way.

If you use fingerprint or face recognition on top of the passphrase, the duress
passphrase only fires after the biometric check passes, same as the real one.

## Wipe after failed unlock attempts

Pick Off, 5, 10 or 20. After that many wrong passphrases in a row, Wren wipes
itself the same way the duress passphrase does. Typing the right passphrase
resets the count.

Wren counts each attempt and saves the count before it checks the passphrase.
Killing the app while the check runs doesn't take the guess back: the attempt
stays counted as a wrong one, and if that brings the count to the limit, the
next attempt wipes before it checks anything. If Wren can't save the count
(say the storage is full), it refuses the attempt instead of letting it
through uncounted.

The count lives in a file under Android's no_backup folder, so restoring an
adb backup of Wren doesn't reset it. Root can still edit it.

Be honest with yourself about this one. A kid, a partner or a bored friend
typing random stuff can trigger it. 10 or 20 is a lot safer than 5 if anyone
else ever handles your phone.

## Panic button

Wren answers PanicKit triggers, the system Guardian Project built for "panic
button" apps such as Ripple. Under Panic button you pick what happens:

- Lock app: Wren locks behind the passphrase. This is what Molly already did.
- Erase all data: Wren wipes itself.

To connect Ripple:

1. Install Ripple (it's on F-Droid).
2. Open Ripple, go to its list of apps it can trigger, and turn Wren on.
3. Wren asks "Let this app trigger Wren's panic action?" and shows the app's
   name, package name and the SHA-256 of its signing certificate. Check the
   package name (Ripple's is `info.guardianproject.ripple`) and tap Allow.
   Deny, back, or tapping outside the box all refuse it.
4. Back in Wren, Settings > Privacy > Panic button should now show Ripple as
   the connected trigger app, with the same certificate.
5. Only now can you switch the panic action to Erase all data, and Wren asks
   for your passphrase before it does.

Some details that matter:

- No app is ever connected without that confirmation. Android tells Wren
  which app asked (that's why Wren is opened for a result instead of sent a
  broadcast), and that package name is what the confirmation shows and what
  gets stored, together with the certificate digest. The Allow button ignores
  taps while another app draws over it, so an overlay can't click it for you.
- Every trigger, connect and disconnect from that package is checked against
  the stored certificate. An app reinstalled under the same package name with
  a different signing key gets what any other app gets, a lock at most, and
  Wren disconnects the trigger. So does a trigger that rotates its own key;
  connect it again and check the new certificate. A trigger connected before
  Wren recorded certificates shows as not connected until you connect it
  again.
- If the same app is already connected, a repeat connect request returns OK
  without asking again.
- Only the connected trigger app can erase Wren. Any other app that sends a
  panic signal gets a lock at most, and only when the passphrase lock is on.
- Another app cannot replace the connected trigger. To switch trigger apps,
  tap Connected trigger app in Wren and disconnect the old one first.
- Connecting or disconnecting a trigger app always resets the panic action to
  Lock app, so you have to pick Erase all data again yourself after you see
  which app is connected.
- Apps that only broadcast the panic signal (instead of opening Wren for a
  result) can only lock Wren, because Android does not tell a broadcast
  receiver who sent it.

## Erase if not unlocked

This is a dead man's switch. Pick Off, 3, 7, 14 or 30 days under Data at rest >
Erase if not unlocked. If nobody types your passphrase for that long, Wren
wipes itself the same way the duress passphrase does.

What resets the countdown: entering your passphrase on Wren's lock screen, or
at one of the passphrase checks described below. Nothing else does. Opening an
already unlocked Wren does not count, receiving messages does not count, and
time with the phone switched off does count. If you keep Wren unlocked for days
at a time (no lockdown triggers, no timeout), it will still erase when the time
runs out. Set Automatic lockdown so Wren locks on its own and you'll be typing
the passphrase anyway.

Details:

- Turning it on, or picking a different number of days, starts the countdown
  from that moment. It can never fire sooner than the number of days you
  picked after you set it.
- Wren stores the time of your last unlock only while this is on, in the same
  no_backup file as the failed-attempt count, so an adb backup can't roll it
  back. Turning it off deletes that time.
- The check runs from an alarm Wren sets for the deadline, and again after a
  reboot, after an app update and whenever the clock is changed. It works
  while Wren is locked. Android may run the alarm a little late when the phone
  is idle, and much later if Wren is battery restricted.
- Turning the passphrase lock off turns this off too. With no lock there is no
  unlock to wait for.

The clock: Wren compares the phone's wall clock with the stored unlock time.

- If the clock moves backwards (before the last unlock), Wren never wipes for
  that. It restarts the countdown from the new time.
- If the clock moves forward past the deadline, Wren wipes. That means someone
  who can change the phone's clock, by hand or by spoofing network time, can
  trigger the wipe early. They can also delay it by moving the clock back,
  because that restarts the countdown. Leave this off if either would hurt you.
- Force stopping Wren from Android's settings cancels its alarms, and Android
  then keeps it from starting at boot until someone opens it. A person with
  your unlocked phone can do that, so this guards against a phone left in a
  drawer or a seizure where nobody touches Wren, not against someone working
  on the phone.

## Passphrase check before sensitive actions

When the passphrase lock is on, Wren asks for the passphrase again before any
of these:

- Chat exports, one chat or all of them. That covers Wren's export and
  Signal's chat history export in Chats settings.
- The passphrase lock itself: turning it off or changing the passphrase.
  Molly's dialogs for these already ask for the current passphrase. Now a
  wrong one there counts as a failed attempt, and the duress passphrase there
  erases Wren.
- The duress passphrase: on, off or a new one.
- The failed-attempt limit.
- The inactivity erase setting.
- The panic action, in either direction.
- Disconnecting the panic trigger app.
- Turning the USB lock off.

The rule is simple: anything that could erase data or weaken a protection.
Turning the USB lock on does not ask, because it only adds protection.

This is the same check as the lock screen. The passphrase goes through the
same Argon2 derivation on a background thread. The attempt is counted before
the check, the same way, so the failed-attempt limit applies here, and typing
the duress passphrase here erases Wren right away. A right one resets the failed
count and the inactivity countdown. If the screen rotates while the box is
open, the box closes and nothing happens; you start the action again.

With the passphrase lock off there is nothing to check against, so these
actions go through without a prompt.

## Lock on USB data connection

Under Data at rest > Lock on USB data connection. When the phone starts a USB
data connection, Wren locks the way a panic trigger locks it: the key leaves
memory and you need the passphrase to get back in.

What counts as data: file transfer (MTP), photo transfer (PTP), USB debugging
(ADB), USB tethering (RNDIS and NCM) and accessory mode. Plain charging does
not lock, and neither do MIDI, audio or webcam modes. With USB debugging
turned on, plugging into a computer counts even if you picked "charging only",
because ADB is a data connection.

Details:

- Wren listens for this only while the option is on and only while Wren is
  running. A stopped Wren holds no key in memory, so there is nothing to lock.
- It locks at the moment a data connection starts. A cable that was already
  plugged in when you turned the option on, or when Wren started, does not
  lock it. Unplug and plug back in and it will.
- It needs the passphrase lock. Without it there is nothing to lock.

What it does not do: it doesn't stop a forensic tool that gets into the phone
some other way, and it doesn't protect anything outside Wren. It narrows one
common path, plugging an unlocked phone into a computer, so Wren's data is
already locked by the time the computer can talk to the phone.

## Route through Orbot

Settings > Privacy > Check this device has a Route through Orbot row, shown
only when Orbot is installed. Tapping it asks first, then sets Wren's proxy to
Tor via Orbot through the same steps Molly's Network settings use, and asks
Orbot to start. It only ever picks Orbot. It doesn't change a proxy you set
yourself unless you confirm, and it is not part of Apply hardened defaults,
because if Orbot stops Wren cannot send or receive until it runs again. When
Wren already goes through Orbot, tapping the row opens the network settings.

## How the wipe works

First Wren deletes every key it holds in the Android KeyStore, including the
one that protects the database key. From that moment the encrypted database
can't be decrypted even if some files are left behind. Then it asks Android to
clear all of its app data, the same call Signal uses when you delete your
account. If Android refuses, Wren deletes its databases, settings, files and
caches itself and exits.

Wren doesn't log why it wiped or locked. The system log (logcat) lives outside
Wren's storage and survives the wipe, so the duress, failed-attempt,
inactivity, panic and USB paths write nothing there that names the cause.

## What this does not protect against

Read this list. A wipe only removes what is on this phone, inside Wren.

- Backups and exports outside the app. Signal backups you saved to storage, a
  computer or the cloud are not touched. Neither is anything you exported,
  shared to another app or saved to your gallery. An export that was still
  being written when Wren was wiped or killed stays behind in the folder you
  picked, under a hidden name that starts with a dot and ends in `.partial`.
  Delete it yourself; Wren can't, because it no longer exists.
- Linked devices. Your desktop or tablet still has its copy of your messages.
  Unlink them, or wipe them separately.
- The other side of every chat. Everyone you talked to still has the messages
  on their phones.
- Flash storage forensics. Deleting files on flash memory doesn't reliably
  overwrite them. Deleting the KeyStore keys first is what makes leftovers
  useless: the database and attachment secrets are sealed by a KeyStore key
  whether or not the passphrase lock is on, so leftover database and
  attachment files can't be decrypted once that key is gone. That does not
  cover what Wren keeps unencrypted, such as the plaintext preferences (the
  duress settings file and the failed-attempt file among them), and it does
  not cover exports.
- Someone who copies the phone before the wipe. If an attacker images the
  storage first and then makes you type the duress passphrase, they still
  have the encrypted copy. It's protected by your real passphrase and the
  phone's KeyStore, not by the wipe.
- Your registration on the Signal server. After a wipe your phone number is
  still registered until someone registers it again. Set a Signal PIN and turn
  on Registration Lock so nobody else can take over your number.
- Someone who watches which passphrase you type, or who knows Wren has this
  feature. The duress passphrase destroys the data fast. It does not fake an
  empty app, and a fresh install after a wipe looks like exactly that.
