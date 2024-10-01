import com.adamratzman.spotify.*
import io.github.cdimascio.dotenv.Dotenv
import kotlinx.coroutines.*
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.entities.MessageEmbed

object SpotifyHelper {
    private val dotEnv = Dotenv.load()
    private val PLAYBACK_DEVICE_NAME = dotEnv["SPOTIFY_DEVICE_NAME"]

    // We handle our own queue because Spotify doesn't support removing items from the queue
    var queue: MutableList<TrimmedTrack> = mutableListOf()
    var previousSongs: MutableList<TrimmedTrack> = mutableListOf()
    var currentTrack: TrimmedTrack? = null

    var maxProgress = 0
    var currentProgress = 0

    private lateinit var spotify: SpotifyAppApi

    private val webSocketServer = SpotWebSocketServer(8080)
    private var isPlaying = false

    init {
        runBlocking {
            initialiseSpotify()
            webSocketServer.start()
        }
        initiatePlaybackLoop()
    }

    fun sendLocalPauseCommand() {
        webSocketServer.sendCommand("pause")
        isPlaying = false
    }

    fun sendLocalPlayCommand() {
        webSocketServer.sendCommand("play")
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

    fun addToQueue(track: TrimmedTrack) {
        queue.add(track)
    }

    private suspend fun initialiseSpotify() {
        val clientId = dotEnv["SPOTIFY_CLIENT_ID"]
        val clientSecret = dotEnv["SPOTIFY_CLIENT_SECRET"]

        require(clientId.isNotEmpty()) { "Missing environment variable: SPOTIFY_CLIENT_ID" }
        require(clientSecret.isNotEmpty()) { "Missing environment variable: SPOTIFY_CLIENT_SECRET" }

        spotify = spotifyAppApi(clientId, clientSecret).build()
        println("Spotify API initialized.")
    }

    private fun normaliseDeviceSettings() {
        sendLocalSetRepeatCommand(0)
        sendLocalSetShuffleCommand(false)
        sendLocalSetVolumeCommand(100)
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

    fun createEmbed(track: TrimmedTrack): MessageEmbed {
        return EmbedBuilder()
            .setTitle("[${track.name}](https://open.spotify.com/track/${track.id})")
            .setDescription("[${track.artist}](https://open.spotify.com/artist/${track.artist.id})")
            .setThumbnail(track.albumCover)
            .build()
    }
}