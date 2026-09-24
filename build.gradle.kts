plugins {
    id("java")
    id("com.gradleup.shadow") version "9.6.1"
}

group = "mu.nada"
version = findProperty("releaseVersion")?.toString() ?: "1.0-SNAPSHOT"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("com.velocitypowered:velocity-api:4.0.0")
    annotationProcessor("com.velocitypowered:velocity-api:4.0.0")

    // Optional compile-time dependency on sibling nadamu-auth if present
    compileOnly(files("../nadamu-auth/build/libs/NadamuAuth-1.0-SNAPSHOT.jar", "../nadamu-auth/build/libs/nadamu-auth.jar"))

    testImplementation(platform("org.junit:junit-bom:6.1.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("com.velocitypowered:velocity-api:4.0.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks {
    test {
        useJUnitPlatform()
    }

    shadowJar {
        archiveClassifier.set("")
    }

    build {
        dependsOn(shadowJar)
    }
}
