package com.smartlights.app

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import bridge.Bridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Whether the saved Tapo account works, learned from every request the app sends to a Tapo bulb.
object TapoAccount {
 enum class Auth { None, Unknown, Ok, Wrong, Locked }
 data class State(val auth:Auth=Auth.None, val authAt:Long=0, val linkOk:Boolean?=null, val linkAt:Long=0, val linkMsg:String="", val device:String="")
 var state by mutableStateOf(State())
  private set
 private val main=Handler(Looper.getMainLooper())
 private fun prefs(c:Context)=c.getSharedPreferences("tapo_status",Context.MODE_PRIVATE)

 fun load(context:Context) {
  val p=prefs(context); val hasAccount=Store(context).credentials()!=null
  val auth=if(!hasAccount) Auth.None else try { Auth.valueOf(p.getString("auth","Unknown")!!) } catch (_:Exception) { Auth.Unknown }
  state=State(auth,p.getLong("authAt",0),if(p.contains("linkOk")) p.getBoolean("linkOk",false) else null,p.getLong("linkAt",0),p.getString("linkMsg","")!!,p.getString("device","")!!)
 }
 private fun update(context:Context,f:(State)->State) {
  val run={ val n=f(state); state=n; prefs(context).edit().putString("auth",n.auth.name).putLong("authAt",n.authAt).apply { if(n.linkOk==null) remove("linkOk") else putBoolean("linkOk",n.linkOk) }.putLong("linkAt",n.linkAt).putString("linkMsg",n.linkMsg).putString("device",n.device).apply() }
  if(Looper.myLooper()==Looper.getMainLooper()) run() else main.post(run)
 }
 fun set(context:Context,s:State)=update(context) { s }

 // The bridge tags its errors: [auth] wrong account, [locked] too many tries, [net] no answer, [dev] device error.
 fun kind(e:Throwable)=Regex("^\\[(\\w+)\\]").find(e.message ?: "")?.groupValues?.get(1) ?: "net"
 fun clean(e:Throwable)=(e.message ?: e.javaClass.simpleName).replace(Regex("^\\[\\w+\\]\\s*"),"").take(300)

 fun request(context:Context,ip:String,name:String,email:String,password:String,method:String,params:String,record:Boolean=true):String {
  try {
   val r=Bridge.request(ip,email,password,method,params)
   if(record) { val now=System.currentTimeMillis(); val dev="$name · ${Bridge.protocol(ip)}"
    update(context) { s -> if(s.auth!=Auth.Ok || s.linkOk!=true) AppLog.i("Tapo","Tapo hesabı doğrulandı, $name bağlandı","light" to "tapo:$ip","proto" to Bridge.protocol(ip),"method" to method); s.copy(auth=Auth.Ok,authAt=now,linkOk=true,linkAt=now,linkMsg="",device=dev) } }
   return r
  } catch (e:Exception) {
   val now=System.currentTimeMillis()
   when(kind(e)) {
    "auth"->{ AppLog.e("Tapo","$name e-posta veya şifreyi kabul etmedi",null,"light" to "tapo:$ip","method" to method,"kind" to "auth","detail" to clean(e)); if(record) update(context) { it.copy(auth=Auth.Wrong,authAt=now,linkOk=true,linkAt=now,linkMsg="") } }
    "locked"->{ AppLog.e("Tapo","$name çok fazla hatalı deneme yüzünden girişi geçici olarak kilitledi",null,"light" to "tapo:$ip","method" to method,"kind" to "locked","detail" to clean(e)); if(record) update(context) { it.copy(auth=Auth.Locked,authAt=now) } }
    "dev"->AppLog.w("Tapo","$name isteği reddetti","light" to "tapo:$ip","method" to method,"kind" to "dev","detail" to clean(e))
    else->{ AppLog.w("Tapo","$name ile bağlantı kurulamadı","light" to "tapo:$ip","method" to method,"kind" to "net","detail" to clean(e)); if(record) update(context) { it.copy(linkOk=false,linkAt=now,linkMsg=clean(e)) } }
   }
   throw e
  }
 }

 enum class Verify { Ok, Wrong, Locked, Unreachable, NoDevice }
 // Tries the account on the known Tapo bulbs (or ones found now) before it is saved.
 fun verify(context:Context,lights:List<Light>,email:String,password:String):Pair<Verify,String> {
  val targets=lights.filter { it.brand=="tapo" }.map { it.ip to it.name }.ifEmpty {
   try { val a=JSONArray(Bridge.discover("255.255.255.255")); (0 until a.length()).map { a.getJSONObject(it) }.map { it.optString("ip") to "Tapo ${it.optString("model")}" } } catch (_:Exception) { emptyList() }
  }
  if(targets.isEmpty()) { AppLog.w("Tapo","Hesap doğrulanamadı: ağda Tapo ampul bulunamadı"); return Verify.NoDevice to "" }
  var last=""
  for((ip,name) in targets) {
   Bridge.forget(ip)
   try { request(context,ip,name,email,password,"get_device_info","",record=false); return Verify.Ok to "$name · ${Bridge.protocol(ip)}" }
   catch (e:Exception) { when(kind(e)) { "auth"->return Verify.Wrong to name; "locked"->return Verify.Locked to name; else->last=clean(e) } }
  }
  return Verify.Unreachable to last
 }
}

private val clock=SimpleDateFormat("HH:mm",Locale("tr"))
private fun ago(t:Long):String { if(t==0L) return ""; val m=(System.currentTimeMillis()-t)/60000; return when { m<1->"az önce"; m<60->"$m dk önce"; m<1440->"${m/60} sa önce"; else->SimpleDateFormat("d MMM HH:mm",Locale("tr")).format(Date(t)) } }

@Composable fun TapoAccountDialog(store:Store,lights:List<Light>,onClose:()->Unit,onChanged:()->Unit) {
 val t=Amber
 val context=LocalContext.current
 val scope=rememberCoroutineScope()
 val s=TapoAccount.state
 val saved=store.credentials()
 var editing by remember { mutableStateOf(saved==null || s.auth==TapoAccount.Auth.Wrong || s.auth==TapoAccount.Auth.Locked) }
 var email by remember { mutableStateOf(saved?.first ?: "") }
 var pass by remember { mutableStateOf("") }
 var busy by remember { mutableStateOf(false) }
 var result by remember { mutableStateOf<String?>(null) }
 val good=Color(0xFF2E9E5B); val bad=Color(0xFFE5484D); val warn=Color(0xFFE08A00)
 val field=OutlinedTextFieldDefaults.colors(focusedBorderColor=t.primary,focusedLabelColor=t.primary,cursorColor=t.primary,focusedTextColor=Ink,unfocusedTextColor=Ink)

 fun save() { busy=true; result=null; scope.launch {
  val (v,info)=withContext(Dispatchers.IO) { TapoAccount.verify(context,lights,email.trim(),pass) }
  val now=System.currentTimeMillis()
  when(v) {
   TapoAccount.Verify.Ok->{ store.saveCredentials(email.trim(),pass); Bridge.forget(""); TapoAccount.set(context,TapoAccount.State(TapoAccount.Auth.Ok,now,true,now,"",info)); AppLog.i("Tapo","Hesap kaydedildi ve doğrulandı: ${email.trim()} · $info"); editing=false; pass=""; onChanged() }
   TapoAccount.Verify.Wrong->result="E-posta veya şifre yanlış. $info bu hesabı kabul etmedi; kaydedilmedi."
   TapoAccount.Verify.Locked->result="Çok fazla hatalı deneme. Ampul girişi geçici olarak kilitledi; birkaç dakika sonra tekrar deneyin."
   TapoAccount.Verify.Unreachable, TapoAccount.Verify.NoDevice->{ store.saveCredentials(email.trim(),pass); Bridge.forget(""); TapoAccount.set(context,TapoAccount.State(TapoAccount.Auth.Unknown,now,if(v==TapoAccount.Verify.Unreachable) false else null,now,info,""))
    AppLog.w("Tapo","Hesap kaydedildi ama doğrulanamadı: ${if(v==TapoAccount.Verify.NoDevice) "Tapo ampul bulunamadı" else info}"); editing=false; pass=""; onChanged() }
  }
  busy=false
 } }
 fun test() { busy=true; scope.launch { withContext(Dispatchers.IO) { val c=store.credentials(); if(c!=null) lights.filter { it.brand=="tapo" }.forEach { l -> Bridge.forget(l.ip); try { TapoAccount.request(context,l.ip,l.name,c.first,c.second,"get_device_info","") } catch (_:Exception) {} } }; busy=false } }

 AlertDialog(onDismissRequest={ if(!busy) onClose() },containerColor=t.cont,titleContentColor=Ink,textContentColor=Ink,title={Text("Tapo hesabı")},
  text={ Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
   val (color,title,sub)=when(s.auth) {
    TapoAccount.Auth.Ok->Triple(good,"Hesap bağlı","${saved?.first ?: ""}\nDoğrulandı ${ago(s.authAt)}${if(s.device.isNotBlank()) " · ${s.device}" else ""}")
    TapoAccount.Auth.Wrong->Triple(bad,"Şifre veya e-posta yanlış","Ampul kayıtlı hesabı ${ago(s.authAt)} reddetti. Bilgileri yeniden girin.")
    TapoAccount.Auth.Locked->Triple(bad,"Ampul girişi kilitledi","Çok fazla hatalı deneme (${ago(s.authAt)}). Birkaç dakika bekleyin.")
    TapoAccount.Auth.Unknown->Triple(warn,"Hesap kayıtlı, doğrulanmadı","${saved?.first ?: ""}\nHenüz bir Tapo ampule bağlanılamadı.")
    TapoAccount.Auth.None->Triple(InkVariant,"Hesap girilmedi","Tapo ampulleri kontrol etmek için Tapo uygulamasındaki hesabınızı girin.")
   }
   Surface(shape=RoundedCornerShape(20.dp),color=color.copy(alpha=.14f),modifier=Modifier.fillMaxWidth()) {
    Row(Modifier.padding(14.dp),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
     Box(Modifier.padding(top=4.dp).size(12.dp).background(color,CircleShape))
     Column(verticalArrangement=Arrangement.spacedBy(2.dp)) { Text(title,style=ts(17,FontWeight.SemiBold,Ink)); Text(sub,style=ts(13,color=InkVariant)) }
    }
   }
   if(s.auth!=TapoAccount.Auth.None && s.linkOk!=null) Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
    Box(Modifier.size(8.dp).background(if(s.linkOk) good else warn,CircleShape))
    Text(if(s.linkOk) "Ampul bağlantısı: bağlı · ${ago(s.linkAt)}" else "Ampul bağlantısı koptu · ${ago(s.linkAt)}",style=ts(13,FontWeight.Medium,Ink))
   }
   if(s.linkOk==false && s.linkMsg.isNotBlank()) Text(s.linkMsg,style=ts(11,color=InkVariant).copy(fontFamily=FontFamily.Monospace))
   val recent=AppLog.entries.filter { it.tag=="Tapo" }.takeLast(5)
   if(recent.isNotEmpty()) {
    Text("Son Tapo kayıtları",style=ts(13,FontWeight.SemiBold,InkVariant))
    Surface(shape=RoundedCornerShape(14.dp),color=t.bg,modifier=Modifier.fillMaxWidth()) { Column(Modifier.padding(10.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
     recent.forEach { e -> Text("${clock.format(Date(e.time))}  ${e.msg}",style=ts(11,color=when(e.level) { 'E'->bad; 'W'->warn; else->Ink }).copy(fontFamily=FontFamily.Monospace),maxLines=3) }
    } }
   }
   if(editing) {
    OutlinedTextField(email,{email=it; result=null},label={Text("E-posta")},singleLine=true,enabled=!busy,colors=field,modifier=Modifier.fillMaxWidth())
    OutlinedTextField(pass,{pass=it; result=null},label={Text("Şifre")},singleLine=true,enabled=!busy,visualTransformation=PasswordVisualTransformation(),colors=field,modifier=Modifier.fillMaxWidth())
    Text("Bilgiler bu telefonda şifreli saklanır. Kaydetmeden önce bir Tapo ampulde denenir.",style=ts(12,color=InkVariant))
   }
   result?.let { Text(it,style=ts(13,FontWeight.Medium,bad)) }
   if(busy) Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) { CircularProgressIndicator(Modifier.size(18.dp),color=t.primary,strokeWidth=2.dp); Text(if(editing) "Ampulde doğrulanıyor…" else "Test ediliyor…",style=ts(13,color=InkVariant)) }
   if(!editing) Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
    FilledTonalButton(onClick={test()},enabled=!busy,modifier=Modifier.weight(1f),colors=ButtonDefaults.filledTonalButtonColors(containerColor=t.tonal,contentColor=t.onTonal)) { Text("Test et",style=ts(14,FontWeight.SemiBold)) }
    FilledTonalButton(onClick={editing=true},enabled=!busy,modifier=Modifier.weight(1f),colors=ButtonDefaults.filledTonalButtonColors(containerColor=t.tonal,contentColor=t.onTonal)) { Text("Değiştir",style=ts(14,FontWeight.SemiBold)) }
   }
   if(!editing && saved!=null) TextButton(onClick={ store.clearCredentials(); Bridge.forget(""); TapoAccount.set(context,TapoAccount.State()); AppLog.i("Tapo","Hesap kaldırıldı"); editing=true; email=""; onChanged() },enabled=!busy) { Text("Hesabı kaldır",color=bad) }
  } },
  confirmButton={ if(editing) TextButton(onClick={save()},enabled=!busy && email.isNotBlank() && pass.isNotEmpty()) { Text("Kaydet ve doğrula",color=t.primary) } else TextButton(onClick=onClose,enabled=!busy) { Text("Kapat",color=t.primary) } },
  dismissButton={ if(editing) TextButton(onClick={ if(saved!=null) { editing=false; result=null; pass="" } else onClose() },enabled=!busy) { Text("Vazgeç",color=Ink) } })
}
