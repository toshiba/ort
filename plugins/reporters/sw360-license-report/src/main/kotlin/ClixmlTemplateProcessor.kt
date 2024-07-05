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

package org.ossreviewtoolkit.plugins.reporters.sw360licensereport

import freemarker.cache.ClassTemplateLoader
import freemarker.ext.beans.BeansWrapperBuilder
import freemarker.template.Configuration
import freemarker.template.DefaultObjectWrapper
import freemarker.template.TemplateExceptionHandler

import java.io.File
import java.util.UUID

import org.apache.logging.log4j.kotlin.logger

import org.ossreviewtoolkit.model.AdvisorCapability
import org.ossreviewtoolkit.model.AdvisorRecord
import org.ossreviewtoolkit.model.AdvisorResult
import org.ossreviewtoolkit.model.AdvisorResultFilter
import org.ossreviewtoolkit.model.HashAlgorithm
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Issue
import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.RemoteArtifact
import org.ossreviewtoolkit.model.RuleViolation
import org.ossreviewtoolkit.model.Severity
import org.ossreviewtoolkit.model.SnippetFinding
import org.ossreviewtoolkit.model.TextLocation
import org.ossreviewtoolkit.model.config.PluginConfiguration
import org.ossreviewtoolkit.model.config.RuleViolationResolution
import org.ossreviewtoolkit.model.config.VulnerabilityResolution
import org.ossreviewtoolkit.model.licenses.LicenseView
import org.ossreviewtoolkit.model.licenses.ResolvedLicense
import org.ossreviewtoolkit.model.licenses.ResolvedLicenseFileInfo
import org.ossreviewtoolkit.model.licenses.ResolvedLicenseFile
import org.ossreviewtoolkit.model.licenses.ResolvedLicenseInfo
import org.ossreviewtoolkit.model.licenses.filterExcluded
import org.ossreviewtoolkit.model.vulnerabilities.Vulnerability
import org.ossreviewtoolkit.model.vulnerabilities.VulnerabilityReference
import org.ossreviewtoolkit.reporter.Reporter
import org.ossreviewtoolkit.reporter.ReporterInput
import org.ossreviewtoolkit.utils.common.expandTilde
import org.ossreviewtoolkit.utils.spdx.SpdxConstants
import org.ossreviewtoolkit.utils.spdx.model.SpdxLicenseChoice


class ClixmlTemplateProcessor(
    val input: ReporterInput, 
    val outputDir: File, 
    val config: PluginConfiguration) {
    
    class LicenseFileInfo(
        val path: String,
        val hash: String,
        val licenses: List<ResolvedLicense>,
        val text: String,
        val acknowledgement: String
    )

    class CopyrightFileInfo(
        val path: String,
        val hash: String,
        val text: String
    )

    companion object {
        const val CLIXML_TEMPLATE_NAME = "clixml_report.ftl"
        const val CLIXML_FILENAME_PREFIX = "ort-cli_"
        const val CLIXML_FILENAME_EXTENSION = "xml"
    }


    private val freemarkerConfig: Configuration by lazy {
        Configuration(Configuration.VERSION_2_3_30).apply {
            defaultEncoding = "UTF-8"
            fallbackOnNullLoopVariable = false
            logTemplateExceptions = true
            tagSyntax = Configuration.SQUARE_BRACKET_TAG_SYNTAX
            templateExceptionHandler = TemplateExceptionHandler.RETHROW_HANDLER
            templateLoader = ClassTemplateLoader(
                this@ClixmlTemplateProcessor.javaClass.classLoader,
                "templates"
            )
            wrapUncheckedExceptions = true
        }
    }


    fun processTemplates(pkg: Package): File {
        val outputFile = outputDir.resolve(getClixmlFileName(pkg.id))
        logger.info { "Generating file '$outputFile'" }

        val template = freemarkerConfig.getTemplate(CLIXML_TEMPLATE_NAME)
        val dataModel = createDataModel(pkg)
        if (dataModel.isNotEmpty()) {
            outputFile.writer().use { template.process(dataModel, it) }
        } else {
            logger.warn {
                "Failed to retrieve the CLIXML data model for this package. (${pkg.id.name} ${pkg.id.version})"
            }
        }

        return outputFile
    }


    fun createDataModel(pkg: Package): Map<String, Any> {

        val documentName = pkg.id.name
        val componentSha1Hash = getComponentHash(pkg, HashAlgorithm.SHA1)
        val version = pkg.id.version

        val generalInformation = getGeneralInformation(pkg)
        val assessmentSummary = getAssessmentSummary(pkg)

        val licenseFileInfoList = getLicenseFileInfoList(pkg)
        if (licenseFileInfoList.isEmpty()) {
            // If there's no license file information available, skip generating the data model.
            logger.warn {
                "Failed to retrieve the licenseFileInfoList for this package. (${pkg.id.name} ${pkg.id.version})"
            }
            return mapOf<String, Any>()
        }
        val licensesMainList = getLicensesMainList(licenseFileInfoList)

        val copyrightFileInfoList = getCopyrightFileInfoList(pkg)
        val copyrightsList = getCopyrightsList(copyrightFileInfoList)

        return mapOf<String, Any>(
            "documentName" to documentName,
            "userName" to "",
            "acknow" to "",
            "componentHash" to componentSha1Hash,
            "version" to version,
            "generalInformation" to generalInformation,
            "assessmentSummary" to assessmentSummary,
            "licensesMainList" to licensesMainList,
            "copyrightsList" to copyrightsList
        )
    }


    fun getGeneralInformation(pkg: Package): Map<String, Any> {
        val reportId = UUID.randomUUID().toString()
        val componentHash = getComponentHash(pkg, HashAlgorithm.NONE)

        return mapOf(
            "reportId" to reportId,
            "reviewedBy" to "",
            "componentName" to pkg.id.name,
            "community" to "",
            "version" to pkg.id.version,
            "componentHash" to componentHash,
            "componentReleaseDate" to "",
            "linkComponentManagement" to "",
            "componentId" to "",
            "componentType" to "",
        )
    }

    @Suppress("UNUSED_PARAMETER")
    fun getAssessmentSummary(pkg: Package): Map<String, Any> {
        return mapOf(
            "generalAssessment" to "NA",
            "criticalFilesFound" to "None",
            "dependencyNotes" to "None",
            "exportRestrictionsFound" to "None",
            "usageRestrictionsFound" to "None",
            "additionalNotes" to "NA",
        )
    }


    fun getLicensesMainList(licenseFileInfoList: List<LicenseFileInfo>): List<Any> {

        val licensesMainList = mutableListOf<Any>()
        
        // Create rendering data for each type of license.
        val licenseIdFileInfoMap = mutableMapOf<String, MutableList<LicenseFileInfo>>()
        licenseFileInfoList.forEach { fileInfo ->
            val licenses = fileInfo.licenses

            licenses.forEach { resolvedLicense ->
                val licenseExpression = resolvedLicense.license
                val licenseId = licenseExpression.toString()
                if (licenseId.isNotEmpty()) {
                    if (licenseIdFileInfoMap.containsKey(licenseId)) {
                        licenseIdFileInfoMap[licenseId]!!.add(fileInfo)
                    } else {
                        licenseIdFileInfoMap[licenseId] = mutableListOf(fileInfo)
                    }
                }
            }
        }
        
        licenseIdFileInfoMap.forEach { (licenseId, fileInfoList) ->
            val licensesMain = mutableListOf<Any>()
            fileInfoList.forEach { fileInfo ->
                val ids = mapOf(
                    "contentMain" to licenseId,
                    "textMain" to fileInfo.text,
                    "files" to listOf<String>(fileInfo.path),
                    "hash" to listOf<String>(fileInfo.hash),
                    "acknowledgementMain" to fileInfo.acknowledgement,
                )
                licensesMain.add(ids)
            }
            licensesMainList.add(licensesMain)
        }
        
        return licensesMainList
    }


    fun getComponentHash(pkg: Package, algorithm: HashAlgorithm): String {

        val artifacts = listOf<RemoteArtifact>(
            pkg.sourceArtifact,
            pkg.binaryArtifact
        )
        artifacts.forEach { artifact ->
            if (artifact.url.isNotBlank()) {
                if (algorithm == HashAlgorithm.NONE) {
                    return artifact.hash.value
                } else if (artifact.hash.algorithm == algorithm) {
                    return artifact.hash.value
                }
            }
        }
        return ""
    }

   
    fun getCopyrightsList(copyrightFileInfoList: List<CopyrightFileInfo>): List<Any> {

        val copyrights = mutableListOf<Any>()
        
        copyrightFileInfoList.forEach { fileInfo ->
            val idc = mapOf(
                "contentCopy" to fileInfo.text,
                "files" to listOf<String>(fileInfo.path),
                "hash" to listOf<String>(fileInfo.hash),
                "comments" to "",
            )
            copyrights.add(idc)
        }
        
        val copyrightsList = mutableListOf<Any>(copyrights)
        return copyrightsList
    }


    fun getLicenseFileInfoList(pkg: Package): List<LicenseFileInfo> {

        val fileInfoList = mutableListOf<LicenseFileInfo>()

        val ortResult = input.ortResult
        val packageId = pkg.id

        val fileList = ortResult.getFileListForId(packageId)
        val filePathSha1Map = fileList?.files?.associateBy({ it.path }, { it.sha1 }) ?: emptyMap()

        val fileInfo = input.licenseInfoResolver.resolveLicenseFiles(packageId)
        val licenseFiles = fileInfo.files

        if (licenseFiles.isEmpty()) {
            return mutableListOf<LicenseFileInfo>()
        }

        licenseFiles.forEach { licenseFile ->
            val fileinfo = LicenseFileInfo(
                path = licenseFile.path,
                hash = filePathSha1Map.getOrDefault(licenseFile.path, ""),
                licenses = licenseFile.licenses,
                text = licenseFile.text,
                acknowledgement = ""
            )
            fileInfoList.add(fileinfo)
        }

        return fileInfoList
    }


    fun getCopyrightFileInfoList(pkg: Package): List<CopyrightFileInfo> {

        val filePathFileInfoMap = mutableMapOf<String, CopyrightFileInfo>()

        val ortResult = input.ortResult
        val packageId = pkg.id

        val fileList = ortResult.getFileListForId(packageId)
        val filePathSha1Map = fileList?.files?.associateBy({ it.path }, { it.sha1 }) ?: emptyMap()

        val fileInfo = input.licenseInfoResolver.resolveLicenseFiles(packageId)
        val licenseFiles = fileInfo.files

        if (licenseFiles.isEmpty()) {
            return mutableListOf<CopyrightFileInfo>()
        }

        licenseFiles.forEach { licenseFile ->
            val resolvedLicenses = licenseFile.licenses
            resolvedLicenses.forEach { resolvedLicense ->
                val resolvedCopyrights = resolvedLicense.getResolvedCopyrights()
                resolvedCopyrights.forEach { resolvedCopyright ->
                    val findings = resolvedCopyright.findings
                    findings.forEach { finding ->
                        val path = finding.location.path
                        val copyrightFileInfo = CopyrightFileInfo(
                            path = path,
                            hash = filePathSha1Map.getOrDefault(path, ""),
                            text = finding.statement
                        )
                        filePathFileInfoMap[path] = copyrightFileInfo
                    }
                }
            
            }
        }

        return filePathFileInfoMap.keys.sorted().mapNotNull { filePathFileInfoMap[it] }
    }


    private fun getClixmlFileName(packageId : Identifier) : String {
        val strBuilder = StringBuilder()
        
        strBuilder.append(CLIXML_FILENAME_PREFIX)

        val type : String? = packageId.type
        if (!type.isNullOrBlank()) {
            strBuilder.append(type)
            strBuilder.append("-")
        }
        val namespace : String? = packageId.namespace
        if (!namespace.isNullOrBlank()) {
            strBuilder.append(namespace)
            strBuilder.append("-")
        }
        strBuilder.append(packageId.name)
        strBuilder.append("@")
        strBuilder.append(packageId.version)
        strBuilder.append(".")
        strBuilder.append(CLIXML_FILENAME_EXTENSION)

        return strBuilder.toString()
    }

}
