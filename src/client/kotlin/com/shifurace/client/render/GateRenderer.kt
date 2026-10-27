package com.shifurace.client.render

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import com.shifurace.client.ClientRace
import com.shifurace.network.GateView
import com.shifurace.network.RaceHudPayload
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.renderer.LightTexture
import net.minecraft.client.renderer.RenderType
import net.minecraft.world.phys.Vec3
import org.joml.Matrix4f
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Arcos dos checkpoints: o próximo em amarelo com uma seta apontando para o seguinte, o seguinte fraco,
 * a chegada quadriculada na última volta. No modo edição, todos os portões com número.
 */
object GateRenderer {
    private const val YELLOW = 0xFFD23F
    private const val WHITE = 0xFFFFFF
    private const val GREEN = 0x4CFF6A
    private const val CYAN = 0x4CE0FF

    private class Ctx(val vc: VertexConsumer, val m: Matrix4f, val cam: Vec3)

    fun render(context: WorldRenderContext) {
        val hud = ClientRace.hud
        val edit = ClientRace.edit
        val racing = hud.active && hud.state != RaceHudPayload.STATE_FINISHED && ClientRace.gates.isNotEmpty()
        val editing = edit.trackName.isNotEmpty()
        if (!racing && !editing) return

        val mc = Minecraft.getInstance()
        val cam = context.camera().position
        val pose = context.matrixStack() ?: PoseStack()
        val buffers = mc.renderBuffers().bufferSource()
        val ctx = Ctx(buffers.getBuffer(RenderType.debugQuads()), pose.last().pose(), cam)
        val t = System.currentTimeMillis()

        if (racing) {
            val nextIdx = if (hud.nextGate >= hud.checkpointCount) -1 else hud.nextGate
            val followingIdx = when {
                nextIdx == -1 -> 0
                nextIdx + 1 >= hud.checkpointCount -> -1
                else -> nextIdx + 1
            }
            val lastLap = hud.currentLap >= hud.laps
            val next = ClientRace.gate(nextIdx)
            // Depois da chegada na última volta não tem "seguinte"
            val following = if (nextIdx == -1 && lastLap) null else ClientRace.gate(followingIdx)
            val pulse = 0.7f + 0.15f * sin(t / 180.0).toFloat()
            if (next != null) {
                arch(ctx, next, YELLOW, pulse, 0.45, checker = nextIdx == -1 && lastLap)
                if (following != null) arrow(ctx, next, center(following), YELLOW, pulse)
            }
            if (following != null) arch(ctx, following, WHITE, 0.22f, 0.25, checker = followingIdx == -1 && lastLap)
        }

        if (editing) {
            for (g in edit.gates) {
                arch(ctx, g, if (g.index == -1) GREEN else CYAN, 0.55f, 0.3, checker = false)
                if (g.index == -1) {
                    val (nx, nz) = normal(g)
                    val c = center(g)
                    arrow(ctx, g, Vec3(c.x + nx * edit.lineForward * 10, c.y, c.z + nz * edit.lineForward * 10), GREEN, 0.9f)
                }
            }
        }
        buffers.endBatch(RenderType.debugQuads())

        if (editing) {
            val font = mc.font
            for (g in edit.gates) {
                val c = center(g)
                val label = if (g.index == -1) "LARGADA/CHEGADA" else "CHECKPOINT ${g.index + 1}"
                label(pose, font, buffers, cam, c.x, archTop(g) + 0.8, c.z, label, if (g.index == -1) 0xFF4CFF6A.toInt() else 0xFF4CE0FF.toInt())
            }
            buffers.endBatch()
        }
    }

    private fun center(g: GateView) = Vec3((g.ax + g.bx) / 2, g.minY + 1.0, (g.az + g.bz) / 2)

    private fun normal(g: GateView): Pair<Double, Double> {
        val w = hypot(g.bx - g.ax, g.bz - g.az)
        if (w < 1e-6) return 0.0 to 1.0
        return -(g.bz - g.az) / w to (g.bx - g.ax) / w
    }

    private fun halfWidth(g: GateView) = hypot(g.bx - g.ax, g.bz - g.az) / 2 + 0.5
    private fun archHeight(g: GateView): Double = min(g.maxY - (g.minY + 1.0), max(3.0, halfWidth(g) * 0.9))
    private fun archTop(g: GateView) = g.minY + 1.0 + archHeight(g)

    /** Arco em semicírculo achatado, de um lado da pista ao outro, no plano do portão. */
    private fun arch(ctx: Ctx, g: GateView, rgb: Int, alpha: Float, width: Double, checker: Boolean) {
        val y0 = g.minY + 1.0
        val half = halfWidth(g)
        val height = archHeight(g)
        val w = hypot(g.bx - g.ax, g.bz - g.az)
        val ux = if (w < 1e-6) 1.0 else (g.bx - g.ax) / w
        val uz = if (w < 1e-6) 0.0 else (g.bz - g.az) / w
        val cx = (g.ax + g.bx) / 2
        val cz = (g.az + g.bz) / 2
        val segments = 28
        fun point(theta: Double, inset: Double): Vec3 {
            val c = -cos(theta)
            return Vec3(cx + ux * c * (half - inset), y0 + sin(theta) * (height - inset), cz + uz * c * (half - inset))
        }
        for (i in 0 until segments) {
            val a = PI * i / segments
            val b = PI * (i + 1) / segments
            val color = if (checker) (if (i % 2 == 0) WHITE else 0x111111) else rgb
            quad(ctx, point(a, 0.0), point(b, 0.0), point(b, width), point(a, width), color, alpha)
        }
    }

    /** Seta em forma de ponta de flecha no meio do portão, inclinada para cima, apontando para [target]. */
    private fun arrow(ctx: Ctx, g: GateView, target: Vec3, rgb: Int, alpha: Float) {
        val c0 = center(g)
        val c = Vec3(c0.x, c0.y + archHeight(g) * 0.5, c0.z)
        var dx = target.x - c.x
        var dz = target.z - c.z
        val len = sqrt(dx * dx + dz * dz)
        if (len < 1e-6) {
            val (nx, nz) = normal(g)
            dx = nx; dz = nz
        } else {
            dx /= len; dz /= len
        }
        val k = 0.7071
        val d = Vec3(dx * k, k, dz * k)
        val s = Vec3(-dz, 0.0, dx)
        val size = 1.3
        val tip = c.add(d.scale(size))
        val left = c.subtract(d.scale(size * 0.6)).add(s.scale(size * 0.8))
        val right = c.subtract(d.scale(size * 0.6)).subtract(s.scale(size * 0.8))
        val notch = c.subtract(d.scale(size * 0.15))
        quad(ctx, tip, left, notch, right, rgb, alpha)
    }

    private fun quad(ctx: Ctx, a: Vec3, b: Vec3, c: Vec3, d: Vec3, rgb: Int, alpha: Float) {
        val r = (rgb shr 16 and 0xFF) / 255f
        val gr = (rgb shr 8 and 0xFF) / 255f
        val bl = (rgb and 0xFF) / 255f
        for (p in arrayOf(a, b, c, d)) {
            ctx.vc.addVertex(ctx.m, (p.x - ctx.cam.x).toFloat(), (p.y - ctx.cam.y).toFloat(), (p.z - ctx.cam.z).toFloat()).setColor(r, gr, bl, alpha)
        }
    }

    private fun label(pose: PoseStack, font: Font, buffers: net.minecraft.client.renderer.MultiBufferSource, cam: Vec3, x: Double, y: Double, z: Double, text: String, color: Int) {
        val mc = Minecraft.getInstance()
        pose.pushPose()
        pose.translate(x - cam.x, y - cam.y, z - cam.z)
        pose.mulPose(mc.entityRenderDispatcher.cameraOrientation())
        pose.scale(0.05f, -0.05f, 0.05f)
        val background = (mc.options.getBackgroundOpacity(0.35f) * 255).toInt() shl 24
        font.drawInBatch(text, -font.width(text) / 2f, 0f, color, false, pose.last().pose(), buffers, Font.DisplayMode.SEE_THROUGH, background, LightTexture.FULL_BRIGHT)
        pose.popPose()
    }
}
