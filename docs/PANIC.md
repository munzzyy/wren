<!-- Copyright 2026 Cole Munz -->
<!-- SPDX-License-Identifier: AGPL-3.0-only -->

# Panic button

Wren answers PanicKit, the small protocol the Guardian Project built so that
one "panic" app can tell other apps to protect themselves. Ripple is the
trigger app most people will use. Any app that speaks PanicKit can be the
trigger.

Molly already answered a trigger by locking. Wren adds a second choice,
erasing everything, and adds rules about who is allowed to ask for it. This
page covers how to connect a trigger, what Wren does with each message, why
it works the way it does, how to test it without risking your real account,
and what it cannot do. The code is in
`app/src/main/java/io/github/munzzyy/wren/duress/PanicResponderActivity.kt`
and `app/src/main/java/io/github/munzzyy/wren/duress/PanicDecision.kt`. What
an erase does is in [DURESS.md](DURESS.md), and the reasoning about hostile
apps is in [THREAT-MODEL.md](THREAT-MODEL.md).

## Connecting a trigger

1. Install the trigger app (for Ripple, from F-Droid or the Guardian Project's
   site).
2. Open the trigger app and turn Wren on in its list of apps to trigger. The
   trigger app sends Wren a CONNECT request.
3. In Wren, open Settings, Privacy, Panic button. "Connected trigger app"
   should show the trigger's name and package id. Read the package id. If it
   is not the app you meant, tap it and disconnect.
4. Leave the action on "Lock app" for now. Test it (below). Switch to "Erase
   all data" only when you have seen it work and you mean it.

The action picker stays disabled until a trigger is connected.

## What Wren does with each message

Wren handles three PanicKit actions. All three arrive at
`PanicResponderActivity`, which has no screen and finishes straight away.
Wren learns who sent a message from `getCallingPackage()`, which Android fills
in only when the sender used `startActivityForResult`.

CONNECT. If no trigger is connected and the sender is known, Wren stores the
sender's package name, sets the action to Lock, and answers RESULT_OK. If the
same app connects again, Wren answers RESULT_OK and changes nothing, so a
reconnect does not undo an Erase setting you chose on purpose. If a different
app tries to connect while one is already connected, Wren answers
RESULT_CANCELED and changes nothing. A sender Wren cannot identify, or Wren
itself, is refused.

TRIGGER. Wren erases only if the action is set to Erase and the sender is the
connected trigger. In every other case it locks, if the passphrase lock is on,
and does nothing if it is off. A trigger from an unknown sender, from an
unconnected app, or while the action is Lock can lock Wren and can never
erase it.

DISCONNECT. If the sender is the connected trigger, Wren forgets it and sets
the action back to Lock. From anyone else, Wren changes nothing.

Disconnecting from inside Wren (Settings, Privacy, Panic button, tap the
connected app) does the same thing as DISCONNECT. Wren also has Molly's older
broadcast receiver for triggers that never open an activity
(`app/src/main/java/org/thoughtcrime/securesms/service/PanicResponderListener.kt`).
It locks when the passphrase lock is on and cannot erase, because a broadcast
receiver cannot learn who sent the broadcast.

"Lock" means the same thing as the Lock button: the master secret is dropped,
the process exits and free memory is overwritten, and the next open asks for
the passphrase. It also clears message notifications.

## Why a new trigger starts on Lock and never on Erase

CONNECT comes from the other app, not from a screen inside Wren. Nothing in
Wren shows you a dialog when it arrives, and if the screen is off or Wren is
locked it still arrives. If a connection could arrive already set to erase,
then any app that managed to connect first, or anyone with your unlocked phone
for a minute, could arm a wipe without you reading a single word.

So connecting only registers the app. Turning on Erase is a separate step
inside Wren, after you have looked at which app is connected. Connecting or
disconnecting any trigger resets the action to Lock for the same reason: the
setting you chose for one app is not a setting for a different one.

Only one app can be connected at a time, and a different app cannot replace
it. To change triggers you disconnect the first one yourself. That stops a
second app from quietly taking over an Erase you had set up for the first.

## Testing it safely

Do not test an erase on the phone that holds your only copy of your messages.
Use a linked device, which is a second install that can be wiped without
touching your account or your primary phone.

1. On your primary phone, link a new Wren install as a secondary device
   (Settings, Linked devices). The README explains this under "Moving from
   Signal or Molly".
2. On the linked install, turn on the passphrase lock with a throwaway
   passphrase. This is what makes Lock do something.
3. Connect the trigger app as above. Check the Panic button section.
4. With the action on Lock, use the trigger. Wren should lock and ask for the
   passphrase. Unlock it again.
5. Switch the action to Erase all data and use the trigger. Wren should exit
   and, when you open it, look like a fresh install. Remove the dead entry
   from the primary phone's Linked devices list, because Wren does not tell
   the server it was wiped.
6. Negative tests, on a fresh linked install with Erase armed. Start the
   responder without a calling package:
   `adb shell am start -n <package>/io.github.munzzyy.wren.duress.PanicResponderActivity -a info.guardianproject.panic.action.TRIGGER`
   where `<package>` is the package id of your build. Expect a lock or
   nothing, never an erase. Then send a second app's CONNECT while the first
   trigger is connected (any small PanicKit test app will do) and check it is
   refused and the connected app in Settings has not changed.

Wren logs each decision as `Panic trigger: LOCK`, `WIPE` or `NOTHING`, plus
`Panic trigger connected` and `Panic trigger connect refused`. Use a debug
build if you want to read them in logcat; I have not checked how much of that
log a release build exposes.

The decision logic has unit tests
(`app/src/test/java/io/github/munzzyy/wren/duress/PanicDecisionTest.kt`).
The activity, the settings screen and the wipe have no automated tests and
have not been run on a real phone against a real Ripple yet. Treat this as a
beta feature until someone has.

## Limits

- Erase works with the passphrase lock off. Lock does not: with the lock off,
  there is nothing to lock, and a trigger that is not the connected one does
  nothing.
- A trigger that only broadcasts can lock and never erase. Whether your
  trigger uses an activity result or a broadcast depends on that app. I have
  not read Ripple's current source for it, so test yours.
- Wren trusts the trigger by package name, not by signing certificate. If the
  trigger app is removed and another app with that package name is installed,
  Wren will treat it as the trigger. Check the package id in Settings if you
  reinstall.
- The first app to send CONNECT wins. If a hostile app connects before the
  one you meant, it shows in Settings under its own name, and you can
  disconnect it. It cannot erase while the action is Lock. It can lock Wren
  at any time with a broadcast, as in Molly.
- Lock refuses to run during an app migration or a device transfer.
- A trigger needs the phone to be on and the trigger app to run. It does
  nothing if the phone is off, and it cannot reach a Wren that was already
  uninstalled.
- An erase removes what is on this phone inside Wren. It does not touch
  backups, exports, linked devices, the other side of your chats or your
  registration on Signal's server. [DURESS.md](DURESS.md) lists these, and the
  [threat model](THREAT-MODEL.md) says what stays behind.
- An erase has no confirmation and no undo. Anyone who can press your trigger
  can press it, and a child or a pocket can do so by accident.
