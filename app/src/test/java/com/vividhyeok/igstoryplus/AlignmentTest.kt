package com.vividhyeok.igstoryplus

import org.junit.Assert.*
import org.junit.Test

class AlignmentTest{
    @Test fun levelWorksAcrossNegativeAndFullRotations(){
        assertEquals(-90f,levelAngle(-89f),0f);assertEquals(0f,levelAngle(-2f),0f);assertEquals(360f,levelAngle(358f),0f);assertEquals(12f,levelAngle(12f),0f)
    }
    @Test fun slowIntentCanLeaveAndReenterTheMagnet(){
        val magnet=AlignmentMagnet(9f,16f)
        assertEquals(100f,magnet.value(107f,100f),0f);assertEquals(100f,magnet.value(115f,100f),0f)
        assertEquals(117f,magnet.value(117f,100f),0f);assertFalse(magnet.held)
        assertEquals(112f,magnet.value(112f,100f),0f);assertEquals(100f,magnet.value(108f,100f),0f)
    }
}
