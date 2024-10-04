import com.adamratzman.spotify.models.Track
import kotlinx.serialization.json.*

class TrimmedTrack {
    val name: String
    val duration: Int
    val id: String
    val url: String?
    val uri: String
    val artist: String
    val artistUrl: String?  // Placeholder for artist URL
    val album: String
    val albumCover: String?

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

    //for being returned over websocket json
    constructor(jsonString: String) {
        val json = Json.parseToJsonElement(jsonString).jsonObject
        val item = json["item"]?.jsonObject ?: throw IllegalArgumentException("Invalid JSON: 'item' not found")

        name = item["name"]?.jsonPrimitive?.content ?: throw IllegalArgumentException("Invalid JSON: 'name' not found")
        duration = item["duration"]?.jsonObject?.get("milliseconds")?.jsonPrimitive?.int
            ?: throw IllegalArgumentException("Invalid JSON: 'duration.milliseconds' not found")
        id = item["uri"]?.jsonPrimitive?.content?.split(":")?.lastOrNull()
            ?: throw IllegalArgumentException("Invalid JSON: 'uri' not found or invalid")
        url = "https://open.spotify.com/track/$id"
        uri = item["uri"]?.jsonPrimitive?.content ?: throw IllegalArgumentException("Invalid JSON: 'uri' not found")
        artist = item["artists"]?.jsonArray?.getOrNull(0)?.jsonObject?.get("name")?.jsonPrimitive?.content
            ?: throw IllegalArgumentException("Invalid JSON: 'artists[0].name' not found")
        artistUrl =
            item["artists"]?.jsonArray?.getOrNull(0)?.jsonObject?.get("external_urls")?.jsonObject?.get("spotify")?.jsonPrimitive?.content
        album = item["album"]?.jsonObject?.get("name")?.jsonPrimitive?.content
            ?: throw IllegalArgumentException("Invalid JSON: 'album.name' not found")
        albumCover =
            item["album"]?.jsonObject?.get("images")?.jsonArray?.getOrNull(0)?.jsonObject?.get("url")?.jsonPrimitive?.content
    }
}
