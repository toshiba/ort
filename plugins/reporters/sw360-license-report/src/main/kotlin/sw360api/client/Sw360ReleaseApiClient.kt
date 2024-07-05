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

import java.io.File
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


open class Sw360ReleaseApiClient(config: Sw360StorageConfiguration): Sw360BaseApiClient(config) {

    fun createRelease(bodyText: String): Sw360Release {
        val url = "${getBaseUrl()}/releases"
        val requestBody = getRequestBodyFromString(bodyText)
        val result = post(url, requestBody)

        if (result.isSuccessful) {
            return Sw360ApiClientDataFactory.createSw360Release(result.responseBody)
        } else {
            val errorMessage = "Sw360ReleaseApiClient.createRelease " + 
                "bodyText='${bodyText}', " + 
                "result='${result.dump()}'"
            logger.error { errorMessage }
            throw IOException(errorMessage)
        }
    }

    fun getRelease(id: String): Sw360Release {
        val url = "${getBaseUrl()}/releases/${id}"
        val result = get(url)

        if (result.isSuccessful) {
            return Sw360ApiClientDataFactory.createSw360Release(result.responseBody)
        } else {
            val errorMessage = "Sw360ReleaseApiClient.getRelease " + 
                "id=${id}, " + 
                "result='${result.dump()}'"
            logger.error { errorMessage }
            throw IOException(errorMessage)
        }
    }

    fun getReleases(): List<Sw360ReleaseSearchResult> {
        val url = "${getBaseUrl()}/releases"
        val result = get(url)

        if (result.isSuccessful) {
            val searchEntry = Sw360ApiClientDataFactory.createSw360ReleaseSearch(result.responseBody)
            return searchEntry.getSearchResults()
        } else {
            val errorMessage = "Sw360ReleaseApiClient.getReleases " + 
                "result='${result.dump()}'"
            logger.error { errorMessage }
            throw IOException(errorMessage)
        }
       
    }

    fun getSw360ReleaseNameIdMap(): Map<Pair<String, String>, String> {
        return getReleases().associate { 
            val name = it.getRootValue("name").lowercase()
            val version = it.getRootValue("version").lowercase()
            val id = it.getId()
            Pair(name, version) to id
        }
    }

    fun updateRelease(id: String, bodyText: String): Sw360Release {
        val url = "${getBaseUrl()}/releases/${id}"
        val requestBody = getRequestBodyFromString(bodyText)
        val result = patch(url, requestBody)

        if (result.isSuccessful) {
            return Sw360ApiClientDataFactory.createSw360Release(result.responseBody)
        } else {
            val errorMessage = "Sw360ProjectApiClient.updateRelease " + 
                "id=${id}, " + 
                "bodyText=${bodyText}, "
                "result='${result.dump()}'"
            logger.error { errorMessage }
            throw IOException(errorMessage)
        }
    }

    fun deleteRelease(id: String): Sw360Release {
        val url = "${getBaseUrl()}/releases/${id}"
        val result = delete(url)

        if (result.isSuccessful) {
            return Sw360ApiClientDataFactory.createSw360Release(result.responseBody)
        } else {
            val errorMessage = "Sw360ProjectApiClient.deleteRelease " + 
                "id=${id}, " + 
                "result='${result.dump()}'"
            logger.error { errorMessage }
            throw IOException(errorMessage)
        }
    }

    fun createReleaseRelationship(id: String, relationship: Map<String, String>): Sw360Release {
        val url = "${getBaseUrl()}/releases/${id}/releases"
        val requestBody = getRequestBodyFromStringMap(relationship)

        val result = post(url, requestBody)

        if (result.isSuccessful) {
            // This endpoint does not have a response body.
            return Sw360ApiClientDataFactory.createSw360Release(result.responseBody)
        } else {
            val errorMessage = "Sw360ReleaseApiClient.createReleaseRelationship " + 
                "relationship='${relationship.toString()}', " + 
                "result='${result.dump()}'"
            logger.error { errorMessage }
            throw IOException(errorMessage)
        }
    }

    fun attachComponentLicenseInfoXml(id: String, file: File): Sw360Release {
        val url = "${getBaseUrl()}/releases/${id}/attachments"
        val mediaType = "application/xml"
        val attachmentType = "COMPONENT_LICENSE_INFO_XML"
        
        val result = postAttachment(url, file, mediaType, attachmentType)

        if (result.isSuccessful) {
            return Sw360ApiClientDataFactory.createSw360Release(result.responseBody)
        } else {
            val errorMessage = "Sw360ReleaseApiClient.attachComponentLicenseInfoXml " + 
                "id=${id}, " + 
                "result='${result.dump()}'"
            logger.error { errorMessage }
            throw IOException(errorMessage)
        }
    }
    
    fun attachSourceCode(id: String, file: File): Sw360Release {
        val url = "${getBaseUrl()}/releases/${id}/attachments"
        val mediaType = "application/zip"
        val attachmentType = "SOURCE"
        
        val result = postAttachment(url, file, mediaType, attachmentType)

        if (result.isSuccessful) {
            return Sw360ApiClientDataFactory.createSw360Release(result.responseBody)
        } else {
            val errorMessage = "Sw360ReleaseApiClient.attachSourceCode " + 
                "id=${id}, " + 
                "result='${result.dump()}'"
            logger.error { errorMessage }
            throw IOException(errorMessage)
        }
    }

    fun attachLicenseText(id: String, file: File): Sw360Release {
        val url = "${getBaseUrl()}/releases/${id}/attachments"
        val mediaType = "text/plain"
        val attachmentType = "DOCUMENT"
        
        val result = postAttachment(url, file, mediaType, attachmentType)

        if (result.isSuccessful) {
            return Sw360ApiClientDataFactory.createSw360Release(result.responseBody)
        } else {
            val errorMessage = "Sw360ReleaseApiClient.attachLicenseText " + 
                "id=${id}, " + 
                "result='${result.dump()}'"
            logger.error { errorMessage }
            throw IOException(errorMessage)
        }
    }

    fun deleteAttachment(releaseId: String, attachmentId: String): Sw360Release {
        val url = "${getBaseUrl()}/releases/${releaseId}/attachments/${attachmentId}"
        val result = delete(url)

        if (result.isSuccessful) {
            return Sw360ApiClientDataFactory.createSw360Release(result.responseBody)
        } else {
            val errorMessage = "Sw360ProjectApiClient.deleteAttachment " + 
                "id=${releaseId}, " + 
                "attachmentId=${attachmentId}, " +
                "result='${result.dump()}'"
            logger.error { errorMessage }
            throw IOException(errorMessage)
        }
    }

}