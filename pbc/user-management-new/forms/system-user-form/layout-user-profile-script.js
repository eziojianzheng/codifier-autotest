function adjustBottomBarStyleIfInMobile() {
  if (window.location.pathname.startsWith(`${window.ICP_PUBLIC_PATH}mobile`)) {
    const bottomBar = document.querySelector('#bottom-bar');
    if (bottomBar) {
      bottomBar.style.position = 'sticky';
      bottomBar.style.bottom = 0;
      bottomBar.style.padding = '20px';
      bottomBar.style.backdropFilter = 'blur(2px)';
      bottomBar.style.zIndex = 2;
    }
  }
}

formApi.on('ready', (params) => {
  adjustBottomBarStyleIfInMobile();
  const context = params?.getContext();
  const userProfile = context?.userProfile;
  const userId = userProfile?.formUserDataId;
  if (!userId) return;
  restApi
    .get(`/form/api/v2/form-entity-data/user-management-new/system-user-form/${userId}`)
    .then((result) => {
      for (const [key, value] of Object.entries(result)) {
        formApi.setValue(key, value);
      }
    });
});

const messages = {
  updateSuccess: {
    'zh-CN': '您的资料已保存。',
    'ja-JP': 'プロフィール情報を保存しました。',
    default: 'Your profile information has been saved.',
  },
  updateFailure: {
    'zh-CN': '更新失败，请重试。',
    'ja-JP': '更新に失敗しました。もう一度お試しください。',
    default: 'Update failed. Please try again.',
  },
};

const getMessage = (key) => messages[key][i18nApi.language] || messages[key]['default'];

formApi.registerMethod('updateUserProfile', async () => {
  try {
    await formApi.submit();
    messageApi.success(getMessage('updateSuccess'));
  } catch (error) {
    console.error(error);
    messageApi.error(getMessage('updateSuccess'));
  }
});
