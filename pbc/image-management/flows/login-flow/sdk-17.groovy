/**
 * ログインAPI呼び出し処理
 * 
 * INPUT: dealerCode, userId, password, loginApiUrl, debug
 * OUTPUT: output (responseCode, responseMessage, responseBody)
 */
 
import groovy.json.JsonOutput
import groovy.json.JsonSlurper

debugFlg = (debug == 'true')

if (debugFlg) {
    println "=== INPUT ==="
    println "dealerCode: ${dealerCode}"
    println "userId: ${userId}"
    println "password: ${password}"
    println "loginApiUrl: ${loginApiUrl}"
    println "debug: ${debug}"
}


// リクエストボディの構築とPOST送信
def requestMap = [
    hansha_code: dealerCode,
    user_id: userId,
    password: password
]
def requestJson = JsonOutput.toJson(requestMap)
def requestJsonForLog = JsonOutput.toJson(requestMap + [password: '****'])

def connection = new URL(loginApiUrl).openConnection().with {
    requestMethod = "POST"
    doOutput = true
    setRequestProperty("Content-Type", "application/json")
    outputStream.withWriter("UTF-8") { it.write(requestJson) }
    it
}

// レスポンス取得と解析
def responseCode = connection.responseCode
def responseMessage = connection.responseMessage
def responseText = ""
def responseBody = [:]

try {
    responseText = (responseCode >= 200 && responseCode < 300) 
        ? connection.inputStream.text 
        : (connection.errorStream?.text ?: "")
    
    if (responseText) {
        try {
            responseBody = new JsonSlurper().parseText(responseText)
        } catch (Exception ex) {
            responseBody = [raw: responseText, parseError: ex.message]
            println "[ERROR] JSON解析エラー: ${ex.message}"
        }
    }
} catch (Exception e) {
    responseBody = [error: e.message]
    println "[ERROR] レスポンス取得エラー: ${e.message}"
}

// APIリクエスト・レスポンス情報
println "=== HTTP Request ==="
println "URL: ${loginApiUrl}"
println "Method: POST"
println "Headers:\n  Content-Type: application/json"
println "Body: ${requestJsonForLog}"
println "=== HTTP Response ==="
println "Response Code: ${responseCode}"
println "Response Message: ${responseMessage}"
println "Response Raw Body: ${responseText}"

// 最終出力
def output = [
    status: responseCode,
    responseCode: responseBody?.responseCode ?: null,
    message: responseBody?.message ?: null,
    responseBody: responseBody
]

if (debugFlg) {
println "=== OUTPUT ==="
println groovy.json.JsonOutput.prettyPrint(groovy.json.JsonOutput.toJson(output))
}

return output