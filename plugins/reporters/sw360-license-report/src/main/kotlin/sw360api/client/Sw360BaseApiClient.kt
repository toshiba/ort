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
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import org.apache.logging.log4j.kotlin.logger
import org.ossreviewtoolkit.model.config.Sw360StorageConfiguration


open class Sw360BaseApiClient(val config: Sw360StorageConfiguration) {

    class ApiResult(
        val isSuccessful: Boolean,
        val statusCode: Int,
        val responseBody: String
    ) {
        fun dump(): String {
            return "ApiResult: " + 
                "isSuccessful=${isSuccessful}, " + 
                "statusCode=${statusCode}, " + 
                "responseBody=\"${responseBody}\""
        }
    }

    private val client = OkHttpClient()

    fun getToken(): String {
        val token = config.token
        if (token.isEmpty()) {
            val errorMessage= "SW360 authentication token string is missing."
            logger.error { errorMessage }
            throw IOException(errorMessage)
        }
        return token
    }


    fun getBaseUrl(): String {
        val baseUrl = config.restUrl
        if (baseUrl.isEmpty()) {
            val errorMessage= "SW360 base url string is missing."
            logger.error { errorMessage }
            throw IOException(errorMessage)
        }
        return baseUrl.removeSuffix("/")
    }


    fun get(url: String): ApiResult {
        val token = getToken()
        val request = Request.Builder()
            .url(url)
            .get()
            .addHeader("Authorization", "Bearer ${token}")
            .build()

        return executeRequest(request)
    }


    fun post(url: String, requestBody: RequestBody): ApiResult {
        val token = getToken()
        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .addHeader("Content-Type", "application/json")
            .addHeader("Authorization", "Bearer ${token}")
            .addHeader("Cookie", "COOKIE_SUPPORT=true; GUEST_LANGUAGE_ID=en_US")
            .build()
 
        return executeRequest(request)
    }

    fun postAttachment(url: String, file: File, mediaType: String, attachmentType: String): ApiResult {

        val fileRequestBody = file.asRequestBody(mediaType.toMediaType())

        val attachmentText = "{\"filename\": \"${file.name}\", \"attachmentType\": \"${attachmentType}\"}"
        val attachmentRequestBody = attachmentText.toRequestBody("application/json".toMediaType())

        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("file", file.name, fileRequestBody)
            .addFormDataPart("attachment", attachmentText, attachmentRequestBody)
            .build()

        val token = getToken()
        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .addHeader("Content-Type", "multipart/form-data")
            .addHeader("Content-Type", "multipart/mixed")
            .addHeader("Authorization", "Bearer ${token}")
            .build()
 
        return executeRequest(request)
    }

    fun delete(url: String): ApiResult {
        val token = getToken()
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer ${token}")
            .delete()
            .build()

        return executeRequest(request)
    }


    fun patch(url: String, requestBody: RequestBody): ApiResult {
        val token = getToken()
        val request = Request.Builder()
            .url(url)
            .patch(requestBody)
            .addHeader("Content-Type", "application/json")
            .addHeader("Authorization", "Bearer ${token}")
            .addHeader("Cookie", "COOKIE_SUPPORT=true; GUEST_LANGUAGE_ID=en_US")
            .build()

        return executeRequest(request)
    }


    private fun executeRequest(request: Request): ApiResult {
        val response = client.newCall(request).execute()
        val responseBody = response.body?.string() ?: ""
        val statusCode = response.code

        return ApiResult(
            isSuccessful = response.isSuccessful,
            statusCode = statusCode,
            responseBody = responseBody)
    }


    fun getRequestBodyFromString(text: String): RequestBody {
        val mediaType = "application/json".toMediaType()
        return text.toRequestBody(mediaType)
    }


    fun getRequestBodyFromStringList(stringList: List<String>): RequestBody {
        val jsonString = Json.encodeToString(stringList)
        return getRequestBodyFromString(jsonString)
    }


    fun getRequestBodyFromStringMap(stringMap: Map<String, String>): RequestBody {
        val jsonString = Json.encodeToString(stringMap)
        return getRequestBodyFromString(jsonString)
    }
}