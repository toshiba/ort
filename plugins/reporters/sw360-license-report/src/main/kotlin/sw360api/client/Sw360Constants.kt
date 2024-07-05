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


const val SW360VISIBILITY_PRIVATE = "PRIVATE"
const val SW360VISIBILITY_ME_AND_MODERATORS = "ME_AND_MODERATORS"
const val SW360VISIBILITY_BUISNESSUNIT_AND_MODERATORS = "BUISNESSUNIT_AND_MODERATORS"
const val SW360VISIBILITY_EVERYONE = "EVERYONE"
 
enum class SW360AttachmentType {
    DOCUMENT,
    SOURCE,
    DESIGN,
    REQUIREMENT,
    CLEARING_REPORT,
    COMPONENT_LICENSE_INFO_XML,
    COMPONENT_LICENSE_INFO_COMBINED,
    SCAN_RESULT_REPORT,
    SCAN_RESULT_REPORT_XML,
    SOURCE_SELF,
    BINARY,
    BINARY_SELF,
    DECISION_REPORT,
    LEGAL_EVALUATION,
    LICENSE_AGREEMENT,
    SCREENSHOT,
    OTHER,
    README_OSS
}
