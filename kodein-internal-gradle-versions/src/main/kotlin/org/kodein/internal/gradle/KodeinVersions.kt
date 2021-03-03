package org.kodein.internal.gradle

import org.gradle.api.*

object KodeinVersions {

    const val kotlin = "1.4.31"
    const val dokka = "1.4.20.2-dev-74"
    const val androidBuildTools = "4.0.2"
    const val androidNdk = "21.3.6528147"

}

class KodeinVersionsPlugin : Plugin<Project> {

    private fun Project.applyPlugin() {
        extensions.add("kodeinVersions", KodeinVersions)
    }

    override fun apply(project: Project) = project.applyPlugin()

}
