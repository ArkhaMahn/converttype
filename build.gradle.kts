import org.zaproxy.gradle.addon.AddOnStatus

plugins {
    java
    id("org.zaproxy.add-on") version "0.13.1"
}

group = "org.zaproxy.addon"
version = "1.0.0"
description =
    "Converts HTTP requests between content types (JSON, XML, SOAP, URL-encoded, multipart form-data, " +
        "YAML, GraphQL and plain text), converting both the request body and the relevant headers."

repositories {
    mavenCentral()
}

java {
    // ZAP 2.17.0 requires Java 17 or above.
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    implementation("org.json:json:20240303")
    implementation("org.yaml:snakeyaml:2.3")

    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

zapAddOn {
    addOnName.set("Content Type Converter")
    addOnStatus.set(AddOnStatus.BETA)
    zapVersion.set("2.17.0")

    manifest {
        author.set("Arkhamahn")
        url.set("https://github.com/h0tak88r/Convert-Type-Convert-All")
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}