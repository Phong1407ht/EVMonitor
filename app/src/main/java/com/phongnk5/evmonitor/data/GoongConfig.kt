package com.phongnk5.evmonitor.data

object GoongConfig {
    const val REST_API_KEY = "mhF4LU3lND5VLKpXpRAXQbgOzcKQnHckWdbq0JeU"
    const val MAP_TILES_KEY = "6cgMmF4indu6IRaWdltLCl534WxoqThBV6r3RcAm"
    
    fun getStyleUrl(): String {
        return "https://tiles.goong.io/assets/goong_map_web.json?api_key=$MAP_TILES_KEY"
    }
}
