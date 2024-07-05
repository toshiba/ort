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


class Sw360ComponentApiClientFunTest : StringSpec({
    "Testing CRUD and various operations of the rest client for SW360 component." {
        if (Sw360LicenseReportTestUtil.hasStorageConfig()) {
        
            val testConfig = Sw360LicenseReportTestUtil.getConfig()
            val sw360StrageConfig = Sw360LicenseReportTestUtil.getSw360StorageConfiguration(testConfig)

            val componentClient = Sw360ComponentApiClient(sw360StrageConfig)

            // String literal for testing
            val name = "ORT_COMPONENT_CREATION_TEST_BY_API"
            val componentType = "OSS"
            val originalDescription = "original message"
            val updatedDescription = "updated message"
            val homepage = "https://github.com/oss-review-toolkit/ort/sw360-license-report/funtest"

            // Create a test Component to SW360.
            val entry = Sw360ApiClientDataFactory.createSw360Component()
            entry.setRootValue("name", name)
            entry.setRootValue("componentType", componentType)
            entry.setRootValue("homepage", homepage)
            entry.setRootValue("description", originalDescription)

            val createComponentResult = componentClient.createComponent(entry.getJsonAsString())
            val componentId = createComponentResult.getId()
            withClue("createComponent createComponentResult=${createComponentResult.getJsonAsString()}") {
                componentId.isNotEmpty() shouldBe true
            }

            // Retrieve the test component from SW360.
            val getComponentResult = componentClient.getComponent(componentId)
            withClue("getComponent getComponentResult=${getComponentResult.getJsonAsString()}") {
                getComponentResult.getId() shouldBe componentId
                getComponentResult.getRootValue("name") shouldBe name
                getComponentResult.getRootValue("componentType") shouldBe componentType
                getComponentResult.getRootValue("homepage") shouldBe homepage
                getComponentResult.getRootValue("description") shouldBe originalDescription
            }

            // Retrieve all test component from SW360.
            val getComponentsResult = componentClient.getComponents()
            withClue("getComponents") {
                getComponentsResult.isNotEmpty() shouldBe true
            }
            
            // Retrieve the ID map using the component's name as the key.
            val nameIdMap = componentClient.getSw360ComponentNameIdMap()
            withClue("getSw360ComponentNameIdMap nameIdMap=${nameIdMap.toString()}") {
                nameIdMap.containsKey(name.lowercase()) shouldBe true
                nameIdMap.get(name.lowercase()) shouldBe componentId
            }

            // Update the test component in SW360.
            val updateEntry = Sw360ApiClientDataFactory.createSw360Component()
            updateEntry.setRootValue("description", updatedDescription)
            val updateProjectResult = componentClient.updateComponent(componentId, updateEntry.getJsonAsString())
            withClue("updateProject updateProjectResult=${updateProjectResult.getJsonAsString()}") {
                updateProjectResult.getId() shouldBe componentId
                updateProjectResult.getRootValue("componentType") shouldBe componentType
                updateProjectResult.getRootValue("name") shouldBe name
                updateProjectResult.getRootValue("description") shouldBe updatedDescription
                updateProjectResult.getRootValue("homepage") shouldBe homepage
            }

            val getComponentResult2 = componentClient.getComponent(componentId)
            withClue("getComponent getComponentResult2=${getComponentResult2.getJsonAsString()}") {
                getComponentResult2.getId() shouldBe componentId
                getComponentResult2.getRootValue("componentType") shouldBe componentType
                getComponentResult2.getRootValue("name") shouldBe name
                getComponentResult2.getRootValue("description") shouldBe updatedDescription
                getComponentResult2.getRootValue("homepage") shouldBe homepage
            }

            // Delete the test component from SW360.
            componentClient.deleteComponent(componentId)
            shouldThrow<IOException> {
                componentClient.getComponent(componentId)
            }
            val deletedNameIdMap = componentClient.getSw360ComponentNameIdMap()
            deletedNameIdMap.containsKey(name.lowercase()) shouldBe false
            
        }
    }
})
