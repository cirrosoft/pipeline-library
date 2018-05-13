package org.cirrosoft.pipeline

class Flyway implements Serializable {
    public def steps
    Flyway(inputStep) {steps = inputStep}
    void migrateWithGradle(String dbCredential, url) {
        steps.withCredentials([steps.usernamePassword(credentialsId: dbCredential, usernameVariable: 'DB_USERNAME', passwordVariable: 'DB_PASSWORD')]) {
            steps.timeout(1) {
                steps.waitUntil {
                    steps.script {
                        steps.echo "Waiting for response from ${url}"
                        def result = steps.sh(script: """./gradlew -Dflyway.user=${steps.DB_USERNAME} -Dflyway.password=${steps.DB_PASSWORD} -Dflyway.url=${url}  flywayMigrate""", returnStatus: true)
                        return (result == 0);
                    }
                }
            }

        }
    }
}
