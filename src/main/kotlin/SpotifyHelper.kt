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

    // Variables to store the last known state
    private var lastTrackName: String? = null
    private var lastTrackArtist: String? = null
    private var lastQueue: List<String> = emptyList()
    private var lastLoopStatus: Boolean? = null

    init {
        runBlocking {
            initialiseSpotifyAPI()
            println("Waiting for SpotifyPlayer initialization")
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
        val currentQueue = SpotifyPlayer.queue.take(5).map { "${it.name} by ${it.artist}" }

        // Check if any relevant information has changed
        if (currentTrack?.name == lastTrackName && currentTrack?.artist == lastTrackArtist && currentQueue == lastQueue && lastLoopStatus == SpotifyPlayer.repeatQueue) {
            // No changes, no need to update
            return
        }

        // Update last known state
        lastTrackName = currentTrack?.name
        lastTrackArtist = currentTrack?.artist
        lastQueue = currentQueue
        lastLoopStatus = SpotifyPlayer.repeatQueue

        val channel = PeopleBot.jda.getTextChannelById(PeopleBot.EMBED_CHANNEL_ID) ?: return
        val embed = createEmbed()

        val messageId = PeopleBot.CURRENT_TRACK_EMBED_ID
        if (messageId.isEmpty()) {
            channel.sendMessageEmbeds(embed).queue { message ->
                PeopleBot.CURRENT_TRACK_EMBED_ID = message.id
                PeopleBot.saveMessageIDs()
                addReactionsToMessage(message)
            }
        } else {
            channel.retrieveMessageById(messageId).queue { message ->
                if (message != null) {
                    message.editMessageEmbeds(embed).queue()
                } else {
                    channel.sendMessageEmbeds(embed).queue { newMessage ->
                        PeopleBot.CURRENT_TRACK_EMBED_ID = newMessage.id
                        PeopleBot.saveMessageIDs()
                        addReactionsToMessage(newMessage)
                    }
                }
            }
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
                    nextTracks.mapIndexed { index, t -> "${index + 1}. ${t.name} by ${t.artist}" }.joinToString("\n")
                embedBuilder.addField("Upcoming Tracksᅟᅟᅟᅟᅟﾠ", queueString, false) //dont fuck the spacing up
            } else {
                embedBuilder.addField("Upcoming Tracksᅟᅟᅟᅟᅟﾠ", "No tracks in queue.", false) //dont fuck the spacing up
            }
                .addField("", "Looping ${if (SpotifyPlayer.repeatQueue) "Enabled" else "Disabled"}", true)
        } else {
            embedBuilder
                .setThumbnail(coverPlaceholderURLs.random())
                .addField("Track", "N/A", false)
                .addField("Artist", "N/A", false)
                .addField("Upcoming Tracksᅟᅟᅟᅟᅟﾠ", "No tracks in queue.", false) //dont fuck the spacing up
        }

        return embedBuilder.build()
    }
}