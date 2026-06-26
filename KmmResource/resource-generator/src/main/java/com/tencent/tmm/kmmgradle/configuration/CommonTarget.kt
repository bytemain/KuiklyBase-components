package com.tencent.tmm.kmmgradle.configuration

import com.squareup.kotlinpoet.KModifier
import com.tencent.tmm.kmmgradle.MultiplatformResourcesPluginExtension
import com.tencent.tmm.kmmgradle.generator.MRGenerator
import com.tencent.tmm.kmmgradle.generator.ResourceGeneratorFeature
import com.tencent.tmm.kmmgradle.generator.common.CommonMRGenerator
import com.tencent.tmm.kmmgradle.tasks.GenerateMultiplatformResourcesTask
import org.gradle.api.Project
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet
import org.jetbrains.kotlin.gradle.plugin.KotlinTarget
import java.io.File

fun setupCommonTarget(
    project: Project,
    mrExtension: MultiplatformResourcesPluginExtension,
    mrSettings: MRGenerator.MRSettings,
    features: List<ResourceGeneratorFeature<out MRGenerator.Generator>>
) {
    setupCommonMRKModifiers(project, mrExtension)

    val generationTask = setupCommonGenerator(
        commonSourceSet = mrSettings.commonSourceSet,
        generatedDir = mrSettings.commonGeneratedDir,
        mrSettings = mrSettings,
        features = features,
        target = project
    )

    project.project.tasks
        .withType<GenerateMultiplatformResourcesTask>()
        .matching { it != generationTask }
        .configureEach { it.dependsOn(generationTask) }

}

private fun setupCommonGenerator(
    commonSourceSet: KotlinSourceSet,
    generatedDir: File,
    mrSettings: MRGenerator.MRSettings,
    features: List<ResourceGeneratorFeature<out MRGenerator.Generator>>,
    target: Project
): GenerateMultiplatformResourcesTask {
    val commonGeneratorSourceSet: MRGenerator.SourceSet = createSourceSet(commonSourceSet)
    return CommonMRGenerator(
        target,
        generatedDir,
        commonGeneratorSourceSet,
        mrSettings,
        generators = features.map { it.createCommonGenerator() }).apply(target)
}

private fun setupCommonMRKModifiers(
    project: Project,
    mrExtension: MultiplatformResourcesPluginExtension
) {

    val multiplatformExtension =
        project.extensions.getByType(KotlinMultiplatformExtension::class)
    val targets: List<KotlinTarget> =
        multiplatformExtension.targets.toList().filter { it.name != "metadata" }
    // 仅单平台时执行
    if (targets.singleOrNull() != null) {
        val taskRequest = project.gradle.startParameter.taskNames
        if (taskRequest.isNullOrEmpty()) {
            mrExtension.commonMRKModifiers = arrayOf(KModifier.PUBLIC)
            return
        }
        if (taskRequest.singleOrNull() != null && taskRequest.single().startsWith("generateMR")) {
            mrExtension.commonMRKModifiers = arrayOf(KModifier.PUBLIC)
            return
        }
    }
    mrExtension.commonMRKModifiers = arrayOf(KModifier.EXPECT)
}

fun createSourceSet(kotlinSourceSet: KotlinSourceSet): MRGenerator.SourceSet {
    return object : MRGenerator.SourceSet {
        override val name: String
            get() = kotlinSourceSet.name

        override fun addSourceDir(directory: File) {
            kotlinSourceSet.kotlin.srcDir(directory)
        }

        override fun addResourcesDir(directory: File) {
            kotlinSourceSet.resources.srcDir(directory)
        }

        override fun addAssetsDir(directory: File) {
            // nothing
        }
    }
}
