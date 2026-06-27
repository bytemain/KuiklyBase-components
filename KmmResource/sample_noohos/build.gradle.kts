plugins {
    kotlin("multiplatform")
    // alias(libs.plugins.composeGradle)
    id("com.tencent.kuiklybase.resource.generator")
    id("com.android.library")
    kotlin("native.cocoapods")
    id("com.google.devtools.ksp")
    id("com.tencent.kuiklybase.knoi.plugin")
}
@OptIn(org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi::class)
kotlin {
    targetHierarchy.default()
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

    // iOS平台
    iosX64()
    iosArm64()
    iosSimulatorArm64()
    cocoapods {
        summary = "qqlive launch Shared Module"
        homepage = "Link to the Shared Module homepage"
        version = "1.0"
        ios.deploymentTarget = "14.1"
        framework {
            baseName = "sample_noohos"
        }
    }

    ohosArm64()


    sourceSets {


        val commonMain by getting {
            dependencies {
                api(project(":resource-compose"))
                //api(project(":sample_noohos_sub"))
                //implementation("com.tencent.tmm:sample_noohos_sub:0.0.1-SNAPSHOT")
                //implementation("com.tencent.tmm:resource-compose:0.0.6-SNAPSHOT")
            }
        }
        val appleMain by getting {
            dependsOn(commonMain)
        }
        val iosMain by getting {
            dependsOn(appleMain)
        }
        val iosSimulatorArm64Main by getting {
            dependsOn(iosMain)

        }
    }
}


knoi {
    ignoreTypeAssert = true
    tsGenDir = projectDir.absolutePath + "/ts-api/"
}

multiplatformResources {
    multiplatformResourcesPackage = "com.tencent.tmm.kmmresource.sample"
    multiplatformResourcesPrefix = "sample_"
    commonGeneratedDir = "build/generated/tmm-res-common"
}

android {
    namespace = "com.tencent.tmm.kmmresource"
    compileSdk = 33
    defaultConfig {
        minSdk = 21
    }
}
