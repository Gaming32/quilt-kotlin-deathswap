plugins {
    id("org.quiltmc.loom") version "1.1.+"
    kotlin("jvm") version "1.8.22"
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
    modImplementation("org.quiltmc.quilt-kotlin-libraries:quilt-kotlin-libraries:2.1.0+kt.1.8.22+flk.1.9.4")

    modCompileOnly("com.github.iPortalTeam.ImmersivePortalsMod:imm_ptl_core:v3.1.0-mc1.20.1") {
        isTransitive = false
    }
    modCompileOnly("com.github.iPortalTeam.ImmersivePortalsMod:q_misc_util:v3.1.0-mc1.20.1") {
        isTransitive = false
    }

    include(modImplementation("xyz.nucleoid:fantasy:0.4.11+1.20-rc1")!!)

    include(implementation(annotationProcessor("com.github.LlamaLad7:MixinExtras:0.2.0-beta.8")!!)!!)
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
