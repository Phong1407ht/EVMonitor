package com.phongnk5.evmonitor.data.DTOs

data class Geometry(val location: LocationData)
data class LocationData(val lat: Double, val lng: Double)

data class GoongAutocompleteResponse(val predictions: List<GoongPrediction>)
data class GoongPrediction(
    val description: String,
    val place_id: String,
    val structured_formatting: StructuredFormatting
)
data class StructuredFormatting(val main_text: String, val secondary_text: String)

data class GoongPlaceDetailResponse(
    val result: GoongPlaceDetailResult,
    val status: String
)

data class GoongPlaceDetailResult(
    val place_id: String,
    val name: String,
    val formatted_address: String,
    val geometry: Geometry,
    val photos: List<GoongPhoto>?,
    val url: String?
)

data class GoongPhoto(
    val photo_reference: String
)

// Distance Matrix DTOs
data class GoongDistanceMatrixResponse(
    val rows: List<DistanceMatrixRow>
)

data class DistanceMatrixRow(
    val elements: List<DistanceMatrixElement>
)

data class DistanceMatrixElement(
    val distance: DistanceData,
    val duration: DurationData,
    val status: String
)

data class DistanceData(
    val text: String,
    val value: Long
)

data class DurationData(
    val text: String,
    val value: Long
)
