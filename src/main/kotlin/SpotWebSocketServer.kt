// MyWebSocketServer.kt
import kotlinx.coroutines.future.await
import kotlinx.coroutines.withTimeout
import org.java_websocket.WebSocket
import org.java_websocket.handshake.ClientHandshake
import org.java_websocket.server.WebSocketServer
import java.net.InetSocketAddress
import java.util.concurrent.CompletableFuture
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class SpotWebSocketServer(port: Int) : WebSocketServer(InetSocketAddress(port)) {

    private var clientConnection: WebSocket? = null
    private val connectionFuture = CompletableFuture<Unit>()

    override fun onOpen(conn: WebSocket, handshake: ClientHandshake) {
        if (clientConnection == null) {
            println("New connection from ${conn.remoteSocketAddress}")
            clientConnection = conn
            sendCommand("volume", "100")
            sendCommand("repeat", "0")
            sendCommand("pause")
            connectionFuture.complete(Unit)
        } else {
            println("A client is already connected. Rejecting connection from ${conn.remoteSocketAddress}")
            conn.close(1000, "Only one client allowed at a time")
        }
    }

    suspend fun waitForConnection(timeout: Duration = 30.seconds) {
        withTimeout(30.seconds) {
            connectionFuture.await()
        }
    }

    override fun onClose(conn: WebSocket, code: Int, reason: String, remote: Boolean) {
        println("Connection closed: ${conn.remoteSocketAddress}")
        if (conn == clientConnection) {
            clientConnection = null
        }
    }

    override fun onMessage(conn: WebSocket, message: String) {
        println("Received message: $message")
        // Handle incoming messages if needed
    }

    override fun onError(conn: WebSocket?, ex: Exception) {
        println("Error on connection ${conn?.remoteSocketAddress}: ${ex.message}")
    }

    override fun onStart() {
        println("WebSocket server started on port ${address.port}")
    }

    fun sendCommand(command: String, parameter: String? = null) {
        val message = if (parameter != null) {
            "$command|$parameter"
        } else {
            command
        }

        clientConnection?.send(message)
    }

    fun stopServer() {
        clientConnection?.close(1000, "Server shutting down")
        this.stop()
        println("WebSocket server stopped")
    }
}
