package com.smartlights.app

import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

// The voice pop-up: tapping the mic raises a large orb card at the bottom centre over a dimmed, blurred screen
// (the blur itself is applied to the content behind, in SmartApp). The orb walks through the session — listening,
// thinking, answering — and the card shrinks away once the reply is done. Tapping outside cancels.
@Composable fun VoiceOverlay(visible:Boolean,state:OrbState,level:()->Float,heard:String,status:String,listening:Boolean,t:Tone,onFinish:()->Unit,onDismiss:()->Unit) {
 Box(Modifier.fillMaxSize()) {
  AnimatedVisibility(visible,enter=fadeIn(tween(220)),exit=fadeOut(tween(320))) {
   Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha=if(t.dark) .38f else .22f)).clickable(interactionSource=remember { MutableInteractionSource() },indication=null,onClick=onDismiss))
  }
  AnimatedVisibility(visible,modifier=Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(start=20.dp,end=20.dp,bottom=28.dp),
   enter=scaleIn(spring(dampingRatio=Spring.DampingRatioMediumBouncy,stiffness=Spring.StiffnessMediumLow),initialScale=.55f,transformOrigin=androidx.compose.ui.graphics.TransformOrigin(.5f,1f))+fadeIn(tween(180))+slideInVertically(spring(stiffness=Spring.StiffnessMediumLow)) { it/4 },
   exit=scaleOut(tween(300),targetScale=.6f,transformOrigin=androidx.compose.ui.graphics.TransformOrigin(.5f,1f))+fadeOut(tween(260))+slideOutVertically(tween(300)) { it/5 }) {
   Surface(shape=RoundedCornerShape(44.dp),color=t.cont,shadowElevation=12.dp,modifier=Modifier.widthIn(max=420.dp).fillMaxWidth().clickable(interactionSource=remember { MutableInteractionSource() },indication=null) {}) {
    Column(Modifier.padding(top=28.dp,bottom=24.dp,start=24.dp,end=24.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(14.dp)) {
     Crossfade(state,animationSpec=tween(380),label="orb") { s -> Orb(s,t.primary,size=196.dp,level=level) }
     AnimatedContent(heard.ifBlank { if(listening) "Dinliyorum…" else "" },transitionSpec={ fadeIn(tween(120)) togetherWith fadeOut(tween(90)) },label="heard") { h ->
      Text(h,style=ts(20,FontWeight.Medium,if(heard.isBlank()) InkVariant else Ink),textAlign=TextAlign.Center,maxLines=3,overflow=TextOverflow.Ellipsis,modifier=Modifier.fillMaxWidth())
     }
     AnimatedContent(status,transitionSpec={ fadeIn(tween(160)) togetherWith fadeOut(tween(120)) },label="status") { s ->
      Text(s,style=ts(14,color=InkVariant),textAlign=TextAlign.Center,maxLines=2,overflow=TextOverflow.Ellipsis,modifier=Modifier.fillMaxWidth())
     }
     AnimatedVisibility(listening) {
      Surface(onClick=onFinish,shape=RoundedCornerShape(20.dp),color=t.primary,modifier=Modifier.height(48.dp)) {
       Row(Modifier.padding(horizontal=22.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) { Ico(P.check,t.onPrimary,20.dp,2.2f); Text("Bitti",style=ts(16,FontWeight.SemiBold,t.onPrimary)) }
      }
     }
    }
   }
  }
 }
}
