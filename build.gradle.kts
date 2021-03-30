import okhttp3.*
import org.kodein.internal.gradle.*

plugins {
    kotlin("jvm") version "1.3.72"
    `kotlin-dsl`
    `maven-publish`
    id("org.ajoberstar.git-publish") version "3.0.0"
    id("org.ajoberstar.grgit") version "4.1.0"
}

buildscript {
    repositories {
        mavenCentral()
    }
    dependencies {
        classpath("com.squareup.okhttp3:okhttp:4.9.0")
    }
}

allprojects {
    group = "org.kodein.internal.gradle"
    version = "6.5.0-kotlin-1.5.0-M2-SNAPSHOT"
}

repositories {
    mavenLocal()
    mavenCentral()
    google()
    maven(url = "https://plugins.gradle.org/m2/")
    maven(url = "https://ajoberstar.github.io/bintray-backup/")
    maven(url = "https://maven.pkg.jetbrains.space/kotlin/p/dokka/dev")
}

dependencies {
    implementation(gradleApi())
    implementation(gradleKotlinDsl())

    api(project(":kodein-internal-gradle-versions"))
    api("org.jetbrains.kotlin:kotlin-gradle-plugin:${KodeinVersions.kotlin}")
    api("com.android.tools.build:gradle:${KodeinVersions.androidBuildTools}")
    api("org.jetbrains.dokka:dokka-gradle-plugin:${KodeinVersions.dokka}")
    api("org.jetbrains.dokka:dokka-core:${KodeinVersions.dokka}")
}

kotlin.target.compilations.all {
    kotlinOptions.jvmTarget = "1.8"
}

kotlin.sourceSets.all {
    languageSettings.progressiveMode = true
}

fun getPublishingVersion(): String {
    val snapshotNumber: String? by project
    val gitRef: String? by project
    val eapBranch = gitRef?.split("/")?.last() ?: "dev"
    return if (snapshotNumber != null) "${project.version}-$eapBranch-$snapshotNumber" else project.version.toString()
}

allprojects {
    afterEvaluate {
        val sourcesJar = task<Jar>("sourcesJar") {
            @Suppress("UnstableApiUsage")
            archiveClassifier.set("sources")
            duplicatesStrategy = DuplicatesStrategy.EXCLUDE
            from(sourceSets["main"].allSource)
        }

        publishing.publications.withType<MavenPublication> {
            version = getPublishingVersion()
            artifact(sourcesJar) {
                classifier = "sources"
            }
            pom {
                licenses {
                    license {
                        name.set("MIT")
                        url.set("https://opensource.org/licenses/MIT")
                    }
                }
                url.set("https://github.com/Kodein-Framework/kodein-internal-gradle-plugin")
                issueManagement {
                    name.set("Github")
                    url.set("https://github.com/Kodein-Framework/kodein-internal-gradle-plugin/issues")
                }
                scm {
                    connection.set("https://github.com/Kodein-Framework/kodein-internal-gradle-plugin.git")
                    url.set("https://github.com/Kodein-Framework/kodein-internal-gradle-plugin")
                }
            }
        }
    }
}

task("validateVersionBeforeGitPublish") {
    group = "publishing"
    doLast {
        val publishingVersion = getPublishingVersion()

        // Allow snapshots to be overridden
        if(!publishingVersion.matches("""^(\d*)\.(\d*)\.(\d*)$""".toRegex())) return@doLast

        // Verify that the SemVer version does not exist on the mvn-repo branch
        //  https://raw.githubusercontent.com/Kodein-Framework/kodein-internal-gradle-plugin/mvn-repo/org/kodein/internal/gradle/kodein-internal-gradle-plugin/5.5.0/kodein-internal-gradle-plugin-5.5.0.pom
        val url = "https://raw.githubusercontent.com/Kodein-Framework/kodein-internal-gradle-plugin"
        val request = Request.Builder()
            .url("$url/mvn-repo/org/kodein/internal/gradle/kodein-internal-gradle-plugin/$publishingVersion/kodein-internal-gradle-plugin-$publishingVersion.pom")
            .get()
            .build()
        val res = OkHttpClient().newCall(request).execute()
        if (res.code == 200) error("Version ($publishingVersion) already published on $url.")
    }
}

tasks.create<Sync>("copyMavenLocalArtifacts") {
    group = "publishing"
    dependsOn("validateVersionBeforeGitPublish")
    val publishingVersion = getPublishingVersion()
    val userHome = System.getProperty("user.home")
    val groupDir = project.group.toString().replace('.', '/')
    val localRepository = "$userHome/.m2/repository/$groupDir/"

    from(localRepository) {
        include("*/$publishingVersion/**")
    }

    into("$buildDir/mvn-repo/$groupDir/")
}

val (gitUser, gitPassword) = (project.findProperty("com.github.http.auth") as? String)?.run {
    val auth = split(":")
    auth[0] to auth[1]
} ?: System.getenv("GIT_USER") as String? to System.getenv("GIT_PASSWORD") as String?
if(gitUser != null && gitPassword != null) {
    System.setProperty("org.ajoberstar.grgit.auth.username", gitUser)
    System.setProperty("org.ajoberstar.grgit.auth.password", gitPassword)
} else {
    logger.warn("Missing git credentials. We won't be able to publish any version.")
}

gitPublish {
    repoUri.set("https://github.com/Kodein-Framework/kodein-internal-gradle-plugin.git")
    branch.set("mvn-repo")
    contents.from("$buildDir/mvn-repo")
    preserve {
        include("**")
    }
    val head = grgit.head()
    commitMessage.set("${head.abbreviatedId}: ${getPublishingVersion()} : ${head.fullMessage}")
}

tasks["gitPublishCopy"].dependsOn("copyMavenLocalArtifacts")

tasks["gitPublishCommit"].doFirst {
    if (!grgit.status().isClean) {
        error("Refusing to commit new pages on a non-clean repo. Please commit first.\n${grgit.status()}")
    }
}
