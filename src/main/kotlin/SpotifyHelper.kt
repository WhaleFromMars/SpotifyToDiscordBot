import com.adamratzman.spotify.*
import com.adamratzman.spotify.SpotifyException.BadRequestException
import com.adamratzman.spotify.endpoints.client.ClientPlayerApi.PlayerRepeatState
import com.adamratzman.spotify.models.SimpleTrack
import io.github.cdimascio.dotenv.Dotenv
import kotlinx.coroutines.*
import java.awt.Desktop
import java.io.File
import java.net.ServerSocket
import java.net.Socket
import java.net.URI
import kotlin.properties.Delegates
import kotlin.random.Random

object SpotifyHelper {
    private val dotEnv = Dotenv.load()
    private const val PLAYBACK_DEVICE_NAME = "DESKTOP-0OFJKBN"
    private const val REDIRECT_URI = "http://localhost:8080"
    private const val CACHE_FILE = ".spotifyCache"

    //we handle our own queue because spotify doesn't support removing items from the queue
    var queue: MutableList<SimpleTrack> = mutableListOf()
    var previousSongs: MutableList<SimpleTrack> = mutableListOf()
    var currentTrack: SimpleTrack? = null

    var maxProgress = 0
    var currentProgress = 0

    private lateinit var spotify: SpotifyClientApi
    private var isPremium = true
    private var playbackDeviceID by Delegates.notNull<String>()

    init {
        runBlocking {
            initialiseSpotify()
            initialisePlaybackDevice()
            normaliseDeviceSettings()
        }
        initiatePlaybackLoop()
    }

    fun addToQueue(track: SimpleTrack) {
        queue.add(track)
    }

    suspend fun pausePlayback() {
        if (isPremium) {
            spotify.player.pause(deviceId = playbackDeviceID)
        }
    }

    suspend fun resumePlayback() {
        if (isPremium) {
            spotify.player.resume(deviceId = playbackDeviceID)
        }
    }

    suspend fun setVolume(volumePercent: Int) {
        if (isPremium) {
        spotify.player.setVolume(volumePercent, deviceId = playbackDeviceID)
            }
    }

    private suspend fun initialiseSpotify() {
        val clientId = dotEnv["SPOTIFY_CLIENT_ID"]
        val clientSecret = dotEnv["SPOTIFY_CLIENT_SECRET"]

        require(clientId != "" && clientSecret != "") {
            "Missing required environment variables"
        }

        val cachedRefreshToken = loadRefreshTokenFromCache()

        if (cachedRefreshToken != null) {
            try {
                spotify = spotifyClientApi(clientId, clientSecret, REDIRECT_URI) {
                    authorization = SpotifyUserAuthorization(refreshTokenString = cachedRefreshToken)
                }.build()
                println("Successfully authenticated using cached refresh token.")
                return
            } catch (_: Exception) {
                println("Failed to authenticate with cached refresh token. Initiating new authorization flow.")
            }
        }

        val pkceCodeVerifier = generateCodeVerifier()
        val pkceCodeChallenge = getSpotifyPkceCodeChallenge(pkceCodeVerifier)

        val authorizationUrl = getSpotifyPkceAuthorizationUrl(
            SpotifyScope.UserReadPlaybackState,
            SpotifyScope.UserModifyPlaybackState,
            SpotifyScope.UserReadCurrentlyPlaying, //might not be needed as readPlayback encompasses this
            clientId = clientId,
            redirectUri = REDIRECT_URI,
            codeChallenge = pkceCodeChallenge
        )

        println("Please open the following URL in your browser:")
        println(authorizationUrl)

        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            Desktop.getDesktop().browse(URI(authorizationUrl))
        }

        val code = waitForAuthorizationCode()

        spotify = spotifyClientPkceApi(
            clientId, REDIRECT_URI, code, pkceCodeVerifier
        ).build()

        // Save the refresh token for future use
        saveRefreshTokenToCache(spotify.token.refreshToken)

        println("Authorization successful. Refresh token saved.")
    }

    private fun loadRefreshTokenFromCache(): String? {
        val file = File(CACHE_FILE)
        return if (file.exists()) file.readText().trim() else null
    }

    private fun saveRefreshTokenToCache(refreshToken: String?) {
        if (refreshToken != null) {
            File(CACHE_FILE).writeText(refreshToken)
        }
    }

    private fun waitForAuthorizationCode(): String {
        return runBlocking {
            withContext(Dispatchers.IO) {
                var server: ServerSocket? = null
                var clientSocket: Socket? = null
                try {
                    server = ServerSocket(8080)
                    println("Waiting for authorization code...")
                    clientSocket = server.accept()
                    val response = clientSocket.getInputStream().bufferedReader().readLine()
                    clientSocket.getOutputStream()
                        .write("HTTP/1.1 200 OK\r\n\r\nAuthorization successful! You can close this window.".toByteArray())

                    val code = response.substringAfter("code=").substringBefore(" ")
                    println("Authorization code received.")
                    code
                } finally {
                    try {
                        clientSocket?.close()
                    } catch (e: Exception) {
                        println("Error closing client socket: ${e.message}")
                    }
                    try {
                        server?.close()
                    } catch (e: Exception) {
                        println("Error closing server socket: ${e.message}")
                    }
                }
            }
        }
    }

    private suspend fun initialisePlaybackDevice() {
        val devices = spotify.player.getDevices()
        println("Devices: ${devices.map { it.name }}")
        playbackDeviceID = devices.find { it.name == PLAYBACK_DEVICE_NAME }?.id
            ?: throw IllegalStateException("Playback device not found")
        println("Device Connected: $playbackDeviceID")
    }

    private suspend fun normaliseDeviceSettings() {
        try {
            spotify.player.setRepeatMode(PlayerRepeatState.Off, deviceId = playbackDeviceID)
            spotify.player.toggleShuffle(false, deviceId = playbackDeviceID)
            spotify.player.setVolume(100, deviceId = playbackDeviceID)
        } catch (e: BadRequestException) {
            isPremium = false
            println("Failed to normalise device settings: ${e.message}")
        }
    }

    private fun generateCodeVerifier(): String {
        val characters = ('a'..'z') + ('A'..'Z') + ('0'..'9') + listOf('_', '.', '-', '~')
        val length = Random.nextInt(43, 129)
        return List(length) { characters.random() }.joinToString("")
    }

    fun initiatePlaybackLoop() {
        if (isPremium) {
            CoroutineScope(Dispatchers.Default).launch {
                while (true) {
                    delay(1000)
                    if (queue.isEmpty()) continue

                    val playbackState = spotify.player.getCurrentlyPlaying()

                    if (playbackState?.isPlaying != true) {
                        if (currentTrack == null && queue.isNotEmpty()) {
                            queue.removeAt(0).apply {
                                currentTrack = this
                                previousSongs.add(this)
                                maxProgress = this.durationMs.toInt()
                                spotify.player.startPlayback(
                                    trackIdsToPlay = listOf(this.id), deviceId = playbackDeviceID
                                )
                            }
                            currentProgress = 0
                        }
                    } else {
                        currentProgress++
                        if (currentProgress >= maxProgress) {
                            currentTrack = null
                            spotify.player.pause(deviceId = playbackDeviceID)
                        }
                    }
                }
            }
        }
    }
}