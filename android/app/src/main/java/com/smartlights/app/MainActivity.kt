package com.smartlights.app

import android.app.*
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.*
import android.os.Bundle
import android.widget.RemoteViews
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*

class MainActivity : ComponentActivity() {
 override fun onCreate(savedInstanceState:Bundle?) {
  enableEdgeToEdge(SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))
  super.onCreate(savedInstanceState); setContent { SmartApp() }
 }
}

private fun optimistic(l:Light,kind:String,v:Int)=when(kind) { "power"->l.copy(on=v==1); "brightness"->l.copy(on=true,brightness=v); "kelvin"->l.copy(on=true,kelvin=v,colorMode=false,scene=0); "color"->l.copy(on=true,hue=v/101,saturation=v%101,colorMode=true,scene=0); "scene"->l.copy(on=true,scene=v,colorMode=false); else->l }

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable fun SmartApp() {
 val context=LocalContext.current
 val store=remember { Store(context) }
 val scope=rememberCoroutineScope()
 var lights by remember { mutableStateOf(store.named(store.load())) }
 var favorites by remember { mutableStateOf(store.favorites()) }
 var hiddenCount by remember { mutableIntStateOf(store.hidden().size) }
 var scanning by remember { mutableStateOf(false) }
 var refreshPending by remember { mutableStateOf(false) }
 var pending by remember { mutableStateOf(setOf<String>()) }
 var error by remember { mutableStateOf<String?>(null) }
 var selected by remember { mutableStateOf((context as? Activity)?.intent?.getStringExtra("open")?.takeIf { it.isNotBlank() }) }
 var credentials by remember { mutableStateOf(false) }
 var manual by remember { mutableStateOf(false) }
 var saveScene by remember { mutableStateOf(false) }
 var sceneNames by remember { mutableStateOf(store.scenes().keys().asSequence().toList()) }
 var active by remember { mutableStateOf(true) }
 val jobs=remember { HashMap<String,Job>() }
 val lifecycleOwner=androidx.lifecycle.compose.LocalLifecycleOwner.current
 DisposableEffect(lifecycleOwner) {
  val observer=androidx.lifecycle.LifecycleEventObserver { _, event -> when(event) { androidx.lifecycle.Lifecycle.Event.ON_START -> active=true; androidx.lifecycle.Lifecycle.Event.ON_STOP -> active=false; else -> {} } }
  lifecycleOwner.lifecycle.addObserver(observer); onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
 }
 suspend fun refresh() {
  if(scanning) { refreshPending=true; return }
  scanning=true
  try { val hidden=store.hidden(); val found=store.named(Network.scan(context,lights)).filter { it.id !in hidden && it.ip !in hidden }; lights=found.map { f -> if(jobs.keys.any { it.startsWith(f.id+"/") && jobs[it]?.isActive==true }) lights.find { it.id==f.id } ?: f else f }; store.save(lights); DualLightsWidget.renderAll(context) } catch(e:CancellationException) { throw e } catch(e:Exception) { error="Tarama tamamlanamadı: ${e.localizedMessage}" } finally { scanning=false }
  if(refreshPending) { refreshPending=false; refresh() }
 }
 // UI updates instantly; the network call is debounced per light and kind so sliders stay smooth.
 fun command(light:Light,kind:String,value:Int) {
  lights=lights.map { if(it.id==light.id) optimistic(it,kind,value) else it }
  val key="${light.id}/$kind"; jobs[key]?.cancel()
  jobs[key]=scope.launch {
   if(kind=="brightness" || kind=="kelvin" || kind=="color") delay(80)
   pending=pending+light.id
   try {
    withContext(Dispatchers.IO) { val target=lights.find { it.id==light.id } ?: light; try { Network.set(context,target,kind,value) } catch(e:Exception) { delay(300); Network.set(context,target,kind,value) } }
    if(kind=="power" && value==1) { val mode=store.int("start_mode"); if(mode>0) { val k=if(mode==1) "kelvin" else "color"; val v=if(mode==1) (if(light.brand=="tapo") 2700 else 2700) else store.int("start_color"); if(mode==1 || v>0) { lights=lights.map { if(it.id==light.id) optimistic(it,k,v) else it }; withContext(Dispatchers.IO) { Network.set(context,light,k,v) } } } }
    store.save(lights); DualLightsWidget.renderAll(context)
   } catch(e:CancellationException) { throw e } catch(e:Exception) { error="${light.name}: bağlantı kurulamadı. Wi-Fi ve Tapo hesabını kontrol edin."; lights=lights.map { if(it.id==light.id) it.copy(online=false) else it } }
   finally { delay(250); if(jobs.none { it.key.startsWith(light.id+"/") && it.value.isActive && it.value!=coroutineContext[Job] }) pending=pending-light.id }
  }
 }
 fun applyScene(scene:String) { scope.launch {
  val a=store.scenes().optJSONArray(scene) ?: return@launch
  val saved=(0 until a.length()).map{Light.from(a.getJSONObject(it))}
  saved.forEach { preset -> val current=lights.find{it.id==preset.id || it.ip==preset.ip} ?: return@forEach
   command(current,"power",if(preset.on) 1 else 0)
   if(preset.on) { command(current,"brightness",preset.brightness); when { preset.scene>0 && current.brand=="wiz" -> command(current,"scene",preset.scene); preset.colorMode -> command(current,"color",preset.hue*101+preset.saturation); else -> command(current,"kelvin",preset.kelvin) } }
  }
 } }
 fun applyReady(r:Ready) { lights.filter { it.online }.forEach { l ->
  command(l,"brightness",if(l.brand=="wiz") r.bri.coerceAtLeast(10) else r.bri)
  when { r.wiz>0 && l.brand=="wiz" -> command(l,"scene",r.wiz); r.kelvin>0 -> command(l,"kelvin",if(l.brand=="tapo") r.kelvin.coerceIn(2500,6500) else r.kelvin); else -> command(l,"color",r.hue*101+r.sat) }
 } }
 LaunchedEffect(active) { if(active) { refresh(); while(true) { delay(30000); refresh() } } }
 var dark by remember { mutableStateOf(!store.flag("light_mode")) }
 var themeChoice by remember { mutableLongStateOf(store.long("theme_seed")) }
 val systemSeed=if(android.os.Build.VERSION.SDK_INT>=31) dynamicDarkColorScheme(context).primary else DefaultSeed
 val tone=animatedTone(toneFrom(seedColor(themeChoice,systemSeed),dark))
 val view=androidx.compose.ui.platform.LocalView.current
 SideEffect { (context as? Activity)?.window?.let { w -> androidx.core.view.WindowCompat.getInsetsController(w,view).apply { isAppearanceLightStatusBars=!dark; isAppearanceLightNavigationBars=!dark } } }
 val scheme=if(dark) darkColorScheme(primary=tone.primary,onPrimary=tone.onPrimary,surface=tone.bg,background=tone.bg,onSurface=tone.ink,surfaceContainerHigh=tone.cont,surfaceContainer=tone.cont,secondaryContainer=tone.tonal,onSecondaryContainer=tone.onTonal,primaryContainer=tone.pc,onPrimaryContainer=tone.onPc,onSurfaceVariant=tone.inkVar,outline=tone.outline)
  else lightColorScheme(primary=tone.primary,onPrimary=tone.onPrimary,surface=tone.bg,background=tone.bg,onSurface=tone.ink,surfaceContainerHigh=tone.cont,surfaceContainer=tone.cont,secondaryContainer=tone.tonal,onSecondaryContainer=tone.onTonal,primaryContainer=tone.pc,onPrimaryContainer=tone.onPc,onSurfaceVariant=tone.inkVar,outline=tone.outline)
 CompositionLocalProvider(LocalTone provides tone) {
 MaterialExpressiveTheme(colorScheme=scheme,motionScheme=MotionScheme.expressive()) {
  val current=lights.find { it.id==selected }
  BackHandler(enabled=current!=null) { selected=null }
  Box(Modifier.fillMaxSize().background(Amber.bg)) {
   AnimatedContent(targetState=current?.id,transitionSpec={ (fadeIn(tween(180))+slideInHorizontally(tween(260)) { if(targetState!=null) it/6 else -it/6 }) togetherWith fadeOut(tween(120)) },label="screen") { id ->
    val light=lights.find { it.id==id }
    if(light==null) Home(lights,favorites,scanning,pending,sceneNames,dark,themeChoice,systemSeed,{ scope.launch { refresh() } },{ selected=it },{ l,on -> command(l,"power",if(on) 1 else 0) },{ on -> lights.filter { it.online }.forEach { command(it,"power",if(on) 1 else 0) } },{ applyReady(it) },{ applyScene(it) },{ store.removeScene(it); sceneNames=store.scenes().keys().asSequence().toList() },{ manual=true },{ saveScene=true },{ credentials=true },{ dark=it; store.putFlag("light_mode",!it) },{ themeChoice=it; store.putLong("theme_seed",it) },{ l -> store.hide(l); hiddenCount=store.hidden().size; lights=lights.filter { it.id!=l.id }; DualLightsWidget.renderAll(context) },hiddenCount,{ store.unhideAll(); hiddenCount=0; scope.launch { refresh() } })
    else Detail(light,light.id in favorites,store,{ kind,value -> command(light,kind,value) },{ selected=null },{ fav -> store.setFavorite(light.id,fav); favorites=store.favorites() },{ scope.launch { refresh() } },{ name -> store.setName(light.id,name); lights=store.named(lights); store.save(lights); DualLightsWidget.renderAll(context) })
   }
  }
  val field=OutlinedTextFieldDefaults.colors(focusedBorderColor=Amber.primary,focusedLabelColor=Amber.primary,cursorColor=Amber.primary)
  if(error!=null) AlertDialog(onDismissRequest={error=null},containerColor=Amber.cont,confirmButton={TextButton(onClick={error=null}){Text("Tamam",color=Amber.primary)}},title={Text("Bağlantı")},text={Text(error!!)})
  if(credentials) { var email by remember { mutableStateOf(store.credentials()?.first ?: "") }; var pass by remember { mutableStateOf("") }; AlertDialog(onDismissRequest={credentials=false},containerColor=Amber.cont,title={Text("Tapo hesabı")},text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)) { Text("Tapo uygulamasında kullandığınız hesap bilgileri bu telefonda şifreli saklanır.",color=InkVariant); OutlinedTextField(email,{email=it},label={Text("E-posta")},singleLine=true,colors=field); OutlinedTextField(pass,{pass=it},label={Text("Şifre")},singleLine=true,visualTransformation=PasswordVisualTransformation(),colors=field) }},confirmButton={TextButton(onClick={store.saveCredentials(email.trim(),pass); credentials=false; scope.launch { refresh() }},enabled=email.isNotBlank() && pass.isNotEmpty()){Text("Kaydet ve ara",color=Amber.primary)}},dismissButton={TextButton(onClick={credentials=false}){Text("Vazgeç",color=Ink)}}) }
  if(saveScene) { var name by remember { mutableStateOf("") }; AlertDialog(onDismissRequest={saveScene=false},containerColor=Amber.cont,title={Text("Sahneyi kaydet")},text={OutlinedTextField(name,{name=it},label={Text("Sahne adı")},singleLine=true,colors=field)},confirmButton={TextButton(onClick={store.saveScene(name.trim(),lights.filter{it.online});sceneNames=store.scenes().keys().asSequence().toList();saveScene=false},enabled=name.isNotBlank() && lights.any{it.online}){Text("Kaydet",color=Amber.primary)}},dismissButton={TextButton(onClick={saveScene=false}){Text("Vazgeç",color=Ink)}}) }
  if(manual) { var ip by remember { mutableStateOf("") }; var tapo by remember { mutableStateOf(true) }; AlertDialog(onDismissRequest={manual=false},containerColor=Amber.cont,title={Text("IP adresiyle ekle")},text={Column(verticalArrangement=Arrangement.spacedBy(12.dp)) { OutlinedTextField(ip,{ip=it},label={Text("IP adresi")},singleLine=true,colors=field); ConnectedGroup(listOf("Tapo","WiZ"),if(tapo) 0 else 1,Amber,{ tapo=it==0 },Modifier.fillMaxWidth(),height=44.dp) }},confirmButton={TextButton(onClick={ val l=Light(ip.trim(),ip.trim(),if(tapo) "Tapo ${ip.trim()}" else "WiZ ${ip.trim()}",if(tapo) "tapo" else "wiz"); lights=(lights+listOf(l)).distinctBy{it.ip}; store.save(lights); manual=false; scope.launch{refresh()} },enabled=ip.isNotBlank()){Text("Ekle",color=Amber.primary)}},dismissButton={TextButton(onClick={manual=false}){Text("Vazgeç",color=Ink)}}) }
 }
 }
}

class LightsWidget:AppWidgetProvider() {
 override fun onUpdate(context:Context,manager:AppWidgetManager,ids:IntArray) { ids.forEach { id -> val views=RemoteViews(context.packageName,R.layout.widget); for ((view,on) in listOf(R.id.widget_on to true,R.id.widget_off to false)) { val i=Intent(context,WidgetAction::class.java).setAction(if(on) "com.smartlights.ON" else "com.smartlights.OFF"); views.setOnClickPendingIntent(view,PendingIntent.getBroadcast(context,if(on) 1 else 2,i,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)) }; manager.updateAppWidget(id,views) } }
}

class WidgetAction:BroadcastReceiver() {
 override fun onReceive(context:Context,intent:Intent) { if(intent.action=="com.smartlights.ON" || intent.action=="com.smartlights.OFF") { val pending=goAsync(); CoroutineScope(Dispatchers.IO).launch { try { Store(context).load().forEach { try { Network.set(context,it,"power",if(intent.action=="com.smartlights.ON") 1 else 0) } catch (_:Exception) {} } } finally { pending.finish() } } } }
}
