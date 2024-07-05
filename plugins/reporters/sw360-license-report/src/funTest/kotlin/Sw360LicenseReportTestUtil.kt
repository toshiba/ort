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

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue

import java.io.File
import org.ossreviewtoolkit.model.config.Sw360StorageConfiguration
import org.ossreviewtoolkit.utils.test.getAssetFile


data class Sw360LicenseReportTestConfig(
    @JsonProperty("storage") val storage: Sw360LicenseReportTestStorageConfig
)

data class Sw360LicenseReportTestStorageConfig(
    @JsonProperty("restUrl") val restUrl: String,
    @JsonProperty("authUrl") val authUrl: String,
    @JsonProperty("username") val username: String,
    @JsonProperty("password") val password: String,
    @JsonProperty("clientId") val clientId: String,
    @JsonProperty("clientPassword") val clientPassword: String,
    @JsonProperty("token") val token: String
)

object Sw360LicenseReportTestUtil {

    fun getConfigFile(): File = getAssetFile("sw360-license-report-config.json")

    fun getConfigFromFile(file: File): Sw360LicenseReportTestConfig {
        val mapper = jacksonObjectMapper()
        return mapper.readValue<Sw360LicenseReportTestConfig>(file)
    }

    fun getConfig(): Sw360LicenseReportTestConfig {
        return getConfigFromFile(getConfigFile())
    }

    fun getSw360StorageConfiguration(testConfig: Sw360LicenseReportTestConfig): Sw360StorageConfiguration {
        val storageConfig = testConfig.storage
        return Sw360StorageConfiguration(
            restUrl = storageConfig.restUrl,
            authUrl = storageConfig.authUrl,
            username = storageConfig.username,
            password = storageConfig.password,
            clientId = storageConfig.clientId,
            clientPassword = storageConfig.clientPassword,
            token = storageConfig.token
        )
    }

    fun hasStorageConfig(): Boolean {
        val configFile = getConfigFile()
        if (!configFile.exists()) {
            return false
        }
        val strageConfig = getConfigFromFile(configFile).storage
        if (strageConfig.restUrl.isEmpty()) {
            return false
        }
        return true
    }

}
