/**
 * ZIP作成＆画像登録API呼び出し処理
 * 画像ファイルをZIP化してmultipart/form-data形式でPOST送信
 * 
 * INPUT: loop, zipFileName, dealerCode, orderNumber, vehicleEvent, 
 *        setImageApiUrl, authorizationHeaderValue, debug, environment
 * OUTPUT: output (status, responseMessage, responseBody, requestInfo)
 */

import java.util.zip.ZipOutputStream
import java.util.zip.ZipEntry

import groovy.json.JsonSlurper

// multipart/form-dataリクエストボディを可読性高く出力する関数
def debugFormatMultipartBody(byte[] requestBody, String boundary, boolean debugFlg) {
    def boundaryStr = boundary
    def bodyIso = new String(requestBody, 'ISO-8859-1')
    def lines = bodyIso.split(/\r?\n/)
    def reqBodyLog = new StringBuilder()
    boolean inBinaryPart = false
    boolean inPart = false
    boolean binaryLogged = false
    def binaryHeaders = ''
    def binaryBody = new StringBuilder()
    lines.eachWithIndex { line, idx ->
        if (line == "--${boundaryStr}") {
            if (inBinaryPart && binaryLogged) {
                reqBodyLog.append(binaryHeaders)
                reqBodyLog.append(binaryBody)
                binaryHeaders = ''
                binaryBody = new StringBuilder()
                binaryLogged = false
            }
            reqBodyLog.append(line).append("\r\n")
            inPart = true
            inBinaryPart = false
        } else if (line == "--${boundaryStr}--") {
            if (inBinaryPart && binaryLogged) {
                reqBodyLog.append(binaryHeaders)
                reqBodyLog.append(binaryBody)
                binaryHeaders = ''
                binaryBody = new StringBuilder()
                binaryLogged = false
            }
            reqBodyLog.append(line).append("\r\n")
            inPart = false
            inBinaryPart = false
        } else if (inPart) {
            if (line == "") {
                reqBodyLog.append("\r\n")
                if (binaryHeaders.contains('filename="')) {
                    inBinaryPart = true
                    binaryLogged = false
                } else {
                    inBinaryPart = false
                }
                inPart = false
            } else {
                reqBodyLog.append(line).append("\r\n")
                if (line.toLowerCase().contains('content-disposition:') && line.contains('filename="')) {
                    binaryHeaders += line + "\r\n"
                }
            }
        } else if (inBinaryPart) {
            binaryBody.append(line)
            binaryBody.append("\r\n")
            if (idx + 1 < lines.size() && (lines[idx + 1].startsWith("--${boundaryStr}"))) {
                def zipBytes = binaryBody.toString().getBytes('ISO-8859-1')
                def b64 = zipBytes.encodeBase64().toString()
                if (debugFlg) {
                    reqBodyLog.append(b64).append("\r\n")
                } else {
                    if (b64.length() > 20) {
                        reqBodyLog.append(b64.substring(0,10)).append("... (binary omitted) ...").append(b64.substring(b64.length()-10)).append("\r\n")
                    } else {
                        reqBodyLog.append(b64).append("\r\n")
                    }
                }
                binaryLogged = true
                inBinaryPart = false
                binaryBody = new StringBuilder()
            }
        } else {
            reqBodyLog.append(line).append("\r\n")
        }
    }
    return reqBodyLog.toString()
}

// レスポンスボディを可読性高く出力する関数
def debugFormatResponseBody(responseBody, int maxBodyLen = 2000) {
    def respStr = ''
    if (responseBody instanceof Map || responseBody instanceof List) {
        respStr = groovy.json.JsonOutput.prettyPrint(groovy.json.JsonOutput.toJson(responseBody))
    } else if (responseBody instanceof String) {
        respStr = responseBody
    } else {
        respStr = responseBody?.toString() ?: ''
    }
    if (respStr.length() > maxBodyLen) respStr = respStr.substring(0, maxBodyLen) + "... (truncated)"
    return respStr
}

debugFlg = (debug == 'true')

if (debugFlg) {
    println "=== INPUT ==="
    println "loop: ${loop}"
    println "zipFileName: ${zipFileName}"
    println "dealerCode: ${dealerCode}"
    println "orderNumber: ${orderNumber}"
    println "vehicleEvent: ${vehicleEvent}"
    println "setImageApiUrl: ${setImageApiUrl}"
    println "igsJwtToken: ${igsJwtToken}"
    println "applicationFssUrl: ${applicationFssUrl}"
    println "debug: ${debug}"
    println "environment: ${environment}"
}

// ファイルリストの取得
def fileList = loop?.output ?: []
def fileCount = fileList.size()

if (debugFlg) {
    println "=== 処理開始 ==="
    println "画像ファイル数: ${fileCount}"
}

// ZIP作成
byte[] zipBytes = null
if (fileCount > 0) {
    def zipBaos = new ByteArrayOutputStream()
    new ZipOutputStream(zipBaos).withCloseable { zipOut ->
        fileList.each { fileInfo ->
            def filePath = applicationFssUrl + fileInfo.filePath?.toString() ?: ""
            filePath = filePath.startsWith("null") ? filePath.substring(4) : filePath
            
            // local環境の場合のみFSS URLをファイルシステムパスに変換
            if (environment == 'local' && filePath.startsWith("/fss/api/public/")) {
                def pathWithoutQuery = filePath.split("\\?")[0]
                filePath = pathWithoutQuery
                    .replace("/fss/api/public/tenant/", "/app/war/target/nas/tenant/")
                    .replace("/session/", "/")
                    .replace("/file/", "/")
            }
            
            def fileName = fileInfo.fileName
            def file = new File(filePath)
            
            if (debugFlg) {
                println "処理中: ${fileName} (パス: ${filePath})"
            }
            
            if (!file.exists()) {
                throw new FileNotFoundException("ファイルが見つかりません: ${filePath}")
            }
            
            zipOut.putNextEntry(new ZipEntry(fileName))
            file.withInputStream { is ->
                is.transferTo(zipOut)
            }
            zipOut.closeEntry()
        }
    }
    zipBytes = zipBaos.toByteArray()
}

// multipart/form-dataボディの構築
def boundary = "----IGSUploadBoundary"
def crlf = "\r\n"
def baos = new ByteArrayOutputStream()

// metadataパート
def metadata = "{\"hansha_code\":\"${dealerCode}\",\"syori_kbn\":\"${vehicleEvent}\",\"order_no\":\"${orderNumber}\",\"order_mode\":\"1\"}"
baos.with {
    write(("--${boundary}${crlf}").bytes)
    write(("Content-Disposition: form-data; name=\"metadata\"${crlf}${crlf}").bytes)
    write(metadata.getBytes("UTF-8"))
    write(crlf.bytes)
}

// imageパート
if (fileCount > 0 && zipBytes != null) {
    baos.with {
        write(("--${boundary}${crlf}").bytes)
        write(("Content-Disposition: form-data; name=\"image\"; filename=\"${zipFileName}\"${crlf}").bytes)
        write(("Content-Type: application/zip${crlf}${crlf}").bytes)
        write(zipBytes)
        write(crlf.bytes)
    }
}

// 終了boundaryは必ず1回だけ追加
baos.write(("--${boundary}--${crlf}").bytes)
byte[] requestBody = baos.toByteArray()

// HTTP POSTリクエスト送信
def connection = new URL(setImageApiUrl).openConnection().with {
    requestMethod = "POST"
    doOutput = true
    setRequestProperty("Authorization", ("Bearer " + igsJwtToken).toString())
    setRequestProperty("Content-Type", "multipart/form-data; boundary=${boundary}")
    setRequestProperty("Content-Length", String.valueOf(requestBody.length))
    outputStream.write(requestBody)
    outputStream.flush()
    outputStream.close()
    it
}

// レスポンス取得と解析
def responseCode = connection.responseCode
def responseMessage = connection.responseMessage
def responseBody = [:]

try {
    def responseText = (responseCode >= 200 && responseCode < 300) 
        ? connection.inputStream.text 
        : (connection.errorStream?.text ?: "")
    
    if (responseText) {
        try {
            responseBody = new JsonSlurper().parseText(responseText)
        } catch (Exception parseEx) {
            responseBody = [raw: responseText, parseError: parseEx.message]
            println "[ERROR] JSON解析エラー: ${parseEx.message}"
        }
    }
} catch (Exception e) {
    responseBody = [error: e.message]
    println "[ERROR] レスポンス取得エラー: ${e.message}"
}

// APIリクエスト・レスポンス情報
println "=== HTTP Request ==="
println "URL: ${setImageApiUrl}"
println "Method: POST"
println "Authorization: ${("Bearer " + igsJwtToken).toString()}"
println "Content-Length: ${requestBody.length} bytes"
println "ZIP Size: ${zipBytes?.length ?: 0} bytes"
println "Files: ${fileCount}"
println "Request Body:\n${debugFormatMultipartBody(requestBody, boundary, debugFlg)}"
println "=== HTTP Response ==="
println "Response Code: ${responseCode}"
println "Response Message: ${responseMessage}"
println "Response Body: ${debugFormatResponseBody(responseBody)}"

// 最終出力

def output = [
    status: responseCode,
    responseCode: responseBody?.responseCode ?: null,
    message: responseBody?.message ?: null,
    responseMessage: responseMessage,
    responseBody: responseBody,
    requestInfo: [
        bodyBytesLength: requestBody.length,
        zipBytesLength: zipBytes?.length ?: 0,
        filesProcessed: fileCount,
        setImageApiUrl: setImageApiUrl
    ]
]

if (debugFlg) {
    println "=== OUTPUT ==="
    println groovy.json.JsonOutput.prettyPrint(groovy.json.JsonOutput.toJson(output))
}

return output