package com.smartlights.app

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale

// Spoken commands inside the app, no system dialog: Android's speech recognizer (the on-device one when the phone
// has it, Pixel does) streams partial text and the microphone level into the voice bar, and TextToSpeech reads the
// assistant's answer back in Turkish. Needs the RECORD_AUDIO permission; no extra library.
class Voice(context:Context) {
 enum class Phase { Idle, Listening, Speaking }
 private val app=context.applicationContext
 private val main=Handler(Looper.getMainLooper())
 private val tr=Locale.forLanguageTag("tr-TR")

 var phase by mutableStateOf(Phase.Idle); private set
 /** Microphone loudness 0..1 while listening. */
 var level by mutableFloatStateOf(0f); private set
 /** What has been recognised so far. */
 var partial by mutableStateOf(""); private set

 private var recognizer:SpeechRecognizer?=null
 private var onDevice=false

 // --- speech in ---------------------------------------------------------------------------------------------
 fun listen(onText:(String)->Unit,onFail:(String)->Unit) { start(preferOnDevice=true,onText,onFail) }
 /** Finish now and use what was heard so far. */
 fun stop() { recognizer?.stopListening() }
 fun cancel() { recognizer?.cancel(); release(); phase=Phase.Idle; level=0f }

 private fun start(preferOnDevice:Boolean,onText:(String)->Unit,onFail:(String)->Unit) {
  release(); tts.stop()
  val local=preferOnDevice && Build.VERSION.SDK_INT>=31 && SpeechRecognizer.isOnDeviceRecognitionAvailable(app)
  if(!local && !SpeechRecognizer.isRecognitionAvailable(app)) { onFail("Bu telefonda konuşma tanıma bulunamadı"); return }
  val r=if(local) SpeechRecognizer.createOnDeviceSpeechRecognizer(app) else SpeechRecognizer.createSpeechRecognizer(app)
  recognizer=r; onDevice=local; partial=""; level=0f; phase=Phase.Listening
  AppLog.i("Asistan","Dinleme başladı (${if(local) "cihaz üzerinde" else "sistem tanıyıcı"})")
  r.setRecognitionListener(object:RecognitionListener {
   override fun onReadyForSpeech(params:Bundle?) {}
   override fun onBeginningOfSpeech() {}
   // rmsdB is roughly -2 (silence) .. 10 (loud speech)
   override fun onRmsChanged(rmsdB:Float) { level=((rmsdB+2f)/12f).coerceIn(0f,1f) }
   override fun onBufferReceived(buffer:ByteArray?) {}
   override fun onEndOfSpeech() { level=0f }
   override fun onPartialResults(b:Bundle?) { b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.takeIf { it.isNotBlank() }?.let { partial=it } }
   override fun onEvent(eventType:Int,params:Bundle?) {}
   override fun onResults(b:Bundle?) {
    val text=b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.takeIf { it.isNotBlank() } ?: partial
    release(); phase=Phase.Idle; level=0f
    if(text.isBlank()) onFail("Seni duyamadım") else { partial=text; onText(text) }
   }
   override fun onError(error:Int) {
    val wasLocal=onDevice; release(); level=0f
    // On-device Turkish not installed: ask the system to download it, and use the regular recognizer this time.
    if(wasLocal && (error==SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED || error==SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE)) {
     AppLog.w("Asistan","Cihaz üzerinde Türkçe tanıma yok, sistem tanıyıcısına geçiliyor","error" to error)
     if(Build.VERSION.SDK_INT>=33) try { SpeechRecognizer.createOnDeviceSpeechRecognizer(app).apply { triggerModelDownload(intent()); destroy() } } catch(_:Exception) {}
     main.post { start(preferOnDevice=false,onText,onFail) }; return
    }
    phase=Phase.Idle
    val msg=when(error) {
     SpeechRecognizer.ERROR_NO_MATCH,SpeechRecognizer.ERROR_SPEECH_TIMEOUT->"Seni duyamadım"
     SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS->"Mikrofon izni gerekli"
     SpeechRecognizer.ERROR_NETWORK,SpeechRecognizer.ERROR_NETWORK_TIMEOUT->"Konuşma tanıma için bağlantı gerekli"
     SpeechRecognizer.ERROR_RECOGNIZER_BUSY->"Konuşma tanıma meşgul, tekrar dene"
     else->"Dinleme durdu (hata $error)"
    }
    AppLog.w("Asistan","Sesli komut alınamadı: $msg","error" to error,"onDevice" to wasLocal)
    onFail(msg)
   }
  })
  r.startListening(intent())
 }
 private fun release() { recognizer?.destroy(); recognizer=null }

 // --- speech out --------------------------------------------------------------------------------------------
 @Volatile private var ready=false
 private val tts:TextToSpeech=TextToSpeech(app) { status ->
  ready=status==TextToSpeech.SUCCESS && ttsLanguageOk()
  AppLog.i("Asistan",if(ready) "Sesli yanıt hazır" else "Türkçe sesli yanıt kullanılamıyor","status" to status)
 }
 private fun ttsLanguageOk()=tts.setLanguage(tr).let { it!=TextToSpeech.LANG_MISSING_DATA && it!=TextToSpeech.LANG_NOT_SUPPORTED }
 init {
  tts.setOnUtteranceProgressListener(object:UtteranceProgressListener() {
   override fun onStart(id:String?) { main.post { phase=Phase.Speaking } }
   override fun onDone(id:String?) { main.post { if(phase==Phase.Speaking) phase=Phase.Idle } }
   @Deprecated("Deprecated in Java") override fun onError(id:String?) { main.post { if(phase==Phase.Speaking) phase=Phase.Idle } }
  })
 }
 fun say(text:String) { if(ready) tts.speak(text,TextToSpeech.QUEUE_FLUSH,null,"reply") }
 fun close() { release(); tts.stop(); tts.shutdown() }

 companion object {
  fun intent()=Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
   .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
   .putExtra(RecognizerIntent.EXTRA_LANGUAGE,"tr-TR")
   .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS,true)
   .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE,true)
 }
}
