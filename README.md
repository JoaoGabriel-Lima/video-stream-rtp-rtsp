# RTP Video Streaming Application

This application demonstrates real-time video streaming using the Real-time Transport Protocol (RTP) over UDP, with control messages sent via the Real Time Streaming Protocol (RTSP) over TCP.

## Overview

The system consists of two main components:

- A **Server** that streams video frames to clients
- A **Client** that receives and displays the video stream

The application implements a subset of the RTSP protocol for managing the streaming session, with custom extensions for seeking within the video.

## Features

- **Basic Video Streaming**: Stream MJPEG-encoded video over RTP
- **Playback Controls**: Setup, Play, Pause, and Teardown functionality
- **Seeking Functionality**: Forward and rewind video by 5 seconds
- **Modern UI**: Clean interface with intuitive controls and visual feedback

## Architecture

### Communication Protocols

- **RTSP (Real Time Streaming Protocol)**: Used for control messages over TCP
  - Controls session establishment, play/pause, and teardown
  - Extended with custom FORWARD and BACKWARD commands
- **RTP (Real-time Transport Protocol)**: Used for media delivery over UDP
  - Carries the actual video frames
  - Includes timestamps, sequence numbers, and payload identification

### Components

#### Server

- Handles RTSP requests from clients
- Reads video frames from MJPEG files
- Packages frames into RTP packets and sends them to clients
- Supports seeking operations (forward/backward)
- Maintains session state (INIT, READY, PLAYING)

#### Client

- Establishes RTSP sessions with the server
- Controls video playback (play, pause, etc.)
- Processes and displays incoming RTP video frames
- Provides a user interface with playback controls

#### VideoStream

- Manages access to the video file
- Provides frame-by-frame reading functionality
- Supports seeking operations

#### RTPpacket

- Encapsulates video frames with RTP header information
- Handles packet creation and parsing
- Provides utilities for inspecting packet headers

## RTSP Commands

The application supports the following RTSP commands:

1. **SETUP**: Initializes a streaming session
2. **PLAY**: Starts or resumes streaming
3. **PAUSE**: Temporarily halts streaming
4. **TEARDOWN**: Ends the streaming session
5. **FORWARD**: Custom extension to skip ahead 5 seconds
6. **BACKWARD**: Custom extension to rewind 5 seconds

## How to Use

### Prerequisites

- Java Runtime Environment (JRE) 8 or newer
- A terminal or command prompt

### Running the Server

```bash
java -cp target/classes com.jglmarinho.rtp.streaming.Server [RTSP_port]
```

Example:

```bash
java -cp target/classes com.jglmarinho.rtp.streaming.Server 8554
```

### Running the Client

```bash
java -cp target/classes com.jglmarinho.rtp.streaming.Client [Server_IP] [Server_RTSP_port] [Video_file]
```

Example:

```bash
java -cp target/classes com.jglmarinho.rtp.streaming.Client 127.0.0.1 8554 movie.Mjpeg
```

### Using the Client Interface

1. Click **Setup** to establish the RTSP connection
2. Click **Play** to start the video stream
3. Use **Pause** to temporarily stop the stream
4. Use **Forward** to skip ahead 5 seconds
5. Use **Backward** to rewind 5 seconds
6. Click **Teardown** to close the connection and exit

## Implementation Details

### Video Format

The application uses a simple MJPEG format where each frame is preceded by a 5-byte length indicator. The server reads this format and streams individual frames to clients.

### Seeking Implementation

Video seeking is implemented by:

- **Forward**: Skipping a set number of frames (5 seconds worth)
- **Backward**: Reopening the file and reading up to the desired position

### User Interface

The client features a clean, modern interface with:

- Video display area
- Playback control buttons with intuitive icons
- Separate controls for basic playback and seeking operations

## Development

### Project Structure

```
src/
├── main/
│   ├── java/
│   │   ├── movie.Mjpeg
│   │   └── com/
│   │       └── jglmarinho/
│   │           └── rtp/
│   │               └── streaming/
│   │                   ├── Client.java
│   │                   ├── RTPpacket.java
│   │                   ├── Server.java
│   │                   └── VideoStream.java
```

### Building from Source

This is a Maven-based project. To build it from source:

```bash
mvn clean package
```

## Future Enhancements

Potential improvements for future versions:

- Support for additional video codecs
- Variable-speed playback
- More precise seeking with a slider control
- Multiple client support
- Adaptive streaming based on network conditions

## License

This project is provided for educational purposes.

## Acknowledgments

- Based on concepts from networking and multimedia communications courses
- Uses Java Swing for the user interface"
