package com.vividhyeok.igstoryplus

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*

class AudioFeaturesTest {
    private fun signal(hz:Double,gain:Double=1.0):AudioFeatures{
        val rate=22050;val size=40;val accumulator=SignalAccumulator(size);val squares=DoubleArray(size);val counts=IntArray(size)
        for(i in 0 until rate*2){val bin=(i*20L/rate).toInt();val value=sin(i*2*PI*hz/rate)*gain*(if(bin%10<2).6 else .04);accumulator.add(value,bin,rate,1);squares[bin]+=value*value;counts[bin]++}
        return accumulator.finish(DoubleArray(size){sqrt(squares[it]/counts[it])},null)
    }
    @Test fun bandsRetainFrequencyIdentityAndGainNormalization(){
        val bass=signal(80.0);val mids=signal(800.0);val treble=signal(6500.0)
        assertTrue(bass.low.average()>bass.high.average()*3);assertTrue(mids.mid.average()>mids.low.average()*2);assertTrue(treble.high.average()>treble.low.average()*10)
        val quiet=signal(800.0,.05);assertArrayEquals(mids.energy,quiet.energy,.001f)
        assertTrue(mids.flux.any{it>.8f});assertTrue(mids.fast.indices.any{mids.fast[it]>mids.slow[it]+.1f})
    }
    @Test fun silenceIsActuallySilentAndOnsetsStayInTime(){
        val silent=SignalAccumulator(40).finish(DoubleArray(40),null)
        for(values in listOf(silent.onsets,silent.energy,silent.low,silent.mid,silent.high,silent.flux,silent.fast,silent.slow))assertTrue(values.all{it==0f})
        val signal=signal(220.0);val onset=(10..19).maxBy{signal.onsets[it]};assertTrue(onset in 10..11)
        assertTrue(signal.onsets.all{it.isFinite() && it in 0f..1f})
    }
}
