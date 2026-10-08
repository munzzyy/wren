<!-- Copyright 2026 Cole Munz -->
<!-- SPDX-License-Identifier: AGPL-3.0-only -->

# Duress passphrase, unlock limit and panic button

Wren can erase itself. There are three ways to make that happen, and all of
them are off until you turn them on in Settings > Privacy. A wipe happens right
away and there is no undo, so read this before you enable any of them.

All three need database encryption (the passphrase lock) to be on, except the
panic button's erase action, which works either way.

## Duress passphrase

This is a second passphrase you set next to your real one. If someone makes you
open Wren, you type the duress passphrase instead. Wren spins for the usual
moment, then deletes everything it keeps on the phone and closes. When you open it again it looks like a fresh install.

Turn it on under Data at rest > Duress passphrase. Wren refuses a duress
passphrase that matches your real one, and it refuses to change your real
passphrase to one that matches your duress passphrase.

How it's stored: I keep a SHA-256 of an Argon2 key derived from the duress
passphrase with the same cost settings as your real passphrase, and that key
is also run through an HMAC whose key lives in the Android KeyStore. So a guess
at the duress passphrase costs as much as a guess at the real one, and copying
the settings file off the phone is not enough to test guesses offline.

Checking the duress passphrase costs a second key derivation, so a wrong
passphrase takes about twice as long to be rejected when duress is on.

If you use fingerprint or face recognition on top of the passphrase, the duress
passphrase only fires after the biometric check passes, same as the real one.

## Wipe after failed unlock attempts

Pick Off, 5, 10 or 20. After that many wrong passphrases in a row, Wren wipes
itself the same way the duress passphrase does. Typing the right passphrase
resets the count. The count survives restarts, so closing the app between guesses does
not help an attacker.

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
3. Back in Wren, Settings > Privacy > Panic button should now show Ripple as
   the connected trigger app.
4. Only now can you switch the panic action to Erase all data.

Some details that matter:

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

## How the wipe works

First Wren deletes every key it holds in the Android KeyStore, including the
one that protects the database key. From that moment the encrypted database
can't be decrypted even if some files are left behind. Then it asks Android to
clear all of its app data, the same call Signal uses when you delete your
account. If Android refuses, Wren deletes its databases, settings, files and
caches itself and exits.

## What this does not protect against

Read this list. A wipe only removes what is on this phone, inside Wren.

- Backups and exports outside the app. Signal backups you saved to storage, a
  computer or the cloud are not touched. Neither is anything you exported,
  shared to another app or saved to your gallery.
- Linked devices. Your desktop or tablet still has its copy of your messages.
  Unlink them, or wipe them separately.
- The other side of every chat. Everyone you talked to still has the messages
  on their phones.
- Flash storage forensics. Deleting files on flash memory doesn't reliably
  overwrite them. Deleting the KeyStore keys first is what makes leftovers
  useless, and that only holds as long as the database stayed encrypted with
  the passphrase lock.
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
