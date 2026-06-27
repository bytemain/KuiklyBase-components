plugins {
    //trick: for the same plugin versions in all sub-modules
    alias(libs.plugins.androidLibrary).apply(false)
    kotlin("multiplatform").version(libs.versions.kuiklyOhosBuildPlugin.get()).apply(false)
    alias(libs.plugins.composeOhos).apply(false)
    alias(libs.plugins.composeCompiler).apply(false)
    id("com.google.devtools.ksp") version "2.0.21-1.0.28" apply false
    id("com.tencent.kuiklybase.knoi.plugin") version ("0.0.4") apply false
    alias(libs.plugins.jetbrainsKotlinJvm) apply false

}

allprojects {
    repositories {
        maven("https://oss.sonatype.org/content/repositories/snapshots")
        maven("https://oss.sonatype.org/content/groups/public/")
        maven("https://mirrors.tencent.com/nexus/repository/maven-public/")

        mavenLocal()
        google()
        mavenCentral()
    }


}



buildscript {
    repositories {
        mavenLocal()
        mavenCentral()
        google()
        gradlePluginPortal()
    }
}
