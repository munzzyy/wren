<!-- Copyright 2026 Cole Munz -->
<!-- SPDX-License-Identifier: AGPL-3.0-only -->

# Audit guide

This is a map for someone reviewing Wren's security claims. It says where each
protection lives, how the secrets are derived and stored, what the wipe does
and in what order, how the panic handshake is checked, how exports are
written, and how to rebuild and compare. [THREAT-MODEL.md](THREAT-MODEL.md)
says what each protection is for. Read that first.

Everything here was read from the tree, not from memory. Paths are relative to
the repository root. If a line number or a name here does not match the code,
the document is wrong and I would like to be told
([SECURITY.md](../SECURITY.md)).

Wren is Molly v8.19.2-4 plus my changes, with Signal 8.20.5 merged in on top
([SIGNAL-MERGE-LOG.md](SIGNAL-MERGE-LOG.md)). Most of the code is Signal's or
Molly's and unchanged. Wren's own code is small. Section 10 shows how to see
exactly what I changed.

## 1. Where things are

Wren's own code is under `app/src/main/java/io/github/munzzyy/wren/`. The
table gives full paths so you can paste them.

| What | File | Function or class |
| --- | --- | --- |
| Passphrase prompt and unlock | `app/src/main/java/org/thoughtcrime/securesms/PassphrasePromptActivity.java` | `unlock`, `SetMasterSecretTask.doInBackground` |
| Setting or changing the passphrase | `app/src/main/java/org/thoughtcrime/securesms/ChangePassphraseDialogFragment.java` | `ChangeMasterSecretTask` |
| Master secret wrap and unwrap | `app/src/main/java/org/thoughtcrime/securesms/crypto/MasterSecretUtil.java` | `changeMasterSecretPassphrase`, `getMasterSecret` |
| Argon2id and the KeyStore HMAC | `app/src/main/java/org/thoughtcrime/securesms/crypto/PassphraseBasedKdf.java` | `findParameters`, `deriveKey` |
| KeyStore keys | `core/util/src/main/java/org/signal/core/util/crypto/KeyStoreHelper.java` | `createKeyStoreEntryHmac`, `seal`, `unseal` |
| Database and attachment secrets | `app/src/main/java/org/thoughtcrime/securesms/crypto/DatabaseSecretProvider.java` | `getOrCreate` |
| Encrypted preferences | `app/src/main/java/org/thoughtcrime/securesms/util/SecurePreferenceManager.java` | the allow-list in `createSecurePreferences` |
| Master secret in memory, lock, timeout | `app/src/main/java/org/thoughtcrime/securesms/service/KeyCachingService.java` | `handleClearKey`, `startTimeoutIfAppropriate` |
| What runs on lock | `app/src/main/java/org/thoughtcrime/securesms/ApplicationContext.java` | `onLock` |
| RAM wipe | `app/src/main/java/org/thoughtcrime/securesms/service/WipeMemoryService.java` | `doWipe` |
| Biometric screen lock | `app/src/main/java/org/thoughtcrime/securesms/ScreenLockController.kt` | `onAppBackgrounded`, `shouldLockScreenAtStart` |
| Duress check and failed-attempt hook | `app/src/main/java/io/github/munzzyy/wren/duress/DuressManager.kt` | `onWrongPassphrase`, `onUnlocked`, `isDuressPassphrase` |
| Duress verifier | `app/src/main/java/io/github/munzzyy/wren/duress/DuressVerifier.kt` | `createVerifier`, `matches` |
| Duress and panic settings storage | `app/src/main/java/io/github/munzzyy/wren/duress/DuressStore.kt` | the whole file |
| Unlock limit | `app/src/main/java/io/github/munzzyy/wren/duress/FailedAttemptPolicy.kt` | `onFailedAttempt` |
| The wipe | `app/src/main/java/io/github/munzzyy/wren/duress/AppWipe.kt` | `wipeNow`, `deleteKeyStoreEntries`, `deleteLocalFiles` |
| PanicKit intents | `app/src/main/java/io/github/munzzyy/wren/duress/PanicResponderActivity.kt` | `handleConnect`, `handleDisconnect`, `handleTrigger` |
| PanicKit decisions | `app/src/main/java/io/github/munzzyy/wren/duress/PanicDecision.kt` | `decide`, `connect`, `isConnectedCaller` |
| Molly's lock-only panic receiver | `app/src/main/java/org/thoughtcrime/securesms/service/PanicResponderListener.kt` | `onReceive` |
| Settings screens for the above | `app/src/main/java/org/thoughtcrime/securesms/components/settings/app/privacy/PrivacySettingsFragment.kt` | Data at rest and Panic button sections |
| Proxy and Orbot | `app/src/main/java/org/thoughtcrime/securesms/net/NetworkManager.java`, `app/src/main/java/org/thoughtcrime/securesms/net/Networking.kt` | `configureProxy`, `socketFactory`, `DUMMY_PROXY` |
| Orbot control | `lib/netcipher/src/main/java/info/guardianproject/netcipher/proxy/OrbotHelper.java` | `requestStart`, status callbacks |
| UnifiedPush hand-off | `app/src/main/java/im/molly/unifiedpush/MollySocketRepository.kt` | `createDevice` |
| Screen security | `app/src/main/java/org/thoughtcrime/securesms/components/TemporaryScreenshotSecurity.kt`, `app/src/main/java/org/thoughtcrime/securesms/BaseActivity.java` | `FLAG_SECURE`, `blockRecentAppsScreenshot` |
| Block unknown | `app/src/main/java/org/thoughtcrime/securesms/messages/MessageContentProcessor.kt` | `shouldBlockSender` |
| Device check | `app/src/main/java/io/github/munzzyy/wren/devicecheck/DeviceChecks.kt`, `app/src/main/java/io/github/munzzyy/wren/devicecheck/HardeningPlan.kt`, `app/src/main/java/io/github/munzzyy/wren/devicecheck/DeviceCheckRepository.kt` | `evaluate`, `changesFor`, `applyHardenedDefaults` |
| Patch banner | `app/src/main/java/io/github/munzzyy/wren/devicecheck/PatchBannerPolicy.kt`, `app/src/main/java/org/thoughtcrime/securesms/banner/banners/SecurityPatchBanner.kt` | `shouldShow` |
| Export | `app/src/main/java/io/github/munzzyy/wren/export/ChatExporter.kt`, `app/src/main/java/io/github/munzzyy/wren/export/ChatExportWriter.kt`, `app/src/main/java/io/github/munzzyy/wren/export/ExportFileNames.kt` | see section 6 |

## 2. How the master secret is derived and stored

This is Molly's design and I did not change it. Wren only added two public
accessors to `MasterSecretUtil` (`getKdfParameters`, `hasStrongBoxKeyStore`)
so the duress code can reuse the same settings.

When you set a passphrase, `changeMasterSecretPassphrase` in
`MasterSecretUtil.java` does this:

1. Benchmarks Argon2id on the phone (`PassphraseBasedKdf.findParameters`,
   `Argon2Benchmark.java`). The target is about 3000 ms. Parallelism is the
   core count minus one, iterations start at 4, and memory is capped at half
   of the memory the system reports as available.
2. Creates an HMAC-SHA256 key in the Android KeyStore under a fresh random
   alias (`KeyStoreHelper.createKeyStoreEntryHmac`, StrongBox when the phone
   has the feature). The key is `PURPOSE_SIGN` only and is not bound to the
   screen lock.
3. Picks a random 16-byte salt.
4. Derives `AES key = HMAC-SHA256(KeyStore key, Argon2id(PKCS12-style UTF-16
   bytes of the passphrase, salt))` in `PassphraseBasedKdf.deriveKey`.
5. Encrypts the master secret (a 32-byte AES key and a 32-byte MAC key) with
   AES-GCM under that key and a random 12-byte IV.
6. Writes `passphrase_salt`, `kdf_parameters`, `kdf_elapsed`, `encryption_iv`,
   `master_secret`, `keystore_alias` and flags into the `MasterKeys` shared
   preferences file, then deletes the old KeyStore alias.

`getMasterSecret` reverses that. A wrong passphrase makes the GCM tag fail,
which becomes `InvalidPassphraseException`. A KeyStore failure becomes
`UnrecoverableKeyException`, which the prompt does not treat as a wrong
passphrase.

When the lock is off, the same code runs with the literal passphrase
`unencrypted` and an all-zero key (`getUnencryptedKey`). That is how Molly
represents "no passphrase", and it is why the lock-off state protects nothing
at that layer.

The master secret then protects the secure preferences file named after the
application id. `SecurePreferenceManager.createSecurePreferences` encrypts
every key except an explicit allow-list (theme, language, passphrase lock
flags, biometric flag, a few UI values). `TextSecurePreferences` keeps the
KeyStore-sealed database secret and attachment secret in that file, so they
are encrypted by the master secret as well as sealed by the KeyStore key
`SignalSecret` (`DatabaseSecretProvider.java`, `KeyStoreHelper.seal`). The
database is SQLCipher keyed with the database secret.

## 3. How the duress verifier is derived and stored

`DuressManager.setDuressPassphrase` in `app/src/main/java/io/github/munzzyy/wren/duress/DuressManager.kt`:

1. Reads the real passphrase's stored `kdf_parameters`. Refuses if there are
   none, which is the case when the passphrase lock is off.
2. Creates a second KeyStore HMAC key under the alias `WrenDuress-<uuid>`.
3. Picks a random 16-byte salt.
4. Calls `DuressVerifier.createVerifier`, which runs the same
   `PassphraseBasedKdf.deriveKey` with the same Argon2id cost and the new
   HMAC key, then stores `SHA-256` of the 32 resulting bytes. The key bytes
   are zeroed after use.
5. Writes `duress_salt`, `duress_verifier`, `duress_kdf_params`,
   `duress_keystore_alias` and `duress_enabled` into the `wren-duress`
   preferences file with `commit()`, not `apply()`. A failed write throws.
6. Deletes the previous duress alias, or the new one if the write failed.

`DuressStore` uses plain `SharedPreferences`. The file is not encrypted with
the master secret, and it also holds `failed_attempt_limit`,
`failed_attempt_count`, `panic_action` and `panic_trigger_package`. It has to
be readable at the lock screen, before the master secret exists.

`DuressVerifier.matches` rejects an empty passphrase, a wrong-length salt or
a wrong-length verifier before doing any work, derives the verifier and
compares with `MessageDigest.isEqual` (constant time). The comparator is
injectable so a unit test can prove it is the one used.

`DuressManager.isDuressPassphrase` catches `Throwable` and returns false, so a
KeyStore error cannot turn into a wipe.

## 4. The unlock path and where the hooks are

In `PassphrasePromptActivity.unlock`:

1. If the biometric screen lock is pending (`ScreenLockController.lockScreenAtStart`),
   `BiometricDialogFragment.authenticate` runs first and the passphrase is
   not looked at until it succeeds. If no biometrics are enrolled any more,
   the biometric lock is switched off and the passphrase is checked.
2. `SetMasterSecretTask.doInBackground` calls
   `MasterSecretUtil.getMasterSecret`.
   - Success: `DuressManager.onUnlocked` resets the failed-attempt counter.
   - `InvalidPassphraseException`: `DuressManager.onWrongPassphrase`.
   - `UnrecoverableKeyException`: logged, not counted, nothing else happens.
3. `onWrongPassphrase` first runs `isDuressPassphrase`. A match calls
   `AppWipe.wipeNow` and the method does not return. Otherwise it runs
   `FailedAttemptPolicy.onFailedAttempt`, saves the new count, and wipes if
   the limit is reached. A `RuntimeException` while saving is caught and
   logged, which leaves that guess uncounted.

The few lines Wren added to Molly's prompt and change-passphrase dialog are
the whole integration. `ChangePassphraseDialogFragment` calls
`DuressManager.isDuressPassphrase` on the new passphrase so the real one can
never be changed into the duress one.

## 5. How the wipe works

`AppWipe.wipeNow` in `app/src/main/java/io/github/munzzyy/wren/duress/AppWipe.kt` runs these steps in order, each
wrapped in `runCatching` so one failure does not stop the next:

1. Logs one warning line.
2. `deleteKeyStoreEntries`: opens the `AndroidKeyStore`, lists every alias the
   app can see and deletes each one. This includes the master secret HMAC
   key, the duress HMAC key and `SignalSecret`, so the sealed database
   secret can no longer be unsealed even if the files survive.
3. Asks Android to clear the app's data with
   `ActivityManager.clearApplicationUserData`. If the call returns true,
   Wren sleeps up to 15 seconds (`CLEAR_GRACE_MILLIS`) expecting the system
   to kill the process.
4. `deleteLocalFiles` (reached if the process is still alive): deletes every
   database from `databaseList()`, then the contents of `shared_prefs` in the
   credential-protected and device-protected data directories, `filesDir`,
   `cacheDir`, `noBackupFilesDir`, `codeCacheDir`, the external files and
   cache directories, and what is left of the data directories, skipping a
   child named `lib`. It deletes; it does not overwrite.
5. `Process.killProcess` and `exitProcess(0)`.

`wipeInBackground`, used by the panic path, runs the same thing on a thread
named `wren-wipe`.

What it does not do is also in the code: it does not call the server, does
not unlink the device, does not touch shared storage or anything the user
saved outside the app, and does not run `WipeMemoryService`.

## 6. How the PanicKit handshake is checked

Manifest entries (`app/src/main/AndroidManifest.xml`): `PanicResponderActivity`
is exported, uses `Theme.NoDisplay`, `noHistory` and `excludeFromRecents`, and
filters on the actions `info.guardianproject.panic.action.TRIGGER`, `CONNECT`
and `DISCONNECT`. Molly's `PanicResponderListener` receiver is exported for
`TRIGGER` too.

Caller identity comes from `Activity.getCallingPackage()` in
`PanicResponderActivity`. Android returns it only when the sender used
`startActivityForResult`. A plain `startActivity`, `adb shell am start` or a
broadcast gives null.

The decisions are in `app/src/main/java/io/github/munzzyy/wren/duress/PanicDecision.kt`, a pure object with unit
tests:

- `connect(connected, caller, ownPackage)`: refuse if the caller is null,
  empty or Wren itself; accept if nothing is connected; return "already
  connected" for the same package; refuse any other package.
- `decide(action, connected, caller, passphraseLockEnabled)`: wipe only if the
  action is Erase and the caller equals the connected package; otherwise lock
  if the passphrase lock is on; otherwise do nothing.
- `isConnectedCaller`: non-empty connected package equal to the caller.

`DuressStore.connectPanicTrigger` stores the package and resets the action to
Lock. `disconnectPanicTrigger` clears the package and resets the action to
Lock. The `panicAction` setter forces Lock when no trigger is connected, and
an unknown stored value reads back as Lock. In the settings screen the
action picker is disabled while no trigger is connected.

Lock is `startService` on `KeyCachingService` with `CLEAR_KEY_ACTION`. That
service ignores the action while it is already locked, and refuses to lock
during a migration or a device transfer.

The broadcast receiver locks only when the passphrase lock is on and never
erases.

## 7. How exports are written and escaped

Everything is under `app/src/main/java/io/github/munzzyy/wren/export/`.

- `ChatExporter.kt` creates a new folder under the Storage Access Framework
  tree the user picked (`DocumentFile.createDirectory`), writes the chat file
  and a `media` folder, and deletes the whole folder if the export fails or
  is canceled. Output is UTF-8.
- Folder names come from `ExportFileNames.sanitizeChatName`: control, format,
  surrogate, private-use and unassigned code points are dropped, so are
  `/ \ : * ? " < > |`, runs of whitespace collapse, leading dots and spaces
  are trimmed, the name is capped at 64 code points, and an empty result
  becomes `chat`. `uniqueName` adds ` (2)` for duplicates.
- Media files are named `<message id>-<index>.<ext>`. The extension must
  match `^[a-z0-9]{1,8}$`, taken from the original name or the MIME type,
  else `bin`. Attacker-controlled attachment file names never reach the
  file system.
- HTML (`HtmlChatExportWriter`): every dynamic value goes through
  `escape()`, which replaces `& < > " '`. The page has a
  `Content-Security-Policy` meta tag with `script-src 'none'`, `object-src 'none'`,
  `base-uri 'none'` and `form-action 'none'`, and a no-referrer meta tag.
  Link-preview links are written as anchors only when the address starts with
  `http://` or `https://`. Media `src` and `href` values are HTML-escaped and
  built from the generated file name; they are not percent-encoded, which is
  safe for the generated names but would not be for a name a storage provider
  rewrites.
- Text (`TextChatExportWriter`): multi-line bodies are indented so a message
  cannot start a fake entry; single-line fields have line breaks replaced.
- JSON (`JsonChatExportWriter`): strings go through kotlinx's
  `JsonPrimitive(...).toString()`.
- The "Export all chats" row is behind the same biometric or device-credential
  gate as Signal's other plaintext export
  (`ChatsSettingsFragment.kt`, `plaintextBiometricsAuthentication`), and the
  Cancel button ignores taps while Wren is locked
  (`ChatExportCancelReceiver` extends `ExportedBroadcastReceiver`).

The files are not encrypted. That is deliberate and written in
[EXPORT.md](EXPORT.md).

## 8. Building reproducibly and comparing

[reproducible-builds/README.md](../reproducible-builds/README.md) has the full
steps. The short form:

```sh
git checkout <tag>
cd reproducible-builds
docker compose up --build
./name-outputs.sh <tag> built
python3 apkdiff/apkdiff.py <official-apk> built/Wren-unsigned-<tag>.apk
```

The base image, the Android command line tools zip and their checksums, the
NDK version and the build tools version are pinned in the root `Dockerfile`.
Gradle dependency checksums are in `gradle/verification-metadata.xml`.
`.github/workflows/reprocheck.yml` runs the same comparison when a release is
published. There is no Wren release yet, so no comparison against a
published Wren APK exists.

The two prebuilt native libraries Wren takes from Molly's artifacts
(libsignal and RingRTC forks) are downloaded, not built here. Their
checksums are in the verification file (commit `e9ef5e1d3a52`). Rebuilding
them is the hard part and is described in the merge log.

## 9. Tests and `tools/apk-report.sh`

Run the unit tests for Wren's code:

```sh
./gradlew :app:testProdWebsiteDebugUnitTest --tests 'io.github.munzzyy.wren.*'
```

There are 86. The ones that carry the security claims:

- `app/src/test/java/io/github/munzzyy/wren/duress/PanicDecisionTest.kt`: only
  the connected package can erase, other callers lock at most, a second
  trigger cannot take over, a null caller never connects.
- `app/src/test/java/io/github/munzzyy/wren/duress/DuressVerifierTest.kt`:
  salt and passphrase both matter, empty and malformed input never matches,
  the constant-time comparator is the one called, key bytes are zeroed.
- `app/src/test/java/io/github/munzzyy/wren/duress/FailedAttemptPolicyTest.kt`:
  wipes exactly at the limit, off never wipes, bad stored values fall back.
- `app/src/test/java/io/github/munzzyy/wren/export/ChatExportWriterTest.kt`:
  hostile markup in text and attributes, every HTML special character,
  JSON control characters, multi-line text, the CSP tag, and golden output
  for all three formats.
- `app/src/test/java/io/github/munzzyy/wren/export/ExportFileNamesTest.kt`:
  path separators, control characters, long and empty names, extensions.
- `app/src/test/java/io/github/munzzyy/wren/devicecheck/HardeningPlanTest.kt`:
  the hardened-defaults plan never touches the passphrase, duress or
  Registration Lock.

What the tests do not cover: `AppWipe`, `DuressManager`, `DuressStore`,
`PanicResponderActivity` and the settings screens, because they need a real
Android runtime. There are no instrumentation tests for Wren's code. Those
paths are covered by reading, and by the test list in
[THREAT-MODEL.md](THREAT-MODEL.md).

`tools/apk-report.sh` takes one or more APKs and prints, for each, the
package id and version, size, min and target SDK, ABIs, the libraries in each
ABI, how many are stored versus compressed, then lines starting `ok` or
`FAIL`:

- baseline profile present (not required for debuggable builds);
- `zipalign -P 16 -v 4` passes;
- every 64-bit library has LOAD segments aligned to at least 16 KB (32-bit
  libraries are skipped);
- every ABI directory has `libsignal_jni.so`;
- the signature verifies, and shows the first 16 characters of the signing
  certificate's SHA-256. An unsigned APK prints `unsigned` and only fails with
  `--require-signed`.

It exits 1 if any check failed. I expect the ELF alignment check to flag
`libargon2.so` and `libnative-utils.so`, which is the known 4 KB alignment gap
in the README. Any other failure is news.

## 10. What Wren changed in Molly's files

Molly's tag is `v8.19.2-4`. If you do not have it, `git fetch molly --tags`
after adding the remote (`tools/merge-molly.sh` adds it).

`git diff v8.19.2-4...HEAD` also contains everything the Signal 8.20.5 merge
brought in, which is hundreds of files. To see only my changes, diff against
the commit just before that merge:

```sh
merge=$(git log --merges --format=%H --grep='^Merge Signal 8.20.5' -1)
git diff v8.19.2-4 "$merge^1" -- <files>
```

For the files that matter, that diff is small:

| File | Change |
| --- | --- |
| `app/src/main/java/org/thoughtcrime/securesms/PassphrasePromptActivity.java` | Calls `DuressManager.onUnlocked` after a good passphrase and `onWrongPassphrase` after `InvalidPassphraseException`. `UnrecoverableKeyException` is split from it so it is not counted. 8 lines. |
| `app/src/main/java/org/thoughtcrime/securesms/ChangePassphraseDialogFragment.java` | Rejects a new passphrase equal to the duress one. 12 lines. |
| `app/src/main/java/org/thoughtcrime/securesms/crypto/MasterSecretUtil.java` | Adds `getKdfParameters` and `hasStrongBoxKeyStore`. No change to the derivation. 8 lines. |
| `app/src/main/AndroidManifest.xml` | Adds three components: the export cancel receiver, `DeviceCheckActivity` and `PanicResponderActivity`. Removes nothing. |
| `app/src/main/java/org/thoughtcrime/securesms/ApplicationContext.java` | Registers the black theme's activity callbacks. |
| `app/src/main/java/org/thoughtcrime/securesms/util/DynamicTheme.java`, `app/src/main/java/org/thoughtcrime/securesms/keyvalue/SettingsValues.java` | Black theme. |
| `app/src/main/java/org/thoughtcrime/securesms/jobs/JobManagerFactories.java` | Registers the two export jobs. |
| `app/src/main/java/org/thoughtcrime/securesms/components/settings/app/privacy/` | The Privacy screen gets the check-this-device row and the Data at rest and Panic button additions. |
| `app/src/main/java/org/thoughtcrime/securesms/components/settings/app/chats/ChatsSettingsFragment.kt`, `app/src/main/java/org/thoughtcrime/securesms/components/settings/conversation/ConversationSettingsFragment.kt` | Export entry points. |
| `app/src/main/java/org/thoughtcrime/securesms/banner/banners/SecurityPatchBanner.kt` | New file, the stale-patch banner, shown from `ConversationListFragment.java`. |
| `app/build.gradle.kts`, `app/gradle.properties` | App name and package id, per-ABI release splits, native libraries stored uncompressed and 16 KB aligned, the updater URL. |

Two commits come after the merge. `b612245f20bc` adapts four files to Signal
8.20.5: the UnifiedPush repository, the export notifications, Signal's
registration storage controller and an import in `WipeMemoryService.java`.
`465886debee3` only wires a test source folder. Neither changes the lock, key
or wipe logic.

## 11. What Wren has deliberately not changed

These are Signal's and Molly's, and the diffs against `v8.19.2-4` are empty
for them. Findings in them are reported upstream, see
[SECURITY.md](../SECURITY.md).

- The key derivation and storage: `PassphraseBasedKdf.java`,
  `Argon2Benchmark.java`, `MasterCipher.java`, `EncryptedPreferences.java`,
  `SecurePreferenceManager.java`, `DatabaseSecretProvider.java`,
  `KeyStoreHelper.java`.
- The lock and memory wipe: `KeyCachingService.java`, `ScreenLockController.kt`,
  `BiometricDialogFragment.java` (the only edit to `WipeMemoryService.java` is
  an import path that moved in Signal 8.20).
- The proxy stack: `Networking.kt`, `NetworkManager.java`,
  `NetworkPreferenceFragment.java`, `lib/netcipher/`.
- UnifiedPush: `app/src/main/java/im/molly/unifiedpush/` (one call made
  blocking to fit a Signal API change).
- Molly's panic receiver `PanicResponderListener.kt`, which still only locks.
- `android:allowBackup="true"` and `SignalBackupAgent.kt`, which backs up
  Signal's SVR auth tokens through the Android Backup Service. I left it
  alone and flagged it in the threat model.
- The libsignal and RingRTC forks (`im.molly:libsignal-android:0.97.3-1`,
  `im.molly:ringrtc-android:2.69.7-1`), and Signal's protocol and messaging
  code apart from what the 8.20.5 merge brought.
- Signal's alternate launcher icon aliases in the manifest. Only the icon
  images were redrawn.

I also have not added any dependency. Wren's changes build on what Molly and
Signal already pull in, and dependency verification stays on.
