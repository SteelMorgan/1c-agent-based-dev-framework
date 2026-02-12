plugins {
    java
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

group = "io.github.onec"
version = "0.1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

dependencies {
    // mdclasses для enum-ов и моделей
    implementation("io.github.1c-syntax:mdclasses:0.17.4")
    
    // Jackson для JSON DSL
    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.+")
    
    // Lombok
    compileOnly("org.projectlombok:lombok:1.18.+")
    annotationProcessor("org.projectlombok:lombok:1.18.+")
    
    // Testing
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.+")
    testImplementation("org.assertj:assertj-core:3.25.+")
    testCompileOnly("org.projectlombok:lombok:1.18.+")
    testAnnotationProcessor("org.projectlombok:lombok:1.18.+")
}

tasks.test {
    useJUnitPlatform()
}

tasks.shadowJar {
    archiveBaseName.set("xml-gen")
    archiveClassifier.set("")
    manifest {
        attributes["Main-Class"] = "io.github.onec.xmlgen.cli.Main"
    }
}

tasks.build {
    dependsOn(tasks.shadowJar)
}
