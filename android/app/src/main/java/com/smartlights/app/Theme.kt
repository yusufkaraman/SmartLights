package com.smartlights.app

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// A full set of colors generated from one seed color, in the shape of the design's M3 tokens.
data class Tone(val bg:Color, val cont:Color, val high:Color, val primary:Color, val onPrimary:Color, val tonal:Color, val onTonal:Color, val ink:Color, val inkVar:Color, val outline:Color, val pc:Color, val onPc:Color, val good:Color, val dark:Boolean)

private fun hsl(h:Float,s:Float,l:Float)=Color(androidx.core.graphics.ColorUtils.HSLToColor(floatArrayOf(h,s.coerceIn(0f,1f),l.coerceIn(0f,1f))))
fun toneFrom(seed:Color,dark:Boolean):Tone {
 val a=FloatArray(3); androidx.core.graphics.ColorUtils.colorToHSL(seed.toArgb(),a); val h=a[0]; val s=a[1]
 return if(dark) Tone(hsl(h,minOf(s,.32f),.07f),hsl(h,minOf(s,.22f),.14f),hsl(h,minOf(s,.18f),.19f),hsl(h,s.coerceIn(.55f,.95f),.72f),hsl(h,.9f,.15f),hsl(h,minOf(s,.32f),.26f),hsl(h,.65f,.87f),hsl(h,.18f,.91f),hsl(h,.14f,.77f),hsl(h,.1f,.56f),hsl(h,.85f,.28f),hsl(h,.9f,.88f),Color(0xFFC5EFB8),true)
 else Tone(hsl(h,minOf(s,.45f),.975f),hsl(h,minOf(s,.4f),.93f),hsl(h,minOf(s,.32f),.88f),hsl(h,s.coerceIn(.55f,.9f),.38f),Color.White,hsl(h,minOf(s,.5f),.84f),hsl(h,.55f,.18f),hsl(h,.2f,.11f),hsl(h,.12f,.33f),hsl(h,.1f,.52f),hsl(h,.8f,.86f),hsl(h,.8f,.16f),Color(0xFF2E6B1F),false)
}

val DefaultSeed=Color(0xFFF7BD48)
val LocalTone=compositionLocalOf { toneFrom(DefaultSeed,true) }
val Amber:Tone @Composable @ReadOnlyComposable get()=LocalTone.current
val PrimaryContainer:Color @Composable @ReadOnlyComposable get()=LocalTone.current.pc
val OnPrimaryContainer:Color @Composable @ReadOnlyComposable get()=LocalTone.current.onPc
val Ink:Color @Composable @ReadOnlyComposable get()=LocalTone.current.ink
val InkVariant:Color @Composable @ReadOnlyComposable get()=LocalTone.current.inkVar
val Outline:Color @Composable @ReadOnlyComposable get()=LocalTone.current.outline
val Good:Color @Composable @ReadOnlyComposable get()=LocalTone.current.good

val Fast=spring<Float>(dampingRatio=0.72f,stiffness=700f)
val FastDp=spring<Dp>(dampingRatio=0.72f,stiffness=700f)
val ColorTween=tween<Color>(220,easing=FastOutSlowInEasing)

@Composable fun animatedTone(t:Tone):Tone {
 @Composable fun a(c:Color)=animateColorAsState(c,ColorTween,label="tone").value
 return Tone(a(t.bg),a(t.cont),a(t.high),a(t.primary),a(t.onPrimary),a(t.tonal),a(t.onTonal),a(t.ink),a(t.inkVar),a(t.outline),a(t.pc),a(t.onPc),t.good,t.dark)
}

fun ts(size:Int,weight:FontWeight=FontWeight.Normal,color:Color=Color.Unspecified,lh:Int=0,spacing:Float=0f)=TextStyle(fontSize=size.sp,fontWeight=weight,color=color,lineHeight=if(lh>0) lh.sp else androidx.compose.ui.unit.TextUnit.Unspecified,letterSpacing=spacing.sp)

private val iconCache=HashMap<String,ImageVector>()
fun icon(d:String,stroke:Float=1.9f,fill:Boolean=false):ImageVector = iconCache.getOrPut("$d|$stroke|$fill") {
 ImageVector.Builder(defaultWidth=24.dp,defaultHeight=24.dp,viewportWidth=24f,viewportHeight=24f).addPath(pathData=PathParser().parsePathString(d).toNodes(),fill=if(fill) SolidColor(Color.Black) else null,stroke=SolidColor(Color.Black),strokeLineWidth=stroke,strokeLineCap=StrokeCap.Round,strokeLineJoin=StrokeJoin.Round).build()
}

object P {
 const val back="M19 12H5 M11 6l-6 6 6 6"
 const val bulb="M9 18h6 M10 21h4 M12 3a6 6 0 0 0-3.5 10.9c.6.4 1 1.1 1 1.8V16h5v-.3c0-.7.4-1.4 1-1.8A6 6 0 0 0 12 3z"
 const val home="M3 11l9-7 9 7v9a1 1 0 0 1-1 1h-5v-6h-6v6H4a1 1 0 0 1-1-1z"
 const val refresh="M20 11a8 8 0 1 0-2.3 5.7 M20 4v7h-7"
 const val tune="M4 6h9 M17 6h3 M15 4v4 M4 12h3 M11 12h9 M9 10v4 M4 18h11 M19 18h1 M17 16v4"
 const val plus="M12 5v14 M5 12h14"
 const val minus="M5 12h14"
 const val close="M6 6l12 12 M18 6L6 18"
 const val link="M10 14a4 4 0 0 0 5.66 0l3-3a4 4 0 0 0-5.66-5.66l-1 1 M14 10a4 4 0 0 0-5.66 0l-3 3a4 4 0 0 0 5.66 5.66l1-1"
 const val bookmark="M6 3h12v18l-6-4-6 4z"
 const val account="M12 12a4 4 0 1 0 0-8 4 4 0 0 0 0 8z M4 21a8 8 0 0 1 16 0"
 const val star="M12 3l2.8 5.7 6.2.9-4.5 4.4 1.1 6.2L12 17.3 6.4 20.2l1.1-6.2L3 9.6l6.2-.9z"
 const val signal="M5 18v-2 M10 18v-5 M15 18v-8 M20 18V7"
 const val thermo="M14 14.8V5a2 2 0 0 0-4 0v9.8a4 4 0 1 0 4 0z"
 const val palette="M12 3a9 9 0 1 0 0 18c1 0 1.6-.8 1.6-1.7 0-.9-1-1.5-1-2.4 0-.9.7-1.6 1.6-1.6H16a5 5 0 0 0 5-5c0-4.1-4-7.3-9-7.3z"
 const val sparkle="M12 3l2 6 6 2-6 2-2 6-2-6-6-2 6-2z"
 const val music="M9 18V5l11-2v13 M9 18a3 3 0 1 1-6 0 3 3 0 0 1 6 0z M20 16a3 3 0 1 1-6 0 3 3 0 0 1 6 0z"
 const val timer="M12 8v5l3 2 M9 2h6 M12 21a8 8 0 1 0 0-16 8 8 0 0 0 0 16z"
 const val sunset="M3 18h18 M7 18a5 5 0 0 1 10 0 M12 6v4 M4.9 11.9l1.4 1.4 M19.1 11.9l-1.4 1.4"
 const val sunrise="M3 18h18 M7 18a5 5 0 0 1 10 0 M12 10V4 M9 7l3-3 3 3"
 const val sun="M12 8a4 4 0 1 0 0 8 4 4 0 0 0 0-8z M12 2v2 M12 20v2 M2 12h2 M20 12h2"
 const val moon="M20 14.5A8 8 0 1 1 9.5 4a6.5 6.5 0 0 0 10.5 10.5z"
 const val chevron="M9 6l6 6-6 6"
 const val mic="M12 3a3 3 0 0 0-3 3v6a3 3 0 0 0 6 0V6a3 3 0 0 0-3-3z M5 11a7 7 0 0 0 14 0 M12 18v3"
 const val pencil="M4 20h4L19 9l-4-4L4 16z M14 6l4 4"
 const val check="M5 12l5 5 9-10"
 const val photo="M4 5h16v14H4z M4 15l4-4 4 4 3-3 5 5"
 val sceneIcons=listOf("M4 4h16v16H4z M8 4v16 M16 4v16 M4 9h4 M4 15h4 M16 9h4 M16 15h4","M4 5a2 2 0 0 1 2-2h13v16H6a2 2 0 0 0-2 2V5z M19 19v2H6",moon,sparkle)
}

@Composable fun Ico(d:String,tint:Color,size:Dp=24.dp,stroke:Float=1.9f,fill:Boolean=false,modifier:Modifier=Modifier) = Icon(icon(d,stroke,fill),null,modifier.size(size),tint=tint)

// Blob between two MaterialShapes-like silhouettes: progress 1 = sunny (8 lobes), 0 = cookie (9 lobes).
fun blobPath(path:Path,cx:Float,cy:Float,r:Float,sunny:Float,amp:Float,lobesOn:Int=8,lobesOff:Int=9,points:Int=96) {
 path.reset()
 for(i in 0 until points) {
  val t=(2*PI*i/points).toFloat()
  val rr=r*(1+amp*(sunny*cos(lobesOn*t)+(1-sunny)*cos(lobesOff*t)))
  val x=cx+rr*sin(t); val y=cy-rr*cos(t)
  if(i==0) path.moveTo(x,y) else path.lineTo(x,y)
 }
 path.close()
}

@Composable fun MorphBlob(on:Boolean,color:Color,modifier:Modifier,radius:Float=0.44f,amp:Float=0.09f,offAmp:Float=0.05f,lobesOn:Int=8,lobesOff:Int=9,alpha:Float=1f) {
 val p by animateFloatAsState(if(on) 1f else 0f,Fast,label="morph")
 val a by animateFloatAsState(if(on) amp else offAmp,Fast,label="amp")
 val r by animateFloatAsState(radius,Fast,label="radius")
 val c by animateColorAsState(color,ColorTween,label="blob")
 val path=remember { Path() }
 Canvas(modifier) { blobPath(path,size.width/2,size.height/2,size.minDimension*r,p,a,lobesOn,lobesOff); drawPath(path,c,alpha) }
}

@Composable fun Glow(c1:Color,c2:Color,selected:Boolean,ring:Color,gap:Color,modifier:Modifier) {
 val sel by animateFloatAsState(if(selected) 1f else 0f,Fast,label="glow")
 Canvas(modifier) {
  val r=size.minDimension/2
  if(sel>0.01f) { drawCircle(ring,r,alpha=sel); drawCircle(gap,r-2.dp.toPx()*sel) }
  val inner=r-5.dp.toPx()*sel
  drawCircle(Brush.radialGradient(listOf(c1.copy(alpha=.45f),Color.Transparent),center,r*1.0f),r)
  drawCircle(Brush.radialGradient(listOf(c1,c1.copy(alpha=0f)),Offset(size.width*.36f,size.height*.36f),inner*1.28f),inner)
  drawCircle(Brush.radialGradient(listOf(c2,c2.copy(alpha=0f)),Offset(size.width*.66f,size.height*.66f),inner*1.28f),inner)
 }
}

// Connected M3 Expressive button group: the selected item turns fully round and can grow.
@Composable fun ConnectedGroup(labels:List<String>,selected:Int,tone:Tone,onPick:(Int)->Unit,modifier:Modifier=Modifier,height:Dp=48.dp,grow:Float=1f,textSize:Int=14,enabled:Boolean=true) {
 Row(modifier.height(height),horizontalArrangement=Arrangement.spacedBy(2.dp)) {
  labels.forEachIndexed { i,label ->
   val on=i==selected
   val full=height/2; val inner=if(height>50.dp) 8.dp else 6.dp
   val start by animateDpAsState(if(on || i==0) full else inner,FastDp,label="s")
   val end by animateDpAsState(if(on || i==labels.lastIndex) full else inner,FastDp,label="e")
   val w by animateFloatAsState(if(on) grow else 1f,Fast,label="w")
   val bg by animateColorAsState(if(on) tone.primary else tone.tonal,ColorTween,label="bg")
   val fg by animateColorAsState(if(on) tone.onPrimary else tone.onTonal,ColorTween,label="fg")
   Surface(onClick={onPick(i)},enabled=enabled,modifier=Modifier.weight(w).fillMaxHeight(),shape=RoundedCornerShape(topStart=start,bottomStart=start,topEnd=end,bottomEnd=end),color=bg,contentColor=fg) {
    Box(contentAlignment=Alignment.Center) { Text(label,style=ts(textSize,FontWeight.SemiBold,fg),maxLines=1) }
   }
  }
 }
}

@Composable fun toneSwitchColors(t:Tone)=SwitchDefaults.colors(checkedTrackColor=t.primary,checkedThumbColor=t.onPrimary,checkedBorderColor=t.primary,uncheckedTrackColor=t.high,uncheckedThumbColor=Outline,uncheckedBorderColor=Outline)
