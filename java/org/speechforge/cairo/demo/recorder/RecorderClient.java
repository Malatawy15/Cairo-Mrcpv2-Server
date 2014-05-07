/*
 * Cairo - Open source framework for control of speech media resources.
 *
 * Copyright (C) 2005-2006 SpeechForge - http://www.speechforge.org
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 *
 * Contact: ngodfredsen@users.sourceforge.net
 *
 */
package org.speechforge.cairo.demo.recorder;

import org.speechforge.cairo.rtp.NativeMediaClient;
import org.speechforge.cairo.server.resource.ResourceImpl;
import org.speechforge.cairo.rtp.RTPConsumer;
import org.speechforge.cairo.sip.SimpleSipAgent;
import org.speechforge.cairo.sip.SipAgent;
import org.speechforge.cairo.sip.SdpMessage;
import org.speechforge.cairo.sip.SipSession;
import org.speechforge.cairo.util.CairoUtil;

import java.awt.Toolkit;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Vector;

import javax.sdp.MediaDescription;
import javax.sdp.SdpConstants;
import javax.sdp.SdpException;
import javax.sip.SipException;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.log4j.Logger;
import org.mrcp4j.MrcpEventName;
import org.mrcp4j.MrcpMethodName;
import org.mrcp4j.MrcpRequestState;
import org.mrcp4j.MrcpResourceType;
import org.mrcp4j.client.MrcpChannel;
import org.mrcp4j.client.MrcpEventListener;
import org.mrcp4j.client.MrcpFactory;
import org.mrcp4j.client.MrcpInvocationException;
import org.mrcp4j.client.MrcpProvider;
import org.mrcp4j.message.MrcpEvent;
import org.mrcp4j.message.MrcpResponse;
import org.mrcp4j.message.header.CompletionCause;
import org.mrcp4j.message.header.IllegalValueException;
import org.mrcp4j.message.header.MrcpHeader;
import org.mrcp4j.message.header.MrcpHeaderName;
import org.mrcp4j.message.request.MrcpRequest;

/**
 * Demo MRCPv2 client application that utilizes a {@code recognizer} resource to perform
 * recording from a microphone input.
 *
 * @author Spencer Lord {@literal <}<a href="mailto:salord@users.sourceforge.net">salord@users.sourceforge.net</a>{@literal >}
 */
public class RecorderClient implements MrcpEventListener {

    private static Logger _logger = Logger.getLogger(RecorderClient.class);

    private static final String BEEP_OPTION = "beep";
    private static final String URL_OPTION = "url";

    private static boolean _beep = false;
    private static Toolkit _toolkit = null;
    private static boolean _url;

    private MrcpChannel _recogChannel;
    private MrcpEvent _mrcpEvent;
    private static SimpleSipAgent sipAgent;
    private static  boolean sentBye=false;

    private static int _myPort = 5080;
    private static String _host = null;
    private static int _peerPort = 5050;
    private static String _mySipAddress ="sip:speechSynthClient@speechforge.org";
    private static String _cairoSipAddress="sip:cairo@speechforge.org";
    private static NativeMediaClient mediaClient;
    
    /**
     * TODOC
     * @param recogChannel 
     */
    public RecorderClient(MrcpChannel recogChannel) {
        _recogChannel = recogChannel;
        _recogChannel.addEventListener(this);
    }

    /* (non-Javadoc)
     * @see org.mrcp4j.client.MrcpEventListener#eventReceived(org.mrcp4j.message.MrcpEvent)
     */
    public void eventReceived(MrcpEvent event) {
        if (_logger.isDebugEnabled()) {
            _logger.debug("MRCP event received:\n" + event.toString());
        }

        try {
            switch (event.getChannelIdentifier().getResourceType()) {

            case RECORDER:
                recorderEventReceived(event);
                break;

            default:
                _logger.warn("Unexpected value for event resource type!");
                break;
            }
        } catch (IllegalValueException e) {
            _logger.warn("Illegal value for event resource type!", e);
        }
   }

    private void recorderEventReceived(MrcpEvent event) {

        MrcpEventName eventName = event.getEventName();

        if (MrcpEventName.RECORD_COMPLETE.equals(eventName)) {
            if (_beep) {
                _toolkit.beep();
            }
            synchronized (this) {
                _mrcpEvent = event;
                this.notifyAll();
            }
            System.exit(0);
        }
    }

    public synchronized String doRecording(String uri)
      throws IOException, MrcpInvocationException, InterruptedException, IllegalValueException {

        _mrcpEvent = null;

        // recog request
        MrcpRequest request = _recogChannel.createRequest(MrcpMethodName.RECORD);
        request.addHeader(MrcpHeaderName.NO_INPUT_TIMEOUT.constructHeader(new Long(30000)));
        request.addHeader(MrcpHeaderName.RECORD_URI.constructHeader(uri));
 
        MrcpResponse response = _recogChannel.sendRequest(request);

        if (_beep) {
            _toolkit.beep();
        }

        if (_logger.isDebugEnabled()) {
            _logger.debug("MRCP response received:\n" + response.toString());
        }
        
        if (response.getRequestState().equals(MrcpRequestState.COMPLETE)) {
            throw new RuntimeException("Recording failed to start!");
        }

        
        while (_mrcpEvent == null) {
            this.wait();
        }

        MrcpHeader completionCauseHeader = _mrcpEvent.getHeader(MrcpHeaderName.COMPLETION_CAUSE);
        CompletionCause completionCause = (CompletionCause) completionCauseHeader.getValueObject();

        return (completionCause.getCauseCode() == 0) ? _mrcpEvent.getContent() : null ;
        
        //return response.getContent();

    }


    public synchronized String stopRecording()
    throws IOException, MrcpInvocationException, InterruptedException, IllegalValueException {

      _mrcpEvent = null;

      // recog request
      MrcpRequest request = _recogChannel.createRequest(MrcpMethodName.STOP);

      MrcpResponse response = _recogChannel.sendRequest(request);

      if (_beep) {
          _toolkit.beep();
      }

      if (_logger.isDebugEnabled()) {
          _logger.debug("MRCP response received:\n" + response.toString());
      }
      
      if (response.getRequestState().equals(MrcpRequestState.COMPLETE)) {
          throw new RuntimeException("Recording failed to start!");
      }
      


      /*
      while (_mrcpEvent == null) {
          this.wait();
      }

      MrcpHeader completionCauseHeader = _mrcpEvent.getHeader(MrcpHeaderName.COMPLETION_CAUSE);
      CompletionCause completionCause = (CompletionCause) completionCauseHeader.getValueObject();

      return (completionCause.getCauseCode() == 0) ? _mrcpEvent.getContent() : null ;
      */
      return response.getContent();

  }
    
////////////////////////////////////
//static methods
////////////////////////////////////
    
    private static SdpMessage constructResourceMessage(int localRtpPort,Vector format) throws UnknownHostException, SdpException {
        SdpMessage sdpMessage = SdpMessage.createNewSdpSessionMessage(_mySipAddress, _host, "The session Name");
        MediaDescription rtpChannel = SdpMessage.createRtpChannelRequest(localRtpPort,format);
        MediaDescription mrcpChannel = SdpMessage.createMrcpChannelRequest(MrcpResourceType.RECORDER);
        Vector v = new Vector();
        v.add(mrcpChannel);
        v.add(rtpChannel);
        sdpMessage.getSessionDescription().setMediaDescriptions(v);
        return sdpMessage;
    }

    public static Options getOptions() {
        Options options = ResourceImpl.getOptions();

        Option option = new Option(BEEP_OPTION, "play response/event timing beep");
        options.addOption(option);
        
        option = new Option(URL_OPTION, "include the grammar in mrcp message or just include the url to the grammar");
        options.addOption(option);

        return options;
    }


////////////////////////////////////
//  main method
////////////////////////////////////

    public static void main(String[] args) throws Exception {

        // setup a shutdown hook to cleanup and send a SIP bye message even if there is a 
        // unexpected crash (ie ctrl-c)
        Runtime.getRuntime().addShutdownHook(new Thread() {
            public void run() {
                _logger.debug("Running shutdown hook");
                if (mediaClient != null)
                   mediaClient.shutdown();
                if (!sentBye && sipAgent!=null) {
                    try {
                        sipAgent.sendBye();
                        sipAgent.dispose();
                    } catch (SipException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }

                }
            }
        }); 
        
        CommandLineParser parser = new GnuParser();
        Options options = getOptions();
        CommandLine line = parser.parse(options, args, true);
        args = line.getArgs();

        if (args.length < 1 || args.length > 2 || line.hasOption(ResourceImpl.HELP_OPTION)) {
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp("RecognitionClient [options] <local-rtp-port> ", options);
            return;
        }

        _beep = line.hasOption(BEEP_OPTION);
        if (_beep) {
            _toolkit = Toolkit.getDefaultToolkit();
        }

        
        _url = line.hasOption(URL_OPTION);
        
        int localRtpPort = -1;
        try {
            localRtpPort = Integer.parseInt(args[0]);
        } catch (Exception e) {
            _logger.debug(e, e);
        }

        if (localRtpPort < 0 || localRtpPort >= RTPConsumer.TCP_PORT_MAX || localRtpPort % 2 != 0) {
            throw new Exception("Improper format for first command line argument <local-rtp-port>," +
                " should be even integer between 0 and " + RTPConsumer.TCP_PORT_MAX);
        }

        //URL grammarUrl = new URL(args[1]);
        //String examplePhrase = (args.length > 2) ? args[2] : null;

        // lookup resource server
        InetAddress rserverHost = line.hasOption(ResourceImpl.RSERVERHOST_OPTION) ?
            InetAddress.getByName(line.getOptionValue(ResourceImpl.RSERVERHOST_OPTION)) : CairoUtil.getLocalHost();
      

        try {
            _host = CairoUtil.getLocalHost().getHostAddress();
        } catch (UnknownHostException e) {
            _host = "localhost";
        }
        String peerAddress = rserverHost.getHostAddress();

        // Construct a SIP agent to be used to send a SIP Invitation to the cairo server
        sipAgent = new SimpleSipAgent(_mySipAddress, "Synth Client Sip Stack", _myPort, "UDP");

        // Construct the SDP message that will be sent in the SIP invitation
        Vector format = new Vector();
        format.add(SdpConstants.PCMU);
        SdpMessage message = constructResourceMessage(localRtpPort,format);

        // Send the sip invitation (This method on the SipAgent blocks until a response is received or a timeout occurs) 
        _logger.info("Sending a SIP invitation to the cairo server.");
        SdpMessage inviteResponse = sipAgent.sendInviteWithoutProxy(_cairoSipAddress, message, peerAddress, _peerPort);
        if (inviteResponse != null) {
            _logger.info("Received the SIP Response.");

            // Get the MRCP media channels (need the port number and the channelID that are sent
            // back from the server in the response in order to setup the MRCP channel)
            List <MediaDescription> receiverChans = inviteResponse.getMrcpRecorderChannels();
            MediaDescription controlChan = receiverChans.get(0);
            int port = controlChan.getMedia().getMediaPort();
            String channelId = receiverChans.get(0).getAttribute(SdpMessage.SDP_CHANNEL_ATTR_NAME);

            List <MediaDescription> rtpChans = inviteResponse.getAudioChansForThisControlChan(controlChan);
            int remoteRtpPort = -1;
            if (rtpChans.size() > 0) {
                //TODO: What if there is more than 1 media channels?
                //TODO: check if there is an override for the host 9attribute in the m block
                //InetAddress remoteHost = InetAddress.getByName(rtpmd.get(1).getAttribute();
                remoteRtpPort =  rtpChans.get(0).getMedia().getMediaPort();
                //rtpmd.get(1).getMedia().setMediaPort(localPort);
            } else {
                _logger.warn("No Media channel specified in the invite request");
                //TODO:  handle no media channel in the response corresponding tp the mrcp channel (sip/sdp error)
            }

            _logger.debug("Starting NativeMediaClient...");
            mediaClient = new NativeMediaClient(localRtpPort, rserverHost, remoteRtpPort);
            mediaClient.startTransmit();

            //Construct the MRCP Channel
            String protocol = MrcpProvider.PROTOCOL_TCP_MRCPv2;
            MrcpFactory factory = MrcpFactory.newInstance();
            MrcpProvider provider = factory.createProvider();
            MrcpChannel recordChannel = provider.createChannel(channelId, rserverHost, port, protocol);

            RecorderClient client = new RecorderClient(recordChannel);

            String uri = "file:///temp/test.wav";
            try {
                String result = client.doRecording(uri);   
                if (_logger.isInfoEnabled()) {
                    StringBuilder sb = new StringBuilder();
                    sb.append("\n**************************************************************");
                    sb.append("\nRecording result: ").append(result);
                    sb.append("\n**************************************************************\n");
                    _logger.info(sb);
                }


            } catch (Exception e){
                if (e instanceof MrcpInvocationException) {
                    MrcpResponse response = ((MrcpInvocationException) e).getResponse();
                    if (_logger.isDebugEnabled()) {
                        _logger.debug("MRCP response received:\n" + response.toString());
                    }
                }
                _logger.warn(e, e);
                sipAgent.sendBye();
                sipAgent.dispose();
                sentBye = true;
                System.exit(1);
            }

        } else {
            //Invitation Timeout
            _logger.info("Sip Invitation timed out.  Is server running?");
        }
        Thread.sleep(1000000); 
    }

}
