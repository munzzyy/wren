# Reproducible Builds

[![Reproducible build](https://github.com/munzzyy/wren/actions/workflows/reprocheck.yml/badge.svg)](https://github.com/munzzyy/wren/actions/workflows/reprocheck.yml)

Follow these instructions to check that the source code is exactly what was used to compile the APK I distribute.

The [reproducible-builds.org](https://reproducible-builds.org/) project has more information about this general topic.

## Prerequisites

- Docker
- Docker Compose
- Python 3

## Release assets

Each release has two builds, and each comes signed or unsigned depending on whether the release key was available to the build:

| Build | Signed | Unsigned |
|-------|--------|----------|
| `prodStore`, no in-app updater (GitHub, Obtainium) | `Wren-<version>.apk` | `Wren-unsigned-<version>.apk` |
| `prodWebsite`, with in-app updater | `Wren-website-<version>.apk` | `Wren-website-unsigned-<version>.apk` |

`SHA256SUMS` lists the checksum of every APK in the release.

## Build and Verify

You can compile your own release of Wren inside a Docker container and compare the result to the APK I publish. To do so, run:

```shell
# Set the release version you want to check
export VERSION=v1.0.0

# Clone the source code repository
git clone https://github.com/munzzyy/wren.git

# Go to this directory
cd wren/reproducible-builds

# Check out the release tag
git checkout $VERSION

# The following steps might be different for the chosen version.
# Before proceeding, review the instructions in this README at that tag.

# Build the APK using the Docker environment
docker compose up --build

# Download the official APK
wget https://github.com/munzzyy/wren/releases/download/$VERSION/Wren-$VERSION.apk

# Name your build the same way the release does
./name-outputs.sh $VERSION built

# Run the diff script to compare the APKs
python apkdiff/apkdiff.py Wren-$VERSION.apk built/Wren-unsigned-$VERSION.apk

# Clean up the Docker environment
docker compose down
```

To check the in-app updater build, use `Wren-website-$VERSION.apk` and `built/Wren-website-unsigned-$VERSION.apk` instead.

If you get `APKs match`, you have **successfully verified** that the official release matches your own self-built version of Wren.

If you get `APKs don't match`, please [report the issue](https://github.com/munzzyy/wren/issues).
