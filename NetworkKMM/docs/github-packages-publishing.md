# GitHub Packages Publishing

This project can publish the NetworkKMM Maven artifacts to GitHub Packages under:

```text
https://maven.pkg.github.com/bytemain/KuiklyBase-components
```

The publishing flow covers all current Kotlin Multiplatform publications:

| Platform | Gradle publication |
| --- | --- |
| Common metadata | `kotlinMultiplatform` |
| Android | `android` |
| iOS simulator x64 | `iosX64` |
| iOS device arm64 | `iosArm64` |
| iOS simulator arm64 | `iosSimulatorArm64` |
| HarmonyOS arm64 | `ohosArm64` |

The verified Maven artifact IDs are:

```text
com.tencent.kuiklybase:network
com.tencent.kuiklybase:network-android
com.tencent.kuiklybase:network-iosx64
com.tencent.kuiklybase:network-iosarm64
com.tencent.kuiklybase:network-iossimulatorarm64
com.tencent.kuiklybase:network-ohosarm64
com.tencent.kuiklybase:network-ohos-runtime
com.tencent.kuiklybase:network-ohos-runtime-gradle-plugin
```

`network-android` publishes both release and debug AAR variants because the module currently calls `publishLibraryVariants("release", "debug")`. `network-ohosarm64` also publishes the generated cinterop KLIB.
`network-ohos-runtime` is a zip artifact that contains the HarmonyOS runtime libraries:

```text
arm64-v8a/libpbcurlwrapper.so
arm64-v8a/libopenssl.so
arm64-v8a/libc++_shared.so
```

Consumers should depend on the root artifact and let Gradle metadata select the platform artifact:

```kotlin
implementation("com.tencent.kuiklybase:network:0.0.4")
```

HarmonyOS apps still need these native runtime libraries in the app entry module. The Maven/KLIB publication provides the Kotlin artifact; the Gradle plugin resolves `network-ohos-runtime` and copies the `.so` files into `entry/libs/arm64-v8a/`.

## Consume From GitHub Packages

GitHub Packages requires authentication for package reads and writes, including public packages. Add the repository to the consuming Gradle build:

```kotlin
repositories {
    maven {
        name = "githubPackages"
        url = uri("https://maven.pkg.github.com/bytemain/KuiklyBase-components")
        credentials {
            username = findProperty("gpr.user") as String? ?: System.getenv("GITHUB_ACTOR")
            password = findProperty("gpr.key") as String?
                ?: System.getenv("GITHUB_PACKAGES_TOKEN")
                ?: System.getenv("GITHUB_TOKEN")
        }
    }
}
```

For local consumption, use a classic GitHub PAT with `read:packages`:

```bash
export GITHUB_ACTOR=your-github-user
export GITHUB_PACKAGES_TOKEN=ghp_xxx
```

For GitHub Actions in a repository that has read access to the package, `GITHUB_TOKEN` can be used.

## Consume OHOS Runtime

Add the GitHub Packages Maven repository to both plugin resolution and dependency resolution:

```kotlin
// settings.gradle.kts
pluginManagement {
    repositories {
        maven {
            url = uri("https://maven.pkg.github.com/bytemain/KuiklyBase-components")
            credentials {
                username = System.getenv("GITHUB_ACTOR")
                password = System.getenv("GITHUB_PACKAGES_TOKEN") ?: System.getenv("GITHUB_TOKEN")
            }
        }
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        maven {
            url = uri("https://maven.pkg.github.com/bytemain/KuiklyBase-components")
            credentials {
                username = System.getenv("GITHUB_ACTOR")
                password = System.getenv("GITHUB_PACKAGES_TOKEN") ?: System.getenv("GITHUB_TOKEN")
            }
        }
        google()
        mavenCentral()
    }
}
```

Apply the plugin from the Gradle project that owns the OHOS app directory:

```kotlin
plugins {
    id("com.tencent.kuiklybase.network.ohos-runtime") version "0.0.4"
}

networkOhosRuntime {
    // Default is ohosApp/entry/libs/arm64-v8a if ohosApp/entry exists,
    // otherwise entry/libs/arm64-v8a.
    outputDir.set(layout.projectDirectory.dir("ohosApp/entry/libs/arm64-v8a"))
}
```

Then sync the native runtime before building the OHOS app:

```bash
./gradlew copyNetworkOhosRuntimeLibs
```

The plugin version is also used as the default `network-ohos-runtime` artifact version. Override only if needed:

```kotlin
networkOhosRuntime {
    version.set("0.0.4")
}
```

## Manual Publish

Use a classic GitHub PAT with `write:packages`:

```bash
export GITHUB_PACKAGES_USERNAME=bytemain
export GITHUB_PACKAGES_TOKEN=ghp_xxx
export MAVEN_VERSION=0.0.5

cd NetworkKMM
./scripts/publish-github-packages.sh
```

By default the script publishes these tasks:

```text
:network:publishAndroidPublicationToGithubPackagesRepository
:network:publishIosX64PublicationToGithubPackagesRepository
:network:publishIosArm64PublicationToGithubPackagesRepository
:network:publishIosSimulatorArm64PublicationToGithubPackagesRepository
:network:publishOhosArm64PublicationToGithubPackagesRepository
:network-ohos-runtime:publishAllPublicationsToGithubPackagesRepository
:network-ohos-runtime-gradle-plugin:publishAllPublicationsToGithubPackagesRepository
:network:publishKotlinMultiplatformPublicationToGithubPackagesRepository
```

The script checks which publish tasks exist on the current host before invoking Gradle. On Linux/HarmonyOS hosts, Kotlin/Native does not create iOS publish tasks, so the script skips them unless `NETWORK_REQUIRE_TASKS=true` is set. Use `NETWORK_DRY_RUN=true` to validate task selection without uploading artifacts.

The root `kotlinMultiplatform` metadata publication is intentionally last in the default task list. If a target publication, runtime artifact, or plugin publication fails, consumers will not see new metadata that points at incomplete artifacts.

To publish a subset while recovering or testing, override `NETWORK_PUBLISH_TASKS`:

```bash
NETWORK_PUBLISH_TASKS=":network:publishAndroidPublicationToGithubPackagesRepository :network:publishOhosArm64PublicationToGithubPackagesRepository" \
  ./scripts/publish-github-packages.sh
```

To require every requested task to exist on the current host:

```bash
NETWORK_REQUIRE_TASKS=true ./scripts/publish-github-packages.sh
```

## CI Publish

The workflow is `.github/workflows/publish-network-github-packages.yml`.

It can be triggered in two ways:

```bash
git tag network-v0.0.5
git push origin network-v0.0.5
```

Or run **Publish NetworkKMM to GitHub Packages** from GitHub Actions and optionally pass `version`.

The workflow splits publishing by host:

- The Linux job uses `ghcr.io/bytemain/harmony-next-pipeline-docker/harmonyos-ci-image:v6.1.1.280`, matching the HarmonyOS command-line tools used by `bytemain/soduku-harmony`. It publishes Android, OHOS, OHOS runtime, and the Gradle plugin.
- The Linux job maps the image-provided `OHOS_BASE_SDK_HOME` to `OHOS_SDK_HOME`, `OHOS_NDK_HOME`, `OHOS_LLVM_HOME`, and DevEco SDK variables so Kotlin/Native can find the HarmonyOS sysroot during `ohosArm64` cinterop.
- The macOS iOS job runs in parallel with the Linux job and publishes the iOS KLIB artifacts.
- The macOS metadata job runs after the platform jobs succeed and publishes the root `kotlinMultiplatform` metadata publication.
- All publish jobs install Android SDK platform 33 and build-tools 33.0.2 because the Android Gradle plugin is configured during project evaluation.
- Publishes with `GITHUB_TOKEN` and `packages: write`, so no extra publish secret is required for this repository.

## Host Notes

Android publication requires an Android SDK. OHOS publication requires the HarmonyOS command-line/native toolchain from the CI image.

iOS here is published as Kotlin Multiplatform KLIB artifacts, not as the CocoaPods/XCFramework demo artifact. The iOS publish tasks are host-specific and should run on macOS. The root KMP metadata publication should run only after Android, OHOS, runtime, plugin, and iOS artifacts are available for that version.
