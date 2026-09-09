import net.lesimc.build.PrepareTestServer
import net.lesimc.build.ResourcePackHost
import net.lesimc.build.RunWikiEditor
import net.lesimc.build.StartResourcePackHost
import net.lesimc.build.StopTestServer
import org.gradle.api.tasks.bundling.Zip

plugins {
    java
    id("xyz.jpenilla.run-paper") version "3.1.0"
}

val buildResourcePack = tasks.register<Zip>("buildResourcePack") {
    group = "build"
    description = "Builds the standalone GuideLegowelt resource pack."
    archiveFileName = "GuideLegowelt-resource-pack-${project.version}.zip"
    destinationDirectory = layout.buildDirectory.dir("distributions")
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true

    from(layout.projectDirectory.dir("resource-pack")) {
        exclude("README.md")
        exclude("source-images/**")
    }
}

val testPackAddress = providers.gradleProperty("guidelegowelt.testPackAddress").getOrElse("127.0.0.1")
val testPackPort = providers.gradleProperty("guidelegowelt.testPackPort").map { it.toInt() }.getOrElse(8123)
val testServerAddress = providers.gradleProperty("guidelegowelt.testServerAddress").getOrElse("127.0.0.1")
val testServerPort = providers.gradleProperty("guidelegowelt.testServerPort").map { it.toInt() }.getOrElse(25565)
val testRconPort = providers.gradleProperty("guidelegowelt.testRconPort").map { it.toInt() }.getOrElse(25575)
val testRconPassword = providers.gradleProperty("guidelegowelt.testRconPassword")
    .getOrElse("guidelegowelt-local-test")
val testResourcePackHost = gradle.sharedServices.registerIfAbsent(
    "guideLegoweltResourcePackHost",
    ResourcePackHost::class
) {
    parameters.packFile.set(buildResourcePack.flatMap { it.archiveFile })
    parameters.bindAddress.set(testPackAddress)
    parameters.port.set(testPackPort)
    maxParallelUsages.set(1)
}

val resourcePackArchive = buildResourcePack.flatMap { it.archiveFile }

val runWikiEditor = tasks.register<RunWikiEditor>("runWikiEditor") {
    group = "application"
    description = "Starts the local GuideLegowelt pages.yml and wiki image editor."
    editorDirectory.set(layout.projectDirectory.dir("tools/wiki-editor"))
    pagesFile.set(layout.projectDirectory.file("src/main/resources/pages.yml"))
    fontFile.set(layout.projectDirectory.file("resource-pack/assets/guidelegowelt/font/wiki.json"))
    texturesDirectory.set(layout.projectDirectory.dir("resource-pack/assets/guidelegowelt/textures/font"))
    sourceImagesDirectory.set(layout.projectDirectory.dir("resource-pack/source-images"))
    bindAddress.set(providers.gradleProperty("guidelegowelt.wikiEditorAddress").getOrElse("127.0.0.1"))
    port.set(providers.gradleProperty("guidelegowelt.wikiEditorPort").map { it.toInt() }.getOrElse(8130))
}

val prepareTestServer = tasks.register<PrepareTestServer>("prepareTestServer") {
    group = "application"
    description = "Builds GuideLegowelt and prepares its pages and resource pack for the test server."
    dependsOn(tasks.named("build"))
    bundledPages.set(layout.projectDirectory.file("src/main/resources/pages.yml"))
    resourcePackArchive.set(buildResourcePack.flatMap { it.archiveFile })
    testServerPages.set(layout.projectDirectory.file("run/plugins/GuideLegowelt/pages.yml"))
    testServerProperties.set(layout.projectDirectory.file("run/server.properties"))
    packAddress.set(testPackAddress)
    packPort.set(testPackPort)
    serverAddress.set(testServerAddress)
    serverPort.set(testServerPort)
    rconPort.set(testRconPort)
    rconPassword.set(testRconPassword)
}

val startResourcePackHost = tasks.register<StartResourcePackHost>("startResourcePackHost") {
    group = "application"
    description = "Starts the local GuideLegowelt resource-pack host for the current Gradle run."
    dependsOn(prepareTestServer)
    host.set(testResourcePackHost)
    usesService(testResourcePackHost)
}

val stopTestServer = tasks.register<StopTestServer>("stopTestServer") {
    group = "application"
    description = "Gracefully stops the Paper test server and its Gradle resource-pack host."
    serverAddress.set(testServerAddress)
    serverPort.set(testServerPort)
    packPort.set(testPackPort)
    rconPort.set(testRconPort)
    rconPassword.set(testRconPassword)
    shutdownTimeoutSeconds.set(30)
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.2.build.+")

    testImplementation("io.papermc.paper:paper-api:26.2.build.+")
    testImplementation("org.junit.jupiter:junit-jupiter:5.13.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

tasks {
    runServer {
        dependsOn(startResourcePackHost)
        usesService(testResourcePackHost)
        minecraftVersion("26.2")
        jvmArgs("-Xms2G", "-Xmx2G")
    }

    compileJava {
        options.encoding = "UTF-8"
        options.release = 25
    }

    test {
        useJUnitPlatform()
    }

    processResources {
        val props = mapOf("version" to version)
        inputs.properties(props)

        filesMatching("paper-plugin.yml") {
            expand(props)
        }
    }

    build {
        dependsOn(buildResourcePack)
    }

    register("runTestServer") {
        group = "application"
        description = "Builds and deploys GuideLegowelt, hosts its resource pack, and starts the Paper test server."
        dependsOn(runServer)
    }
}
