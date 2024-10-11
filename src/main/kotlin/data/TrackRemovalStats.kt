package data

import java.util.concurrent.atomic.AtomicInteger

data class TrackRemovalStats(
    val nullTracks: AtomicInteger = AtomicInteger(0),
    val unplayableTracks: AtomicInteger = AtomicInteger(0),
    val localTracks: AtomicInteger = AtomicInteger(0)
)
