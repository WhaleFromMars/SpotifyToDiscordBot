# Spotify to Discord Bot

> [!IMPORTANT]
> This proof-of-concept demonstrates streaming Spotify into a Discord voice chat. Only one server may be connected to at a
> time. Please be aware of and respect all relevant terms of service, copyright laws, and licensing agreements of software this bot utilises when using
> or modifying this code.



## Current Features

- Stream audio to voice channel
- Basic queue functionality (song name, autocomplete suggestions, spotify link, or spotify album link)
- Rich Embed + Reaction Controls
- See who requester a track with /who
 
![image](https://github.com/user-attachments/assets/dba42dfa-5f36-4dc8-be3b-0f28ff6842dd)


## Roadmap

- [ ] docker container for running on a vps/locally
- [ ] Audio processing (boost/reduce certain frequency bands)
- [ ] Tracking for a Spotify wrapped alternative
- [ ] leave vc when its alone
- [ ] self destruct ephemeral replies after x seconds (5?)

## Setup Instructions


> [!CAUTION]
> It is reccomended you create a new spotify account if you plan on running this bot outside of your home network, such as via a vps.

### 1. Download From Releases
On the right youll see a 'releases' section, click into it and expand the assets, download the .7z file you find in here and extract it to its own folder
in here youll find a start.bat for windows, and a start.sh for linux or macos. youll also find a .env file, cover_placeholders.txt, and the jar file that is the bulk of the bot, dont touch anything here until you reach their relevent parts in the readme.

### 2. Discord Bot Setup

1. Visit https://discord.com/developers/applications
2. Create a new application
3. In the "Bot" tab:
    - Click "Add Bot"
    - Reset token and copy it for your .env (step 5)
4. In the "OAuth2" tab:
    - Select "bot" under "Scopes"
    - Select "Manage Messages" under "Bot Permissions"
    - Copy the generated URL and use it to invite the bot to your server

### 3. Spotify API Setup

1. Go to https://developer.spotify.com/dashboard
2. Create a new app
3. Enable Web API
4. Save changes
5. In Settings, copy the Client ID and Client Secret

### 4. Spotify Spicetify Client Setup

The bot assumes a premium account as it does not account for ads, do with this knowledge as you want, theres a whole
marketplace of spicetify plugins that may help.

1. Install Spotify
2. Open PowerShell and run:
   ```bash
   iwr -useb https://raw.githubusercontent.com/spicetify/cli/main/install.ps1 | iex
   ```
3. Launch Spotify once, then close it
4. Navigate to `%appdata%/spicetify/Extensions`
5. Move the `spicetify-local-api.js` file into the folder from the releases
   tab [here](https://github.com/WhaleFromMars/SpicetifyLocalAPI)
6. In PowerShell or CMD, run:
   ```
   spicetify apply
   ```
7. Relaunch Spotify

### 5. Audio Routing

1. Install [Virtual Cables](https://vb-audio.com/Cable/index.htm) on Windows
2. Navigate to Settings - Sound - Advanced Sound Options - App Volume and Device Preferences
3. Redirect your Spotify output to the virtual cable input and max out its volume
![image](https://github.com/user-attachments/assets/a2641d43-0e62-4c2e-a6ac-660eb04c1f1d)


### 6. Environment Configuration

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
### 7. Prettification Steps (Optional)

You may provide URL links for the embed thumbnail when no track is playing. Add them to the `cover_placeholders.txt`
file, one URL per line. The bot will pick one at random each time it returns to the no track playing state.

### 8. Run The Bot

1. Run the respective start file for your OS (.bat for Windows, .sh for Linux/macos)
2. In the server you are running the bot for, run /setnowplayingchannel to set the channel the bot will display its embed in, it will function without, but
   controls will be inaccessible
3. Join a voice channel and start /play ing tracks :)

## Known Issues

Issue: Skipping one song may cause many skips to occur in a row.
Reason: Minimising Spotify on a free account with adblock can trigger this, if you open it on a second monitor, it
should be fixed. A docker container is in the works to solve this if you dont have another monitor.

## Notes

- Windows is the primary supported platform. Linux and Mac support may be possible but is not provided out of the box outside of a start script,
  due to lack of knowledge regarding audio routing. Both the bot and spicetify will run on all platforms to the best of
  my knowledge.


## Contributing

Feel free to make any prs for bugs and optimisations. Features will for the most part be ignored as they are most likely bloat, unless mentioned in the roadmap.
If you notice anything stupid or seemingly unnecessary in the code, its either me being terrible, or working around spotify jank, test before you push anything x)
