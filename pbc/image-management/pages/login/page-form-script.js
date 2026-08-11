formApi.registerMethod('loginfunc', function (params) {
  return new Promise(function (resolve, reject) {
    const buttonApi = formApi.getFieldApi('loginButton');

    const validation = formApi.validateForm();
    validation.then((result) => {
      // バリデーション NG
      if (result) {
        console.log('Validation Error: ', result);
        return reject(new Error('Validation Error'));
      }

      // バリデーション OK
      buttonApi.setLoading(true);

      const flowApi = '/flow/api/flow-rest-selfauth/login-flow';
      const requestParams = {
        dealerCode: formApi.getValue('dealerCode'),
        loginId: formApi.getValue('loginId'),
        password: formApi.getValue('password'),
      };

      console.log('Flow-API:' + flowApi);
      console.log('Flow-Parameter:');
      console.table({
        ...requestParams,
        password: '******',
      });

      restApi
        .post(flowApi, requestParams)
        .then((response) => {
          console.log('Normal Response:', {
            ...requestParams,
            password: '******',
          });

          const code = String(response.responseCode).trim();

          switch (code) {
            case '0000':
              localStorage.setItem('ACCESS_TOKEN', `"${response.accessToken}"`);
              localStorage.setItem('REFRESH_TOKEN', `"${response.refreshToken}"`);
              localStorage.setItem('IGS_LOGIN_DEALER_CODE', `${response.igsDealerCode}`);
              localStorage.setItem('IGS_LOGIN_ID', `${response.igsUserId}`);
              localStorage.setItem('IGS_LOGIN_NAME', `${response.igsUserName}`);
              localStorage.setItem('IGS_JWT_TOKEN', `${response.igsJwtToken}`);
              return resolve('Success');

            case '1000':
            case '2000':
            case '5100':
            case '5200':
            case '5300':
            case '5400':
            case '5500':
            case '5600':
              messageApi.error(response.message);
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
          messageApi.error('サーバー処理で異常が発生しました');
          return reject(new Error('Abnormal Response'));
        })
        .finally(() => {
          buttonApi.setLoading(false);
        });
    });
  });
});

