plugins {
    id("java")
    id("idea")
    id("maven-publish")
    id("net.neoforged.moddev") version "2.0.42-beta"
}

val modId = "eira-core"
val minecraftVersion = "1.21.4"
val neoForgeVersion = "21.4.75-beta"

version = "1.0.0"
group = "org.eira"

base {
    archivesName.set(modId)
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
    withSourcesJar()
    withJavadocJar()
}

repositories {
    mavenCentral()
    maven {
        name = "NeoForged"
        url = uri("https://maven.neoforged.net/releases")
    }
}

dependencies {
    // Gson for JSON handling
    implementation("com.google.code.gson:gson:2.10.1")
}

neoForge {
    version = neoForgeVersion
    
    parchment {
        minecraftVersion = "1.21.4"
        mappingsVersion = "2024.12.07"
    }
    
    runs {
        create("client") {
            client()
            gameDirectory.set(file("run"))
        }
        
        create("server") {
            server()
            gameDirectory.set(file("run-server"))
            programArgument("--nogui")
        }
    }
    
    mods {
        create(modId) {
            sourceSet(sourceSets.main.get())
        }
    }
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

tasks.withType<Javadoc> {
    options.encoding = "UTF-8"
}

// Publishing for other mods to depend on
publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            
            groupId = "org.eira"
            artifactId = modId
            version = project.version.toString()
            
            pom {
                name.set("Eira Core")
                description.set("Core API and shared services for the Eira mod ecosystem")
                url.set("https://github.com/eira-org/eira-core")
                
                licenses {
                    license {
                        name.set("MIT License")
                        url.set("https://opensource.org/licenses/MIT")
                    }
                }
                
                developers {
                    developer {
                        name.set("Eira Organization")
                        url.set("https://eira.org")
                    }
                }
            }
        }
    }
    
    repositories {
        maven {
            name = "EiraRepo"
            url = uri("https://maven.eira.org/releases")
            credentials {
                username = System.getenv("MAVEN_USERNAME") ?: ""
                password = System.getenv("MAVEN_PASSWORD") ?: ""
            }
        }
    }
}
