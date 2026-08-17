(function transform(data) {
  return { ...data, enabled: !!data.enabled };
})(this);
