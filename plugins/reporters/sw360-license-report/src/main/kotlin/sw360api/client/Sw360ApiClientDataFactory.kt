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


object Sw360ApiClientDataFactory {

    private fun createJsonNode(jsonText: String = ""): JsonNode {
        val objectMapper = ObjectMapper()
        return if (jsonText.isNotEmpty()) {
            objectMapper.readTree(jsonText)
        } else {
            objectMapper.createObjectNode()
        }
    }

    fun createSw360ObjectNodeHolder(jsonText: String = ""): Sw360ObjectNodeHolder {
        return Sw360ObjectNodeHolder(createJsonNode(jsonText))
    }

    fun createSw360Project(jsonText: String = ""): Sw360Project {
        return Sw360Project(createJsonNode(jsonText))
    }

    fun createSw360ProjectSearch(jsonText: String = ""): Sw360ProjectSearch {
        return Sw360ProjectSearch(createJsonNode(jsonText))
    }

    fun createSw360ProjectSearchResult(jsonNode: JsonNode): Sw360ProjectSearchResult {
        return Sw360ProjectSearchResult(jsonNode)
    }

    fun createSw360Release(jsonText: String = ""): Sw360Release {
        return Sw360Release(createJsonNode(jsonText))
    }

    fun createSw360ReleaseSearch(jsonText: String = ""): Sw360ReleaseSearch {
        return Sw360ReleaseSearch(createJsonNode(jsonText))
    }

    fun createSw360ReleaseSearchResult(jsonNode: JsonNode): Sw360ReleaseSearchResult {
        return Sw360ReleaseSearchResult(jsonNode)
    }

    fun createSw360Component(jsonText: String = ""): Sw360Component {
        return Sw360Component(createJsonNode(jsonText))
    }

    fun createSw360ComponentSearch(jsonText: String = ""): Sw360ComponentSearch {
        return Sw360ComponentSearch(createJsonNode(jsonText))
    }

    fun createSw360ComponentSearchResult(jsonNode: JsonNode): Sw360ComponentSearchResult {
        return Sw360ComponentSearchResult(jsonNode)
    }
    
    fun createSw360EmbeddedAttachment(jsonNode: JsonNode): Sw360EmbeddedAttachment {
        return Sw360EmbeddedAttachment(jsonNode)
    }


    fun createObjectNode(): ObjectNode {
        val objectMapper = ObjectMapper()
        return objectMapper.createObjectNode()
    }

    fun createArrayNode(): ArrayNode {
        val objectMapper = ObjectMapper()
        return objectMapper.createArrayNode()
    }

}