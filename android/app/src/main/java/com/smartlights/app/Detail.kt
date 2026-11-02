package com.smartlights.app

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.roundToInt

data class Func(val name:String, val wiz:Int, val kelvin:Int, val hue:Int, val sat:Int, val bri:Int)
data class Swatch(val name:String, val color:Color, val tone:Int)
data class Dyn(val name:String, val wiz:Int, val c1:Color, val c2:Color, val tone:Int)

object Catalog {
 val whites=listOf("Çok sıcak" to 2200,"Sıcak" to 2700,"Gün ışığı" to 4200,"Soğuk" to 6500)
 val funcs=listOf(Func("Gece ışığı",14,2500,-1,0,5),Func("Rahat",6,2700,-1,0,60),Func("Odak",15,5000,-1,0,100),Func("Rahatla",16,-1,280,30,50),Func("TV",18,4000,-1,0,30),Func("Bitki",19,-1,300,60,80))
 val colors=listOf(Swatch("Amber",Color(0xFFFFB95C),0),Swatch("Turuncu",Color(0xFFFF9A4D),0),Swatch("Mercan",Color(0xFFFF7A6B),3),Swatch("Kırmızı",Color(0xFFFF5A5A),3),Swatch("Gül",Color(0xFFFF8AAE),3),Swatch("Mor",Color(0xFFA98BF5),1),Swatch("Lavanta",Color(0xFFCDB8FF),1),Swatch("Mavi",Color(0xFF6FA8FF),2),Swatch("Deniz",Color(0xFF4FB8AC),2),Swatch("Yeşil",Color(0xFF7FD67A),2),Swatch("Limon",Color(0xFFD9E86A),0),Swatch("Beyaz",Color(0xFFFFF4E5),0))
 val dyns=listOf(Dyn("Şömine",5,Color(0xFFFF7A2F),Color(0xFFFFC24D),0),Dyn("Sonbahar",22,Color(0xFFE0582B),Color(0xFFF2B33D),0),Dyn("Gün batımı",3,Color(0xFFFF6B4A),Color(0xFF7A4BC2),3),Dyn("Parti",4,Color(0xFFFF4FA3),Color(0xFF4F8BFF),3),Dyn("Okyanus",1,Color(0xFF2F7BFF),Color(0xFF38D1C9),2),Dyn("Orman",7,Color(0xFF3FAF5A),Color(0xFFD4E157),2),Dyn("Mum ışığı",29,Color(0xFFFFB347),Color(0xFFFFE0A3),0),Dyn("Romantik",2,Color(0xFFFF5C8A),Color(0xFF9B6BFF),3),Dyn("Bahar",20,Color(0xFF9BE07A),Color(0xFFFFB3D1),2),Dyn("Yaz",21,Color(0xFFFFD54F),Color(0xFFFF8A50),0),Dyn("Noel",27,Color(0xFFE53935),Color(0xFF43A047),3),Dyn("Nabız",31,Color(0xFFFFF1C9),Color(0xFFFFD54F),0))
 fun kelvinColor(k:Int)=when { k<2600->Color(0xFFFFA94D); k<3200->Color(0xFFFFC27A); k<4500->Color(0xFFFFE9C9); k<5500->Color(0xFFFFF6EA); else->Color(0xFFE3ECFF) }
 fun hueSat(c:Color):Pair<Int,Int> { val hsv=FloatArray(3); android.graphics.Color.colorToHSV(c.toArgb(),hsv); return hsv[0].roundToInt() to (hsv[1]*100).roundToInt().coerceIn(1,100) }
 fun lightColor(l:Light):Color {
  dyns.firstOrNull { it.wiz==l.scene && l.scene>0 }?.let { return it.c1 }
  funcs.firstOrNull { it.wiz==l.scene && l.scene>0 }?.let { f -> return if(f.kelvin>0) kelvinColor(f.kelvin) else colorOf(f.hue,f.sat) }
  if(l.scene>0) return kelvinColor(2700)
  return if(l.colorMode) colorOf(l.hue,l.saturation) else kelvinColor(l.kelvin)
 }
 fun colorOf(hue:Int,sat:Int)=Color(android.graphics.Color.HSVToColor(floatArrayOf(hue.toFloat(),sat/100f,1f)))
}

// What the light is showing right now, so the right chip, theme and hero color are selected.
sealed class Pick { data class White(val k:Int):Pick(); data class F(val i:Int):Pick(); data class C(val color:Color, val tone:Int):Pick(); data class D(val i:Int):Pick() }

private fun initialPick(l:Light):Pick {
 Catalog.dyns.indexOfFirst { it.wiz==l.scene && l.scene>0 }.takeIf { it>=0 }?.let { return Pick.D(it) }
 Catalog.funcs.indexOfFirst { it.wiz==l.scene && l.scene>0 }.takeIf { it>=0 }?.let { return Pick.F(it) }
 if(l.colorMode) { val c=Catalog.colorOf(l.hue,l.saturation); val near=Catalog.colors.minByOrNull { val (h,_)=Catalog.hueSat(it.color); minOf(abs(h-l.hue),360-abs(h-l.hue)) }; return Pick.C(c,near?.tone ?: 0) }
 return Pick.White(l.kelvin)
}

private enum class Sheet(val title:String) { White("Beyaz ışık"), Color("Renk"), Dyn("Dinamik sahneler"), Music("Müzik senkronu"), Timer("Zamanlayıcı"), Auto("Otomasyon"), Energy("Enerji"), Conn("Bağlantı teşhisi") }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalFoundationApi::class)
@Composable fun Detail(light:Light,favorite:Boolean,store:Store,cmd:(String,Int)->Unit,onBack:()->Unit,onFavorite:(Boolean)->Unit,onRefresh:()->Unit,onRename:(String)->Unit) {
 val context=LocalContext.current
 var pick by remember(light.id) { mutableStateOf(initialPick(light)) }
 var level by remember(light.id) { mutableIntStateOf(light.brightness.coerceIn(1,100)) }
 LaunchedEffect(light.brightness) { level=light.brightness.coerceIn(1,100) }
 var sheet by remember { mutableStateOf<Sheet?>(null) }
 var usage by remember(light.id) { mutableStateOf<Usage?>(null) }
 LaunchedEffect(light.id) { if(light.brand=="tapo") usage=withContext(Dispatchers.IO) { try { Network.usage(context,light) } catch (_:Exception) { null } } }
 val on=light.on && light.online
 // The whole screen, sheets included, takes its colors from what the light is showing.
 val heroColor=when(val p=pick) { is Pick.White->Catalog.kelvinColor(p.k); is Pick.C->p.color; is Pick.D->Catalog.dyns[p.i].c1; is Pick.F->Catalog.funcs[p.i].let { f -> if(f.kelvin>0) Catalog.kelvinColor(f.kelvin) else Catalog.colorOf(f.hue,f.sat) } }
 val t=animatedTone(toneFrom(heroColor,LocalTone.current.dark))
 var renaming by remember { mutableStateOf(false) }
 val statusName=when(val p=pick) { is Pick.White->"${p.k} K"; is Pick.F->Catalog.funcs[p.i].name; is Pick.C->Catalog.colors.firstOrNull { it.color==p.color }?.name ?: "Renk"; is Pick.D->Catalog.dyns[p.i].name }
 fun setLevel(v:Int) { level=v.coerceIn(1,100); cmd("brightness",level) }
 val actions=PickActions(light,cmd) { pick=it }

 CompositionLocalProvider(LocalTone provides t) {
 Box(Modifier.fillMaxSize().background(t.bg)) {
  Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().navigationBarsPadding().padding(horizontal=16.dp).padding(top=20.dp,bottom=24.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
   Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
    Surface(onClick=onBack,shape=CircleShape,color=t.cont,modifier=Modifier.size(48.dp)) { Box(contentAlignment=Alignment.Center) { Icon(icon(P.back,2f),"Geri",tint=Ink) } }
    Column(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).combinedClickable(onClick={renaming=true})) { Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)) { Text(light.name,style=ts(24,FontWeight.SemiBold,Ink),maxLines=1,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis,modifier=Modifier.weight(1f,false)); Ico(P.pencil,InkVariant,16.dp) }; Text("${if(light.brand=="wiz") "WiZ" else "Tapo"} · ${if(!light.online) "Bağlantı bekleniyor" else if(on) statusName else "Kapalı"}",style=ts(13,color=InkVariant),maxLines=1) }
    val q=quality(light.rssi)
    Surface(onClick={sheet=Sheet.Conn},shape=RoundedCornerShape(20.dp),color=t.cont,modifier=Modifier.height(40.dp)) { Row(Modifier.padding(horizontal=12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)) { Ico(P.signal,if(light.online) Good else Outline,18.dp,2.2f); Text(if(light.online) q else "Yok",style=ts(13,FontWeight.SemiBold,if(light.online) Good else Outline)) } }
    val favR by animateDpAsState(if(favorite) 16.dp else 24.dp,FastDp,label="fav")
    Surface(onClick={onFavorite(!favorite)},shape=RoundedCornerShape(favR),color=t.cont,modifier=Modifier.size(48.dp)) { Box(contentAlignment=Alignment.Center) { Icon(icon(P.star,1.9f,favorite),"Favori",tint=t.primary,modifier=Modifier.size(22.dp)) } }
   }

   Hero(on,level,heroColor,t,light.online) { cmd("power",if(on) 0 else 1) }

   Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
    Surface(onClick={setLevel(level-10)},shape=RoundedCornerShape(16.dp),color=t.tonal,modifier=Modifier.size(52.dp)) { Box(contentAlignment=Alignment.Center) { Icon(icon(P.minus,2.2f),"Parlaklığı azalt",tint=t.onTonal) } }
    var width by remember { mutableIntStateOf(1) }
    val shown by animateFloatAsState(level/100f,Fast,label="level")
    Box(Modifier.weight(1f).height(44.dp).onSizeChanged { width=it.width }.pointerInput(light.id) { detectTapGestures { setLevel((it.x/width*100).roundToInt()) } }.pointerInput(light.id) { detectHorizontalDragGestures { change,_ -> change.consume(); setLevel((change.position.x/width*100).roundToInt()) } },contentAlignment=Alignment.Center) {
     LinearWavyProgressIndicator(progress={shown},modifier=Modifier.fillMaxWidth(),color=t.primary,trackColor=t.tonal)
    }
    Surface(onClick={setLevel(level+10)},shape=CircleShape,color=t.primary,modifier=Modifier.size(52.dp)) { Box(contentAlignment=Alignment.Center) { Icon(icon(P.plus,2.2f),"Parlaklığı artır",tint=t.onPrimary) } }
   }

   val timerEnd=store.long("timer_${light.id}")
   val minutesLeft=((timerEnd-System.currentTimeMillis())/60000).toInt()
   val autos=listOf(store.flag("circadian")).count { it }
   val tiles=listOf(
    Triple(Sheet.White,P.thermo,if(pick is Pick.White || pick is Pick.F) statusName else "${Catalog.whites.size+store.presets().size} ton"),
    Triple(Sheet.Color,P.palette,if(pick is Pick.C) statusName else "12 renk"),
    Triple(Sheet.Dyn,P.sparkle,if(pick is Pick.D) statusName else "12 sahne"),
    Triple(Sheet.Music,P.music,"Yakında"),
    Triple(Sheet.Timer,P.timer,if(timerEnd>System.currentTimeMillis()) "${minutesLeft+1} dk kaldı" else "Kapalı"),
    Triple(Sheet.Auto,P.sunset,"$autos aktif"))
   val corners=listOf(RoundedCornerShape(28.dp,12.dp,12.dp,12.dp),RoundedCornerShape(12.dp),RoundedCornerShape(12.dp,28.dp,12.dp,12.dp),RoundedCornerShape(12.dp,12.dp,12.dp,28.dp),RoundedCornerShape(12.dp),RoundedCornerShape(12.dp,12.dp,28.dp,12.dp))
   Spacer(Modifier.height(2.dp))
   for(row in 0..1) Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
    for(col in 0..2) { val i=row*3+col; val (s,d,sub)=tiles[i]
     Surface(onClick={sheet=s},shape=corners[i],color=t.cont,modifier=Modifier.weight(1f).height(104.dp)) {
      Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.SpaceBetween) {
       Box(Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(t.tonal),contentAlignment=Alignment.Center) { Ico(d,t.primary,20.dp) }
       Column { Text(if(s==Sheet.Timer) "Zamanlayıcı" else if(s==Sheet.Auto) "Otomasyon" else if(s==Sheet.Dyn) "Dinamik" else if(s==Sheet.White) "Beyaz" else s.title.substringBefore(' '),style=ts(14,FontWeight.SemiBold,Ink),maxLines=1); Text(sub,style=ts(12,color=InkVariant),maxLines=1) }
      }
     }
    }
   }
   Surface(onClick={sheet=Sheet.Energy},shape=RoundedCornerShape(28.dp),color=t.cont,modifier=Modifier.fillMaxWidth().height(76.dp)) {
    Row(Modifier.padding(horizontal=16.dp),verticalAlignment=Alignment.CenterVertically) {
     Column(Modifier.weight(1f)) { Text("Enerji · bugün",style=ts(14,FontWeight.SemiBold,Ink)); Text(usage?.let { "${hours(it.minutes[0])} açık · ${kwh(it.wh[0])}" } ?: if(light.brand=="tapo") "Yükleniyor…" else "WiZ ampuller enerji ölçmüyor",style=ts(12,color=InkVariant)) }
     Ico(P.chevron,InkVariant,20.dp,2f)
    }
   }
  }

  sheet?.let { s ->
   val state=rememberModalBottomSheetState(skipPartiallyExpanded=true)
   ModalBottomSheet(onDismissRequest={sheet=null},sheetState=state,containerColor=t.cont,contentColor=Ink,dragHandle={ BottomSheetDefaults.DragHandle(color=Outline) }) {
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal=16.dp).padding(bottom=24.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
     Row(verticalAlignment=Alignment.CenterVertically) { Text(s.title,style=ts(22,FontWeight.SemiBold,Ink),modifier=Modifier.weight(1f)); Button(onClick={sheet=null},colors=ButtonDefaults.buttonColors(containerColor=t.primary,contentColor=t.onPrimary)) { Text("Tamam",style=ts(14,FontWeight.SemiBold)) } }
     when(s) {
      Sheet.White->WhiteSheet(light,pick,t,store,actions)
      Sheet.Color->ColorSheet(pick,t,store,actions)
      Sheet.Dyn->DynSheet(pick,t,actions)
      Sheet.Music->Soon("Müzik senkronu","Telefon mikrofonu ritmi dinleyip ışığı ona göre değiştirecek. Mikrofon izni gerekecek; ses kaydedilmeyecek.",t)
      Sheet.Timer->TimerSheet(light,t,store)
      Sheet.Auto->AutoSheet(t,store)
      Sheet.Energy->EnergySheet(light,usage,t)
      Sheet.Conn->ConnSheet(light,t,onRefresh)
     }
    }
   }
  }
  if(renaming) { var name by remember { mutableStateOf(light.name) }
   AlertDialog(onDismissRequest={renaming=false},containerColor=t.cont,titleContentColor=Ink,textContentColor=InkVariant,title={Text("Işığın adı")},
    text={OutlinedTextField(name,{name=it},singleLine=true,label={Text("Ad")},colors=OutlinedTextFieldDefaults.colors(focusedBorderColor=t.primary,focusedLabelColor=t.primary,cursorColor=t.primary,focusedTextColor=Ink,unfocusedTextColor=Ink))},
    confirmButton={TextButton({ renaming=false; onRename(name.trim()) },enabled=name.isNotBlank()){Text("Kaydet",color=t.primary)}},dismissButton={TextButton({renaming=false}){Text("Vazgeç",color=Ink)}})
  }
 }
 }
}

class PickActions(private val light:Light,private val cmd:(String,Int)->Unit,private val onPick:(Pick)->Unit) {
 fun white(k:Int) { val kk=if(light.brand=="tapo") k.coerceIn(2500,6500) else k.coerceIn(2200,6500); onPick(Pick.White(kk)); cmd("kelvin",kk) }
 fun func(i:Int) { val f=Catalog.funcs[i]; onPick(Pick.F(i)); if(light.brand=="wiz") cmd("scene",f.wiz) else { if(f.kelvin>0) cmd("kelvin",f.kelvin.coerceIn(2500,6500)) else cmd("color",f.hue*101+f.sat); cmd("brightness",f.bri) } }
 fun color(c:Color,tone:Int) { val (h,s)=Catalog.hueSat(c); onPick(Pick.C(c,tone)); cmd("color",h*101+s) }
 fun dyn(i:Int) { val d=Catalog.dyns[i]; onPick(Pick.D(i)); if(light.brand=="wiz") cmd("scene",d.wiz) else { val (h,s)=Catalog.hueSat(d.c1); cmd("color",h*101+s) } }
}

@Composable private fun Hero(on:Boolean,level:Int,color:Color,t:Tone,online:Boolean,onToggle:()->Unit) {
 val spin=rememberInfiniteTransition(label="spin").animateFloat(0f,360f,infiniteRepeatable(tween(24000,easing=LinearEasing)),label="rot")
 val r=if(on) (70+level*0.6f)/300f else 72/300f
 Box(Modifier.fillMaxWidth().height(250.dp),contentAlignment=Alignment.Center) {
  MorphBlob(on,t.primary,Modifier.size(250.dp).graphicsLayer { rotationZ=spin.value },radius=r*1.22f,amp=0.05f,offAmp=0f,lobesOn=12,lobesOff=12,alpha=0.16f)
  MorphBlob(on,if(on) color else t.cont,Modifier.size(250.dp),radius=r,amp=0.07f,offAmp=0f)
  Surface(onClick=onToggle,enabled=online,shape=CircleShape,color=Color.Transparent,modifier=Modifier.size(150.dp)) {
   Column(horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center) {
    val fg=if(on) Color(0xFF1A1208) else Ink
    Text(if(on) "%$level" else "Kapalı",style=ts(48,FontWeight.Light,fg,52))
    Text(if(!online) "Bağlantı yok" else if(on) "Kapatmak için dokun" else "Açmak için dokun",style=ts(13,FontWeight.SemiBold,fg))
   }
  }
 }
}

@Composable private fun Label(text:String,hint:String?=null) { Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.Bottom) { Text(text,style=ts(15,FontWeight.SemiBold,Ink),modifier=Modifier.weight(1f)); hint?.let { Text(it,style=ts(13,color=InkVariant)) } } }

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable private fun WhiteSheet(light:Light,pick:Pick,t:Tone,store:Store,a:PickActions) {
 var presets by remember { mutableStateOf(store.presets()) }
 val all=Catalog.whites+presets
 val current=(pick as? Pick.White)?.k
 androidx.compose.foundation.lazy.LazyRow(horizontalArrangement=Arrangement.spacedBy(10.dp),contentPadding=PaddingValues(vertical=4.dp)) {
  items(all.size) { i -> val (name,k)=all[i]; val saved=i>=Catalog.whites.size
   Column(Modifier.width(60.dp).combinedClickable(onClick={a.white(k)},onLongClick={ if(saved) { presets=presets-presets[i-Catalog.whites.size]; store.savePresets(presets) } }),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(6.dp)) {
    Glow(Catalog.kelvinColor(k),Catalog.kelvinColor(k),current==k,t.primary,t.cont,Modifier.size(52.dp))
    Text(name,style=ts(12,color=Ink),maxLines=1); Text("$k K",style=ts(11,color=InkVariant))
   }
  }
  item {
   Column(Modifier.width(60.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(6.dp)) {
    Surface(onClick={ val k=current ?: light.kelvin; if(presets.size<6 && all.none { it.second==k }) { presets=presets+("Ton ${presets.size+1}" to k); store.savePresets(presets) } },shape=RoundedCornerShape(16.dp),color=Color.Transparent,border=androidx.compose.foundation.BorderStroke(2.dp,t.primary),modifier=Modifier.size(52.dp)) { Box(contentAlignment=Alignment.Center) { Icon(icon(P.plus,2.2f),"Bu tonu kaydet",tint=t.primary) } }
    Text("Kaydet",style=ts(12,color=Ink))
   }
  }
 }
 Label("Ton",(current ?: light.kelvin).toString()+" K")
 KelvinBar(current ?: light.kelvin,if(light.brand=="tapo") 2500 else 2200) { a.white(it) }
 Text("Kaydettiğiniz tonu silmek için üzerine basılı tutun.",style=ts(12,color=InkVariant))
 Label("İşlevsel")
 FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
  Catalog.funcs.forEachIndexed { i,f -> val sel=(pick as? Pick.F)?.i==i
   val r by animateDpAsState(if(sel) 20.dp else 12.dp,FastDp,label="chip")
   Surface(onClick={a.func(i)},shape=RoundedCornerShape(r),color=if(sel) t.primary else t.tonal,contentColor=if(sel) t.onPrimary else t.onTonal,modifier=Modifier.height(40.dp)) { Box(Modifier.padding(horizontal=14.dp),contentAlignment=Alignment.Center) { Text(f.name,style=ts(14,FontWeight.Medium)) } }
  }
 }
}

@Composable private fun KelvinBar(k:Int,min:Int,onPick:(Int)->Unit) {
 var width by remember { mutableIntStateOf(1) }
 val f by animateFloatAsState(((k-min).toFloat()/(6500-min)).coerceIn(0f,1f),Fast,label="k")
 fun at(x:Float)=((min+(x/width).coerceIn(0f,1f)*(6500-min))/100).roundToInt()*100
 Box(Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(20.dp)).background(Brush.horizontalGradient(listOf(Color(0xFFFFA94D),Color(0xFFFFE9C9),Color(0xFFE3ECFF)))).onSizeChanged { width=it.width }
  .pointerInput(min) { detectTapGestures { onPick(at(it.x)) } }.pointerInput(min) { detectHorizontalDragGestures { c,_ -> c.consume(); onPick(at(c.position.x)) } }) {
  BoxWithConstraints(Modifier.fillMaxSize()) { Box(Modifier.offset(x=(maxWidth-6.dp)*f).padding(vertical=8.dp).width(6.dp).fillMaxHeight().clip(RoundedCornerShape(3.dp)).background(Color(0xFF17130B))) }
 }
}

@Composable private fun ColorSheet(pick:Pick,t:Tone,store:Store,a:PickActions) {
 val context=LocalContext.current
 var photo by remember { mutableStateOf<List<Color>>(emptyList()) }
 var busy by remember { mutableStateOf(false) }
 var uri by remember { mutableStateOf<Uri?>(null) }
 val launcher=rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri=it }
 LaunchedEffect(uri) { uri?.let { busy=true; photo=withContext(Dispatchers.Default) { photoColors(context,it) }; busy=false } }
 val current=(pick as? Pick.C)?.color
 for(row in 0..1) Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
  for(col in 0..5) { val s=Catalog.colors[row*6+col]; SwatchButton(s.color,current==s.color,t,s.name) { a.color(s.color,s.tone) } }
 }
 var start by remember { mutableIntStateOf(store.int("start_mode")) }
 Label("Açılış rengi","Işık açılınca")
 ConnectedGroup(listOf("Son kullanılan","Sıcak beyaz","Bu renk"),start,t,{ start=it; store.putInt("start_mode",it); if(it==2) current?.let { c -> val (h,s)=Catalog.hueSat(c); store.putInt("start_color",h*101+s) } },Modifier.fillMaxWidth(),height=44.dp,textSize=13)
 Surface(shape=RoundedCornerShape(24.dp),color=t.tonal,modifier=Modifier.fillMaxWidth()) {
  Row(Modifier.padding(14.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
   Box(Modifier.size(64.dp).clip(RoundedCornerShape(16.dp)).background(Color.Black.copy(alpha=.3f)),contentAlignment=Alignment.Center) { if(busy) LoadingIndicatorCompat(t) else Ico(P.photo,t.onTonal,28.dp) }
   Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(8.dp)) {
    Text("Fotoğraftan renk",style=ts(15,FontWeight.SemiBold,t.onTonal))
    if(photo.isEmpty()) Text("Bir fotoğraf seçin, içindeki renkler çıkarılsın.",style=ts(12,color=t.onTonal))
    else Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) { photo.forEach { c -> Surface(onClick={a.color(c,0)},shape=RoundedCornerShape(if(current==c) 14.dp else 9.dp),color=c,modifier=Modifier.size(30.dp)) {} } }
   }
   Button(onClick={launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))},colors=ButtonDefaults.buttonColors(containerColor=t.primary,contentColor=t.onPrimary)) { Text("Seç",style=ts(13,FontWeight.SemiBold)) }
  }
 }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable private fun LoadingIndicatorCompat(t:Tone) = LoadingIndicator(Modifier.size(32.dp),color=t.primary)

@Composable private fun SwatchButton(c:Color,selected:Boolean,t:Tone,name:String,onClick:()->Unit) {
 val r by animateDpAsState(if(selected) 14.dp else 22.dp,FastDp,label="sw")
 Box(Modifier.size(50.dp),contentAlignment=Alignment.Center) {
  if(selected) Box(Modifier.size(50.dp).clip(RoundedCornerShape(r+3.dp)).background(Ink))
  if(selected) Box(Modifier.size(46.dp).clip(RoundedCornerShape(r+1.dp)).background(t.cont))
  Surface(onClick=onClick,shape=RoundedCornerShape(r),color=c,modifier=Modifier.size(if(selected) 40.dp else 44.dp)) {}
 }
}

@Composable private fun DynSheet(pick:Pick,t:Tone,a:PickActions) {
 val current=(pick as? Pick.D)?.i
 for(row in 0..2) Row(Modifier.fillMaxWidth()) {
  for(col in 0..3) { val i=row*4+col; val d=Catalog.dyns[i]
   Surface(onClick={a.dyn(i)},color=Color.Transparent,shape=RoundedCornerShape(16.dp),modifier=Modifier.weight(1f)) {
    Column(Modifier.padding(vertical=6.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(5.dp)) { Glow(d.c1,d.c2,current==i,t.primary,t.cont,Modifier.size(50.dp)); Text(d.name,style=ts(12,color=Ink),maxLines=1) }
   }
  }
 }
 Surface(shape=RoundedCornerShape(26.dp),color=t.tonal.copy(alpha=.6f),modifier=Modifier.fillMaxWidth().height(52.dp)) { Row(horizontalArrangement=Arrangement.Center,verticalAlignment=Alignment.CenterVertically) { Ico(P.sparkle,t.onTonal.copy(alpha=.6f),20.dp); Spacer(Modifier.width(8.dp)); Text("Kendi efektini yap · Yakında",style=ts(15,FontWeight.SemiBold,t.onTonal.copy(alpha=.6f))) } }
 Text("Tapo ampullerde dinamik sahneler sahnenin ana rengiyle sabit olarak uygulanır.",style=ts(12,color=InkVariant))
}

@Composable private fun Soon(title:String,text:String,t:Tone) {
 Surface(shape=RoundedCornerShape(24.dp),color=t.tonal,modifier=Modifier.fillMaxWidth()) { Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) { Ico(P.mic,t.primary,28.dp); Text("$title · Yakında",style=ts(17,FontWeight.SemiBold,t.onTonal)); Text(text,style=ts(14,color=t.onTonal)) } }
}

@Composable private fun TimerSheet(light:Light,t:Tone,store:Store) {
 val context=LocalContext.current
 val opts=listOf(15,30,60,0)
 var sel by remember { mutableIntStateOf(if(store.long("timer_${light.id}")>System.currentTimeMillis()) opts.indexOf(store.int("timer_min_${light.id}")).coerceAtLeast(0) else 3) }
 Label("Kapanma zamanı")
 ConnectedGroup(listOf("15 dk","30 dk","1 sa","Kapalı"),sel,t,{ sel=it; Timers.schedule(context,light,opts[it]) },Modifier.fillMaxWidth(),height=52.dp)
 Text(if(sel==3) "Zamanlayıcı kapalı" else "${light.name} ${listOf("15 dakika","30 dakika","1 saat")[sel]} sonra kapanacak",style=ts(13,color=InkVariant))
 Label("Yumuşak geçiş","Yakında")
 ConnectedGroup(listOf("Anında","1 sn","2 sn"),0,t,{},Modifier.fillMaxWidth(),height=44.dp,textSize=13,enabled=false)
}

@Composable private fun AutoSheet(t:Tone,store:Store) {
 val context=LocalContext.current
 var circ by remember { mutableStateOf(store.flag("circadian")) }
 AutoRow(P.sun,"Sirkadiyen ritim","Tüm ışıklar · sabah soğuk, akşam sıcak",circ,t,true) { circ=it; store.putFlag("circadian",it); Circadian.schedule(context,it) }
 AutoRow(P.sunset,"Gün batımında aç","Yakında · konum gerekir",false,t,false) {}
 AutoRow(P.sunrise,"Gün doğumunda kapat","Yakında · konum gerekir",false,t,false) {}
}

@Composable private fun AutoRow(d:String,title:String,sub:String,checked:Boolean,t:Tone,enabled:Boolean,onChange:(Boolean)->Unit) {
 Row(Modifier.fillMaxWidth().height(64.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
  Box(Modifier.size(40.dp).clip(CircleShape).background(t.tonal),contentAlignment=Alignment.Center) { Ico(d,t.primary,22.dp) }
  Column(Modifier.weight(1f)) { Text(title,style=ts(15,color=if(enabled) Ink else Ink.copy(alpha=.6f))); Text(sub,style=ts(12,color=InkVariant)) }
  Switch(checked=checked,onCheckedChange=onChange,enabled=enabled,colors=toneSwitchColors(t))
 }
}

@Composable private fun EnergySheet(light:Light,usage:Usage?,t:Tone) {
 var tab by remember { mutableIntStateOf(0) }
 if(light.brand!="tapo") { Text("WiZ ampuller enerji tüketimini ölçmez. Bu bilgi yalnızca Tapo ampullerde gösterilir.",style=ts(14,color=InkVariant)); return }
 ConnectedGroup(listOf("Bugün","7 gün","30 gün"),tab,t,{tab=it},Modifier.fillMaxWidth(),height=40.dp,textSize=13)
 if(usage==null) { Text("Veri alınamadı. Tapo hesabını ve bağlantıyı kontrol edin.",style=ts(14,color=InkVariant)); return }
 Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
  listOf("Çalışma" to hours(usage.minutes[tab]),"Tüketim" to kwh(usage.wh[tab]),"Tasarruf" to kwh(usage.savedWh[tab])).forEach { (k,v) ->
   Surface(shape=RoundedCornerShape(18.dp),color=t.tonal,modifier=Modifier.weight(1f)) { Column(Modifier.padding(12.dp)) { Text(k,style=ts(11,color=t.onTonal)); Text(v,style=ts(18,FontWeight.Medium,Ink)) } }
  }
 }
 Text("Tasarruf, aynı ışığı veren klasik ampule göre hesaplanır (Tapo verisi).",style=ts(12,color=InkVariant))
}

@Composable private fun ConnSheet(light:Light,t:Tone,onRefresh:()->Unit) {
 val context=LocalContext.current
 var diag by remember { mutableStateOf<Diagnosis?>(null) }
 var run by remember { mutableIntStateOf(0) }
 LaunchedEffect(run) { diag=null; diag=withContext(Dispatchers.IO) { Network.diagnose(context,light) } }
 val d=diag
 val rows=listOf("Durum" to (d?.let { if(it.ok) "Bağlı" else "Ulaşılamıyor" } ?: "Test ediliyor…"),"IP adresi" to light.ip,"Sinyal" to (d?.rssi?.let { "$it dBm · ${quality(it)}" } ?: "—"),"Yanıt süresi" to (d?.pingMs?.let { "$it ms" } ?: "—"),"Model" to light.model.ifBlank { light.brand.uppercase() })
 rows.forEach { (k,v) -> Row(Modifier.fillMaxWidth().height(36.dp),verticalAlignment=Alignment.CenterVertically) { Text(k,style=ts(15,color=InkVariant),modifier=Modifier.weight(1f)); Text(v,style=ts(15,color=Ink)) } }
 Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
  FilledTonalButton(onClick={run++},modifier=Modifier.weight(1f).height(48.dp),colors=ButtonDefaults.filledTonalButtonColors(containerColor=t.tonal,contentColor=t.onTonal)) { Text("Test et",style=ts(14,FontWeight.SemiBold)) }
  Button(onClick={onRefresh(); run++},modifier=Modifier.weight(1f).height(48.dp),colors=ButtonDefaults.buttonColors(containerColor=t.primary,contentColor=t.onPrimary)) { Text("Yeniden bağlan",style=ts(14,FontWeight.SemiBold)) }
 }
}

fun quality(rssi:Int)=when { rssi==0->"Bağlı"; rssi>=-60->"İyi"; rssi>=-70->"Orta"; else->"Zayıf" }
fun hours(min:Int)=if(min<60) "$min dk" else String.format(java.util.Locale("tr"),"%.1f sa",min/60f)
fun kwh(wh:Int)=String.format(java.util.Locale("tr"),"%.2f kWh",wh/1000f)

// Picks up to five dominant, saturated colors from a photo by bucketing hues.
fun photoColors(context:Context,uri:Uri):List<Color> = try {
 val bounds=BitmapFactory.Options().apply { inJustDecodeBounds=true }
 context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it,null,bounds) }
 var sample=1; while(bounds.outWidth/sample>256 || bounds.outHeight/sample>256) sample*=2
 val bmp=context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it,null,BitmapFactory.Options().apply { inSampleSize=sample }) } ?: throw IllegalStateException()
 val small=android.graphics.Bitmap.createScaledBitmap(bmp,48,48,true)
 val weight=FloatArray(12); val sums=Array(12) { FloatArray(3) }; val hsv=FloatArray(3)
 for(x in 0 until 48) for(y in 0 until 48) { val p=small.getPixel(x,y); android.graphics.Color.colorToHSV(p,hsv); if(hsv[1]<0.18f || hsv[2]<0.22f) continue; val b=(hsv[0]/30).toInt().coerceIn(0,11); val w=hsv[1]*hsv[2]; weight[b]+=w; sums[b][0]+=hsv[0]*w; sums[b][1]+=hsv[1]*w; sums[b][2]+=w }
 (0 until 12).filter { weight[it]>0.5f }.sortedByDescending { weight[it] }.take(5).map { b -> Catalog.colorOf((sums[b][0]/weight[b]).roundToInt(),((sums[b][1]/weight[b])*100).roundToInt().coerceIn(30,100)) }
} catch (_:Exception) { emptyList() }
