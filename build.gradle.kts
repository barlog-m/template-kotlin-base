import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.gradle.plugins.ide.idea.model.IdeaLanguageLevel
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    application
    idea
    kotlin("jvm") version "1.3.21"

    id("com.github.johnrengelman.shadow") version "5.0.0"
    // gradle dependencyUpdates -Drevision=release
    id("com.github.ben-manes.versions") version "0.21.0"
    id("com.palantir.docker") version "0.21.0"
}

repositories {
    jcenter()
}

val coroutinesVer = "1.1.0"

val kotlinLoggingVer = "1.6.25"
val log4jVer = "2.11.2"
val slf4jVer = "1.7.25"

val junitJupiterVer = "5.4.0"

dependencies {
    implementation(kotlin("stdlib-jdk8"))
//    implementation(kotlin("reflect"))
//    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesVer")

    implementation("io.github.microutils:kotlin-logging:$kotlinLoggingVer")

    implementation("org.slf4j:slf4j-api:$slf4jVer")
    runtime("org.apache.logging.log4j:log4j-core:$log4jVer")
    runtime("org.apache.logging.log4j:log4j-slf4j-impl:$log4jVer")

    testImplementation(kotlin("test-junit5"))
    testImplementation("org.junit.jupiter:junit-jupiter-api:$junitJupiterVer")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:$junitJupiterVer")
}

val appName = "app"
val appVer by lazy { "0.0.1+${gitRev()}" }

application {
    mainClassName = "app.AppKt"
    applicationName = appName
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

idea {
    project {
        languageLevel = IdeaLanguageLevel(JavaVersion.VERSION_11)
    }
    module {
        isDownloadJavadoc = true
        isDownloadSources = true
    }
}

tasks {
    withType(KotlinCompile::class).configureEach {
        kotlinOptions {
            jvmTarget = JavaVersion.VERSION_1_8.toString()
            freeCompilerArgs = listOf("-progressive")
        }
    }

    withType(JavaCompile::class).configureEach {
        options.isFork = true
    }

    withType(Test::class).configureEach {
        maxParallelForks = (Runtime.getRuntime().availableProcessors() / 2).takeIf { it > 0 } ?: 1

        useJUnitPlatform()
        testLogging {
            events = setOf(TestLogEvent.PASSED, TestLogEvent.SKIPPED, TestLogEvent.FAILED)
        }

        reports.html.isEnabled = false
    }

    wrapper {
        gradleVersion = "5.2.1"
        distributionType = Wrapper.DistributionType.ALL
    }

    jar {
        manifest {
            attributes(
                "Implementation-Title" to appName,
                "Implementation-Version" to appVer
            )
        }
    }

    shadowJar {
        archiveBaseName.set(appName)
        archiveVersion.set(appVer)
        archiveClassifier.set("")
    }

    build { dependsOn("shadowJar") }

    docker {
        val build = build.get()
        val shadowJar = shadowJar.get()

        dependsOn(build)
        name = "${project.group}/${shadowJar.archiveBaseName.get()}"
        files(shadowJar.outputs)
        setDockerfile(file("$projectDir/src/main/docker/Dockerfile"))
        buildArgs(mapOf(
            "JAR_FILE" to shadowJar.archiveFileName.get(),
            "JAVA_OPTS" to "-XX:-TieredCompilation",
            "PORT" to "8080"
        ))
        pull(true)
    }

    register("stage") {
        dependsOn("build", "clean")
    }
}

fun gitRev() = ProcessBuilder("git", "rev-parse", "HEAD").start().let { process ->
    process.waitFor(100, TimeUnit.MILLISECONDS)
    process.inputStream.bufferedReader().readLine() ?: "none"
}
