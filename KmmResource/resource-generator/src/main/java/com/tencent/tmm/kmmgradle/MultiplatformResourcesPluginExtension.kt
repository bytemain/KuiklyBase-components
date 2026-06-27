/*
 * Copyright 2019 IceRock MAG Inc. Use of this source code is governed by the Apache 2.0 license.
 */

package com.tencent.tmm.kmmgradle

import com.squareup.kotlinpoet.KModifier
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet

open class MultiplatformResourcesPluginExtension {
    var multiplatformResourcesPackage: String? = null
    var multiplatformResourcesClassName: String = "MR"
    var multiplatformResourcesSourceSet: String? = null
    var multiplatformResourcesPrefix: String? = null
    var iosBaseLocalizationRegion: String = "en"
    var staticFrameworkWarningEnabled: Boolean = false
    /**
     * Optional generated root for the common MR source set.
     *
     * When unset, common MR keeps the historical root:
     * build/generated/tmm-res/commonMain/{src,res,assets}.
     * Set this to a stable root such as "build/generated/tmm-res-common" to
     * register only build/generated/tmm-res-common/commonMain/src into
     * commonMain while platform generated outputs keep the default root.
     */
    var commonGeneratedDir: String? = null
    val sourceSetName: String
        get() = multiplatformResourcesSourceSet ?: KotlinSourceSet.COMMON_MAIN_SOURCE_SET_NAME
    var disableStaticFrameworkWarning = false
    var multiplatformResourcesVisibility: MRVisibility = MRVisibility.Public
    var commonMRKModifiers: Array<KModifier> = emptyArray()

}
