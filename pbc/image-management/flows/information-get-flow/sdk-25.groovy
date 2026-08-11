// URL組み立て関数
def buildFssS3Url(String url, String fssS3Bucket, String fssS3Host) {
    def parsedUrl = new URL(url)
    def path = parsedUrl.path
    def query = parsedUrl.query ? ('?' + parsedUrl.query) : ''
    // すべてのUUIDを抽出
    def uuidList = (path =~ /[0-9a-fA-F\-]{36}/)*.toString()
    // ファイル名抽出
    def fileNameMatch = path =~ /([0-9a-fA-F\-]{36}_[^\/]+\.zip)$/
    if (uuidList.size() > 0 && fileNameMatch) {
        def uuid = uuidList.size() >= 2 ? uuidList[-2] : uuidList[0]
        def filename = fileNameMatch[0][1]
        def logicalPath = "tmp/System/${uuid}/${filename}"
        return "https://${fssS3Bucket}.${fssS3Host}/${logicalPath}"
    } else {
        // fallback: /tmp/ 以降をそのまま
        def idx = path.indexOf('/tmp/')
        def logicalPath = (idx >= 0) ? path.substring(idx + 1) : path.replaceFirst('^/', '')
        return "https://${fssS3Bucket}.${fssS3Host}/${logicalPath}${query}"
    }
}

def urls = in.output.collect {
    if (environment == 'TRIALRUN' || environment == 'local') {
        fileStorageUrl.replaceAll("/+\$", "") + it.url
    } else {
        // ★ TODO: S3 URL変換ロジックを削除
        //buildFssS3Url(it.url, fssS3Bucket, fssS3Host)
        //it.url
        it.url.replace('https://dev-dmsigsmobile.dev-rfd.com', fileStorageUrl ?: 'http://localhost:9220')
    }
}

return [urls:urls]