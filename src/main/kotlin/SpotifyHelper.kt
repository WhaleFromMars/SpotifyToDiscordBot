import com.adamratzman.spotify.*
import io.github.cdimascio.dotenv.Dotenv
import kotlinx.coroutines.*
import kotlin.time.Duration.Companion.seconds

object SpotifyHelper {
    private val dotEnv = Dotenv.load()

    // We handle our own queue because Spotify doesn't support removing items from the queue
    var queue: MutableList<TrimmedTrack> = mutableListOf()
    var previousSongs: MutableList<TrimmedTrack> = mutableListOf()
    var currentTrack: TrimmedTrack? = null

    var maxProgress = 0
    var currentProgress = 0

    private lateinit var spotify: SpotifyAppApi

    private val webSocketServer = SpotWebSocketServer(8080)
    private var isPlaying = false
    val spotifyPath = dotEnv["SPOTIFY_EXE_PATH"]


    init {
        require(spotifyPath.isNotEmpty()) { "Missing environment variable: SPOTIFY_EXE_PATH" }
        runBlocking {
            ensureLocalSpotifyIsRunning()
            initialiseSpotifyAPI()

            println("waiting for spotify api connection")
            webSocketServer.start()
            webSocketServer.waitForConnection()
            println("connection established")
        }
        initiatePlaybackLoop()
    }

    suspend fun ensureLocalSpotifyIsRunning() {
        if (isLocalSpotifyRunning()) return

        println("Spotify is not running. Attempting to start...")
        startSpotify()
        waitForSpotifyToStart()

    }

    fun isLocalSpotifyRunning(): Boolean {
        return ProcessHandle.allProcesses()
            .anyMatch { it.info().command().orElse("").endsWith("Spotify.exe", ignoreCase = true) }
    }

    suspend fun startSpotify() {
        spotifyPath?.let { path ->
            withContext(Dispatchers.IO) {
                ProcessBuilder(path).start()
            }
        } ?: throw IllegalStateException("Spotify executable path not found in .env file")
    }

    suspend fun waitForSpotifyToStart() {
        withTimeout(30.seconds) {
            while (!isLocalSpotifyRunning()) {
                delay(500)
            }
        }
        println("Spotify has started successfully.")
    }


    fun addToQueue(track: TrimmedTrack) {
        queue.add(track)
    }

    private suspend fun initialiseSpotifyAPI() {
        val clientId = dotEnv["SPOTIFY_CLIENT_ID"]
        val clientSecret = dotEnv["SPOTIFY_CLIENT_SECRET"]

        require(clientId.isNotEmpty()) { "Missing environment variable: SPOTIFY_CLIENT_ID" }
        require(clientSecret.isNotEmpty()) { "Missing environment variable: SPOTIFY_CLIENT_SECRET" }

        spotify = spotifyAppApi(clientId, clientSecret).build()
        println("Spotify API initialized.")
    }

    fun initiatePlaybackLoop() {
        CoroutineScope(Dispatchers.Default).launch {
            while (true) {
                delay(1000)
                if (queue.isEmpty()) continue

                if (!isPlaying) {
                    if (currentTrack == null) {
                        queue.removeAt(0).apply {
                            currentTrack = this
                            previousSongs.add(this)
                            maxProgress = this.duration
                            sendLocalPlayUriCommand("spotify:track:${this.id}$")
                        }
                        currentProgress = 0
                    }
                } else {
                    currentProgress++
                    if (currentProgress >= maxProgress) {
                        currentTrack = null
                        sendLocalPauseCommand()
                    }
                }
            }
        }
    }

    suspend fun searchTrack(query: String, returnAmount: Int = 1): List<TrimmedTrack>? {
        val tracks = spotify.search.searchTrack(query, limit = returnAmount).map { TrimmedTrack(it!!) }

        return if (tracks.isNotEmpty()) tracks else null
    }

    suspend fun getTrack(id: String): TrimmedTrack? {
        val track = spotify.tracks.getTrack(id)
        return if (track != null) TrimmedTrack(track) else null
    }

    fun sendLocalPauseCommand() {
        webSocketServer.sendCommand("pause")
        isPlaying = false
    }

    fun sendLocalResumeCommand() {
        webSocketServer.sendCommand("play") //resumes x)
        isPlaying = true
    }

    fun sendLocalPlayUriCommand(uri: String) {
        webSocketServer.sendCommand("playUri", uri)
        isPlaying = true
    }

    fun sendLocalSetVolumeCommand(volume: Int) {
        webSocketServer.sendCommand("volume", volume.toString())
    }

    fun sendLocalSetRepeatCommand(repeatMode: Int) {
        webSocketServer.sendCommand("repeat", repeatMode.toString())
    }

    fun sendLocalSetMuteCommand(mute: Boolean) {
        webSocketServer.sendCommand("mute", mute.toString())
    }

    fun sendLocalSetShuffleCommand(shuffle: Boolean) {
        webSocketServer.sendCommand("shuffle", shuffle.toString())
    }
}