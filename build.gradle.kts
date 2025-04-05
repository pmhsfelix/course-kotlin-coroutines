
buildscript {
    dependencies {
        "classpath"("org.jetbrains.kotlinx:kotlinx-knit:0.2.3")
    }
}

plugins {
    kotlin("jvm") version "2.1.0"
    id("org.jlleitschuh.gradle.ktlint") version "12.1.1"
}

group = "org.pedrofelix.courses"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib-jdk8"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")
    implementation("org.slf4j:slf4j-api:1.7.30")
    implementation("org.slf4j:slf4j-simple:1.7.30")

    implementation("io.ktor:ktor-client-android:2.3.13")
    implementation("io.ktor:ktor-client:2.3.11")

    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(21)
}
