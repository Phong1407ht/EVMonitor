package com.phongnk5.evmonitor.data

object GoongConfig {
    const val REST_API_KEY = "QceUDHELWgPZHA55agye6orFUrfuWg2sKQhldyJZ"
    const val MAP_TILES_KEY = "QJIR450bUC2VKDbw3F8OfEvU9ykpYXjeqSNfGDrn"
    
    fun getStyleUrl(): String {
        return "https://tiles.goong.io/assets/style/style.json?api_key=$MAP_TILES_KEY"
    }
}
