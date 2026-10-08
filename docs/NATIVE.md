# Rebuilding the native libraries

Wren is stuck at Signal 8.20.5 for one reason: Signal 8.21 and later need
newer builds of two native libraries, libsignal and RingRTC, and the hardened
forks Molly publishes (`im.molly:libsignal-android`,
`im.molly:ringrtc-android` on Cloudsmith) stop at 0.97.3-1 and 2.69.7-1. This
page is the recipe for building newer ones. I have not run it end to end yet;
the toolchain is installed on my machine and the steps come from Molly's own
build setup. Treat every step as "verify, then trust".

## What the forks change

- libsignal: `infer_proxy_mode_for_config` returns `ProxyOnly`, so a
  configured proxy is never bypassed by a direct connection. Upstream
  libsignal races a direct connection when the proxy is slow or down; with
  packet captures that race starts within 2 ms when the proxy is down and
  about 500 ms into a slow proxied connect.
- RingRTC: a `PeerConnection.ProxyInfo` parameter on `CallManager.proceed`,
  `createGroupCall` and `createCallLinkCall`, threaded through to the Android
  side, so calls go through the proxy too. Molly also keeps a WebRTC fork
  (`mollyim/webrtc`); whether the RingRTC patch needs it is the first thing to
  check.

## Versions Signal needs

| Signal Android | libsignal | RingRTC |
|---|---|---|
| 8.20.5 (Wren today) | 0.97.3 | 2.69.7 |
| 8.21.6 | 0.99.1 | 2.70.0 |
| 8.25.2 | 0.100.0 | 2.71.0 |
| 8.29.4 | 0.102.2 | 2.72.0 |

Read them from `gradle/libs.versions.toml` at the Signal tag you are aiming
for: `git show v8.29.4:gradle/libs.versions.toml | grep -E 'libsignal|ringrtc'`.

## Toolchain

- Rust through rustup with the Android targets:
  `rustup target add aarch64-linux-android armv7-linux-androideabi x86_64-linux-android i686-linux-android`
- `cargo install cargo-ndk`
- Android NDK. Molly pins 28.0.13004108 (`gradle/libs.versions.toml`); install
  it with `sdkmanager "ndk;28.0.13004108"` so the output matches what Molly's
  Docker builder produces.
- depot_tools (`git clone https://chromium.googlesource.com/chromium/tools/depot_tools.git`)
  only if the RingRTC fork needs WebRTC built from source.
- JDK 21.

## libsignal

```sh
git clone https://github.com/mollyim/libsignal.git
cd libsignal
git remote add upstream https://github.com/signalapp/libsignal.git
git fetch upstream --tags
git diff upstream/v0.97.3 origin/libsignal-0.97.3 > /tmp/molly-libsignal.patch
git checkout -b wren-0.102.2 v0.102.2
git apply --3way /tmp/molly-libsignal.patch
```

Resolve what moved (the patch is small: `rust/net/src/connect_state.rs`
and `rust/bridge/shared/src/net.rs`), keep the `ProxyOnly` meaning exactly,
then build the Android artifacts the way Molly's workflow does (read
`.github/workflows` in `mollyim/libsignal`; the Java side lives under
`java/`, built with Gradle, and `java/build_jni.sh` drives cargo-ndk). Check
every `.so` with `readelf -lW` for `LOAD` alignment `0x4000`. Run the net
crate's tests: `cargo test -p libsignal-net`.

## RingRTC

```sh
git clone https://github.com/mollyim/ringrtc.git
cd ringrtc
git remote add upstream https://github.com/signalapp/ringrtc.git
git fetch upstream --tags
git diff upstream/v2.69.7 origin/ringrtc-2.69.7 > /tmp/molly-ringrtc.patch
```

If the diff touches only Rust, Kotlin and Java under `src/`, RingRTC's
prebuilt WebRTC is enough: `git checkout -b wren-2.72.0 v2.72.0`, apply the
patch, and build with RingRTC's `bin/build-aar --release` (read its
`BUILDING.md` for the exact flags). If Molly's `config/version.properties`
or its workflows point at `mollyim/webrtc`, WebRTC has to be built from
source: a gclient checkout of 20 to 40 GB and a few hours of compile with
`bin/fetch-android` and `bin/build-aar --webrtc-source` or the Makefile
targets. Plan the disk before starting.

## Wiring the result into Wren

Put the AAR, the JAR and their POMs in a Maven layout (for example
`app/libs/m2/im/molly/libsignal-android/0.102.2-wren.1/`), add a
`maven { url = uri("libs/m2") }` repository in `settings.gradle.kts`, bump the
versions in `gradle/libs.versions.toml`, and run
`./gradlew --write-verification-metadata sha256 help` so
`gradle/verification-metadata.xml` carries the new checksums. Then merge the
Signal tags (`docs/SIGNAL-MERGE-LOG.md` has the method) and build.

Record the upstream tag, the patch, the toolchain versions and the sha256 of
every artifact next to the files. Anyone verifying a Wren release needs
those to rebuild the same binary.
