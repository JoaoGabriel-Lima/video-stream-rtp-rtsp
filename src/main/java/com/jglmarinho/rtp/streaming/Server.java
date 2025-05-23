/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.jglmarinho.rtp.streaming;

/**
 *
 * @author marinho
 */
import java.io.*;
import java.net.*;
import java.awt.*;
import java.util.*;
import java.awt.event.*;
import javax.swing.*;
import javax.swing.Timer;

public class Server extends JFrame implements ActionListener {

  // RTP variables:
  // ----------------
  DatagramSocket RTPsocket; // socket to be used to send and receive UDP packets
  DatagramPacket senddp; // UDP packet containing the video frames

  InetAddress ClientIPAddr; // Client IP address
  int RTP_dest_port = 0; // destination port for RTP packets (given by the RTSP Client)

  // GUI:
  // ----------------
  JLabel label;

  // Video variables:
  // ----------------
  int imagenb = 0; // image nb of the image currently transmitted
  VideoStream video; // VideoStream object used to access video frames
  static int MJPEG_TYPE = 26; // RTP payload type for MJPEG video
  static int FRAME_PERIOD = 100; // Frame period of the video to stream, in ms
  static int VIDEO_LENGTH = 500; // length of the video in frames

  Timer timer; // timer used to send the images at the video frame rate
  byte[] buf; // buffer used to store the images to send to the client

  // RTSP variables
  // ----------------
  // rtsp states
  static final int INIT = 0;
  static final int READY = 1;
  static final int PLAYING = 2;
  // rtsp message types
  static final int SETUP = 3;
  static final int PLAY = 4;
  static final int PAUSE = 5;
  static final int TEARDOWN = 6;
  static final int FORWARD = 7; // New command for forwarding
  static final int BACKWARD = 8; // New command for going backward

  // Constants for seeking
  static final int SEEK_FRAMES = 25; // Number of frames to skip when seeking forward/backward
  // This is roughly 5 seconds since FRAME_PERIOD is 100ms (10fps)

  static int state; // RTSP Server state == INIT or READY or PLAY
  Socket RTSPsocket; // socket used to send/receive RTSP messages
  // input and output stream filters
  static BufferedReader RTSPBufferedReader;
  static BufferedWriter RTSPBufferedWriter;
  static String VideoFileName; // video file requested from the client
  static int RTSP_ID = 123456; // ID of the RTSP session
  int RTSPSeqNb = 0; // Sequence number of RTSP messages within the session

  static final String CRLF = "\r\n";

  // --------------------------------
  // Constructor
  // --------------------------------
  public Server() {

    // init Frame
    super("Server");

    // init Timer
    timer = new Timer(FRAME_PERIOD, this);
    timer.setInitialDelay(0);
    timer.setCoalesce(true);

    // allocate memory for the sending buffer
    buf = new byte[15000];

    // Handler to close the main window
    addWindowListener(new WindowAdapter() {
      @Override
      public void windowClosing(WindowEvent e) {
        // stop the timer and exit
        timer.stop();
        System.exit(0);
      }
    });

    // GUI:
    label = new JLabel("Send frame #        ", JLabel.CENTER);
    getContentPane().add(label, BorderLayout.CENTER);
  }

  // ------------------------------------
  // main
  // ------------------------------------
  public static void main(String[] argv) throws Exception {
    // create a Server object
    Server theServer = new Server();

    // show GUI:
    theServer.pack();
    theServer.setVisible(true);

    // get RTSP socket port from the command line
    int RTSPport = Integer.parseInt(argv[0]);

    try ( // Initiate TCP connection with the client for the RTSP session
        ServerSocket listenSocket = new ServerSocket(RTSPport)) {
      theServer.RTSPsocket = listenSocket.accept();
    }

    // Get Client IP address
    theServer.ClientIPAddr = theServer.RTSPsocket.getInetAddress();

    // Initiate RTSPstate
    state = INIT;

    // Set input and output stream filters:
    RTSPBufferedReader = new BufferedReader(new InputStreamReader(theServer.RTSPsocket.getInputStream()));
    RTSPBufferedWriter = new BufferedWriter(new OutputStreamWriter(theServer.RTSPsocket.getOutputStream()));

    // Wait for the SETUP message from the client
    int request_type;
    boolean done = false;
    while (!done) {
      request_type = theServer.parse_RTSP_request(); // blocking

      if (request_type == SETUP) {
        done = true;

        // update RTSP state
        state = READY;
        System.out.println("New RTSP state: READY");

        // Send response
        theServer.send_RTSP_response();

        // init the VideoStream object:
        String currentDir = System.getProperty("user.dir");
        String filePath = currentDir + File.separator + VideoFileName;
        theServer.video = new VideoStream(filePath);

        // init RTP socket
        theServer.RTPsocket = new DatagramSocket();
      }
    } // loop to handle RTSP requests
    while (true) {
      // parse the request
      request_type = theServer.parse_RTSP_request(); // blocking

      if ((request_type == PLAY) && (state == READY)) {
        // send back response
        theServer.send_RTSP_response();
        // start timer
        theServer.timer.start();
        // update state
        state = PLAYING;
        System.out.println("New RTSP state: PLAYING");
      } else if ((request_type == PAUSE) && (state == PLAYING)) {
        // send back response
        theServer.send_RTSP_response();
        // stop timer
        theServer.timer.stop();
        // update state
        state = READY;
        System.out.println("New RTSP state: READY");
      } else if ((request_type == FORWARD) && (state == PLAYING)) {
        // Send response first
        theServer.send_RTSP_response();

        // Temporarily stop the timer
        theServer.timer.stop();

        try {
          // Forward 5 seconds (50 frames at 10fps)
          theServer.video.seekForward(SEEK_FRAMES);
          // Update frame counter
          theServer.imagenb += SEEK_FRAMES;
          System.out.println("Forwarded " + SEEK_FRAMES + " frames to frame #" + theServer.imagenb);
        } catch (Exception ex) {
          System.out.println("Exception on forwarding: " + ex);
        }

        // Restart timer
        theServer.timer.start();
      } else if ((request_type == BACKWARD) && (state == PLAYING)) {
        // Send response first
        theServer.send_RTSP_response();

        // Temporarily stop the timer
        theServer.timer.stop();

        try {
          // Backward 5 seconds (50 frames at 10fps)
          theServer.video.seekBackward(SEEK_FRAMES);
          // Update frame counter
          theServer.imagenb = Math.max(0, theServer.imagenb - SEEK_FRAMES);
          System.out.println("Rewound " + SEEK_FRAMES + " frames to frame #" + theServer.imagenb);
        } catch (Exception ex) {
          System.out.println("Exception on rewinding: " + ex);
        }

        // Restart timer
        theServer.timer.start();
      } else if (request_type == TEARDOWN) {
        // send back response
        theServer.send_RTSP_response();
        // stop timer
        theServer.timer.stop();
        // close sockets
        theServer.RTSPsocket.close();
        theServer.RTPsocket.close();

        System.exit(0);
        break;
      }
    }
  }

  // ------------------------
  // Handler for timer
  // ------------------------
  @Override
  public void actionPerformed(ActionEvent e) {

    // if the current image nb is less than the length of the video
    if (imagenb < VIDEO_LENGTH) {
      // update current imagenb
      imagenb++;

      try {
        // get next frame to send from the video, as well as its size
        int image_length = video.getnextframe(buf);

        // Builds an RTPpacket object containing the frame
        RTPpacket rtp_packet = new RTPpacket(MJPEG_TYPE, imagenb, imagenb * FRAME_PERIOD, buf, image_length);

        // get to total length of the full rtp packet to send
        int packet_length = rtp_packet.getlength();

        // retrieve the packet bitstream and store it in an array of bytes
        byte[] packet_bits = new byte[packet_length];
        rtp_packet.getpacket(packet_bits);

        // send the packet as a DatagramPacket over the UDP socket
        senddp = new DatagramPacket(packet_bits, packet_length, ClientIPAddr, RTP_dest_port);
        RTPsocket.send(senddp);

        // System.out.println("Send frame #"+imagenb);
        // print the header bitstream
        rtp_packet.printheader();

        // update GUI
        label.setText("Send frame #" + imagenb);
      } catch (Exception ex) {
        System.out.println("Exception caught on sending RTP packet: " + ex);
        // print the stack trace
        ex.printStackTrace();
        System.exit(0);
      }
    } else {
      // if we have reached the end of the video file, stop the timer
      timer.stop();
    }
  }

  // ------------------------------------
  // Parse RTSP Request
  // ------------------------------------
  private int parse_RTSP_request() {
    int request_type = -1;
    try {
      // parse request line and extract the request_type:
      String RequestLine = RTSPBufferedReader.readLine();
      // System.out.println("RTSP Server - Received from Client:");
      System.out.println(RequestLine);

      StringTokenizer tokens = new StringTokenizer(RequestLine);
      String request_type_string = tokens.nextToken();

      // convert to request_type structure:
      if ((request_type_string).compareTo("SETUP") == 0)
        request_type = SETUP;
      else if ((request_type_string).compareTo("PLAY") == 0)
        request_type = PLAY;
      else if ((request_type_string).compareTo("PAUSE") == 0)
        request_type = PAUSE;
      else if ((request_type_string).compareTo("TEARDOWN") == 0)
        request_type = TEARDOWN;
      else if ((request_type_string).compareTo("FORWARD") == 0)
        request_type = FORWARD;
      else if ((request_type_string).compareTo("BACKWARD") == 0)
        request_type = BACKWARD;

      if (request_type == SETUP) {
        // extract VideoFileName from RequestLine
        VideoFileName = tokens.nextToken();
      }

      // parse the SeqNumLine and extract CSeq field
      String SeqNumLine = RTSPBufferedReader.readLine();
      System.out.println(SeqNumLine);
      tokens = new StringTokenizer(SeqNumLine);
      tokens.nextToken();
      RTSPSeqNb = Integer.parseInt(tokens.nextToken());

      // get LastLine
      String LastLine = RTSPBufferedReader.readLine();
      System.out.println(LastLine);

      if (request_type == SETUP) {
        // extract RTP_dest_port from LastLine
        tokens = new StringTokenizer(LastLine);
        for (int i = 0; i < 3; i++)
          tokens.nextToken(); // skip unused stuff
        RTP_dest_port = Integer.parseInt(tokens.nextToken());
      }
      // else LastLine will be the SessionId line ... do not check for now.
    } catch (IOException | NumberFormatException ex) {
      System.out.println("Exception caught on parsing RTSP request: " + ex);
      System.exit(0);
    }
    return (request_type);
  }

  // ------------------------------------
  // Send RTSP Response
  // ------------------------------------
  private void send_RTSP_response() {
    try {
      RTSPBufferedWriter.write("RTSP/1.0 200 OK" + CRLF);
      RTSPBufferedWriter.write("CSeq: " + RTSPSeqNb + CRLF);
      RTSPBufferedWriter.write("Session: " + RTSP_ID + CRLF);
      RTSPBufferedWriter.flush();
      // System.out.println("RTSP Server - Sent response to Client.");
    } catch (IOException ex) {
      System.out.println("Exception caught on send_RTSP_Response: " + ex);
      System.exit(0);
    }
  }
}