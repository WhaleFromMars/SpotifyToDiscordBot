(async function () {
    while (!Spicetify.React || !Spicetify.ReactDOM) {
        await new Promise(resolve => setTimeout(resolve, 10));
    }
    var localDapi = (() => {
        // src/app.tsx
        async function main() {
            while (!(Spicetify == null ? void 0 : Spicetify.showNotification)) {
                await new Promise((resolve) => setTimeout(resolve, 100));
            }
            console.log("trying connection");

            function connectWebSocket() {
                const socket = new WebSocket("ws://localhost:8080");
                socket.onopen = () => {
                    console.log("WebSocket connection established");
                };
                socket.onmessage = (event) => {
                    const message = event.data;
                    handleCommand(message);
                };
                socket.onclose = () => {
                    console.log("WebSocket connection closed, retrying in 1 second...");
                    setTimeout(connectWebSocket, 1e3);
                };
                socket.onerror = (error) => {
                    console.error("WebSocket error:", error);
                    socket.close();
                };
            }

            function handleCommand(message) {
                const [command, param] = message.split("|");
                switch (command) {
                    case "pause":
                        Spicetify.Player.pause();
                        break;
                    case "play":
                        Spicetify.Player.play();
                        break;
                    case "playUri":
                        if (param) {
                            Spicetify.Player.playUri(param);
                        }
                        break;
                    case "volume":
                        if (param) {
                            const volume = parseInt(param, 10);
                            Spicetify.Player.setVolume(volume);
                        }
                        break;
                    case "repeat":
                        if (param) {
                            const repeatMode = parseInt(param, 10);
                            Spicetify.Player.setRepeat(repeatMode);
                        }
                        break;
                    case "mute":
                        if (param) {
                            const mute = param === "true";
                            Spicetify.Player.setMute(mute);
                        }
                        break;
                    case "shuffle":
                        if (param) {
                            const shuffle = param === "true";
                            Spicetify.Player.setShuffle(shuffle);
                        }
                        break;
                    case "getProgress":
                        const progress = Spicetify.Player.getProgress();
                        console.log("Current Progress:", progress);
                        break;
                    default:
                        console.warn("Unknown command:", command);
                }
            }

            connectWebSocket();
            console.log("connected client");
            Spicetify.showNotification("WebSocket client started!");
        }

        var app_default = main;

        // ../../../AppData/Local/Temp/spicetify-creator/index.jsx
        (async () => {
            await app_default();
        })();
    })();

})();