package data

import com.adamratzman.spotify.models.Track

class TrimmedTrack {
    val name: String
    val duration: Int
    val id: String
    val url: String?
    val uri: String
    val artist: String
    val artistUrl: String?
    val album: String
    val albumCover: String?
    var requesterID: String = ""

    //for being returned over spotify api
    constructor(fullTrack: Track) {
        name = fullTrack.name
        duration = fullTrack.durationMs
        id = fullTrack.id
        url = fullTrack.externalUrls.spotify
        uri = fullTrack.uri.uri
        artist = fullTrack.artists[0].name.toString()
        artistUrl = fullTrack.artists[0].externalUrls.spotify
        album = fullTrack.album.name
        albumCover = fullTrack.album.images?.get(0)?.url
    }
}
