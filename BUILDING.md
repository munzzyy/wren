# Building and Self-Signing Wren

## Overview

This guide covers building and self-signing Wren. It is for developers and experienced users who are comfortable compiling software and managing signing keys.

⚠️ **Warning**: Mishandling signing keys can result in security vulnerabilities and compromise the app's integrity. Protect your keys and understand the implications of self-signing your app.

## Security Considerations

- **In-App Updater**: Self-signed apps cannot update automatically via the integrated updater because of differing signatures. You'll need to build and install updates manually, or set up your own private F-Droid repository (beyond this guide's scope).

- **Clean Environment**: Building the app requires a clean and secure environment. Using non-dedicated computers or the cloud is discouraged as it increases the risk of attackers injecting malicious code during the build. Running Reproducible Builds on the same environment used to build the app only verifies that the build is deterministic, not that the build is secure.

- **Offline Signing**: Signing APKs offline with the Android SDK is recommended. For better security, consider keeping your signing key on a smartcard and signing with that.

## Prerequisites

### Install JDK

Install a Java Development Kit (JDK) so you can generate your signing key with `keytool`.

### Generate Your Private Key

Generate a signing private key using:

```sh
keytool -genkey -v -keystore my-release-key.jks -keyalg RSA -keysize 4096 -validity 10000 -alias my-alias
```

## Building Using GitHub Actions

You can build Wren using GitHub Actions, either with GitHub-hosted public runners or [self-hosted runners](https://docs.github.com/en/actions/hosting-your-own-runners/managing-self-hosted-runners/about-self-hosted-runners).

### Steps

1. **Fork the Repository**: Fork [munzzyy/wren](https://github.com/munzzyy/wren) in GitHub. You can keep your fork private if you prefer, but public repositories get GitHub Actions for free, while private repositories have limited free storage and minutes. For details, see [GitHub's billing information](https://docs.github.com/en/billing/managing-billing-for-github-actions/about-billing-for-github-actions).

2. **Configure Repository Variables**: Customize your build via `Settings > Secrets and Variables > Actions > Variables > Repository variables`. Check the table below for available options.

3. **Tag a Release**: Push a tag that starts with `v` (for example `git tag v1.0.0 && git push origin v1.0.0`) to start the `Release` workflow. To rebuild a tag that already exists, run the workflow by hand under Actions and enter the tag.

4. **Monitor Build Progress**: The build typically takes around 45 minutes.

5. **Download APKs**: Once the workflow finishes, go to Releases in your repository and download the APKs and `SHA256SUMS` listed under "Assets". The release is created as a draft.

6. **Publish Release**: Optionally, publish the release draft to trigger the Reproducible Build workflow.

7. **Sign the APKs**: If you did not configure automatic signing, follow the instructions below on how to [sign the APKs](#signing-the-apks) before installation.

8. **Install the APKs**: After signing, the APKs are ready for installation on Android.

9. **Keep Your Fork Updated**: Periodically sync your repository and then go back to step 3 with a new tag. Follow GitHub's documentation on [syncing a fork](https://docs.github.com/en/github/collaborating-with-issues-and-pull-requests/syncing-a-fork). This keeps your app updated.

## Building Using the CLI

If you prefer building Wren locally, these steps are essentially the same as the [Reproducible Build guide](reproducible-builds/README.md). You can customize the build by exporting environment variables or saving them in a `.env` file before running `docker compose`.

### Steps

```sh
# Set the release version you want to build
export VERSION=v1.0.0

# Clone the source code repository
git clone https://github.com/munzzyy/wren.git

# Navigate to the reproducible builds directory
cd wren/reproducible-builds

# Checkout the specific release tag
git checkout $VERSION

# Customize your build by exporting environment variables if needed
export CI_APP_TITLE="Wren"
export CI_PACKAGE_ID="io.github.munzzyy.wren"

# Build the APK using Docker environment
docker compose up --build

# Optionally, save environment variables in a .env file for future builds
echo "CI_APP_TITLE=Wren" >> .env
echo "CI_PACKAGE_ID=io.github.munzzyy.wren" >> .env

# Copy the APKs out under the release names
./name-outputs.sh $VERSION built

# Shut down the Docker environment after use
docker compose down
```

The built APKs will be available in the `outputs/apk` directory, and the renamed copies in `built`. Make sure to [sign the APKs](#signing-the-apks) before installation.

## Build Customization

| Environment Variable  | Default Value | Description                                      |
|-----------------------|---------------|--------------------------------------------------|
| `CI_APP_TITLE`        | Wren          | App title as shown in the UI                     |
| `CI_APP_FILENAME`     | Wren          | Base filename for APKs and backups               |
| `CI_PACKAGE_ID`       | io.github.munzzyy.wren | Application ID (change as needed)       |
| `CI_BUILD_VARIANTS`   | prod          | Regex pattern for building different flavors (must match one of the build flavors) |
| `CI_FORCE_INTERNAL_USER_FLAG` | false | Enable internal testing extensions               |

## Build Flavors

- `prodStoreRelease`: Production version of Wren without the in-app updater. This is what GitHub Releases and Obtainium get.
- `prodWebsiteRelease`: Production version of Wren with the in-app updater.
- `stagingWebsiteRelease`: Testing version of Wren for the Signal staging network.

## Signing the APKs

### Offline Signing

To sign the APKs offline, install the Android SDK and use the `apksigner` tool:

```sh
apksigner sign --ks my-release-key.jks --out Wren-$VERSION.apk Wren-unsigned-$VERSION.apk
```

### Automatic Signing via GitHub Actions

Configure automatic signing if you trust GitHub to safeguard your private key.

1. **Encode Keystore File**

   ```sh
   base64 my-release-key.jks
   ```

2. **Add Secrets to GitHub**

   Go to your repository's `Settings > Secrets > Actions` and add the following secrets:
   - `SECRET_KEYSTORE`: Paste the base64 encoded content of your keystore file.
   - `SECRET_KEYSTORE_ALIAS`: Your key alias (e.g., `my-alias`).
   - `SECRET_KEYSTORE_PASSWORD`: Your keystore password.

Without these secrets the release workflow uploads the unsigned APKs.
