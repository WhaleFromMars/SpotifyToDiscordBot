import io.github.cdimascio.dotenv.Dotenv
import kotlinx.coroutines.*
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.*
import kotlin.time.Duration.Companion.seconds

object SpotifyPlayer {
    private val webSocketServer = SpotWebSocketServer
    private val dotEnv = Dotenv.load()
    private val spotifyPath = dotEnv["SPOTIFY_EXE_PATH"] ?: ""

    var queue: MutableList<TrimmedTrack> = mutableListOf()
    var previousQueue: MutableList<TrimmedTrack> = mutableListOf()
    var songsPlayed = 0

    var lastUpdate = 0

    var currentTrack: TrimmedTrack? = null
    var currentProgress = 0
    var totalLength = 0

    var isPaused: Boolean = true
    var repeatQueue: Boolean = false

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
        val jsonElement = Json.parseToJsonElement(jsonData)
        val jsonObject = jsonElement.jsonObject

        lastUpdate = jsonObject["timestamp"]?.jsonPrimitive?.intOrNull ?: lastUpdate
        isPaused = jsonObject["isPaused"]?.jsonPrimitive?.booleanOrNull ?: isPaused
        totalLength = jsonObject["duration"]?.jsonPrimitive?.intOrNull ?: totalLength
        currentProgress = jsonObject["positionAsOfTimestamp"]?.jsonPrimitive?.intOrNull ?: currentProgress
    }

    fun updateProgress(progress: Int) {
        currentProgress = progress
    }

    fun addToQueue(track: TrimmedTrack) {
        queue.add(track)
    }

    fun removeFromQueue(track: TrimmedTrack) {
        queue.remove(track)
    }

    fun clearQueue() {
        queue.clear()
    }

    fun shuffle() {
        queue.shuffle()
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
    }

    fun resume() {
        webSocketServer.sendCommand("play")
        isPaused = false
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
    }

    fun mute(mute: Boolean) {
        webSocketServer.sendCommand("mute", mute.toString())
    }

    private fun setShuffleState(shuffle: Boolean) {
        webSocketServer.sendCommand("shuffle", shuffle.toString())
    }

    fun normaliseSpotify() {
        setVolume(100)
        setRepeatMode(0)
        pause()
    }

    fun playNext() {
        val track = currentTrack
        if (track != null) {
            previousQueue.add(track)
        }

        if (queue.isNotEmpty()) {
            val nextTrack = queue.removeAt(0)
            previousQueue.add(nextTrack)
            playUri(nextTrack.uri)
            currentTrack = nextTrack
        } else {
            currentTrack = null
            isPaused = true
            songsPlayed = 0
        }
        SpotifyHelper.updateEmbedMessage()
        songsPlayed++
    }

    fun playPrevious() {
        val track = currentTrack
        if (track != null) {
            queue.add(0, track)
        }
        if (previousQueue.isNotEmpty()) {
            val prevTrack = previousQueue.removeAt(previousQueue.size - 1)
            playUri(prevTrack.uri)
            currentTrack = prevTrack
            isPaused = false
        }
        SpotifyHelper.updateEmbedMessage()
    }

    fun initiatePlaybackLoop() {
        CoroutineScope(Dispatchers.Default).launch {
            while (true) {
                delay(1000)
                SpotifyHelper.updateEmbedMessage()

                if (queue.isEmpty() && repeatQueue && previousQueue.isNotEmpty()) {
                    queue.addAll(previousQueue)
                    previousQueue.clear()
                }

                if (queue.isEmpty()) {
                    if (currentProgress == 0) {
                        currentTrack = null
                        isPaused = true
                        songsPlayed = 0
                        SpotifyHelper.updateEmbedMessage()
                    }
                    continue
                }
                if (songsPlayed == 0) {
                    playNext()
                    continue
                }
                if (currentProgress != 0) continue

                playNext()
            }
        }
    }
}
