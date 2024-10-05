# Spotify to Discord Bot

⚠️ **IMPORTANT: This project is for educational purposes only** ⚠️

This proof-of-concept demonstrates streaming Spotify into a Discord voice chat, only one server may be connected to. Please be aware of and respect all relevant terms of service, copyright laws, and licensing agreements when using or modifying this code.

Progress:
- [x] Stream client to voice channel
- [x] Hook up local api via spicetify to support non premium users
- [x] Basic queue functionality (cant rely on spotifys as it doesnt allow you to remove from queue)
  - [x] Shuffle
  - [ ] Repeat song/queue
- [x] Rich Embed
- [x] Control features
  - [x] From embed
  - [x] From command
- [ ] Audio processing
- [ ] Tracking for a spotify wrapped alternative

## Prerequisites

- A Spotify [(Spicetify)](https://spicetify.app/docs/getting-started) client running on the same device as the bot
- add the extension found in this repo (local-api.js) to spicetifys extensions
- Virtual Cables on Windows (alternative required for Linux)
- Discord Bot Token
- Spotify Client Secret and Key

## Setup Instructions

### 1. Discord Bot Setup

1. Visit https://discord.com/developers/applications
2. Create a new application
3. In the "Installation" tab:
   - Remove user install
   - Add the "bot" scope
4. In the "Bot" tab:
   - Reset token
   - Copy the token

### 2. Spotify API Setup

1. Go to https://developer.spotify.com/dashboard
2. Create a new app
3. Enable Web API
4. Save changes
5. In Settings, copy the Client ID and Client Secret

### 3. Environment Configuration

1. Rename `.envExample` to `.env`
2. Open `.env` and fill in the following:
```env
DISCORD_BOT_TOKEN=your_discord_bot_token_here
DISCORD_GUILD_ID=your_servers_id_here
SPOTIFY_CLIENT_ID=your_spotify_client_id_here
SPOTIFY_CLIENT_SECRET=your_spotify_client_secret_here
SPOTIFY_EXE_PATH=c://user/etc/etc/etc//Spotify.exe
```

### 4. Audio Setup

- On Windows: Install Virtual Cables
- On Linux: Use an alternative to Virtual Cables
- Change the `cableName` in `SoundAudioHandler` if necessary
- Redirect your Spotify output to the virtual cable

## Notes

- Linux support may be possible by using an alternative to Virtual Cables and adjusting the `cableName` in the .env
- Mac? go beg daddy Tim ive got no clue

## Disclaimer

This project is a proof-of-concept and should be used responsibly and in compliance with all applicable terms of service and laws. The authors are not responsible for any misuse or violations resulting from the use of this code.
