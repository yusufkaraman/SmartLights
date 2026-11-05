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
  if(light.brand=="wiz") { val j=wiz(light.ip,"getPilot").getJSONObject("result"); val rgb=j.has("r") && j.optInt("temp",0)==0; val hsv=FloatArray(3); if(rgb) android.graphics.Color.RGBToHSV(j.optInt("r"),j.optInt("g"),j.optInt("b"),hsv); return light.copy(online=true,on=j.optBoolean("state"),brightness=(j.optInt("dimming",50)),hue=if(rgb) hsv[0].toInt() else light.hue,saturation=if(rgb) (hsv[1]*100).toInt() else light.saturation,kelvin=j.optInt("temp",light.kelvin).takeIf{it>0} ?: light.kelvin,colorMode=rgb,scene=j.optInt("sceneId"),rssi=j.optInt("rssi")) }
  val (email,password)=Store(context).credentials() ?: throw IllegalStateException("Tapo hesabı gerekli")
  val j=JSONObject(TapoAccount.request(context,light.ip,light.name,email,password,"get_device_info",""))
  val name=try { String(Base64.decode(j.optString("nickname"),Base64.DEFAULT)) } catch (_:Exception) { light.name }
  return light.copy(id=j.optString("device_id",light.id),name=name.ifBlank { light.name },online=true,on=j.optBoolean("device_on"),brightness=j.optInt("brightness",50),hue=j.optInt("hue"),saturation=j.optInt("saturation",100),kelvin=j.optInt("color_temp",4000).takeIf{it>0} ?: 4000,colorMode=j.optInt("color_temp",0)==0,scene=0,rssi=j.optInt("rssi"))
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
 private fun send(context:Context,light:Light,kind:String,value:Int) {
  val p=JSONObject()
  if(light.brand=="wiz") {
   when(kind) { "power"->p.put("state",value==1); "brightness"->p.put("state",true).put("dimming",value.coerceIn(10,100)); "color"->p.put("state",true).put("hue",value / 101).put("sat",value % 101); "kelvin"->p.put("state",true).put("temp",value); "scene"->p.put("state",true).put("sceneId",value).put("dimming",light.brightness.coerceIn(10,100)) }
   if(kind=="color") { val c=android.graphics.Color.HSVToColor(floatArrayOf((value/101).toFloat(),(value%101)/100f,1f)); p.remove("hue"); p.remove("sat"); p.put("r",android.graphics.Color.red(c)).put("g",android.graphics.Color.green(c)).put("b",android.graphics.Color.blue(c)) }
   wiz(light.ip,"setPilot",p)
  } else {
   val (email,password)=Store(context).credentials() ?: throw IllegalStateException("Tapo hesabını ayarlardan girin")
   when(kind) { "power"->p.put("device_on",value==1); "brightness"->p.put("brightness",value.coerceIn(1,100)); "color"->p.put("hue",value / 101).put("saturation",(value % 101).coerceAtLeast(1)).put("color_temp",0); "kelvin"->p.put("color_temp",value) }
   TapoAccount.request(context,light.ip,light.name,email,password,"set_device_info",p.toString())
  }
 }
}
