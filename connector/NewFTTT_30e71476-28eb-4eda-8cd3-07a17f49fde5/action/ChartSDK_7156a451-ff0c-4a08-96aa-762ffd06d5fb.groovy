import com.ceta.plugin.sdk.ChartSdk
def title = "1"
def xTitle = "1"
def yTitle = "1"
def seriesName ="1"
def values = [
    20250120,
    20250121
  ]

def categories= [
    "20250120",
    "20250121"
  ]

return ["lineChart": ChartSdk.drawLineChartBase64(
                title, xTitle, yTitle, seriesName,
                categories,
                values
        )]