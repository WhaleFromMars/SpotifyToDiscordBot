(async () => {
    for (; !Spicetify.React || !Spicetify.ReactDOM;) await new Promise(e => setTimeout(e, 10));
    var e;
    e = async function () {
        for (; null == Spicetify || !Spicetify.showNotification;) await new Promise(e => setTimeout(e, 100));
        console.log("trying connection");
        let i = Spicetify.Player;
        !function e() {
            let r = new WebSocket("ws://localhost:8080"), t = (r.onopen = () => {
                console.log("WebSocket connection established");
                var e = i.data;
                null != e && r.send("playerState|" + JSON.stringify(e))
            }, r.onmessage = e => {
                var e = e.data, a = r;
                console.log(e);
                var t, [n, o] = e.split("|");
                switch (n) {
                    case"ping":
                        a.send("pong");
                        break;
                    case"pong":
                        break;
                    case"pause":
                        i.pause();
                        break;
                    case"play":
                        i.play();
                        break;
                    case"playUri":
                        o && (console.log(o), i.playUri(o));
                        break;
                    case"volume":
                        o && (t = parseInt(o, 10), i.setVolume(t));
                        break;
                    case"repeat":
                        o && (t = parseInt(o, 10), Spicetify.Player.setRepeat(t));
                        break;
                    case"mute":
                        o && Spicetify.Player.setMute("true" === o);
                        break;
                    case"shuffle":
                        o && Spicetify.Player.setShuffle("true" === o);
                        break;
                    case"getProgress":
                        var s = Spicetify.Player.getProgress();
                        a.send(s.toString());
                        break;
                    default:
                        console.warn("Unknown command:", n)
                }
            }, r.onclose = () => {
                console.log("WebSocket connection closed, retrying in 1 second..."), setTimeout(e, 1e3)
            }, r.onerror = e => {
                console.error("WebSocket error:", e), r.close()
            }, i.addEventListener("appchange", e => {
                e && e.data && r.send("something|" + JSON.stringify(e.data))
            }), 0), n = 0;
            i.addEventListener("onprogress", e => {
                var a;
                e && e.data && (a = Date.now(), (e = e.data) !== n) && 250 <= a - t && (r.send("progress|" + e), t = a, n = e)
            }), i.addEventListener("onplaypause", e => {
                e && e.data && r.send("playerState|" + JSON.stringify(e.data))
            }), i.addEventListener("songchange", e => {
                e && e.data && r.send("playerState|" + JSON.stringify(e.data))
            })
        }(), console.log("connected client"), Spicetify.showNotification("WebSocket client started!")
    }, (async () => {
        await e()
    })()
})();