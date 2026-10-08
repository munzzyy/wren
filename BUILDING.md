# Building and signing Wren

This is for people who compile things and can look after a signing key. If
that is not you, wait for a release.

## The short local build

You need JDK 21 and the Android SDK (platform 36, build tools 36.0.0). Then:

```sh
git clone https://github.com/munzzyy/wren.git
cd wren
export ANDROID_HOME=$HOME/Android/Sdk
./gradlew :app:assembleProdStoreRelease
```

The unsigned APKs land in `app/build/outputs/apk/prodStore/release/`, one per
CPU plus a universal one (see below). Sign the one you want before installing
(see Signing). The debug variant, `:app:assembleProdWebsiteDebug`, installs as
is and carries every ABI.

The Gradle defaults ask for a 12 GB heap. On a machine with 16 to 24 GB of
memory, put this in `~/.gradle/gradle.properties` or the build daemon gets
killed during dexing:

```
org.gradle.jvmargs=-Xmx7g -XX:+UseParallelGC -XX:+UnlockExperimentalVMOptions -XX:hashCode=3
kotlin.daemon.jvmargs=-Xmx5g
```

Unit tests for the Wren additions:

```sh
./gradlew :app:testProdWebsiteDebugUnitTest --tests 'io.github.munzzyy.wren.*'
```

## Flavors

- `prodStoreRelease` has no in-app updater. GitHub Releases and Obtainium get
  this one.
- `prodWebsiteRelease` checks the Wren F-Droid repository for updates.
- `stagingWebsiteRelease` talks to Signal's staging network, for testing.

`app/gradle.properties` holds the app title, backup file name and package id.
The environment variables `CI_APP_TITLE`, `CI_APP_FILENAME`,
`CI_PACKAGE_ID`, `CI_BUILD_VARIANTS` (a regex over the flavors, default
`prod`) and `CI_FORCE_INTERNAL_USER_FLAG` override them in CI and in the
Docker build. Change the package id if you want your own build to install
next to the official one.

Every build is free of Google push code by default. If you want Firebase
Cloud Messaging back, build with `-PwrenFcm=true`, set `wrenFcm=true` in
`app/gradle.properties`, or set `CI_FCM=true` in the environment Gradle runs
in. The Docker build does not pass `CI_FCM` through, so the release workflow
always builds without it. [docs/FOSS.md](docs/FOSS.md) explains what the flag
links in and why Wren's own releases never use it.

## Which APK to install

Release builds come as four files per flavor. They are the same app, signed
with the same key and carrying the same version code. Sizes are for the
`prodStore` build.

| File | For | Size |
|------|-----|------|
| `Wren-<version>-arm64-v8a.apk` | nearly every phone and tablet from the last decade, Pixels, GrapheneOS | 82 MiB |
| `Wren-<version>-armeabi-v7a.apk` | old 32-bit ARM devices | 73 MiB |
| `Wren-<version>-x86_64.apk` | Chromebooks, Android-x86, the emulator | 86 MiB |
| `Wren-<version>.apk` | all three in one file, if you do not know or want a single file for a repository | 124 MiB |

If you are unsure which CPU you have, a device info app will tell you. In
Obtainium, put `arm64-v8a` in the APK filter regex so it keeps picking the
small file.

The native libraries are stored uncompressed and aligned to 16 KB, so Android
maps them straight from the APK and does not keep a second copy on disk. That
makes the download bigger than a compressed APK would be and the installed app
smaller. The universal file pays the most for it.

`tools/apk-report.sh` checks what you built or downloaded:

```sh
tools/apk-report.sh app/build/outputs/apk/prodStore/release/*.apk
```

For each file it prints the size, min and target SDK, ABIs and libraries,
whether the baseline profile is inside, whether the zip and every 64-bit
library are 16 KB aligned, the `google` section and the signature state. It
exits non-zero if a check fails.

- The `google` lines look for Play Services, Firebase and FCM code, endpoints
  and manifest entries; [docs/FOSS.md](docs/FOSS.md) lists exactly what they
  check. A build made with `-PwrenFcm=true` fails them on purpose; pass
  `--allow-fcm` to have them reported instead.
- An unsigned APK is only a failure with `--require-signed`.
- `libargon2.so` and `libnative-utils.so` come from prebuilt Molly artifacts
  that are still 4 KB aligned, so the ELF check fails on them until those
  are rebuilt. `--known-4k=libargon2.so,libnative-utils.so` turns those two
  into warnings and keeps failing on any other library. The release workflow
  runs the report on the universal APKs with that flag, plus
  `--require-signed` when it signs, so everything else still blocks a
  release.

## A reproducible build in Docker

This is the same path the release workflow uses, so what you get here should
match a release byte for byte apart from the signature.

```sh
export VERSION=v1.0.0
git clone https://github.com/munzzyy/wren.git
cd wren/reproducible-builds
git checkout $VERSION
docker compose up --build
./name-outputs.sh $VERSION built
docker compose down
```

The APKs end up in `outputs/apk` and, renamed to the release names, in
`built/`. [reproducible-builds/README.md](reproducible-builds/README.md)
has the comparison step.

## Building your own signed Wren with GitHub Actions

Fork the repository. Public forks get Actions minutes for free. Under
Settings, Secrets and variables, Actions, you can set the `CI_*` variables
from the table above, and if you want the workflow to sign for you, three
secrets:

- `SECRET_KEYSTORE`: your keystore file, base64 encoded (`base64 my-key.jks`).
- `SECRET_KEYSTORE_ALIAS`: the key alias.
- `SECRET_KEYSTORE_PASSWORD`: the keystore password.

Push a tag that starts with `v`:

```sh
git tag v1.0.0
git push origin v1.0.0
```

The Release workflow builds in Docker (about 45 minutes), signs if the
secrets exist, and creates a draft release with the APKs and `SHA256SUMS`.
Publishing the draft starts the reproducible build check. To rebuild an
existing tag, run the workflow by hand and enter the tag. To stay current,
sync your fork and tag again.

Only do this if you trust GitHub with your key. If you do not, leave the
secrets out, download the unsigned APK and sign it at home.

## Signing

Make a key once and keep it somewhere safe. If you lose it, nobody can
update your build in place.

```sh
keytool -genkey -v -keystore my-key.jks -keyalg RSA -keysize 4096 -validity 10000 -alias my-alias
```

Sign with the SDK's `apksigner`:

```sh
apksigner sign --ks my-key.jks --out Wren-$VERSION.apk Wren-unsigned-$VERSION.apk
```

A self-signed build cannot update from the in-app updater, because the
signature differs from the official one. Build and install updates yourself,
or run your own F-Droid repository.

Do your builds on a machine you control. Running the reproducible build on
the same machine only shows the build is deterministic, not that the machine
is clean.
