package com.smartlights.app

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import java.util.Calendar
import java.util.concurrent.Executors

private val worker=Executors.newSingleThreadExecutor()

object Timers {
 private fun intent(context:Context,id:String)=PendingIntent.getBroadcast(context,id.hashCode(),Intent(context,TimerReceiver::class.java).setData(Uri.parse("smartlights://timer/${Uri.encode(id)}")).putExtra("id",id),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
 fun schedule(context:Context,light:Light,minutes:Int) {
  val am=context.getSystemService(AlarmManager::class.java); val store=Store(context); val pi=intent(context,light.id)
  am.cancel(pi)
  if(minutes<=0) { store.putLong("timer_${light.id}",0); return }
  val at=System.currentTimeMillis()+minutes*60_000L
  am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,at,pi)
  store.putLong("timer_${light.id}",at); store.putInt("timer_min_${light.id}",minutes)
 }
}

class TimerReceiver:BroadcastReceiver() {
 override fun onReceive(context:Context,intent:Intent) {
  val id=intent.getStringExtra("id") ?: return; val pending=goAsync()
  worker.execute { try { val store=Store(context); store.putLong("timer_$id",0); store.load().find { it.id==id }?.let { Network.set(context,it,"power",0) } } catch (_:Exception) {} finally { pending.finish() } }
 }
}

// Warm in the evening, cool around midday; applied every 30 minutes to lights showing white.
object Circadian {
 private fun intent(context:Context)=PendingIntent.getBroadcast(context,7,Intent(context,CircadianReceiver::class.java),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
 fun kelvinAt(hour:Float):Int {
  val curve=listOf(0f to 2200,6f to 2700,9f to 5000,13f to 6000,17f to 4200,20f to 2700,24f to 2200)
  val j=curve.indexOfLast { it.first<=hour }.coerceIn(0,curve.size-2); val (h0,k0)=curve[j]; val (h1,k1)=curve[j+1]
  val u=((hour-h0)/(h1-h0)).coerceIn(0f,1f); val e=(1-kotlin.math.cos(u*Math.PI.toFloat()))/2
  return ((k0+(k1-k0)*e)/100).toInt()*100
 }
 fun schedule(context:Context,enabled:Boolean) {
  val am=context.getSystemService(AlarmManager::class.java); am.cancel(intent(context))
  if(enabled) { am.setInexactRepeating(AlarmManager.RTC,System.currentTimeMillis()+5_000,AlarmManager.INTERVAL_HALF_HOUR,intent(context)) }
 }
}

class CircadianReceiver:BroadcastReceiver() {
 override fun onReceive(context:Context,intent:Intent) {
  val store=Store(context); if(!store.flag("circadian")) return
  val pending=goAsync()
  worker.execute {
   try {
    val c=Calendar.getInstance(); val k=Circadian.kelvinAt(c.get(Calendar.HOUR_OF_DAY)+c.get(Calendar.MINUTE)/60f)
    AppLog.i("Otomasyon","Sirkadiyen ritim: beyaz ışıklar $k K'e ayarlanıyor","kelvin" to k)
    store.load().forEach { l -> try { val s=Network.state(context,l); if(s.on && !s.colorMode && s.scene==0) Network.set(context,s,"kelvin",if(s.brand=="tapo") k.coerceIn(2500,6500) else k) else AppLog.i("Otomasyon","${s.name} atlandı (${if(!s.on) "kapalı" else "renk/sahne modunda"})","light" to s.ref()) } catch (e:Exception) { AppLog.w("Otomasyon","${l.name} sirkadiyen ayarı yapılamadı",*AppLog.errorFields(e),"light" to l.ref()) } }
   } finally { pending.finish() }
  }
 }
}

class BootReceiver:BroadcastReceiver() {
 override fun onReceive(context:Context,intent:Intent) { if(intent.action==Intent.ACTION_BOOT_COMPLETED) Circadian.schedule(context,Store(context).flag("circadian")) }
}

class LightsTile:TileService() {
 override fun onStartListening() { render(Store(this).load().any { it.on }) }
 private fun render(on:Boolean) { qsTile?.apply { state=if(on) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE; label="Işıklar"; if(android.os.Build.VERSION.SDK_INT>=29) subtitle=if(on) "Açık" else "Kapalı"; updateTile() } }
 override fun onClick() {
  val store=Store(this); val lights=store.load(); val turnOn=lights.none { it.on }
  render(turnOn)
  worker.execute {
   val updated=lights.map { l -> try { Network.set(this,l,"power",if(turnOn) 1 else 0); l.copy(on=turnOn) } catch (_:Exception) { l } }
   store.save(updated); DualLightsWidget.renderAll(this)
  }
 }
}
