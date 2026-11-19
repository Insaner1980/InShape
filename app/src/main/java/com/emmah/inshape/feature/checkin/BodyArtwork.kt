package com.emmah.inshape.feature.checkin

import android.graphics.Matrix
import android.graphics.RectF
import android.graphics.Region
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.vector.PathParser

internal const val BODY_WIDTH = 240f
internal const val BODY_HEIGHT = 392f

internal class BodyPath(
    val id: String?,
    val geometry: androidx.compose.ui.graphics.Path,
    val fill: Color?,
    val detailInk: Color?,
    val bounds: RectF,
    val hitArea: Region?
)

private fun bodyPath(id: String?, fill: Color?, data: String, detailInk: Color? = null, mirrored: Boolean = false): BodyPath {
    val geometry = PathParser().parsePathString(data).toPath()
    val path = geometry.asAndroidPath()
    if (mirrored) {
        path.transform(
            Matrix().apply {
                setScale(-1f, 1f)
                postTranslate(BODY_WIDTH, 0f)
            }
        )
    }
    val bounds = RectF().also { path.computeBounds(it, true) }
    val hitArea = id?.let {
        Region().apply { setPath(path, Region(0, 0, BODY_WIDTH.toInt(), BODY_HEIGHT.toInt())) }
    }
    return BodyPath(id, geometry, fill, detailInk, bounds, hitArea)
}

// Paths follow the source SVG paint order. The anatomy is independent of the UI layout direction.
internal val frontBodyArtwork by lazy {
    listOf(
        bodyPath(
            "right_shoulder",
            Color(0xFFDCE1CD),
            "M88 78 Q80 78 73 84 Q65 92 64 105 Q70 108 76 105 Q83 102 87 94 Z"
        ),
        bodyPath(
            "right_upper_arm",
            Color(0xFFE9ECDF),
            "M64 105 Q69 108 76 105 Q74 119 66 140 Q62 143 56 140 Q58 119 64 105Z"
        ),
        bodyPath(
            "right_elbow",
            Color(0xFFDCE2D2),
            "M56 140 Q61 143 66 140 L64 152 Q60 155 54 151Z"
        ),
        bodyPath(
            "right_forearm_wrist",
            Color(0xFFE8EBDE),
            "M54 151 Q60 155 64 152 Q64 167 57 183 L55 195 Q58 201 54 209 Q52 212 51 206 Q50 216 47 216 Q43 217 " +
                "41 211 Q39 205 43 195 L46 190 Q47 170 54 151Z"
        ),
        bodyPath(
            "right_hip",
            Color(0xFFE0E5D6),
            "M84 168 Q102 170 120 178 L120 194 Q114 194 110 199 Q98 193 80 194 Q79 181 84 168Z"
        ),
        bodyPath(
            "right_thigh",
            Color(0xFFE9ECDE),
            "M80 194 Q98 193 110 199 Q113 220 108 249 Q106 263 105 275 Q98 279 89 275 Q84 260 80 237 Q76 214 80 194Z"
        ),
        bodyPath(
            "right_knee",
            Color(0xFFF0F1E7),
            "M89 275 Q98 279 105 275 Q108 285 105 295 Q98 299 91 295 Q88 286 89 275Z"
        ),
        bodyPath(
            "right_calf_shin",
            Color(0xFFE3E8D8),
            "M91 295 Q98 299 105 295 Q109 307 106 322 Q103 338 102 353 Q98 356 93 353 Q94 339 90 324 Q86 306 91 295Z"
        ),
        bodyPath(
            "right_ankle_foot",
            Color(0xFFE9ECDF),
            "M93 353 Q98 356 102 353 L103 366 Q105 371 104 375 Q101 379 95 379 L83 379 Q79 378 81 374 L89 365 Q92 361 93 353Z"
        ),
        bodyPath(
            "left_shoulder",
            Color(0xFFDCE1CD),
            "M88 78 Q80 78 73 84 Q65 92 64 105 Q70 108 76 105 Q83 102 87 94 Z",
            mirrored = true
        ),
        bodyPath(
            "left_upper_arm",
            Color(0xFFE9ECDF),
            "M64 105 Q69 108 76 105 Q74 119 66 140 Q62 143 56 140 Q58 119 64 105Z",
            mirrored = true
        ),
        bodyPath(
            "left_elbow",
            Color(0xFFDCE2D2),
            "M56 140 Q61 143 66 140 L64 152 Q60 155 54 151Z",
            mirrored = true
        ),
        bodyPath(
            "left_forearm_wrist",
            Color(0xFFE8EBDE),
            "M54 151 Q60 155 64 152 Q64 167 57 183 L55 195 Q58 201 54 209 Q52 212 51 206 Q50 216 47 216 Q43 217 " +
                "41 211 Q39 205 43 195 L46 190 Q47 170 54 151Z",
            mirrored = true
        ),
        bodyPath(
            "left_hip",
            Color(0xFFE0E5D6),
            "M84 168 Q102 170 120 178 L120 194 Q114 194 110 199 Q98 193 80 194 Q79 181 84 168Z",
            mirrored = true
        ),
        bodyPath(
            "left_thigh",
            Color(0xFFE9ECDE),
            "M80 194 Q98 193 110 199 Q113 220 108 249 Q106 263 105 275 Q98 279 89 275 Q84 260 80 237 Q76 214 80 194Z",
            mirrored = true
        ),
        bodyPath(
            "left_knee",
            Color(0xFFF0F1E7),
            "M89 275 Q98 279 105 275 Q108 285 105 295 Q98 299 91 295 Q88 286 89 275Z",
            mirrored = true
        ),
        bodyPath(
            "left_calf_shin",
            Color(0xFFE3E8D8),
            "M91 295 Q98 299 105 295 Q109 307 106 322 Q103 338 102 353 Q98 356 93 353 Q94 339 90 324 Q86 306 91 295Z",
            mirrored = true
        ),
        bodyPath(
            "left_ankle_foot",
            Color(0xFFE9ECDF),
            "M93 353 Q98 356 102 353 L103 366 Q105 371 104 375 Q101 379 95 379 L83 379 Q79 378 81 374 L89 365 Q92 361 93 353Z",
            mirrored = true
        ),
        bodyPath(
            "chest",
            Color(0xFFEDF0E4),
            "M88 78 Q103 74 109 68 Q120 77 131 68 Q138 75 152 78 Q153 93 157 108 Q154 120 147 132 Q120 136 93 " +
                "132 Q86 120 83 108 Q87 94 88 78Z"
        ),
        bodyPath(
            "abdomen",
            Color(0xFFE8EBDF),
            "M93 132 Q120 136 147 132 Q141 149 156 168 Q137 170 120 178 Q103 170 84 168 Q98 149 93 132Z"
        ),
        bodyPath(
            "neck",
            Color(0xFFD9DFCE),
            "M110 49 Q112 61 109 68 Q120 78 131 68 Q128 62 130 49Z"
        ),
        bodyPath(
            null,
            Color(0xFFECEEE4),
            "M102 28 Q102 9 119 9 Q137 8 138 28 L137 41 Q135 51 128 56 Q120 61 112 56 Q104 51 103 42 Q99 40 100 34 Q100 31 102 32Z"
        ),
        bodyPath(
            null,
            Color(0xFFBDC6B3),
            "M102 32 Q99 10 118 9 Q137 7 138 26 L136 32 Q134 25 131 22 Q121 26 114 22 Q108 20 104 31Z"
        ),
        bodyPath(
            null,
            null,
            "M101 85 Q110 86 115 89 M139 85 Q130 86 125 89",
            detailInk = Color(0xFFC5CDBE)
        )
    )
}

internal val backBodyArtwork by lazy {
    listOf(
        bodyPath(
            "left_shoulder",
            Color(0xFFDCE1CD),
            "M88 78 Q80 78 73 84 Q65 92 64 105 Q70 108 76 105 Q83 102 87 94 Z"
        ),
        bodyPath(
            "left_upper_arm",
            Color(0xFFE9ECDF),
            "M64 105 Q69 108 76 105 Q74 119 66 140 Q62 143 56 140 Q58 119 64 105Z"
        ),
        bodyPath(
            "left_elbow",
            Color(0xFFDCE2D2),
            "M56 140 Q61 143 66 140 L64 152 Q60 155 54 151Z"
        ),
        bodyPath(
            "left_forearm_wrist",
            Color(0xFFE8EBDE),
            "M54 151 Q60 155 64 152 Q64 167 57 183 L55 195 Q58 201 54 209 Q52 212 51 206 Q50 216 47 216 Q43 217 " +
                "41 211 Q39 205 43 195 L46 190 Q47 170 54 151Z"
        ),
        bodyPath(
            "left_hip",
            Color(0xFFE0E5D6),
            "M84 168 Q102 170 120 178 L120 194 Q114 194 110 199 Q98 193 80 194 Q79 181 84 168Z"
        ),
        bodyPath(
            "left_thigh",
            Color(0xFFE9ECDE),
            "M80 194 Q98 193 110 199 Q113 220 108 249 Q106 263 105 275 Q98 279 89 275 Q84 260 80 237 Q76 214 80 194Z"
        ),
        bodyPath(
            "left_knee",
            Color(0xFFF0F1E7),
            "M89 275 Q98 279 105 275 Q108 285 105 295 Q98 299 91 295 Q88 286 89 275Z"
        ),
        bodyPath(
            "left_calf_shin",
            Color(0xFFE3E8D8),
            "M91 295 Q98 299 105 295 Q109 307 106 322 Q103 338 102 353 Q98 356 93 353 Q94 339 90 324 Q86 306 91 295Z"
        ),
        bodyPath(
            "left_ankle_foot",
            Color(0xFFE9ECDF),
            "M93 353 Q98 356 102 353 L103 366 Q105 371 104 375 Q101 379 95 379 L83 379 Q79 378 81 374 L89 365 Q92 361 93 353Z"
        ),
        bodyPath(
            "right_shoulder",
            Color(0xFFDCE1CD),
            "M88 78 Q80 78 73 84 Q65 92 64 105 Q70 108 76 105 Q83 102 87 94 Z",
            mirrored = true
        ),
        bodyPath(
            "right_upper_arm",
            Color(0xFFE9ECDF),
            "M64 105 Q69 108 76 105 Q74 119 66 140 Q62 143 56 140 Q58 119 64 105Z",
            mirrored = true
        ),
        bodyPath(
            "right_elbow",
            Color(0xFFDCE2D2),
            "M56 140 Q61 143 66 140 L64 152 Q60 155 54 151Z",
            mirrored = true
        ),
        bodyPath(
            "right_forearm_wrist",
            Color(0xFFE8EBDE),
            "M54 151 Q60 155 64 152 Q64 167 57 183 L55 195 Q58 201 54 209 Q52 212 51 206 Q50 216 47 216 Q43 217 " +
                "41 211 Q39 205 43 195 L46 190 Q47 170 54 151Z",
            mirrored = true
        ),
        bodyPath(
            "right_hip",
            Color(0xFFE0E5D6),
            "M84 168 Q102 170 120 178 L120 194 Q114 194 110 199 Q98 193 80 194 Q79 181 84 168Z",
            mirrored = true
        ),
        bodyPath(
            "right_thigh",
            Color(0xFFE9ECDE),
            "M80 194 Q98 193 110 199 Q113 220 108 249 Q106 263 105 275 Q98 279 89 275 Q84 260 80 237 Q76 214 80 194Z",
            mirrored = true
        ),
        bodyPath(
            "right_knee",
            Color(0xFFF0F1E7),
            "M89 275 Q98 279 105 275 Q108 285 105 295 Q98 299 91 295 Q88 286 89 275Z",
            mirrored = true
        ),
        bodyPath(
            "right_calf_shin",
            Color(0xFFE3E8D8),
            "M91 295 Q98 299 105 295 Q109 307 106 322 Q103 338 102 353 Q98 356 93 353 Q94 339 90 324 Q86 306 91 295Z",
            mirrored = true
        ),
        bodyPath(
            "right_ankle_foot",
            Color(0xFFE9ECDF),
            "M93 353 Q98 356 102 353 L103 366 Q105 371 104 375 Q101 379 95 379 L83 379 Q79 378 81 374 L89 365 Q92 361 93 353Z",
            mirrored = true
        ),
        bodyPath(
            "upper_back",
            Color(0xFFE2E6D7),
            "M88 78 Q104 73 109 68 Q120 72 131 68 Q137 74 152 78 Q153 93 157 108 Q154 119 147 132 Q120 143 93 " +
                "132 Q86 118 83 108 Q87 94 88 78Z"
        ),
        bodyPath(
            "lower_back",
            Color(0xFFEDF0E4),
            "M93 132 Q120 143 147 132 Q140 149 156 168 Q138 170 120 178 Q102 170 84 168 Q99 149 93 132Z"
        ),
        bodyPath(
            "neck",
            Color(0xFFD9DFCE),
            "M110 49 Q112 61 109 68 Q120 78 131 68 Q128 62 130 49Z"
        ),
        bodyPath(
            null,
            Color(0xFFECEEE4),
            "M102 28 Q102 9 119 9 Q137 8 138 28 L137 41 Q135 51 128 56 Q120 61 112 56 Q104 51 103 42 Q99 40 100 34 Q100 31 102 32Z"
        ),
        bodyPath(
            null,
            Color(0xFFB5BEAA),
            "M102 32 Q100 10 117 9 Q135 7 138 24 L137 40 Q133 44 130 50 Q121 46 111 50 Q107 43 103 40Z"
        ),
        bodyPath(
            null,
            null,
            "M104 88 Q107 97 110 102 M136 88 Q133 97 130 102 M120 90v30 M120 144v17",
            detailInk = Color(0xFFBBC4B4)
        )
    )
}
