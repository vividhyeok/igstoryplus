package com.vividhyeok.igstoryplus

import kotlin.math.*

data class AudioFeatures(val onsets:FloatArray,val energy:FloatArray,val low:FloatArray,val mid:FloatArray,val high:FloatArray,val flux:FloatArray,val fast:FloatArray,val slow:FloatArray)

/** Mono PCM, 50ms windows, three IIR bands and twelve Goertzel spectral probes. No instrument labels. */
internal class SignalAccumulator(private val size:Int){
    private val low=DoubleArray(size);private val mid=DoubleArray(size);private val high=DoubleArray(size);private val count=IntArray(size)
    private val spectrum=Array(size){DoubleArray(12)};private val s1=DoubleArray(12);private val s2=DoubleArray(12)
    private val frequencies=doubleArrayOf(70.0,110.0,170.0,260.0,400.0,610.0,930.0,1400.0,2100.0,3200.0,4800.0,7000.0)
    private var coefficients=DoubleArray(12);private var sampleRate=0;private var alphaLow=0.0;private var alphaMid=0.0
    private var lowState=0.0;private var midState=0.0;private var current=-1;private var total=0.0;private var channel=0
    fun add(value:Double,bin:Int,rate:Int,channels:Int){
        total+=value;channel++;if(channel<channels)return
        val sample=total/channels;total=0.0;channel=0
        if(bin !in 0 until size)return
        if(rate!=sampleRate){sampleRate=rate;coefficients=DoubleArray(12){2*cos(2*PI*min(frequencies[it],rate*.45)/rate)};alphaLow=1-exp(-2*PI*180/rate);alphaMid=1-exp(-2*PI*2400/rate)}
        if(bin!=current){flush();current=bin}
        lowState+=alphaLow*(sample-lowState);midState+=alphaMid*(sample-midState)
        low[bin]+=lowState*lowState;mid[bin]+=(midState-lowState).pow(2);high[bin]+=(sample-midState).pow(2);count[bin]++
        for(i in s1.indices){val next=sample+coefficients[i]*s1[i]-s2[i];s2[i]=s1[i];s1[i]=next}
    }
    private fun flush(){
        if(current>=0){val n=count[current].coerceAtLeast(1).toDouble();for(i in s1.indices)spectrum[current][i]=sqrt(max(0.0,s1[i]*s1[i]+s2[i]*s2[i]-coefficients[i]*s1[i]*s2[i]))/n}
        s1.fill(0.0);s2.fill(0.0)
    }
    fun finish(rms:DoubleArray,loopSpan:Long?):AudioFeatures{
        flush()
        val bands=listOf(low,mid,high).map{band->DoubleArray(size){sqrt(band[it]/count[it].coerceAtLeast(1))}}
        val flux=DoubleArray(size){i->if(i==0)0.0 else spectrum[i].indices.sumOf{k->max(0.0,spectrum[i][k]-spectrum[i-1][k])}}
        if(loopSpan!=null)for(array in listOf(rms,flux)+bands)for(i in array.indices)if(i*50L>=loopSpan)array[i]=array[((i*50L%loopSpan)/50).toInt().coerceIn(array.indices)]
        fun normalized(values:DoubleArray,referenceValues:DoubleArray=values):FloatArray{
            val sorted=referenceValues.sorted();val reference=sorted[(sorted.lastIndex*.95).toInt()].coerceAtLeast(referenceValues.maxOrNull()?.times(.25)?:0.0).coerceAtLeast(.0001)
            return FloatArray(size){if(rms[it]<.0001)0f else (values[it]/reference).toFloat().coerceIn(0f,1f)}
        }
        val energy=normalized(rms);val fluxN=normalized(flux)
        val onset=Audio.onsets(DoubleArray(size){energy[it]*.85+fluxN[it]*.15})
        fun smooth(coefficient:Float)=FloatArray(size).also{result->for(i in result.indices)result[i]=energy[i]*coefficient+(if(i==0)0f else result[i-1])*(1-coefficient)}
        return AudioFeatures(onset,energy,normalized(bands[0],rms),normalized(bands[1],rms),normalized(bands[2],rms),fluxN,smooth(.55f),smooth(.14f))
    }
}
