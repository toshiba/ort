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


open class Sw360ProjectApiClient(config: Sw360StorageConfiguration): Sw360BaseApiClient(config) {

    fun createProject(bodyText: String): Sw360Project {
        val url = "${getBaseUrl()}/projects"
        val requestBody = getRequestBodyFromString(bodyText)
        val result = post(url, requestBody)

        if (result.isSuccessful) {
            return Sw360ApiClientDataFactory.createSw360Project(result.responseBody)
        } else {
            val errorMessage = "Sw360ProjectApiClient.createProject " + 
                "bodyText='${bodyText}', " + 
                "result='${result.dump()}'"
            logger.error { errorMessage }
            throw IOException(errorMessage)
        }
    }

    fun getProject(id: String): Sw360Project {
        val url = "${getBaseUrl()}/projects/${id}"
        val result = get(url)

        if (result.isSuccessful) {
            return Sw360ApiClientDataFactory.createSw360Project(result.responseBody)
        } else {
            val errorMessage = "Sw360ProjectApiClient.getProject " + 
                "id=${id}, " + 
                "result='${result.dump()}'"
            logger.error { errorMessage }
            throw IOException(errorMessage)
        }
    }

    fun getProjects(): List<Sw360ProjectSearchResult> {
        val url = "${getBaseUrl()}/projects"
        val result = get(url)

        if (result.isSuccessful) {
            val searchEntry = Sw360ApiClientDataFactory.createSw360ProjectSearch(result.responseBody)

            val pageInfo = searchEntry.getPageInfo()
            if (pageInfo.isEmpty()) {
                return searchEntry.getSearchResults()
            } else {
                val totalPages = pageInfo.getValue("totalPages")
                val totalElements = pageInfo.getValue("totalElements")
                val resultEntryList = mutableListOf<Sw360ProjectSearchResult>()

                val firstResultEntries = searchEntry.getSearchResults()
                resultEntryList.addAll(firstResultEntries)
                if (0 < totalPages) {
                    for (number in 1..totalPages) {
                        val pageUrl = "${getBaseUrl()}/projects?page=${number}"
                        val pageResult = get(pageUrl)
                        if (pageResult.isSuccessful) {
                            val pageSearchEntry = Sw360ApiClientDataFactory.createSw360ProjectSearch(pageResult.responseBody)

                            val newPageInfo = pageSearchEntry.getPageInfo()
                            if (newPageInfo.isEmpty()) {
                                val errorMessage = "Sw360ProjectApiClient.getProjects newPageInfo is empty"
                                logger.error { errorMessage }
                                throw IOException(errorMessage)

                            } else if (newPageInfo.getValue("number") != number) {
                                val errorMessage = "Sw360ProjectApiClient.getProjects " + 
                                    "The value of number does not match. Expected value: ${number}, " + 
                                    "actual value: ${newPageInfo.getValue("number")}."
                                logger.error { errorMessage }
                                throw IOException(errorMessage)
                            }
                            val pageResultEntries = pageSearchEntry.getSearchResults()
                            resultEntryList.addAll(pageResultEntries)
                        } else {
                            val errorMessage = "Sw360ProjectApiClient.getProjects " + 
                                "pageResult='${result.dump()}'"
                            logger.error { errorMessage }
                            throw IOException(errorMessage)
                        }
                    }
                }

                if (resultEntryList.size != totalElements) {
                    val errorMessage = "Sw360ProjectApiClient.getProjects " + 
                        "The resultsList size does not match. Expected value: ${totalElements}, " + 
                        "actual value: ${resultEntryList.size}."
                    logger.error { errorMessage }
                    throw IOException(errorMessage)
                }

                return resultEntryList
            }

        } else {
            val errorMessage = "Sw360ProjectApiClient.getProjects " + 
                "result='${result.dump()}'"
            logger.error { errorMessage }
            throw IOException(errorMessage)
        }
    }

    fun getSw360ProjectNameIdMap(): Map<Pair<String, String>, String> {
        return getProjects().associate { 
            val name = it.getRootValue("name").lowercase()
            val version = if (it.getRootNode().has("version")) {
                it.getRootValue("version").lowercase()
            } else {
                ""
            }
            val id = it.getId()
            Pair(name, version) to id
        }
    }

    fun updateProject(id: String, bodyText: String): Sw360Project {
        val url = "${getBaseUrl()}/projects/${id}"
        val requestBody = getRequestBodyFromString(bodyText)
        val result = patch(url, requestBody)

        if (result.isSuccessful) {
            return Sw360ApiClientDataFactory.createSw360Project(result.responseBody)
        } else {
            val errorMessage = "Sw360ProjectApiClient.updateProject " + 
                "id=${id}, " + 
                "bodyText=${bodyText}, "
                "result='${result.dump()}'"
            logger.error { errorMessage }
            throw IOException(errorMessage)
        }
    }

    fun updateDependencyNetwork(id: String, bodyText: String): Sw360Project {
        val url = "${getBaseUrl()}/projects/network/${id}"
        val requestBody = getRequestBodyFromString(bodyText)
        val result = patch(url, requestBody)

        if (result.isSuccessful) {
            return Sw360ApiClientDataFactory.createSw360Project(result.responseBody)
        } else {
            val errorMessage = "Sw360ProjectApiClient.updateDependencyNetwork " + 
                "id=${id}, " + 
                "bodyText=${bodyText}, "
                "result='${result.dump()}'"
            logger.error { errorMessage }
            throw IOException(errorMessage)
        }
    }

    fun deleteProject(id: String): Sw360Project {
        val url = "${getBaseUrl()}/projects/${id}"
        val result = delete(url)

        if (result.isSuccessful) {
            return Sw360ApiClientDataFactory.createSw360Project(result.responseBody)
        } else {
            val errorMessage = "Sw360ProjectApiClient.deleteProject " + 
                "id=${id}, " + 
                "result='${result.dump()}'"
            logger.error { errorMessage }
            throw IOException(errorMessage)
        }
    }

    fun createReleaseRelationship(id: String, releaseIds: List<String>): Sw360Project {
        val url = "${getBaseUrl()}/projects/${id}/releases"
        val requestBody = getRequestBodyFromStringList(releaseIds)

        val result = post(url, requestBody)

        if (result.isSuccessful) {
            return Sw360ApiClientDataFactory.createSw360Project(result.responseBody)
        } else {
            val errorMessage = "Sw360ProjectApiClient.createReleaseRelationship " + 
                "releaseIds='${releaseIds.toString()}', " + 
                "result='${result.dump()}'"
            logger.error { errorMessage }
            throw IOException(errorMessage)
        }
    }

}