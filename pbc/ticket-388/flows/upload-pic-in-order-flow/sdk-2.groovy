def baseUrl = "http://127.0.0.1:9220"
//def baseUrl = "http://file-storage-svc:9200"
def urls = []

// 遍历dataInput中的每个对象
dataList.each { item ->
    // 确保uploadPic和resources存在
    if (item.uploadPic && item.uploadPic.resources) {
        // 遍历resources中的每个资源
        item.uploadPic.resources.each { resource ->
            if (resource.url) {
                // 将baseUrl和url拼接后添加到urls列表
                def fullUrl = baseUrl + resource.url
                urls.add(fullUrl)
            }
        }
    }
}

return [urls:urls]