# Wren F-Droid repository

**Status: not live yet.** Nothing is hosted at `https://munzzyy.dev/wren/fdroid/` today. The `prodWebsite` build already points its in-app updater at `https://munzzyy.dev/wren/fdroid/repo` (`FDROID_UPDATE_URL`), so that URL has to exist before a `prodWebsite` build can update itself. Until then, GitHub Releases and Obtainium use the `prodStore` build, which has no updater.

## Plan

I run the repo with [fdroidserver](https://gitlab.com/fdroid/fdroidserver) on my own build machine and publish the generated `repo/` directory to static hosting.

1. Install fdroidserver on the build machine.
2. Create a working directory and run `fdroid init`. It writes `config.yml` and generates the repo signing keystore. Set `repo_url` to `https://munzzyy.dev/wren/fdroid/repo`, `repo_name` to `Wren`, and keep `keystore.jks` and the passwords on this machine only.
3. Copy each signed release APK (`Wren-website-<version>.apk`) into `repo/`. The repo only ever carries release builds from the tag, checked against `SHA256SUMS`.
4. Run `fdroid update --create-metadata` to generate the index and metadata stubs, then fill in the name, summary, description and icon under `metadata/`.
5. Run `fdroid update` again so the index is regenerated and signed with the new metadata, then check it with `fdroid lint`.
6. Upload `repo/` to `https://munzzyy.dev/wren/fdroid/repo/` with the static host's normal deploy path. Only the generated `repo/` directory is uploaded, never `config.yml` or the keystore.

The repo signing key is separate from the APK signing key. Losing or rotating it breaks every client that pinned the fingerprint, so it gets backed up offline before the first publish.

## Add-repo URL

Once the repo exists, users add it with the fingerprint of the repo signing certificate so the client can verify the index:

```
https://munzzyy.dev/wren/fdroid/repo?fingerprint=<SHA-256 fingerprint>
```

The fingerprint is the SHA-256 of the repo signing certificate, uppercase hex with no colons. Get it from the keystore:

```sh
keytool -list -v -keystore keystore.jks -alias repokey | grep 'SHA256:'
```

and strip the colons. Clients that handle the `fdroidrepos://` scheme take the same address as `fdroidrepos://munzzyy.dev/wren/fdroid/repo?fingerprint=<SHA-256 fingerprint>`, which is what a QR code should encode.

The fingerprint is published in the README only after the repo is live and the first index has been checked from a clean F-Droid client.
