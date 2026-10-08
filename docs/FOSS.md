<!-- Copyright 2026 Cole Munz -->
<!-- SPDX-License-Identifier: AGPL-3.0-only -->

# Free of Google services code

Wren's default build has no Google Play Services client and no Firebase. This
page says what is inside, what is not, how messages reach you without Google,
and how to check a build yourself.

## What changed

Molly merged its GMS and FOSS flavors into one build in December 2025. That one
build still shipped the Firebase Cloud Messaging client (`firebase-messaging`,
`firebase-installations` and the transport code under `com.google.android.datatransport`),
with Molly's Firebase project id and API key in the resources. On a phone with
Play Services it registered for FCM on its own and talked to
`firebaseinstallations.googleapis.com`. Wren 8.19.2-4 and everything built from
this repository before the change below did the same, and the README's claim
that it was built without Google code was wrong for them.

Now `firebase-messaging` is only linked when you ask for it with
`-PwrenFcm=true`. Without it:

- The dex files hold no `com.google.firebase`, `com.google.android.datatransport`
  or Play Services client classes.
- The manifest has no Firebase service, content provider or c2dm permission.
- The resources have no Firebase project id, sender id or API key.
- `FcmUtil` is a stub that never returns a token, so registration goes ahead
  without one and the account is a WebSocket or UnifiedPush account.
- FCM is not offered in Settings > Notifications. An account that was set to
  FCM is switched to WebSocket at the next start, and the FCM token is cleared
  from Signal's server.
- Registration no longer warns that Play Services are missing, because nobody
  is expected to have them.

## What is still in the APK

These `com.google` packages remain and are not services:

| Package | What it is |
| --- | --- |
| `com.google.common`, `com.google.thirdparty` | Guava |
| `com.google.crypto.tink` | Tink |
| `com.google.zxing` | ZXing, QR codes |
| `com.google.i18n.phonenumbers` | libphonenumber |
| `com.google.android.material` | Material Components |
| `com.google.android.flexbox` | Flexbox layout |
| `com.google.accompanist` | Compose helpers |
| `com.google.android.gms.common`, `.tasks`, `.stats`, `.security` | Small stand-ins from `core-gms/`, built from source here, that let Signal's code compile without the real library. They do not talk to Play Services except to ask whether it is installed. |
| `com.google.android.gms.maps` | A stand-in for the Maps API that draws OpenStreetMap tiles. No Google Maps SDK is linked. |

`GooglePlayServicesUtil.isGooglePlayServicesAvailable` only asks the package
manager whether `com.google.android.gms` is installed and signed by Google. It
sends nothing.

Hosts that appear in the strings of a default build, all in Wren's own code:

| Host | Used for |
| --- | --- |
| `www.google.com` and country variants, `android.clients.google.com`, `clients3.google.com`, `clients4.google.com`, `googlemail.com` | Domain fronting for censorship circumvention, in `SignalServiceNetworkAccess`. It is used when circumvention is switched on, or by default when your number's country code is on Signal's list (Egypt, the UAE, Oman, Qatar, Uzbekistan, Venezuela, Pakistan; Iran and Cuba are on it too but use other fronts). Turn it off under Settings > Privacy > Advanced > Censorship circumvention. |
| `maps.google.com` | A Google Maps link that `SignalPlace` writes into the text of a location message next to the OpenStreetMap one. The app never fetches it; whoever receives the message can tap it. |
| `play.google.com` | A link `PlayStoreUtil` opens in the browser. |
| `cloud.google.com`, `developers.google.com`, `issuetracker.google.com` | Text in error messages from Tink, CameraX and DataStore. |

Nothing refers to `googleapis.com` other than the `type.googleapis.com/...`
labels inside protobuf messages, which are type names and not addresses.

## How notifications reach you

Pick one under Settings > Notifications > Delivery service.

- WebSocket is the default. Wren keeps its own connection to Signal's server
  open and no other service is involved. It costs some battery, and Android
  will stop the connection to save power unless you turn off battery
  optimization for Wren, which the app offers to help with.
- UnifiedPush wakes the app through a distributor app you choose, such as
  ntfy. Signal's server cannot reach a distributor, so something has to watch
  your account and send the wake-up: MollySocket, a linked device that you run
  yourself or get from someone you trust. This is Molly's UnifiedPush and
  MollySocket code, unchanged. It is not available on a linked device.

There is no third option in the default build. A Pixel with Play Services gets
exactly the same delivery as a phone without it.

## Building with FCM

If you want Google's push anyway, for example on a phone whose battery cannot
handle the WebSocket:

```sh
./gradlew :app:assembleProdStoreRelease -PwrenFcm=true
```

or set `wrenFcm=true` in `app/gradle.properties`, or `CI_FCM=true` in the CI
environment. That links `firebase-messaging` and puts the sources under
`app/src/fcm/` in place of the stubs in `app/src/foss/`.

Two things to know before you do:

- The Firebase values in `app/src/fcm/res/values/firebase_messaging.xml` are
  Molly's. Wren has no Firebase project of its own, and an API key is normally
  restricted to the package name and signing certificate it was made for, so
  push registration for a Wren build is likely refused. I haven't tested it.
  If you want FCM to work, use your own project's values.
- The result is not free of Google code. `tools/apk-report.sh` fails on it
  unless you pass `--allow-fcm`, and even then it prints every finding.

Wren's own releases are built without this flag. If you ever see one that was,
it is a bug and I want to hear about it.

## Check a build

```sh
tools/apk-report.sh app/build/outputs/apk/prodStore/release/*.apk
```

The `google` lines of the report check three things:

1. Every class descriptor in every dex file. Firebase, Firebase transport, ML Kit,
   Play Core, anything under `com.google.android.gms` that is not one of the stand-ins
   above, and any `com.google` package that is not on the list of known open-source
   libraries fail the check. A new package has to be looked at and added by hand.
2. Every URL in the dex string tables. Firebase, Crashlytics, Analytics,
   `gstatic.com` and `googleapis.com` hosts fail. The fronting hosts above are
   listed and pass.
3. The manifest and resource table. Any Firebase or c2dm entry, and any
   Firebase project id, sender id or API key, fails.

It runs on the signed or unsigned file, release or debug, and needs python3 on
the path. Class matching is by name, so a library that repackages Play Services
under another name would not be caught. The dex check would see its endpoints,
and the manifest check would see its services, which is why there are three.

To read the dex yourself:

```sh
$ANDROID_HOME/build-tools/36.0.0/dexdump -f app-prod-store-arm64-v8a-release.apk \
  | grep -cE 'Lcom/google/(firebase|android/datatransport|android/gms/cloudmessaging)'
```

`0` is what you want. Before this change the same count on a Wren build was
in the hundreds.
