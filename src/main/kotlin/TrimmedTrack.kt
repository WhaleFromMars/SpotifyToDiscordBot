import com.adamratzman.spotify.models.Track

class TrimmedTrack(fullTrack: Track) {

    init {
        println(fullTrack.album.images)
    }

    val name = fullTrack.name
    val duration = fullTrack.durationMs.toInt()
    val id = fullTrack.id
    val artist = fullTrack.artists[0]
    val album = fullTrack.album
    val albumLink = album.externalUrls.spotify
    val albumCover = album.images?.get(0)?.url
}