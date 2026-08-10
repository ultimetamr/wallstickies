package com.spatialapps.wallstickies.data.repository

import android.util.Log
import com.pico.spatial.core.math.EulerAngles
import com.pico.spatial.core.math.Vector3
import com.pico.spatial.sense.world.WorldTrackingManager
import com.pico.spatial.sense.world.WorldTrackingResult
import com.pico.spatial.sense.world.WorldAnchor

/** Full-Space-only bridge; callers persist the returned UUID with the note. */
class WorldAnchorRepository {
    suspend fun create(position: Vector3, rotation: EulerAngles, name: String): String? =
        when (val result = WorldTrackingManager.createAnchor(position, rotation, name)) {
            is WorldTrackingResult.Success -> result.data?.anchorUUID?.toString()?.also { Log.i(TAG, "created anchor=$it") }
            is WorldTrackingResult.Error -> {
                Log.w(TAG, "create failed: ${result.errorCode} ${result.errorMessage}")
                null
            }
        }

    suspend fun remove(uuid: java.util.UUID): Boolean =
        (WorldTrackingManager.removeAnchor(uuid) is WorldTrackingResult.Success).also { Log.i(TAG, "remove anchor=$uuid success=$it") }

    /**
     * Loads every anchor owned by this application. The SDK documents an empty
     * UUID array as the all-anchors path; it avoids sending a large UUID payload
     * through the emulator's unreliable anchor shared-memory bridge.
     */
    suspend fun loadAll(): Map<String, WorldAnchor> =
        when (val result = WorldTrackingManager.loadAnchor()) {
            is WorldTrackingResult.Success -> result.data?.associateBy { it.anchorUUID.toString() }.orEmpty().also { Log.i(TAG, "loaded all anchors=${it.size}") }
            is WorldTrackingResult.Error -> emptyMap<String, WorldAnchor>().also { Log.w(TAG, "load failed: ${result.errorCode} ${result.errorMessage}") }
        }

    fun subscribe(onAnchorChanged: (WorldAnchor) -> Unit) =
        WorldTrackingManager.subscribeAnchorUpdate { update ->
            Log.i(TAG, "anchor event=${update.event} uuid=${update.anchor.anchorUUID}")
            onAnchorChanged(update.anchor)
        }

    private companion object { const val TAG = "WallStickiesAnchor" }
}
