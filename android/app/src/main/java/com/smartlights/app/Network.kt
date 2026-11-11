package com.smartlights.app

import android.content.Context
import android.net.wifi.WifiManager
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import bridge.Bridge
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.net.*
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlin.math.roundToInt

// WiZ output floor and the color temperatures of its warm and cold white LEDs (getModelConfig cctRange).
const val WIZ_FLOOR=10
const val WIZ_WARM=2700
const val WIZ_COLD=6500

data class Light(val id:String, val ip:String, val name:String, val brand:String, val model:String="", val online:Boolean=false, val on:Boolean=false, val brightness:Int=50, val hue:Int=0, val saturation:Int=100, val kelvin:Int=4000, val colorMode:Boolean=false, val scene:Int=0, val rssi:Int=0) {
 fun json() = JSONObject().put("id",id).put("ip",ip).put("name",name).put("brand",brand).put("model",model).put("on",on).put("brightness",brightness).put("hue",hue).put("saturation",saturation).put("kelvin",kelvin).put("colorMode",colorMode).put("scene",scene)
 companion object { fun from(j:JSONObject)=Light(j.optString("id",j.optString("ip")),j.optString("ip"),j.optString("name"),j.optString("brand"),j.optString("model"),false,j.optBoolean("on"),j.optInt("brightness",50),j.optInt("hue"),j.optInt("saturation",100),j.optInt("kelvin",4000),j.optBoolean("colorMode"),j.optInt("scene")) }
}

fun Light.ref()="$brand:$ip"
data class Usage(val minutes:IntArray, val wh:IntArray, val savedWh:IntArray)
data class Diagnosis(val ip:String, val rssi:Int?, val pingMs:Long?, val ok:Boolean)

class Store(val context:Context) {
 private val prefs=context.getSharedPreferences("lights",Context.MODE_PRIVATE)
 private fun key():SecretKey {
  val ks=KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
  (ks.getKey("smartlights",null) as? SecretKey)?.let { return it }
  return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore").apply { init(KeyGenParameterSpec.Builder("smartlights",KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build()) }.generateKey()
 }
 fun saveCredentials(email:String,password:String) {
  val cipher=Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE,key())
  val blob=cipher.iv+cipher.doFinal(JSONObject().put("email",email).put("password",password).toString().toByteArray())
  prefs.edit().putString("credentials",Base64.encodeToString(blob,Base64.NO_WRAP)).apply()
 }
 fun credentials():Pair<String,String>? = try { val blob=Base64.decode(prefs.getString("credentials",null),Base64.NO_WRAP); val c=Cipher.getInstance("AES/GCM/NoPadding"); c.init(Cipher.DECRYPT_MODE,key(),GCMParameterSpec(128,blob.copyOfRange(0,12))); val j=JSONObject(String(c.doFinal(blob.copyOfRange(12,blob.size)))); j.getString("email") to j.getString("password") } catch (_:Exception){null}
 fun clearCredentials() { prefs.edit().remove("credentials").apply() }
 fun save(lights:List<Light>) { prefs.edit().putString("devices",JSONArray().apply { lights.forEach { put(it.json()) } }.toString()).apply() }
 fun saveScene(name:String,lights:List<Light>) { val all=scenes(); all.put(name,JSONArray().apply { lights.forEach { put(it.json()) } }); prefs.edit().putString("scenes",all.toString()).apply() }
 fun scenes():JSONObject = try { JSONObject(prefs.getString("scenes","{}")) } catch (_:Exception) { JSONObject() }
 fun removeScene(name:String) { val all=scenes(); all.remove(name); prefs.edit().putString("scenes",all.toString()).apply() }
 fun load():List<Light> = try { val a=JSONArray(prefs.getString("devices","[]")); (0 until a.length()).map { Light.from(a.getJSONObject(it)) }.distinctBy { it.id }.distinctBy { it.ip } } catch (_:Exception){ emptyList() }
 fun names():Map<String,String> = try { val j=JSONObject(prefs.getString("names","{}")); j.keys().asSequence().associateWith { j.getString(it) } } catch (_:Exception) { emptyMap() }
 fun setName(id:String,name:String) { prefs.edit().putString("names",JSONObject(names()+(id to name)).toString()).apply() }
 fun named(lights:List<Light>):List<Light> { val n=names(); return lights.map { l -> n[l.id]?.let { l.copy(name=it) } ?: l } }
 // Lights removed from the list stay hidden even when discovery finds them again.
 fun hidden():Set<String> = prefs.getStringSet("hidden",emptySet()) ?: emptySet()
 fun hide(light:Light) { prefs.edit().putStringSet("hidden",hidden()+light.id+light.ip).apply(); save(load().filter { it.id!=light.id }) }
 fun unhideAll() { prefs.edit().remove("hidden").apply() }
 fun favorites():Set<String> = prefs.getStringSet("favorites",emptySet()) ?: emptySet()
 fun setFavorite(id:String,on:Boolean) { prefs.edit().putStringSet("favorites",if(on) favorites()+id else favorites()-id).apply() }
 fun presets():List<Pair<String,Int>> = try { val a=JSONArray(prefs.getString("presets","[]")); (0 until a.length()).map { a.getJSONObject(it).let { j -> j.getString("name") to j.getInt("k") } } } catch (_:Exception) { emptyList() }
 fun savePresets(list:List<Pair<String,Int>>) { prefs.edit().putString("presets",JSONArray().apply { list.forEach { put(JSONObject().put("name",it.first).put("k",it.second)) } }.toString()).apply() }
 fun int(key:String,default:Int=0) = prefs.getInt(key,default)
 fun putInt(key:String,value:Int) { prefs.edit().putInt(key,value).apply() }
 fun long(key:String) = prefs.getLong(key,0L)
 fun putLong(key:String,value:Long) { prefs.edit().putLong(key,value).apply() }
 fun flag(key:String) = prefs.getBoolean(key,false)
 fun putFlag(key:String,value:Boolean) { prefs.edit().putBoolean(key,value).apply() }
}

object Network {
 // UDP drops packets now and then: resend up to three times before calling the bulb unreachable.
 private fun wiz(ip:String,method:String,params:JSONObject=JSONObject(),timeout:Int=900,tries:Int=3):JSONObject {
  val data=JSONObject().put("method",method).put("params",params).toString().toByteArray()
  DatagramSocket().use { s -> s.soTimeout=timeout
   for(attempt in 1..tries) {
    s.send(DatagramPacket(data,data.size,InetAddress.getByName(ip),38899))
    try { val packet=DatagramPacket(ByteArray(8192),8192); s.receive(packet); if(attempt>1) AppLog.w("WiZ","Ampul ancak $attempt. denemede yanıt verdi (Wi-Fi paket kaybı)","ip" to ip,"method" to method,"attempt" to attempt,"timeoutMs" to timeout); return JSONObject(String(packet.data,0,packet.length)) }
    catch (e:SocketTimeoutException) { if(attempt==tries) throw SocketTimeoutException("$ip $method: $tries denemede (${tries*timeout} ms) yanıt yok") }
   }
  }
  throw SocketTimeoutException(ip)
 }
 private fun broadcast(context:Context):String = try { val wifi=context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager; val info=wifi.dhcpInfo; val ip=info.ipAddress or info.netmask.inv(); listOf(ip and 255,ip shr 8 and 255,ip shr 16 and 255,ip shr 24 and 255).joinToString(".") } catch (_:Exception){"255.255.255.255"}
 suspend fun scan(context:Context,old:List<Light>):List<Light> = coroutineScope {
  val wifi=context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
  val lock=wifi.createMulticastLock("smartlights-discovery").apply { setReferenceCounted(false); acquire() }
  try {
   val target=broadcast(context)
   val started=System.currentTimeMillis()
   AppLog.i("Tarama","Ağ taraması başladı","broadcast" to target,"saved" to old.joinToString(",") { it.ref() })
   val wizJob=async(Dispatchers.IO) { discoverWiz(target) }
   val tapoJob=async(Dispatchers.IO) { try { val a=JSONArray(Bridge.discover(target)); (0 until a.length()).map { a.getJSONObject(it) }.map { j -> Light(j.optString("id").ifBlank { j.optString("ip") },j.optString("ip"),j.optString("name").ifBlank { "Tapo ${j.optString("model")}" },"tapo",j.optString("model")) } } catch (e:Exception) { AppLog.w("Tarama","Tapo keşfi başarısız oldu; kayıtlı IP'ler yine denenecek",*AppLog.errorFields(e)); emptyList() } }
   val wizFound=wizJob.await(); val tapoFound=tapoJob.await()
   AppLog.i("Tarama","Keşif: ${wizFound.size} WiZ ve ${tapoFound.size} Tapo ampul yanıt verdi","wiz" to wizFound.joinToString(",") { "${it.ip}/${it.id}" },"tapo" to tapoFound.joinToString(",") { "${it.ip}/${it.model}" },"ms" to System.currentTimeMillis()-started)
   val found=wizFound+tapoFound
   // Two different lights can't share an IP; discovery and get_device_info may report different ids for one Tapo bulb.
   val merged=(found+old.filter { oldLight -> found.none { it.id==oldLight.id || it.ip==oldLight.ip } }).distinctBy { it.id }.distinctBy { it.ip }
   val result=merged.map { light -> async(Dispatchers.IO) { val t0=System.currentTimeMillis(); try { state(context,light).also { logState(it,System.currentTimeMillis()-t0) } } catch (e:Exception) { AppLog.e("Durum","${light.name} yanıt vermedi; çevrimdışı gösteriliyor",e,"light" to light.ref(),"ms" to System.currentTimeMillis()-t0); light.copy(online=false) } } }.awaitAll()
    .sortedByDescending { it.online }.distinctBy { it.id }.distinctBy { it.ip }
   AppLog.i("Tarama","Tarama bitti: ${result.count { it.online }}/${result.size} ışık bağlı","online" to result.filter { it.online }.joinToString(",") { it.ref() },"offline" to result.filter { !it.online }.joinToString(",") { it.ref() }.ifEmpty { null },"ms" to System.currentTimeMillis()-started)
   result
  } finally { if(lock.isHeld) lock.release() }
 }
 private fun discoverWiz(target:String):List<Light> {
  val lights=mutableListOf<Light>()
  try { DatagramSocket().use { s -> s.broadcast=true; s.soTimeout=700; val data="{\"method\":\"getSystemConfig\",\"params\":{}}".toByteArray(); for (host in setOf(target,"255.255.255.255")) s.send(DatagramPacket(data,data.size,InetAddress.getByName(host),38899)); val end=System.currentTimeMillis()+3000; while(System.currentTimeMillis()<end) { try { val packet=DatagramPacket(ByteArray(8192),8192); s.receive(packet); val j=JSONObject(String(packet.data,0,packet.length)).optJSONObject("result") ?: continue; val ip=packet.address.hostAddress ?: continue; val id=j.optString("mac").ifBlank { ip }; lights.add(Light(id,ip,j.optString("moduleName").ifBlank { "WiZ $ip" },"wiz",j.optString("moduleName"))) } catch (_:SocketTimeoutException) {} } } } catch (_:Exception) {}
  return lights.distinctBy { it.id }
 }
 fun state(context:Context,light:Light):Light {
  if(light.brand=="wiz") {
   val j=wiz(light.ip,"getPilot").getJSONObject("result")
   val r=j.optInt("r"); val g=j.optInt("g"); val b=j.optInt("b"); val c=j.optInt("c"); val w=j.optInt("w")
   // Sub-10% white is sent as raw warm/cold channels (see wizPilot), which the bulb reports back without "temp".
   val raw=j.optInt("temp",0)==0 && r+g+b==0 && c+w>0
   val rgb=!raw && j.has("r") && j.optInt("temp",0)==0
   val hsv=FloatArray(3); if(rgb) android.graphics.Color.RGBToHSV(r,g,b,hsv)
   // Scenes such as Night light leave "dimming" out; keep the last known level instead of jumping to 50.
   val dim=j.optInt("dimming",light.brightness)
   val bri=when { raw -> (dim*(c+w)/255f).roundToInt(); rgb && dim<=WIZ_FLOOR -> (dim*maxOf(r,g,b)/255f).roundToInt(); else -> dim }.coerceIn(1,100)
   val kelvin=if(raw) (WIZ_WARM+(WIZ_COLD-WIZ_WARM)*c/(c+w))/100*100 else j.optInt("temp",light.kelvin).takeIf{it>0} ?: light.kelvin
   return light.copy(online=true,on=j.optBoolean("state"),brightness=bri,hue=if(rgb) hsv[0].toInt() else light.hue,saturation=if(rgb) (hsv[1]*100).toInt() else light.saturation,kelvin=if(raw && light.kelvin<WIZ_WARM) light.kelvin else kelvin,colorMode=rgb,scene=j.optInt("sceneId"),rssi=j.optInt("rssi"))
  }
  val (email,password)=Store(context).credentials() ?: throw IllegalStateException("Tapo hesabı gerekli")
  val j=JSONObject(TapoAccount.request(context,light.ip,light.name,email,password,"get_device_info",""))
  val name=try { String(Base64.decode(j.optString("nickname"),Base64.DEFAULT)) } catch (_:Exception) { light.name }
  return light.copy(id=j.optString("device_id",light.id),name=name.ifBlank { light.name },online=true,on=j.optBoolean("device_on"),brightness=j.optInt("brightness",50),hue=j.optInt("hue"),saturation=j.optInt("saturation",100),kelvin=j.optInt("color_temp",4000).takeIf{it>0} ?: 4000,colorMode=j.optInt("color_temp",0)==0,scene=0,rssi=j.optInt("rssi"))
 }
 // Logs what a bulb really shows after a mode or color change: raw kelvin / rgb / brightness from the bulb itself,
 // so WiZ and Tapo can be compared side by side in the log screen.
 fun audit(context:Context,light:Light,mode:String) {
  try {
   if(light.brand=="wiz") {
    val j=wiz(light.ip,"getPilot").getJSONObject("result"); val s=state(context,light)
    AppLog.i("Doğrulama","$mode → ${light.name}: ${if(!s.on) "kapalı" else if(s.colorMode) "renk ${s.hue}°/%${s.saturation}" else if(s.scene>0) "sahne #${s.scene}" else "${s.kelvin} K"}, %${s.brightness}","light" to light.ref(),"mode" to mode,
     "on" to s.on,"bri" to s.brightness,"dimming" to j.opt("dimming"),"temp" to j.opt("temp"),"r" to j.opt("r"),"g" to j.opt("g"),"b" to j.opt("b"),"c" to j.opt("c"),"w" to j.opt("w"),"scene" to j.opt("sceneId"),"rssi" to j.opt("rssi"))
   } else {
    val (email,password)=Store(context).credentials() ?: return
    val j=JSONObject(TapoAccount.request(context,light.ip,light.name,email,password,"get_device_info",""))
    val ct=j.optInt("color_temp"); val on=j.optBoolean("device_on")
    val rgb=if(ct==0) android.graphics.Color.HSVToColor(floatArrayOf(j.optInt("hue").toFloat(),j.optInt("saturation")/100f,1f)) else null
    AppLog.i("Doğrulama","$mode → ${light.name}: ${if(!on) "kapalı" else if(ct==0) "renk ${j.optInt("hue")}°/%${j.optInt("saturation")}" else "$ct K"}, %${j.optInt("brightness")}","light" to light.ref(),"mode" to mode,
     "on" to on,"bri" to j.opt("brightness"),"temp" to (if(ct>0) ct else null),"hue" to j.opt("hue"),"sat" to j.opt("saturation"),
     "r" to rgb?.let { android.graphics.Color.red(it) },"g" to rgb?.let { android.graphics.Color.green(it) },"b" to rgb?.let { android.graphics.Color.blue(it) },"rssi" to j.opt("rssi"))
   }
  } catch (e:Exception) { AppLog.w("Doğrulama","$mode → ${light.name}: durum okunamadı",*AppLog.errorFields(e),"light" to light.ref()) }
 }
 fun usage(context:Context,light:Light):Usage? {
  if(light.brand!="tapo") return null
  val (email,password)=Store(context).credentials() ?: return null
  val j=JSONObject(TapoAccount.request(context,light.ip,light.name,email,password,"get_device_usage",""))
  fun triple(key:String)=j.optJSONObject(key)?.let { intArrayOf(it.optInt("today"),it.optInt("past7"),it.optInt("past30")) } ?: intArrayOf(0,0,0)
  return Usage(triple("time_usage"),triple("power_usage"),triple("saved_power"))
 }
 fun diagnose(context:Context,light:Light):Diagnosis {
  val start=System.nanoTime()
  return try { val s=state(context,light); Diagnosis(light.ip,s.rssi.takeIf{it!=0},(System.nanoTime()-start)/1_000_000,true) } catch (_:Exception) { Diagnosis(light.ip,null,null,false) }
 }
 // Steps a change over `ms` instead of jumping; the last step sends the exact target. Tapo fades power
 // on the bulb itself (set_on_off_gradually_info); WiZ has no local setting for it, so its power is faded here.
 suspend fun fade(context:Context,from:Light,kind:String,value:Int,ms:Int,now:()->Light={ from }) {
  fun lerp(a:Int,b:Int,f:Float)=(a+(b-a)*f).toInt()
  val min=1
  val target=from.brightness.coerceIn(min,100)
  var after:Pair<String,Int>?=null
  val step:((Float)->Pair<String,Int>)?=when {
   kind=="brightness" && from.on -> { f -> "brightness" to lerp(from.brightness,value,f) }
   kind=="kelvin" && from.on && !from.colorMode && from.scene==0 -> { f -> "kelvin" to lerp(from.kelvin,value,f)/50*50 }
   kind=="color" && from.on && from.colorMode -> { val h0=from.hue; val d=((value/101-h0+540)%360)-180; val s0=from.saturation; { f -> "color" to ((h0+(d*f).toInt()+360)%360)*101+lerp(s0,value%101,f).coerceIn(1,100) } }
   kind=="power" && from.brand=="wiz" && value==1 && !from.on -> { send(context,now(),"brightness",min); { f -> "brightness" to lerp(min,target,f) } }
   kind=="power" && from.brand=="wiz" && value==0 && from.on -> { after="power" to 0; { f -> "brightness" to lerp(target,min,f) } }
   else -> null
  }
  // Tapo: every step is a KLAP request of ~300 ms, so a stepped fade is slow and jerky; send the change once.
  if(step==null || from.brand=="tapo") { set(context,now(),kind,value); return }
  val t0=System.currentTimeMillis(); var n=0
  try {
   while(true) {
    currentCoroutineContext().ensureActive()
    val f=((System.currentTimeMillis()-t0).toFloat()/ms).coerceAtMost(1f)
    val (k,v)=step(f); val s=System.currentTimeMillis(); send(context,now(),k,v); n++
    if(f>=1f) break
    delay((70-(System.currentTimeMillis()-s)).coerceAtLeast(10))
   }
   after?.let { (k,v) -> send(context,now(),k,v) }
   AppLog.i("Geçiş","${from.name}: ${describe(kind,value)} (${ms/1000} sn geçiş)","light" to from.ref(),"kind" to kind,"value" to value,"steps" to n,"ms" to System.currentTimeMillis()-t0)
  } catch (e:CancellationException) { AppLog.i("Geçiş","${from.name}: geçiş yeni bir komutla kesildi","light" to from.ref(),"kind" to kind,"steps" to n); throw e }
  catch (e:Exception) { AppLog.e("Geçiş","${from.name}: geçiş yarıda kaldı, son değer doğrudan gönderiliyor",e,"light" to from.ref(),"kind" to kind,"steps" to n); set(context,now(),kind,value) }
 }

 // Tapo bulbs fade their own power on and off; the duration is whole seconds.
 fun applyPowerFade(context:Context,light:Light,ms:Int) {
  if(light.brand!="tapo") return
  val (email,password)=Store(context).credentials() ?: return
  val secs=ms/1000; val on=secs>0
  fun call(method:String,params:String)=TapoAccount.request(context,light.ip,light.name,email,password,method,params)
  fun state()=JSONObject().put("enable",on).apply { if(on) put("duration",secs) }
  try { call("set_on_off_gradually_info",JSONObject().put("on_state",state()).toString()); call("set_on_off_gradually_info",JSONObject().put("off_state",state()).toString()) }
  catch (e:Exception) { if(TapoAccount.kind(e)!="dev") throw e; call("set_on_off_gradually_info",JSONObject().put("enable",on).toString()) }
  val info=try { call("get_on_off_gradually_info","") } catch (_:Exception) { "" }
  Store(context).putInt("fade_applied_${light.id}",ms)
  AppLog.i("Geçiş","${light.name}: açma/kapama geçişi ${if(on) "$secs sn" else "kapalı"}","light" to light.ref(),"secs" to secs,"device" to info)
 }

 fun set(context:Context,light:Light,kind:String,value:Int) {
  val t0=System.currentTimeMillis()
  try { send(context,light,kind,value); AppLog.i("Komut","${light.name}: ${describe(kind,value)}","light" to light.ref(),"kind" to kind,"value" to value,"ms" to System.currentTimeMillis()-t0) }
  catch (e:Exception) { AppLog.e("Komut","${light.name}: ${describe(kind,value)} gönderilemedi",e,"light" to light.ref(),"kind" to kind,"value" to value,"ms" to System.currentTimeMillis()-t0); throw e }
 }
 private fun describe(kind:String,value:Int)=when(kind) { "color"->"renk (ton ${value/101}°, doygunluk %${value%101})"; "kelvin"->"beyaz $value K"; "brightness"->"parlaklık %$value"; "power"->if(value==1) "açıldı" else "kapatıldı"; "scene"->"sahne #$value"; else->"$kind=$value" }
 private fun logState(l:Light,ms:Long) {
  val mode=if(l.scene>0) "sahne #${l.scene}" else if(l.colorMode) "renk ${l.hue}°/%${l.saturation}" else "${l.kelvin} K"
  AppLog.i("Durum","${l.name} bağlı: ${if(l.on) "açık, %${l.brightness}, $mode" else "kapalı"}","light" to l.ref(),"on" to l.on,"bri" to l.brightness,
   "mode" to (if(l.scene>0) "scene" else if(l.colorMode) "color" else "white"),"kelvin" to (if(!l.colorMode && l.scene==0) l.kelvin else null),
   "hue" to (if(l.colorMode) l.hue else null),"sat" to (if(l.colorMode) l.saturation else null),"scene" to (if(l.scene>0) l.scene else null),
   "rssi" to (if(l.rssi!=0) l.rssi else null),"ms" to ms,"proto" to (if(l.brand=="tapo") Bridge.protocol(l.ip) else "udp"))
 }
 // WiZ firmware has a 10% output floor (getModelConfig minDimLevel=10), so "dimming" 1-9 looks the same as 10.
 // Below it, hold dimming at 10 and scale the raw LED channels instead: warm/cold for white, r/g/b for color.
 private fun lowPilot(l:Light):JSONObject {
  val total=255f*l.brightness.coerceIn(1,WIZ_FLOOR)/WIZ_FLOOR
  val p=JSONObject().put("state",true).put("dimming",WIZ_FLOOR)
  if(l.colorMode) { val c=android.graphics.Color.HSVToColor(floatArrayOf(l.hue.toFloat(),l.saturation/100f,1f)); fun ch(v:Int)=(v*total/255f).roundToInt()
   return p.put("r",ch(android.graphics.Color.red(c))).put("g",ch(android.graphics.Color.green(c))).put("b",ch(android.graphics.Color.blue(c))).put("c",0).put("w",0) }
  val f=((l.kelvin-WIZ_WARM).toFloat()/(WIZ_COLD-WIZ_WARM)).coerceIn(0f,1f)
  val cold=(total*f).roundToInt(); val warm=(total.roundToInt()-cold).coerceAtLeast(if(cold==0) 1 else 0)
  return p.put("w",warm).put("c",cold)
 }
 private fun fullPilot(l:Light):JSONObject {
  val p=JSONObject().put("state",true).put("dimming",l.brightness.coerceIn(WIZ_FLOOR,100))
  if(!l.colorMode) return p.put("temp",l.kelvin.coerceIn(2200,6500))
  val c=android.graphics.Color.HSVToColor(floatArrayOf(l.hue.toFloat(),l.saturation/100f,1f))
  return p.put("r",android.graphics.Color.red(c)).put("g",android.graphics.Color.green(c)).put("b",android.graphics.Color.blue(c))
 }
 private fun send(context:Context,light:Light,kind:String,value:Int) {
  val p=JSONObject()
  if(light.brand=="wiz") {
   val next=when(kind) { "brightness"->light.copy(brightness=value); "kelvin"->light.copy(kelvin=value,colorMode=false,scene=0); "color"->light.copy(hue=value/101,saturation=value%101,colorMode=true,scene=0); "power"->light; else->null }
   if(next!=null && next.scene==0 && next.brightness<WIZ_FLOOR && (kind!="power" || value==1)) { wiz(light.ip,"setPilot",lowPilot(next)); return }
   // At 10% and above send the whole white/color state, never "dimming" alone: after a sub-10% command the bulb
   // is still on the scaled raw channels, and dimming by itself would keep it that dim even at 100%.
   if(next!=null && next.scene==0 && kind!="power") { wiz(light.ip,"setPilot",fullPilot(next)); return }
   when(kind) { "power"->{ if(value==1 && Store(context).int("fade_ms")>0 && light.scene==0) { wiz(light.ip,"setPilot",fullPilot(light)); return }; p.put("state",value==1); if(value==1 && Store(context).int("fade_ms")>0) p.put("dimming",light.brightness.coerceIn(1,100)) }; "brightness"->p.put("state",true).put("dimming",value.coerceIn(1,100)); "color"->p.put("state",true).put("hue",value / 101).put("sat",value % 101); "kelvin"->p.put("state",true).put("temp",value); "scene"->p.put("state",true).put("sceneId",value).put("dimming",light.brightness.coerceIn(1,100)) }
   if(kind=="color") { val c=android.graphics.Color.HSVToColor(floatArrayOf((value/101).toFloat(),(value%101)/100f,1f)); p.remove("hue"); p.remove("sat"); p.put("r",android.graphics.Color.red(c)).put("g",android.graphics.Color.green(c)).put("b",android.graphics.Color.blue(c)) }
   wiz(light.ip,"setPilot",p)
  } else {
   val (email,password)=Store(context).credentials() ?: throw IllegalStateException("Tapo hesabını ayarlardan girin")
   when(kind) { "power"->p.put("device_on",value==1); "brightness"->p.put("brightness",value.coerceIn(1,100)); "color"->p.put("hue",value / 101).put("saturation",(value % 101).coerceAtLeast(1)).put("color_temp",0); "kelvin"->p.put("color_temp",value) }
   TapoAccount.request(context,light.ip,light.name,email,password,"set_device_info",p.toString())
  }
 }
}
