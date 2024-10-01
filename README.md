# Spotify to Discord Bot

⚠️ **IMPORTANT: This project is for educational purposes only** ⚠️

This proof-of-concept demonstrates streaming Spotify into a Discord voice chat. Please be aware of and respect all relevant terms of service, copyright laws, and licensing agreements when using or modifying this code.

Progress:
- [x] Stream client to voice channel
- [x] Authenticate spotify via local webserver/user input
  - [ ] save token for automatic refresh between sessions
- [ ] Basic queue functionality (cant rely on spotifys as it doesnt allow you to remove from queue)
  - [ ] Shuffle
  - [ ] Repeat song/queue
- [ ] Rich Emned
- [ ] Control features
  - [ ] From embed
  - [ ] From command
- [ ] Link to sources
- [ ] Audio processing

## Prerequisites

- A Spotify client (Premium recommended) running on the same device as the bot
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
3. Add `http://localhost:8080` to the redirect URIs
4. Enable Web API
5. Save changes
6. In Settings, copy the Client ID and Client Secret

### 3. Environment Configuration

1. Rename `.envExample` to `.env`
2. Open `.env` and fill in the following:
```env
DISCORD_BOT_TOKEN=your_discord_bot_token_here
SPOTIFY_CLIENT_ID=your_spotify_client_id_here
SPOTIFY_CLIENT_SECRET=your_spotify_client_secret_here
```

### 4. Audio Setup

- On Windows: Install Virtual Cables
- On Linux: Use an alternative to Virtual Cables
- Change the `cableName` in `SoundAudioHandler` if necessary
- Redirect your Spotify output to the virtual cable

## Usage

1. Run the bot
2. The bot will prompt for Spotify authorization in your browser
3. Authorize the application for the currently logged-in Spotify user

## Notes

- There is work-in-progress support for non-premium Spotify accounts
- Linux support may be possible by using an alternative to Virtual Cables and adjusting the `cableName` in `SoundAudioHandler`
- Mac? go beg daddy Tim ive got no clue

## Disclaimer

This project is a proof-of-concept and should be used responsibly and in compliance with all applicable terms of service and laws. The authors are not responsible for any misuse or violations resulting from the use of this code.
