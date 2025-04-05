import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

buildscript {
    dependencies {
        "classpath"("org.jetbrains.kotlinx:kotlinx-knit:0.2.3")
    }
}

plugins {
    kotlin("jvm") version "1.7.0"
}

group = "org.pedrofelix.courses"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib-jdk8"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.2")
    implementation("org.slf4j:slf4j-api:1.7.30")
    implementation("org.slf4j:slf4j-simple:1.7.30")

    implementation("io.ktor:ktor-client-android:1.6.5")
    implementation("io.ktor:ktor-client:1.6.5")

    testImplementation(kotlin("test-junit"))

}

tasks.test {
    useJUnit()
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "11"
}
