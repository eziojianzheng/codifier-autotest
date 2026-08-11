/**
 * 画像ダウンロード＆圧縮処理
 * URLリストから画像をダウンロードし、サイズ圧縮してBase64エンコードする
 * 
 * INPUT: imageUrls, debug
 * OUTPUT: resultList (fileName, base64Data)
 */

import java.net.URL
import java.nio.file.Files
import org.apache.commons.io.FileUtils
import org.im4java.core.ConvertCmd
import org.im4java.core.IMOperation

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
    println "imageUrls: ${imageUrls}"
    println "debug: ${debug}"
}

def processImagesFromUrls(List<String> imageUrls, boolean debugFlg) {
    def tempDir = Files.createTempDirectory("image_processing").toFile()
    def resultMap = [:]
    
    try {
        if (debugFlg) {
            println "処理を開始します: ${imageUrls.size()} 件の画像URL"
            println "=" * 80
        }
        
        int idx = 0
        imageUrls.each {
            try {
                if (debugFlg) {
                    println "\n[${idx + 1}/${imageUrls.size()}] 画像を処理中..."
                    println "  URL: $it"
                }
                // 画像をダウンロード
                def downloadedImage = downloadImageFromUrl(it, tempDir, debugFlg)
                if (!downloadedImage) {
                    if (debugFlg) println "ダウンロードに失敗しました: $it"
                    idx++
                    return
                }
                // ダウンロードした画像の元のサイズを記録
                def originalSizeKB = downloadedImage.length() / 1024.0
                // 出力ファイル名を生成
                def fileName = getFileNameFromUrl(it)
                def dotIndex = fileName.lastIndexOf('.')
                def baseName = dotIndex > 0 ? fileName.substring(0, dotIndex) : fileName
                def outputFile = new File(tempDir, "converted_${baseName}.jpeg")
                // IM4Javaを使って画像を変換・圧縮
                def success = convertAndCompressWithIM4Java(downloadedImage, outputFile, originalSizeKB, debugFlg)
                if (success) {
                    // Base64に変換
                    def base64String = fileToBase64(outputFile)
                    // 結果mapに追加
                    resultMap["${baseName}.jpeg"] = base64String
                    if (debugFlg) {
                        def compressedSizeKB = outputFile.length() / 1024.0
                        def compressionRatio = ((originalSizeKB - compressedSizeKB) / originalSizeKB * 100).round(1)
                        println "画像処理完了: $baseName"
                        println "  圧縮前: ${String.format('%.2f', originalSizeKB)} KB → 圧縮後: ${String.format('%.2f', compressedSizeKB)} KB (${compressionRatio}% 削減)"
                    }
                } else {
                    if (debugFlg) println "画像の変換に失敗しました: $fileName"
                }
            } catch (Exception e) {
                if (debugFlg) {
                    println "画像の処理に失敗しました: ${e.message}"
                    e.printStackTrace()
                }
            }
            idx++
        }
        
        if (debugFlg) {
            println "\n" + "=" * 80
            println "すべての画像処理が完了しました"
            println "正常に処理されたファイル数: ${resultMap.size()} / ${imageUrls.size()}"
            println "=" * 80
        }
        return resultMap
            if (debugFlg) {
                println "=== OUTPUT ==="
                println groovy.json.JsonOutput.prettyPrint(groovy.json.JsonOutput.toJson(resultMap))
            }
        
    } finally {
        // 一時ファイルをクリーンアップ
        cleanUp(tempDir, debugFlg)
    }
}

// IM4Javaを使用して画像を変換および圧縮
def convertAndCompressWithIM4Java(File inputFile, File outputFile, double originalSizeKB, boolean debugFlg) {
    try {
        // 目標のサイズと容量を定義
        int maxWidth = 700
        int maxHeight = 500
        long maxSizeKB = 500
        
        if (debugFlg) {
            println "  画像を処理中: ${inputFile.name}"
            println "  目標: ${maxWidth}x${maxHeight}、最大容量: ${maxSizeKB} KB"
        }
        
        def convertCmd = new ConvertCmd()
        def operation = new IMOperation()
        operation.addImage(inputFile.absolutePath)
        
        // 変換パラメータを設定
        operation.resize(maxWidth, maxHeight, ">") // 最大サイズを制限、アスペクト比を維持
        operation.strip() // メタデータを削除
        operation.quality(85.0) // 初期品質を設定
        operation.define("jpeg:extent=${maxSizeKB}kb") // jpeg:extentオプションで自動的に最大サイズ以下に圧縮
        operation.addImage(outputFile.absolutePath) // 出力ファイルを追加
        
        // 変換を実行
        convertCmd.run(operation)
        
        // 出力ファイルの確認
        if (!outputFile.exists() || outputFile.length() == 0) {
            throw new IOException("IM4Javaが出力ファイルを生成しませんでした")
        }
        
        return true
    } catch (Exception e) {
        if (debugFlg) {
            println "IM4Javaによる画像変換・圧縮に失敗しました: ${e.message}"
            e.printStackTrace()
        }
        return false
    }
}

// URLから単一画像をダウンロード
def downloadImageFromUrl(String imageUrl, File tempDir, boolean debugFlg) {
    try {
        def url = new URL(imageUrl)
        def fileName = getFileNameFromUrl(imageUrl) ?: "image_" + System.currentTimeMillis() + ".tmp"
        def outputFile = new File(tempDir, fileName)
        
        if (debugFlg) println "  ダウンロード中..."
        // SSL証明書の問題を回避するため、接続を直接制御
        def connection = url.openConnection()
        connection.setConnectTimeout(10000)
        connection.setReadTimeout(10000)
        connection.inputStream.withStream { input ->
            outputFile.withOutputStream { output ->
                output << input
            }
        }
        
        if (!outputFile.exists() || outputFile.length() == 0) {
            throw new IOException("ダウンロードに失敗したかファイルが空です")
        }
        
        // JPG/JPEG形式かどうか検証
        def dotIndex = outputFile.name.lastIndexOf('.')
        def ext = (dotIndex > 0 ? outputFile.name.substring(dotIndex + 1) : "").toLowerCase()
        if (ext != 'jpg' && ext != 'jpeg') {
            throw new IOException("JPG/JPEG形式ではありません: ${outputFile.name}")
        }
        
        if (debugFlg) {
            println "  ダウンロード完了: ${outputFile.name} (${String.format('%.2f', outputFile.length() / 1024.0)} KB)"
        }
        return outputFile
        
    } catch (Exception e) {
        if (debugFlg) println "  ✗ 画像のダウンロードに失敗しました: ${e.message}"
        return null
    }
}

// URLからファイル名を抽出
def getFileNameFromUrl(String url) {
    try {
        def urlObj = new URL(url)
        def path = urlObj.path
        def lastSlashIndex = path.lastIndexOf('/')
        if (lastSlashIndex >= 0 && lastSlashIndex < path.length() - 1) {
            return path.substring(lastSlashIndex + 1)
        }
        return "image_" + System.currentTimeMillis() + ".tmp"
    } catch (Exception e) {
        return "image_" + System.currentTimeMillis() + ".tmp"
    }
}

// ファイルをBase64に変換
def fileToBase64(File file) {
    if (!file.exists()) {
        throw new FileNotFoundException("ファイルが存在しません: ${file.absolutePath}")
    }
    // 改行なしの標準的なBase64エンコード
    return file.bytes.encodeBase64().toString()
}

// 一時ファイルをクリーンアップ
def cleanUp(File tempDir, boolean debugFlg) {
    if (tempDir.exists()) {
        try {
            FileUtils.deleteDirectory(tempDir)
            if (debugFlg) println "一時ファイルをクリーンアップしました"
        } catch (Exception e) {
            if (debugFlg) println "一時ファイルのクリーンアップに失敗しました: ${e.message}"
        }
    }
}

try {
    def resultMap = processImagesFromUrls(imageUrls, debugFlg)
    
    if (debugFlg) {
        println "処理が完了しました!"
        println "正常に処理された画像数: ${resultMap.size()}"
        resultMap.each { key, value ->
            println "画像: $key, Base64長: ${value.length()}"
        }
    }
    
    def resultList = resultMap.collect { key, value ->
        [fileName: key, base64Data: value]
    }
    
    def output = [resultList: resultList]
    // APIレスポンス情報はdebugFlgに関わらず必ず出力
    println "=== OUTPUT ==="
    // Base64部分はdebugFlgで省略切り替え
    def outputForLog = [
        resultList: output.resultList.collect { item ->
            [fileName: item.fileName, base64Data: abbreviateBase64ForLog(item.base64Data, debugFlg)]
        }
    ]
    println groovy.json.JsonOutput.prettyPrint(groovy.json.JsonOutput.toJson(outputForLog))
    // 最終出力
    return output
    
} catch (Exception e) {
    println "[ERROR] 致命的エラー: ${e.message}"
    e.printStackTrace()
    return [resultList: []]
}