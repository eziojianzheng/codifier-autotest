// product 表单交互脚本（Codifier 拆分测试：应被提取为独立 .js 文件）
// 特殊字符测试: 中文注释 / emoji 🚀 / 引号 "quoted" 'single'
export default {
  onFieldChange(field, value, formApi) {
    if (field === 'price') {
      const p = Number(value) || 0;
      // 价格超过 10000 自动关闭上架开关
      if (p > 10000) {
        formApi.setFieldValue('active', false);
      }
    }
  }
}
