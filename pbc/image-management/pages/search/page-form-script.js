// グローバル変数
let localData = {};

/**
 * 初期表示時の処理
 * @param {string} eventType 初期表示時は'ready'を設定
 */
formApi.on('ready', function () {
  localData = {
    igsLoginDealerCode: localStorage.getItem('IGS_LOGIN_DEALER_CODE'),
    igsLoginId: localStorage.getItem('IGS_LOGIN_ID'),
    igsLoginName: localStorage.getItem('IGS_LOGIN_NAME'),
    igsJwtToken: localStorage.getItem('IGS_JWT_TOKEN'),
  };
});

/**
 * 業務種別が変更された時に車台番号フィールドをクリアする
 */
formApi.on('fieldValueChange', (field, value) => {
  if (field === 'veichleEvent') {
    formApi.setValue('vehicleNumber', null);
  }
});

/**
 * バリデーション処理
 */
formApi.registerMethod('validatefunc', function (params) {
  const veichleEvent = formApi.getValue('veichleEvent');
  const orderNumber = formApi.getValue('orderNumber');
  const vehicleNumber = formApi.getValue('vehicleNumber');
  if (veichleEvent == 10) {
    if (orderNumber && vehicleNumber) {
      messageApi.error('注文番号か車台番号のいずれかを削除してください');
      throw new Error('invalid');
    } else if (!orderNumber && !vehicleNumber) {
      messageApi.error('注文番号か車台番号のいずれか一方を入力してください');
      throw new Error('invalid');
    }
  } else if (veichleEvent == 20) {
    if (!orderNumber) {
      messageApi.error('注文番号は必須です');
      throw new Error('invalid');
    }
  } else {
    if (!orderNumber) {
      messageApi.error('RONOは必須です');
      throw new Error('invalid');
    }
  }
});

/**
 検索ボタン
*/
formApi.registerMethod('searchfunc', function (params) {
  return new Promise(function (resolve, reject) {
    const linkFormPath = '/image-management/form/vehicle-images-form/layout/default/';
    const linkLoginPage = '/image-management/page/login/';
    const buttonApi = formApi.getFieldApi('searchButton');

    buttonApi.setLoading(true);

    const flowApi = '/flow/api/flow-rest/information-get-flow';
    const requestParams = {
      igsLoginDealerCode: localData.igsLoginDealerCode,
      igsLoginId: localData.igsLoginId,
      igsJwtToken: localData.igsJwtToken,
      veichleEvent: formApi.getValue('veichleEvent'),
      orderNumber: formApi.getValue('orderNumber'),
      vehicleNumber: formApi.getValue('vehicleNumber'),
    };

    console.log('Flow-API:' + flowApi);
    console.log('Flow-Parameter:');
    console.table(requestParams);

    restApi
      .post(flowApi, requestParams)
      .then((response) => {
        console.log('Normal Response:', response);

        const code = String(response.responseCode).trim();

        switch (code) {
          case '0000':
            resolve();
            routerApi.navigate(linkFormPath + response.newFormId);
            return;

          case '1000':
          case '5100':
          case '5200':
          case '5300':
          case '5400':
          case '5500':
            messageApi.error(response.message);
            return;

          case '2000':
          case '2010':
          case '3100':
          case '3200':
            messageApi.error(response.message);
            resolve();
            routerApi.navigate(linkLoginPage);
            return;

          case '9999':
            messageApi.error('サーバー処理で異常が発生しました');
            return;

          default:
            messageApi.error('サーバー処理で異常が発生しました');
            return;
        }
      })
      .catch((error) => {
        console.log('Abnormal Response:', error);
        messageApi.error(error?.message || 'サーバー処理で異常が発生しました');
        reject(new Error('Abnormal Response'));
      })
      .finally(() => {
        buttonApi.setLoading(false);
      });
  });
});

/**
 * QRコードリーダー機能を登録
 * カメラを使用してQRコードを読み取り、指定されたフィールドに値を設定します
 *
 * @param {Object} params - パラメータオブジェクト
 * @param {string} params.returnField - 読み取った値を設定するフィールド名（デフォルト: 'vehicleNumber'）
 * @param {number} params.timeout - タイムアウト時間（ミリ秒、デフォルト: 60000）
 * @returns {Promise<string>} 読み取ったQRコードの値
 */
formApi.registerMethod('readQRCode', function (params) {
  return new Promise(function (resolve, reject) {
    // ========================================
    // 定数定義
    // ========================================
    const RETURN_FIELD = params?.returnField || 'vehicleNumber';
    const DEFAULT_TIMEOUT = 60000; // 60秒
    const JSQR_CDN_URL = 'https://cdn.jsdelivr.net/npm/jsqr@1.4.0/dist/jsQR.min.js';

    // ========================================
    // 状態管理用変数
    // ========================================
    let animationFrameId = null;
    let cameraStream = null;
    let isQRCodeRead = false;
    let isClosed = false;
    let modalElements = null;
    let videoElement = null;

    // ========================================
    // jsQRライブラリの動的ロード
    // ========================================
    /**
     * jsQRライブラリをCDNから動的にロード（既にロード済みの場合は再利用）
     * @returns {Promise<void>}
     */
    function loadJsQRLibrary() {
      return new Promise((res, rej) => {
        if (window.jsQR) {
          return res();
        }

        const script = document.createElement('script');
        script.src = JSQR_CDN_URL;
        script.async = true;
        script.onload = () => res();
        script.onerror = () => rej(new Error('jsQRライブラリの読み込みに失敗しました'));
        document.head.appendChild(script);
      });
    }

    // ========================================
    // モーダルUI作成
    // ========================================
    /**
     * QRコードリーダー用のモーダルUIを作成
     * @returns {Object} モーダル要素のオブジェクト
     */
    function createQRReaderModal() {
      // オーバーレイ（背景）
      const overlay = document.createElement('div');
      overlay.style.position = 'fixed';
      overlay.style.left = '0';
      overlay.style.top = '0';
      overlay.style.width = '100%';
      overlay.style.height = '100%';
      overlay.style.background = 'rgba(0, 0, 0, 0.75)';
      overlay.style.backdropFilter = 'blur(4px)';
      overlay.style.display = 'flex';
      overlay.style.alignItems = 'center';
      overlay.style.justifyContent = 'center';
      overlay.style.zIndex = '9999';
      overlay.style.animation = 'fadeIn 0.2s ease-out';

      // コンテナ（白い枠）
      const container = document.createElement('div');
      container.style.position = 'relative';
      container.style.background = '#ffffff';
      container.style.borderRadius = '16px';
      container.style.padding = '24px';
      container.style.maxWidth = '600px';
      container.style.width = '90%';
      container.style.maxHeight = '90%';
      container.style.display = 'flex';
      container.style.flexDirection = 'column';
      container.style.alignItems = 'center';
      container.style.gap = '16px';
      container.style.boxShadow = '0 20px 60px rgba(0, 0, 0, 0.3), 0 0 0 1px rgba(0, 0, 0, 0.1)';
      container.style.animation = 'slideUp 0.3s ease-out';

      // タイトル
      const title = document.createElement('div');
      title.textContent = 'QRコードをスキャン';
      title.style.fontSize = '20px';
      title.style.fontWeight = '600';
      title.style.color = '#1a1a1a';
      title.style.marginBottom = '4px';

      // 閉じるボタン
      const closeBtn = document.createElement('button');
      closeBtn.innerHTML = '×';
      closeBtn.style.position = 'absolute';
      closeBtn.style.top = '16px';
      closeBtn.style.right = '16px';
      closeBtn.style.width = '32px';
      closeBtn.style.height = '32px';
      closeBtn.style.border = 'none';
      closeBtn.style.borderRadius = '50%';
      closeBtn.style.background = '#f0f0f0';
      closeBtn.style.color = '#666';
      closeBtn.style.fontSize = '24px';
      closeBtn.style.lineHeight = '1';
      closeBtn.style.cursor = 'pointer';
      closeBtn.style.display = 'flex';
      closeBtn.style.alignItems = 'center';
      closeBtn.style.justifyContent = 'center';
      closeBtn.style.transition = 'all 0.2s ease';
      closeBtn.style.zIndex = '2';
      closeBtn.onmouseenter = () => {
        closeBtn.style.background = '#e0e0e0';
        closeBtn.style.transform = 'scale(1.1)';
      };
      closeBtn.onmouseleave = () => {
        closeBtn.style.background = '#f0f0f0';
        closeBtn.style.transform = 'scale(1)';
      };

      // ローディング表示
      const loading = document.createElement('div');
      loading.id = 'qr-loading';
      loading.style.display = 'flex';
      loading.style.flexDirection = 'column';
      loading.style.alignItems = 'center';
      loading.style.gap = '12px';
      loading.style.padding = '40px';
      loading.style.color = '#666';
      loading.style.fontSize = '14px';

      const spinner = document.createElement('div');
      spinner.style.width = '40px';
      spinner.style.height = '40px';
      spinner.style.border = '3px solid #f0f0f0';
      spinner.style.borderTop = '3px solid #3b82f6';
      spinner.style.borderRadius = '50%';
      spinner.style.animation = 'spin 1s linear infinite';

      const loadingText = document.createElement('div');
      loadingText.textContent = 'カメラを起動中...';

      loading.appendChild(spinner);
      loading.appendChild(loadingText);

      // キャンバスラッパー（角丸適用のため）
      const canvasWrapper = document.createElement('div');
      canvasWrapper.style.borderRadius = '12px';
      canvasWrapper.style.overflow = 'hidden';
      canvasWrapper.style.boxShadow = '0 4px 12px rgba(0, 0, 0, 0.15)';
      canvasWrapper.style.maxWidth = '100%';
      canvasWrapper.style.maxHeight = '70vh';
      canvasWrapper.hidden = true;

      // キャンバス（カメラ映像とQRコード解析用）
      const canvas = document.createElement('canvas');
      canvas.id = 'qr-canvas';
      canvas.style.display = 'block';
      canvas.style.maxWidth = '100%';
      canvas.style.maxHeight = '70vh';
      canvas.style.objectFit = 'contain';

      canvasWrapper.appendChild(canvas);

      // アニメーションキーフレームを追加
      const style = document.createElement('style');
      style.textContent = `
        @keyframes fadeIn {
          from { opacity: 0; }
          to { opacity: 1; }
        }
        @keyframes slideUp {
          from { 
            opacity: 0;
            transform: translateY(20px);
          }
          to { 
            opacity: 1;
            transform: translateY(0);
          }
        }
        @keyframes spin {
          to { transform: rotate(360deg); }
        }
      `;
      document.head.appendChild(style);

      // 要素を組み立て
      container.appendChild(closeBtn);
      container.appendChild(title);
      container.appendChild(loading);
      container.appendChild(canvasWrapper);
      overlay.appendChild(container);
      document.body.appendChild(overlay);

      return { overlay, container, canvas, loading, closeBtn, canvasWrapper };
    }

    // ========================================
    // クリーンアップ処理
    // ========================================
    /**
     * カメラストリーム、アニメーションフレーム、モーダルUIをクリーンアップ
     */
    function cleanup() {
      // アニメーションフレームのキャンセル
      if (animationFrameId) {
        cancelAnimationFrame(animationFrameId);
        animationFrameId = null;
      }

      // カメラストリームの停止
      if (cameraStream) {
        cameraStream.getTracks().forEach((track) => track.stop());
        cameraStream = null;
      }

      // ビデオ要素のクリーンアップ
      if (videoElement) {
        videoElement.srcObject = null;
        videoElement = null;
      }

      // モーダルUIの削除
      if (modalElements && modalElements.overlay) {
        modalElements.overlay.remove();
        modalElements = null;
      }
    }

    // ========================================
    // 閉じる処理
    // ========================================
    /**
     * モーダルを閉じてエラーを返す
     */
    function handleClose() {
      if (isClosed) return;
      isClosed = true;
      cleanup();
      reject(new Error('ユーザーがキャンセルしました'));
    }

    // ========================================
    // QRコード読み取りループ
    // ========================================
    /**
     * カメラ映像からQRコードを継続的に読み取る
     * @param {CanvasRenderingContext2D} canvasContext - キャンバスのコンテキスト
     */
    function startQRCodeScanLoop(canvasContext) {
      function scanTick() {
        animationFrameId = requestAnimationFrame(scanTick);

        // ビデオの準備ができていない場合はスキップ
        if (videoElement.readyState !== videoElement.HAVE_ENOUGH_DATA) {
          return;
        }

        // 初回のみ: ローディング非表示、キャンバス表示
        if (modalElements.loading && modalElements.loading.style.display !== 'none') {
          modalElements.loading.style.display = 'none';
          modalElements.canvasWrapper.hidden = false;
        }

        // ビデオの映像をキャンバスに描画
        const videoWidth = videoElement.videoWidth;
        const videoHeight = videoElement.videoHeight;
        modalElements.canvas.width = videoWidth;
        modalElements.canvas.height = videoHeight;
        canvasContext.drawImage(videoElement, 0, 0, videoWidth, videoHeight);

        try {
          // QRコードの解析
          const imageData = canvasContext.getImageData(0, 0, videoWidth, videoHeight);
          const qrCode = window.jsQR(imageData.data, imageData.width, imageData.height, {
            inversionAttempts: 'dontInvert',
          });

          // QRコードが検出された場合
          if (qrCode && !isQRCodeRead) {
            isQRCodeRead = true;
            formApi.setValue(RETURN_FIELD, qrCode.data);
            cleanup();
            resolve(qrCode.data);
          }
        } catch (error) {
          // getImageDataはクロスオリジンや他の理由で例外が発生する場合がある
          console.error('QRコード読み取りエラー:', error);
        }
      }

      scanTick();
    }

    // ========================================
    // イベントハンドラー登録
    // ========================================
    /**
     * 閉じるボタンのクリックハンドラー
     */
    function handleCloseButtonClick(event) {
      if (!modalElements) return;

      if (event.target === modalElements.closeBtn) {
        document.removeEventListener('click', handleCloseButtonClick);
        document.removeEventListener('click', handleOverlayClick);
        handleClose();
      }
    }

    /**
     * オーバーレイ外クリックのハンドラー
     */
    function handleOverlayClick(event) {
      if (!modalElements) return;

      if (event.target === modalElements.overlay) {
        document.removeEventListener('click', handleCloseButtonClick);
        document.removeEventListener('click', handleOverlayClick);
        handleClose();
      }
    }

    // ========================================
    // タイムアウト処理
    // ========================================
    /**
     * タイムアウト処理を設定
     */
    const timeoutDuration = (params && params.timeout) || DEFAULT_TIMEOUT;
    const timeoutId = setTimeout(() => {
      if (!isQRCodeRead && !isClosed) {
        isClosed = true;
        document.removeEventListener('click', handleCloseButtonClick);
        document.removeEventListener('click', handleOverlayClick);
        cleanup();
        reject(new Error('タイムアウトしました'));
      }
    }, timeoutDuration);

    // ========================================
    // Promise のラッパー（クリーンアップ保証）
    // ========================================
    const originalResolve = resolve;
    const originalReject = reject;

    resolve = function (value) {
      clearTimeout(timeoutId);
      document.removeEventListener('click', handleCloseButtonClick);
      document.removeEventListener('click', handleOverlayClick);
      originalResolve(value);
    };

    reject = function (error) {
      clearTimeout(timeoutId);
      document.removeEventListener('click', handleCloseButtonClick);
      document.removeEventListener('click', handleOverlayClick);
      originalReject(error);
    };

    // ========================================
    // メイン処理の実行
    // ========================================
    loadJsQRLibrary()
      .then(() => {
        // モーダルUIの作成
        modalElements = createQRReaderModal();

        // ビデオ要素の作成
        videoElement = document.createElement('video');
        videoElement.setAttribute('playsinline', 'true');

        const canvasContext = modalElements.canvas.getContext('2d');

        // カメラアクセスの取得
        return navigator.mediaDevices
          .getUserMedia({ video: { facingMode: 'environment' } })
          .then((stream) => {
            cameraStream = stream;
            videoElement.srcObject = stream;
            return videoElement.play();
          })
          .then(() => {
            // QRコード読み取りループの開始
            startQRCodeScanLoop(canvasContext);

            // イベントリスナーの登録
            document.addEventListener('click', handleCloseButtonClick);
            document.addEventListener('click', handleOverlayClick);
          });
      })
      .catch((error) => {
        cleanup();
        reject(error);
      });
  });
});
