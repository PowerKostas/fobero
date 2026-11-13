## Getting started
The starting files for running the project locally are:
- **UI:** `src/main/java/com/chessbot/application/ChessbotApplication.java`
- **Perft:** `src/main/java/com/chessbot/engine/utils/Perft.java`
###
## Guide to running the bot on Lichess
###
### Step 1: Add the required datasets
Download [Daring.bin](https://github.com/ChrisWhittington/polyglot-books), [Jeroen.bin](https://github.com/ChrisWhittington/polyglot-books) and [quiet-labeled.epd](https://github.com/KierenP/ChessTrainingSets), then place them in:
```text
src/main/resources/com/chessbot/datasets/
```
###
### Step 2: Build `fobero.jar`
- Open the project in `IntelliJ IDEA`
- Go to **File → Project Structure → Artifacts → + → JAR → From modules with dependencies...**
- Set **Main Class** to `com.chessbot.engine.utils.Uci`.
- Click **OK**, then go to **Build → Build Artifacts... → Build**.
###
### Step 3: Create `fobero.bat`
Create a `fobero.bat` file containing the following code (any similar java.exe path works):
```bat
@echo off
"C:\Users\Kostas\.jdks\openjdk-26.0.2.1\bin\java.exe" -jar "%~dp0fobero.jar"
```
###
### Step 4: Add the files to `lichess-bot`
Download [lichess-bot](https://github.com/lichess-bot-devs/lichess-bot) and its dependencies, then place `fobero.jar` and `fobero.bat` inside:
```text
lichess-bot-master/engines/
```
###
### Step 5: Configure `config.yml` in `lichess-bot`
Rename `config.yml.default` to `config.yml` and configure the following fields:
- `token`: Set to your Lichess bot account API access token
- `name`
- `ponder`
- `uci_options`: Comment out the entire block
- `move_overhead`: Set to `100`.
- `time_controls`
- `greeting`
- `allow_matchmaking`
- `challenge_timeout`
- `challenge_mode`
###
### Step 6: Launch the bot
Inside `lichess-bot-master` run:
```bash
python lichess-bot.py
```