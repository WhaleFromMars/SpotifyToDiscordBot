package helpers

import com.adamratzman.spotify.SpotifyAppApi
import com.adamratzman.spotify.models.Album
import com.adamratzman.spotify.models.Playlist
import com.adamratzman.spotify.spotifyAppApi
import data.TrackRemovalStats
import data.TrimmedTrack
import io.github.cdimascio.dotenv.Dotenv
import kotlinx.coroutines.*

object SpotifyHelper {

    private val dotEnv = Dotenv.configure().ignoreIfMissing().load()
    private lateinit var spotify: SpotifyAppApi

    suspend fun initialize() {
        initialiseSpotifyAPI()
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

    suspend fun getAlbum(albumID: String): Album? {
        return spotify.albums.getAlbum(albumID)
    }

    suspend fun addPlaylistToQueue(playlist: Playlist, requesterID: String? = null) {
        val itemsPerPage = 50
        val playlistID = playlist.id
        val totalTracks = playlist.tracks.total
        val totalPages = (totalTracks + itemsPerPage - 1) / itemsPerPage
        val removalStats = TrackRemovalStats()

        PeopleBot.startStreaming()

        coroutineScope {
            val fetchJobs = (0 until totalPages).map { page ->
                launch(Dispatchers.IO + SupervisorJob()) {
                    fetchAndProcessPage(
                        page, itemsPerPage, playlistID, requesterID, removalStats
                    )
                }
            }

            fetchJobs.joinAll()
            EmbedHelper.updateEmbedMessage(forceUpdate = true)
        }
    }

    private suspend fun fetchAndProcessPage(
        page: Int,
        itemsPerPage: Int,
        playlistID: String,
        requesterID: String?,
        removalStats: TrackRemovalStats
    ) {
        // Fetch tracks for the current page
        val pageTracks = spotify.playlists.getPlaylistTracks(playlistID, itemsPerPage, page * itemsPerPage)

        // Process tracks
        val tracksToAdd = pageTracks.items.mapNotNull {
            val track = it.track?.asTrack
            if (track == null) {
                removalStats.nullTracks.incrementAndGet()
                return@mapNotNull null
            }
            if (!track.isPlayable) {
                removalStats.unplayableTracks.incrementAndGet()
                return@mapNotNull null
            }
            if (track.isLocal == true) { //TODO support local files *in our own playlists*
                removalStats.localTracks.incrementAndGet()
                return@mapNotNull null
            }

            TrimmedTrack(track).apply { this.requesterID = requesterID ?: "" }
        }

        if (tracksToAdd.isNotEmpty()) {
            SpotifyPlayer.addBulkToQueue(tracksToAdd)
        } else {
            println("No tracks to add from page $page.")
        }
    }

    suspend fun searchTrack(query: String, returnAmount: Int = 1): List<TrimmedTrack>? {
        val tracks = spotify.search.searchTrack(query, limit = returnAmount).map { TrimmedTrack(it!!) }
        return if (tracks.isNotEmpty()) tracks else null
    }

    suspend fun getTrack(id: String): TrimmedTrack? {
        //TODO:: market check, dont add if unavailable, no idea how to get user market rn
        val track = spotify.tracks.getTrack(id)
        return if (track != null) TrimmedTrack(track) else null
    }
}
