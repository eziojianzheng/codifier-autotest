// BASE64エンコード
String encodeCustomBase64EncodedString(String inputStr) {
    def BASE64_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"

    String base64Encoded = inputStr.bytes.encodeBase64().toString()
    StringBuilder modified = new StringBuilder()

    base64Encoded.each { c ->
        int index = BASE64_CHARS.indexOf(c)
        if (index != -1) {
            index = (index + 13) % 64  // -51 mod 64 == +13
            modified.append(BASE64_CHARS.charAt(index))
        } else {
            modified.append(c)
        }
    }

    return modified.toString()
}

return ["passwordEncode":encodeCustomBase64EncodedString("isuzu-dms-igs-mobile")]