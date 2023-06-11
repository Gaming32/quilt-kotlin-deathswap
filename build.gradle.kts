plugins {
    id("org.quiltmc.loom") version "1.1.+"
    kotlin("jvm") version "1.8.21"
    java
}

version = "1.0.2+1.19.4"
group = "io.github.gaming32"

repositories {
    mavenCentral()

    maven {
        name = "ParchmentMC"
        url = uri("https://maven.parchmentmc.org")
    }

    maven("https://maven.nucleoid.xyz")

    maven("https://jitpack.io")
}

dependencies {
    minecraft(libs.minecraft)
    @Suppress("UnstableApiUsage")
    mappings(loom.layered {
        officialMojangMappings()
        parchment("org.parchmentmc.data:parchment-1.19.3:2023.03.12@zip")
    })
    modImplementation(libs.quilt.loader)

    modImplementation(libs.quilted.fabric.api)
    modImplementation("org.quiltmc.quilt-kotlin-libraries:quilt-kotlin-libraries:2.0.2+kt.1.8.20+flk.1.9.3")

    modCompileOnly("com.github.iPortalTeam.ImmersivePortalsMod:imm_ptl_core:v2.3.1-1.19") {
        isTransitive = false
    }
    modCompileOnly("com.github.iPortalTeam.ImmersivePortalsMod:q_misc_util:v2.3.1-1.19") {
        isTransitive = false
    }

    include(modImplementation("xyz.nucleoid:fantasy:0.4.10+1.19.4")!!)
}

loom {
    serverOnlyMinecraftJar()
}

tasks.processResources {
    inputs.property("version", version)

    filesMatching("quilt.mod.json") {
        expand("version" to version)
    }
}

java {
    withSourcesJar()
}

kotlin {
    jvmToolchain(17)
}
