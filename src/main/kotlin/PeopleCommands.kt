import PeopleBot.logger
import data.TrimmedTrack
import helpers.EmbedHelper
import helpers.SpotifyHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.dv8tion.jda.api.entities.GuildVoiceState
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.interactions.commands.Command.Choice
import java.util.concurrent.TimeUnit

object PeopleCommands {

    fun nowPlayingChannelSlashCommand(event: SlashCommandInteractionEvent) {
        val channel =
            event.getOption("channel")?.asChannel ?: return event.reply("Please provide a channel").setEphemeral(true)
                .queue()

        Cache.channelID = channel.id
        Cache.messageID = null

        val currentState = SpotifyPlayer.getEmbedState()

        channel.asTextChannel().sendMessageEmbeds(EmbedHelper.createEmbed(currentState)).queue { message ->
            Cache.messageID = message.id
            Cache.saveChannelAndMessageIDs()
            EmbedHelper.addReactionsToMessage(message)
        }

        event.reply("Now playing channel set to ${channel.asMention}").setEphemeral(true).queue()
    }

    fun clearQueueSlashCommand(event: SlashCommandInteractionEvent) {
        if (SpotifyPlayer.queue.isEmpty()) {
            event.reply("The queue is empty.").setEphemeral(true).queue()
            return
        }

        SpotifyPlayer.clearQueue()
        event.reply("Queue cleared").setEphemeral(true).queue()
    }

    fun removeSlashCommand(event: SlashCommandInteractionEvent) {
        if (SpotifyPlayer.queue.isEmpty()) {
            event.reply("The queue is empty.").setEphemeral(true).queue()
            return
        }

        val option = event.getOption("index")
        val userInput = option?.asString

        if (userInput.isNullOrBlank()) {
            event.reply("Please provide a valid index or song name.").setEphemeral(true).queue()
            return
        }

        val queueSize = SpotifyPlayer.queue.size

        val index = userInput.toIntOrNull()?.let { it - 1 }
        val removedSong = if (index != null && index in 0 until queueSize) {
            val track = SpotifyPlayer.queue.elementAt(index)
            SpotifyPlayer.removeFromQueue(track)
            track
        } else {
            val trackIndex = SpotifyPlayer.queue.indexOfFirst { track ->
                track.name.equals(userInput, ignoreCase = true) || track.artist.equals(userInput, ignoreCase = true)
            }
            if (index != null && trackIndex != -1) {
                val track = SpotifyPlayer.queue.elementAt(index)
                SpotifyPlayer.removeFromQueue(track)
                track
            } else {
                event.reply("No matching song found in the queue.").setEphemeral(true).queue()
                return
            }
        }

        event.reply("Removed **${removedSong.name}** by **${removedSong.artist}** from the queue.").queue { message ->
            PeopleBot.scope.launch {
                delay(TimeUnit.SECONDS.toMillis(5))
                message.deleteOriginal().queue()
            }
        }
    }

    fun whoSlashCommand(event: SlashCommandInteractionEvent) {
        val option = event.getOption("index")
        val userInput = option?.asString
        if (userInput == null) {
            val requesterID =
                SpotifyPlayer.currentTrack?.requesterID ?: return event.reply("Unknown Requester").setEphemeral(true)
                    .queue()

            event.reply("Song Requested By: <@${requesterID}>").setEphemeral(true).queue()
            return
        }

        val queueSize = SpotifyPlayer.queue.size

        val index = userInput.toIntOrNull()?.let { it - 1 }
        val querySong = if (index != null && index in 0 until queueSize) {
            SpotifyPlayer.queue.elementAt(index)
        } else {
            val trackIndex = SpotifyPlayer.queue.indexOfFirst { track ->
                track.name.equals(userInput, ignoreCase = true) || track.artist.equals(userInput, ignoreCase = true)
            }
            if (index != null && trackIndex != -1) {
                SpotifyPlayer.queue.elementAt(index)
            } else {
                event.reply("No matching song found in the queue.").setEphemeral(true).queue()
                return
            }
        }

        event.reply("Song Requested By: <@${querySong.requesterID}>").setEphemeral(true).queue()
    }

    suspend fun playSlashCommand(event: SlashCommandInteractionEvent) {
        val member = event.member ?: return
        val guild = event.guild ?: return

        val authorVoiceChannel = member.voiceState?.channel
        if (authorVoiceChannel == null) {
            event.reply("You need to be in a voice channel to use this command.").setEphemeral(true).queue()
            return
        }

        val botSelfMember = guild.selfMember
        val botVoiceState: GuildVoiceState = botSelfMember.voiceState ?: return
        val botVoiceChannel = botVoiceState.channel

        if (botVoiceChannel != null && botVoiceChannel != authorVoiceChannel && botVoiceChannel.members.size > 1) {
            event.reply("I'm already in a voice channel with other members.").setEphemeral(true).queue()
            return
        } else {
            guild.audioManager.openAudioConnection(authorVoiceChannel)
            guild.audioManager.sendingHandler = AudioStreamHandler
        }

        val songQuery: String = event.getOption("song")?.asString ?: return
        if (songQuery.isBlank()) {
            event.reply("Please provide a song name or Spotify URL.").setEphemeral(true).queue()
            return
        }

        when {
            // Playlist link query
            songQuery.startsWith("https://open.spotify.com/playlist/") -> {
                val playlistId = songQuery.substringAfter("https://open.spotify.com/playlist/").substringBefore("?si")
                val playlist = SpotifyHelper.getPlaylist(playlistId)
                if (playlist == null) {
                    event.reply("Couldn't find the playlist.").setEphemeral(true).queue()
                    return
                }
                val playlistName = playlist.name
                val totalTracks = playlist.tracks.total

                event.reply("Processing **$totalTracks** tracks from **$playlistName**.").setEphemeral(true).queue()

                SpotifyHelper.addPlaylistToQueue(playlist, event.user.id)
            }

            // Track link query
            songQuery.startsWith("https://open.spotify.com/track/") -> {
                val trackId = songQuery.substringAfter("https://open.spotify.com/track/").substringBefore("?si")
                val track = SpotifyHelper.getTrack(trackId) ?: run {
                    event.reply("Couldn't match provided link: $songQuery").setEphemeral(true).queue()
                    return
                }
                track.requesterID = event.user.id
                SpotifyPlayer.addToQueue(track)
                PeopleBot.startStreaming()
                event.reply("Added **${track.name}** to the queue.").setEphemeral(true).queue()
            }

            //album link query
            songQuery.startsWith("https://open.spotify.com/album/") -> {
                val albumId = songQuery.substringAfter("https://open.spotify.com/album/").substringBefore("?si")
                val album = SpotifyHelper.getAlbum(albumId) ?: run {
                    event.reply("Couldn't match provided link: $songQuery").setEphemeral(true).queue()
                    return
                }
                val tracks = album.tracks.items.mapNotNull { it.toFullTrack()?.let { it1 -> TrimmedTrack(it1) } }
                SpotifyPlayer.addBulkToQueue(tracks)
                PeopleBot.startStreaming()
                event.reply("Added **${tracks.size}** tracks from **${album.name}** to the queue")
            }

            // Autocomplete query
            songQuery.startsWith("id:") -> {
                val trackId = songQuery.substringAfter("id:")
                val track = SpotifyHelper.getTrack(trackId) ?: run {
                    event.reply("Something went wrong").setEphemeral(true).queue()
                    return
                }
                track.requesterID = event.user.id
                SpotifyPlayer.addToQueue(track)
                PeopleBot.startStreaming()
                event.reply("Added **${track.name}** to the queue.").setEphemeral(true).queue()
            }

            // String input query
            else -> {
                val searchedTracks = SpotifyHelper.searchTrack(songQuery, returnAmount = 1)
                if (searchedTracks.isNullOrEmpty()) {
                    event.reply("No tracks found for your query.").setEphemeral(true).queue()
                    return
                }
                val track = searchedTracks.first()
                track.requesterID = event.user.id
                SpotifyPlayer.addToQueue(track)
                PeopleBot.startStreaming()
                event.reply("Added **${track.name}** to the queue.").setEphemeral(true).queue()
            }
        }
    }

    suspend fun handleAutoComplete(event: CommandAutoCompleteInteractionEvent) {
        when {
            event.name == "play" && event.focusedOption.name == "song" -> {
                val userInput = event.focusedOption.value
                if (userInput.isEmpty() || userInput.startsWith("https://open.spotify.com/")) {
                    event.replyChoices(emptyList()).queue()
                    return
                }

                val tracks = SpotifyHelper.searchTrack(userInput, returnAmount = 5)

                if (tracks.isNullOrEmpty()) {
                    event.replyChoices(emptyList()).queue()
                    return
                }

                val options = tracks.map { track ->
                    val trackName = if (track.name.length < 50) track.name else "${track.name.take(50)}..."
                    val artistName = if (track.artist.length < 40) track.artist else "${track.artist.take(40)}..."
                    Choice("$trackName - $artistName", "id:${track.id}")
                }

                event.replyChoices(options).queue()
            }

            event.name == "remove" && event.focusedOption.name == "index" -> {
                val userInput = event.focusedOption.value

                val tracks = SpotifyPlayer.queue

                if (tracks.isEmpty()) {
                    event.replyChoices(emptyList()).queue()
                    return
                }

                val options = tracks.mapIndexedNotNull { i, track ->
                    val displayIndex = i + 1
                    val indexString = displayIndex.toString()
                    val matchesInput = userInput.isEmpty() || indexString.startsWith(userInput) || track.name.contains(
                        userInput, ignoreCase = true
                    )

                    if (matchesInput) {
                        val trackName = if (track.name.length <= 45) track.name else "${track.name.take(45)}..."
                        val artistName = if (track.artist.length <= 30) track.artist else "${track.artist.take(30)}..."
                        Choice("$displayIndex. $trackName - $artistName", "$displayIndex")
                    } else {
                        null
                    }
                }.take(25)

                event.replyChoices(options).queue()
            }

            event.name == "who" && event.focusedOption.name == "index" -> {
                val userInput = event.focusedOption.value

                val tracks = SpotifyPlayer.queue

                if (tracks.isEmpty()) {
                    event.replyChoices(emptyList()).queue()
                    return
                }

                val options = tracks.mapIndexedNotNull { i, track ->
                    val displayIndex = i + 1
                    val indexString = displayIndex.toString()
                    val matchesInput = userInput.isEmpty() || indexString.startsWith(userInput) || track.name.contains(
                        userInput, ignoreCase = true
                    )

                    if (matchesInput) {
                        val trackName = if (track.name.length <= 45) track.name else "${track.name.take(45)}..."
                        val artistName = if (track.artist.length <= 30) track.artist else "${track.artist.take(30)}..."
                        Choice("$displayIndex. $trackName - $artistName", "$displayIndex")
                    } else {
                        null
                    }
                }.take(25)

                event.replyChoices(options).queue()
            }

            else -> {
                event.replyChoices(emptyList()).queue()
            }
        }
    }
}