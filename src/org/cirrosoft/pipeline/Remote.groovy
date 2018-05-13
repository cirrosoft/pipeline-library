package org.cirrosoft.pipeline

class Remote implements Serializable {
    public def steps
    Remote(inputStep) {steps = inputStep}

    String executeRemoteCommands(String credentialId, String address, ArrayList commands) {
        def lastResult = ""
        address = address.trim()
        steps.withCredentials([steps.sshUserPrivateKey(credentialsId: credentialId, keyFileVariable: 'SSH_KEYFILE', passphraseVariable: 'SSH_PASSWORD', usernameVariable: 'SSH_USERNAME')]) {
            for (command in commands) {
                steps.sh """
            ssh -i ${steps.SSH_KEYFILE} -o StrictHostKeyChecking=no -tt ${steps.SSH_USERNAME}@${address} ${command} | tee ssh-output.out
            """
                def result = steps.readFile 'ssh-output.out'
                result = result?.trim();
                steps.echo result
                lastResult = result
                steps.sh """rm ssh-output.out"""
            }
        }
        return lastResult
    }

    void scp(String credentialId, String address, String fromPath, String toPath) {
        address = address.trim()
        steps.withCredentials([steps.sshUserPrivateKey(credentialsId: credentialId, keyFileVariable: 'SSH_KEYFILE', passphraseVariable: 'SSH_PASSWORD', usernameVariable: 'SSH_USERNAME')]) {
            steps.sh """
           scp -i ${steps.SSH_KEYFILE} -B ${fromPath} ${steps.SSH_USERNAME}@${address}:${toPath}
           """
        }
    }

    void waitForUrlSuccess(String url) {
        steps.timeout(5) {
            steps.waitUntil {
                steps.script {
                    steps.echo "Waiting for response from ${url}"
                    def result = steps.sh(script: "wget -q ${url} -O /dev/null", returnStatus: true)
                    return (result == 0);
                }
            }
        }
    }

}
