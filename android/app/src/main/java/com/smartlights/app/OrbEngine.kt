package com.smartlights.app

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

// Kotlin port of the geometry engine of thinking-orbs by Jakub Antalik (MIT License,
// https://github.com/Jakubantalik/thinking-orbs, commit de85557). Only the three states the voice bar uses are
// ported: listening (wave), solving (rubik) and breathing (ring). Pure math with no Android imports, so a JVM test
// can compare its frames against the library's golden vectors (spec/orbs-golden.json).

enum class OrbState { Listening, Solving, Breathing }

/** One dot of a finished frame. `white` is ink: 0 = darkest ink on paper. Frames are z-sorted far → near. */
class OrbDot(val x:Double, val y:Double, val z:Double, var r:Double, val white:Double, val a:Double=1.0)

object OrbEngine {
 internal enum class Mode { Wave, Rubik, Ring }
 class Resolved internal constructor(internal val mode:Mode, val speed:Double, internal val opts:Map<String,Double>)

 // JavaScript's Math.round (half rounds up), so lattice sizes match the web library exactly.
 private fun jsRound(x:Double)=floor(x+0.5)

 // --- presets (size 64 and 20) and the profile scaling machinery -------------------------------------------
 private val countPairs=listOf("latRings" to "lonDensity","rings" to "lonDensity","lanes" to "segs")
 private val countKeys=listOf("orbitN","ghostN","nodeN","strandN","signals")
 private val radiusKeys=listOf("rBase","rDepth","rActive","rDot","ghostR","partR","partRDepth","nodeR","nodeRDepth")
 private val base=mapOf(
  Mode.Rubik to mapOf("latRings" to 15.0,"lonDensity" to 40.0,"moveCount" to 14.0,"rBase" to 0.6,"rDepth" to 1.7,"rActive" to 0.3,"inkFar" to 0.62,"inkSpan" to 0.54,"rsPow" to 0.6,"rMin" to 0.3),
  Mode.Wave to mapOf("rings" to 15.0,"lonDensity" to 40.0,"rBase" to 0.6,"rDepth" to 1.7,"rsPow" to 0.6,"rMin" to 0.3),
  Mode.Ring to mapOf("lanes" to 5.0,"segs" to 88.0,"ghostN" to 0.0,"faceOn" to 1.0,"rBase" to 1.1,"rDepth" to 1.7,"rsPow" to 0.6,"rMin" to 0.3))
 private class Preset(val speed:Double, val count:Double, val size:Double, val extra:Map<String,Double> = emptyMap())
 private val presets=mapOf(
  Mode.Rubik to mapOf(64 to Preset(1.82,0.35,1.05),20 to Preset(1.95,0.088,1.9)),
  Mode.Wave to mapOf(64 to Preset(4.388,0.341,1.0),20 to Preset(3.998,0.105,1.6)),
  Mode.Ring to mapOf(64 to Preset(3.24,0.25,0.956,mapOf("spin" to 0.0,"bandMul" to 3.627,"wobMul" to 0.368)),20 to Preset(3.78,0.028,1.622,mapOf("spin" to 0.0,"bandMul" to 3.968,"wobMul" to 0.565))))

 private fun scaleCounts(o:Map<String,Double>,scale:Double):Map<String,Double> {
  val out=o.toMutableMap(); val done=HashSet<String>(); val rt=sqrt(scale)
  for((a,b) in countPairs) { val va=out[a]; val vb=out[b]
   if(va!=null && vb!=null && a !in done && b !in done) { out[a]=max(2.0,jsRound(va*rt)); out[b]=max(2.0,jsRound(vb*rt)); done+=a; done+=b } }
  for(k in countKeys) { val v=out[k]; if(v!=null && v!=0.0 && k !in done) out[k]=max(1.0,jsRound(v*scale)) }
  out["iconD"]?.let { out["iconD"]=max(0.02,it*scale) }
  return out
 }
 private fun scaleRadii(o:Map<String,Double>,scale:Double):Map<String,Double> {
  val out=o.toMutableMap(); for(k in radiusKeys) out[k]?.let { out[k]=it*scale }
  out["rSizeMul"]=(out["rSizeMul"] ?: 1.0)*scale; return out
 }
 private val cache=HashMap<Pair<OrbState,Int>,Resolved>()
 /** size is one of the two tuned presets, 64 or 20 (separate designs, not a scale factor). */
 fun resolve(state:OrbState,size:Int):Resolved = synchronized(cache) { cache.getOrPut(state to size) {
  val mode=when(state) { OrbState.Listening->Mode.Wave; OrbState.Solving->Mode.Rubik; OrbState.Breathing->Mode.Ring }
  val p=presets.getValue(mode).getValue(if(size==20) 20 else 64)
  var o=base.getValue(mode)
  if(p.count!=1.0) o=scaleCounts(o,p.count)
  if(p.size!=1.0) o=scaleRadii(o,p.size)
  Resolved(mode,p.speed,o+p.extra)
 } }

 /**
  * Geometry for one instant. `t` is already multiplied by the preset speed (seconds × speed), as in the library.
  * `amp` scales the listening wave's swell (1 = the library's tuning) so the orb can follow the microphone level.
  */
 fun frame(r:Resolved,size:Double,t:Double,amp:Double=1.0):List<OrbDot> = when(r.mode) {
  Mode.Wave->wave(size,t,r.opts,amp); Mode.Rubik->rubik(size,t,r.opts); Mode.Ring->ring(size,t,r.opts)
 }

 // --- shared primitives -------------------------------------------------------------------------------------
 private fun hashD(a:Double,b:Double):Double { val h=sin(a*12.9898+b*78.233)*43758.5453; return h-floor(h) }
 private class Proj(yaw:Double,tilt:Double,val cx:Double,val cy:Double,val scale:Double) {
  val st=sin(tilt); val ct=cos(tilt); val sy=sin(yaw); val cyw=cos(yaw)
  var px=0.0; var py=0.0; var pz=0.0
  fun at(x:Double,y:Double,z:Double) { val x1=x*cyw+z*sy; val z1=-x*sy+z*cyw; val y1=y*ct-z1*st; pz=y*st+z1*ct; px=cx+x1*scale; py=cy-y1*scale }
 }
 private fun radiusScale(size:Double,pow:Double)=(size/300).pow(pow)
 private fun finalize(dots:MutableList<OrbDot>,rMin:Double):List<OrbDot> {
  val visible=ArrayList<OrbDot>(dots.size)
  for(d in dots) { if(d.a<0.02) continue; d.r=max(rMin,d.r); visible.add(d) }
  visible.sortBy { it.z }   // stable, like Array.prototype.sort
  return visible
 }
 private fun Map<String,Double>.o(k:String,d:Double)=this[k] ?: d

 // --- wave: a waveform rolls through the rings (listening) --------------------------------------------------
 private fun wave(size:Double,t:Double,o:Map<String,Double>,amp:Double):List<OrbDot> {
  val cx=size/2; val cy=size/2; val R=(size/2)*0.874
  val pt=Proj(t*0.18,0.38,cx,cy,1.0); val rs=radiusScale(size,o.o("rsPow",0.6))
  val dots=ArrayList<OrbDot>()
  val rings=o.o("rings",15.0).toInt(); val lonDensity=o.o("lonDensity",40.0)
  for(ri in 0..rings) {
   val lat=-PI/2+(ri.toDouble()/rings)*PI; val cosLat=cos(lat); val sinLat=sin(lat)
   val w=0.62*sin(t*2.1-ri*0.52)+0.38*sin(t*1.27+ri*0.83)
   // the library's swell is 0.105·w; amp > 1 lets a loud voice push it further, clamped to stay in frame
   val rr=R*min(1.12,0.88+0.105*amp*w)
   val lonCount=max(1.0,jsRound(abs(cosLat)*lonDensity)).toInt()
   for(lj in 0 until lonCount) {
    val lon=(lj.toDouble()/lonCount)*2*PI
    pt.at(cosLat*cos(lon)*rr,sinLat*rr,cosLat*sin(lon)*rr)
    val depth=(pt.pz/R+1)/2; val crest=max(0.0,w)
    dots.add(OrbDot(pt.px,pt.py,pt.pz,(o.o("rBase",0.6)+o.o("rDepth",1.7)*depth)*(1+0.4*crest)*rs,0.66-0.56*depth-0.1*crest))
   }
  }
  return finalize(dots,o.o("rMin",0.3))
 }

 // --- rubik: bands twist in quarter turns, scramble → solve (solving) ---------------------------------------
 private class Move(val axis:Int,val lo:Double,val hi:Double,val ang:Double)
 private fun makeMoves(count:Int)=List(count) { i ->
  val axis=min(2,floor(hashD(i.toDouble(),2.3)*3).toInt())
  val lo=-1.0+0.5*min(3.0,floor(hashD(i.toDouble(),5.9)*4))
  val dir=if(hashD(i.toDouble(),7.7)<0.5) 1 else -1
  Move(axis,lo,lo+0.5,dir*PI/2)
 }
 private fun rubik(size:Double,t:Double,o:Map<String,Double>):List<OrbDot> {
  val cx=size/2; val cy=size/2; val R=(size/2)*0.82
  val pt=Proj(t*0.55,0.35+0.1*sin(t*0.9),cx,cy,R); val rs=radiusScale(size,o.o("rsPow",0.6))
  val count=o.o("moveCount",14.0).toInt(); val moves=makeMoves(count)
  // solve cycle: rapid eased moves scramble, then replay in reverse so it clicks back to solved
  val slotDur=0.42; val rest=1.2; val cyc=2*count*slotDur+rest; val tc=t%cyc
  val amount=DoubleArray(count); var active=-1
  if(tc<2*count*slotDur) {
   val slot=floor(tc/slotDur).toInt(); val p=(tc-slot*slotDur)/slotDur; val cl=min(1.0,p/0.7); val ep=1-(1-cl).pow(3)
   if(slot<count) { for(i in 0 until slot) amount[i]=1.0; amount[slot]=ep; active=slot }
   else { val u=2*count-1-slot; for(i in 0 until u) amount[i]=1.0; amount[u]=1-ep; active=u }
  }
  val dots=ArrayList<OrbDot>()
  val latRings=o.o("latRings",15.0).toInt(); val lonDensity=o.o("lonDensity",40.0)
  for(li in 0..latRings) {
   val lat=-PI/2+(li.toDouble()/latRings)*PI; val cosLat=cos(lat); val sinLat=sin(lat)
   val lonCount=max(1.0,jsRound(abs(cosLat)*lonDensity)).toInt()
   for(lj in 0 until lonCount) {
    val lon=(lj.toDouble()/lonCount)*2*PI
    var x=cosLat*cos(lon); var y=sinLat; var z=cosLat*sin(lon); var inActive=false
    for(i in moves.indices) {
     if(amount[i]<=0) continue
     val mv=moves[i]; val coord=when(mv.axis) { 0->x; 1->y; else->z }
     if(coord<mv.lo || coord>=mv.hi) continue
     if(i==active) inActive=true
     val a=mv.ang*amount[i]; val ca=cos(a); val sa=sin(a)
     when(mv.axis) {
      0 -> { val y2=y*ca-z*sa; z=y*sa+z*ca; y=y2 }
      1 -> { val x2=x*ca+z*sa; z=-x*sa+z*ca; x=x2 }
      else -> { val x2=x*ca-y*sa; y=x*sa+y*ca; x=x2 }
     }
    }
    pt.at(x,y,z); val depth=(pt.pz+1)/2
    dots.add(OrbDot(pt.px,pt.py,pt.pz,(o.o("rBase",0.6)+o.o("rDepth",1.7)*depth+(if(inActive) o.o("rActive",0.3) else 0.0))*rs,
     o.o("inkFar",0.62)-o.o("inkSpan",0.54)*depth-(if(inActive) 0.14 else 0.0)))
   }
  }
  return finalize(dots,o.o("rMin",0.3))
 }

 // --- ring: a face-on circle whose radius undulates (breathing; ribbon geometry with faceOn) ----------------
 private fun fibDir(i:Int,n:Int):DoubleArray { val golden=PI*(3-sqrt(5.0)); val y=1-(2*(i+0.5))/n; val rad=sqrt(1-y*y); val a=i*golden; return doubleArrayOf(rad*cos(a),y,rad*sin(a)) }
 private fun ring(size:Double,t:Double,o:Map<String,Double>):List<OrbDot> {
  val cx=size/2; val cy=size/2; val R=(size/2)*0.78
  val spin=o.o("spin",1.0); val camTilt=0.3
  val pt=Proj(t*0.1*spin,camTilt,cx,cy,1.0); val rs=radiusScale(size,o.o("rsPow",0.6))
  val dots=ArrayList<OrbDot>()
  val ghostN=o.o("ghostN",150.0).toInt()
  for(i in 0 until ghostN) { val d=fibDir(i,ghostN); pt.at(d[0]*R,d[1]*R,d[2]*R); val depth=(pt.pz/R+1)/2; dots.add(OrbDot(pt.px,pt.py,pt.pz,0.8*rs,0.78,0.1+0.22*depth)) }
  val faceOn=o.o("faceOn",0.0)!=0.0
  val ya=t*0.24*spin; val ta=if(faceOn) -camTilt else 0.55+0.3*sin(t*0.18)*spin
  val ux=cos(ya); val uy=0.0; val uz=sin(ya)
  val vx=-uz*sin(ta); val vy=cos(ta); val vz=ux*sin(ta)
  val nx=uy*vz-uz*vy; val ny=uz*vx-ux*vz; val nz=ux*vy-uy*vx
  val wobMul=o.o("wobMul",1.0); val wobAmp=0.23*wobMul
  val baseR=if(faceOn) R/(1+0.85*wobAmp) else R
  val segs=o.o("segs",88.0).toInt()
  val lanes=max(1.0,jsRound(o.o("lanes",5.0)*o.o("bandMul",1.0))).toInt()
  for(w in 0 until lanes) {
   val laneOff=(w-(lanes-1)/2.0)*0.075
   val edge=abs(w-(lanes-1)/2.0)/max(1.0,(lanes-1)/2.0)
   for(k in 0 until segs) {
    val a=(k.toDouble()/segs)*2*PI
    val wob=(0.16*sin(a*3-t*1.7+w*0.22)+0.07*sin(a*5+t*1.1))*wobMul
    val radial=if(faceOn) 1+wob else 1.0; val off=if(faceOn) laneOff else laneOff+wob
    val x=ux*cos(a)+vx*sin(a)+nx*off; val y=uy*cos(a)+vy*sin(a)+ny*off; val z=uz*cos(a)+vz*sin(a)+nz*off
    val l=sqrt(x*x+y*y+z*z); val rr=baseR*radial
    pt.at(x/l*rr,y/l*rr,z/l*rr); val depth=(pt.pz/R+1)/2
    dots.add(OrbDot(pt.px,pt.py,pt.pz,(o.o("rBase",1.1)+o.o("rDepth",1.7)*depth)*(1-0.25*edge)*rs,0.52-0.44*depth+0.18*edge,0.4+0.6*depth))
   }
  }
  return finalize(dots,o.o("rMin",0.3))
 }

}
