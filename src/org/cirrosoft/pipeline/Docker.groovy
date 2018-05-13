package org.cirrosoft.pipeline

class Docker implements Serializable {
    public def steps
    ProjectTools projectTools
    Remote remote
    Docker(inputStep) {
        steps = inputStep
        projectTools = new ProjectTools(steps)
        remote = new Remote(steps)
    }

    public def installCommands = [
            amazonLinux: [
                    "uname -a",
                    "sudo yum update -y",
                    "sudo yum install docker -y",
                    "sudo service docker start",
                    "sudo usermod -a -G docker ec2-user"
            ]
    ]
    void buildCleanImageAsLatest(String imageName, String filename, ArrayList additionalImageTags = []) {
        def tagsString = ""
        for (tag in additionalImageTags) {
            tagsString += "-t " + imageName + ":" + tag + " "
        }
        steps.sh(script: "docker build ${tagsString} -t ${imageName}:latest .")
        steps.sh(script: "docker image save ${imageName}:latest > ${filename}")
        steps.sh(script: "docker rmi -f `docker images -a -q --filter=reference=\"${imageName}:*\"`")
    }

    void deployImageFile(
            String sshCred,
            String address,
            String imageFileString,
            String imageName,
            LinkedHashMap buildMap,
            String dbCredential = null,
            String dbAddress = null
    ) {
        remote.executeRemoteCommands(sshCred, address, ["rm -rf ${imageFileString}"]) // remove previous tar
        remote.scp(sshCred, address, imageFileString, imageFileString) // deploy new tar
        // Stop and cleanup old containers
        def runningContainers = remote.executeRemoteCommands(sshCred, address, ["docker ps -a -q --filter=\"ancestor=${imageName}:latest\""])
        runningContainers?.trim()?.eachLine {
            remote.executeRemoteCommands(sshCred, address, ["docker stop ${it}"])
            remote.executeRemoteCommands(sshCred, address, ["docker rm ${it}"])
        }
        // Deploy new container
        def javaParams = projectTools.generateJavaPropertiesString(buildMap)
        if (dbCredential && dbAddress) {
            steps.withCredentials([steps.usernamePassword(credentialsId: dbCredential, usernameVariable: 'DB_USERNAME', passwordVariable: 'DB_PASSWORD')]) {
                javaParams += "-Dspring.datasource.url=${dbAddress} -Dspring.datasource.username=${steps.DB_USERNAME} -Dspring.datasource.password=${steps.DB_PASSWORD}"
            }
        }
        def commands = [
                "docker image load -i ${imageFileString}",
                "sudo docker run -e JAVA_OPTS=\\\"${javaParams}\\\" -d -p \"80:8080\" ${imageName}:latest"
        ]
        remote.executeRemoteCommands(sshCred, address, commands)
    }

    void deployImage(
            String sshCred,
            String address,
            String imageNameAndTag,
            String params
    ) {
        // Stop and cleanup old containers
        def runningContainers = remote.executeRemoteCommands(sshCred, address, ["docker ps -a -q --filter=\"ancestor=${imageNameAndTag}\""])
        runningContainers?.trim()?.eachLine {
            remote.executeRemoteCommands(sshCred, address, ["docker stop ${it}"])
            remote.executeRemoteCommands(sshCred, address, ["docker rm ${it}"])
        }
        // Run
        def commands = [
                "sudo docker run ${params} ${imageNameAndTag}"
        ]
        remote.executeRemoteCommands(sshCred, address, commands)
    }

}
