/*
 * Copyright (C) TOSHIBA CORPORATION, 2023.
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

 import com.fasterxml.jackson.databind.JsonNode
 import com.fasterxml.jackson.databind.ObjectMapper
 import com.fasterxml.jackson.databind.node.ArrayNode
 import java.io.File
 import java.time.LocalDate
 import java.time.format.DateTimeFormatter
 import kotlin.random.Random
 import kotlinx.serialization.encodeToString
 import kotlinx.serialization.json.Json
 import okhttp3.MediaType.Companion.toMediaType
 import okhttp3.OkHttpClient
 import okhttp3.Request
 import okhttp3.RequestBody.Companion.toRequestBody
 import org.apache.logging.log4j.kotlin.logger
 import org.ossreviewtoolkit.downloader.Downloader
 import org.ossreviewtoolkit.model.Identifier
 import org.ossreviewtoolkit.model.OrtResult
 import org.ossreviewtoolkit.model.Package
 import org.ossreviewtoolkit.model.Project
 import org.ossreviewtoolkit.model.config.PluginConfiguration
 import org.ossreviewtoolkit.model.config.Sw360StorageConfiguration
 import org.ossreviewtoolkit.model.utils.toPurl
 import org.ossreviewtoolkit.model.licenses.LicenseView
 import org.ossreviewtoolkit.model.licenses.LicenseClassifications
 import org.ossreviewtoolkit.plugins.reporters.evaluatedmodel.EvaluatedModel
 import org.ossreviewtoolkit.plugins.reporters.evaluatedmodel.DependencyTreeNode
 import org.ossreviewtoolkit.plugins.reporters.sw360licensereport.sw360api.client.*
 import org.ossreviewtoolkit.scanner.storages.Sw360Storage
 import org.ossreviewtoolkit.utils.common.collectMessages
 import org.ossreviewtoolkit.utils.common.packZip
 import org.ossreviewtoolkit.reporter.Reporter
 import org.ossreviewtoolkit.reporter.ReporterInput
 import org.ossreviewtoolkit.utils.spdx.SpdxExpression
 import org.ossreviewtoolkit.utils.spdx.SpdxSingleLicenseExpression
 
 
 class Sw360LicenseReporter : Reporter {

    private class Sw360ClientParameters(
        val projectClient: Sw360ProjectApiClient,
        val componentClient: Sw360ComponentApiClient,
        val releaseClient: Sw360ReleaseApiClient,
        val projectIdMap: MutableMap<Pair<String, String>, String>,
        val releaseIdMap: MutableMap<Pair<String, String>, String>,
        val componentIdMap: MutableMap<String, String>,
        val config: Sw360StorageConfiguration
    ) {}

    companion object {
        const val OPTION_DEDUPLICATE_DEPENDENCY_TREE = "deduplicateDependencyTree"
        const val OPTION_DEPENDENCY_NETWORK_ENABLE = "dependencyNetwork"
        const val OPTION_ROOT_PROJECT_NAME = "projectName"
        const val OPTION_ROOT_PROJECT_VERSION = "projectVersion"
        const val OPTION_LICENSE_TEXT_ATTACHMENT_ENABLE = "licenseTextAttachment"
        const val DEFAULT_DEPENDENCY_NETWORK_ENABLE = "false"
        const val DEFAULT_ROOT_PROJECT_NAME = "ORT_LICENSE_REPORT"
        const val DEFAULT_ROOT_PROJECT_VERSION = ""
        const val DEFAULT_LICENSE_TEXT_ATTACHMENT_ENABLE = "true"
        const val REPORT_FILENAME_PREFIX = "ort-license-text_"
        const val REPORT_FILENAME_EXTENSION = "txt"
        const val ARCHIVE_FILENAME_PREFIX = "ort-source-archive_"

        val REPORT_FILE_LINE_SEPARATION = "\n\n" + "=".repeat(72) + "\n\n"
        val COPYLEFT_LICENSES: Set<String> = setOf(
            "copyleft",
            "strong-copyleft",
            "copyleft-limited"
        )
        val INCLUDE_IN_NOTICE_FILE_LICENSES: Set<String> = setOf(
            "include-in-notice-file"
        )
        val DEPENDENCY_NETWORK_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    }

    override val type = "Sw360LicenseReport"
    

    private fun getLicenseTextFileName(packageId : Identifier) : String {
        val strBuilder = StringBuilder()
        
        strBuilder.append(REPORT_FILENAME_PREFIX)

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
        strBuilder.append(REPORT_FILENAME_EXTENSION)

        return strBuilder.toString()
    }

    private fun getSourceArchiveFileName(packageId : Identifier) : String {
        val strBuilder = StringBuilder()
        
        strBuilder.append(ARCHIVE_FILENAME_PREFIX)

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
        strBuilder.append(".zip")

        return strBuilder.toString()
    }

    private fun getProjects(ortResult : OrtResult) : Set<Project> {
        return ortResult.getProjects(omitExcluded = true)
    }

    private fun getProjectPackages(ortResult : OrtResult, project : Project) : List<Package> {
        val dependencies : Set<Identifier> = ortResult.dependencyNavigator.projectDependencies(project)
        // TODO: While referencing the process of UploadResultToSw360Command, 
        // it is necessary to carefully consider the need for getUncuratedPackageOrProject.
        return dependencies.mapNotNull { ortResult.getUncuratedPackageOrProject(it) }
    }

    private fun getReleaseName(pkgId: Identifier) : String {
        val namespace : String? = pkgId.namespace
        return if (namespace.isNullOrBlank()) pkgId.name else "${namespace}/${pkgId.name}"
    }

    private fun getReleaseNameForOrtProject(projectId: Identifier) : String {
        return "ort-project/${projectId.type}/${getReleaseName(projectId)}"
    }

    private fun createSw360Project(
        name: String, 
        version: String, 
        visibility:String, 
        sw360params: Sw360ClientParameters): Sw360Project {

        val entry = Sw360ApiClientDataFactory.createSw360Project()
        entry.setRootValue("name", name)
        if (version.isNotEmpty()) {
            entry.setRootValue("version", version)
        }
        entry.setRootValue("visibility", visibility)
        
        val newProject = sw360params.projectClient.createProject(entry.getJsonAsString())
        logger.debug { "Project '${name}-${version}' for the ort prject is created in SW360." }

        sw360params.projectIdMap.put(Pair(name.lowercase(), version.lowercase()), newProject.getId())

        return newProject
    }

    private fun createSw360Component(
        name: String, 
        componentType: String,
        sw360params: Sw360ClientParameters): Sw360Component {

        val entry = Sw360ApiClientDataFactory.createSw360Component()
        entry.setRootValue("name", name)
        entry.setRootValue("componentType", componentType)
        val newComponent = sw360params.componentClient.createComponent(entry.getJsonAsString())
        logger.debug { "Component '${name}' for the ort package is created in SW360." }

        sw360params.componentIdMap.put(name.lowercase(), newComponent.getId())

        return newComponent
    }

    private fun createSw360Release(
        name: String, 
        version: String, 
        mainLicenseIds: List<String>,
        sw360params: Sw360ClientParameters): Sw360Release {

        val componentId = sw360params.componentIdMap.getOrElse(name.lowercase()) {
            createSw360Component(name, "OSS", sw360params).getId()
        }

        val entry = Sw360ApiClientDataFactory.createSw360Release()
        entry.setRootValue("name", name)
        entry.setRootValue("version", version)
        entry.setRootValue("componentId", componentId)
        if (mainLicenseIds.isNotEmpty()) {
            val mapper = ObjectMapper()
            val licensesArrayNode:ArrayNode = mapper.valueToTree(mainLicenseIds)
            entry.setRootValue("mainLicenseIds", licensesArrayNode)
        }
        val newRelease = sw360params.releaseClient.createRelease(entry.getJsonAsString())
        logger.debug { "Release '${name}-${version}' created in SW360." }

        sw360params.releaseIdMap.put(Pair(name.lowercase(), version.lowercase()), newRelease.getId())

        return newRelease
    }

    private fun createSw360ReleaseForOrtPackage(pkg: Package, sw360params: Sw360ClientParameters): Sw360Release {
        val licenseShortNames = pkg.declaredLicensesProcessed.spdxExpression?.licenses().orEmpty()
        val unmappedLicenses = pkg.declaredLicensesProcessed.unmapped
        if (unmappedLicenses.isNotEmpty()) {
            logger.warn {
                "The following licenses could not be mapped in order to create a SW360 release: $unmappedLicenses"
            }
        }
        val releaseName = getReleaseName(pkg.id)
        val releaseVersion = getVersionOrDefault(pkg.id.version)
        return createSw360Release(releaseName, releaseVersion, licenseShortNames, sw360params)
    }

    private fun createSw360ReleaseForOrtProject(project: Project, sw360params: Sw360ClientParameters): Sw360Release {
        val releaseName = getReleaseNameForOrtProject(project.id)
        val releaseVersion = getVersionOrDefault(project.id.version)
        return createSw360Release(releaseName, releaseVersion, listOf<String>(), sw360params)
    }

    private fun createSw360DpendencyTree(
        input: ReporterInput, 
        config: PluginConfiguration, 
        sw360params: Sw360ClientParameters) {

        // Create EvaluatedModel for this project.
        val evaluatedModel = EvaluatedModel.create(
            input,
            config.options[OPTION_DEDUPLICATE_DEPENDENCY_TREE].toBoolean()
        )

        // Get plugin configurations
        val ortConfig = input.ortConfig
        val sw360Config = ortConfig.scanner.storages?.values?.filterIsInstance<Sw360StorageConfiguration>()?.singleOrNull()
        requireNotNull(sw360Config) {
            "createSw360DpendencyTree : No SW360 storage is configured for the scanner."
        }

        val projectName = config.options.getOrDefault(OPTION_ROOT_PROJECT_NAME, DEFAULT_ROOT_PROJECT_NAME)
        val projectVersion = config.options.getOrDefault(OPTION_ROOT_PROJECT_VERSION, DEFAULT_ROOT_PROJECT_VERSION)
        val dependencyNetworkEnable = config.options.getOrDefault(OPTION_DEPENDENCY_NETWORK_ENABLE, DEFAULT_DEPENDENCY_NETWORK_ENABLE).toBoolean()
        println("ort report ${type} PluginConfiguration:")
        println("projectName=${projectName}")
        println("projectVersion=${projectVersion}")
        println("dependencyNetworkEnable=${dependencyNetworkEnable}")

        // Create the root project for BOM management.
        val sw360ProjectId = sw360params.projectIdMap.getOrElse(Pair(projectName.lowercase(), projectVersion.lowercase())) {
            createSw360Project(projectName, projectVersion, SW360VISIBILITY_EVERYONE, sw360params).getId()
        }

        // Create SW360 records corresponding to the child nodes of the project.
        val dependencyReleaseLinks = mutableListOf<Any>()
        val linkedReleases = mutableListOf<Sw360Release>()
        val dependencyTrees = evaluatedModel.dependencyTrees
        dependencyTrees.forEach { treeNode ->
            if (isProjectNode(treeNode)) {
                // To align with the data structure of SW360, register ORT projects and packages as releases.
                val release = createSw360ReleaseForDependencyTree(treeNode, dependencyReleaseLinks, input, sw360params)
                linkedReleases += release
            } else {
                require(false) {"createSw360DpendencyTree : Unexpected node type. The root node is expected to represent the project."}
            }
        }

        // Link this project to the releases.
        if (linkedReleases.isNotEmpty()) {
            val releaseIds = linkedReleases.filterNotNull().map { it.getId() }
            sw360params.projectClient.createReleaseRelationship(sw360ProjectId, releaseIds)
        }

        // Update the Dependency network of this project.
        if (dependencyNetworkEnable) {
            val objectMapper = ObjectMapper()
            val releaseRelationNetwork = mutableMapOf<String, Any>("dependencyNetwork" to dependencyReleaseLinks)
            val projectBody = objectMapper.writeValueAsString(releaseRelationNetwork)

            sw360params.projectClient.updateDependencyNetwork(sw360ProjectId, projectBody)
        }
    }

    private fun isProjectNode(treeNode: DependencyTreeNode): Boolean {
        if (treeNode.pkg == null) return false
        val evaluatedPkg = treeNode.pkg!!
        return evaluatedPkg.isProject
    }

    private fun isPackageNode(treeNode: DependencyTreeNode): Boolean {
        if (treeNode.pkg == null) return false
        val evaluatedPkg = treeNode.pkg!!
        return !evaluatedPkg.isProject
    }

    private fun isScopeNode(treeNode: DependencyTreeNode): Boolean {
        if (treeNode.pkg != null) return false
        return treeNode.scope != null
    }

    private fun createSw360ReleaseForDependencyTree(
        treeNode: DependencyTreeNode, 
        parentDependencyReleaseLinks: MutableList<Any>,
        input: ReporterInput, 
        sw360params: Sw360ClientParameters): Sw360Release {

        val ortResult = input.ortResult

        // Create a SW360 release for this node.
        var sw360ReleaseWork: Sw360Release? = null
        if(isProjectNode(treeNode)) {
            // To align with the data structure of SW360, register ORT projects as releases.
            requireNotNull(treeNode.pkg) {"createSw360ReleaseForDependencyTree : this project treeNode package must not be null."}
            val evaluatedPkg = treeNode.pkg!!
            val project = ortResult.getProject(evaluatedPkg.id)
            project?.let {
                val releaseName = getReleaseNameForOrtProject(it.id)
                val releaseVersion = it.id.version
                // Get or create a SW360 Release
                sw360ReleaseWork = findSw360Release(releaseName, releaseVersion, sw360params) ?: run {
                    createSw360ReleaseForOrtProject(it, sw360params)
                }
            }
            requireNotNull(project) {"createSw360ReleaseForDependencyTree : project must not be null."}

        } else if(isPackageNode(treeNode)) {

            treeNode.pkg?.let { evaluatedPkg ->
                val metadata = ortResult.getPackage(evaluatedPkg.id)?.metadata

                metadata?.let { resultPkg ->
                    // Get or create a SW360 Release
                    val releaseName = getReleaseName(resultPkg.id)
                    val releaseVersion = getVersionOrDefault(resultPkg.id.version)
                    sw360ReleaseWork = findSw360Release(releaseName, releaseVersion, sw360params) ?: run {
                        createSw360ReleaseForOrtPackage(resultPkg, sw360params) 
                    }
                }
                requireNotNull(metadata) {"createSw360ReleaseForDependencyTree : metadata must not be null."}
            }
            requireNotNull(treeNode.pkg) {"createSw360ReleaseForDependencyTree : this treeNode package must not be null."}

        } else {
            require(false) {"createSw360ReleaseForDependencyTree : This is an unexpected node type. it is expected to be a project or a package."}
        }
        requireNotNull(sw360ReleaseWork) {"createSw360ReleaseForDependencyTree : sw360ReleaseWork must not be null."}
        val sw360Release = sw360ReleaseWork!!

        // Create SW360 Releases corresponding to the child nodes of this node.
        val linkedReleases = mutableListOf<Sw360Release>()
        val dependencyReleaseLinks = mutableListOf<Any>()

        treeNode.children.forEach { childNode ->
            if(isProjectNode(childNode) || isPackageNode(childNode)) {
                // To align with the data structure of SW360, register ORT projects and packages as releases.
                val childSw360Release = createSw360ReleaseForDependencyTree(childNode, dependencyReleaseLinks, input, sw360params)
                linkedReleases += childSw360Release
            } else if(isScopeNode(childNode)) {
                // Since SW360 does not have data corresponding to the ORT scope, skip and link to the parent node.
                val scopeChildren = childNode.children
                scopeChildren.forEach { scopeChild ->
                    if(isProjectNode(scopeChild) || isPackageNode(scopeChild)) {
                        val childSw360Release = createSw360ReleaseForDependencyTree(scopeChild, dependencyReleaseLinks, input, sw360params)
                        linkedReleases += childSw360Release
                    } else {
                        require(false) {"createSw360ReleaseForDependencyTree : Unexpected node type. Children of the scope are expected to be packages."}
                    }
                }
            } else {
                require(false) {"createSw360ReleaseForDependencyTree : This is an unexpected node type. Children of the package are expected to be packages."}
            }
        }        
        
        // Linking the SW360 Release and its children.
        if (linkedReleases.isNotEmpty()) {
            val relationship = linkedReleases.associate { it.getId() to "CONTAINED" }
            sw360params.releaseClient.createReleaseRelationship(sw360Release.getId(), relationship)
        }

        // Create a dependency network node
        val dependencyRelease = mutableMapOf<String, Any>(
            "releaseId" to sw360Release.getId(),
            "releaseRelationship" to "CONTAINED",
            "mainlineState" to "MAINLINE",
            "createOn" to LocalDate.now().format(DEPENDENCY_NETWORK_DATE_FORMATTER),
            "createBy" to sw360params.config.username,
            "releaseLink" to dependencyReleaseLinks
        )
        parentDependencyReleaseLinks += dependencyRelease

        return sw360Release
    }

    private fun findSw360Release(
        name: String, 
        version: String, 
        sw360params: Sw360ClientParameters
        ): Sw360Release? {
        
        val mappedReleaseId = sw360params.releaseIdMap[Pair(name.lowercase(), version.lowercase())]
        val sw360release = mappedReleaseId?.let { releaseId ->
            sw360params.releaseClient.getRelease(releaseId)
        }

        return sw360release
    }

    private fun createPackageSourceDirectory(pkg: Package, baseDir: File) : File {
        val path = pkg.id.toPath("-")
        val srcDir = baseDir.resolve(path)
        if (!srcDir.exists()) {
            srcDir.mkdirs()
        } else {
            logger.warn {
                "The folder ${srcDir.path} already exists."
            }
        }
        return srcDir
    }

    private fun deleteDirectoryRecursively(dir: File) {
        if (dir.exists()) {
            dir.listFiles()?.forEach { subFile ->
                if (subFile.isDirectory) {
                    deleteDirectoryRecursively(subFile)
                } else {
                    subFile.delete()
                }
            }
            dir.delete()
        } else {
            logger.warn {
                "The Directory ${dir.path} does not exist."
            }
        }
    }

    private fun getSourceArchiveFile(downloader: Downloader, pkg: Package, outputDir: File) : File {
        val tempDirectory = createPackageSourceDirectory(pkg, outputDir)
        try {
            val srcDirectory = tempDirectory.resolve("src")
            downloader.download(pkg, srcDirectory)

            val archiveFileName = getSourceArchiveFileName(pkg.id)
            val zipFile = outputDir.resolve(archiveFileName)
            val archiveFile = srcDirectory.packZip(zipFile)

            return archiveFile

        } finally {
            deleteDirectoryRecursively(tempDirectory)
        }
    }

    private fun getVersionOrDefault(version: String): String {
        // SW360 client library does not accept an empty version, 
        // so a dummy string is assigned.
        if (version.isNotEmpty()) {
            return version
        } else {
            return "unknown"
        }
    }

    private fun isCopyLeftPackage(pkg: Package, licenseClassifications: LicenseClassifications): Boolean {
        val licenseShortNames = pkg.declaredLicensesProcessed.spdxExpression?.licenses().orEmpty().toSet()
        for (licenseName in licenseShortNames.orEmpty()) {
            val licenseExpression = SpdxExpression.parse(licenseName)
            val categories = licenseClassifications.get(licenseExpression)
            for (category in categories.orEmpty()) {
                if (COPYLEFT_LICENSES.contains(category)) {
                    return true
                }
            }
        }

        return false
    }

    private fun isIncludeInNoticeFilePackage(pkg: Package, licenseClassifications: LicenseClassifications): Boolean {
        val licenseShortNames = pkg.declaredLicensesProcessed.spdxExpression?.licenses().orEmpty().toSet()
        for (licenseName in licenseShortNames.orEmpty()) {
            val licenseExpression = SpdxExpression.parse(licenseName)
            val categories = licenseClassifications.get(licenseExpression)
            for (category in categories.orEmpty()) {
                if (INCLUDE_IN_NOTICE_FILE_LICENSES.contains(category)) {
                    return true
                }
            }
        }

        return false
    }

    private fun createNoticeFile(input: ReporterInput, pkg: Package, outputDir: File): File {
        val packageId = pkg.id
        val fileInfo = input.licenseInfoResolver.resolveLicenseFiles(packageId)
        val licenseFiles = fileInfo.files
        val textBuilder = StringBuilder()
        val fileName = getLicenseTextFileName(packageId)
        val outputFile = outputDir.resolve(fileName)

        licenseFiles.forEachIndexed  { index, file ->
            if (index > 0) {
                textBuilder.append(REPORT_FILE_LINE_SEPARATION)
            }
            val licenseText = file.text
            textBuilder.append(licenseText)
        }

        if (textBuilder.length > 0) {
            val outputText = textBuilder.toString()
            outputFile.writeText(outputText)

        } else {
            logger.warn {
                "Unable to retrieve the license text for package ${pkg.id.name} ${pkg.id.version}"
            }
        }

        return outputFile
    }

    private fun randomSleep(n: Int) {
        val sleepTime = Random.nextInt(1, n + 1) * 1000L
        try {
            Thread.sleep(sleepTime)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }

    override fun generateReport(input: ReporterInput, outputDir: File, config: PluginConfiguration): List<File> {

        try {
            val outputFiles = mutableListOf<File>()
            val ortResult = input.ortResult
            val ortConfig = input.ortConfig
            val projects = getProjects(ortResult)
            val licenseTextAttachmentEnable = config.options.getOrDefault(
                OPTION_LICENSE_TEXT_ATTACHMENT_ENABLE, DEFAULT_LICENSE_TEXT_ATTACHMENT_ENABLE).toBoolean()
            println("licenseTextAttachment=${licenseTextAttachmentEnable}")    

            ortResult.scanner ?: run {
                logger.warn { 
                    "The specified result file does not contain scan information."
                }
                return outputFiles
            }

            // Create SW360 Api clients
            val sw360Config = ortConfig.scanner.storages?.values?.filterIsInstance<Sw360StorageConfiguration>()?.singleOrNull()
            requireNotNull(sw360Config) {
                "No SW360 storage is configured for the scanner."
            }

            val sw360ProjectClient = Sw360ProjectApiClient(sw360Config)
            val sw360ComponentClient = Sw360ComponentApiClient(sw360Config)
            val sw360ReleaseClient = Sw360ReleaseApiClient(sw360Config)
            val sw360params = Sw360ClientParameters(
                projectClient = sw360ProjectClient,
                componentClient = sw360ComponentClient,
                releaseClient = sw360ReleaseClient,
                projectIdMap = sw360ProjectClient.getSw360ProjectNameIdMap().toMutableMap(),
                componentIdMap = sw360ComponentClient.getSw360ComponentNameIdMap().toMutableMap(),
                releaseIdMap = sw360ReleaseClient.getSw360ReleaseNameIdMap().toMutableMap(),
                config = sw360Config
            )

            // Create a dependency tree for SW360.
            createSw360DpendencyTree(input, config, sw360params)

            // Create ORT downloader
            val downloader = Downloader(ortConfig.downloader)

            // Create ClixmlTemplateProcessor
            val clixmlTemplateProcessor = ClixmlTemplateProcessor(input, outputDir, config)

            // Create report information related to the packages and upload them to SW360
            projects.forEach { 
                val project = it
                val packages = getProjectPackages(ortResult, project)

                packages.forEach { pkg ->
                    
                    try {
                        val attachmentFileMap = mutableMapOf<SW360AttachmentType, File>()
                        
                        // To consider the load on the repository server, introduce a random time delay.
                        randomSleep(3)

                        // Outputting the source code archive file for this package.
                        val archiveFile = getSourceArchiveFile(downloader, pkg, outputDir)
                        if (archiveFile.exists()) {
                            outputFiles += archiveFile
                            attachmentFileMap[SW360AttachmentType.SOURCE] = archiveFile
                        }

                        // Outputting a component license information for this package.
                        val clixmlFile = clixmlTemplateProcessor.processTemplates(pkg)
                        if (clixmlFile.exists()) {
                            outputFiles += clixmlFile
                            attachmentFileMap[SW360AttachmentType.COMPONENT_LICENSE_INFO_XML] = clixmlFile
                        } else {
                            logger.warn { 
                                "Unable to retrieve the CLIXML for package (${pkg.id.name} ${pkg.id.version})"
                            }
                        }

                        // Outputting a license text file for this package.
                        if (licenseTextAttachmentEnable) {
                            val isIncludeInNoticeFile = isIncludeInNoticeFilePackage(pkg, input.licenseClassifications)
                            if (isIncludeInNoticeFile) {
                                val noticeFile = createNoticeFile(input, pkg, outputDir)
                                if (noticeFile.exists()) {
                                    outputFiles += noticeFile
                                    attachmentFileMap[SW360AttachmentType.DOCUMENT] = noticeFile
                                }
                            }
                        }

                        // Get the release for this package.
                        val releaseName = getReleaseName(pkg.id)
                        val releaseVersion = getVersionOrDefault(pkg.id.version)
                        
                        val release = findSw360Release(releaseName, releaseVersion, sw360params) ?: run {
                            logger.warn { 
                                "The absence of this record ${releaseName}(${releaseVersion}) indicates an anomaly in the dependency tree."
                            }
                            createSw360ReleaseForOrtPackage(pkg, sw360params) 
                        }

                        // Uploading result files to SW360.
                        if (!attachmentFileMap.isNullOrEmpty()) {

                            val attachmentFilenameIdMap = release.getEmbeddedAttachmentEntries().associate {
                                it.getFilename() to it.getId()
                            }

                            attachmentFileMap.forEach { (type, file) ->

                                // Remove the file if a file with the same name is already attached
                                if (attachmentFilenameIdMap.containsKey(file.name)) {
                                    val attachmentId = attachmentFilenameIdMap.get(file.name)
                                    attachmentId?.let {
                                        sw360params.releaseClient.deleteAttachment(release.getId(), it)
                                    }
                                    requireNotNull(attachmentId) {
                                        "attachmentId must not be null."
                                    }
                                }
                                
                                // Attach the file that was outputted
                                when(type) {
                                    SW360AttachmentType.DOCUMENT -> {
                                        sw360params.releaseClient.attachLicenseText(release.getId(), file)
                                    }
                                    SW360AttachmentType.SOURCE -> {
                                        sw360params.releaseClient.attachSourceCode(release.getId(), file)
                                    }
                                    SW360AttachmentType.COMPONENT_LICENSE_INFO_XML -> {
                                        sw360params.releaseClient.attachComponentLicenseInfoXml(release.getId(), file)
                                    }
                                    else -> {
                                        logger.warn { 
                                            "Unexpected attachment type specified in the release ${releaseName}(${releaseVersion}) AttachmentType=${type.name}"
                                        }
                                    }
                                }
                            }
                        }

                    } catch(e: Exception) {
                        val stackTrace = e.stackTrace.joinToString("\n") { it.toString() }
                        logger.warn { 
                            "Unable to update the release for the package ${pkg.id.name} ${pkg.id.version}. ${e.toString()}\n" + stackTrace
                        }
                    }
                }
            }

            return outputFiles

        } catch (e: Exception) {
            val stackTrace = e.stackTrace.joinToString("\n") { it.toString() }
            logger.error { 
                "Sw360LicenseReporter.generateReport was failed. ${e.toString()}\n" + stackTrace
            }
            throw e
        }
    }
}
 