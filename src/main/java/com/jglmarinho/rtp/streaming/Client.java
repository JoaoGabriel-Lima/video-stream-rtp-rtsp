/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.jglmarinho.rtp.streaming;

import java.io.*;
import java.net.*;
import java.util.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import javax.swing.*;
import javax.swing.Timer;
import javax.swing.border.*;

public class Client { // GUI
  // ----
  JFrame f = new JFrame("RTP Video Client");
  JButton setupButton = new JButton("Setup");
  JButton playButton = new JButton("Play");
  JButton pauseButton = new JButton("Pause");
  JButton tearButton = new JButton("Teardown");
  JButton forwardButton = new JButton("Forward"); // New forward button
  JButton backwardButton = new JButton("Backward"); // New backward button
  JPanel mainPanel = new JPanel();
  JPanel buttonPanel = new JPanel();
  JPanel seekPanel = new JPanel(); // New panel for seeking controls
  JLabel iconLabel = new JLabel();
  ImageIcon icon;
  // Icons for buttons
  ImageIcon setupIcon = createIcon("\uD83D\uDD0E", Color.BLUE.darker()); // Setup icon
  ImageIcon playIcon = createIcon("\u25B6", Color.GREEN.darker()); // Play icon
  ImageIcon pauseIcon = createIcon("\u23F8", Color.ORANGE); // Pause icon
  ImageIcon teardownIcon = createIcon("\u23F9", Color.RED); // Stop icon
  ImageIcon forwardIcon = createIcon("\u23ED", new Color(0, 128, 128)); // Fast forward icon
  ImageIcon backwardIcon = createIcon("\u23EE", new Color(0, 128, 128)); // Rewind icon

  // RTP variables:
  // ----------------
  DatagramPacket rcvdp; // UDP packet received from the server
  DatagramSocket RTPsocket; // socket to be used to send and receive UDP packets
  static int RTP_RCV_PORT = 25000; // port where the client will receive the RTP packets

  Timer timer; // timer used to receive data from the UDP socket
  byte[] buf; // buffer used to store data received from the server

  // RTSP variables
  // ----------------
  // rtsp states
  final static int INIT = 0;
  final static int READY = 1;
  final static int PLAYING = 2;
  static int state; // RTSP state == INIT or READY or PLAYING
  Socket RTSPsocket; // socket used to send/receive RTSP messages
  // input and output stream filters
  static BufferedReader RTSPBufferedReader;
  static BufferedWriter RTSPBufferedWriter;
  static String VideoFileName; // video file to request to the server
  int RTSPSeqNb = 0; // Sequence number of RTSP messages within the session
  int RTSPid = 0; // ID of the RTSP session (given by the RTSP Server)

  final static String CRLF = "\r\n";

  // Video constants:
  // ------------------
  static int MJPEG_TYPE = 26; // RTP payload type for MJPEG video

  // --------------------------
  // Constructor
  // --------------------------
  public Client() {

    // build GUI
    // --------------------------

    // Frame
    f.addWindowListener(new WindowAdapter() {

      @Override
      public void windowClosing(WindowEvent e) {
        System.exit(0);
      }

    });
    f.setBackground(new Color(245, 245, 250)); // Set icons for buttons
    setupButton.setIcon(setupIcon);
    playButton.setIcon(playIcon);
    pauseButton.setIcon(pauseIcon);
    tearButton.setIcon(teardownIcon);
    forwardButton.setIcon(forwardIcon);
    backwardButton.setIcon(backwardIcon);

    // Style buttons with rounded corners
    styleButton(setupButton, new Color(230, 230, 230));
    styleButton(playButton, new Color(230, 245, 230));
    styleButton(pauseButton, new Color(255, 245, 230));
    styleButton(tearButton, new Color(255, 230, 230));
    styleButton(forwardButton, new Color(230, 240, 240));
    styleButton(backwardButton, new Color(230, 240, 240));

    // Buttons panel with FlowLayout for better spacing
    buttonPanel.setLayout(new FlowLayout(FlowLayout.CENTER, 10, 5));
    buttonPanel.setBorder(new EmptyBorder(10, 10, 10, 10));
    buttonPanel.setBackground(new Color(245, 245, 250));
    buttonPanel.add(setupButton);
    buttonPanel.add(playButton);
    buttonPanel.add(pauseButton);
    buttonPanel.add(tearButton);

    // Create a separate panel for seek buttons
    seekPanel.setLayout(new FlowLayout(FlowLayout.CENTER, 10, 5));
    seekPanel.setBorder(new EmptyBorder(5, 10, 10, 10));
    seekPanel.setBackground(new Color(245, 245, 250));
    seekPanel.add(backwardButton);
    seekPanel.add(forwardButton);

    // Add listeners
    setupButton.addActionListener(new setupButtonListener());
    playButton.addActionListener(new playButtonListener());
    pauseButton.addActionListener(new pauseButtonListener());
    tearButton.addActionListener(new tearButtonListener());
    forwardButton.addActionListener(new forwardButtonListener());
    backwardButton.addActionListener(new backwardButtonListener());// Image display label with border
    iconLabel.setIcon(null);
    iconLabel.setBorder(BorderFactory.createLineBorder(new Color(200, 200, 200), 1));
    iconLabel.setBackground(Color.BLACK);
    iconLabel.setOpaque(true);
    iconLabel.setHorizontalAlignment(JLabel.CENTER); // Center horizontally
    iconLabel.setVerticalAlignment(JLabel.CENTER); // Center vertically

    // Create a panel to hold the video with padding for centering
    JPanel videoPanel = new JPanel(new GridBagLayout());
    videoPanel.setBackground(new Color(245, 245, 250));
    videoPanel.add(iconLabel); // frame layout with padding
    mainPanel.setLayout(new BorderLayout(10, 10));
    mainPanel.setBorder(new EmptyBorder(15, 15, 15, 15));
    mainPanel.setBackground(new Color(245, 245, 250));
    mainPanel.add(videoPanel, BorderLayout.CENTER); // Add the video panel instead of iconLabel directly

    // Create a panel for controls (button panel and seek panel)
    JPanel controlsPanel = new JPanel(new BorderLayout());
    controlsPanel.setBackground(new Color(245, 245, 250));
    controlsPanel.add(buttonPanel, BorderLayout.NORTH);
    controlsPanel.add(seekPanel, BorderLayout.SOUTH);

    mainPanel.add(controlsPanel, BorderLayout.SOUTH);
    f.getContentPane().add(mainPanel, BorderLayout.CENTER);
    f.setSize(new Dimension(520, 480)); // Even larger size for better video viewing
    f.setLocationRelativeTo(null); // Center on screen
    f.setVisible(true);

    // init timer
    // --------------------------
    timer = new Timer(20, new timerListener());
    timer.setInitialDelay(0);
    timer.setCoalesce(true);

    // allocate enough memory for the buffer used to receive data from the server
    buf = new byte[15000];
  }

  // ------------------------------------
  // main
  // ------------------------------------
  public static void main(String[] argv) throws Exception {
    // Create a Client object
    Client theClient = new Client();

    // get server RTSP port and IP address from the command line
    // ------------------
    int RTSP_server_port = Integer.parseInt(argv[1]);
    String ServerHost = argv[0];
    InetAddress ServerIPAddr = InetAddress.getByName(ServerHost);

    // get video filename to request:
    VideoFileName = argv[2];

    // Establish a TCP connection with the server to exchange RTSP messages
    // ------------------
    theClient.RTSPsocket = new Socket(ServerIPAddr, RTSP_server_port);

    // Set input and output stream filters:
    RTSPBufferedReader = new BufferedReader(new InputStreamReader(theClient.RTSPsocket.getInputStream()));
    RTSPBufferedWriter = new BufferedWriter(new OutputStreamWriter(theClient.RTSPsocket.getOutputStream()));

    // init RTSP state:
    state = INIT;
  }

  // ------------------------------------
  // Handler for buttons
  // ------------------------------------

  // .............
  // TO COMPLETE
  // .............

  // Handler for Setup button
  // -----------------------
  class setupButtonListener implements ActionListener {
    @Override
    public void actionPerformed(ActionEvent e) {

      // System.out.println("Setup Button pressed !");

      if (state == INIT) {
        // Init non-blocking RTPsocket that will be used to receive data
        try {
          // construct a new DatagramSocket to receive RTP packets from the server, on
          // port RTP_RCV_PORT
          RTPsocket = new DatagramSocket(RTP_RCV_PORT);

          // set TimeOut value of the socket to 5msec.
          RTPsocket.setSoTimeout(5);

        } catch (SocketException se) {
          System.out.println("Socket exception: " + se);
          System.exit(0);
        }

        // init RTSP sequence number
        RTSPSeqNb = 1;

        // Send SETUP message to the server
        send_RTSP_request("SETUP");

        // Wait for the response
        if (parse_server_response() != 200)
          System.out.println("Invalid Server Response");
        else {
          state = READY; // change RTSP state and print new state
          System.out.println("New RTSP state: " + state);
        }
      } else if (state != INIT) {
        // do nothing
        System.out.println("Invalid State: " + state);
      }
    }
  }

  // Handler for Play button
  // -----------------------
  class playButtonListener implements ActionListener {
    @Override
    public void actionPerformed(ActionEvent e) {

      // System.out.println("Play Button pressed !");
      switch (state) {
        case READY -> {
          // increase RTSP sequence number
          // .....
          RTSPSeqNb++;
          // Send PLAY message to the server
          send_RTSP_request("PLAY");
          // Wait for the response
          if (parse_server_response() != 200)
            System.out.println("Invalid Server Response");
          else {
            state = PLAYING; // change RTSP state and print out new state
            System.out.println("New RTSP state: PLAYING");

            timer.start();
          }
        }
        case PLAYING -> // do nothing
          System.out.println("Invalid State: " + state);
        default -> // do nothing
          System.out.println("Invalid State: " + state);
      }
    }
  }

  // Handler for Pause button
  // -----------------------
  class pauseButtonListener implements ActionListener {
    @Override
    public void actionPerformed(ActionEvent e) {

      // System.out.println("Pause Button pressed !");
      switch (state) {
        case PLAYING -> {
          // increase RTSP sequence number
          RTSPSeqNb++;
          // Send PAUSE message to the server
          send_RTSP_request("PAUSE");
          // Wait for the response
          if (parse_server_response() != 200)
            System.out.println("Invalid Server Response");
          else {
            state = READY; // change RTSP state and print out new state
            System.out.println("New RTSP state: " + state);

            // stop the timer
            timer.stop();
          }
        }
        case READY -> // do nothing
          System.out.println("Invalid State: " + state);
        default -> // do nothing
          System.out.println("Invalid State: " + state);
      }
    }
  }

  // Handler for Teardown button
  // -----------------------
  class tearButtonListener implements ActionListener {
    @Override
    public void actionPerformed(ActionEvent e) {

      // System.out.println("Teardown Button pressed !");

      // increase RTSP sequence number
      // ..........
      RTSPSeqNb++;

      // Send TEARDOWN message to the server
      send_RTSP_request("TEARDOWN");

      // Wait for the response
      if (parse_server_response() != 200)
        System.out.println("Invalid Server Response");
      else {
        state = INIT; // change RTSP state and print out new state
        System.out.println("New RTSP state: " + state);

        // stop the timer
        timer.stop();

        // exit
        System.exit(0);
      }
    }
  }

  // ------------------------------------
  // Handler for timer
  // ------------------------------------

  class timerListener implements ActionListener {
    @Override
    public void actionPerformed(ActionEvent e) {

      // Construct a DatagramPacket to receive data from the UDP socket
      rcvdp = new DatagramPacket(buf, buf.length);

      try {
        // receive the DP from the socket:
        RTPsocket.receive(rcvdp);

        // create an RTPpacket object from the DP
        RTPpacket rtp_packet = new RTPpacket(rcvdp.getData(), rcvdp.getLength());

        // print important header fields of the RTP packet received:
        System.out.println("Got RTP packet with SeqNum # " + rtp_packet.getsequencenumber() + " TimeStamp "
            + rtp_packet.gettimestamp() + " ms, of type " + rtp_packet.getpayloadtype());

        // print header bitstream:
        rtp_packet.printheader();

        // get the payload bitstream from the RTPpacket object
        int payload_length = rtp_packet.getpayload_length();
        byte[] payload = new byte[payload_length];
        rtp_packet.getpayload(payload);

        // get an Image object from the payload bitstream
        Toolkit toolkit = Toolkit.getDefaultToolkit();
        Image image = toolkit.createImage(payload, 0, payload_length); // display the image as an ImageIcon object with
                                                                       // proper centering
        icon = new ImageIcon(image);
        iconLabel.setIcon(icon);
        iconLabel.revalidate(); // Ensure layout is updated after icon change
      } catch (InterruptedIOException iioe) {
        // System.out.println("Nothing to read");
      } catch (IOException ioe) {
        System.out.println("Exception caught on receiving RTP packet: " + ioe);
      }
    }
  }

  // ------------------------------------
  // Parse Server Response
  // ------------------------------------
  private int parse_server_response() {
    int reply_code = 0;

    try {
      // parse status line and extract the reply_code:
      String StatusLine = RTSPBufferedReader.readLine();
      // System.out.println("RTSP Client - Received from Server:");
      System.out.println(StatusLine);

      StringTokenizer tokens = new StringTokenizer(StatusLine);
      tokens.nextToken(); // skip over the RTSP version
      reply_code = Integer.parseInt(tokens.nextToken());

      // if reply code is OK get and print the 2 other lines
      if (reply_code == 200) {
        String SeqNumLine = RTSPBufferedReader.readLine();
        System.out.println(SeqNumLine);

        String SessionLine = RTSPBufferedReader.readLine();
        System.out.println(SessionLine);

        // if state == INIT gets the Session Id from the SessionLine
        if (state == INIT) {
          // get the session id from the SessionLine
          // System.out.println("SessionLine: " + SessionLine);

          tokens = new StringTokenizer(SessionLine);
          tokens.nextToken(); // skip over the Session:
          RTSPid = Integer.parseInt(tokens.nextToken());

        }
      }
    } catch (IOException | NumberFormatException ex) {
      System.out.println("Exception caught on parsing RTSP request: " + ex);
      System.exit(0);
    }

    return (reply_code);
  }
  // ------------------------------------
  // Send RTSP Request
  // ------------------------------------

  // Handler for Forward button
  // -----------------------
  class forwardButtonListener implements ActionListener {
    @Override
    public void actionPerformed(ActionEvent e) {
      if (state == PLAYING) {
        // increase RTSP sequence number
        RTSPSeqNb++;
        // Send FORWARD message to the server
        send_RTSP_request("FORWARD");
        // Wait for the response
        if (parse_server_response() != 200)
          System.out.println("Invalid Server Response");
        else {
          System.out.println("Forwarded 5 seconds");
        }
      } else {
        System.out.println("Cannot forward: Not in PLAYING state");
      }
    }
  }

  // Handler for Backward button
  // -----------------------
  class backwardButtonListener implements ActionListener {
    @Override
    public void actionPerformed(ActionEvent e) {
      if (state == PLAYING) {
        // increase RTSP sequence number
        RTSPSeqNb++;
        // Send BACKWARD message to the server
        send_RTSP_request("BACKWARD");
        // Wait for the response
        if (parse_server_response() != 200)
          System.out.println("Invalid Server Response");
        else {
          System.out.println("Rewound 5 seconds");
        }
      } else {
        System.out.println("Cannot rewind: Not in PLAYING state");
      }
    }
  }

  // ------------------------------------
  // Send RTSP Request
  // ------------------------------------

  private void send_RTSP_request(String request_type) {
    try {
      // Use the RTSPBufferedWriter to write to the RTSP socket

      // write the request line:
      // RTSPBufferedWriter.write(...);

      RTSPBufferedWriter.write(request_type + " " + VideoFileName + " RTSP/1.0" + CRLF);

      // write the CSeq line:
      RTSPBufferedWriter.write("CSeq: " + RTSPSeqNb + CRLF);

      // check if request_type is equal to "SETUP" and in this case write the
      // Transport: line advertising to the server the port used to receive the RTP
      // packets RTP_RCV_PORT

      if (request_type.equals("SETUP")) {
        RTSPBufferedWriter.write("Transport: RTP/UDP; client_port= " + RTP_RCV_PORT + CRLF);
      } else {
        // write the Session line from the RTSPid field
        RTSPBufferedWriter.write("Session: " + RTSPid + CRLF);
      }

      // otherwise, write the Session line from the RTSPid field
      // else ....

      RTSPBufferedWriter.flush();
    } catch (IOException ex) {
      System.out.println("Exception caught: " + ex);
      System.exit(0);
    }
  }

  // Helper method to create unicode-based icons for buttons
  private ImageIcon createIcon(String text, Color color) {
    Font iconFont = new Font(Font.SANS_SERIF, Font.BOLD, 18);

    // Calculate the size based on the font metrics
    FontMetrics metrics = new Canvas().getFontMetrics(iconFont);
    int width = Math.max(24, metrics.stringWidth(text) + 8);
    int height = Math.max(24, metrics.getHeight() + 4);

    BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
    Graphics2D g2 = image.createGraphics();

    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    g2.setFont(iconFont);
    g2.setColor(color);

    // Center the text
    int x = (width - metrics.stringWidth(text)) / 2;
    int y = ((height - metrics.getHeight()) / 2) + metrics.getAscent();

    g2.drawString(text, x, y);
    g2.dispose();

    return new ImageIcon(image);
  } // Helper method to style buttons with rounded corners and padding

  private void styleButton(JButton button, Color bgColor) {
    button.setBackground(bgColor);
    button.setForeground(Color.DARK_GRAY);
    button.setFocusPainted(false);
    button.setBorderPainted(true);
    button.setContentAreaFilled(false);
    button.setOpaque(true);
    button.setMargin(new Insets(8, 12, 8, 12));

    // Create a nice compound border with padding
    Border outerBorder = new RoundedCornerBorder(bgColor.darker());
    Border emptyBorder = BorderFactory.createEmptyBorder(5, 10, 5, 10);
    Border compoundBorder = BorderFactory.createCompoundBorder(outerBorder, emptyBorder);
    button.setBorder(compoundBorder);

    button.setCursor(new Cursor(Cursor.HAND_CURSOR));
  }

  // Custom rounded border implementation
  private static class RoundedCornerBorder extends AbstractBorder {
    private final Color borderColor;

    public RoundedCornerBorder(Color color) {
      this.borderColor = color;
    }

    @Override
    public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
      Graphics2D g2d = (Graphics2D) g.create();
      g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g2d.setColor(borderColor);
      g2d.drawRoundRect(x, y, width - 1, height - 1, 15, 15);
      g2d.dispose();
    }

    @Override
    public Insets getBorderInsets(Component c) {
      return new Insets(4, 8, 4, 8);
    }

    @Override
    public Insets getBorderInsets(Component c, Insets insets) {
      insets.set(4, 8, 4, 8);
      return insets;
    }
  }

} // end of Class Client

// end of Class Client
