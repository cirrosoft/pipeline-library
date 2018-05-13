package org.cirrosoft.pipeline

class ProjectTools implements Serializable {
    public def steps
    Instances instances
    Remote remote
    ProjectTools(inputStep) {
        steps = inputStep
        instances = new Instances(steps)
        remote = new Remote(steps)
    }

    ArrayList getSuccessfullBuilds() {
        def b = steps.currentBuild
        def builds = []
        while (b != null) {
            b = b?.getPreviousBuild()
            if (b?.result == 'SUCCESS') {
                builds.add(b)
            }
        }
        return builds;
    }
    String getBlueOrGreen() {
        def builds = getSuccessfullBuilds()
        def count = builds.size()
        if (count % 2 == 0) {
            return "blue"
        } else {
            return "green"
        }
    }
    String generateJavaPropertiesString(Map props) {
        def propsString = ""
        props.each{ k, v ->
            propsString += "-Dbuild."+k+"="+v+" "
        }
        return propsString
    }

    ArrayList<String> ensureDockerInstance(
            String sshCredentials,
            String instanceName,
            String instanceType,
            String instanceImage,
            String instanceSecurityGroup,
            String instanceKeyPair,
            ArrayList<String> dockerInstall) {
        def instanceIds
        if (instances.instanceExists(instanceName)) {
            instanceIds = instances.getInstanceIds(instanceName)
        } else {
            def instanceId = instances.createInstance(instanceName, instanceType, instanceImage, instanceSecurityGroup, instanceKeyPair)
            instanceIds = [instanceId]
            instances.waitForInstance(instanceId)
            def ip = instances.getInstancePublicIP(instanceId)
            remote.executeRemoteCommands(sshCredentials, ip, dockerInstall)
        }
        return instanceIds
    }
}
