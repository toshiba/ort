/*
 * Copyright (C) TOSHIBA CORPORATION, 2024.
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

package org.ossreviewtoolkit.plugins.reporters.sw360licensereport.sw360api.client

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.core.spec.Spec
import io.kotest.core.spec.style.StringSpec
import io.kotest.core.test.TestCase
import io.kotest.engine.spec.tempdir
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

import java.io.IOException
import java.time.LocalDate

import org.ossreviewtoolkit.model.config.Sw360StorageConfiguration
import org.ossreviewtoolkit.reporter.ORT_RESULT
import org.ossreviewtoolkit.reporter.ReporterInput
import org.ossreviewtoolkit.utils.test.getAssetFile
import org.ossreviewtoolkit.utils.test.matchExpectedResult


class Sw360ProjectApiClientFunTest : StringSpec({
    "Testing CRUD and various operations of the rest client for SW360 Project." {
        if (Sw360LicenseReportTestUtil.hasStorageConfig()) {

            val testConfig = Sw360LicenseReportTestUtil.getConfig()
            val sw360StrageConfig = Sw360LicenseReportTestUtil.getSw360StorageConfiguration(testConfig)

            // Create api clients
            val projectClient = Sw360ProjectApiClient(sw360StrageConfig)

            // String literal for testing
            val name = "ORT_PROJECT_CREATION_TEST_BY_API"
            val version = "1.00"
            val visibility = SW360VISIBILITY_EVERYONE

            // Create a test project to SW360.
            val entry = Sw360ApiClientDataFactory.createSw360Project()
            entry.setRootValue("name", name)
            entry.setRootValue("version", version)
            entry.setRootValue("visibility", visibility)
            val createProjectResult = projectClient.createProject(entry.getJsonAsString())

            createProjectResult shouldNotBe null
            val projectId = createProjectResult.getId()
            withClue("createProject createProjectResult=${createProjectResult.getJsonAsString()}") {
                projectId.isNotEmpty() shouldBe true
            }

            // Retrieve the test project from SW360.
            val getProjectResult = projectClient.getProject(projectId)
            withClue("getProject getProjectResult=${getProjectResult.getJsonAsString()}") {
                getProjectResult.getId() shouldBe projectId
                getProjectResult.getRootValue("name") shouldBe name
            }

            // Retrieve all test release from SW360.
            val getProjectsResult = projectClient.getProjects()
            withClue("getProjects getProjectsResult=${getProjectsResult}") {
                getProjectsResult shouldNotBe null
                getProjectsResult.isNotEmpty() shouldBe true
            }
            
            // Retrieve the ID map using the project's name and version as the key.
            val nameIdMap = projectClient.getSw360ProjectNameIdMap()
            withClue("getSw360ProjectNameIdMap nameIdMap=${nameIdMap.toString()}") {
                nameIdMap.containsKey(Pair(name.lowercase(), version.lowercase())) shouldBe true
                nameIdMap.get(Pair(name.lowercase(), version.lowercase())) shouldBe projectId
            }

            // Update the test project in SW360.
            val description = "additional message"
            val updatedVersion = "1.10"

            val updateEntry = Sw360ApiClientDataFactory.createSw360Project()
            updateEntry.setRootValue("version", updatedVersion)
            updateEntry.setRootValue("description", description)
            val updateProjectResult = projectClient.updateProject(projectId, updateEntry.getJsonAsString())
            withClue("updateProject updateProjectResult=${updateProjectResult.getJsonAsString()}") {
                updateProjectResult.getId() shouldBe projectId
                updateProjectResult.getRootValue("version") shouldBe updatedVersion
                updateProjectResult.getRootValue("description") shouldBe description
            }

            val updatedProject = projectClient.getProject(projectId)
            withClue("getProject updatedProject=${updatedProject.getJsonAsString()}") {
                updatedProject shouldNotBe null
                updatedProject.getId() shouldBe projectId
                updatedProject.getRootValue("version") shouldBe updatedVersion
                updatedProject.getRootValue("description") shouldBe description
            }

            // Delete the test project from SW360.
            projectClient.deleteProject(projectId)
            shouldThrow<IOException> {
                projectClient.getProject(projectId)
            }
            val deletedNameIdMap = projectClient.getSw360ProjectNameIdMap()
            deletedNameIdMap.containsKey(Pair(name.lowercase(), version.lowercase())) shouldBe false

        }
    }
})
