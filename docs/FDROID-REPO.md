# Wren F-Droid repository

**Status: live since 2026-10-08** at `https://munzzyy.dev/wren/fdroid/repo`.

Add it in the F-Droid client with the fingerprint, so the client can check the
signed index:

```
https://munzzyy.dev/wren/fdroid/repo?fingerprint=279DA658DD3DD265B4EA91D6B1C0925C9F19D9472A3D315068B2BADB8937DD6F
```

The repo page at that address shows a QR code for the same link.

## What it serves

The repo carries exactly the signed APKs from each GitHub release: the
universal one and the three per-CPU ones, all with the same version code, so
the client picks the one for your phone. Nothing is built for the repo
separately, which keeps one set of hashes for the reproducible-build check,
the release page and the repo index.

The index files (`entry.jar`, `index-v2.json`, `index-v1.jar`, icons and the
generated landing page) are static files on the munzzyy.dev host. The APK
entries in the index point at files under the same path, and a small function
on the host answers those by streaming the identical GitHub release asset,
with HEAD and Range passed through so downloads can resume. That detour
exists because the host caps static files at 25 MiB and the F-Droid client
refuses HTTP redirects for downloads (I tried; it fails with "Unhandled
redirect"). The client verifies the SHA-256 from the signed index after the
download, so where the bytes come from does not change what it accepts.

The app's icon is an adaptive XML icon, which fdroidserver cannot render, so
the repo takes it from `metadata/io.github.munzzyy.wren/en-US/images/icon.png`
instead.

## How it is built

The working directory lives on my build machine and is not in any repository.
It holds `config.yml`, the repo signing key, `metadata/io.github.munzzyy.wren.yml`
(name, summary, description, links and license) and a `repo/` directory.

For each release:

1. Download the signed APKs from the GitHub release and check them against
   `SHA256SUMS`.
2. Copy them into `repo/`, then run `fdroid update` (fdroidserver 2.4.5, pinned
   in its own virtual environment). It signs the index with the repo key.
3. Copy everything under `repo/` except the APKs and `status/` into the site
   tree and deploy the site. The function that streams the APKs derives the
   release tag from the file name, so a new release needs no site change
   beyond the index.
4. Add the repo in a clean F-Droid client and install Wren from it before
   calling the release done.

## The repo signing key

The repo key is separate from the APK signing key. It signs only the index.
Losing it, or rotating it, breaks every client that pinned the fingerprint
above, so a copy and its password went into the same offline backup as the APK
key before the first index was published, and the backup was listed to confirm
the file is in it.

Repo key certificate SHA-256:

```
27:9D:A6:58:DD:3D:D2:65:B4:EA:91:D6:B1:C0:92:5C:9F:19:D9:47:2A:3D:31:50:68:B2:BA:DB:89:37:DD:6F
```

APK signing certificate SHA-256 (the one `apksigner verify` prints for every
Wren APK):

```
b66420073b986655a97cb35b166fa5a06abd5119545b9e0b21b087d0f71a7d66
```
