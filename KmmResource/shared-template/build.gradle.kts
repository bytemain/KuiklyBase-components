import org.jetbrains.kotlin.gradle.plugin.KotlinHierarchyTemplate

plugins {
    id("com.tencent.kuiklybase.resource.generator")
    kotlin("multiplatform")
    kotlin("native.cocoapods")
    id("com.android.library")
    id("com.tencent.kuiklybase.knoi.plugin")
}

@OptIn(org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi::class)
kotlin {
    // 使用默认层级结构模板
    KotlinHierarchyTemplate.default

    // Android平台
    androidTarget {
        compilations.all {
            kotlinOptions {
                jvmTarget = "1.8"
            }
        }

        // 添加以下配置，将Android平台打包到组件产物中
        publishAllLibraryVariants()
        publishLibraryVariantsGroupedByFlavor = true
        publishLibraryVariants("release", "debug")
    }
    // jvm()

    // iOS平台
    iosX64()
    iosArm64()
    iosSimulatorArm64()
    cocoapods {
        name = "shared_template"
        summary = "Some description for the Shared Module"
        homepage = "Link to the Shared Module homepage"
        version = "1.0"
        ios.deploymentTarget = "11.0"
        podfile = project.file("../iosApp/Podfile")
        framework {
            baseName = "shared_template"
            // 如果依赖的 pod 组件是静态库，那么建议打开下面注释
            // 同时需要去除 iosApp 中对应脚本：iosApp Target -> Build Phases -> Run Script
//            isStatic = true
        }
    }

    // Harmony Native平台
    ohosArm64 {

        val main by compilations.getting

        compilations.forEach {
            // 抑制 NativeApi 提示
            it.compilerOptions.options.optIn.addAll(
                "kotlinx.cinterop.ExperimentalForeignApi",
                "kotlin.experimental.ExperimentalNativeApi",
            )
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                //put your multiplatform dependencies here
                implementation(project(":resource-compose"))
                implementation(project(":sample"))
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }

        val ohosArm64Main by getting {
            dependencies {
                // 鸿蒙依赖
            }
        }

    }
}

android {
    namespace = "com.tencent.kmm.component.template"
    compileSdk = 33
    defaultConfig {
        minSdk = 21
    }
}

multiplatformResources {
    multiplatformResourcesPackage = "com.tencent.tmm.kmmresource.sample"
    multiplatformResourcesPrefix = "sample_"
    commonGeneratedDir = "build/generated/tmm-res-common"
}
