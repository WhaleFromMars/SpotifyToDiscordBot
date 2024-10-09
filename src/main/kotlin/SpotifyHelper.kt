import com.adamratzman.spotify.SpotifyAppApi
import com.adamratzman.spotify.models.Playlist
import com.adamratzman.spotify.spotifyAppApi
import io.github.cdimascio.dotenv.Dotenv
import kotlinx.coroutines.*
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.MessageEmbed
import net.dv8tion.jda.api.entities.emoji.Emoji
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max

@Suppress("SYNTHETIC_PROPERTY_WITHOUT_JAVA_ORIGIN")
object SpotifyHelper {

    private val dotEnv = Dotenv.load()
    private lateinit var spotify: SpotifyAppApi

    private var coverPlaceholderURLs: List<String> = emptyList()

    private const val COOLDOWN_DURATION = 50L //ms
    private var lastUpdateTime = 0L
    private val updateScheduled = AtomicBoolean(false)
    private val coroutineScope = CoroutineScope(Dispatchers.Default)

    private var lastSeenTrack: TrimmedTrack? = null
    private var lastSeenQueue: List<String> = emptyList()
    private var lastSeenQueueSize: Int = 0
    private var lastSeenRepeat: Boolean? = null
    private var lastSeenPause: Boolean? = null

    init {
        runBlocking {
            initialiseSpotifyAPI()
            SpotifyPlayer.initialize()
        }
        loadCoverPlaceholders()
    }

    private suspend fun initialiseSpotifyAPI() {
        val clientId = dotEnv["SPOTIFY_CLIENT_ID"] ?: ""
        val clientSecret = dotEnv["SPOTIFY_CLIENT_SECRET"] ?: ""

        require(clientId.isNotEmpty()) { "Missing environment variable: SPOTIFY_CLIENT_ID" }
        require(clientSecret.isNotEmpty()) { "Missing environment variable: SPOTIFY_CLIENT_SECRET" }

        spotify = spotifyAppApi(clientId, clientSecret).build()
        println("Spotify API initialized.")
    }

    suspend fun getPlaylist(playlistID: String): Playlist? {
        return spotify.playlists.getPlaylist(playlistID)
    }

    suspend fun addPlaylistToQueue(playlist: Playlist, requesterID: String? = null) {
        val itemsPerPage = 50

        // Fetch the initial page to get total tracks
        val playlistID = playlist.id
        val initialPlaylistPage = playlist.tracks
        val totalTracks = playlist.tracks.total
        val totalPages = (totalTracks + itemsPerPage - 1) / itemsPerPage

        // Process and add the initial batch
        val initialBatch = initialPlaylistPage.items.mapNotNull {
            it.track?.asTrack?.let { track ->
                TrimmedTrack(track).apply { this.requesterID = requesterID ?: "" }
            }
        }
        SpotifyPlayer.addBulkToQueue(initialBatch)

        // Fetch remaining pages in parallel
        coroutineScope {
            (1 until totalPages).map { pageIndex ->
                launch {
                    val offset = pageIndex * itemsPerPage
                    val playlist = spotify.playlists.getPlaylistTracks(playlistID, itemsPerPage, offset)
                    val batch = playlist.items.mapNotNull {
                        it.track?.asTrack?.let { track ->
                            TrimmedTrack(track).apply { this.requesterID = requesterID ?: "" }
                        }
                    }
                    SpotifyPlayer.addBulkToQueue(batch)
                }
            }
        }
    }


    private fun loadCoverPlaceholders() {
        val file = File("cover_placeholders.txt")
        if (file.exists()) {
            coverPlaceholderURLs = file.readLines().filter { it.isNotBlank() }
        } else {
            println("Warning: cover_placeholders.txt not found. Using empty list for placeholders.")
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

    fun updateEmbedMessage() {
        val currentTime = System.currentTimeMillis()
        val timeUntilNextUpdate = max(0, COOLDOWN_DURATION - (currentTime - lastUpdateTime))

        if (timeUntilNextUpdate == 0L && !updateScheduled.getAndSet(true)) { // We can update immediately
            performUpdate()
        } else if (!updateScheduled.getAndSet(true)) { // Schedule the update
            coroutineScope.launch {
                delay(timeUntilNextUpdate)
                performUpdate()
            }
        } // If updateScheduled is already true, we don't need to do anything
        // as an update is already scheduled
    }

    private fun performUpdate() {
        val currentTime = System.currentTimeMillis()

        val currentTrack = SpotifyPlayer.currentTrack
        val currentQueuePreview = SpotifyPlayer.queue.take(5).map { "${it.name} - ${it.artist}" }
        val currentQueueSize = SpotifyPlayer.queue.size
        val currentRepeat = SpotifyPlayer.repeatQueue
        val currentPause = SpotifyPlayer.isPaused

        // Check if any relevant information has changed
        if (currentTrack == lastSeenTrack && currentQueuePreview == lastSeenQueue && currentRepeat == lastSeenRepeat && currentPause == lastSeenPause && currentQueueSize == lastSeenQueueSize) {
            updateScheduled.set(false)
            return
        }

        val channel = Cache.getChannel() ?: run {
            updateScheduled.set(false)
            return
        }

        // Update last known state
        lastSeenTrack = currentTrack
        lastSeenQueue = currentQueuePreview
        lastSeenRepeat = SpotifyPlayer.repeatQueue
        lastSeenPause = SpotifyPlayer.isPaused
        lastSeenQueueSize = currentQueueSize

        val embed = createEmbed()

        val messageId = Cache.messageID
        if (messageId == null) {
            channel.sendMessageEmbeds(embed).queue { message ->
                Cache.messageID = message.id
                Cache.saveChannelAndMessageIDs()
                addReactionsToMessage(message)
                lastUpdateTime = currentTime
                updateScheduled.set(false)
            }
            return
        }

        channel.editMessageEmbedsById(messageId, embed)
            .queue(
                {
                    lastUpdateTime = currentTime
                    updateScheduled.set(false)
                },
                { throwable ->
                    // Handle failure, e.g., message not found or deleted
                    println("Failed to edit message: ${throwable.message}")
                    channel.sendMessageEmbeds(embed).queue { newMessage ->
                        Cache.messageID = newMessage.id
                        Cache.saveChannelAndMessageIDs()
                        addReactionsToMessage(newMessage)
                        lastUpdateTime = currentTime
                        updateScheduled.set(false)
                    }
                }
            )
    }

    fun addReactionsToMessage(message: Message) {
        val emojis = listOf(
            "\uD83D\uDD00",  // Shuffle
            "\u23EE\uFE0F",  // Previous track
            "\u23EF\uFE0F",  // Play/Pause
            "\u23ED\uFE0F",  // Next track
            "\uD83D\uDD01",  // Repeat
            "\u23F9\uFE0F"   // Stop
        )
        emojis.forEach { emojiUnicode ->
            message.addReaction(Emoji.fromUnicode(emojiUnicode)).queue()
        }
    }

    fun createEmbed(): MessageEmbed {
        val embedBuilder = EmbedBuilder()
        val track = SpotifyPlayer.currentTrack

        if (track != null) {
            embedBuilder.setThumbnail(track.albumCover).addField(
                "Track${if (SpotifyPlayer.isPaused) " (Paused)" else ""}", "[${track.name}](${track.url})", false
            ).addField("Artist", "[${track.artist}](${track.artistUrl})", false)

            val nextTracks = SpotifyPlayer.queue.take(5)
            if (nextTracks.isNotEmpty()) {
                val queueString =
                    nextTracks.mapIndexed { index, t -> "${index + 1}. ${t.name} - ${t.artist}" }.joinToString("\n")
                embedBuilder.addField(
                    "Upcoming Tracks - ${SpotifyPlayer.queue.size}${if (SpotifyPlayer.repeatQueue) " (Looped)" else ""}",
                    queueString,
                    false
                )
            } else {
                embedBuilder.addField(
                    "Upcoming Tracks${if (SpotifyPlayer.repeatQueue) " (Looped)" else ""}", "No tracks in queue.", false
                )
            }
        } else {
            val placeholderThumbnail = if (coverPlaceholderURLs.isNotEmpty()) coverPlaceholderURLs.random() else null
            embedBuilder.setThumbnail(placeholderThumbnail).addField("Track", "N/A", false)
                .addField("Artist", "N/A", false).addField(
                    "Upcoming Tracks${if (SpotifyPlayer.repeatQueue) " (Looped)" else ""}", "No tracks in queue.", false
                )
        }
        return embedBuilder.build()
    }
}
