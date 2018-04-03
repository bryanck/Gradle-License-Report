package com.github.jk1.license.render

import com.github.jk1.license.ImportedModuleBundle
import com.github.jk1.license.LicenseReportPlugin.LicenseReportExtension
import com.github.jk1.license.ModuleData
import com.github.jk1.license.ProjectData
import groovy.json.JsonBuilder
import org.gradle.api.Project

import static com.github.jk1.license.render.LicenseDataCollector.singleModuleLicenseInfo
import static com.github.jk1.license.render.LicenseDataCollector.multiModuleLicenseInfo

/**
 *
 * This renderer has two modes:  single-license-per-module  and  all-licenses-per-module.
 * The mode can be controlled with the constructor parameter  onlyOneLicensePerModule   and depending on
 * the mode, the result looks differently:
 *
 * single-license-per-module
 * =========================
 * Renders a simply structured JSON dependency report
 *
 *  {
 *  "dependencies": [
 *   {
 *      "moduleName": "...",
 *      "moduleUrl": "...",
 *      "moduleVersion": "...",
 *      "moduleLicense": "...",
 *      "moduleLicenseUrl": "...",
 *   }, ...],
 *  "importedModules": [
 *   {
 *       "name": "...",
 *       "dependencies": [
 *           "moduleName": "...",
 *           "moduleUrl": "...",
 *           "moduleVersion": "...",
 *           "moduleLicense": "...",
 *           "moduleLicenseUrl": "..."
 *       ]
 *   }, ...]
 * }
 *
 *
 * all-licenses-per-module
 * =======================
 * Renders a structured JSON with all licenses per module
 *
 *  {
 *  "dependencies": [
 *   {
 *      "moduleName": "...",
 *      "moduleVersion": "...",
 *      "moduleUrls": [ "..." ],
 *      "moduleLicenses": [
 *          {
 *              "moduleLicense": "...",
 *              "moduleLicenseUrl": "..."
 *          }, ... ]
 *   }, ...],
 *  "importedModules": [
 *   {
 *       "name": "...",
 *       "dependencies": [
 *           "moduleName": "...",
 *           "moduleVersion": "...",
 *           "moduleUrl": "...",
 *           "moduleLicense": "...",
 *           "moduleLicenseUrl": "..."
 *       ]
 *   }, ...]
 * }
 *
 */

class JsonReportRenderer implements ReportRenderer {

    private String fileName
    private Project project
    private LicenseReportExtension config
    private File output
    private Boolean onlyOneLicensePerModule

    JsonReportRenderer(String fileName = 'index.json', boolean onlyOneLicensePerModule = true) {
        this.fileName = fileName
        this.onlyOneLicensePerModule = onlyOneLicensePerModule
    }

    void render(ProjectData data) {
        project = data.project
        config = project?.licenseReport
        output = new File(config.outputDir, fileName)

        def jsonReport = [:]

        if (onlyOneLicensePerModule) {
            jsonReport.dependencies = renderSingleLicensePerModule(data.allDependencies)
        } else {
            jsonReport.dependencies = renderAllLicensesPerModule(data.allDependencies)
        }
        jsonReport.importedModules = readImportedModules(data.importedModules)

        output.text = new JsonBuilder(trimAndRemoveNullEntries(jsonReport)).toPrettyString()
    }

    def renderSingleLicensePerModule(Collection<ModuleData> allDependencies) {
        allDependencies.collect {
            String moduleName = "${it.group}:${it.name}"
            String moduleVersion = it.version
            def (String moduleUrl, String moduleLicense, String moduleLicenseUrl) = singleModuleLicenseInfo(it)
            trimAndRemoveNullEntries([moduleName      : moduleName,
                                      moduleUrl       : moduleUrl,
                                      moduleVersion   : moduleVersion,
                                      moduleLicense   : moduleLicense,
                                      moduleLicenseUrl: moduleLicenseUrl])
        }.sort { it.moduleName }
    }

    def renderAllLicensesPerModule(Collection<ModuleData> allDependencies) {
        allDependencies.collect {
            String moduleName = "${it.group}:${it.name}"
            String moduleVersion = it.version
            def info = multiModuleLicenseInfo(it)

            def jsonLicenseList = info.licenses.collect {
                [moduleLicense: it.name, moduleLicenseUrl: it.url]
            }

            trimAndRemoveNullEntries([moduleName    : moduleName,
                                      moduleVersion : moduleVersion,
                                      moduleUrls    : info.moduleUrls,
                                      moduleLicenses: jsonLicenseList])
        }.sort { it.moduleName }
    }

    static def readImportedModules(def incModules) {
        incModules.collect { ImportedModuleBundle importedModuleBundle ->
            trimAndRemoveNullEntries([moduleName  : importedModuleBundle.name,
                                      dependencies: readModuleDependencies(importedModuleBundle.modules)])
        }.sort { it.moduleName }
    }

    static def readModuleDependencies(def modules) {
        modules.collectEntries {
            trimAndRemoveNullEntries([moduleName      : it.name,
                                      moduleUrl       : it.projectUrl,
                                      moduleVersion   : it.version,
                                      moduleLicense   : it.license,
                                      moduleLicenseUrl: it.licenseUrl])
        }
    }

    static def trimAndRemoveNullEntries(def map) {
        map.collectEntries { k, v ->
            v ? [(k): v instanceof String ? v.trim() : v] : [:]
        }
    }
}
