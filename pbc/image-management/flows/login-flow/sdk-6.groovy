#!/usr/bin/env groovy

// AES-256-GCM 暗号化ユーティリティ
class AESUtil {
    private static final String ALGORITHM = "AES"
    private static final String TRANSFORMATION = "AES/GCM/NoPadding"
    private static final int IV_LENGTH = 12          // GCM仕様：12byte固定
    private static final int TAG_LENGTH_BIT = 128    // GCMタグ長 16byte

    // AES-256用の32byte鍵生成
    static javax.crypto.spec.SecretKeySpec generateKey(String keyMaterial) {
        java.security.MessageDigest sha = java.security.MessageDigest.getInstance("SHA-256")
        byte[] fullKey = sha.digest(keyMaterial.getBytes("UTF-8"))  // SHA-256 → 32byte
        return new javax.crypto.spec.SecretKeySpec(fullKey, ALGORITHM)
    }

    // AES-256-GCM 暗号化（仕様書準拠）
    static String encrypt(String plainText, String keyMaterial) {

        javax.crypto.spec.SecretKeySpec secretKey = generateKey(keyMaterial)

        // IV 12byte（仕様書必須）
        byte[] iv = new byte[IV_LENGTH]
        new java.security.SecureRandom().nextBytes(iv)

        javax.crypto.spec.GCMParameterSpec gcmSpec =
                new javax.crypto.spec.GCMParameterSpec(TAG_LENGTH_BIT, iv)

        javax.crypto.Cipher cipher = javax.crypto.Cipher.getInstance(TRANSFORMATION)
        cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, secretKey, gcmSpec)

        // GCM の doFinal() は CIPHERTEXT + TAG（16byte）を返す
        byte[] cipherWithTag = cipher.doFinal(plainText.getBytes("UTF-8"))

        // 仕様書形式: IV || CIPHERTEXT || TAG
        byte[] combined = new byte[iv.length + cipherWithTag.length]
        System.arraycopy(iv, 0, combined, 0, iv.length)
        System.arraycopy(cipherWithTag, 0, combined, iv.length, cipherWithTag.length)

        return java.util.Base64.getEncoder().encodeToString(combined)
    }
}


// -----------------------------
//  Binding から inputpassword を取得
// -----------------------------
if (this.binding == null || !this.binding.hasVariable('inputpassword')) {
    throw new IllegalStateException("ワークフロー変数 'inputpassword' が見つかりません。binding に inputpassword がセットされていることを確認してください。")
}

def raw = this.binding.getVariable('inputpassword')
String inputPassword = (raw != null) ? raw.toString() : null
if (!inputPassword) {
    throw new IllegalStateException("'inputpassword' の値が空です。")
}

// 環境変数から取得した共通鍵を使って暗号化
String encryptedResult = AESUtil.encrypt(inputPassword, apiCommonKey)


// Binding と戻り値に格納
try {
    this.binding.setVariable('AESpass', encryptedResult)
} catch (Exception e) {
    println "AES処理のエラー: " + e.printStackTrace()
}

def result = [ AESpass: encryptedResult ]

println groovy.json.JsonOutput.toJson(result)

return result