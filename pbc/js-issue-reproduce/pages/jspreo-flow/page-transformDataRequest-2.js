(function transform(data) {
  return {
    selectedRows: data.startRows,
    userId: data.uid,
    factory: data.factory,
    operatorId: data.operatorId,
    operator: data.operator,
    shift: data.shift,
  };
})(this);
