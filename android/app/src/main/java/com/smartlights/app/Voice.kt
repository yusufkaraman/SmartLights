package com.smartlights.app

import android.content.Context
import android.content.Intent
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import java.util.Locale

// Spoken commands: the system speech recognizer turns speech into text (it shows its own listening UI, so the app
// needs no microphone permission), and TextToSpeech reads the assistant's answer back in Turkish.
class Voice(context:Context) {
 private val tr=Locale.forLanguageTag("tr-TR")
 @Volatile private var ready=false
 private lateinit var tts:TextToSpeech
 init {
  tts=TextToSpeech(context.applicationContext) { status ->
   ready=status==TextToSpeech.SUCCESS && tts.setLanguage(tr).let { it!=TextToSpeech.LANG_MISSING_DATA && it!=TextToSpeech.LANG_NOT_SUPPORTED }
   AppLog.i("Asistan",if(ready) "Sesli yanıt hazır" else "Türkçe sesli yanıt kullanılamıyor","status" to status)
  }
 }
 fun say(text:String) { if(ready) tts.speak(text,TextToSpeech.QUEUE_FLUSH,null,"reply") }
 fun close() { tts.stop(); tts.shutdown() }
 companion object {
  fun intent()=Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
   .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
   .putExtra(RecognizerIntent.EXTRA_LANGUAGE,"tr-TR")
   .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE,true)
   .putExtra(RecognizerIntent.EXTRA_PROMPT,"Işıklara ne yapayım?")
 }
}
