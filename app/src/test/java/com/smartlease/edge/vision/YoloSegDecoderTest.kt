package com.smartlease.edge.vision

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Geometry and area arithmetic for the YOLOv8n-Seg decoder.
 *
 * These are the parts that silently produce a wrong number rather than crashing: a box that
 * forgets letterbox padding still looks plausible, and an area computed from the bounding box
 * instead of the mask overstates damage by several times. Both are checked here against
 * hand-computed values.
 */
class YoloSegDecoderTest {

    private val anchors = YoloSegConfig.ANCHORS
    private val protoPlane = YoloSegConfig.PROTO_SIZE * YoloSegConfig.PROTO_SIZE

    /** preds with a single detection planted at anchor 0. */
    private fun predsWithOneDetection(
        cx: Float, cy: Float, w: Float, h: Float,
        classIndex: Int, score: Float,
        anchorIndex: Int = 0
    ): FloatArray {
        val preds = FloatArray(YoloSegConfig.PRED_CHANNELS * anchors)
        preds[0 * anchors + anchorIndex] = cx
        preds[1 * anchors + anchorIndex] = cy
        preds[2 * anchors + anchorIndex] = w
        preds[3 * anchors + anchorIndex] = h
        preds[(YoloSegConfig.BOX_CHANNELS + classIndex) * anchors + anchorIndex] = score
        // Coefficient 0 large & positive -> sigmoid saturates -> mask covers the whole box.
        preds[(YoloSegConfig.BOX_CHANNELS + YoloSegConfig.NUM_CLASSES) * anchors + anchorIndex] = 10f
        return preds
    }

    /** protos where channel 0 is uniformly 1.0, so mask value is driven purely by coefficient 0. */
    private fun uniformProtos(): FloatArray {
        val protos = FloatArray(YoloSegConfig.MASK_COEFFS * protoPlane)
        for (i in 0 until protoPlane) protos[i] = 1f
        return protos
    }

    @Test
    fun `square frame needs no letterbox padding`() {
        val lb = Letterbox.forSource(640, 640)
        assertEquals(1f, lb.scale, 1e-4f)
        assertEquals(0, lb.padX)
        assertEquals(0, lb.padY)
        assertEquals(25600f, lb.contentAreaInProtoCells(), 1f) // 160 x 160
    }

    @Test
    fun `wide frame is padded vertically and maps coordinates back`() {
        val lb = Letterbox.forSource(1280, 720)
        assertEquals(0.5f, lb.scale, 1e-4f)
        assertEquals(0, lb.padX)
        assertEquals(140, lb.padY)          // (640 - 360) / 2
        // A point at the top of the real content sits at y = padY in input space.
        assertEquals(0f, lb.sourceY(140f), 0.5f)
        assertEquals(720f, lb.sourceY(500f), 0.5f)
    }

    @Test
    fun `detection is decoded with correct class and mask-derived coverage`() {
        val lb = Letterbox.forSource(640, 640)
        val preds = predsWithOneDetection(cx = 320f, cy = 320f, w = 160f, h = 160f, classIndex = 2, score = 0.9f)

        val out = YoloSegDecoder.decode(preds, uniformProtos(), lb)

        assertEquals(1, out.size)
        val d = out.first()
        assertEquals(2, d.classIndex)
        assertEquals("spalling", YoloSegConfig.CLASS_NAMES[d.classIndex])
        assertEquals(0.9f, d.score, 1e-4f)

        // Box: centre 320 +/- 80 -> 240..400 in source pixels (scale 1, no padding).
        assertEquals(240f, d.box.left, 0.5f)
        assertEquals(400f, d.box.right, 0.5f)

        // Mask covers the full box: 160px / 4 = 40 proto cells a side -> 1600 of 25600.
        assertEquals(1600f / 25600f, d.coverageFraction, 1e-3f)
    }

    @Test
    fun `area comes from mask coverage not bounding box`() {
        val lb = Letterbox.forSource(640, 640)
        val preds = predsWithOneDetection(320f, 320f, 160f, 160f, classIndex = 0, score = 0.8f)
        val d = YoloSegDecoder.decode(preds, uniformProtos(), lb).first()

        // A 48in x 36in frame is 4ft x 3ft = 12 sq ft; 6.25% coverage -> 0.75 sq ft.
        val frameSqFt = (48f / 12f) * (36f / 12f)
        assertEquals(0.75f, d.coverageFraction * frameSqFt, 1e-2f)
    }

    @Test
    fun `scores below threshold are discarded`() {
        val lb = Letterbox.forSource(640, 640)
        val preds = predsWithOneDetection(320f, 320f, 160f, 160f, classIndex = 1, score = 0.10f)
        assertTrue(YoloSegDecoder.decode(preds, uniformProtos(), lb).isEmpty())
    }

    @Test
    fun `nms suppresses an overlapping duplicate of the same class`() {
        val lb = Letterbox.forSource(640, 640)
        val preds = predsWithOneDetection(320f, 320f, 160f, 160f, classIndex = 3, score = 0.9f, anchorIndex = 0)
        // Near-identical box on another anchor, slightly lower score.
        val dup = predsWithOneDetection(324f, 324f, 160f, 160f, classIndex = 3, score = 0.7f, anchorIndex = 1)
        for (i in preds.indices) if (dup[i] != 0f) preds[i] = dup[i]

        val out = YoloSegDecoder.decode(preds, uniformProtos(), lb)
        assertEquals(1, out.size)
        assertEquals(0.9f, out.first().score, 1e-4f)
    }

    @Test
    fun `iou is zero for disjoint boxes and one for identical boxes`() {
        val a = YoloSegDecoder.BoxF(0f, 0f, 10f, 10f)
        val b = YoloSegDecoder.BoxF(20f, 20f, 30f, 30f)
        assertEquals(0f, YoloSegDecoder.iou(a, b), 1e-6f)
        assertEquals(1f, YoloSegDecoder.iou(a, a), 1e-6f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `wrong preds size is rejected rather than silently misread`() {
        YoloSegDecoder.decode(FloatArray(10), uniformProtos(), Letterbox.forSource(640, 640))
    }
}
