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

import io.kotest.assertions.fail
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


class Sw360ReleaseApiClientFunTest : StringSpec({

    "Testing CRUD and various operations of the rest client for SW360 Release." {
        if (Sw360LicenseReportTestUtil.hasStorageConfig()) {
            
            val testConfig = Sw360LicenseReportTestUtil.getConfig()
            val sw360StrageConfig = Sw360LicenseReportTestUtil.getSw360StorageConfiguration(testConfig)

            // Create api clients
            val componentClient = Sw360ComponentApiClient(sw360StrageConfig)
            val releaseClient = Sw360ReleaseApiClient(sw360StrageConfig)

            // String literal for testing
            val name = "ORT_RELEASE_CREATION_TEST_BY_API"
            val componentType = "OSS"
            val version = "1.23"
            val updatedVersion = "2.34"

            // Create a test Component to SW360.
            val createComponentEntry = Sw360ApiClientDataFactory.createSw360Component()
            createComponentEntry.setRootValue("name", name)
            createComponentEntry.setRootValue("componentType", componentType)
            val createComponentResult = componentClient.createComponent(createComponentEntry.getJsonAsString())
            val componentId = createComponentResult.getId()
            withClue("createComponent createComponentResult=${createComponentResult.getJsonAsString()}") {
                componentId.isNotEmpty() shouldBe true
            }

            // Create a test Release to SW360.
            val createReleaseEntry = Sw360ApiClientDataFactory.createSw360Release()
            createReleaseEntry.setRootValue("name", name)
            createReleaseEntry.setRootValue("version", version)
            createReleaseEntry.setRootValue("componentId", componentId)
            val createReleaseResult = releaseClient.createRelease(createReleaseEntry.getJsonAsString())
            val releaseId = createReleaseResult.getId()
            withClue("createRelease createReleaseResult=${createReleaseResult.getJsonAsString()}") {
                releaseId.isNotEmpty() shouldBe true
            }

            // Retrieve the test release from SW360.
            val getReleaseResult = releaseClient.getRelease(releaseId)
            withClue("getRelease getReleaseResult=${getReleaseResult.getJsonAsString()}") {
                getReleaseResult.getId() shouldBe releaseId
                getReleaseResult.getRootValue("name") shouldBe name
                getReleaseResult.getRootValue("version") shouldBe version
                getReleaseResult.getComponentId() shouldBe componentId
            }

            // Retrieve all test release from SW360.
            val getReleasesResult = releaseClient.getReleases()
            withClue("getReleases") {
                getReleasesResult.isNotEmpty() shouldBe true
            }
            
            // Retrieve the ID map using the release's name and version as the key.
            val releaseNameIdMap = releaseClient.getSw360ReleaseNameIdMap()
            withClue("getSw360ReleaseNameIdMap releaseNameIdMap=${releaseNameIdMap.toString()}") {
                releaseNameIdMap.containsKey(Pair(name.lowercase(), version.lowercase())) shouldBe true
                releaseNameIdMap.get(Pair(name.lowercase(), version.lowercase())) shouldBe releaseId
            }

            // Update the test release in SW360.
            val updateReleaseEntry = Sw360ApiClientDataFactory.createSw360Release()
            updateReleaseEntry.setRootValue("version", updatedVersion)
            val updateReleaseResult = releaseClient.updateRelease(releaseId, updateReleaseEntry.getJsonAsString())
            withClue("updateRelease updateReleaseResult=${updateReleaseResult.getJsonAsString()}") {
                updateReleaseResult.getId() shouldBe releaseId
                updateReleaseResult.getRootValue("name") shouldBe name
                updateReleaseResult.getRootValue("version") shouldBe updatedVersion
                updateReleaseResult.getComponentId() shouldBe componentId
            }

            val updateReleaseResult2 = releaseClient.getRelease(releaseId)
            withClue("getRelease updateReleaseResult2=${updateReleaseResult2.getJsonAsString()}") {
                updateReleaseResult2.getId() shouldBe releaseId
                updateReleaseResult2.getRootValue("name") shouldBe name
                updateReleaseResult2.getRootValue("version") shouldBe updatedVersion
                updateReleaseResult2.getComponentId() shouldBe componentId
            }

            // Post attachment files
            val totalFileNumber = 2
            val cliFileName = "ort-cli_Gem-dummy@1.1.1.xml"
            val srcFileName = "ort-source-archive_Gem-dummy@1.1.1.zip"

            val cliFile = getAssetFile(cliFileName)
            val srcFile = getAssetFile(srcFileName)

            val cliAttacheEntry = releaseClient.attachComponentLicenseInfoXml(releaseId, cliFile)
            withClue("attachComponentLicenseInfoXml cliAttacheEntry=${cliAttacheEntry.getJsonAsString()}") {
                val attachmentEntries = cliAttacheEntry.getEmbeddedAttachmentEntries()
                attachmentEntries.size shouldBe 1
                attachmentEntries[0].getFilename() shouldBe cliFileName
                attachmentEntries[0].getAttachmentType() shouldBe "COMPONENT_LICENSE_INFO_XML"
            }

            val srcAttacheEntry = releaseClient.attachSourceCode(releaseId, srcFile)
            withClue("attachSourceCode srcAttacheEntry=${srcAttacheEntry.getJsonAsString()}") {
                val attachmentEntries = srcAttacheEntry.getEmbeddedAttachmentEntries()
                attachmentEntries.size shouldBe 2
                attachmentEntries.forEach { entry -> 
                    val filename = entry.getFilename()
                    val attachmentType = entry.getAttachmentType()
                    if (filename == cliFileName) {
                        attachmentType shouldBe "COMPONENT_LICENSE_INFO_XML"
                    } else if (filename == srcFileName) {
                        attachmentType shouldBe "SOURCE"
                    } else {
                        fail("${filename} is an unexpected filename.")
                    }
                }
            }

            // Delete attachment files
            val attachmentIds = releaseClient.getRelease(releaseId).getEmbeddedAttachmentEntries().map { it.getId() }
            withClue("attachmentIds") {
                attachmentIds.size shouldBe totalFileNumber
            }
            attachmentIds.forEachIndexed { index, attachmentId ->
                val deleteAttachmentEntry = releaseClient.deleteAttachment(releaseId, attachmentId)
                withClue("deleteAttachment deleteAttachmentEntry=${deleteAttachmentEntry.getJsonAsString()}") {
                    deleteAttachmentEntry.getEmbeddedAttachmentEntries().size shouldBe (totalFileNumber - index - 1)
                }
            }

            // Delete the test release from SW360.
            releaseClient.deleteRelease(releaseId)
            shouldThrow<IOException> {
                releaseClient.getRelease(releaseId)
            }
            val releaseNameIdMap2 = releaseClient.getSw360ReleaseNameIdMap()
            withClue("getRelease releaseNameIdMap2=${releaseNameIdMap2.toString()}") {
                releaseNameIdMap2.containsKey(Pair(name.lowercase(), version.lowercase())) shouldBe false
                releaseNameIdMap2.containsKey(Pair(name.lowercase(), updatedVersion.lowercase())) shouldBe false
            }

            // Delete the test component from SW360.
            componentClient.deleteComponent(componentId)
            shouldThrow<IOException> {
                componentClient.getComponent(componentId)
            }

        }
    }

    
    "Testing release link operations for SW360 Release." {
        if (Sw360LicenseReportTestUtil.hasStorageConfig()) {
            
            val testConfig = Sw360LicenseReportTestUtil.getConfig()
            val sw360StrageConfig = Sw360LicenseReportTestUtil.getSw360StorageConfiguration(testConfig)

            // Create api clients
            val componentClient = Sw360ComponentApiClient(sw360StrageConfig)
            val releaseClient = Sw360ReleaseApiClient(sw360StrageConfig)

            // String literal for testing
            val name = "ORT_RELEASE_LINK_TEST_BY_API"
            val componentType = "OSS"
            val version1 = "1.00"
            val version2 = "2.00"
            val version3 = "3.00"

            // Create a test Component to SW360.
            val createComponentEntry = Sw360ApiClientDataFactory.createSw360Component()
            createComponentEntry.setRootValue("name", name)
            createComponentEntry.setRootValue("componentType", componentType)
            val createComponentResult = componentClient.createComponent(createComponentEntry.getJsonAsString())
            val componentId = createComponentResult.getId()
            withClue("createComponent createComponentResult=${createComponentResult.getJsonAsString()}") {
                componentId.isNotEmpty() shouldBe true
            }

            // Create a test Release (version1) to SW360.
            val createReleaseEntry1 = Sw360ApiClientDataFactory.createSw360Release()
            createReleaseEntry1.setRootValue("name", name)
            createReleaseEntry1.setRootValue("version", version1)
            createReleaseEntry1.setRootValue("componentId", componentId)
            val createReleaseResult1 = releaseClient.createRelease(createReleaseEntry1.getJsonAsString())
            val releaseId1 = createReleaseResult1.getId()
            withClue("createRelease 1 createReleaseResult1=${createReleaseResult1.getJsonAsString()}") {
                releaseId1.isNotEmpty() shouldBe true
            }

            // Create a test Release (version2) to SW360.
            val createReleaseEntry2 = Sw360ApiClientDataFactory.createSw360Release()
            createReleaseEntry2.setRootValue("name", name)
            createReleaseEntry2.setRootValue("version", version2)
            createReleaseEntry2.setRootValue("componentId", componentId)
            val createReleaseResult2 = releaseClient.createRelease(createReleaseEntry2.getJsonAsString())
            val releaseId2 = createReleaseResult2.getId()
            withClue("createRelease 2 createReleaseResult2=${createReleaseResult2.getJsonAsString()}") {
                releaseId2.isNotEmpty() shouldBe true
            }

            // Create a test Release (version3) to SW360.
            val createReleaseEntry3 = Sw360ApiClientDataFactory.createSw360Release()
            createReleaseEntry3.setRootValue("name", name)
            createReleaseEntry3.setRootValue("version", version3)
            createReleaseEntry3.setRootValue("componentId", componentId)
            val createReleaseResult3 = releaseClient.createRelease(createReleaseEntry3.getJsonAsString())
            val releaseId3 = createReleaseResult3.getId()
            withClue("createRelease 3 createReleaseResult3=${createReleaseResult3.getJsonAsString()}") {
                releaseId3.isNotEmpty() shouldBe true
            }

            // Create release links. Release (version3) to Release (version1) and Release (version2)
            val relationship = mapOf<String, String>(
                releaseId1 to "CONTAINED",
                releaseId2 to "CONTAINED"
            )

            releaseClient.createReleaseRelationship(releaseId3, relationship)
            // This endpoint does not have a response body.
            /*
            val createReleaseRelationshipResult = releaseClient.createReleaseRelationship(releaseId3, relationship)
            withClue("createReleaseRelationship createReleaseRelationshipResult=${createReleaseRelationshipResult.getJsonAsString()}") {
                createReleaseRelationshipResult.getId() shouldBe releaseId3
                val resultRelationship = createReleaseRelationshipResult.getReleaseRelationship()
                resultRelationship.size shouldBe 2
                resultRelationship.containsKey(releaseId1)
                resultRelationship.get(releaseId1) shouldBe "CONTAINED"
                resultRelationship.containsKey(releaseId2)
                resultRelationship.get(releaseId2) shouldBe "CONTAINED"
            }
            */

            val getReleaseResult = releaseClient.getRelease(releaseId3)
            withClue("getRelease getReleaseResult=${getReleaseResult.getJsonAsString()}") {
                getReleaseResult.getId() shouldBe releaseId3
                val resultRelationship = getReleaseResult.getReleaseRelationship()
                resultRelationship.size shouldBe 2
                resultRelationship.containsKey(releaseId1)
                resultRelationship.get(releaseId1) shouldBe "CONTAINED"
                resultRelationship.containsKey(releaseId2)
                resultRelationship.get(releaseId2) shouldBe "CONTAINED"
            }

            // Delete the test releases from SW360.
            releaseClient.deleteRelease(releaseId3)
            shouldThrow<IOException> {
                releaseClient.getRelease(releaseId3)
            }

            releaseClient.deleteRelease(releaseId2)
            shouldThrow<IOException> {
                releaseClient.getRelease(releaseId2)
            }

            releaseClient.deleteRelease(releaseId1)
            shouldThrow<IOException> {
                releaseClient.getRelease(releaseId1)
            }

            // Delete the test component from SW360.
            componentClient.deleteComponent(componentId)
            shouldThrow<IOException> {
                componentClient.getComponent(componentId)
            }
            
        }
    }

})