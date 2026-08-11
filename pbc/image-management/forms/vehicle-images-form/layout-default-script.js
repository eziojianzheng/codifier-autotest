// グローバル変数
let localData = {};

const leaveConfirm = (event) => {
  event.preventDefault();
};

/**
 * 半角数字チェックし、半角数字以外が含まれていればクリアする
 * @param {string} fieldName
 */
function validateHalfWidthNumber(fieldName) {
  const value = formApi.getValue(fieldName);

  if (!value) return; // 空はスルー

  // 半角数字のみ許可
  if (!/^[0-9]+$/.test(value)) {
    setValues({
      [fieldName]: ''
    });
  }
}

/**
 * entryVerifierCode / maintenanceVerifierCode / exitVerifierCode に
 * 半角チェックイベントを設定する
 */
function setVerifierCodeValidation() {
  formApi.onChange('entryVerifierCode', () => {
    validateHalfWidthNumber('entryVerifierCode');
  });

  formApi.onChange('maintenanceVerifierCode', () => {
    validateHalfWidthNumber('maintenanceVerifierCode');
  });

  formApi.onChange('exitVerifierCode', () => {
    validateHalfWidthNumber('exitVerifierCode');
  });
}

/**
 * 半角→全角の変換
 */
function toZenkaku(str) {
  // 半角カタカナを先に全角化
  str = str.normalize('NFKC');

  return str.replace(/[\x20-\x7E]/g, function (s) {
    const map = {
      ' ': '　', // 全角スペース
      '-': '－', // 全角ハイフン
      '~': '～', // 波ダッシュ
      '\\': '￥', // バックスラッシュ → 全角円記号に
    };
    if (map[s]) return map[s];
    return String.fromCharCode(s.charCodeAt(0) + 0xfee0);
  });
}

/**
 * デフォルト値設定
 */
function setDefaultFieldValue() {
  const vehicleEvent = formApi.getValue('vehicleEvent');

  // 現在日付YYYY/MM/DD取得用
  const getFormattedDate = () => {
    const date = new Date();
    return `${date.getFullYear()}/${('0' + (date.getMonth() + 1)).slice(-2)}/${('0' + date.getDate()).slice(-2)}`;
  };

  // 業務種別ごとに、未登録の場合のデフォルト値を設定する
  switch (vehicleEvent[0].value) {
    case '31':
      if (!formApi.getValue('entryVerifyDate')) {
        formApi.setValue('entryVerifyDate', getFormattedDate());
      }
      if (!formApi.getValue('entryVerifierCode')) {
        formApi.setValue('entryVerifierCode', localData.igsLoginId);
      }
      break;
    case '32':
      if (!formApi.getValue('maintenanceVerifyDate')) {
        formApi.setValue('maintenanceVerifyDate', getFormattedDate());
      }
      if (!formApi.getValue('maintenanceVerifierCode')) {
        formApi.setValue('maintenanceVerifierCode', localData.igsLoginId);
      }
      break;
    case '33':
      if (!formApi.getValue('exitVerifyDate')) {
        formApi.setValue('exitVerifyDate', getFormattedDate());
      }
      if (!formApi.getValue('exitVerifierCode')) {
        formApi.setValue('exitVerifierCode', localData.igsLoginId);
      }
      break;
    default:
      break;
  }
}

/**
 * 初期表示時の処理
 */
formApi.on('ready', function () {
  window.addEventListener('beforeunload', leaveConfirm);
  localData = {
    igsLoginDealerCode: localStorage.getItem('IGS_LOGIN_DEALER_CODE'),
    igsLoginId: localStorage.getItem('IGS_LOGIN_ID'),
    igsLoginName: localStorage.getItem('IGS_LOGIN_NAME'),
    igsJwtToken: localStorage.getItem('IGS_JWT_TOKEN'),
  };
  setDefaultFieldValue();
});

/**
 * ブラウザバック時の処理
 */
formApi.on('unmount', function () {
  console.log('unmount');
  window.removeEventListener('beforeunload', leaveConfirm);
  //modalApi.confirm('未登録の内容は破棄されます。よろしいですか？');
  modalApi.confirm({
    content: 'The Title',
    title: 'The Content?',
    onOk: () => {
      console.log('ok');
    },
    onCancel: () => {
      console.log('cancel');
    },
  });
});

/**
 * 全角入力項目の自動修正
 */
formApi.registerMethod('transfunc', function (params) {
  return new Promise(function (resolve, reject) {
    formApi.setValue('registrationNumber', toZenkaku(formApi.getValue('registrationNumber')));
    formApi.setValue('customerName', toZenkaku(formApi.getValue('customerName')));
    resolve('Success');
  });
});
console.log('Before:', formApi.getValue('registrationNumber'));
console.log('After:', toZenkaku(formApi.getValue('registrationNumber')));

/**
 * 登録ボタン
 */
formApi.registerMethod('regifunc', function (params) {
  return new Promise(function (resolve, reject) {
    const buttonApi = formApi.getFieldApi('registerButton');

    const validation = formApi.validateForm();
    validation.then((result) => {
      // バリデーション NG
      if (result) {
        messageApi.error('入力項目の修正が必要です');
        console.log('Validation Error: ', result);
        return reject(new Error('Validation Error'));
      }
    });

    // バリデーション OK
    buttonApi.setLoading(true);

    // FlowAPI呼び出し
    const flowApi = '/flow/api/flow-rest/information-send-flow';
    const requestParams = {
      igsLoginDealerCode: localData.igsLoginDealerCode,
      igsJwtToken: localData.igsJwtToken,
      formdataid: formApi.getContext().formEntityDataId,
    };
    console.log('Parameter:');
    console.table(requestParams);
    restApi
      .post(flowApi, requestParams)
      .then((response) => {
        // 正常レスポンス
        console.log('Normal Response:', response);
        buttonApi.setLoading(false);
        switch (response.responseCode) {
          case '0000':
            messageApi.info('登録完了しました');
            resolve('Success');
            break;
          case '1000':
          case '5000':
          case '5100':
          case '5110':
          case '5200':
          case '5300':
          case '5400':
          case '5500':
          case '5600':
          case '5700':
          case '5800':
            break;
          case '2000':
          case '2010':
          case '3100':
          case '3200':
            messageApi.error(response.message);
            routerApi.navigate('/image-management/login');
            break;
          case '9999':
          default:
            messageApi.error('不明なエラーが発生しました。');
            return reject(new Error('Error'));
            break;
        }
      })
      .catch((error) => {
        // 異常レスポンス
        console.log('Abnormal Response:', error);
        messageApi.error('サーバー処理で異常が発生しました');
        buttonApi.setLoading(false);
        return reject(new Error('Abnormal Response'));
      });
  });
});
