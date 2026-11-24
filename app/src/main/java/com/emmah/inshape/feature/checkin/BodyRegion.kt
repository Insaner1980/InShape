package com.emmah.inshape.feature.checkin

import androidx.annotation.StringRes
import com.emmah.inshape.R

data class BodyRegion(val id: String, @StringRes val label: Int)

val bodyRegions = listOf(
    BodyRegion("neck", R.string.region_neck),
    BodyRegion("upper_back", R.string.region_upper_back),
    BodyRegion("lower_back", R.string.region_lower_back),
    BodyRegion("chest", R.string.region_chest),
    BodyRegion("abdomen", R.string.region_abdomen),
    BodyRegion("left_shoulder", R.string.region_left_shoulder),
    BodyRegion("right_shoulder", R.string.region_right_shoulder),
    BodyRegion("left_upper_arm", R.string.region_left_upper_arm),
    BodyRegion("right_upper_arm", R.string.region_right_upper_arm),
    BodyRegion("left_elbow", R.string.region_left_elbow),
    BodyRegion("right_elbow", R.string.region_right_elbow),
    BodyRegion("left_forearm_wrist", R.string.region_left_forearm_wrist),
    BodyRegion("right_forearm_wrist", R.string.region_right_forearm_wrist),
    BodyRegion("left_hip", R.string.region_left_hip),
    BodyRegion("right_hip", R.string.region_right_hip),
    BodyRegion("left_thigh", R.string.region_left_thigh),
    BodyRegion("right_thigh", R.string.region_right_thigh),
    BodyRegion("left_knee", R.string.region_left_knee),
    BodyRegion("right_knee", R.string.region_right_knee),
    BodyRegion("left_calf_shin", R.string.region_left_calf_shin),
    BodyRegion("right_calf_shin", R.string.region_right_calf_shin),
    BodyRegion("left_ankle_foot", R.string.region_left_ankle_foot),
    BodyRegion("right_ankle_foot", R.string.region_right_ankle_foot)
)

@StringRes
internal fun bodyRegionLabel(id: String): Int = bodyRegions.find { it.id == id }?.label ?: R.string.body_region_unknown
