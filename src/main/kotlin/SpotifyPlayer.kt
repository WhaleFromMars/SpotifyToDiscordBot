import io.github.cdimascio.dotenv.Dotenv
import kotlinx.coroutines.*
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

    var hasWaitedOnce = false //add a second delay between songs because jank
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
        SpotifyHelper.updateEmbedMessage()
    }

    fun removeFromQueue(track: TrimmedTrack) {
        queue.remove(track)
        SpotifyHelper.updateEmbedMessage()
    }

    fun clearQueue() {
        queue.clear()
        SpotifyHelper.updateEmbedMessage()
    }

    fun shuffle() {
        queue.shuffle()
        SpotifyHelper.updateEmbedMessage()
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
        SpotifyHelper.updateEmbedMessage()
    }

    fun normaliseSpotify() {
        setVolume(100)
        setRepeatMode(0)
        pause()
    }

    fun playNext() {
        // If looping is enabled, add the current track back to the end of the queue
        currentTrack?.let {
            if (repeatQueue) {
                queue.add(it)
            } else {
                previousQueue.add(it)
            }
        }

        if (queue.isNotEmpty()) {
            val nextTrack = queue.removeAt(0)
            playUri(nextTrack.uri)
            currentTrack = nextTrack
            currentProgress = 1
            hasWaitedOnce = false
        } else {
            currentTrack = null
            isPaused = true
            songsPlayed = 0
        }
        SpotifyHelper.updateEmbedMessage()
        songsPlayed++
    }

    fun playPrevious() {
        if (previousQueue.isNotEmpty()) {
            // Remove the previous track from previousQueue
            val prevTrack = previousQueue.removeAt(previousQueue.size - 1)
            currentTrack?.let { queue.addFirst(it) }
            currentTrack = prevTrack
            playUri(prevTrack.uri)

            isPaused = false
            currentProgress = 1
            hasWaitedOnce = false
        } else if (repeatQueue) {
            // When looping is enabled and there are no previous tracks
            if (queue.isNotEmpty()) {
                val lastTrack = queue.removeAt(queue.size - 1)
                currentTrack = lastTrack
                playUri(lastTrack.uri)

                isPaused = false
                currentProgress = 1
                hasWaitedOnce = false
            }
        } else {
            // No previous tracks and looping is disabled
            // Cba to drill the user here but we can tell them
        }
        SpotifyHelper.updateEmbedMessage()
    }


    fun shutdownPlayer() {
        pause()
        PeopleBot.leaveVoiceChannel(PeopleBot.jda.getGuildById(PeopleBot.GUILD_ID)!!)
        webSocketServer.stopServer()
        currentTrack = null
        SpotifyHelper.updateEmbedMessage()
    }

    fun initiatePlaybackLoop() {
        CoroutineScope(Dispatchers.Default).launch {
            while (true) {
                delay(1000)
                if (currentTrack == null && queue.isEmpty()) {
                    isPaused = true
                    songsPlayed = 0
                    SpotifyHelper.updateEmbedMessage()
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
