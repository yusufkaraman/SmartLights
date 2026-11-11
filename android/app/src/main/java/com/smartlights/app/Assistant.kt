package com.smartlights.app

import com.google.mlkit.genai.common.DownloadStatus
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.GenerativeModel
import com.google.mlkit.genai.prompt.SystemInstruction
import com.google.mlkit.genai.prompt.TextPart
import com.google.mlkit.genai.prompt.generateContentRequest
import kotlinx.coroutines.CancellationException
import org.json.JSONObject
import java.util.Locale

// What a typed command asks for. Null fields are left as they are.
data class Plan(val targets:List<Light>, val on:Boolean?=null, val brightness:Int?=null, val kelvin:Int?=null, val hue:Int?=null, val sat:Int=100, val scene:Ready?=null, val source:String="", val ms:Long=0) {
 val empty get()=on==null && brightness==null && kelvin==null && hue==null && scene==null
}

data class AssistantUi(val ready:Boolean=false, val status:String="Asistan hazırlanıyor…", val busy:Boolean=false, val reply:String?=null)

// Turns Turkish commands into light changes. Gemini Nano (AICore, on-device) reads the sentence when the phone has it;
// otherwise, or when the model answers with something unusable, a keyword parser does the job.
object Assistant {
 private val tr=Locale.forLanguageTag("tr")
 private var model:GenerativeModel?=null
 @Volatile var nano=false; private set

 // Color names the model may answer with, and the Turkish words the keyword parser looks for.
 private val colors=listOf(
  Triple("red",0,listOf("kırmızı","kirmizi")), Triple("orange",30,listOf("turuncu")), Triple("yellow",55,listOf("sarı","sari")),
  Triple("green",120,listOf("yeşil","yesil")), Triple("cyan",180,listOf("turkuaz","camgöbeği")), Triple("blue",225,listOf("mavi","lacivert")),
  Triple("purple",275,listOf("mor","lila")), Triple("pink",330,listOf("pembe")))

 suspend fun prepare(update:(AssistantUi)->Unit) {
  val t0=System.currentTimeMillis()
  try {
   val m=Generation.getClient(); model=m
   var status=m.checkStatus()
   AppLog.i("Asistan","Gemini Nano durumu: ${statusName(status)}","status" to status)
   if(status==FeatureStatus.DOWNLOADABLE || status==FeatureStatus.DOWNLOADING) {
    update(AssistantUi(true,"Gemini Nano indiriliyor · şimdilik basit komutlar"))
    m.download().collect { d -> when(d) {
     is DownloadStatus.DownloadProgress -> update(AssistantUi(true,"Gemini Nano indiriliyor · ${d.totalBytesDownloaded/1_048_576} MB"))
     is DownloadStatus.DownloadFailed -> AppLog.e("Asistan","Model indirilemedi",d.e)
     else -> {}
    } }
    status=m.checkStatus()
   }
   if(status==FeatureStatus.AVAILABLE) {
    m.warmup(); nano=true
    AppLog.i("Asistan","Gemini Nano hazır","model" to m.getBaseModelName(),"tokenLimit" to m.getTokenLimit(),"ms" to System.currentTimeMillis()-t0)
    update(AssistantUi(true,"Gemini Nano · cihaz üzerinde, çevrimdışı"))
   } else update(AssistantUi(true,"Bu cihazda Gemini Nano yok · basit komutlar"))
  } catch(e:CancellationException) { throw e } catch(e:Exception) {
   AppLog.e("Asistan","AICore kullanılamıyor; anahtar kelime ayrıştırıcısı kullanılacak",e)
   update(AssistantUi(true,"AICore yok · basit komutlar"))
  }
 }

 suspend fun interpret(text:String,lights:List<Light>):Plan {
  val t0=System.currentTimeMillis()
  val m=model
  if(nano && m!=null) try {
   val raw=m.generateContent(generateContentRequest(SystemInstruction(system(lights)),TextPart(text)) { temperature=0f; topK=1; maxOutputTokens=160 }).candidates.firstOrNull()?.text.orEmpty()
   // The model sometimes drops a plain number ("yüzde 60") or decorates a scene with its own white,
   // so numbers the keyword parser finds fill the gaps and a scene keeps its own colors.
   val r=rules(text,lights)
   val plan=fromJson(raw,lights)?.let { p -> if(p.targets.size==lights.size && r.targets.size<lights.size) p.copy(targets=r.targets) else p }?.let { p -> if(p.scene!=null) p.copy(kelvin=null,hue=null,brightness=r.brightness) else p.copy(brightness=p.brightness ?: r.brightness,kelvin=if(p.hue==null) p.kelvin ?: r.kelvin else null) }
    ?.copy(source="Gemini Nano",ms=System.currentTimeMillis()-t0)
   AppLog.i("Asistan","Nano cevabı${if(plan==null || plan.empty) " kullanılamadı" else ""}","input" to text,"output" to raw.replace('\n',' '),"ms" to System.currentTimeMillis()-t0)
   if(plan!=null && !plan.empty) return plan
  } catch(e:CancellationException) { throw e } catch(e:Exception) { AppLog.e("Asistan","Gemini Nano hata verdi; anahtar kelimelere düşülüyor",e,"input" to text) }
  return rules(text,lights).copy(source=if(nano) "anahtar kelime (yedek)" else "anahtar kelime",ms=System.currentTimeMillis()-t0)
 }

 // A small model follows short English rules and examples best; the user's sentence stays Turkish.
 private fun system(lights:List<Light>)="""
You control smart lights. The user writes in Turkish. Reply with ONE JSON object and nothing else:
{"lights":[numbers] or ["all"],"on":true|false|null,"brightness":1-100|null,"kelvin":2200-6500|null,"color":"red|orange|yellow|green|cyan|blue|purple|pink"|null,"scene":"<scene name>"|null}
Lights:
${lights.mapIndexed { i,l -> "${i+1}: ${l.name}" }.joinToString("\n")}
Scenes: ${readyScenes.joinToString(", ") { it.name }}
Rules: if no light is named use ["all"]. "kapat"/"söndür" means on=false. "loş" means brightness 20, "parlak" 100. "sıcak" means kelvin 2700, "soğuk"/"gün ışığı" 5500. Use a scene only when the request matches its mood. Use null for anything not asked.
Examples:
"salonu kapat" -> {"lights":[1],"on":false,"brightness":null,"kelvin":null,"color":null,"scene":null}
"her yer mavi olsun yarı parlaklıkta" -> {"lights":["all"],"on":true,"brightness":50,"kelvin":null,"color":"blue","scene":null}
""".trimIndent()

 private fun fromJson(raw:String,lights:List<Light>):Plan? {
  val start=raw.indexOf('{'); val end=raw.lastIndexOf('}')
  if(start<0 || end<=start) return null
  val j=try { JSONObject(raw.substring(start,end+1)) } catch(_:Exception) { return null }
  val ids=j.optJSONArray("lights")
  val picked=if(ids==null) emptyList() else (0 until ids.length()).mapNotNull { lights.getOrNull(ids.optInt(it,0)-1) }
  val all=ids==null || picked.isEmpty() || (0 until ids.length()).any { ids.optString(it)=="all" }
  fun int(k:String)=if(j.isNull(k) || !j.has(k)) null else j.optInt(k,-1).takeIf { it>=0 }
  val color=j.optString("color").lowercase().let { c -> colors.find { it.first==c } }
  val scene=j.optString("scene").takeIf { !j.isNull("scene") }?.let { s -> readyScenes.find { it.name.equals(s,true) } }
  return Plan(if(all) lights else picked,on=if(j.isNull("on") || !j.has("on")) null else j.optBoolean("on"),
   brightness=int("brightness")?.coerceIn(1,100),kelvin=int("kelvin")?.coerceIn(2200,6500),hue=color?.second,scene=scene)
 }

 // People often type without Turkish letters ("sicak", "yuzde"), so both sides are folded to ASCII.
 private fun fold(s:String)=s.lowercase(tr).map { when(it) { 'ı'->'i'; 'ş'->'s'; 'ğ'->'g'; 'ü'->'u'; 'ö'->'o'; 'ç'->'c'; 'â'->'a'; 'î'->'i'; 'û'->'u'; else->it } }.joinToString("")

 fun rules(text:String,lights:List<Light>):Plan {
  val s=fold(text)
  val words=Regex("[\\p{L}\\d]+").findAll(s).map { it.value }.toList()
  fun has(vararg keys:String)=keys.any { k -> fold(k).let { f -> words.any { it.startsWith(f) } } }
  // A light is named when a word of its name starts a word in the sentence ("camı" names "Cam Tapo");
  // when several match, the ones with the most matching words win ("yatak odası" over "oturma odası").
  val score=lights.associateWith { l -> fold(l.name).split(' ','_','-').filter { it.length>=3 }.count { p -> words.any { it.startsWith(p) } } }
  val best=score.values.maxOrNull() ?: 0
  val named=if(best==0) emptyList() else lights.filter { score[it]==best }
  val targets=if(named.isEmpty() || has("tüm","hepsi","bütün","her")) lights else named
  val off=has("kapat","kapa","söndür")
  val bri=Regex("(?:%|yuzde\\s*)(\\d{1,3})|(\\d{1,3})\\s*%").find(s)?.let { (it.groupValues[1].ifEmpty { it.groupValues[2] }).toInt() }
   ?: when { has("loş","kıs","az") -> 20; has("yarı") -> 50; has("parlak","full","tam") -> 100; else -> null }
  val kelvin=Regex("(\\d{4})\\s*k\\b").find(s)?.groupValues?.get(1)?.toInt()
   ?: when { has("sıcak") -> 2700; has("soğuk","gün") -> 5500; has("nötr","doğal") -> 4000; has("beyaz") -> 4000; else -> null }
  val color=colors.find { c -> c.third.any { w -> has(w) } }
  val scene=readyScenes.find { r -> has(r.name.split(' ').first()) }
  if(off) return Plan(targets,on=false)
  return Plan(targets,on=if(has("aç","yak")) true else null,brightness=bri?.coerceIn(1,100),kelvin=if(color==null) kelvin?.coerceIn(2200,6500) else null,hue=color?.second,scene=if(color==null && kelvin==null) scene else null)
 }

 fun describe(p:Plan):String {
  val who=if(p.targets.size>1) "${p.targets.size} ışık" else p.targets.firstOrNull()?.name ?: "Işık yok"
  val what=buildList {
   if(p.on==false) add("kapat") else {
    p.scene?.let { add(it.name) }
    p.brightness?.let { add("%$it") }
    p.hue?.let { h -> add(colors.find { it.second==h }?.third?.first() ?: "$h°") } ?: p.kelvin?.let { add("$it K") }
    if(isEmpty() && p.on==true) add("aç")
   }
  }
  return "$who → ${what.joinToString(" · ")}  (${p.source}, ${p.ms} ms)"
 }

 private fun statusName(s:Int)=when(s) { FeatureStatus.AVAILABLE->"hazır"; FeatureStatus.DOWNLOADABLE->"indirilebilir"; FeatureStatus.DOWNLOADING->"indiriliyor"; else->"desteklenmiyor" }
}
