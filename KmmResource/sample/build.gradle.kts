
plugins {
    kotlin("multiplatform")
   // alias(libs.plugins.composeGradle)
    id("com.tencent.kuiklybase.resource.generator")
    id("com.android.library")
    id("com.tencent.kuiklybase.knoi.plugin")
}
@OptIn(org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi::class)
kotlin {
    targetHierarchy.default()
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
    ohosArm64 {
        binaries.sharedLib {
            linkerOpts("-L${projectDir}/libs/", "-lresource_compose")

            baseName = "kn"
        }
    }

    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64()
    ).forEach {
        it.binaries.framework {
            baseName = "sample"
            isStatic = true
        }
    }

    sourceSets {
        val ohosArm64Main by getting

        commonMain.dependencies {
            //put your multiplatform dependencies here
            implementation(project(":resource-compose"))
        }

        val commonTest by getting {
            dependencies {
                api(kotlin("test"))
            }
        }
    }
}



knoi {
    ignoreTypeAssert = true
    tsGenDir = projectDir.absolutePath + "/ts-api/"
}


multiplatformResources {
    multiplatformResourcesPackage = "com.tencent.tmm.knoi"
    multiplatformResourcesPrefix = "sample_"
    commonGeneratedDir = "build/generated/tmm-res-common"
}

android {
    namespace = "com.tencent.tmm.knoi"
    compileSdk = 33
}
