# Changelog

## 8.20.5-1 (2026-10-08)

Forked from Molly v8.19.2-4 (Signal 8.19.2), then merged up to Signal 8.20.5 (see docs/SIGNAL-MERGE-LOG.md).

- Duress passphrase: entering it at the lock screen erases all app data.
- Wipe after 5, 10 or 20 failed unlock attempts.
- PanicKit responder with a lock or wipe action and a connected trigger app.
- Export a chat to HTML, plain text or JSON, with media.
- Rebranded as Wren: package id io.github.munzzyy.wren, new icon.
- Pure black theme for OLED screens.
- Check this device: security patch age, screen lock and privacy settings in one screen, with one-tap hardened defaults and a chat list warning when updates are six months stale.
- Export all chats into one folder, and a Cancel button on every export notification.
- Release builds ship one APK per CPU type plus a universal one, with native libraries stored uncompressed and 16 KB aligned where the upstream library allows; tools/apk-report.sh checks a built APK.
- PanicKit: a trigger app has to be confirmed before it is connected; the trigger's package is shown.
- The passphrase is asked again before exports, turning the lock off, duress and panic changes.
- Opt-in erase after 3, 7, 14 or 30 days without an unlock; opt-in lock when a USB data connection starts.
- Encrypted export to one .wrenx file (scrypt, AES-256-GCM) with tools/wren-export-decrypt.py.
- Route through Orbot from the device check.
- Google-free by default: Firebase is linked only with -PwrenFcm=true; tools/apk-report.sh checks an APK for Play Services, Firebase and FCM code.
- Threat model, audit guide, panic guide, translating guide and a disclosure policy.
