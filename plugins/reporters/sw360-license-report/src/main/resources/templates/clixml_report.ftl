[#--
    Copyright (C) TOSHIBA CORPORATION, 2023.

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

        https://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.

    SPDX-License-Identifier: Apache-2.0
    License-Filename: LICENSE
--]
<?xml version="1.0" encoding="UTF-8"?>
<ComponentLicenseInformation component="${documentName}" creator="${userName}" date="${.now?string('yyyy-MM-dd')}" baseDoc="" toolUsed="oss-review-toolkit" componentID="${acknow}" componentSHA1="${componentHash}" version="${version}">
  <GeneralInformation>
    <ReportId>${generalInformation.reportId?trim}</ReportId>
    [#if generalInformation.reviewedBy?has_content]
    <ReviewedBy>${generalInformation.reviewedBy?trim}</ReviewedBy>
    [/#if]
    <ComponentName>${generalInformation.componentName?trim}</ComponentName>
    <Community>${generalInformation.community?trim}</Community>
    <ComponentVersion>${generalInformation.version?trim}</ComponentVersion>
    <ComponentHash>${generalInformation.componentHash?trim}</ComponentHash>
    <ComponentReleaseDate>${generalInformation.componentReleaseDate?trim}</ComponentReleaseDate>
    <LinkComponentManagement><![CDATA[${generalInformation.linkComponentManagement?trim}]]></LinkComponentManagement>
    [#if generalInformation.componentId?has_content]
    <ComponentId>
      <Type>${generalInformation.componentType?trim}</Type>
      <Id>${generalInformation.componentId?trim}</Id>
    </ComponentId>
    [/#if]
  </GeneralInformation>

  <AssessmentSummary>
    <GeneralAssessment><![CDATA[${assessmentSummary.generalAssessment?trim}]]></GeneralAssessment>
    <CriticalFilesFound>${assessmentSummary.criticalFilesFound?trim}</CriticalFilesFound>
    <DependencyNotes>${assessmentSummary.dependencyNotes?trim}</DependencyNotes>
    <ExportRestrictionsFound>${assessmentSummary.exportRestrictionsFound?trim}</ExportRestrictionsFound>
    <UsageRestrictionsFound>${assessmentSummary.usageRestrictionsFound?trim}</UsageRestrictionsFound>
    <AdditionalNotes><![CDATA[${assessmentSummary.additionalNotes?trim}]]></AdditionalNotes>
  </AssessmentSummary>

  [#list licensesMainList as licensesMain]
  [#if licensesMain?has_content]
    [#list licensesMain as ids]
      [#if ids.textMain?has_content]
  <License type="otherwhite" name="${ids.contentMain?html}" spdxidentifier="${ids.contentMain?html}">
    <Content><![CDATA[${ ids.textMain }]]></Content>
    <Files><![CDATA[
    [#list ids.files as id]
      [#if id?has_content]${id}[/#if]
    [/#list]
]]></Files>
    <FileHash><![CDATA[
    [#list ids.hash as id]
      [#if id?has_content]${id}[/#if]
    [/#list]
]]></FileHash>
        [#if ids.acknowledgementMain?has_content]
    <Acknowledgements><![CDATA[
    [#list ids.acknowledgementMain as id]${id?html}[/#list]
]]></Acknowledgements>
        [/#if]
  </License>
      [/#if]
    [/#list]
  [/#if]
  [/#list]

  [#list copyrightsList as copyrights]
  [#if copyrights?has_content]
    [#list copyrights as idc]
      [#if idc.contentCopy?has_content]
  <Copyright>
    <Content><![CDATA[${ idc.contentCopy }]]></Content>
    <Files><![CDATA[
      [#list idc.files as id]
        [#if id?has_content]${id}[/#if]
      [/#list]
]]></Files>
    <FileHash><![CDATA[
      [#list idc.hash as id]
        [#if id?has_content]${id}[/#if]
      [/#list]
]]></FileHash>
    [#if idc.comments?has_content]
    <Comment><![CDATA[${ idc.comments }]]></Comment>
    [/#if]
  </Copyright>
      [/#if]
    [/#list]
  [/#if]
  [/#list]
</ComponentLicenseInformation>

