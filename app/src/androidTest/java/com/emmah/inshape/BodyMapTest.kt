package com.emmah.inshape

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.emmah.inshape.feature.checkin.bodyRegionAt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BodyMapTest {
    @Test fun frontAndBackKeepAnatomicalRightKnee() {
        assertEquals("right_knee", bodyRegionAt(42f, 168f, false))
        assertEquals("right_knee", bodyRegionAt(57f, 168f, true))
        assertEquals("left_knee", bodyRegionAt(57f, 168f, false))
        assertEquals("lower_back", bodyRegionAt(50f, 88f, true))
        assertNull(bodyRegionAt(5f, 5f, true))
    }
}
