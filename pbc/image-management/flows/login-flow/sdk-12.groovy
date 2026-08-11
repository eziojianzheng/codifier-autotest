/*
 * 環境変数取得
 */

// 動作環境
String environment = System.getenv('ENVIRONMENT') ?: "TRIALRUN"

// デバッグモード 
String debug = System.getenv('APP_DEBUG') ?: "true"

// CETAユーザーのUID
String cetaUserId = System.getenv('CETA_USER_ID') ?: "14761"

// CETAユーザーのパスワード
String cetaUserPassword = System.getenv('CETA_USER_PASSWORD') ?: "TRIALRUN"

// パスワード暗号化の共通鍵
String apiCommonKey = System.getenv('IGS_ENCRIPT_COMMON_KEY') ?: "TRIALRUN"

// IGSシステムAPIのURL
String loginApiUrl = System.getenv('IGS_LOGIN_API_URL') ?: "https://mock.apidog.com/m1/1089198-1078606-default/api/igs/login"

return [
  environment:environment,
  debug:debug,
  cetaUserId:cetaUserId,
  cetaUserPassword:cetaUserPassword,
  apiCommonKey:apiCommonKey,
  loginApiUrl:loginApiUrl
]