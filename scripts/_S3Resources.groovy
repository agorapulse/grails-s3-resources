import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.services.s3.model.ListObjectsRequest
import com.amazonaws.services.s3.model.ObjectListing
import grails.util.Metadata
import groovy.io.FileType

includeTargets << grailsScript("_GrailsInit")
includeTargets << grailsScript("_GrailsBootstrap")

target(loadConfig: "Load S3 resources config") {
    depends(compile, parseArguments)

    if (argsMap['help']) {
        println USAGE
        exit 0
    }

    loadApp()
    configureApp()

    awsConfig = grailsApp.config.grails.plugin?.awssdk
    s3ResourcesConfig = grailsApp.config.grails.resources?.s3

    // Parse arguments
    bucket = argsMap['bucket'] ?: s3ResourcesConfig?.bucket ?: awsConfig?.s3?.bucket ?: awsConfig?.bucket
    accessKey = argsMap['access-key'] ?: s3ResourcesConfig?.accessKey ?: awsConfig?.s3?.accessKey ?: awsConfig?.accessKey
    secretKey = argsMap['secret-key'] ?: s3ResourcesConfig?.secretKey ?: awsConfig?.s3?.secretKey ?: awsConfig?.secretKey
    recursive = argsMap['all'] ? true : false // Only used in status (forced to true in push)
    region = argsMap['region'] ?: s3ResourcesConfig?.region ?: awsConfig?.s3?.region ?: awsConfig?.region ?: ''

    expirationDate = null
    def expires = argsMap['expires'] ?: s3ResourcesConfig?.expires ?: awsConfig?.s3?.expires ?: 0
    if (expires) {
        if (expires instanceof Date) {
            expirationDate = expires
        } else if (expires instanceof Integer) {
            expirationDate = new Date() + expires
        }
    }

    prefix = argsMap['prefix'] ?: s3ResourcesConfig.prefix ?: ''
    if (!prefix.endsWith('/')) prefix = "$prefix/"
    if (prefix.startsWith('/')) prefix = prefix.replaceFirst('/', '')

    pluginsPrefix = argsMap['plugins-prefix'] ?: s3ResourcesConfig.pluginsPrefix ?: "${prefix}plugins/"
    if (!pluginsPrefix.endsWith('/')) pluginsPrefix = "$pluginsPrefix/"
    if (pluginsPrefix.startsWith('/')) pluginsPrefix = pluginsPrefix.replaceFirst('/', '')

    if (isPluginProject) {
        def pluginName = new Metadata().getApplicationName()
        def pluginVersion = appCtx.getBean('pluginManager').getGrailsPlugin(pluginName).version
        prefix = "$pluginsPrefix$pluginName-$pluginVersion/"
    }
}

target(loadModulesResources: "Load modules resources") {
    depends(loadConfig)

    modulesResources = [:]
    def grailsResourceProcessor = appCtx.getBean('grailsResourceProcessor')

    // Delete all previously processed files in the resources plugin workDir
    grailsResourceProcessor.getWorkDir().eachFileRecurse (FileType.FILES) { File file ->
        file.delete()
    }

    // Override the getMimeType method since the MockServletContext will always return "application/octet-stream",
    // causing mappers (minify,bundle,etc) to ignore all the files
    grailsResourceProcessor.metaClass.getMimeType = { uri ->
        switch (uri.tokenize('.').last()) {
            case 'css':
                return 'text/css'
            case 'js':
                return 'application/javascript'
            default:
                return 'application/octet-stream'
        }
    }
    // We need to reload resources after we injected that method, this is where all the processed resources (bundled,
    // minified, zipped, etc ) gets generated
    grailsResourceProcessor.reloadAll()

    // Uncomment the line below to see which modules have been processed by the resource plugin
    // println grailsResourceProcessor.dumpResources()

    // Loop over all the files processed by the resource plugin and add them to the modulesResources
    grailsResourceProcessor.getWorkDir().eachFileRecurse (FileType.FILES) { File file ->
        def relativePath = file.path.replaceFirst(grailsResourceProcessor.getWorkDir().path, '')
        modulesResources[relativePath] = file
    }

    // The block below could allow to have a fine-grained control of the uploaded files, instead of uploading everything
    // the resource plugin generated
    /*
    grailsResourceProcessor.modulesByName.each { name, module ->
         module.resources.each { meta ->

            // We add the resource to the list only when :
            // 1) They're not served by a third-party
            // 2) They're not part of a bigger bundle (bundles lead to multiple resources with the same actualUrl, the
            //    only one that matters to us is the bundle itself, not the submodules)

             if (!meta.actualUrl.startsWith('http') && meta.bundle == null) {
                modulesResources[meta.actualUrl] = meta.processedFile
             }
         }
    }
    */
}

target(loadWebAppResources: "Load web-app resources") {
    webAppResources = [:]
    def includeRegex = s3ResourcesConfig?.webapp?.includeRegex ?: /.*/
    def excludeRegex = s3ResourcesConfig?.webapp?.excludeRegex ?: /(?i)WEB-INF|META-INF|CNAME|LICENSE|node_modules|Gemfile|Makefile|Rakefile|.DS_Store|Spec\.js|\.coffee|\.ico|\.gitignore|\.html|\.json|\.less|\.md|\.npmignore|\.php|\.sh|\.scss|\.svn|\.yml|\/doc\/|\/docs\/|\/spec\/|\/src\/|\/test\/|\/tests\//
    new File("${basedir}/web-app").eachFileRecurse (FileType.FILES) { File file ->
        def relativePath = file.path.replace("${basedir}/web-app", '')
        if (relativePath.find(includeRegex) && !relativePath.find(excludeRegex)) {
            webAppResources[relativePath] = file
        }
    }
}

target(loadResources: "Load web-app and modules resources") {
    depends(loadWebAppResources, loadModulesResources)

    resources = webAppResources + modulesResources
}

target(loadResourceKeys: "Load appResourceKeys and pluginsResourceKeys") {
    depends(loadResources)

    appResourceKeys = []
    pluginsResourceKeys = []

    resources.each { String path, File file ->
        def key = path.replaceFirst('/', '')
        if (key.startsWith('plugins/')) {
            if (recursive) {
                pluginsResourceKeys << key.replaceFirst('plugins/' , '')
            } else {
                def pluginName = key.replaceFirst('plugins/' , '').tokenize('/').first()
                if (!(pluginName in pluginsResourceKeys)) {
                    pluginsResourceKeys << pluginName
                }
            }
        } else {
            if (recursive) {
                appResourceKeys << key
            } else {
                def directoryName = key.tokenize('/').first()
                if (!(directoryName in appResourceKeys)) {
                    appResourceKeys << directoryName
                }
            }
        }
    }
}

target(loadS3Client: "Load S3 Amazon Web Service") {
    depends(loadConfig)

    def amazonWebService = appCtx.getBean('amazonWebService')
    if (accessKey && secretKey) {
        BasicAWSCredentials credentials = new BasicAWSCredentials(accessKey, secretKey)
        s3 = new AmazonS3Client(credentials)
        if (region == 'us' || region == 'us-east-1') {
            s3.endpoint = "s3.amazonaws.com"
        } else {
            s3.endpoint = "s3-${region}.amazonaws.com"
        }
    } else {
        s3 = region ? amazonWebService.getS3(region) : amazonWebService.s3
    }
}

target(loadS3ResourceKeys: "Load S3 appResourceKeys and pluginsResourceKeys") {
    depends(loadConfig, loadS3Client)

    s3AppResourceKeys = []
    s3PluginsResourceKeys = []

    ListObjectsRequest listObjectsRequest = new ListObjectsRequest()
            .withBucketName(bucket)
            .withDelimiter(recursive ? '' : '/')

    ObjectListing appObjectListing = s3.listObjects(listObjectsRequest.withPrefix(prefix))
    ObjectListing pluginsObjectListing = s3.listObjects(listObjectsRequest.withPrefix(pluginsPrefix))

    if (recursive) {
        s3AppResourceKeys = collectAllObjectKeys(s3, appObjectListing)*.replace(prefix, '')
        s3PluginsResourceKeys = collectAllObjectKeys(s3, pluginsObjectListing)*.replace(pluginsPrefix, '')
    } else {
        // Check web-app directory/file names
        s3AppResourceKeys = appObjectListing.commonPrefixes.collect { it.tokenize('/').last() }
        s3AppResourceKeys.addAll(appObjectListing.objectSummaries.collect { it.key.tokenize('/').last() })
        // Check plugin names
        s3PluginsResourceKeys = pluginsObjectListing.commonPrefixes.collect { it.tokenize('/').last() }
    }

    s3ResourceKeys = (s3AppResourceKeys + s3PluginsResourceKeys).flatten()
}

// PRIVATE

List collectAllObjectKeys(AmazonS3Client s3, ObjectListing objectListing) {
    List keys = objectListing.objectSummaries*.key
    if (objectListing.truncated) {
        // Request returns more than 1000 items (default max keys)
        ObjectListing next = s3.listNextBatchOfObjects(objectListing)
        while (next.truncated) {
            current = s3.listNextBatchOfObjects(next)
            keys.addAll(current.objectSummaries.collect { it.key })
            next = s3.listNextBatchOfObjects(current)
        }
        keys.addAll(next.objectSummaries*.key)
    }
    return keys
}
