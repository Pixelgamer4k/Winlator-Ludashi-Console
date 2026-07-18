package com.winlator.cmod.delta

import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.PointerId
import kotlin.math.hypot
import kotlin.math.min

enum class PressHaptic { BUTTON, SHOULDER, GUIDE, KEY }

/**
 * Multi-touch press bus for the whole chassis pad.
 *
 * Button pressed-state is derived from the set of active pointer→zone ownership
 * (not a refcount). That way missed ups / cancels / 3–4 finger mash can never
 * leave holdCount stuck and a button wedged down.
 *
 * Coordinates are in **window/root** space (boundsInRoot / positionInRoot).
 */
class ChassisPressRouter(private val haptics: ChassisHaptics?) {

    private class Zone(
        val id: String,
        val haptic: PressHaptic,
        val circular: Boolean,
        /** Keys: fire once on down; release is silent. */
        val fireOnDownOnly: Boolean,
        var onPressed: (Boolean) -> Unit,
        var rect: Rect = Rect.Zero,
        /** Analog reserved — router will not claim pointers here. */
        val analog: Boolean = false,
    )

    private val zones = LinkedHashMap<String, Zone>()
    /** Which digital control each finger currently owns. */
    private val pointerOwner = HashMap<PointerId, String>()

    /** UI observes this for pressed visuals. */
    val visuallyPressed: SnapshotStateMap<String, Boolean> = mutableStateMapOf()

    @Synchronized
    fun register(
        id: String,
        haptic: PressHaptic,
        circular: Boolean,
        fireOnDownOnly: Boolean = false,
        analog: Boolean = false,
        onPressed: (Boolean) -> Unit,
    ) {
        val existing = zones[id]
        if (existing != null) {
            existing.onPressed = onPressed
            return
        }
        zones[id] = Zone(id, haptic, circular, fireOnDownOnly, onPressed, analog = analog)
    }

    @Synchronized
    fun unregister(id: String) {
        val affected = pointerOwner.filterValues { it == id }.keys.toList()
        affected.forEach { pointerOwner.remove(it) }
        zones.remove(id)
        if (visuallyPressed[id] == true) {
            visuallyPressed[id] = false
        }
        visuallyPressed.remove(id)
        // Dropping a zone mid-hold: remaining ownership still syncs cleanly
        syncButtons(transferHapticId = null)
    }

    @Synchronized
    fun updateBoundsRoot(id: String, rect: Rect) {
        val z = zones[id] ?: return
        z.rect = rect
    }

    /** True if this pointer is currently claimed by a digital control. */
    @Synchronized
    fun owns(pointerId: PointerId): Boolean = pointerId in pointerOwner

    /**
     * Drop every finger ownership and release all buttons.
     * Call on gesture-scope cancel / edit-mode enter.
     */
    @Synchronized
    fun releaseAll() {
        if (pointerOwner.isEmpty() && visuallyPressed.none { it.value }) return
        pointerOwner.clear()
        syncButtons(transferHapticId = null)
    }

    /**
     * Release any owned pointers that are no longer pressed.
     * [stillPressed] = pointer ids reported pressed in the current event.
     * [seen] = pointer ids that appeared in this event (up or down).
     *
     * Only releases pointers that were *seen* as up/cancel — never drops a
     * finger that simply didn't appear in this frame's change list.
     */
    @Synchronized
    fun releaseIfUp(pointerId: PointerId): Boolean {
        if (pointerId !in pointerOwner) return false
        pointerOwner.remove(pointerId)
        syncButtons(transferHapticId = null)
        return true
    }

    /**
     * @return true if this pointer was claimed by a digital control (caller should consume).
     */
    @Synchronized
    fun onDown(pointerId: PointerId, posRoot: Offset): Boolean {
        if (pointerId in pointerOwner) return true
        if (hitsAnalog(posRoot)) return false
        val id = hitDigital(posRoot, prefer = null) ?: return false
        pointerOwner[pointerId] = id
        syncButtons(transferHapticId = null)
        return true
    }

    @Synchronized
    fun onMove(pointerId: PointerId, posRoot: Offset): Boolean {
        val prev = pointerOwner[pointerId] ?: return false
        val hit = hitDigital(posRoot, prefer = prev)
        if (hit != null && hit != prev) {
            pointerOwner[pointerId] = hit
            syncButtons(transferHapticId = hit)
        } else if (hit == null) {
            val prevZone = zones[prev]
            // Sticky hold — only drop when clearly outside the diamond/cluster area
            if (prevZone == null || !nearZone(posRoot, prevZone, slop = 1.55f)) {
                if (hitDigital(posRoot, prefer = null) == null && !nearAnyDigital(posRoot, 1.25f)) {
                    pointerOwner.remove(pointerId)
                    syncButtons(transferHapticId = null)
                }
            }
        }
        return true
    }

    @Synchronized
    fun onUp(pointerId: PointerId): Boolean = releaseIfUp(pointerId)

    /**
     * Derive each button's down state from [pointerOwner].
     * Eliminates refcount skew that left buttons stuck with 3–4 fingers.
     */
    private fun syncButtons(transferHapticId: String?) {
        val active = HashSet<String>()
        for (id in pointerOwner.values) active.add(id)

        for ((id, z) in zones) {
            if (z.analog) continue
            val down = id in active
            val was = visuallyPressed[id] == true
            if (down == was) continue

            visuallyPressed[id] = down
            if (down) {
                if (transferHapticId == id) {
                    haptics?.faceTransfer()
                } else {
                    when (z.haptic) {
                        PressHaptic.SHOULDER -> haptics?.shoulderDown()
                        PressHaptic.GUIDE -> haptics?.guideDown()
                        PressHaptic.KEY, PressHaptic.BUTTON -> haptics?.buttonDown()
                    }
                }
                z.onPressed(true)
            } else {
                if (!z.fireOnDownOnly) {
                    when (z.haptic) {
                        PressHaptic.SHOULDER -> haptics?.shoulderUp()
                        PressHaptic.BUTTON -> haptics?.buttonUp()
                        PressHaptic.GUIDE, PressHaptic.KEY -> { /* quiet */ }
                    }
                    z.onPressed(false)
                } else {
                    // Key already fired on down; just clear visual
                }
            }
        }

        // Clear visuals for ids that no longer exist as zones
        val stale = visuallyPressed.keys.filter { it !in zones || zones[it]?.analog == true }
        stale.forEach { visuallyPressed.remove(it) }
    }

    private fun hitsAnalog(pos: Offset): Boolean =
        zones.values.any { it.analog && contains(it, pos, expand = 1.02f) }

    private fun hitDigital(pos: Offset, prefer: String?): String? {
        var best: String? = null
        var bestD = Float.MAX_VALUE
        for (z in zones.values) {
            if (z.analog) continue
            if (!contains(z, pos, expand = 1.08f)) continue
            val d = distanceToCenter(z, pos)
            if (d < bestD) {
                bestD = d
                best = z.id
            }
        }
        if (prefer != null) {
            val pz = zones[prefer]
            if (pz != null && !pz.analog && contains(pz, pos, expand = 1.28f)) {
                val preferD = distanceToCenter(pz, pos)
                // Stronger hysteresis so multi-finger mash doesn't flicker ownership
                if (best == null || best == prefer || bestD >= preferD - 18f) {
                    return prefer
                }
            }
        }
        return best
    }

    private fun nearAnyDigital(pos: Offset, slop: Float): Boolean =
        zones.values.any { !it.analog && nearZone(pos, it, slop) }

    private fun nearZone(pos: Offset, z: Zone, slop: Float): Boolean =
        contains(z, pos, expand = slop)

    private fun contains(z: Zone, pos: Offset, expand: Float): Boolean {
        val r = z.rect
        if (r.width <= 1f || r.height <= 1f) return false
        if (z.circular) {
            val cx = r.left + r.width * 0.5f
            val cy = r.top + r.height * 0.5f
            val rad = min(r.width, r.height) * 0.5f * expand
            return hypot((pos.x - cx).toDouble(), (pos.y - cy).toDouble()) <= rad
        }
        val dx = r.width * (expand - 1f) * 0.5f
        val dy = r.height * (expand - 1f) * 0.5f
        return pos.x >= r.left - dx && pos.x <= r.right + dx &&
            pos.y >= r.top - dy && pos.y <= r.bottom + dy
    }

    private fun distanceToCenter(z: Zone, pos: Offset): Float {
        val cx = z.rect.left + z.rect.width * 0.5f
        val cy = z.rect.top + z.rect.height * 0.5f
        return hypot((pos.x - cx).toDouble(), (pos.y - cy).toDouble()).toFloat()
    }
}
