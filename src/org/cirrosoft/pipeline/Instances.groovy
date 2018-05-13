package org.cirrosoft.pipeline

class Instances implements Serializable {
    public def steps
    Instances(inputStep) {steps = inputStep}

    String createInstance(String nameTag, String type, String ami, String securityGroup, String keyPairName) {
        nameTag = nameTag.replaceAll(' ', '-')
        steps.sh(script: """aws ec2 run-instances --image-id ${ami} --count 1 --instance-type ${type} --key-name ${keyPairName} --security-groups ${securityGroup} --tag-specifications ResourceType=instance,Tags=[\\{Key=Name,Value=${nameTag}\\}] | tee instance.out""", returnStdout: true).trim()
        def result = steps.readFile 'instance.out'
        steps.sh """rm instance.out"""
        def regex = /InstanceId.*?(i-.*?)",/
        def match = (result =~ regex)
        match.find()
        def instanceId = match.group(1)
        return instanceId
    }

    String[] getInstanceIds(nameTag) {
        nameTag = nameTag.replaceAll(" ", "-")

        steps.sh(script: """aws ec2 describe-instances --filters 'Name=tag:Name,Values=${nameTag}' 'Name=instance-state-name,Values=running' | tee instances.out""", returnStdout: true)
        def result = steps.readFile 'instances.out'
        steps.sh """rm instances.out"""
        def regex = /InstanceId.*?(i-.*?)",/
        def match = (result =~ regex)
        def instances = []
        while (match.find()) {
            def instanceId = match.group(1)
            instances.add(instanceId)
        }
        return instances
    }

    boolean instanceExists(String nameTag) {
        def instances = this.getInstanceIds(nameTag)
        if (instances) {
            return true
        } else {
            return false
        }
    }

    void deleteInstance(String id) {
        steps.sh(script: """aws ec2 terminate-instances --instance-ids ${id}""")
    }

    void deleteInstances(String nameTag) {
        nameTag = nameTag.replaceAll(" ", "-")
        def instances = this.getInstanceIds(nameTag)
        for (id in instances) {
            this.deleteInstance(id)
        }
    }

    void waitForInstance(String id) {
        steps.echo "Waiting for instance to start..."
        steps.sh(script: """aws ec2 wait instance-running --instance-ids ${id}""")
        steps.echo "Waiting for instance to become available to the network..."
        steps.sh(script: """aws ec2 wait instance-status-ok --instance-ids ${id}""")
    }

    String getInstancePublicIP(String id) {
        return steps.sh(script: """aws ec2 describe-instances --instance-ids ${id} | grep PublicIpAddress | awk -F ":" '{print \$2}' | sed 's/[",]//g'""", returnStdout: true).trim()
    }

    String getInstancePrivateIP(String id) {
        return steps.sh(script: """aws ec2 describe-instances --instance-ids ${id} | grep PrivateIpAddress  | head -1 | awk -F ":" '{print \$2}' | sed 's/[",]//g'""", returnStdout: true).trim()
    }

}
