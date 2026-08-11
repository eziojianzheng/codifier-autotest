/**
 * ZIPダウンロード＆解凍処理
 *
 * INPUT: fileStorageUrl, fssS3Bucket, fccS3Host, url, environment, debug
 * OUTPUT: output (jpgCount, jpgList)
 *   jpgList: [filepath, filename, filebase64]
 */

import java.util.zip.*
import java.nio.file.Files

import groovy.json.JsonOutput

// 長いBase64文字列を省略して出力する関数（debugFlgで切り替え）
def abbreviateBase64ForLog(String base64Str, boolean debugFlg = false, int head = 10, int tail = 10, int threshold = 40) {
    if (!base64Str) return ''
    if (debugFlg) return base64Str
    if (base64Str.length() > threshold) {
        return base64Str.substring(0, head) + '... (omitted) ...' + base64Str.substring(base64Str.length() - tail)
    } else {
        return base64Str
    }
}

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

debugFlg = (debug == 'true')

if (debugFlg) {
    println "=== INPUT ==="
    println "fileStorageUrl: ${fileStorageUrl}"
    println "fssS3Bucket: ${fssS3Bucket}"
    println "fssS3Host: ${fssS3Host}"
    println "url: ${url}"
    println "environment: ${environment}"
    println "debug: ${debug}"
}


if (environment == 'TRIALRUN' || environment == 'local') {
    url = fileStorageUrl.replaceAll("/+\$", "") + url
} else {
    // ★★TODO: s3 URL変換ロジックは削除する、S3利用環境ではfileStorageUrlを結合せずにurlをそのまま使う★★
    //url = buildFssS3Url(url, fssS3Bucket, fssS3Host)

    // test 置き換え
    if (url?.startsWith('https://dev-dmsigsmobile.dev-rfd.com')) {
        url = url.replace('https://dev-dmsigsmobile.dev-rfd.com', fileStorageUrl ?: 'http://localhost:9220')
    }
}

// ZIP解凍関数
def unzip(String zipPath, File dest) {
    if (debugFlg) println "[DEBUG] unzip: ${zipPath} -> ${dest.absolutePath}";
    new ZipInputStream(new FileInputStream(zipPath)).withStream { zis ->
        ZipEntry entry
        while ((entry = zis.nextEntry) != null) {
            if (!entry.isDirectory()) {
                def out = new File(dest, entry.name)
                out.parentFile.mkdirs()
                out.withOutputStream { os -> zis.transferTo(os) }
                if (debugFlg) println "[DEBUG] 解凍: ${out.absolutePath}";
            }
        }
    }
}

// 画像拡張子判定
def isImage(String name) {
    ['jpg','jpeg','png','gif','bmp','webp'].any { ext ->
        name.toLowerCase().endsWith(ext)
    }
}

// URL から zip ファイル名を自動取得
def fileName = url.tokenize('/')[-1].split('\\?')[0]
if (debugFlg) println "[DEBUG] ZIPファイル名: ${fileName}";

// 作業ディレクトリ
def tempDir = Files.createTempDirectory("zip_proc").toFile()
if (debugFlg) println "[DEBUG] 作業ディレクトリ: ${tempDir.absolutePath}";

// ZIPダウンロード
def zipFile = new File(tempDir, fileName)
if (debugFlg) println "[DEBUG] ZIPダウンロード: ${url} -> ${zipFile.absolutePath}";
try {
    new URL(url).withInputStream { input ->
        zipFile.withOutputStream { out -> out << input }
    }
    if (debugFlg) println "[DEBUG] ZIPダウンロード成功: ${zipFile.absolutePath}"
} catch (Exception e) {
    println "[ERROR] ZIPダウンロード失敗: ${url}"
    e.printStackTrace()
    throw e
}

// ZIP解凍
def extractDir = new File(tempDir, "extracted")
extractDir.mkdirs()
unzip(zipFile.absolutePath, extractDir)

// 画像ファイル情報を格納するリスト
def jpgList = []

// 画像抽出 → 1ファイルごとにオブジェクト化
try {
    extractDir.eachFileRecurse {
        if (it.isFile() && isImage(it.name)) {
            if (debugFlg) println "[DEBUG] 画像抽出: ${it.absolutePath}";
            // Base64 へ変換
            def base64Str = it.bytes.encodeBase64().toString()
            if (debugFlg) {
                println "[DEBUG] Base64(省略表示): " + abbreviateBase64ForLog(base64Str)
            }
            // 1件分の JSON オブジェクト
            def fileInfo = [
                filepath   : it.absolutePath,
                filename   : it.name,
                filebase64 : base64Str
            ]
            // リストに追加
            jpgList << fileInfo
        }
    }

    if (debugFlg) println "[DEBUG] 画像抽出完了: ${jpgList.size()}件";

    def output = [
        jpgCount : jpgList.size(),
        jpgList  : jpgList
    ]
    // APIレスポンス情報はdebugFlgに関わらず必ず出力
    println "=== OUTPUT ==="
    // Base64部分はdebugFlgで省略切り替え
    def outputForLog = [
        jpgCount: output.jpgCount,
        jpgList: output.jpgList.collect { item ->
            [filepath: item.filepath, filename: item.filename, filebase64: abbreviateBase64ForLog(item.filebase64, debugFlg)]
        }
    ]
    println groovy.json.JsonOutput.prettyPrint(groovy.json.JsonOutput.toJson(outputForLog))
    return output
} catch (Exception e) {
    println "[ERROR] 致命的エラー: ${e.message}"
    e.printStackTrace()
    return [jpgCount: 0, jpgList: []]
}