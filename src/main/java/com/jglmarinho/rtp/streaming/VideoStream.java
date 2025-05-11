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

public class VideoStream {

  FileInputStream fis; // video file
  int frame_nb; // current frame nb
  String filename; // store the filename to allow reopening the stream

  // -----------------------------------
  // constructor
  // -----------------------------------
  public VideoStream(String filename) throws Exception {
    // init variables
    this.filename = filename;
    fis = new FileInputStream(filename);
    frame_nb = 0;
  }

  // -----------------------------------
  // getnextframe
  // returns the next frame as an array of byte and the size of the frame
  // -----------------------------------
  public int getnextframe(byte[] frame) throws Exception {
    int length = 0;
    String length_string;
    byte[] frame_length = new byte[5];

    // read current frame length
    fis.read(frame_length, 0, 5);

    // transform frame_length to integer
    length_string = new String(frame_length);
    length = Integer.parseInt(length_string);

    frame_nb++; // Increment frame number when getting next frame
    return (fis.read(frame, 0, length));
  }

  // -----------------------------------
  // seek forward by a number of frames
  // -----------------------------------
  public void seekForward(int frames) throws Exception {
    // To seek forward, we simply skip the required number of frames
    for (int i = 0; i < frames; i++) {
      byte[] frame_length = new byte[5];
      // Read the length
      fis.read(frame_length, 0, 5);
      String length_string = new String(frame_length);
      int length = Integer.parseInt(length_string);

      // Skip the frame data
      fis.skip(length);
      frame_nb++;
    }
  }

  // -----------------------------------
  // seek backward by a number of frames
  // -----------------------------------
  public void seekBackward(int frames) throws Exception {
    // To seek backward, we need to restart the file and go forward to the right
    // position
    // This is because FileInputStream doesn't support backwards seeking

    // Don't go before first frame
    int targetFrameNb = Math.max(0, frame_nb - frames);

    // Close current stream
    fis.close();

    // Reopen the file
    fis = new FileInputStream(filename);

    // Reset frame counter
    frame_nb = 0;

    // Forward to target frame
    for (int i = 0; i < targetFrameNb; i++) {
      byte[] frame_length = new byte[5];
      // Read the length
      fis.read(frame_length, 0, 5);
      String length_string = new String(frame_length);
      int length = Integer.parseInt(length_string);

      // Skip the frame data
      fis.skip(length);
      frame_nb++;
    }
  }

  // -----------------------------------
  // get current frame number
  // -----------------------------------
  public int getFrameNb() {
    return frame_nb;
  }
}