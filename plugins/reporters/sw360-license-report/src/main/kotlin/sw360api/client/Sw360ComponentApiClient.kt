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

import java.io.IOException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.apache.logging.log4j.kotlin.logger
import org.ossreviewtoolkit.model.config.Sw360StorageConfiguration


open class Sw360ComponentApiClient(config: Sw360StorageConfiguration): Sw360BaseApiClient(config) {

    fun createComponent(bodyText: String): Sw360Component {
        val url = "${getBaseUrl()}/components"
        val requestBody = getRequestBodyFromString(bodyText)
        val result = post(url, requestBody)

        if (result.isSuccessful) {
            return Sw360ApiClientDataFactory.createSw360Component(result.responseBody)
        } else {
            val errorMessage = "Sw360ComponentApiClient.createComponent " + 
                "bodyText='${bodyText}', " + 
                "result='${result.dump()}'"
            logger.error { errorMessage }
            throw IOException(errorMessage)
        }
    }

    fun getComponent(id: String): Sw360Component {
        val url = "${getBaseUrl()}/components/${id}"
        val result = get(url)

        if (result.isSuccessful) {
            return Sw360ApiClientDataFactory.createSw360Component(result.responseBody)
        } else {
            val errorMessage = "Sw360ComponentApiClient.getComponent " + 
                "id=${id}, " + 
                "result='${result.dump()}'"
            logger.error { errorMessage }
            throw IOException(errorMessage)
        }
    }

    fun getComponents(): List<Sw360ComponentSearchResult> {
        val url = "${getBaseUrl()}/components"
        val result = get(url)

        if (result.isSuccessful) {
            val searchEntry = Sw360ApiClientDataFactory.createSw360ComponentSearch(result.responseBody)
            return searchEntry.getSearchResults()
        } else {
            val errorMessage = "Sw360ComponentApiClient.getComponents " + 
                "result='${result.dump()}'"
            logger.error { errorMessage }
            throw IOException(errorMessage)
        }
    }

    fun getSw360ComponentNameIdMap(): Map<String, String> {
        return getComponents().associate { 
            val name = it.getRootValue("name").lowercase()
            val id = it.getId()
            name to id
        }
    }

    fun updateComponent(id: String, bodyText: String): Sw360Component {
        val url = "${getBaseUrl()}/components/${id}"
        val requestBody = getRequestBodyFromString(bodyText)
        val result = patch(url, requestBody)

        if (result.isSuccessful) {
            return Sw360ApiClientDataFactory.createSw360Component(result.responseBody)
        } else {
            val errorMessage = "Sw360ComponentApiClient.updateComponent " + 
                "id=${id}, " + 
                "bodyText=${bodyText}, "
                "result='${result.dump()}'"
            logger.error { errorMessage }
            throw IOException(errorMessage)
        }
    }

    fun deleteComponent(id: String): Sw360Component {
        val url = "${getBaseUrl()}/components/${id}"
        val result = delete(url)

        if (result.isSuccessful) {
            return Sw360ApiClientDataFactory.createSw360Component(result.responseBody)
        } else {
            val errorMessage = "Sw360ComponentApiClient.deleteComponent " + 
                "id=${id}, " + 
                "result='${result.dump()}'"
            logger.error { errorMessage }
            throw IOException(errorMessage)
        }
    }

}