println "=== INPUT ==="
println "environment: ${environment}"
println "igsLoginDealerCode: ${igsLoginDealerCode}"
println "orderNumber: ${orderNumber}"
println "images: ${images}"
println "fssS3Bucket: ${fssS3Bucket}"
println "fssS3Host: ${fssS3Host}"

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

// URL 配列を生成
// URL配列を生成（環境ごとに分岐しbuildFssS3Urlも利用）
def fullUrls = images.collect { img ->
    def path = img.url
    if (environment == 'TRIALRUN' || environment == 'local') {
        return applicationFssUrl.replaceAll(/\/+$/, "") + path
    } else {
        return buildFssS3Url(path, fssS3Bucket, fssS3Host)
    }
}

output = [
    zipFileName: (igsLoginDealerCode + "_" + orderNumber + ".zip").toString(),
    fullUrls: fullUrls
]

println "=== OUTPUT ==="
println "zipFileName: ${output.zipFileName}"
println "fullUrls: ${output.fullUrls}"

// Map を返却
return output