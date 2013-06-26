class S3ResourcesGrailsPlugin {
    // the plugin version
    def version = "1.4.7-SNAPSHOT"
    // the version or versions of Grails the plugin is designed for
    def grailsVersion = "2.0 > *"
    // resources that are excluded from plugin packaging
    def pluginExcludes = []
    def dependsOn = [resources: '1.0 > *']
    def loadAfter = ['resources']

    def title = "Agorapulse S3 Resources Plugin"
    def author = "Jean-Vincent Drean"
    def authorEmail = "jv@agorapulse.com"
    def description = '''\
Uploads resources to S3
'''

    // URL to the plugin's documentation
    def documentation = "http://agorapulse.atlassian.net"

    // License: one of 'APACHE', 'GPL2', 'GPL3'
    // def license = "APACHE"

    // Details of company behind the plugin (if there is one)
    def organization = [ name: "AgoraPulse", url: "http://www.agorapulse.com/" ]
    // Any additional developers beyond the author specified above.
    // def developers = [ [ name: "Joe Bloggs", email: "joe@bloggs.net" ]]
    // Location of the plugin's issue tracker.
    // def issueManagement = [ system: "JIRA", url: "http://jira.grails.org/browse/GPMYPLUGIN" ]
    // Online location of the plugin's browseable source code.
    // def scm = [ url: "http://svn.codehaus.org/grails-plugins/" ]
}
