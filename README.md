S3 Grails Plugin
================

# Introduction

The S3 Plugin allows to push [Grails](http://grails.org) [resources](http://grails.org/plugin/resources) to [Amazon S3](aws.amazon.com/s3/).

# Installation

Declare the plugin dependency in the BuildConfig.groovvy file, as shown here:

```groovy
grails.project.dependency.resolution = {
		inherits("global") { }
		log "info"
		repositories {
				//your repositories
		}
		dependencies {
				// Workaround to resolve dependency issue with aws-java-sdk and http-builder (dependent on httpcore:4.0)
                build 'org.apache.httpcomponents:httpcore:4.1'
                build 'org.apache.httpcomponents:httpclient:4.1'
                runtime 'org.apache.httpcomponents:httpcore:4.1'
                runtime 'org.apache.httpcomponents:httpclient:4.1'
		}
		plugins {
				//here go your plugin dependencies
				runtime ':s3-resources:1.4.7'
		}
}
```

# Config

```groovy
grails {
    resources {        
        s3 {
            accessKey = {ACCESS_KEY}
            secretKey = {SECRET_KEY}
            bucket = 'mybucket.domain.com'
            region = 'eu-west-1'
        }
    }
}
```
