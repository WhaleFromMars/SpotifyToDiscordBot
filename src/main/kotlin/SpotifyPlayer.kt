import data.TrimmedTrack
import helpers.EmbedHelper
import helpers.EmbedHelper.EmbedState
import io.github.cdimascio.dotenv.Dotenv
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import java.util.LinkedHashSet
import kotlin.time.Duration.Companion.seconds

object SpotifyPlayer {

    private val webSocketServer = SpotWebSocketServer
    private val dotEnv = Dotenv.configure().ignoreIfMissing().load()
    private val spotifyPath = dotEnv["SPOTIFY_EXE_PATH"] ?: ""

    val queue: LinkedHashSet<TrimmedTrack> = LinkedHashSet()
    private val previousQueue: LinkedHashSet<TrimmedTrack> = LinkedHashSet()
    var songsPlayed = 0

    var lastUpdate = 0

    var currentTrack: TrimmedTrack? = null
    var currentProgress = 0
    var totalLength = 0

    var hasWaitedOnce = false
    var isPaused = false
    var repeatQueue = false

    suspend fun initialize() {
        require(spotifyPath.isNotEmpty()) { "Missing environment variable: SPOTIFY_EXE_PATH" }

        webSocketServer.start()
        ensureLocalSpotifyIsRunning()
        webSocketServer.waitForConnection()
        normaliseSpotify()
        println("SpotifyPlayer: Connection established")
        initiatePlaybackLoop()
    }

    private suspend fun ensureLocalSpotifyIsRunning() {
        if (isLocalSpotifyRunning()) return

        println("Spotify is not running. Attempting to start...")
        waitForSpotifyToStart()
    }

    private fun isLocalSpotifyRunning(): Boolean {
        return ProcessHandle.allProcesses()
            .anyMatch { it.info().command().orElse("").endsWith("Spotify.exe", ignoreCase = true) }
    }

    private suspend fun waitForSpotifyToStart() {
        withContext(Dispatchers.IO) {
            ProcessBuilder(spotifyPath).start()
        }
        withTimeout(30.seconds) {
            while (!isLocalSpotifyRunning()) {
                delay(500)
            }
        }
        println("Spotify has started successfully.")
    }

    fun updateFromJson(jsonData: String) {
        val jsonObject = Json.parseToJsonElement(jsonData).jsonObject

        lastUpdate = jsonObject["timestamp"]?.jsonPrimitive?.intOrNull ?: lastUpdate
        isPaused = jsonObject["isPaused"]?.jsonPrimitive?.booleanOrNull ?: isPaused
        totalLength = jsonObject["duration"]?.jsonPrimitive?.intOrNull ?: totalLength
        currentProgress = jsonObject["positionAsOfTimestamp"]?.jsonPrimitive?.intOrNull ?: currentProgress

        EmbedHelper.updateEmbedMessage()
    }

    fun getEmbedState(): EmbedState {
        return EmbedState(
            currentTrack,
            queue.take(5).map { "${it.name} - ${it.artist} " },
            queue.size,
            repeatQueue,
            isPaused
        )
    }

    fun updateProgress(progress: Int) {
        currentProgress = progress
    }

    /**
     * Adds a track to the queue
     * @return true if the track was added
     */
    fun addToQueue(track: TrimmedTrack): Boolean {
        val added = queue.add(track)
        if (added) {
            EmbedHelper.updateEmbedMessage()
        }
        return added
    }

    /**
     * Adds a list of tracks to the queue
     * @return the number of tracks added
     */
    fun addBulkToQueue(tracks: List<TrimmedTrack>, updateEmbed: Boolean = true): Int {
        val initialSize = queue.size
        queue.addAll(tracks)
        val addedCount = queue.size - initialSize
        if (addedCount > 0 && updateEmbed) {
            EmbedHelper.updateEmbedMessage()
        }
        return addedCount
    }

    /**
     * Removes a track from the queue
     * @return true if the track was removed
     */
    fun removeFromQueue(track: TrimmedTrack): Boolean {
        val removed = queue.remove(track)
        if (removed) {
            EmbedHelper.updateEmbedMessage()
        }
        return removed
    }

    fun clearQueue() {
        queue.clear()
        EmbedHelper.updateEmbedMessage()
    }

    fun shuffle() {
        val shuffled = queue.toList().shuffled()
        queue.clear()
        queue.addAll(shuffled)
        EmbedHelper.updateEmbedMessage()
    }

    fun togglePause() {
        if (isPaused) {
            resume()
        } else {
            pause()
        }
    }

    fun pause() {
        webSocketServer.sendCommand("pause")
        isPaused = true
        EmbedHelper.updateEmbedMessage()
    }

    fun resume() {
        webSocketServer.sendCommand("play")
        isPaused = false
        EmbedHelper.updateEmbedMessage()
    }

    fun playUri(uri: String) {
        webSocketServer.sendCommand("playUri", uri)
    }

    fun setVolume(volume: Int) {
        webSocketServer.sendCommand("volume", volume.toString())
    }

    fun setRepeatMode(repeatMode: Int) {
        webSocketServer.sendCommand("repeat", repeatMode.toString())
    }

    fun toggleRepeat() {
        repeatQueue = !repeatQueue
        EmbedHelper.updateEmbedMessage()
    }

    fun normaliseSpotify() {
        setVolume(100)
        setRepeatMode(0)
        pause()
    }

    fun playNext() {
        currentTrack?.let {
            if (repeatQueue) {
                queue.add(it)
            } else {
                previousQueue.add(it)
            }
        }

        if (queue.isNotEmpty()) {
            val nextTrack = queue.first()
            queue.remove(nextTrack)
            playUri(nextTrack.uri)
            currentTrack = nextTrack
            currentProgress = 1
            hasWaitedOnce = false
            isPaused = false
        } else {
            currentTrack = null
            isPaused = true
            songsPlayed = 0
        }
        EmbedHelper.updateEmbedMessage()
        songsPlayed++
    }

    fun skipCurrent() {
        if (queue.isNotEmpty()) {
            playNext()
        } else {
            pause()
            currentTrack = null
            EmbedHelper.updateEmbedMessage()
        }
    }

    fun playPrevious() {
        if (previousQueue.isNotEmpty()) {
            val prevTrack = previousQueue.last()
            previousQueue.remove(prevTrack)
            currentTrack?.let { queue.add(it) }
            currentTrack = prevTrack
            playUri(prevTrack.uri)

            isPaused = false
            currentProgress = 1
            hasWaitedOnce = false
        } else if (repeatQueue && queue.isNotEmpty()) {
            val lastTrack = queue.last()
            queue.remove(lastTrack)
            currentTrack = lastTrack
            playUri(lastTrack.uri)

            isPaused = false
            currentProgress = 1
            hasWaitedOnce = false
        } else {
            // No previous tracks and looping is disabled
        }
        EmbedHelper.updateEmbedMessage()
    }

    fun shutdownPlayer() {
        pause()
        PeopleBot.leaveVoiceChannel()
        webSocketServer.stopServer()
        currentTrack = null
        queue.clear()
        previousQueue.clear()
        songsPlayed = 0
        EmbedHelper.updateEmbedMessage()
    }

    @Suppress("SYNTHETIC_PROPERTY_WITHOUT_JAVA_ORIGIN")
    private fun initiatePlaybackLoop() {
        PeopleBot.scope.launch {
            while (true) {
                delay(500)
                if (currentTrack == null && queue.isEmpty) {
                    isPaused = true
                    songsPlayed = 0
                    EmbedHelper.updateEmbedMessage()
                    continue
                }

                if (songsPlayed == 0 && currentTrack == null) {
                    playNext()
                    continue
                }

                if (currentProgress != 0) {
                    continue
                }

                if (hasWaitedOnce) {
                    hasWaitedOnce = false
                    playNext()
                    continue
                } else {
                    hasWaitedOnce = true
                }
            }
        }
    }
}