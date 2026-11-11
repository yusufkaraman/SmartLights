package com.smartlights.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// A dotted thought-orb (thinking-orbs port, see OrbEngine) drawn in one tint. The library's ink is grayscale with
// near dots darkest on light paper and brightest on dark paper; drawing the tint at alpha (1 - ink) gives the same
// depth reading on both themes, in the app's own color.
//
// `level` (0..1, e.g. microphone loudness) speeds the listening wave up and swells it; it is smoothed here so the
// orb breathes with the voice instead of flickering with every audio buffer.
@Composable fun Orb(state:OrbState,tint:Color,modifier:Modifier=Modifier,size:Dp=28.dp,level:()->Float={ 0f }) {
 val preset=if(size<24.dp) 20 else 64
 val resolved=remember(state,preset) { OrbEngine.resolve(state,preset) }
 var dots by remember { mutableStateOf<List<OrbDot>>(emptyList()) }
 LaunchedEffect(resolved) {
  var t=0.0; var last=0L; var smooth=0.0
  while(true) withFrameNanos { now ->
   val dt=if(last==0L) 0.0 else (now-last)/1e9; last=now
   smooth+=(level().toDouble().coerceIn(0.0,1.0)-smooth)*(1-Math.exp(-dt*10))
   t+=dt*resolved.speed*(1+1.2*smooth)
   dots=OrbEngine.frame(resolved,preset.toDouble(),t,amp=1+2.2*smooth)
  }
 }
 val label=when(state) { OrbState.Listening->"Dinliyor"; OrbState.Solving->"Düşünüyor"; OrbState.Breathing->"Konuşuyor" }
 Canvas(modifier.size(size).semantics { contentDescription=label }) {
  val k=this.size.minDimension/preset
  for(d in dots) {
   val alpha=(d.a*(1-d.white.coerceIn(0.0,1.0))).toFloat().coerceIn(0f,1f)
   drawCircle(tint.copy(alpha=alpha*tint.alpha),radius=(d.r*k).toFloat(),center=Offset((d.x*k).toFloat(),(d.y*k).toFloat()))
  }
 }
}
