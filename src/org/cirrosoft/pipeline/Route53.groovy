package org.cirrosoft.pipeline

class Route53 implements Serializable {
    public def steps
    Route53(inputStep) {steps = inputStep}

    String getHostedZoneId(String domainName) {
        steps.sh(script: """aws route53 list-hosted-zones | jq '.HostedZones[] | select(.Name=="${domainName}")' | tee zones.out""", returnStdout: true)
        def result = steps.readFile 'zones.out'
        steps.sh """rm zones.out"""
        def regex = /Id.*?\/hostedzone\/(.*?)",/
        def match = (result =~ regex)
        if (match.find()) {
            def zone = match.group(1)
            return zone
        }
        return null
    }
    String createRecord(String zoneId, String domainName, String ip) {
        def record = """
        {
            "Comment": "A new record set for the zone.",
            "Changes": [
                {
                    "Action": "UPSERT",
                    "ResourceRecordSet": {
                    "Name": "${domainName}",
                    "Type": "A",
                    "TTL": 300,
                    "ResourceRecords": [
                            {
                                "Value": "${ip}"
                            }
                    ]
                }
                }
        ]
        }
        """
        steps.echo record
        steps.sh """touch dns-record.json"""
        steps.writeFile file: "dns-record.json", text: record
        steps.sleep 1
        steps.sh(script: """aws route53 change-resource-record-sets --hosted-zone-id ${zoneId} --change-batch file://dns-record.json | tee change.out""", returnStdout: true)
        def result = steps.readFile 'change.out'
        steps.sh """rm change.out"""
        steps.sh """rm dns-record.json"""
        def regex = /Id.*?\/change\/(.*?)",/
        def match = (result =~ regex)
        if (match.find()) {
            def changeId = match.group(1)
            return changeId
        }
        return null
    }
}
