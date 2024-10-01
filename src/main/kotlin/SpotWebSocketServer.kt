// MyWebSocketServer.kt
import org.java_websocket.WebSocket
import org.java_websocket.handshake.ClientHandshake
import org.java_websocket.server.WebSocketServer
import java.net.InetSocketAddress

class SpotWebSocketServer(port: Int) : WebSocketServer(InetSocketAddress(port)) {

    private var clientConnection: WebSocket? = null

    override fun onOpen(conn: WebSocket, handshake: ClientHandshake) {
        if (clientConnection == null) {
            println("New connection from ${conn.remoteSocketAddress}")
            clientConnection = conn
            sendCommand("volume", "100")
            sendCommand("repeat", "0")
            sendCommand("pause")
        } else {
            println("A client is already connected. Rejecting connection from ${conn.remoteSocketAddress}")
            conn.close(1000, "Only one client allowed at a time")
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

        println("Sending message: $message")
        clientConnection?.send(message)
        println("client is null: ${clientConnection == null}")
    }

    fun stopServer() {
        clientConnection?.close(1000, "Server shutting down")
        this.stop()
        println("WebSocket server stopped")
    }
}
