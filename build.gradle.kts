plugins {
    java
}

group = "dev.ryvrook"
version = "1.9.0"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

// Which Hytale server artifact to compile against. Defaults live in gradle.properties
// and can be overridden with -Phytale_channel=release -Phytale_version=0.6.8
val hytaleChannel = (project.findProperty("hytale_channel") as String?)?.trim() ?: "release"
val hytaleVersion = (project.findProperty("hytale_version") as String?)?.trim() ?: "0.7.1"
require(hytaleChannel == "release" || hytaleChannel == "pre-release") {
    "hytale_channel must be \"release\" or \"pre-release\", got \"$hytaleChannel\""
}

repositories {
    mavenCentral()
    maven {
        name = "hytale-$hytaleChannel"
        url = uri("https://maven.hytale.com/$hytaleChannel")
    }
    maven {
        name = "helpchat"
        url = uri("https://repo.helpch.at/releases/")
    }
}

// Sibling jars Feats compiles against, the oldest version it supports so nothing newer is used by accident.
// TrackerAPI is the exception since 1.9.0, Feats compiles against 1.2.0 for the mod records in
// integration/ModKinds and still runs with 1.1.x, where that one class is never loaded.
// Each is given with -PtrackerApiJar=<path>, -PcoinageJar=<path> or -PatlasJar=<path>, else the sibling checkout next to this one,
// else the checkout under ~/Projects. Both are optional at runtime, see integration/TrackerBridge and
// integration/CoinageRewards, the only classes that touch them.
fun siblingJar(property: String, path: String): String {
    (project.findProperty(property) as String?)?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
    val candidates = listOf("../$path", System.getProperty("user.home") + "/Projects/$path")
    return candidates.firstOrNull { file(it).exists() } ?: candidates.first()
}
val trackerApiJar = siblingJar("trackerApiJar", "trackerapi/build/libs/TrackerAPI-1.2.0.jar")
val coinageJar = siblingJar("coinageJar", "coinage/build/libs/Coinage-api-1.3.0.jar")
val atlasJar = siblingJar("atlasJar", "atlas/build/libs/Atlas-1.9.0.jar")

dependencies {
    // Provided by the Hytale server at runtime; never bundled into the mod jar.
    compileOnly("com.hypixel.hytale:Server:$hytaleVersion")
    // Optional at runtime. Feats only touches it when the PlaceholderAPI plugin is installed.
    compileOnly("at.helpch:placeholderapi-hytale:1.0.8")
    // Optional at runtime. Only the bridge touches it, and only when TrackerAPI is installed.
    compileOnly(files(trackerApiJar))
    // Optional at runtime. Only CoinageRewards and CoinageFeats touch it, and only when Coinage is installed.
    compileOnly(files(coinageJar))
    // Optional at runtime. Only AtlasFeats touches it, and only when Atlas is installed.
    compileOnly(files(atlasJar))

    // Tests run without a server. The server jar is only on the test classpath for bson and the logger.
    testImplementation("com.hypixel.hytale:Server:$hytaleVersion")
    testImplementation(files(trackerApiJar))
    testImplementation(files(coinageJar))
    testImplementation(files(atlasJar))
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
    // The tracking benchmark prints its numbers, run with -Pbench to see them in the console.
    testLogging {
        showStandardStreams = project.hasProperty("bench")
        events("failed")
    }
    systemProperty("feats.bench", project.hasProperty("bench").toString())
    // The engine logger insists on owning java.util.logging before anything logs.
    systemProperty("java.util.logging.manager", "com.hypixel.hytale.logger.backend.HytaleLogManager")
    jvmArgs("-Xmx1G")
}

/**
 * Writes src/main/resources/lang/en-US/feats.lang from the code, every key with its English
 * text. LangFileTest fails when the committed file is behind, so run this after adding a feat or a string.
 */
val generateLang by tasks.registering(JavaExec::class) {
    group = "hytale"
    description = "Regenerates the bundled English language file, src/main/resources/lang/en-US/feats.lang, from the code."
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("dev.ryvrook.feats.lang.WriteLangFile")
    systemProperty("java.util.logging.manager", "com.hypixel.hytale.logger.backend.HytaleLogManager")
    workingDir = projectDir
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(25)
}

tasks.jar {
    archiveBaseName.set("Feats")
    archiveVersion.set(project.version.toString())
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

/**
 * Copies the built jar into the local Hytale UserData/Mods folder so it loads in
 * singleplayer worlds. Override the target with -PmodsDir=/path/to/Mods.
 */
val installMod by tasks.registering(Copy::class) {
    group = "hytale"
    description = "Builds the mod and copies it into the local Hytale UserData/Mods folder."
    dependsOn(tasks.jar)

    val home = System.getProperty("user.home")
    val os = System.getProperty("os.name").lowercase()
    val defaultMods = when {
        os.contains("mac") -> "$home/Library/Application Support/Hytale/UserData/Mods"
        os.contains("win") -> (System.getenv("APPDATA") ?: "$home/AppData/Roaming") + "/Hytale/UserData/Mods"
        else -> "$home/.local/share/Hytale/UserData/Mods"
    }
    val target = (project.findProperty("modsDir") as String?) ?: defaultMods

    from(tasks.jar.flatMap { it.archiveFile })
    into(target)
    doLast { println("Installed ${tasks.jar.get().archiveFileName.get()} -> $target") }
}
