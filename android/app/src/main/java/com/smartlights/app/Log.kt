package com.smartlights.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

// In-app log. Every entry has a Turkish sentence for people and key=value fields for tools;
// it is kept on disk (JSON Lines) so earlier sessions and crashes stay readable.
object AppLog {
 data class Entry(val seq:Long, val time:Long, val session:String, val level:Char, val tag:String, val msg:String, val data:Map<String,String>) {
  fun fields()=data.entries.joinToString(" ") { (k,v) -> "$k=${if(v.any { it==' ' || it=='"' }) JSONObject.quote(v) else v}" }
  fun line()="${stamp.get()!!.format(Date(time))} $level/$tag [$session#$seq] $msg${if(data.isEmpty()) "" else " | "+fields()}"
  fun json():String { val j=JSONObject().put("t",iso.get()!!.format(Date(time))).put("ms",time).put("sid",session).put("seq",seq).put("lvl",level.toString()).put("tag",tag).put("msg",msg); if(data.isNotEmpty()) j.put("data",JSONObject(data)); return j.toString() }
  companion object {
   fun parse(s:String):Entry? = try { val j=JSONObject(s); val d=j.optJSONObject("data"); Entry(j.getLong("seq"),j.getLong("ms"),j.getString("sid"),j.getString("lvl")[0],j.getString("tag"),j.getString("msg"),d?.keys()?.asSequence()?.associateWith { d.optString(it) } ?: emptyMap()) } catch (_:Exception) { null }
  }
 }
 private val stamp=ThreadLocal.withInitial { SimpleDateFormat("MM-dd HH:mm:ss.SSS",Locale.US) }
 private val iso=ThreadLocal.withInitial { SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX",Locale.US) }
 const val MAX=4000
 private const val FILE_LIMIT=1_500_000L
 val session=java.lang.Long.toHexString(System.currentTimeMillis() and 0xFFFFFF).padStart(6,'0')
 private var seq=0L
 private val buffer=ArrayDeque<Entry>()
 val entries:SnapshotStateList<Entry> = mutableStateListOf()
 private val main=Handler(Looper.getMainLooper())
 private val writer=Executors.newSingleThreadExecutor { Thread(it,"applog").apply { isDaemon=true } }
 private var file:File?=null
 private val fileLock=Any()

 fun i(tag:String,msg:String,vararg kv:Pair<String,Any?>)=add('I',tag,msg,kv)
 fun w(tag:String,msg:String,vararg kv:Pair<String,Any?>)=add('W',tag,msg,kv)
 fun e(tag:String,msg:String,t:Throwable?=null,vararg kv:Pair<String,Any?>)=add('E',tag,msg,if(t==null) kv else arrayOf(*kv,*errorFields(t)))

 fun errorFields(t:Throwable):Array<Pair<String,Any?>> {
  val at=t.stackTrace.firstOrNull { it.className.startsWith("com.smartlights") } ?: t.stackTrace.firstOrNull()
  return arrayOf("err" to t.javaClass.simpleName,"errMsg" to (t.message ?: ""),"at" to at?.let { "${it.className.substringAfterLast('.')}.${it.methodName}:${it.lineNumber}" })
 }

 private fun add(level:Char,tag:String,msg:String,kv:Array<out Pair<String,Any?>>):Entry {
  val data=LinkedHashMap<String,String>(); kv.forEach { (k,v) -> if(v!=null) data[k]=v.toString() }
  val entry=synchronized(buffer) { Entry(++seq,System.currentTimeMillis(),session,level,tag,msg,data).also { buffer.addLast(it); while(buffer.size>MAX) buffer.removeFirst() } }
  android.util.Log.println(when(level) { 'E'->android.util.Log.ERROR; 'W'->android.util.Log.WARN; else->android.util.Log.INFO },"SmartLights/$tag",entry.line())
  writer.execute { append(entry) }
  if(Looper.myLooper()==Looper.getMainLooper()) push(entry) else main.post { push(entry) }
  return entry
 }
 private fun push(e:Entry) { entries.add(e); if(entries.size>MAX) entries.removeRange(0,entries.size-MAX) }

 private fun append(e:Entry) = synchronized(fileLock) {
  val f=file ?: return
  try { if(f.length()>FILE_LIMIT) { f.renameTo(File(f.parentFile,"log.1.jsonl")) }; f.appendText(e.json()+"\n") } catch (_:Exception) {}
 }
 fun clear() {
  synchronized(buffer) { buffer.clear() }; entries.clear()
  writer.execute { synchronized(fileLock) { file?.let { it.delete(); File(it.parentFile,"log.1.jsonl").delete() } } }
 }
 fun snapshot():List<Entry> = synchronized(buffer) { buffer.toList() }

 // Header first, so a pasted log says which phone, app build and lights it came from.
 fun header(context:Context):String {
  val pi=try { context.packageManager.getPackageInfo(context.packageName,0) } catch (_:Exception) { null }
  val lights=Store(context).load().joinToString(",") { "{\"brand\":\"${it.brand}\",\"ip\":\"${it.ip}\",\"model\":${JSONObject.quote(it.model)},\"name\":${JSONObject.quote(it.name)}}" }
  return "{\"app\":\"SmartLights\",\"version\":\"${pi?.versionName}\",\"versionCode\":${pi?.longVersionCode},\"android\":\"${android.os.Build.VERSION.RELEASE}\",\"sdk\":${android.os.Build.VERSION.SDK_INT},\"device\":\"${android.os.Build.MODEL}\",\"session\":\"$session\",\"exported\":\"${iso.get()!!.format(Date())}\",\"tapoAccount\":\"${TapoAccount.state.auth}\",\"lights\":[$lights]}"
 }
 fun humanText(context:Context,list:List<Entry>)="SmartLights kayıtları · ${android.os.Build.MODEL} · Android ${android.os.Build.VERSION.RELEASE} · oturum $session\n"+list.joinToString("\n") { it.line() }
 fun jsonText(context:Context,list:List<Entry>)=header(context)+"\n"+list.joinToString("\n") { it.json() }

 private var installed=false
 fun install(context:Context) {
  if(installed) return; installed=true
  val dir=File(context.filesDir,"logs").apply { mkdirs() }
  val f=File(dir,"log.jsonl")
  // Earlier sessions are read from disk off the main thread and slotted in before this session's lines.
  writer.execute {
   val old=try { (File(dir,"log.1.jsonl").takeIf { it.exists() }?.readLines().orEmpty()+(if(f.exists()) f.readLines() else emptyList())).takeLast(MAX).mapNotNull { Entry.parse(it) } } catch (_:Exception) { emptyList() }
   synchronized(fileLock) { file=f }
   synchronized(buffer) { buffer.addAll(0,old); while(buffer.size>MAX) buffer.removeFirst() }
   main.post { entries.addAll(0,old); if(entries.size>MAX) entries.removeRange(0,entries.size-MAX) }
  }
  val crash=File(context.filesDir,"last_crash.txt")
  val previous=Thread.getDefaultUncaughtExceptionHandler()
  Thread.setDefaultUncaughtExceptionHandler { thread,error ->
   try {
    val e=synchronized(buffer) { Entry(++seq,System.currentTimeMillis(),session,'E',"Çökme","Uygulama çöktü (${thread.name}): ${error.javaClass.simpleName}: ${error.message}",linkedMapOf("thread" to thread.name,"err" to error.javaClass.name,"errMsg" to (error.message ?: ""),"stack" to error.stackTraceToString().take(4000))) }
    append(e); crash.writeText(e.msg)
   } catch (_:Exception) {}
   previous?.uncaughtException(thread,error)
  }
  if(crash.exists()) { w("Çökme","Önceki açılışta uygulama çöktü; ayrıntı ve yığın izi önceki oturumun son kaydında.","summary" to crash.readText().take(300)); crash.delete() }
  val pi=try { context.packageManager.getPackageInfo(context.packageName,0) } catch (_:Exception) { null }
  i("Uygulama","Uygulama başladı","version" to pi?.versionName,"android" to android.os.Build.VERSION.RELEASE,"sdk" to android.os.Build.VERSION.SDK_INT,"device" to android.os.Build.MODEL)
 }
}

// Full-screen log page. Newest entries are on top, so new lines never fight the user's scrolling.
@Composable fun LogScreen(onClose:()->Unit) {
 val t=Amber
 val context=LocalContext.current
 var filter by remember { mutableIntStateOf(0) }
 val all=AppLog.entries
 val shown by remember { derivedStateOf { all.asReversed().filter { when(filter) { 1->it.level!='I'; 2->it.session==AppLog.session; else->true } } } }
 val errors by remember { derivedStateOf { all.count { it.level=='E' } } }
 var expanded by remember { mutableStateOf(setOf<Long>()) }
 var toast by remember { mutableStateOf<String?>(null) }
 LaunchedEffect(toast) { if(toast!=null) { kotlinx.coroutines.delay(2000); toast=null } }
 BackHandler(onBack=onClose)
 val red=Color(0xFFE5484D); val orange=Color(0xFFE08A00)
 Box(Modifier.fillMaxSize().background(t.bg).clickable(remember { androidx.compose.foundation.interaction.MutableInteractionSource() },null) {}) {
  Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal=16.dp).padding(top=12.dp,bottom=12.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
   Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
    Surface(onClick=onClose,shape=CircleShape,color=t.cont,modifier=Modifier.size(48.dp)) { Box(contentAlignment=Alignment.Center) { Icon(icon(P.back,2f),"Geri",tint=Ink) } }
    Column(Modifier.weight(1f)) { Text("Kayıtlar",style=ts(24,FontWeight.SemiBold,Ink)); Text("${all.size} kayıt · $errors hata · oturum ${AppLog.session}",style=ts(13,color=InkVariant)) }
   }
   ConnectedGroup(listOf("Tümü","Sorunlar","Bu açılış"),filter,t,{ filter=it },Modifier.fillMaxWidth(),height=40.dp,textSize=13)
   Surface(shape=RoundedCornerShape(20.dp),color=t.cont,modifier=Modifier.fillMaxWidth().weight(1f)) {
    if(shown.isEmpty()) Box(contentAlignment=Alignment.Center) { Text("Kayıt yok",style=ts(14,color=InkVariant)) }
    else LazyColumn(contentPadding=PaddingValues(12.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
     items(shown,key={ "${it.session}-${it.seq}" }) { e ->
      val color=when(e.level) { 'E'->red; 'W'->orange; else->InkVariant }
      val id="${e.session}-${e.seq}".hashCode().toLong()
      val open=id in expanded
      Row(Modifier.fillMaxWidth().clickable { expanded=if(id in expanded) expanded-id else expanded+id },horizontalArrangement=Arrangement.spacedBy(8.dp)) {
       Box(Modifier.padding(top=5.dp).size(8.dp).background(color,CircleShape))
       Column(verticalArrangement=Arrangement.spacedBy(2.dp)) {
        Text("${SimpleDateFormat("dd.MM HH:mm:ss",Locale.US).format(Date(e.time))} · ${e.tag}${if(e.session!=AppLog.session) " · önceki açılış" else ""}",style=ts(11,FontWeight.SemiBold,color))
        Text(e.msg,style=ts(13,color=Ink),maxLines=if(open) 50 else 3,overflow=TextOverflow.Ellipsis)
        if(e.data.isNotEmpty()) Text(e.fields(),style=ts(11,color=InkVariant).copy(fontFamily=FontFamily.Monospace,lineHeight=14.sp),maxLines=if(open) 80 else 2,overflow=TextOverflow.Ellipsis)
       }
      }
     }
    }
   }
   toast?.let { Text(it,style=ts(13,FontWeight.Medium,t.primary)) }
   Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
    FilledTonalButton(onClick={ AppLog.clear(); expanded=emptySet() },modifier=Modifier.weight(1f).height(48.dp),colors=ButtonDefaults.filledTonalButtonColors(containerColor=t.tonal,contentColor=t.onTonal)) { Text("Temizle",style=ts(14,FontWeight.SemiBold)) }
    FilledTonalButton(onClick={ val list=AppLog.snapshot().takeLast(1500); (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("SmartLights kayıtları",AppLog.humanText(context,list))); toast="Son ${list.size} kayıt panoya kopyalandı" },modifier=Modifier.weight(1f).height(48.dp),colors=ButtonDefaults.filledTonalButtonColors(containerColor=t.tonal,contentColor=t.onTonal)) { Text("Kopyala",style=ts(14,FontWeight.SemiBold)) }
    Button(onClick={ share(context) },modifier=Modifier.weight(1f).height(48.dp),colors=ButtonDefaults.buttonColors(containerColor=t.primary,contentColor=t.onPrimary)) { Text("Paylaş",style=ts(14,FontWeight.SemiBold)) }
   }
  }
 }
}

// Shares the whole log as a JSON Lines file (header line, then one entry per line).
private fun share(context:Context) {
 try {
  val dir=File(context.cacheDir,"share").apply { mkdirs() }
  val f=File(dir,"smartlights-log-${SimpleDateFormat("yyyyMMdd-HHmmss",Locale.US).format(Date())}.jsonl")
  dir.listFiles()?.forEach { it.delete() }
  f.writeText(AppLog.jsonText(context,AppLog.snapshot()))
  val uri=androidx.core.content.FileProvider.getUriForFile(context,"${context.packageName}.logs",f)
  val send=Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_STREAM,uri).putExtra(Intent.EXTRA_SUBJECT,f.name).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
  context.startActivity(Intent.createChooser(send,"Kayıtları paylaş").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
  AppLog.i("Kayıtlar","Kayıt dosyası paylaşıldı","file" to f.name,"bytes" to f.length())
 } catch (e:Exception) { AppLog.e("Kayıtlar","Kayıt dosyası paylaşılamadı",e) }
}
