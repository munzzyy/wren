<!-- Copyright 2026 Cole Munz -->
<!-- SPDX-License-Identifier: AGPL-3.0-only -->

# Device check and the black theme

## Check this device

Settings > Privacy > Check this device gives you one screen that says how well
this phone and Wren are locked down. Each row has a status icon and one line
that says what it found:

| Row | Passes when |
| --- | --- |
| Security patch | The Android patch level is three months old or newer |
| Android version | Android 10 or later |
| Screen lock | The phone has a PIN, pattern or password |
| Passphrase lock | Wren asks for its own passphrase |
| Duress passphrase | One is set (see [DURESS.md](DURESS.md)) |
| Registration Lock | Your Signal PIN also guards re-registration |
| Notification content | Notifications show no name and no message |
| Screen security | Screenshots and app switcher previews are blocked |
| Incognito keyboard | Keyboards are asked not to learn what you type |
| Generate link previews | Off |
| Read receipts | Off |
| Typing indicators | Off |
| Block unknown | On |

The patch row reads `Build.VERSION.SECURITY_PATCH`, the date your phone maker
says it last shipped Android security fixes. Past three months it turns into a
warning, past six an alert, and either way it says the date and how many months
behind that is. If the phone doesn't report a date, or reports one I can't
parse, that's a warning too. I'd rather nag than pass a phone I know nothing
about.

The Android version row warns below Android 10. Google stopped shipping
security fixes for Android 9 and older in early 2022. Wren still installs on
Android 8.1 and 9 because some people have nothing newer, but it can't make up
for a system that no longer gets patched.

Tap a row to fix it. A tap only ever makes things stricter: screen security,
incognito keyboard, link previews, read receipts, typing indicators and block
unknown switch to the hardened setting in place when they aren't hardened yet.
Tapping one that is already hardened doesn't turn it off; it opens the
settings screen where that option lives (Privacy, Chats, or the network
settings for Orbot), so turning something off is always a deliberate change
there. The passphrase, duress, Registration Lock and notification rows open
the screen where you change them, and the phone rows open Android's own
settings. On a linked device, read receipts and typing indicators belong to
your primary device, so those rows don't respond to taps.

### Apply hardened defaults

The button at the bottom changes every privacy row that isn't hardened yet. It
asks first and the dialog lists exactly what it will change, out of these:

- Turn on screen security
- Turn on incognito keyboard
- Hide names and messages in notifications
- Turn off link previews
- Turn off read receipts
- Turn off typing indicators
- Turn on block unknown

It never touches your passphrase, your duress passphrase or Registration Lock.
Each of those needs a secret only you can pick, so the button can't set them
for you and won't try.

### The patch banner

If the patch level is more than six months old, the chat list shows a banner:
"This phone's security updates stopped N months ago". Learn more opens the
device check. Dismiss hides it for 30 days, then it comes back if nothing
changed. A dismissal with a timestamp in the future (the clock got moved back)
doesn't count, so a clock change can't silence it for good.

An old patch level doesn't always mean the maker gave up. Sometimes the update
is sitting there waiting for you to install it, so check Settings > System >
Software update before you shop for a new phone. If your phone really is out of
support, a custom OS like GrapheneOS or LineageOS may keep it patched.

## Black theme

Settings > Appearance > Theme now has a fourth choice, Black, next to System,
Light and Dark. It's the dark theme with the backgrounds pushed to true black,
so OLED screens switch those pixels off.

The window, the chat list and the conversation screen sit on `#000000`.
Raised surfaces like cards, menus, dialogs and bottom sheets step up through a
few very dark grays (`#0A0A0A` to `#2A2A2A`) so they still read as separate
layers. Text, accent and error colors are the same as in Dark, and if Dynamic
Colors is on, the accents still come from your wallpaper. Chat wallpapers and
chat colors don't change.

Black always uses night mode, the same as Dark does. It doesn't follow the
system light/dark switch. A backup saves it as Dark, since Signal's backup
format has no black value, so after a restore you pick Black again.
