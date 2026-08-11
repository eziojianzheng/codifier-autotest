/*
 * 環境変数取得
 */

// 動作環境
String environment = System.getenv('ENVIRONMENT') ?: "TRIALRUN"

// デバッグモード 
String debug = System.getenv('APP_DEBUG') ?: "true"

// FSSのS3バケット
String fssS3Bucket = System.getenv('CETA_FSS_S3_BUCKET') ?: "NOT_S3"

// FSSのS3ホスト
String fssS3Host = System.getenv('CETA_FSS_S3_HOST') ?: "NOT_S3"

// IGSシステムAPIのURL
String setImageApiUrl = System.getenv('IGS_SET_IMAGE_API_URL') ?: "https://mock.apidog.com/m1/1089198-1078606-default/api/igs/setimage"

return [environment:environment, debug:debug, setImageApiUrl:setImageApiUrl,fssS3Bucket:fssS3Bucket, fssS3Host:fssS3Host]