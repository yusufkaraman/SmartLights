package com.smartlights.app

import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import kotlin.math.abs

// The Kotlin orb engine must draw exactly what the web library draws: every dot's position, radius, ink and alpha
// at the library's frozen timestamps (golden vectors from thinking-orbs, tolerance 1e-4).
class OrbEngineTest {
 @Test fun matchesGoldenFrames() {
  val lines=javaClass.classLoader!!.getResource("orbs-golden.txt")!!.readText().lines().filter { it.isNotBlank() && !it.startsWith("#") }
  assertEquals(24,lines.size)
  for(line in lines) {
   val p=line.split(' ')
   val state=when(p[0]) { "listening"->OrbState.Listening; "solving"->OrbState.Solving; else->OrbState.Breathing }
   val size=p[1].toInt(); val t=p[2].toDouble(); val count=p[3].toInt()
   val want=p.drop(4).map { it.toDouble() }
   val got=OrbEngine.frame(OrbEngine.resolve(state,size),size.toDouble(),t)
   val key="${p[0]}-$size-$t"
   assertEquals("$key dot count",count,got.size)
   got.forEachIndexed { i,d ->
    val vals=doubleArrayOf(d.x,d.y,d.z,d.r,d.white,d.a)
    for(f in 0 until 6) { val w=want[i*6+f]; if(abs(vals[f]-w)>1e-4) fail("$key dot $i field $f: got ${vals[f]}, want $w") }
   }
  }
 }
}
