grails.project.work.dir = 'target'
grails.project.source.level = 1.6

grails.project.dependency.resolver = 'maven'
grails {
    project {
        dependency {
            distribution = {
                remoteRepository(id: "internal", url: "file://${basedir}/mvn-repo")
            }
            resolution = {
                inherits 'global'
                log 'warn'
                repositories {
                    grailsCentral()
                    mavenLocal()
                    mavenCentral()
                }
                dependencies {
                }
                plugins {
                    build ':release:3.0.1', ':rest-client-builder:1.0.3', {
                        export = false
                    }

                    compile ':resources:1.2.1'
                    compile ':aws-sdk:1.6.9'
                }

            }

        }

    }
}

agorapulse.repositories.url = 'http://repository.agorapulse.com/nexus/content/repositories'
grails.project.dependency.distribution = {
    // To deploy run "grails maven-deploy --repository=snapshots"
    remoteRepository(id: 'snapshots', url: "${agorapulse.repositories.url}/snapshots/") {
        authentication username: 'deployment', password: 'eej-yoylm-of-cev'
    }
    // To deploy run "grails maven-deploy --repository=releases"
    remoteRepository(id: 'releases', url: "${agorapulse.repositories.url}/releases/") {
        authentication username: 'deployment', password: 'eej-yoylm-of-cev'
    }
}
grails.project.repos.default = 'snapshots'