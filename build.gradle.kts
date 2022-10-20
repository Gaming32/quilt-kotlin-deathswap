import org.jetbrains.kotlin.config.KotlinCompilerVersion

plugins {
	id("maven-publish")
	alias(libs.plugins.quilt.loom)
	id("org.jetbrains.kotlin.jvm") version "1.7.20"
}

archivesBaseName = project.archives_base_name
version = project.version
group = project.maven_group

repositories {
	mavenCentral()
	// Add repositories to retrieve artifacts from in here.
	// You should only use this when depending on other mods because
	// Loom adds the essential maven repositories to download Minecraft and libraries from automatically.
	// See https://docs.gradle.org/current/userguide/declaring_repositories.html
	// for more information about repositories.
}

// All the dependencies are declared at gradle/libs.version.toml and referenced with "libs.<id>"
// See https://docs.gradle.org/current/userguide/platforms.html for information on how version catalogs work.
dependencies {
	minecraft libs.minecraft
	mappings variantOf(libs.quilt.mappings) { classifier "intermediary-v2" }
	// Replace the above line with the block below if you want to use Mojang mappings as your primary mappings, falling back on QM for parameters and Javadocs
	/*
	mappings loom.layered {
		mappings "org.quiltmc:quilt-mappings:${libs.versions.quilt.mappings.get()}:intermediary-v2"
		officialMojangMappings()
	}
	*/
	modImplementation libs.quilt.loader

	// QSL is not a complete API; You will need Quilted Fabric API to fill in the gaps.
	// Quilted Fabric API will automatically pull in the correct QSL version.
	modImplementation libs.quilted.fabric.api
	implementation(kotlin("stdlib", KotlinCompilerVersion.VERSION))
	// modImplementation libs.bundles.quilted.fabric.api // If you wish to use Fabric API"s deprecated modules, you can replace the above line with this one
	modImplementation "org.quiltmc.quilt-kotlin-libraries:quilt-kotlin-libraries:0.1.4+kt.1.7.20+flk.1.8.5-SNAPSHOT"
	include("org.quiltmc.quilt-kotlin-libraries:quilt-kotlin-libraries:0.1.4+kt.1.7.20+flk.1.8.5-SNAPSHOT")
}

processResources {
	inputs.property "version", version

	filesMatching("quilt.mod.json") {
		expand "version": version
	}
}

tasks.withType(JavaCompile).configureEach {
	it.options.encoding = "UTF-8"
	// Minecraft 1.18 (1.18-pre2) upwards uses Java 17.
	it.options.release = 17
}

java {
	// Still required by IDEs such as Eclipse and Visual Studio Code
	sourceCompatibility = JavaVersion.VERSION_17
	targetCompatibility = JavaVersion.VERSION_17

	// Loom will automatically attach sourcesJar to a RemapSourcesJar task and to the "build" task if it is present.
	// If you remove this line, sources will not be generated.
	withSourcesJar()

	// If this mod is going to be a library, then it should also generate Javadocs in order to aid with developement.
	// Uncomment this line to generate them.
	// withJavadocJar()
}

// If you plan to use a different file for the license, don"t forget to change the file name here!
jar {
	from("LICENSE") {
		rename { "${it}_${archivesBaseName}" }
	}
}

// Configure the maven publication
publishing {
	publications {
		mavenJava(MavenPublication) {
			from components.java
		}
	}

	// See https://docs.gradle.org/current/userguide/publishing_maven.html for information on how to set up publishing.
	repositories {
		// Add repositories to publish to here.
		// Notice: This block does NOT have the same function as the block in the top level.
		// The repositories here will be used for publishing your artifact, not for
		// retrieving dependencies.
	}
}
compileKotlin {
	kotlinOptions {
		jvmTarget = "17"
	}
}
compileTestKotlin {
	kotlinOptions {
		jvmTarget = "17"
	}
}
