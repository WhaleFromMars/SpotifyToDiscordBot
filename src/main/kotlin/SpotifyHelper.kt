import com.adamratzman.spotify.SpotifyAppApi
import com.adamratzman.spotify.spotifyAppApi
import io.github.cdimascio.dotenv.Dotenv
import kotlinx.coroutines.*
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.MessageEmbed
import net.dv8tion.jda.api.entities.emoji.Emoji
import java.io.File

object SpotifyHelper {
    private val dotEnv = Dotenv.load()
    private lateinit var spotify: SpotifyAppApi

    private var coverPlaceholderURLs: List<String> = emptyList()

    private var lastSeenTrack: TrimmedTrack? = null
    private var lastSeenQueue: List<String> = emptyList()
    private var lastSeenRepeat: Boolean? = null

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
        if (PeopleBot.EMBED_CHANNEL_ID.isEmpty()) return

        val currentTrack = SpotifyPlayer.currentTrack
        val currentQueue = SpotifyPlayer.queue.take(5).map { "${it.name} - ${it.artist}" }
        val currentRepeat = SpotifyPlayer.repeatQueue

        // Check if any relevant information has changed
        if (currentTrack == lastSeenTrack && currentQueue == lastSeenQueue && lastSeenRepeat == currentRepeat) {
            return // Nothing has changed
        }
        val channel =
            PeopleBot.jda.getTextChannelById(PeopleBot.EMBED_CHANNEL_ID) ?: return //expensive so fail after cheap stuff
        // Update last known state
        lastSeenTrack = currentTrack
        lastSeenQueue = currentQueue
        lastSeenRepeat = SpotifyPlayer.repeatQueue

        val embed = createEmbed()

        val messageId = PeopleBot.CURRENT_TRACK_EMBED_ID
        if (messageId.isEmpty()) {
            channel.sendMessageEmbeds(embed).queue { message ->
                PeopleBot.CURRENT_TRACK_EMBED_ID = message.id
                PeopleBot.saveMessageIDs()
                addReactionsToMessage(message)
            }
        } else {
            channel.retrieveMessageById(messageId).queue({ message ->
                message.editMessageEmbeds(embed).queue()
            }, { throwable ->
                // Handle failure, e.g., message not found or deleted
                channel.sendMessageEmbeds(embed).queue { newMessage ->
                    PeopleBot.CURRENT_TRACK_EMBED_ID = newMessage.id
                    PeopleBot.saveMessageIDs()
                    addReactionsToMessage(newMessage)
                }
            })
        }
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
            embedBuilder
                .setThumbnail(track.albumCover)
                .addField("Track", "[${track.name}](${track.url})", false)
                .addField("Artist", "[${track.artist}](${track.artistUrl})", false)

            val nextTracks = SpotifyPlayer.queue.take(5)
            if (nextTracks.isNotEmpty()) {
                val queueString =
                    nextTracks.mapIndexed { index, t -> "${index + 1}. ${t.name} - ${t.artist}" }.joinToString("\n")
                embedBuilder.addField(
                    "Upcoming Tracks${if (SpotifyPlayer.repeatQueue) " (Looped)" else ""}",
                    queueString,
                    false
                )
            } else {
                embedBuilder.addField(
                    "Upcoming Tracks${if (SpotifyPlayer.repeatQueue) " (Looped)" else ""}",
                    "No tracks in queue.",
                    false
                )
            }
        } else {
            val placeholderThumbnail = if (coverPlaceholderURLs.isNotEmpty()) coverPlaceholderURLs.random() else null
            embedBuilder
                .setThumbnail(placeholderThumbnail)
                .addField("Track", "N/A", false)
                .addField("Artist", "N/A", false)
                .addField(
                    "Upcoming Tracks${if (SpotifyPlayer.repeatQueue) " (Looped)" else ""}",
                    "No tracks in queue.",
                    false
                )
        }

        return embedBuilder.build()
    }
}
