package com.talkto.core.touch

import com.google.common.truth.Truth.assertThat
import com.talkto.core.avatar.Expression
import com.talkto.core.avatar3d.Mat4
import com.talkto.core.avatar3d.Primitives
import com.talkto.core.avatar3d.Spring
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sqrt

class TouchClassifierTest {

    private fun track(vararg pts: Triple<Long, Float, Float>, pressure: Float = 0f) =
        pts.map { (t, x, y) -> TouchSample(t, x, y, pressure) }

    private val c get() = TouchClassifier(300f, 400f)

    @Test fun `fast horizontal swipe is a slap`() {
        val t = track(Triple(0, 50f, 200f), Triple(40, 150f, 205f), Triple(80, 250f, 210f))
        val touch = c.classify(t)!!
        assertThat(touch.kind).isEqualTo(TouchKind.SLAP)
        assertThat(touch.nx).isLessThan(0.6f)
    }

    @Test fun `slow stroke and long hold are gentle`() {
        assertThat(c.classify(track(Triple(0, 100f, 100f), Triple(300, 110f, 140f), Triple(600, 120f, 180f)))!!.kind).isEqualTo(TouchKind.GENTLE)
        assertThat(c.classify(track(Triple(0, 150f, 150f), Triple(700, 152f, 151f)))!!.kind).isEqualTo(TouchKind.GENTLE)
    }

    @Test fun `slow sideways drag twirls the pet`() {
        val t = c.classify(track(Triple(0, 60f, 200f), Triple(300, 120f, 205f), Triple(600, 200f, 210f)))!!
        assertThat(t.kind).isEqualTo(TouchKind.TWIRL)
        assertThat(c.isTwirl(40f, 5f)).isTrue()
        assertThat(c.isTwirl(5f, 40f)).isFalse()
    }

    @Test fun `taps land in the zone under the finger`() {
        val body = BodyBox(0.5f, 0.5f, 0.3f, 0.3f)
        val cls = TouchClassifier(300f, 400f, body = { body })
        // belly: below the centre
        assertThat(cls.classify(track(Triple(0, 150f, 260f), Triple(100, 150f, 260f)))!!.zone).isEqualTo(TouchZone.BELLY)
        assertThat(body.zoneOf(0.5f, 0.3f)).isEqualTo(TouchZone.HEAD)
        assertThat(body.zoneOf(0.6f, 0.45f)).isEqualTo(TouchZone.EYE)
        assertThat(body.zoneOf(0.5f, 0.55f)).isEqualTo(TouchZone.MOUTH)
        assertThat(body.zoneOf(0.5f, 0.78f)).isEqualTo(TouchZone.FEET)
        assertThat(body.zoneOf(0.75f, 0.55f)).isEqualTo(TouchZone.SIDE)
        assertThat(body.zoneOf(0.05f, 0.1f)).isEqualTo(TouchZone.MISS)
    }

    @Test fun `very short jab or a hard press is a hit`() {
        assertThat(c.classify(track(Triple(0, 150f, 150f), Triple(40, 151f, 150f)))!!.kind).isEqualTo(TouchKind.HIT)
        assertThat(c.classify(track(Triple(0, 150f, 150f), Triple(150, 151f, 150f), pressure = 0.9f))!!.kind).isEqualTo(TouchKind.HIT)
    }

    @Test fun `constant pressure 1_0 does not count as hard`() {
        assertThat(c.classify(track(Triple(0, 150f, 150f), Triple(150, 151f, 150f), pressure = 1f))!!.kind).isEqualTo(TouchKind.POKE)
    }

    @Test fun `quick taps become a pat on the second tap`() {
        val cls = c
        assertThat(cls.classify(track(Triple(1_000, 150f, 80f), Triple(1_120, 150f, 80f)))!!.kind).isEqualTo(TouchKind.POKE)
        assertThat(cls.classify(track(Triple(1_400, 152f, 82f), Triple(1_500, 152f, 82f)))!!.kind).isEqualTo(TouchKind.PAT)
        assertThat(cls.classify(track(Triple(1_800, 150f, 80f), Triple(1_900, 150f, 80f)))!!.kind).isEqualTo(TouchKind.PAT)
        // after a pause the streak restarts
        assertThat(cls.classify(track(Triple(5_000, 150f, 80f), Triple(5_120, 150f, 80f)))!!.kind).isEqualTo(TouchKind.POKE)
    }
}

class TemperamentTest {
    private var now = 0L
    private var dice = 0.9f
    private val t = Temperament(clock = { now }, random = { dice })
    private fun touch(kind: TouchKind, nx: Float = 0.3f, zone: TouchZone = TouchZone.BELLY) = Touch(kind, nx, 0.5f, 0.8f, zone)

    @Test fun `first slap angers, repeated slaps sadden, the head turns away from the hand`() {
        val first = t.react(touch(TouchKind.SLAP, nx = 0.2f), petHappy = true)
        assertThat(first.expression).isEqualTo(Expression.ANGRY)
        assertThat(first.recoilYaw).isGreaterThan(0f)
        t.react(touch(TouchKind.SLAP), true)
        val third = t.react(touch(TouchKind.SLAP, nx = 0.8f), true)
        assertThat(third.expression).isEqualTo(Expression.SAD)
        assertThat(third.recoilYaw).isLessThan(0f)
        assertThat(third.happinessDelta).isLessThan(first.happinessDelta)
    }

    @Test fun `gentle touch calms and grievance fades with time`() {
        repeat(3) { t.react(touch(TouchKind.HIT), true) }
        assertThat(t.upset).isGreaterThan(0.6f)
        assertThat(t.react(touch(TouchKind.GENTLE), true).line).isIn(Temperament.FORGIVE)
        now += 20 * 60_000L
        assertThat(t.upset).isLessThan(0.05f)
        assertThat(t.react(touch(TouchKind.GENTLE, zone = TouchZone.HEAD), true).expression).isEqualTo(Expression.LOVE)
    }

    @Test fun `the same poke feels different on different parts of the body`() {
        val eye = t.react(touch(TouchKind.POKE, zone = TouchZone.EYE), true)
        val belly = t.react(touch(TouchKind.POKE, zone = TouchZone.BELLY), true)
        val feet = t.react(touch(TouchKind.POKE, zone = TouchZone.FEET), true)
        assertThat(eye.effect).isEqualTo(TouchEffect.WINCE)
        assertThat(belly.effect).isEqualTo(TouchEffect.GIGGLE)
        assertThat(feet.effect).isEqualTo(TouchEffect.HOP)
        assertThat(setOf(eye.line, belly.line, feet.line)).hasSize(3)
    }

    @Test fun `lines do not repeat twice in a row`() {
        val a = t.react(touch(TouchKind.POKE, zone = TouchZone.BELLY), true).line
        val b = t.react(touch(TouchKind.POKE, zone = TouchZone.BELLY), true).line
        assertThat(a).isNotEqualTo(b)
    }

    @Test fun `spinning the pet again and again makes it dizzy`() {
        val first = t.react(touch(TouchKind.TWIRL).copy(strength = 0.6f), true)
        assertThat(first.effect).isNotEqualTo(TouchEffect.DIZZY)
        val later = (1..3).map { t.react(touch(TouchKind.TWIRL).copy(strength = 0.6f), true) }
        assertThat(later.map { it.effect }).contains(TouchEffect.DIZZY)
    }

    @Test fun `a happy pet sometimes sticks its tongue out when patted`() {
        dice = 0.1f
        assertThat(t.react(touch(TouchKind.PAT), petHappy = true).expression).isEqualTo(Expression.TONGUE)
        assertThat(t.react(touch(TouchKind.PAT), petHappy = false).expression).isEqualTo(Expression.HAPPY)
    }
}

class GeometryTest {
    @Test fun `sphere normals are unit length and indices are in range`() {
        val m = Primitives.sphere(12, 16)
        for (i in 0 until m.vertexCount) {
            val l = sqrt(m.normals[i * 3] * m.normals[i * 3] + m.normals[i * 3 + 1] * m.normals[i * 3 + 1] + m.normals[i * 3 + 2] * m.normals[i * 3 + 2])
            assertThat(l).isWithin(1e-4f).of(1f)
        }
        assertThat(m.indices.all { it in 0 until m.vertexCount }).isTrue()
        assertThat(m.indexCount % 3).isEqualTo(0)
    }

    @Test fun `every primitive has consistent buffers`() {
        listOf(Primitives.cylinder(), Primitives.cone(), Primitives.torus(), Primitives.sphere(latFrom = Math.PI / 2)).forEach { m ->
            assertThat(m.indices.all { it in 0 until m.vertexCount }).isTrue()
            assertThat(m.indexCount).isGreaterThan(0)
        }
    }

    @Test fun `matrices compose like OpenGL`() {
        val m = Mat4.multiply(Mat4.translation(1f, 2f, 3f), Mat4.scale(2f, 2f, 2f))
        assertThat(Mat4.transform(m, 1f, 1f, 1f).toList().take(3)).containsExactly(3f, 4f, 5f).inOrder()
        val r = Mat4.transform(Mat4.rotationZ(90f), 1f, 0f, 0f)
        assertThat(r[0]).isWithin(1e-5f).of(0f)
        assertThat(r[1]).isWithin(1e-5f).of(1f)
        val v = Mat4.lookAt(0f, 0f, 5f, 0f, 0f, 0f)
        assertThat(Mat4.transform(v, 0f, 0f, 0f)[2]).isWithin(1e-5f).of(-5f)
    }

    @Test fun `normal matrix undoes non-uniform scale under rotation`() {
        val m = Mat4.multiply(Mat4.rotationZ(30f), Mat4.scale(2f, 1f, 1f))
        val n = Mat4.normalMatrix(m)
        // A surface tangent t = (0,1,0) in model space; its normal (1,0,0) must stay perpendicular after transforming.
        val t = Mat4.transform(m, 0f, 1f, 0f).let { floatArrayOf(it[0] - m[12], it[1] - m[13], it[2] - m[14]) }
        val nx = n[0] * 1f; val ny = n[1] * 1f; val nz = n[2] * 1f
        assertThat(abs(t[0] * nx + t[1] * ny + t[2] * nz)).isLessThan(1e-5f)
        // and it is not simply the rotated normal (which would be wrong for a non-uniform scale)
        val bad = Mat4.transform(m, 1f, 0f, 0f)
        assertThat(abs(nx - bad[0]) + abs(ny - bad[1])).isGreaterThan(0.1f)
    }

    @Test fun `spring settles at its target`() {
        val s = Spring()
        s.kick(10f)
        repeat(240) { s.step(1f / 60f) }
        assertThat(s.settled).isTrue()
        s.target = 1f
        repeat(240) { s.step(1f / 60f) }
        assertThat(s.value).isWithin(1e-2f).of(1f)
    }
}

class TwirlInputTest {
    @Test fun `drags add up until taken and a fling is capped`() {
        val t = TwirlInput()
        t.drag(10f); t.drag(5f)
        assertThat(t.take()).isEqualTo(TwirlInput.Step(15f, null, true))
        t.release(99_999f)
        assertThat(t.take()).isEqualTo(TwirlInput.Step(0f, TwirlInput.MAX_SPIN, false))
        assertThat(t.take().flingDegPerS).isNull()
    }
}
