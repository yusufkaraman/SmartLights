package com.smartlights.app

import android.app.Activity
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.RemoteViews
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.util.concurrent.Executors

private const val SELECTED = "widget_selections"
private fun selections(context:Context,id:Int):List<String> {
 val prefs=context.getSharedPreferences(SELECTED,Context.MODE_PRIVATE)
 return (0..2).mapNotNull { prefs.getString("${id}_$it",null) }
}
private fun saveSelections(context:Context,id:Int,ids:List<String>) {
 val edit=context.getSharedPreferences(SELECTED,Context.MODE_PRIVATE).edit()
 (0..2).forEach { slot -> if(slot < ids.size) edit.putString("${id}_$slot",ids[slot]) else edit.remove("${id}_$slot") }
 edit.apply()
}
private fun maxLights(context:Context,id:Int)=when(AppWidgetManager.getInstance(context).getAppWidgetInfo(id)?.provider?.className) { SingleLightWidget::class.java.name->1; RoomWidget::class.java.name->3; DualLightsWidget::class.java.name->2; else->0 }

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
class WidgetConfigActivity:ComponentActivity() {
 override fun onCreate(savedInstanceState:Bundle?) {
  super.onCreate(savedInstanceState)
  setResult(Activity.RESULT_CANCELED)
  val id=intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,AppWidgetManager.INVALID_APPWIDGET_ID)
  val max=if(id==AppWidgetManager.INVALID_APPWIDGET_ID) 0 else maxLights(this,id)
  if(max==0) { finish(); return }
  val store=Store(this); val devices=store.named(store.load())
  setContent {
   val dark=isSystemInDarkTheme()
   val seed=if(android.os.Build.VERSION.SDK_INT>=31) dynamicDarkColorScheme(this).primary else DefaultSeed
   val t=toneFrom(seed,dark)
   CompositionLocalProvider(LocalTone provides t) {
    MaterialExpressiveTheme(colorScheme=if(dark) darkColorScheme(primary=t.primary,onPrimary=t.onPrimary,surface=t.bg,onSurface=t.ink) else lightColorScheme(primary=t.primary,onPrimary=t.onPrimary,surface=t.bg,onSurface=t.ink)) {
     var selected by remember { mutableStateOf(selections(this,id).take(max)) }
     Scaffold(containerColor=t.bg,bottomBar={Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(20.dp),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
      FilledTonalButton(onClick={finish()},modifier=Modifier.weight(1f).height(52.dp),colors=ButtonDefaults.filledTonalButtonColors(containerColor=t.tonal,contentColor=t.onTonal)){Text("Vazgeç",style=ts(15,FontWeight.SemiBold))}
      Button(onClick={saveSelections(this@WidgetConfigActivity,id,selected); Widgets.renderAll(this@WidgetConfigActivity); setResult(Activity.RESULT_OK,Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,id)); finish()},enabled=selected.isNotEmpty(),modifier=Modifier.weight(1f).height(52.dp),colors=ButtonDefaults.buttonColors(containerColor=t.primary,contentColor=t.onPrimary)){Text("Kaydet",style=ts(15,FontWeight.SemiBold))}
     }}) { padding ->
      LazyColumn(Modifier.fillMaxSize().padding(padding),contentPadding=PaddingValues(start=16.dp,end=16.dp,top=WindowInsets.statusBars.asPaddingValues().calculateTopPadding()+28.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
       item { Text("Widget ışıkları",style=ts(32,FontWeight.SemiBold,t.ink)); Spacer(Modifier.height(6.dp)); Text(if(max==1) "Bir ışık seçin." else "En fazla $max ışık seçin.",style=ts(15,color=t.inkVar)); Spacer(Modifier.height(10.dp)) }
       if(devices.isEmpty()) item { Text("Önce SmartLights uygulamasında ışıkları bulun veya IP adresiyle ekleyin.",style=ts(15,color=t.ink)) }
       items(devices,key={it.id}) { light -> val on=light.id in selected
        Surface(onClick={ selected=if(on) selected-light.id else if(max==1) listOf(light.id) else if(selected.size<max) selected+light.id else selected },shape=androidx.compose.foundation.shape.RoundedCornerShape(24.dp),color=if(on) t.tonal else t.cont,modifier=Modifier.fillMaxWidth()) {
         Row(Modifier.padding(16.dp),verticalAlignment=Alignment.CenterVertically) {
          Column(Modifier.weight(1f)) { Text(light.name,style=ts(17,FontWeight.Medium,t.ink)); Text(light.brand.uppercase(),style=ts(13,color=t.inkVar)) }
          if(on) Ico(P.check,t.primary,24.dp,2.4f)
         }
        }
       }
      }
     }
    }
   }
  }
 }
}

// All home-screen widgets: rendering is instant from stored state, commands run in the background.
object Widgets {
 private val executor=Executors.newSingleThreadExecutor()
 private const val ACTION="com.smartlights.WIDGET"
 private fun action(context:Context,widget:Int,op:String,light:String="",value:Int=0):PendingIntent {
  val i=Intent(context,WidgetActionReceiver::class.java).setAction(ACTION).setData(Uri.parse("smartlights://w/$widget/$op/${Uri.encode(light)}/$value")).putExtra("op",op).putExtra("light",light).putExtra("value",value)
  return PendingIntent.getBroadcast(context,0,i,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
 }
 private fun open(context:Context,light:String?):PendingIntent {
  val i=Intent(context,MainActivity::class.java).setData(Uri.parse("smartlights://open/${Uri.encode(light ?: "")}")).putExtra("open",light).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
  return PendingIntent.getActivity(context,0,i,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
 }
 private fun config(context:Context,id:Int)=PendingIntent.getActivity(context,id,Intent(context,WidgetConfigActivity::class.java).setAction(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,id).setData(Uri.parse("smartlights://widget/$id/config")),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
 private fun ids(context:Context,cls:Class<*>)=AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context,cls))
 private fun lights(context:Context)=Store(context).let { it.named(it.load()) }
 private fun chosen(context:Context,id:Int,all:List<Light>):List<Light?> { val by=all.associateBy { it.id }; return selections(context,id).map { by[it] } }
 private fun status(l:Light)=l.brand.let { if(it=="wiz") "WiZ" else "Tapo" }+" · "+when { !l.online && l.ip.isBlank()->"—"; l.on->"%${l.brightness}"; else->"Kapalı" }

 fun renderAll(context:Context) {
  val all=lights(context); val m=AppWidgetManager.getInstance(context)
  ids(context,SingleLightWidget::class.java).forEach { m.updateAppWidget(it,single(context,it,all)) }
  ids(context,DualLightsWidget::class.java).forEach { m.updateAppWidget(it,dual(context,it,all)) }
  ids(context,ScenesWidget::class.java).forEach { m.updateAppWidget(it,scenes(context,it)) }
  ids(context,RoomWidget::class.java).forEach { m.updateAppWidget(it,room(context,it,all)) }
 }

 private fun single(context:Context,id:Int,all:List<Light>):RemoteViews {
  val v=RemoteViews(context.packageName,R.layout.widget_single); val l=chosen(context,id,all).firstOrNull()
  if(l==null) { v.setTextViewText(R.id.name,"Işık seçin"); v.setTextViewText(R.id.level,"⚙"); v.setOnClickPendingIntent(R.id.root,config(context,id)); return v }
  v.setTextViewText(R.id.name,l.name); v.setTextViewText(R.id.level,if(l.on) "%${l.brightness}" else "Kapalı")
  v.setImageViewResource(R.id.shape,if(l.on) R.drawable.w_shape_on else R.drawable.w_shape_off); v.setImageViewResource(R.id.bulb,if(l.on) R.drawable.w_bulb_on else R.drawable.w_bulb_off)
  v.setOnClickPendingIntent(R.id.header,open(context,l.id)); v.setOnClickPendingIntent(R.id.toggle,action(context,id,"toggle",l.id))
  v.setOnClickPendingIntent(R.id.minus,action(context,id,"bri",l.id,-10)); v.setOnClickPendingIntent(R.id.plus,action(context,id,"bri",l.id,10))
  return v
 }

 private fun dual(context:Context,id:Int,all:List<Light>):RemoteViews {
  val v=RemoteViews(context.packageName,R.layout.dual_widget); val c=chosen(context,id,all)
  val slots=arrayOf(intArrayOf(R.id.name1,R.id.sw1,R.id.bar1,R.id.bminus1,R.id.level1,R.id.bplus1,R.id.warm1,R.id.neutral1,R.id.cool1),intArrayOf(R.id.name2,R.id.sw2,R.id.bar2,R.id.bminus2,R.id.level2,R.id.bplus2,R.id.warm2,R.id.neutral2,R.id.cool2))
  for(s in 0..1) { val r=slots[s]; val l=c.getOrNull(s)
   v.setOnClickPendingIntent(r[0],config(context,id))
   if(l==null) { v.setTextViewText(r[0],if(s==0) "Işık seçin" else "İkinci ışık"); v.setTextViewText(r[4],"—"); v.setProgressBar(r[2],100,0,false); continue }
   v.setTextViewText(r[0],l.name); v.setImageViewResource(r[1],if(l.on) R.drawable.w_switch_on else R.drawable.w_switch_off)
   v.setProgressBar(r[2],100,if(l.on) l.brightness else 0,false); v.setTextViewText(r[4],if(l.on) "%${l.brightness}" else "Kapalı")
   v.setOnClickPendingIntent(r[1],action(context,id,"toggle",l.id)); v.setOnClickPendingIntent(r[3],action(context,id,"bri",l.id,-10)); v.setOnClickPendingIntent(r[5],action(context,id,"bri",l.id,10))
   val sel=if(l.colorMode || l.scene>0) -1 else listOf(2700,4000,6500).withIndex().minBy { kotlin.math.abs(it.value-l.kelvin) }.index
   listOf(2700,4000,6500).forEachIndexed { i,k -> val view=r[6+i]
    v.setInt(view,"setBackgroundResource",if(i==sel) R.drawable.w_pill_primary else listOf(R.drawable.w_tonal_left,R.drawable.w_tonal_mid,R.drawable.w_tonal_right)[i])
    v.setTextColor(view,context.getColor(if(i==sel) R.color.w_on_primary else R.color.w_on_tonal))
    v.setOnClickPendingIntent(view,action(context,id,"kelvin",l.id,k))
   }
  }
  return v
 }

 private fun scenes(context:Context,id:Int):RemoteViews {
  val v=RemoteViews(context.packageName,R.layout.widget_scenes)
  listOf(R.id.scene0 to 0,R.id.scene1 to 1,R.id.scene2 to 2).forEach { (view,i) -> v.setOnClickPendingIntent(view,action(context,id,"scene",value=i)) }
  v.setOnClickPendingIntent(R.id.alloff,action(context,id,"alloff"))
  return v
 }

 private fun room(context:Context,id:Int,all:List<Light>):RemoteViews {
  val v=RemoteViews(context.packageName,R.layout.widget_room)
  val picked=chosen(context,id,all).filterNotNull().ifEmpty { all.take(3) }
  v.setTextViewText(R.id.count,if(all.isEmpty()) "Işık yok" else "${picked.count { it.on }} / ${picked.size} açık")
  v.setOnClickPendingIntent(R.id.header,open(context,null))
  val rows=listOf(intArrayOf(R.id.row0,R.id.shape0,R.id.name0,R.id.sub0,R.id.sw0,R.id.text0),intArrayOf(R.id.row1,R.id.shape1,R.id.name1,R.id.sub1,R.id.sw1,R.id.text1),intArrayOf(R.id.row2,R.id.shape2,R.id.name2,R.id.sub2,R.id.sw2,R.id.text2))
  rows.forEachIndexed { i,r -> val l=picked.getOrNull(i)
   if(l==null) { v.setViewVisibility(r[0],android.view.View.INVISIBLE); return@forEachIndexed }
   v.setViewVisibility(r[0],android.view.View.VISIBLE)
   v.setImageViewResource(r[1],if(l.on) R.drawable.w_shape_on else R.drawable.w_shape_off); v.setTextViewText(r[2],l.name); v.setTextViewText(r[3],status(l))
   v.setImageViewResource(r[4],if(l.on) R.drawable.w_switch_on else R.drawable.w_switch_off)
   v.setOnClickPendingIntent(r[4],action(context,id,"toggle",l.id)); v.setOnClickPendingIntent(r[5],open(context,l.id))
  }
  v.setOnClickPendingIntent(R.id.sceneA,action(context,id,"scene",value=0)); v.setOnClickPendingIntent(R.id.sceneB,action(context,id,"scene",value=1))
  return v
 }

 // Applies the change to stored state and redraws first, so the widget reacts on the same frame.
 fun handle(context:Context,op:String,lightId:String,value:Int,done:()->Unit) {
  val store=Store(context); val before=store.load()
  val targets=when(op) { "scene","alloff"->before.filter { it.online || it.ip.isNotBlank() }; else->before.filter { it.id==lightId } }
  val planned=targets.map { l -> l to when(op) {
   "toggle"->listOf("power" to if(l.on) 0 else 1)
   "bri"->listOf("brightness" to (l.brightness+value).coerceIn(if(l.brand=="wiz") 10 else 1,100))
   "kelvin"->listOf("kelvin" to if(l.brand=="tapo") value.coerceIn(2500,6500) else value)
   "alloff"->listOf("power" to 0)
   "scene"->readyScenes.getOrNull(value)?.let { r -> listOf("brightness" to if(l.brand=="wiz") r.bri.coerceAtLeast(10) else r.bri)+when { r.wiz>0 && l.brand=="wiz"->listOf("scene" to r.wiz); r.kelvin>0->listOf("kelvin" to if(l.brand=="tapo") r.kelvin.coerceIn(2500,6500) else r.kelvin); else->listOf("color" to r.hue*101+r.sat) } } ?: emptyList()
   else->emptyList() } }
  fun apply(l:Light,kind:String,v:Int)=when(kind) { "power"->l.copy(on=v==1); "brightness"->l.copy(on=true,brightness=v); "kelvin"->l.copy(on=true,kelvin=v,colorMode=false,scene=0); "color"->l.copy(on=true,hue=v/101,saturation=v%101,colorMode=true,scene=0); "scene"->l.copy(on=true,scene=v,colorMode=false); else->l }
  val after=before.map { l -> planned.find { it.first.id==l.id }?.second?.fold(l) { acc,(k,v) -> apply(acc,k,v) } ?: l }
  store.save(after); renderAll(context)
  executor.execute {
   var failed=false
   planned.forEach { (l,cmds) -> cmds.forEach { (k,v) -> try { Network.set(context,l,k,v) } catch (_:Exception) { try { Thread.sleep(300); Network.set(context,l,k,v) } catch (_:Exception) { failed=true } } } }
   if(failed) { store.save(store.load().map { cur -> before.find { it.id==cur.id && planned.any { p -> p.first.id==it.id } } ?: cur }); renderAll(context)
    android.os.Handler(android.os.Looper.getMainLooper()).post { Toast.makeText(context,"Işığa ulaşılamadı. Wi-Fi bağlantısını kontrol edin.",Toast.LENGTH_LONG).show() } }
   done()
  }
 }
}

class SingleLightWidget:AppWidgetProvider() {
 override fun onUpdate(context:Context,manager:AppWidgetManager,ids:IntArray) { Widgets.renderAll(context) }
 override fun onDeleted(context:Context,ids:IntArray) { ids.forEach { saveSelections(context,it,emptyList()) } }
}
class DualLightsWidget:AppWidgetProvider() {
 override fun onUpdate(context:Context,manager:AppWidgetManager,ids:IntArray) { Widgets.renderAll(context) }
 override fun onDeleted(context:Context,ids:IntArray) { ids.forEach { saveSelections(context,it,emptyList()) } }
 companion object { fun renderAll(context:Context)=Widgets.renderAll(context) }
}
class ScenesWidget:AppWidgetProvider() {
 override fun onUpdate(context:Context,manager:AppWidgetManager,ids:IntArray) { Widgets.renderAll(context) }
}
class RoomWidget:AppWidgetProvider() {
 override fun onUpdate(context:Context,manager:AppWidgetManager,ids:IntArray) { Widgets.renderAll(context) }
 override fun onDeleted(context:Context,ids:IntArray) { ids.forEach { saveSelections(context,it,emptyList()) } }
}

class WidgetActionReceiver:BroadcastReceiver() {
 override fun onReceive(context:Context,intent:Intent) {
  val op=intent.getStringExtra("op") ?: return
  val pending=goAsync()
  Widgets.handle(context,op,intent.getStringExtra("light") ?: "",intent.getIntExtra("value",0)) { pending.finish() }
 }
}
