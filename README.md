# Spotify to Discord Bot

⚠️ **This project is for educational purposes only** ⚠️

This proof-of-concept demonstrates streaming Spotify into a Discord voice chat. Only one server may be connected to at a
time. Please be aware of and respect all relevant terms of service, copyright laws, and licensing agreements of software this bot utilises when using
or modifying this code.

## Current Features

- Stream audio to voice channel
- Basic queue functionality
    - Shuffle
    - Repeat Queue
- Rich Embed + Reaction Controls
 
![image](https://github.com/user-attachments/assets/dba42dfa-5f36-4dc8-be3b-0f28ff6842dd)


## Roadmap

- [x] clean up looping to re-add to the queue right away/fix general wonkiness
- [x] fix removing from queue
- [ ] implement caching of message/channel for up to 7(/15) days
- [ ] Audio processing
- [ ] In the same vein -> clip/trim the fuzz on initial playback
- [ ] Tracking for a Spotify wrapped alternative
- [x] Track Requester (/who)
- [ ] leave vc when its alone, stop playing audio when current track is null
- [ ] self destruct ephemeral replies after x seconds (5?)

## Setup Instructions

### 1. Discord Bot Setup

1. Visit https://discord.com/developers/applications
2. Create a new application
3. In the "Bot" tab:
    - Click "Add Bot"
    - Reset token and copy it for your .env (step 5)
4. In the "OAuth2" tab:
    - Select "bot" under "Scopes"
    - Select "Manage Messages" under "Bot Permissions"
    - Copy the generated URL and use it to invite the bot to your server

### 2. Spotify API Setup

1. Go to https://developer.spotify.com/dashboard
2. Create a new app
3. Enable Web API
4. Save changes
5. In Settings, copy the Client ID and Client Secret

### 3. Spotify Spicetify Client Setup

The bot assumes a premium account as it does not account for ads, do with this knowledge as you want, theres a whole
marketplace of spicetify plugins that may help.

1. Install Spotify
2. Open PowerShell and run:
   ```
   iwr -useb https://raw.githubusercontent.com/spicetify/cli/main/install.ps1 | iex
   ```
3. Launch Spotify once, then close it
4. Navigate to `%appdata%/spicetify/Extensions`
5. Move the `local-api.js` file from this repository into the Extensions folder
6. In PowerShell or CMD, run:
   ```
   spicetify apply
   ```
7. Relaunch Spotify

### 4. Audio Routing

1. Install [Virtual Cables](https://vb-audio.com/Cable/index.htm) on Windows
2. Navigate to Settings - Sound - Advanced Sound Options - App Volume and Device Preferences
3. Redirect your Spotify output to the virtual cable input and max out its volume
![image](https://github.com/user-attachments/assets/a2641d43-0e62-4c2e-a6ac-660eb04c1f1d)


### 5. Environment Configuration

1. Rename `.envExample` to `.env`
2. Open `.env` and fill in the following:
   ```env
   DISCORD_BOT_TOKEN=your_discord_bot_token_here
   DISCORD_GUILD_ID=your_servers_id_here
   
   SPOTIFY_EXE_PATH=c://user/etc/etc/etc//Spotify.exe
   SPOTIFY_CLIENT_ID=your_spotify_client_id_here
   SPOTIFY_CLIENT_SECRET=your_spotify_client_secret_here
   
   CABLE_NAME=Leave this as the default unless you know what you're doing
   ```

### 6. Run The Bot

1. Run the bot from an ide or clone and build a release yourself (while its feature complete, theres still a few issues I need to cleanup before a full release)
2. Run /setnowplayingchannel to set the channel the bot will display its embed in, it will function without, but
   controls will be inaccessible
3. Join a voice channel and start /play ing tracks :)

### 7. Prettification Steps

You may provide URL links for the embed thumbnail when no track is playing. Add them to the `cover_placeholders.txt`
file, one URL per line. The bot will pick one at random each time it returns to the no track playing state.

## Known Issues

Issue: Skipping one song may cause many skips to occur in a row.
Reason: Minimising Spotify on a free account with adblock can trigger this, if you open it on a second monitor, it
should be fixed.

## Notes

- Windows is the primary supported platform. Linux and Mac support may be possible but is not provided out of the box,
  due to lack of knowledge regarding audio routing. Both the bot and spicetify will run on all platforms to the best of
  my knowledge.


## Contributing

Feel free to make any prs for bugs and optimisations. Features will for the most part be ignored as they are most likely bloat, unless mentioned in the roadmap.
If you notice anything stupid or seemingly unnecessary in the code, its either me being terrible, or working around spotify jank, test before you push anything x)
