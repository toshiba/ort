/*
 * Copyright (C) TOSHIBA CORPORATION, 2023.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 * License-Filename: LICENSE
 */

import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    // Apply precompiled plugins.
    id("ort-library-conventions")

    // Apply third-party plugins.
    alias(libs.plugins.kotlinSerialization)
}

dependencies {
    api(project(":reporter"))
    api(project(":utils:spdx-utils"))

    implementation(project(":downloader"))
    implementation(project(":model"))
    implementation(project(":scanner"))
    implementation(project(":utils:common-utils"))
    implementation(project(":plugins:reporters:evaluated-model-reporter"))
    
    implementation(libs.bundles.kotlinxSerialization)
    implementation(libs.okhttp)
    implementation(libs.jackson.core)
    implementation(libs.jackson.databind)
    implementation(libs.jackson.module.kotlin)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.freemarker)

    funTestImplementation(testFixtures(project(":reporter")))
}

tasks.withType<KotlinCompile>().configureEach {
    val customCompilerArgs = listOf(
        "-opt-in=kotlinx.serialization.ExperimentalSerializationApi"
    )

    compilerOptions {
        freeCompilerArgs.addAll(customCompilerArgs)
    }
}
