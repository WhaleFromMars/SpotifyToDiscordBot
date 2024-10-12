package helpers

import data.TrimmedTrack
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.MessageEmbed
import net.dv8tion.jda.api.entities.emoji.Emoji
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max

object EmbedHelper {

    data class EmbedState(
        val track: TrimmedTrack?,
        val queuePreview: List<String>,
        val queueSize: Int,
        val repeat: Boolean?,
        val paused: Boolean?
    )

    private var coverPlaceholderURLs: List<String> = emptyList()
    private const val FALLBACK_COVER_URL = "https://example.com/default-cover.png"

    private const val COOLDOWN_DURATION = 100L //ms
    private var lastUpdateTime = 0L
    private val updateScheduled = AtomicBoolean(false)

    private var lastSentState: EmbedState? = null

    init {
        loadCoverPlaceholders()
    }

    private fun loadCoverPlaceholders() {
        val file = File("cover_placeholders.txt")
        if (file.exists()) {
            coverPlaceholderURLs = file.readLines().filter { it.isNotBlank() }
        } else {
            println("Warning: cover_placeholders.txt not found. Using fallback URL.")
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

    fun updateEmbedMessage(forceUpdate: Boolean = false) {
        //        println("embed update called")
        val currentTime = System.currentTimeMillis()
        val timeUntilNextUpdate = max(0, COOLDOWN_DURATION - (currentTime - lastUpdateTime))

        if (timeUntilNextUpdate == 0L && !updateScheduled.getAndSet(true)) {
            //            println("performing update")
            performUpdate(forceUpdate)
        } else if (!updateScheduled.getAndSet(true)) {
            PeopleBot.scope.launch {
                //                println("delaying update")
                delay(timeUntilNextUpdate)
                performUpdate(forceUpdate)
            }
        }
    }

    private fun performUpdate(forceUpdate: Boolean = false) {
        val currentTime = System.currentTimeMillis()

        val currentState = SpotifyPlayer.getEmbedState()

        // Skip state check if forceUpdate is true
        if (!forceUpdate && currentState == lastSentState) {
            //            println("same state, not updating")
            updateScheduled.set(false)
            return
        }
        //        println("state diff:")
        //        println(currentState)
        //        println(lastSeenState)

        val channel = Cache.getChannel() ?: run {
            updateScheduled.set(false)
            return
        }

        val embed = createEmbed(currentState)

        val messageId = Cache.messageID
        if (messageId == null) {
            //            println("message id null, sending fresh one")
            channel.sendMessageEmbeds(embed).queue { message ->
                Cache.messageID = message.id
                Cache.saveChannelAndMessageIDs()
                addReactionsToMessage(message)
                lastUpdateTime = currentTime
                updateScheduled.set(false)
            }
            return
        }

        //        println("updating embed message")
        channel.editMessageEmbedsById(messageId, embed).queue({
            lastUpdateTime = currentTime
            updateScheduled.set(false)
        }, { throwable ->
            //            println("Failed to edit message: ${throwable.localizedMessage}")
            channel.sendMessageEmbeds(embed).queue { newMessage ->
                Cache.messageID = newMessage.id
                Cache.saveChannelAndMessageIDs()
                addReactionsToMessage(newMessage)
                lastUpdateTime = currentTime
                updateScheduled.set(false)
            }
        })
    }

    fun createEmbed(state: EmbedState): MessageEmbed {
        lastSentState = state
        val embedBuilder = EmbedBuilder()
        val placeholderThumbnail = coverPlaceholderURLs.randomOrNull() ?: FALLBACK_COVER_URL

        if (state.track != null) {
            embedBuilder.setThumbnail(state.track.albumCover).addField(
                "Track${if (state.paused == true) " (Paused)" else ""}",
                "[${state.track.name}](${state.track.url})",
                false
            ).addField("Artist", "[${state.track.artist}](${state.track.artistUrl})", false)

            val queueString = state.queuePreview.mapIndexed { index, t -> "${index + 1}. $t" }.joinToString("\n")
            embedBuilder.addField(
                "Upcoming Tracks - ${state.queueSize}${if (state.repeat == true) " (Looped)" else ""}",
                if (queueString.isNotEmpty()) queueString else "No tracks in queue.",
                false
            )
        } else {
            embedBuilder.setThumbnail(placeholderThumbnail).addField("Track", "N/A", false)
                .addField("Artist", "N/A", false).addField(
                    "Upcoming Tracks${if (state.repeat == true) " (Looped)" else ""}", "No tracks in queue.", false
                )
        }
        return embedBuilder.build()
    }
}
