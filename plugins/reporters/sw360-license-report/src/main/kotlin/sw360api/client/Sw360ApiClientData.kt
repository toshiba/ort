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
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.apache.logging.log4j.kotlin.logger


open class Sw360JsonHolder(val jsonNode: JsonNode) {

    companion object {

        fun getValueAsString(objectNode: ObjectNode, key:String): String {
            val valueNode = getValueNode(objectNode, key)
            return valueNode?.let {
                it.asText()
            } ?: run {
                throw IOException("Sw360JsonHolder.getValueAsString the valueNode of key=${key} is null")
            }
        }

        fun getValueAsInt(objectNode: ObjectNode, key:String): Int {
            val valueNode = getValueNode(objectNode, key)
            return valueNode?.let {
                it.asInt()
            } ?: run {
                throw IOException("Sw360JsonHolder.getValueAsInt the valueNode of key=${key} is null")
            }
        }

        fun getValueAsBoolean(objectNode: ObjectNode, key:String): Boolean {
            val valueNode = getValueNode(objectNode, key)
            return valueNode?.let {
                it.asBoolean()
            } ?: run {
                throw IOException("Sw360JsonHolder.getValueAsBoolean the valueNode of key=${key} is null")
            }
        }

        fun getValueNode(objectNode: ObjectNode, key:String): JsonNode? {
            return if (objectNode.has(key)) {
                objectNode.get(key)
            } else {
                logger.warn {
                    "Sw360JsonHolder.getValueNode The specified key=${key} does not exist in this node."
                }
                null
            }
        }

        fun setValueNode(objectNode: ObjectNode, key:String, value: Any) {
            when (value) {
                is String -> objectNode.put(key, value)
                is Int -> objectNode.put(key, value)
                is Boolean -> objectNode.put(key, value)
                else -> objectNode.putPOJO(key, value)
            }
        }
    }

    fun getJsonAsString(): String = jsonNode.toString()

}

open class Sw360ObjectNodeHolder(jsonNode: JsonNode): Sw360JsonHolder(jsonNode) {

    fun getRootNode(): ObjectNode {
        return jsonNode as ObjectNode
    }

    fun getRootValue(key: String): String {
        val rootNode = getRootNode()
        return getValueAsString(rootNode, key)
    }

    fun setRootValue(key:String, value: Any) {
        val rootNode = getRootNode()
        setValueNode(rootNode, key, value)
    }

    fun getSelfUrl(): String {
        val url = jsonNode["_links"]?.get("self")?.get("href")?.asText()?.takeIf { it.isNotEmpty() }
        return url ?: throw IOException("Sw360ObjectNodeHolder.getSelfUrl the self link url is null")
    }

    fun getIdFromSelfUrl(url: String): String {
        return url.substringAfterLast('/').takeIf { it.isNotEmpty() }?: throw IllegalArgumentException("Invalid URL format. url=${url}")
    }

    fun getId(): String {
        val selfUrl = getSelfUrl()

        return getIdFromSelfUrl(selfUrl)
    }

    fun getEmbeddedAttachmentEntries(): List<Sw360EmbeddedAttachment> {
        val entryList = mutableListOf<Sw360EmbeddedAttachment>()
        val arrayNode = getEmbeddedAttachments()
        for (node in arrayNode) {
            val resultEntry = Sw360ApiClientDataFactory.createSw360EmbeddedAttachment(node)
            entryList.add(resultEntry)
        }
        return entryList
    }

    private fun getEmbeddedAttachments(): JsonNode {
        val embeddedNode = jsonNode.path("_embedded")
        if (embeddedNode.isMissingNode || embeddedNode.isNull) {
            return Sw360ApiClientDataFactory.createArrayNode()
        }

        val documentsNode = embeddedNode.path("sw360:attachments")
        if (documentsNode.isMissingNode || documentsNode.isNull) {
            return Sw360ApiClientDataFactory.createArrayNode()
        }

        return documentsNode
    }

}

open class Sw360Project(jsonNode: JsonNode): Sw360ObjectNodeHolder(jsonNode) {

    fun getReleaseRelationship(): List<String> {
        val arrayNode = jsonNode.path("linkedReleases")
        if (arrayNode.isMissingNode || arrayNode.isNull) {
            return listOf<String>()
        }
        val releaseIds = mutableListOf<String>()
        for (node in arrayNode) {
            val releaseUrl = getValueAsString(node as ObjectNode, "release")
            val releaseId = getIdFromSelfUrl(releaseUrl)
            releaseIds.add(releaseId)
        }
        return releaseIds
    }

}

open class Sw360ProjectSearch(jsonNode: JsonNode): Sw360ObjectNodeHolder(jsonNode) {

    fun getSearchResults(): List<Sw360ProjectSearchResult>{
        val entryList = mutableListOf<Sw360ProjectSearchResult>()
        val arrayNode = getEmbeddedProjects()
        for (node in arrayNode) {
            val resultEntry = Sw360ApiClientDataFactory.createSw360ProjectSearchResult(node)
            entryList.add(resultEntry)
        }
        return entryList
    }

    private fun getEmbeddedProjects(): JsonNode {
        val embeddedNode = jsonNode.path("_embedded")
        if (embeddedNode.isMissingNode || embeddedNode.isNull) {
            return Sw360ApiClientDataFactory.createArrayNode()
        }

        val documentsNode = embeddedNode.path("sw360:projects")
        if (documentsNode.isMissingNode || documentsNode.isNull) {
            return Sw360ApiClientDataFactory.createArrayNode()
        }

        return documentsNode
    }

    fun getPageInfo(): Map<String, Int> {
        val pageNode = jsonNode.path("page") as ObjectNode
        if (pageNode.isMissingNode || pageNode.isNull) {
            return mapOf<String, Int>()
        }

        val size = getValueAsInt(pageNode, "size")
        val totalElements = getValueAsInt(pageNode, "totalElements")
        val totalPages = getValueAsInt(pageNode, "totalPages")
        val number = getValueAsInt(pageNode, "number")

        return mapOf<String, Int>(
            "size" to size,
            "totalElements" to totalElements,
            "totalPages" to totalPages,
            "number" to number
        )
    }

}

open class Sw360ProjectSearchResult(jsonNode: JsonNode): Sw360ObjectNodeHolder(jsonNode) {}

open class Sw360Release(jsonNode: JsonNode): Sw360ObjectNodeHolder(jsonNode) {

    fun getComponentUrl(): String {
        val url = jsonNode["_links"]?.get("sw360:component")?.get("href")?.asText()?.takeIf { it.isNotEmpty() }
        return url ?: throw IOException("Sw360Release.getComponentUrl the component link url is null")
    }

    fun getComponentId(): String {
        val selfUrl = getComponentUrl()

        return getIdFromSelfUrl(selfUrl)
    }

    fun getReleaseRelationship(): Map<String, String> {
        val relationshipNode = jsonNode.path("releaseIdToRelationship")
        if (relationshipNode.isMissingNode || relationshipNode.isNull) {
            return mapOf<String, String>()
        }
        return relationshipNode.fields().asSequence().associate { it.key to it.value.asText() }
    }

}

open class Sw360ReleaseSearch(jsonNode: JsonNode): Sw360ObjectNodeHolder(jsonNode) {

    fun getSearchResults(): List<Sw360ReleaseSearchResult>{
        val entryList = mutableListOf<Sw360ReleaseSearchResult>()
        val arrayNode = getEmbeddedReleases()
        for (node in arrayNode) {
            val resultEntry = Sw360ApiClientDataFactory.createSw360ReleaseSearchResult(node)
            entryList.add(resultEntry)
        }
        return entryList
    }

    private fun getEmbeddedReleases(): JsonNode {
        val embeddedNode = jsonNode.path("_embedded")
        if (embeddedNode.isMissingNode || embeddedNode.isNull) {
            return Sw360ApiClientDataFactory.createArrayNode()
        }

        val documentsNode = embeddedNode.path("sw360:releases")
        if (documentsNode.isMissingNode || documentsNode.isNull) {
            return Sw360ApiClientDataFactory.createArrayNode()
        }

        return documentsNode
    }

}

open class Sw360ReleaseSearchResult(jsonNode: JsonNode): Sw360ObjectNodeHolder(jsonNode) {}

open class Sw360Component(jsonNode: JsonNode): Sw360ObjectNodeHolder(jsonNode) {}

open class Sw360ComponentSearch(jsonNode: JsonNode): Sw360ObjectNodeHolder(jsonNode) {

    fun getSearchResults(): List<Sw360ComponentSearchResult>{
        val entryList = mutableListOf<Sw360ComponentSearchResult>()
        val arrayNode = getEmbeddedComponents()
        for (node in arrayNode) {
            val resultEntry = Sw360ApiClientDataFactory.createSw360ComponentSearchResult(node)
            entryList.add(resultEntry)
        }
        return entryList
    }

    private fun getEmbeddedComponents(): JsonNode {
        val embeddedNode = jsonNode.path("_embedded")
        if (embeddedNode.isMissingNode || embeddedNode.isNull) {
            return Sw360ApiClientDataFactory.createArrayNode()
        }

        val documentsNode = embeddedNode.path("sw360:components")
        if (documentsNode.isMissingNode || documentsNode.isNull) {
            return Sw360ApiClientDataFactory.createArrayNode()
        }

        return documentsNode
    }
}

open class Sw360ComponentSearchResult(jsonNode: JsonNode): Sw360ObjectNodeHolder(jsonNode) {}

open class Sw360EmbeddedAttachment(jsonNode: JsonNode): Sw360ObjectNodeHolder(jsonNode) {
    fun getFilename(): String = getRootValue("filename")
    fun getSha1(): String = getRootValue("sha1")
    fun getAttachmentType(): String = getRootValue("attachmentType")
}
