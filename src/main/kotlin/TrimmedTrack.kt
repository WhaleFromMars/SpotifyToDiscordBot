import com.adamratzman.spotify.models.Track

class TrimmedTrack(fullTrack: Track) {
    val name = fullTrack.name
    val duration = fullTrack.durationMs
    val id = fullTrack.id
    val url = fullTrack.externalUrls.spotify
    val uri = fullTrack.uri
    val artist = fullTrack.artists[0]
    val album = fullTrack.album
    val albumCover = album.images?.get(0)?.url //the highest resolution image

}