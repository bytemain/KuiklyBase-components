### ChangeLog

##### Unreleased

- docs: point consumer setup to the bytemain GitHub Packages Maven repository
- docs: add file-based GitHub Packages credentials with environment fallback
- feature: add P0 unified NetworkClient request/response model, cancellation, auth middleware, and policy API

##### 0.0.5-raft.0

- feature: support custom HTTP methods via VBTransportRequest
- feature: add multipart/form-data request body builder
- docs: add PUT and binary upload examples
- docs: add HarmonyOS native library integration guide
- build: add GitHub Packages publishing for Android, iOS, and OHOS artifacts
- docs: document manual and CI publishing to GitHub Packages
- build: publish HarmonyOS native runtime libraries as `network-ohos-runtime`
- build: add Gradle plugin to sync HarmonyOS native runtime libraries
- build: split GitHub Packages CI publishing across Linux/HarmonyOS and macOS hosts
- build: run NetworkKMM Android/OHOS and iOS publish jobs in parallel, then publish KMP metadata
- build: align NetworkKMM Kotlin, KSP, Android Gradle Plugin, and Gradle wrapper versions
- build: fail publishing early when Gradle task discovery cannot configure the project
- build: keep NetworkKMM KSP on the Kotlin 2.0.21-compatible line and disable the KBA suffix version warning
- build: configure HarmonyOS SDK environment variables from the CI image before publishing OHOS artifacts

##### 0.0.4

- feature: first publish
