/**
 * 画像読込API呼び出し処理 + レスポンス解析
 * 
 * INPUT: dealerCode, userId, veichleEvent, order_no, car_no, getImageApiUrl, igsJwtToken, debug, environment
 * OUTPUT: output (status, responseMessage, responseBody, parsedData)
 * 
 * parsedData: json (syori_kbnをvehicleEventに変換済), zipData, filename, contentTransferEncoding
 */

import groovy.json.JsonOutput
import groovy.json.JsonSlurper

import java.util.regex.Pattern

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

debugFlg = (debug == 'true')

if (debugFlg) {
    println "=== INPUT ==="
    println "dealerCode: ${dealerCode}"
    println "userId: ${userId}"
    println "veichleEvent: ${veichleEvent}"
    println "order_no: ${order_no}"
    println "car_no: ${car_no}"
    println "getImageApiUrl: ${getImageApiUrl}"
    println "igsJwtToken: ${igsJwtToken}"
    println "debug: ${debug}"
    println "environment: ${environment}"
}

// URLにクエリパラメータを追加
def appendQueryParam(String url, List<Map<String, String>> params) {
    def result = url
    params.each { param ->
        if (param.value) {
            def separator = result.contains('?') ? '&' : '?'
            result = "${result}${separator}${param.key}=${param.value}"
        }
    }
    return result
}

// multipartレスポンス解析
def parseMultipart(String responseBody, boolean debugFlg) {
    if (!responseBody) {
        return [:]
    }

    if (debugFlg) {
        println "=== Multipart解析開始 ==="
    }

    // boundary自動検出
    def detectedBoundary = null
    responseBody.eachLine { line ->
        if (!detectedBoundary) {
            def m = (line =~ /^--(.+)$/)
            if (m.find()) {
                detectedBoundary = m.group(1)
            }
        }
    }
    if (!detectedBoundary) {
        detectedBoundary = "IGSBoundary"
    }

    if (debugFlg) {
        println "検出されたboundary: ${detectedBoundary}"
    }

    // パートを分割
    def parts = responseBody.split("--" + Pattern.quote(detectedBoundary))
    if (!parts || parts.size() < 2) {
        if (debugFlg) println "parts不足 size=${parts?.size()}"
        return [:]
    }

    def jsonObj = null
    def zipBase64 = null
    def filename = "file.zip"
    def contentTransferEncoding = "base64"

    // Part本体を解析（最初/最後は除外）
    for (int i = 1; i < parts.size() - 1; i++) {
        def part = parts[i].trim()

        // ヘッダとボディの境界を探す（空行）
        def matcher = part =~ /\r?\n\r?\n/
        if (!matcher.find()) {
            continue
        }

        int splitIndex = matcher.end()
        def headersText = part.substring(0, matcher.start()).trim()
        def bodyText = part.substring(splitIndex).trim()

        // ヘッダ解析
        def contentType = null
        def contentDisposition = null
        def transferEnc = null

        headersText.eachLine { hl ->
            def L = hl.trim()
            def lower = L.toLowerCase()

            if (lower.startsWith("content-type:")) {
                contentType = L.substring(L.indexOf(":") + 1).trim()
            }
            if (lower.startsWith("content-disposition:")) {
                contentDisposition = L.substring(L.indexOf(":") + 1).trim()
            }
            if (lower.startsWith("content-transfer-encoding:")) {
                transferEnc = L.substring(L.indexOf(":") + 1).trim()
            }
        }

        // JSONパート
        if (contentType?.toLowerCase()?.contains("application/json")) {
            try {
                jsonObj = new JsonSlurper().parseText(bodyText)
                if (debugFlg) println "JSONパートを解析しました"
            } catch (e) {
                if (debugFlg) println "JSON parse error: ${e.message}"
            }
            continue
        }

        // ZIPパート
        if (contentType?.toLowerCase()?.contains("application/zip")) {
            // ファイル名取得
            if (contentDisposition) {
                def m = (contentDisposition =~ /filename="([^"]+)"/)
                if (m.find()) {
                    filename = m.group(1)
                }
            }

            // bodyTextが空の場合 空ZIP
            def clean = bodyText.replaceAll(/\s/, "")
            zipBase64 = clean

            // content-transfer-encoding
            if (transferEnc) {
                contentTransferEncoding = transferEnc
            }

            // ZIPパートのBase64はdebugFlgで省略切り替え
            println "ZIPパートを検出: ${filename}"
            println "ZIP Base64: " + abbreviateBase64ForLog(clean, debugFlg)
            continue
        }
    }

    // syori_kbnをvehicleEventに変換
    def vehicleEvent = null
    if (jsonObj?.syori_kbn) {
        def syorival = jsonObj.syori_kbn
        def mapping = [
            "10": [label: "新車",          value: "10"],
            "20": [label: "中古車",        value: "20"],
            "31": [label: "車検(入庫時)",  value: "31"],
            "32": [label: "車検(整備中)",  value: "32"],
            "33": [label: "車検(出庫時)",  value: "33"]
        ]
        vehicleEvent = mapping[syorival]
        
        // jsonObj.syori_kbnをvehicleEventで上書き
        if (vehicleEvent) {
            jsonObj.syori_kbn = vehicleEvent
            if (debugFlg) println "syori_kbn変換: ${syorival} -> ${vehicleEvent}"
        }
    }

    if (debugFlg) {
        println "=== Multipart解析完了 ==="
    }

    return [
        json: jsonObj,
        zipData: zipBase64,
        filename: filename,
        contentTransferEncoding: contentTransferEncoding
    ]
}

// リクエストボディの構築とPOST送信
def requestJson = JsonOutput.toJson([
    hansha_code: dealerCode,
    user_id: userId,
    syori_kbn: veichleEvent,
    order_no: order_no,
    car_no: car_no,
    order_mode: "1"
])

def targetUrl = getImageApiUrl
if (environment in ['local', 'TRIALRUN', 'dev']) {
    targetUrl = appendQueryParam(targetUrl, [[key: 'order_no', value: order_no]])
}

def connection = new URL(targetUrl).openConnection().with {
    requestMethod = "POST"
    doOutput = true
    setRequestProperty("Content-Type", "application/json")
    setRequestProperty("Authorization", ("Bearer " + igsJwtToken).toString())
    outputStream.withWriter("UTF-8") { it.write(requestJson) }
    it
}

// レスポンス取得と解析
def responseCode = connection.responseCode
def responseMessage = connection.responseMessage
def responseText = ""

try {
    responseText = (responseCode >= 200 && responseCode < 300) 
        ? connection.inputStream.text 
        : (connection.errorStream?.text ?: "")
} catch (Exception e) {
    responseText = ""
    println "[ERROR] レスポンス取得エラー: ${e.message}"
}

// APIリクエスト・レスポンス情報
println "=== HTTP Request ==="
println "URL: ${targetUrl}"
println "Method: POST"
println "Headers:"
println "  Content-Type: application/json"
println "  Authorization: ${("Bearer " + igsJwtToken).toString()}"
println "Body: ${requestJson}"
println "=== HTTP Response ==="
println "Response Code: ${responseCode}"
println "Response Message: ${responseMessage}"
if (responseText?.startsWith('--')) {
    // multipartレスポンスの場合はバイナリ省略制御
    println "Response Raw Body:"
    println summarizeMultipartForLog(responseText, debugFlg)
} else {
    println "Response Raw Body: ${responseText}"
}

// multipartレスポンスのバイナリ部分を省略する関数
def summarizeMultipartForLog(String responseText, boolean debugFlg) {
    def lines = responseText.split(/\r?\n/)
    def boundary = null
    for (line in lines) {
        if (line.startsWith('--') && !line.startsWith('---')) {
            boundary = line.replaceFirst('^--', '')
            break
        }
    }
    if (!boundary) return responseText
    def out = new StringBuilder()
    boolean inBinary = false
    boolean inPart = false
    def binaryBody = new StringBuilder()
    lines.eachWithIndex { line, idx ->
        if (line == "--${boundary}") {
            out.append(line).append("\r\n")
            inPart = true
            inBinary = false
        } else if (line == "--${boundary}--") {
            out.append(line).append("\r\n")
            inPart = false
            inBinary = false
        } else if (inPart) {
            out.append(line).append("\r\n")
            if (line.toLowerCase().contains('content-disposition:') && line.contains('filename="')) {
                inBinary = true
            }
            if (line == "") {
                inPart = false
            }
        } else if (inBinary) {
            binaryBody.append(line)
            binaryBody.append("\r\n")
            if (idx + 1 < lines.size() && (lines[idx + 1].startsWith("--${boundary}"))) {
                def zipBytes = binaryBody.toString().getBytes('ISO-8859-1')
                def b64 = zipBytes.encodeBase64().toString()
                if (debugFlg) {
                    out.append(b64).append("\r\n")
                } else {
                    if (b64.length() > 20) {
                        out.append(b64.substring(0,10)).append("... (binary omitted) ...").append(b64.substring(b64.length()-10)).append("\r\n")
                    } else {
                        out.append(b64).append("\r\n")
                    }
                }
                binaryBody = new StringBuilder()
                inBinary = false
            }
        } else {
            out.append(line).append("\r\n")
        }
    }
    return out.toString()
}

// レスポンスボディを解析
def parsedData = parseMultipart(responseText, debugFlg)

// 最終出力
def output = [
    status: responseCode,
    responseCode: parsedData?.json?.responseCode ?: null,
    message: parsedData?.json?.message ?: null,
    responseBody: responseText,
    parsedData: parsedData
]

if (debugFlg) {
println "=== OUTPUT ==="
println groovy.json.JsonOutput.prettyPrint(groovy.json.JsonOutput.toJson(output))
}

return output