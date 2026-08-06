# Rannah identity completion report

Date: 2026-07-28  
Branch: `master`  
Application ID: `com.bal.reminders`  
Version: `1.1.0` (`versionCode 2`)

## Verification environment

- Java: OpenJDK `17.0.19` (`17.0.19+10`)
- Android compile/target SDK: 35
- Minimum SDK: 26

## Required build results

| Check | Result |
|---|---|
| `git diff --check` | Passed |
| `./gradlew test` | Passed; 210 debug + 210 release unit tests, 0 failures |
| `./gradlew lint` | Passed; 0 errors, 72 dependency/version warnings |
| `./gradlew assembleDebug` | Passed |
| `./gradlew assembleRelease` | Passed |
| `./gradlew bundleRelease` | Passed |

The remaining lint warnings only report newer available build tools and
dependencies. They are unrelated to the identity change and do not block the
release.

## Android resource verification

- AAPT2 compiled and linked all debug and release resources.
- No unresolved or obsolete color, drawable, theme, launcher, widget, splash,
  or notification resource references remain.
- Day resources provide the complete base set. Night-qualified resources
  override the splash and widget colors that differ at runtime.
- The light and dark Compose color schemes define their primary, secondary,
  tertiary, surface, container, outline, inverse, and error roles explicitly.
- The adaptive icon resolves its background, foreground, and monochrome layers.
- The monochrome layer is a separate 108dp vector containing one opaque white
  path, with no color-dependent detail.
- The notification icon is a 24dp vector with a transparent background and one
  opaque white silhouette, suitable for Android's system tint.
- The former bell path, teal/brass values, and obsolete resource names are not
  present in production or documentation sources.

## Release properties

- Merged package/application ID: `com.bal.reminders`.
- Release manifest contains no `android:debuggable` attribute; Gradle explicitly
  configures `isDebuggable = false`.
- `minifyReleaseWithR8`, `shrinkReleaseRes`, and
  `shrinkBundleReleaseResources` completed successfully.
- Release APK signature verified with APK Signature Schemes v2 and v3.
- Release AAB reports `jar verified`.
- Signer certificate SHA-256:
  `70ff2a39e29485fbfb1f08bb917c55fada6bf4ae765d571813c0d4022e46dd50`
- No keystore, private key, signing properties, or signing password is tracked
  by Git. Release credentials are loaded from an external properties file.

The signing certificate is intentionally self-signed, as expected for an
Android app upload/release key. Its expiry is 2056-07-18.

## Final release artifacts

| Artifact | Size | SHA-256 |
|---|---:|---|
| `rannah-release.apk` | 2,167,878 bytes | `7d18f7d5db5040414cd441852453487163b924b821cd7ea96110398eb5be0d73` |
| `rannah-release.aab` | 4,492,991 bytes | `4c5afda14a5f4b0704affe58f06824a3eefeefda6872313cd81d40a99cd6165f` |

Delivery locations:

- `~/Desktop/rannah-release.apk`
- `~/Downloads/rannah-release.apk`
- `~/Desktop/rannah-release.aab`

## Runtime-only risks

No emulator or device run was performed, by request. Static verification cannot
prove launcher-mask appearance across every OEM, themed-icon rendering on every
launcher, notification presentation on vendor skins, widget refresh behavior,
or perceived light/dark contrast on physical displays. These are the remaining
runtime-only risks; no compile, test, lint, shrinking, packaging, or signing
blocker remains.
