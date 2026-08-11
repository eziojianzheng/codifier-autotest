// Codifier 拆分测试 Groovy 脚本 — 应被提取为独立 .groovy 文件
// 特殊字符: 中文 / "quotes" / emoji 🚀
def input = binding.variables
int a = 4 + 8
int b = a * 2
def result = [
    sum: a,
    doubled: b,
    note: "computed by groovy",
    ts: System.currentTimeMillis()
]
return result
