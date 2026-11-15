# COMP3011-Assignment-3

[github.com/fiskt/COMP3011-Assignment-3](https://github.com/fiskt/COMP3011-Assignment-3)

## Project Overview

A video player with multi-threaded frame processor handling and subtitle generation. It uses the OpenAI whisper-1 transcription model through a local server API to transcribe video subtitles.

## Features

* Multi-threaded frame processing, command line frame processor parsing, help command
* On screen subtitles generated from the video's audio track via OpenAI's transcription models
* Toggleable subtitles with 'S' key or button in the video player, and can be enabled by `-S` or `--subtitle-frames` in the terminal input
* Automatic retry for subtitle fetching if it fails
* Server-side subtitle caching keyed by an audio content hash
* Playback errors handled gracefully

## Building

### Video Player

```Shell
cd VideoPlayerMultiThread
mvn validate
mvn compile
mvn package
```

### Subtitle Server

```Shell
cd SpeechToText
mvn clean package
```

## Running

### 1. Start the subtitle server

The server will try to retrieve the key in 3 ways:

1. Pass the file path of the key when starting the server
2. Have the key in a `key.txt` file inside the root directory
3. Have an `OPENAI_API_KEY` environment variable

```Shell
cd SpeechToText

# Command for passing key file path
java -jar <jar_name>.jar --openai.key-file=/path/to/key.txt

# Command for retrieving the key through root directory file or environment variable
java -jar <jar_name>.jar
```

### 2. Start the video player

```Shell
cd VideoPlayerMultiThread
mvn javafx:run "-Djavafx.args=[options] <video-file>"
```

### Basic Options

| Short | Long        | Description                  |
| ----- | ----------- | ---------------------------- |
| -h    | --help      | Show help message            |
| -a    | --audio     | Enable audio                 |
| -x    | --maximise  | Open the player maximised    |
| -1    | --monitor-1 | Open the player on display 1 |
| -2    | --monitor-2 | Open the player on display 2 |

### Frame Processors

| Short | Long              | Description                              |
| ----- | ----------------- | ---------------------------------------- |
| -n    | --number-frames   | Render the frame number onto each frame  |
| -s    | --scratch-frames  | Render vertical film scratches           |
| -f    | --flicker-frames  | Randomly dim frames                      |
| -w    | --black-and-white | Convert frames to black and white        |
| -y    | --yellow-frames   | Apply a warmer colour temperature        |
| -v    | --vignette-frames | Darken the frame edges                   |
| -d    | --dust-frames     | Render dust and hair marks               |
| -j    | --jitter-frames   | Randomly displace frames by a few pixels |
| -m    | --mottle-frames   | Add cloudy emulsion mottling             |
| -b    | --bleed-frames    | Bleed light into frames                  |
| -p    | --pepper-frames   | Pepper frames with dark spots/blotches   |
| -S    | --subtitle-frames | Enable subtitles                         |

Frame processors are applied in command-line order and can be repeated.

```Shell
# Play video.mp4 with subtitles and frame numbering
cd VideoPlayerMultiThread
mvn javafx:run "-Djavafx.args=movie.mp4 -Sn"
```

## Concurrency Design

In addition to the video player frame processing concurrency design, this project adds:

* Subtitle fetching on its own thread. The player waits up to 10 seconds for subtitles before starting playback anyway. The subtitles are attached once they are available if playback has already started.
* If the fetch request fails or connection cuts, it automatically retries at 10,000 ms intervals. `while (videoFile == file) ` ensures that the video file hasn't changed.
* Server-side subtitle caching keyed by the audio content hash so playing identical audio (replayed or from a renamed file) pulls from the cache.
* A lock (`FONT_RENDER_LOCK`) stops multiple threads calling AWT’s text rendering at the same time since it isn’t thread-safe. A cache then ensures each distinct subtitle line is only rendered once rather than every frame it appears on.

## Testing

Extended the video player command line and frame processor thread tests to include the frame subtitler.

### Command Line & Frame Processor Thread Tests

Command line tests check the options available in the Assignment 2 video player and the new subtitle options `-S` and `--subtitle-frames`.

Frame processor thread tests check that a `FrameSubtitler` instance is added, even if `-S` or `--subtitle-frames` wasn't in the launch command.

```Shell
cd VideoPlayerMultiThread
mvn test
```

### Load Comparison Test

The load comparison test is unchanged from Assignment 2.

```Shell
cd VideoPlayerMultiThread
mvn verify
```

### Usage Tracker Test

The usage tracker test checks that a new `UsageTracker` starts with 0 usage seconds and that `addSeconds()` accumulates the usage seconds.

```Shell
cd SpeechToText
mvn test
```



## Attribution

Modifications to the supplied files are attributed in the header. Code adapted from external sources are cited with inline comments. AI assisted code is marked with an inline comment at the relevant code block.
