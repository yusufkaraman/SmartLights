package com.smartlights.app

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material3.carousel.HorizontalMultiBrowseCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

// Scenes that ship with the app; they apply to every connected light.
data class Ready(val name:String, val icon:String, val bg:Color, val fg:Color, val bri:Int, val kelvin:Int=0, val hue:Int=0, val sat:Int=0, val wiz:Int=0)
val readyScenes=listOf(
 Ready("Film gecesi",P.sceneIcons[0],Color(0xFF3E3360),Color(0xFFEADDFF),20,hue=265,sat=70),
 Ready("Okuma",P.sceneIcons[1],Color(0xFF6B4A00),Color(0xFFFFDEA6),90,kelvin=4000),
 Ready("Uyku",P.moon,Color(0xFF1F2D3D),Color(0xFFD1E4FF),5,kelvin=2200),
 Ready("Odak",P.sun,Color(0xFF1E4A5A),Color(0xFFCDEBFF),100,kelvin=6000),
 Ready("Rahat akşam",P.bulb,Color(0xFF5A3A12),Color(0xFFFFDDB4),50,kelvin=2700),
 Ready("Romantik",P.star,Color(0xFF5A1F3A),Color(0xFFFFD9E3),40,hue=340,sat=60,wiz=2),
 Ready("Parti",P.music,Color(0xFF4A1E5C),Color(0xFFF3D9FF),100,hue=300,sat=90,wiz=4),
 Ready("Şömine",P.sunset,Color(0xFF5C2A0E),Color(0xFFFFDBC8),60,hue=25,sat=90,wiz=5))

val themeSeeds=listOf(0L,0xFFF7BD48,0xFFFF7A6B,0xFFFF8AAE,0xFFA98BF5,0xFF6FA8FF,0xFF4FB8AC,0xFF7FD67A,0xFFD9E86A)
fun seedColor(choice:Long,system:Color)=if(choice==0L) system else Color(choice)

private val userSceneColors=listOf(Color(0xFF3E3360) to Color(0xFFEADDFF),Color(0xFF6B4A00) to Color(0xFFFFDEA6),Color(0xFF1F2D3D) to Color(0xFFD1E4FF),Color(0xFF5A1F3A) to Color(0xFFFFD9E3))

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable fun Home(lights:List<Light>,favorites:Set<String>,scanning:Boolean,pending:Set<String>,scenes:List<String>,dark:Boolean,themeChoice:Long,systemSeed:Color,onRefresh:()->Unit,onOpen:(String)->Unit,onToggle:(Light,Boolean)->Unit,onAll:(Boolean)->Unit,onReady:(Ready)->Unit,onScene:(String)->Unit,onDeleteScene:(String)->Unit,onAddIp:()->Unit,onSaveScene:()->Unit,onAccount:()->Unit,onDark:(Boolean)->Unit,onTheme:(Long)->Unit,onRemove:(Light)->Unit,hiddenCount:Int,onUnhide:()->Unit,onLog:()->Unit) {
 val t=Amber
 var fabOpen by remember { mutableStateOf(false) }
 var themeSheet by remember { mutableStateOf(false) }
 var removing by remember { mutableStateOf<Light?>(null) }
 var allMode by remember { mutableIntStateOf(-1) }
 val sorted=remember(lights,favorites) { lights.distinctBy { it.id }.sortedByDescending { it.id in favorites } }
 val online=lights.count { it.online }
 val top=WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
 val pull=rememberPullToRefreshState()
 Box(Modifier.fillMaxSize().background(t.bg)) {
  PullToRefreshBox(isRefreshing=scanning,onRefresh=onRefresh,state=pull,modifier=Modifier.fillMaxSize(),indicator={ PullToRefreshDefaults.LoadingIndicator(state=pull,isRefreshing=scanning,modifier=Modifier.align(Alignment.TopCenter).padding(top=top+8.dp),containerColor=t.pc,color=t.onPc) }) {
   LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(start=16.dp,end=16.dp,top=top+28.dp,bottom=WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()+120.dp)) {
    item {
     Row(verticalAlignment=Alignment.CenterVertically) {
      Column(Modifier.weight(1f)) {
       Text("Işıkların",style=ts(40,FontWeight.SemiBold,Ink,44,-0.5f))
       Spacer(Modifier.height(8.dp))
       Row(Modifier.height(24.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        if(scanning) LoadingIndicator(Modifier.size(24.dp),color=t.primary)
        Text((if(scanning) "Ağ taranıyor · " else "")+if(lights.isEmpty()) "Henüz ışık yok" else "${lights.size} ışıktan $online'i bağlı",style=ts(15,color=InkVariant))
       }
      }
      Surface(onClick=onLog,shape=CircleShape,color=t.cont,modifier=Modifier.size(44.dp)) { Box(contentAlignment=Alignment.Center) { Icon(icon(P.info,2f),"Kayıtlar",tint=Ink,modifier=Modifier.size(22.dp)) } }
      Spacer(Modifier.width(10.dp))
      Switch(checked=dark,onCheckedChange=onDark,colors=toneSwitchColors(t),thumbContent={ Icon(icon(if(dark) P.moon else P.sun,2f),if(dark) "Koyu tema" else "Açık tema",Modifier.size(SwitchDefaults.IconSize),tint=if(dark) t.primary else t.onPrimary) })
     }
     Spacer(Modifier.height(20.dp))
     ConnectedGroup(listOf("Tümünü aç","Tümünü kapat"),allMode,t,{ allMode=it; onAll(it==0) },Modifier.fillMaxWidth(),height=56.dp,grow=1.35f,textSize=16)
     Spacer(Modifier.height(20.dp))
    }
    if(lights.isEmpty()) item {
     Surface(shape=RoundedCornerShape(28.dp),color=t.cont) { Column(Modifier.fillMaxWidth().padding(20.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
      Text("Işık bulunamadı",style=ts(20,FontWeight.Medium,Ink)); Text("Aynı Wi-Fi ağına bağlanın, aşağı çekerek yenileyin. Tapo için hesabınızı veya IP adresini + menüsünden ekleyin.",style=ts(14,color=InkVariant))
     } }
     Spacer(Modifier.height(20.dp))
    }
    if(sorted.size>1) item(key=ALL_LIGHTS) { GroupCard(sorted,t,{ onOpen(ALL_LIGHTS) },{ onAll(it) }); Spacer(Modifier.height(10.dp)) }
    items(sorted,key={it.id}) { light -> LightCard(light,light.id in favorites,light.id in pending,t,{onOpen(light.id)},{onToggle(light,it)},{removing=light}); Spacer(Modifier.height(10.dp)) }
    item {
     Spacer(Modifier.height(10.dp))
     Text("Sahneler",style=ts(22,FontWeight.Medium,Ink)); Spacer(Modifier.height(12.dp))
     val count=readyScenes.size+scenes.size
     val state=rememberCarouselState { count }
     HorizontalMultiBrowseCarousel(state=state,preferredItemWidth=190.dp,itemSpacing=8.dp,modifier=Modifier.fillMaxWidth().height(140.dp)) { i ->
      val ready=readyScenes.getOrNull(i); val userName=if(ready==null) scenes[i-readyScenes.size] else null
      val (bg,fg)=ready?.let { it.bg to it.fg } ?: userSceneColors[i % userSceneColors.size]
      var ask by remember { mutableStateOf(false) }
      Box(Modifier.fillMaxSize().maskClip(RoundedCornerShape(28.dp)).background(bg).combinedClickable(onClick={ if(ready!=null) onReady(ready) else onScene(userName!!) },onLongClick={ if(userName!=null) ask=true }).padding(16.dp)) {
       Ico(ready?.icon ?: P.bookmark,fg,modifier=Modifier.align(Alignment.TopStart))
       Text(ready?.name ?: userName!!,style=ts(18,FontWeight.SemiBold,fg),maxLines=1,overflow=TextOverflow.Clip,softWrap=false,modifier=Modifier.align(Alignment.BottomStart))
      }
      if(ask) AlertDialog(onDismissRequest={ask=false},containerColor=t.cont,titleContentColor=Ink,title={Text("\"$userName\" silinsin mi?")},confirmButton={TextButton({ask=false; onDeleteScene(userName!!)}){Text("Sil",color=t.primary)}},dismissButton={TextButton({ask=false}){Text("Vazgeç",color=Ink)}})
     }
    }
   }
  }
  if(fabOpen) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha=.45f)).clickable(MutableInteractionSource(),null) { fabOpen=false })
  Surface(onClick={themeSheet=true},modifier=Modifier.align(Alignment.BottomStart).navigationBarsPadding().padding(start=16.dp,bottom=24.dp).size(64.dp),shape=RoundedCornerShape(20.dp),color=t.high,shadowElevation=6.dp) {
   Box(contentAlignment=Alignment.Center) { Icon(icon(P.palette),"Renk teması",tint=t.primary) }
  }
  FloatingActionButtonMenu(expanded=fabOpen,modifier=Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(bottom=12.dp,end=4.dp),button={
   ToggleFloatingActionButton(checked=fabOpen,onCheckedChange={fabOpen=it},containerColor=ToggleFloatingActionButtonDefaults.containerColor(t.pc,t.primary)) {
    val p=checkedProgress
    Icon(icon(if(p>0.5f) P.close else P.plus,2f),if(fabOpen) "Menüyü kapat" else "Ekle menüsü",tint=lerp(t.onPc,t.onPrimary,p),modifier=Modifier.size(26.dp))
   }
  }) {
   (listOf(Triple(P.link,"IP ile ışık ekle",onAddIp),Triple(P.bookmark,"Sahneyi kaydet",onSaveScene),Triple(P.account,"Tapo hesabı · "+when(TapoAccount.state.auth) { TapoAccount.Auth.Ok->"bağlı"; TapoAccount.Auth.Wrong->"şifre yanlış"; TapoAccount.Auth.Locked->"kilitli"; TapoAccount.Auth.Unknown->"doğrulanmadı"; else->"girilmedi" },onAccount))+if(hiddenCount>0) listOf(Triple(P.refresh,"Gizli ışıkları geri getir ($hiddenCount)",onUnhide)) else emptyList()).forEach { (d,label,action) ->
    FloatingActionButtonMenuItem(onClick={fabOpen=false; action()},text={Text(label,style=ts(16,FontWeight.Medium))},icon={Ico(d,t.onPc,22.dp)},containerColor=t.pc,contentColor=t.onPc)
   }
  }
  if(themeSheet) ThemeSheet(dark,themeChoice,systemSeed,{themeSheet=false},onTheme)
  removing?.let { l -> AlertDialog(onDismissRequest={removing=null},containerColor=t.cont,titleContentColor=Ink,textContentColor=InkVariant,title={Text("${l.name} kaldırılsın mı?")},
   text={Text("Işık listeden ve widget'lardan kaldırılır, taramada tekrar eklenmez. Geri getirmek için + menüsündeki \"Gizli ışıkları geri getir\"i kullanın.")},
   confirmButton={TextButton({ removing=null; onRemove(l) }){Text("Kaldır",color=t.primary)}},dismissButton={TextButton({removing=null}){Text("Vazgeç",color=Ink)}}) }
 }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun ThemeSheet(dark:Boolean,current:Long,systemSeed:Color,onClose:()->Unit,onApply:(Long)->Unit) {
 var choice by remember { mutableLongStateOf(current) }
 val preview=animatedTone(toneFrom(seedColor(choice,systemSeed),dark))
 val sheet=rememberModalBottomSheetState(skipPartiallyExpanded=true)
 CompositionLocalProvider(LocalTone provides preview) {
  ModalBottomSheet(onDismissRequest=onClose,sheetState=sheet,containerColor=preview.cont,contentColor=Ink,dragHandle={ BottomSheetDefaults.DragHandle(color=Outline) }) {
   Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal=16.dp).padding(bottom=24.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
    Text("Renk teması",style=ts(22,FontWeight.SemiBold,Ink))
    Preview(preview)
    Text("Renk",style=ts(15,FontWeight.SemiBold,Ink))
    for(row in themeSeeds.chunked(5)) Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
     row.forEach { seed -> val sel=seed==choice
      Column(horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(6.dp),modifier=Modifier.width(56.dp).clip(RoundedCornerShape(16.dp)).clickable { choice=seed }) {
       Box(Modifier.size(52.dp).then(if(sel) Modifier.border(3.dp,Ink,CircleShape) else Modifier).padding(5.dp).clip(CircleShape).background(seedColor(seed,systemSeed)),contentAlignment=Alignment.Center) { if(sel) Ico(P.check,Color(0xFF1A1208),20.dp,2.4f) }
       Text(if(seed==0L) "Sistem" else "",style=ts(12,color=InkVariant),maxLines=1)
      }
     }
    }
    Text(if(choice==0L) "Sistem: renkler duvar kâğıdınızdan gelir (Material You)." else "Seçtiğiniz renkten tüm uygulama renkleri üretilir.",style=ts(13,color=InkVariant))
    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
     FilledTonalButton(onClick=onClose,modifier=Modifier.weight(1f).height(48.dp),colors=ButtonDefaults.filledTonalButtonColors(containerColor=preview.tonal,contentColor=preview.onTonal)) { Text("Vazgeç",style=ts(14,FontWeight.SemiBold)) }
     Button(onClick={ onApply(choice); onClose() },modifier=Modifier.weight(1f).height(48.dp),colors=ButtonDefaults.buttonColors(containerColor=preview.primary,contentColor=preview.onPrimary)) { Text("Uygula",style=ts(14,FontWeight.SemiBold)) }
    }
   }
  }
 }
}

// A small live copy of the home screen in the theme being considered.
@Composable private fun Preview(t:Tone) {
 Surface(shape=RoundedCornerShape(28.dp),color=t.bg,modifier=Modifier.fillMaxWidth()) {
  Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
   Row(verticalAlignment=Alignment.CenterVertically) { Text("Işıkların",style=ts(26,FontWeight.SemiBold,t.ink),modifier=Modifier.weight(1f)); Box(Modifier.size(44.dp,26.dp).clip(CircleShape).background(t.primary)) }
   ConnectedGroup(listOf("Tümünü aç","Tümünü kapat"),0,t,{},Modifier.fillMaxWidth(),height=40.dp,grow=1.35f,textSize=13)
   Surface(shape=RoundedCornerShape(22.dp),color=t.cont) {
    Row(Modifier.fillMaxWidth().padding(12.dp),verticalAlignment=Alignment.CenterVertically) {
     Box(Modifier.size(40.dp),contentAlignment=Alignment.Center) { MorphBlob(true,t.primary,Modifier.fillMaxSize()); Ico(P.bulb,t.onPrimary,20.dp,1.8f) }
     Spacer(Modifier.width(12.dp))
     Column(Modifier.weight(1f)) { Text("Salon",style=ts(16,FontWeight.Medium,t.ink)); Text("WIZ · %70",style=ts(12,color=t.inkVar)) }
     Box(Modifier.size(44.dp,26.dp).clip(CircleShape).background(t.primary))
    }
   }
  }
 }
}

// Opens one detail screen whose changes go to every light at once.
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable private fun GroupCard(lights:List<Light>,t:Tone,onOpen:()->Unit,onToggle:(Boolean)->Unit) {
 val g=groupLight(lights)
 val on=g.on && g.online
 val lc=Catalog.lightColor(g)
 val ct=if(on) toneFrom(lc,t.dark) else t
 val bg by animateColorAsState(if(on) ct.cont else t.cont,ColorTween,label="gcard")
 val ink by animateColorAsState(ct.ink,ColorTween,label="gink"); val inkVar by animateColorAsState(ct.inkVar,ColorTween,label="ginkVar")
 val online=lights.count { it.online }
 Surface(onClick=onOpen,shape=RoundedCornerShape(28.dp),color=bg,modifier=Modifier.fillMaxWidth()) {
  Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
   Row(verticalAlignment=Alignment.CenterVertically) {
    Box(Modifier.size(52.dp)) {
     lights.take(2).forEachIndexed { i,l -> val lit=l.on && l.online
      MorphBlob(lit,if(lit) Catalog.lightColor(l) else if(l.online) t.high else t.bg,Modifier.size(38.dp).align(if(i==0) Alignment.TopStart else Alignment.BottomEnd),offAmp=if(l.online) 0.05f else 0f)
     }
    }
    Spacer(Modifier.width(14.dp))
    Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(2.dp)) {
     Text("Tüm ışıklar",style=ts(20,FontWeight.Medium,ink),maxLines=1)
     Text("$online/${lights.size} bağlı · "+if(on) "%${g.brightness}" else "Kapalı",style=ts(14,color=inkVar))
    }
    Switch(checked=on,onCheckedChange=onToggle,enabled=g.online,colors=toneSwitchColors(ct))
   }
  }
 }
}

// An on light's card takes the light's own color, and the tinted band shows its brightness.
@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalFoundationApi::class)
@Composable private fun LightCard(light:Light,favorite:Boolean,pending:Boolean,t:Tone,onOpen:()->Unit,onToggle:(Boolean)->Unit,onRemove:()->Unit) {
 val on=light.on && light.online
 val lc=Catalog.lightColor(light)
 val ct=if(on) toneFrom(lc,t.dark) else t
 val bg by animateColorAsState(if(on) ct.cont else t.cont,ColorTween,label="card")
 val band by animateColorAsState(if(on) lc.copy(alpha=if(t.dark) .30f else .45f) else lc.copy(alpha=0f),ColorTween,label="band")
 val level by animateFloatAsState(if(on) light.brightness.coerceIn(1,100)/100f else 0f,Fast,label="level")
 val ink by animateColorAsState(ct.ink,ColorTween,label="ink"); val inkVar by animateColorAsState(ct.inkVar,ColorTween,label="inkVar")
 Surface(shape=RoundedCornerShape(28.dp),color=bg,modifier=Modifier.fillMaxWidth()) {
  Column(Modifier.drawBehind { drawRect(band,size=Size(size.width*level,size.height)) }.combinedClickable(onClick=onOpen,onLongClick=onRemove).padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
   Row(verticalAlignment=Alignment.CenterVertically) {
    Box(Modifier.size(52.dp),contentAlignment=Alignment.Center) {
     MorphBlob(on,if(on) lc else if(light.online) t.high else t.bg,Modifier.fillMaxSize(),offAmp=if(light.online) 0.05f else 0f)
     Ico(P.bulb,if(on) Color(0xFF1A1208) else if(light.online) InkVariant else Outline,24.dp,1.8f)
    }
    Spacer(Modifier.width(14.dp))
    Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(2.dp)) {
     Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)) { Text(light.name,style=ts(20,FontWeight.Medium,ink),maxLines=1,overflow=TextOverflow.Ellipsis,modifier=Modifier.weight(1f,false)); if(favorite) Ico(P.star,ct.primary,16.dp,fill=true) }
     Text(light.brand.uppercase()+" · "+when { !light.online->"Bağlantı bekleniyor"; on->"%${light.brightness}"; else->"Kapalı" },style=ts(14,color=inkVar))
    }
    Switch(checked=on,onCheckedChange=onToggle,enabled=light.online,colors=toneSwitchColors(ct))
   }
   if(pending) LinearWavyProgressIndicator(Modifier.fillMaxWidth(),color=ct.primary,trackColor=Color.Transparent)
  }
 }
}
