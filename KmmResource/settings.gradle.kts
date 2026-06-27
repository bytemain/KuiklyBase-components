enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")
pluginManagement {
    repositories {
        maven("https://oss.sonatype.org/content/repositories/snapshots")
        maven("https://oss.sonatype.org/content/groups/public/")
        maven("https://mirrors.tencent.com/nexus/repository/maven-public/")

        mavenLocal()
        google()
        gradlePluginPortal()
        mavenCentral()

    }
}

dependencyResolutionManagement {
    repositories {
        maven("https://oss.sonatype.org/content/repositories/snapshots")
        maven("https://oss.sonatype.org/content/groups/public/")
        maven("https://mirrors.tencent.com/nexus/repository/maven-public/")

        mavenLocal()
        google()
        gradlePluginPortal()
        mavenCentral()

    }
}

rootProject.name = "KmmResource"
include(":resource-core")
include(":resource-generator")

val publishOnly = providers.gradleProperty("kmmResourcePublishOnly")
    .map(String::toBoolean)
    .orElse(false)
    .get()

val publishCompose = providers.gradleProperty("kmmResourcePublishCompose")
    .map(String::toBoolean)
    .orElse(false)
    .get()

if (publishCompose || !publishOnly) {
    include(":resource-compose")
}

if (!publishOnly) {
    include(":sample")
    include(":androidApp")
    include(":sample_noohos")
    //include(":sample_noohos_sub")
    include(":shared-template")
    //includeBuild("resource-generator")
}
