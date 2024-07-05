# Program for Automatically Creating Legal Outputs in Accordance with License Compliance Obligations

## Overview

This program automatically creates legal outputs that comply with license fulfillment obligations. Legal outputs refer to documents containing code that must be disclosed, as well as documents with license clauses that require reproduction reporting.

## Key Points

The program automates the following operations as features of the ORT report function:
- Analyzing license compatibility and fulfillment obligations of OSS licenses by acquiring code through ORT.
- Extracting the original license clauses from the code.
- Obtaining package source code for code that has a disclosure obligation.
    * Currently, sources are attached for all packages where source code can be obtained.
- Registering package dependencies in SW360 and attaching the license text (in CLIXML format) and the package's source code to SW360.
    The correspondence between ORT data and SW360 records:
    |ORT Data|SW360 Record|Remarks|
    |---|---|---|
    |OrtResult|Project|Create a Project in SW360 as a record corresponding to the analysis result data of ORT.|
    |Project|Release|Since the analysis results of ORT are managed as data of one project, create a Release in SW360 as a record corresponding to the Project node in ORT's dependency tree. Sub SW360 Projects are not created.|
    |Package|Release|Create a Release in SW360 as a record corresponding to the Package node in ORT's dependency tree.|
    |Scope|-|Since there is no record in SW360 corresponding to ORT's Scope, skip the Scope node in ORT's dependency tree and directly connect the child nodes of Scope to the parent node of Scope. This relationship becomes a link between Project and Release in SW360.|

## Implementation Details (Extension of Existing ORT Functions)
- About the addition of a custom Report function
    The specific processing for each output type of the Report function is provided as an implementation class of the Reporter interface. The Reporter interface is a derivative of ORT's Plugin interface and operates as part of ORT's architecture, which is designed for loose coupling between modules.
    Correspondence for adding a custom Report function:
    - Add a submodule to ort/plugins/reporters.
        Place build.gradle.kts according to the structure of other plugin folders, and place the source under src/main.
    - Adding an implementation class of the Report interface
        Add an implementation class of the org.ossreviewtoolkit.reporter.Reporter interface.
        The string specified in the member variable type is treated as the option string for the -f option when executing the ort report command (also dynamically reflected in the help text).
        Report processing can be defined by overriding the generateReport function.
        The ReporterInput object provided as an argument contains information such as package information and license text, which are the basis for the report function. The return is a list of generated files.
    - Definition for the service loader
        ORT obtains plugin class instances through Java's ServiceLoader. It is necessary to add the definition of the added Reporter implementation class to the following file.
        plugins/reporters/<custom plugin>/src/main/resources/META-INF/services/org.ossreviewtoolkit.reporter.Reporter

## Function Details
- Environment Configuration
    - Adding ORT's binary folder to PATH
        ```bash
        $ export PATH=/home/ubuntu/work/ort-work/ort-gitlab/cli/build/install/ort/bin:$PATH
        ```
    - Adding the SW360 host to NoProxy (if the SW360 host is deployed in the local network)
        ```bash
        $ export JAVA_OPTS="$JAVA_OPTS -Dhttp.nonProxyHosts=<SW360 host>"
        $ export no_proxy=$no_proxy,<SW360 host>
        ```
    - ORT's config settings
        The connection information for SW360 is used in the existing ORT config's scan storage settings.
        Add the following settings to the config.yml file. If the config.yml file does not exist, create a config.yml file in the ~/.ort/config folder.

        ```yaml
        ort:
        scanner:
            storages:
            sw360Configuration:
                restUrl: "http://127.0.0.1:8080/resource/api/"
                authUrl: "http://127.0.0.1:8080/authorization/oauth/token"
                username: admin@sw360.org
                password: password
                clientId: xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
                clientPassword: xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
                token: xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
        ```

        Reference configuration:
        [1](https://github.com/oss-review-toolkit/ort/blob/main/model/src/main/resources/reference.yml)

    - Building the scan environment
        Since this report function uses the result files and cache files of ORT's scan function, it is necessary to install tools such as scancode in the execution environment beforehand.

- How to execute the report function
    ```bash
    $ ort -P <ORT options> report -f Sw360LicenseReport -i <path to scan-result file> -o <path to output folder> --license-classifications-file=<path to license-classifications file>
    ```
    Example:
    ```bash
    $ ort -P ort.reporter.config.Sw360LicenseReport.options.dependencyNetwork=true -P ort.reporter.config.Sw360LicenseReport.options.projectName=MY_ORT_PROJECT_1 -P ort.reporter.config.Sw360LicenseReport.options.projectVersion=1.00 report -f Sw360LicenseReport -i ./ort-results/scan/scan-result.yml -o ./ort-results/report-sw360 --license-classifications-file=license-classifications.yml
    ```

    Explanation of parameters:
    |Option|Value|Required|Description|
    |---|---|---|---|
    |-f|Sw360LicenseReport|✓|Specification of the report type. If using Sw360LicenseReport, it is a fixed string.|
    |-i|Path to scan-result file|✓|The path to the ORT result file that serves as input.|
    |-o|Path to output folder|✓|The path to the folder where the report files will be output. Specify an empty folder.|
    |--license-classifications-file|Path to license-classifications file|✓|The path to the file defining the categories of licenses. The license-classifications file is created based on the sample file provided by the official ORT. [2](https://github.com/oss-review-toolkit/ort/blob/main/examples/license-classifications.yml)|
    |-P ort.reporter.config.Sw360LicenseReport.options.dependencyNetwork|Boolean||A flag to switch whether to register the DependencyNetwork to the created project. (The default value is false.)|
    |-P ort.reporter.config.Sw360LicenseReport.options.licenseTextAttachment|License text attachment flag||A flag to switch whether to attach the license text obtained from the package's source code to SW360's Release. (The default value is true.)|
    |-P ort.reporter.config.Sw360LicenseReport.options.projectName|SW360 project name||Specify the project name in SW360 where the ORT report results will be registered. (The default value is ORT_LICENSE_REPORT.)|
    |-P ort.reporter.config.Sw360LicenseReport.options.projectVersion|SW360 project version||Specify the version of the project in SW360 where the ORT report results will be registered. (The default value is 1.00.)|

- Function Explanation
    - Outputting report information to SW360 based on ORT's scan result information
        The scan result file scan-result.yml is received as the input file. License and copyright texts are obtained from ORT's scan result information (the texts refer to the cache).
    
    - Registering the report information to SW360 according to the EvaluatedModel of ORT, and registering the dependency tree of Project and Release to SW360.
        If the option flag is enabled, update the DependencyNetwork.

    - Attaching source code, license text, and CLIXML
        Determine the license classification for each package, and obtain the source code and license text. The obtained files are saved to the output folder and attached to SW360's Release.
        The license classification information follows the definitions in the command argument license-classifications.yml.
        license-classifications.yml:
        ```yaml
        categories:
        - name: "copyleft"
        - name: "strong-copyleft"
        - name: "copyleft-limited"
        - name: "permissive"
          description: "Licenses with permissive obligations."
        - name: "public-domain"
        - name: "include-in-notice-file"
          description: >-
            This category is checked by templates used by the ORT report generator. The licenses associated with this
            category are included into NOTICE files.
        - name: "include-source-code-offer-in-notice-file"
          description: >-
            A marker category that indicates that the licenses assigned to it require that the source code of the packages
            needs to be provided.

        categorizations:
        - id: "AGPL-1.0"
          categories:
          - "copyleft"
          - "include-in-notice-file"
          - "include-source-code-offer-in-notice-file"
        ```

# Copyright

Copyright © 2024 TOSHIBA CORPORATION, All Rights Reserved.


# README (OSS Review Toolkit)

![OSS Review Toolkit Logo](./logos/ort.png)

&nbsp;

[![Slack][1]][2]

[![Wrapper Validation][3]][4] [![Static Analysis][5]][6]

[![Build and Test][7]][8] [![JitPack build status][9]][10] [![Code coverage][11]][12]

[![TODOs][13]][14] [![REUSE status][15]][16] [![CII][17]][18]

[1]: https://img.shields.io/badge/Join_us_on_Slack!-ort--talk-blue.svg?longCache=true&logo=slack
[2]: http://slack.oss-review-toolkit.org
[3]: https://github.com/oss-review-toolkit/ort/actions/workflows/wrapper-validation.yml/badge.svg
[4]: https://github.com/oss-review-toolkit/ort/actions/workflows/wrapper-validation.yml
[5]: https://github.com/oss-review-toolkit/ort/actions/workflows/static-analysis.yml/badge.svg
[6]: https://github.com/oss-review-toolkit/ort/actions/workflows/static-analysis.yml
[7]: https://github.com/oss-review-toolkit/ort/actions/workflows/build-and-test.yml/badge.svg
[8]: https://github.com/oss-review-toolkit/ort/actions/workflows/build-and-test.yml
[9]: https://jitpack.io/v/oss-review-toolkit/ort.svg
[10]: https://jitpack.io/#oss-review-toolkit/ort
[11]: https://codecov.io/gh/oss-review-toolkit/ort/branch/main/graph/badge.svg?token=QD2tCSUTVN
[12]: https://app.codecov.io/gh/oss-review-toolkit/ort
[13]: https://badgen.net/https/api.tickgit.com/badgen/github.com/oss-review-toolkit/ort
[14]: https://www.tickgit.com/browse?repo=github.com/oss-review-toolkit/ort
[15]: https://api.reuse.software/badge/github.com/oss-review-toolkit/ort
[16]: https://api.reuse.software/info/github.com/oss-review-toolkit/ort
[17]: https://bestpractices.coreinfrastructure.org/projects/4618/badge
[18]: https://bestpractices.coreinfrastructure.org/projects/4618

# Introduction

The OSS Review Toolkit (ORT) is a FOSS policy automation and orchestration toolkit which you can use to manage your
(open source) software dependencies in a strategic, safe and efficient manner.

You can use it to:

* Generate CycloneDX, SPDX SBOMs, or custom FOSS attribution documentation for your software project
* Automate your FOSS policy using risk-based Policy as Code to do licensing, security vulnerability, InnerSource
and engineering standards checks for your software project and its dependencies
* Create a source code archive for your software project and its dependencies to comply with certain licenses or have
your own copy as nothing on the internet is forever
* Correct package metadata or licensing findings yourself, using InnerSource or with the help of the FOSS community

ORT can be used as library (for programmatic use), via a command line interface (for scripted use), or via its CI
integrations. It consists of the following tools which can be combined into a *highly customizable* pipeline:

* [*Analyzer*](https://oss-review-toolkit.org/ort/docs/tools/analyzer) - determines the dependencies of projects and
  their metadata, abstracting which package managers or build systems are actually being used.
* [*Downloader*](https://oss-review-toolkit.org/ort/docs/tools/downloader) - fetches all source code of the projects and
  their dependencies, abstracting which Version Control System (VCS) or other means are used to retrieve the source
  code.
* [*Scanner*](https://oss-review-toolkit.org/ort/docs/tools/scanner) - uses configured source code scanners to detect
  license / copyright findings, abstracting the type of scanner.
* [*Advisor*](https://oss-review-toolkit.org/ort/docs/tools/advisor) - retrieves security advisories for used
  dependencies from configured vulnerability data services.
* [*Evaluator*](https://oss-review-toolkit.org/ort/docs/tools/evaluator) - evaluates custom policy rules along with
  custom license classifications against the data gathered in preceding stages and returns a list of policy violations,
  e.g. to flag license findings.
* [*Reporter*](https://oss-review-toolkit.org/ort/docs/tools/reporter) - presents results in various formats such as
  visual reports, Open Source notices or Bill-Of-Materials (BOMs) to easily identify dependencies, licenses, copyrights
  or policy rule violations.
* *Notifier* - sends result notifications via different channels (like [emails](./examples/example.notifications.kts)
  and / or JIRA tickets).

Also see the [list of related tools](https://oss-review-toolkit.org/ort/docs/related-tools) that help with running ORT.

## Documentation

For detailed information see the documentation on the [ORT Website](https://oss-review-toolkit.org/ort/).

# Installation

## System requirements

ORT is being continuously used on Linux, Windows and macOS by the
[core development team](https://github.com/orgs/oss-review-toolkit/people), so these operating systems are
considered to be well-supported.

To run the ORT binaries (also see [Installation from binaries](#from-binaries)) at least Java 11 is required. Memory and
CPU requirements vary depending on the size and type of project(s) to analyze / scan, but the general recommendation is
to configure Java with 8 GiB of memory and to use a CPU with at least 4 cores.

```shell
# This will give the Java Virtual Machine 8GB Memory.
export JAVA_OPTS="$JAVA_OPTS -Xmx8g"
```

If ORT requires external tools in order to analyze a project, these tools are listed by the `ort requirements` command.
If a package manager is not list listed there, support for it is integrated directly into ORT and does not require any
external tools to be installed.

## From binaries

Preliminary binary artifacts for ORT are currently available via
[JitPack](https://jitpack.io/#oss-review-toolkit/ort). Please note that due to limitations with the JitPack build
environment, the reporter is not able to create the Web App report.

## From sources

Install the following basic prerequisites:

* Git (any recent version will do).

Then clone this repository.

```shell
git clone https://github.com/oss-review-toolkit/ort
# If you intend to run tests, you have to clone the submodules too.
cd ort
git submodule update --init --recursive
```

### Build using Docker

Install the following basic prerequisites:

* Docker 18.09 or later (and ensure its daemon is running).
* Enable [BuildKit](https://docs.docker.com/develop/develop-images/build_enhancements/#to-enable-buildkit-builds) for
  Docker.

Change into the directory with ORT's source code and run `docker build -t ort .`. Alternatively, use the script at
`scripts/docker_build.sh` which also sets the ORT version from the Git revision.

### Build natively

Install these additional prerequisites:

* Java Development Kit (JDK) version 11 or later; also remember to set the `JAVA_HOME` environment variable accordingly.

Change into the directory with ORT's source code and run `./gradlew installDist` (on the first run this will bootstrap
Gradle and download all required dependencies).

## Basic usage

Depending on how ORT was installed, it can be run in the following ways:

* If the Docker image was built, use

  ```shell
  docker run ort --help
  ```

  You can find further hints for using ORT with Docker in the [documentation](./website/docs/guides/docker.md).

* If the ORT distribution was built from sources, use

  ```shell
  ./cli/build/install/ort/bin/ort --help
  ```

* If running directly from sources via Gradle, use

  ```shell
  ./gradlew cli:run --args="--help"
  ```

  Note that in this case the working directory used by ORT is that of the `cli` project, not the directory `gradlew` is
  located in (see https://github.com/gradle/gradle/issues/6074).

# Want to Help or have Questions?

All contributions are welcome. If you are interested in contributing, please read our
[contributing guide](https://github.com/oss-review-toolkit/.github/blob/main/CONTRIBUTING.md), and to get quick answers
to any of your questions we recommend you
[join our Slack community][2].

# License

Copyright (C) 2017-2023 [The ORT Project Authors](./NOTICE).

See the [LICENSE](./LICENSE) file in the root of this project for license details.

OSS Review Toolkit (ORT) is a [Linux Foundation project](https://www.linuxfoundation.org) and part of
[ACT](https://automatecompliance.org/).
