# Signal merge log

Wren came from Molly v8.19.2-4, which is Signal 8.19.2. Molly had not moved past that, so I merge
Signal's release tags into Wren myself, one minor version at a time, using the last patch tag of
each minor. This file lists every conflict and what I did with it.

How each merge is done:

- `git merge --no-ff <tag>` with the merge drivers from `.gitattributes` set up
  (`merge.theirs.driver 'cp -- %B %A'`, `merge.ours.driver true`).
- Translations (`values-*/strings.xml`) come from Signal through the theirs driver. Then
  `./gradlew updateTranslationsAll` puts Molly's name back into the strings Molly marks with
  `mollyify="true"`, and `python3 tools/rebrand.py` turns Molly into Wren. Molly's own translations
  live in `strings2.xml`, which Signal does not have, so they are untouched.
- Directories Molly removed (demo apps, benchmarks, screenshot tests, Signal's CI workflows, and
  the rest) stay removed. New Signal files that land inside them are dropped too.
- New Signal drawables get Molly's colors through `./gradlew updateColors`. I keep Wren's own
  files and the "Signal" alternate launcher icon out of that pass.
- Molly's forks of libsignal and RingRTC stay. Signal's version number is followed with Molly's
  `-1` build of the same version.

## v8.20.5

Signal 8.19.2 to 8.20.5, 102 commits. Molly has published `im.molly:libsignal-android:0.97.3-1`
and `im.molly:ringrtc-android:2.69.7-1`, which are the versions 8.20.5 asks for.

Removed directories, dropped again: 186 screenshot reference images and 2 screenshot tests under
`feature/registration/src/screenshotTest*`, 14 files under `demo/`, 3 under
`app/src/benchmarkShared`, and Signal's `android.yml`, `diffuse.yml` and `stale.yml` workflows.

Conflicts:

| File | Decision |
| --- | --- |
| `app/build.gradle.kts` | Took Signal's version (1724, 8.20.5, hotfix 1), kept Molly's removal of the static IPs block, ktlint and screenshot tests. `mollyRevision` back to 1. |
| `gradle/libs.versions.toml` | libsignal `0.97.3-1` and RingRTC `2.69.7-1` from Molly's repositories; Molly's extra libraries kept. |
| `ApplicationContext.java` | Took Signal's import cleanup; it also dropped a duplicate import. |
| `AccountDataArchiveProcessor.kt` | Signal's new backup tier parsing and the link-and-sync check on storage optimization, with Molly's rule that only the primary device takes the backup tier. |
| `AppSettingsFragment.kt` | Kept Molly showing Linked devices on every device. Backups row: Molly's version without the registration gate, with Signal's change that only the primary device copies the subscriber id on long press. Molly's Network row kept. |
| `BackupsSettingsFragment.kt` | Molly's `BackupState.None(featureSupported)` with Signal's new linked-device row. A linked device now sees Signal's "backups are off on linked devices" row; a primary device without support still sees nothing. |
| `ConversationUpdateItem.java`, `AttachmentKeyboardFragment.kt` | Molly removed payments; Signal only added a primary-device check to the payment buttons. Kept Molly. |
| `NetworkDependenciesModule.kt` | Signal moved the plain OkHttp client into `provideOkHttpClient()`. Took that, and moved Molly's SOCKS socket factory, proxy selector and proxy-aware DNS into `ApplicationDependencyProvider.provideOkHttpClient()` so the client still goes through the proxy. Dropped the `Payments` import Molly does not have. |
| `BackupFileIOError.java`, both device transfer fragments, `LocalArchiveJob.kt`, `LocalBackupJob.java`, `LocalBackupJobApi29.java`, `LocalPlaintextArchiveJob.kt`, `AppRegistrationNetworkController.kt` | Signal moved `ic_signal_backup` into core/ui. Molly uses its own `ic_notification_backup`; kept Molly and deleted the moved PNGs. |
| `BackupProgressService.kt` | Took Signal's wake lock; kept Molly's `@CheckResult` and its `ic_notification` icon. |
| `ActiveCallManager.kt` | `SafeForegroundService` moved to core/util (Molly's change to it came along with the move). Kept Molly's `ExportedBroadcastReceiver` import. |
| `Environment.kt` | Signal turned on the new registration flow and link-and-sync. Molly has both off until tested; kept Molly. |
| `core/ui/.../SignalTheme.kt` | Signal's new dark `colorOnCustomVariant`, Molly's dark surface colors. |
| `feature/registration/.../RegistrationRepository.kt` | Took Signal's new import, kept Molly's removal of the Google SMS retriever. |
| `reproducible-builds/apkdiff/apkdiff.py` | Molly keeps its own version; kept it whole. |

No database migrations in this range. Every Wren change from `main` still applies in reverse
against the merged tree.

Fixes after the merge, in their own commits:

- Signal moved `ForegroundServiceUtil` and `UnableToStartException` into core/util. Wren's export
  notifications and Molly's `WipeMemoryService` import them from there now.
- `LinkDeviceRepository.removeDevice` became a suspend function. Molly's MollySocket code calls it
  from the UnifiedPush job thread, so it blocks on it there.
- Signal's new `AppRegistrationStorageController` called `resetNetwork()`; Molly's takes a
  `restartMessageObserver` argument, and `true` does what Signal's two calls did.
- Molly declares `src/testShared` only as a Kotlin source dir, so javac never compiled the one Java
  file in it. Signal's new `SignalStoreRule` needs it; the test and androidTest source sets list it
  as a Java dir too.
- Signal's new `AppRegistrationStorageControllerTest` writes through `TextSecurePreferences`, which
  Molly encrypts with the master secret. The test now points those preferences at a plain in-memory
  file.

## Where this stops: v8.21.x and later

Signal 8.21.6 needs libsignal 0.99.1 and RingRTC 2.70.0, and every later minor needs newer ones
(8.29.4 asks for libsignal 0.102.2 and RingRTC 2.72.0). Molly ships its own builds of both, and as
of 2026-10-08 its newest published ones are `im.molly:libsignal-android:0.97.3-1` and
`im.molly:ringrtc-android:2.69.7-1`. Nothing newer is on Molly's Cloudsmith repositories.

Molly's forks are not cosmetic:

- libsignal: `infer_proxy_mode_for_config` always returns `ProxyOnly`, so when a proxy is set the
  library never falls back to a direct connection. Upstream falls back for proxies it did not set
  itself.
- RingRTC: `CallManager.proceed`, `createGroupCall` and `createCallLinkCall` take a
  `PeerConnection.ProxyInfo`, with matching Rust and WebRTC changes, so calls go through the proxy.
  Molly's call code passes it, so upstream RingRTC does not even compile against Wren.

So there were three ways past 8.20.5, and I took none of them:

1. Switch to Signal's `org.signal` builds. That drops the proxy-only rule and the call proxy, which
   would quietly weaken what Molly promises Tor and proxy users.
2. Merge 8.21+ while staying on libsignal 0.97.3-1 and RingRTC 2.69.7-1. Signal's libsignal bumps
   in 8.21 change no app code, but the RingRTC 2.70.0 bump does, and running newer Signal code on
   native libraries it was never tested with is a guess, not a merge.
3. Build Molly's forks myself. libsignal needs rustup with the Android targets (I have not set that up
   yet; only the system cargo is here) or Molly's Docker builder; RingRTC needs depot_tools and a full WebRTC
   checkout, which this machine does not have. Pulling and running those toolchains is new code
   execution and needs its own go-ahead.

The next merge can start the day Molly publishes `libsignal-android:0.99.1-1` and
`ringrtc-android:2.70.0-1` (or newer), or once one of the three options above is chosen.

