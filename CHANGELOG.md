# Changelog

## Unreleased

Forked from Molly v8.19.2-4 (Signal 8.19.2).

- Duress passphrase: entering it at the lock screen erases all app data.
- Wipe after 5, 10 or 20 failed unlock attempts.
- PanicKit responder with a lock or wipe action and a connected trigger app.
- Export a chat to HTML, plain text or JSON, with media.
- Rebranded as Wren: package id io.github.munzzyy.wren, new icon.
- Pure black theme for OLED screens.
- Check this device: security patch age, screen lock and privacy settings in one screen, with one-tap hardened defaults and a chat list warning when updates are six months stale.
- Export all chats into one folder, and a Cancel button on every export notification.
- Release builds ship one APK per CPU type plus a universal one, with native libraries stored uncompressed and 16 KB aligned where the upstream library allows; tools/apk-report.sh checks a built APK.
