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
String getImageApiUrl = System.getenv('IGS_GET_IMAGE_API_URL') ?: "https://3bf50a37-8e40-44d0-8933-e9f4daf200c9.mock.pstmn.io/api/igs/getimage"

return[environment:environment, debug:debug, fssS3Bucket:fssS3Bucket, fssS3Host:fssS3Host, getImageApiUrl:getImageApiUrl]