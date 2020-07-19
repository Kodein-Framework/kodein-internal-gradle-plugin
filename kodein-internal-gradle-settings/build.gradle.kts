plugins {
    kotlin("jvm")
    id("org.gradle.kotlin.kotlin-dsl")
    `maven-publish`
}

repositories {
    jcenter()
}

dependencies {
    implementation(gradleApi())
    implementation(gradleKotlinDsl())

    implementation(project(":kodein-internal-gradle-versions"))
}

kotlin.target.compilations.all {
    kotlinOptions.jvmTarget = "1.8"
}
