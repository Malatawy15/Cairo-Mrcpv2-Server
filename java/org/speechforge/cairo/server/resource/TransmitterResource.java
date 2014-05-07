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
package org.speechforge.cairo.server.resource;

import org.speechforge.cairo.server.config.CairoConfig;
import org.speechforge.cairo.server.config.TransmitterConfig;
import org.speechforge.cairo.server.resource.session.ChannelResources;
import org.speechforge.cairo.server.resource.session.RecognizerResources;
import org.speechforge.cairo.server.resource.session.RecorderResources;
import org.speechforge.cairo.server.resource.session.TransmitterResources;
import org.speechforge.cairo.rtp.server.PortPairPool;
import org.speechforge.cairo.server.tts.MrcpSpeechSynthChannel;
import org.speechforge.cairo.server.tts.PromptGeneratorFactory;
import org.speechforge.cairo.server.tts.RTPSpeechSynthChannel;
import org.speechforge.cairo.util.CairoUtil;
import org.speechforge.cairo.rtp.AudioFormats;
import org.speechforge.cairo.sip.ResourceUnavailableException;
import org.speechforge.cairo.sip.SdpMessage;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URL;
import java.net.UnknownHostException;
import java.rmi.Naming;
import java.rmi.RemoteException;
import java.util.List;
import java.util.Map;
import java.util.Vector;

import javax.sdp.MediaDescription;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.pool.ObjectPool;
import org.apache.log4j.Logger;
import org.mrcp4j.MrcpResourceType;
import org.mrcp4j.server.MrcpServerSocket;

/**
 * Implements a {@link org.speechforge.cairo.server.resource.Resource} for handling MRCPv2 requests
 * that require generation of audio data to be streamed to the MRCPv2 client.
 *
 * @author Niels Godfredsen {@literal <}<a href="mailto:ngodfredsen@users.sourceforge.net">ngodfredsen@users.sourceforge.net</a>{@literal >}
 */
public class TransmitterResource extends ResourceImpl {

    private static Logger _logger = Logger.getLogger(TransmitterResource.class);

    public static final Resource.Type RESOURCE_TYPE = Resource.Type.TRANSMITTER;

    private File _basePromptDir;

    private String _speechSynthesizer;

    private MrcpServerSocket _mrcpServer;

    private ObjectPool _promptGeneratorPool;

    private PortPairPool _portPairPool;

	private InetAddress _myIpAddress;

    public TransmitterResource(TransmitterConfig config) 
      throws IOException, RemoteException, InstantiationException {
        super(RESOURCE_TYPE);
        _basePromptDir = config.getBasePromptDir();
	_speechSynthesizer = config.getSpeechSynthesizer();
        _mrcpServer = new MrcpServerSocket(config.getMrcpPort());
        _promptGeneratorPool = PromptGeneratorFactory.createObjectPool(config.getVoiceName(), config.getEngines(), _speechSynthesizer);
        _portPairPool = new PortPairPool(config.getRtpBasePort(), config.getMaxConnects());
        
        //if in config file, use as specified else get the local host programatically
        //_myIpAddress = InetAddress.getByName(config.getIpAddress());
        //_logger.info(_myIpAddress);
        //if (_myIpAddress == null) {
        	_myIpAddress = CairoUtil.getLocalHost();
        //}
        _logger.info(_myIpAddress);
    }

    /* (non-Javadoc)

     * @see org.speechforge.cairo.server.resource.Resource#invite(org.speechforge.cairo.server.resource.ResourceMessage)
     */
    public SdpMessage invite(SdpMessage request, String sessionId) throws ResourceUnavailableException, RemoteException {
        _logger.debug("Resource received invite() request.");
        _logger.debug(request.getSessionDescription().toString());
        InetAddress remoteHost = null;

        // Create a resource session object
        // TODO: Check if there is already a session (ie. This is a re-invite)        
        ResourceSession session = ResourceSession.createResourceSession(sessionId);
        
        // get the map that holds list of the channels and the resources used for each channel
        // the key is the dialogID
        Map<String, ChannelResources> sessionChannels = session.getChannels();
        
        
        try {
            List<MediaDescription> channels = request.getMrcpTransmitterChannels();
            if (channels.size() > 0) {

                remoteHost = InetAddress.getByName(request.getSessionAddress());
                InetAddress mediaHost = remoteHost;
                int localPort = 0;
                int remotePort = 0;
                RTPSpeechSynthChannel rtpscc;
                for (MediaDescription md : channels) {
                    String channelID = md.getAttribute(SdpMessage.SDP_CHANNEL_ATTR_NAME);
                    String rt = md.getAttribute(SdpMessage.SDP_RESOURCE_ATTR_NAME);

                    MrcpResourceType resourceType = MrcpResourceType.fromString(rt);

                    // if (rt.equalsIgnoreCase("speechrecog")) {
                    // resourceType = MrcpResourceType.SPEECHRECOG;
                    // } else if (rt.equalsIgnoreCase("speechsynth")) {
                    // resourceType = MrcpResourceType.SPEECHSYNTH;
                    // }
                    AudioFormats af = null;
                    List<MediaDescription> rtpmd = request.getAudioChansForThisControlChan(md);
                    Vector formatsInRequest = null;
                    if (rtpmd.size() > 0) {
                        //TODO: Complete the method below that checks if audio format is supported.  
                        //      If not resource not available exception should be shown.
                        //      maybe this could be part of the up-front validation
                        formatsInRequest = rtpmd.get(0).getMedia().getMediaFormats(true);      
                        af  = AudioFormats.constructWithSdpVector(formatsInRequest);
                        
                        // TODO: What if there is more than 1 media channels?
                        localPort = _portPairPool.borrowPort();
                        
                        // TODO: check if there is an override for the host attribute in the m block
                        // InetAddress remoteHost = InetAddress.getByName(rtpmd.get(1).getAttribute();
                        remotePort = rtpmd.get(0).getMedia().getMediaPort();
                        
                        //get the host for the rtp channel.  maybe the media is going to a differnet host.
                        //if so tehre will be a c-line in the media block
                        if (rtpmd.get(0).getConnection()!= null)
                            mediaHost = InetAddress.getByName(rtpmd.get(0).getConnection().getAddress());
                        
                    } else {
                        _logger.warn("No Media channel specified in the invite request");
                        // TODO: handle no media channel in the request corresponding to the mrcp channel (sip error)
                    }

                    switch (resourceType) {
                    case BASICSYNTH:
                    case SPEECHSYNTH:

                        rtpscc = new RTPSpeechSynthChannel(localPort, _myIpAddress, mediaHost, remotePort,af);
                        MrcpSpeechSynthChannel mrcpChannel = new MrcpSpeechSynthChannel(channelID, rtpscc, _basePromptDir, _promptGeneratorPool, _speechSynthesizer);
                        _mrcpServer.openChannel(channelID, mrcpChannel);
                        md.getMedia().setMediaPort(_mrcpServer.getPort());
                        rtpmd.get(0).getMedia().setMediaFormats(af.filterOutUnSupportedFormatsInOffer());
                        _logger.debug("Created a SPEECHSYNTH Channel.  id is: "+channelID+" rtp remotehost:port is: "+ mediaHost+":"+remotePort);
                        break;

                    default:
                        throw new ResourceUnavailableException("Unsupported resource type!");
                    }
                    
                    // Create a channel resources object and put it in the channel map (which is in the session).  
                    // These resources must be returned to the pool when the channel is closed.  In the case of a 
                    // transmitter, the resource is the RTP port in the port pair pool
                    // TODO:  The channels should cleanup after themselves (retrun resource to pools)
                    //        instead of keeping track of the resoruces in the session.
                    ChannelResources cr = new TransmitterResources();
                    cr.setChannelId(channelID);
                    ((TransmitterResources)cr).setPort(localPort);
                    ((TransmitterResources)cr).setRtpssc(rtpscc);
                    sessionChannels.put(channelID, cr);
                }
            } else {
                _logger.warn("Invite request had no channels.");
            }
        } catch (ResourceUnavailableException e) {
            _logger.debug(e, e);
            throw e;
        } catch (UnknownHostException e) {
            _logger.debug("Specified host for media stream not found: " + remoteHost, e);
            throw new RemoteException(e.getMessage(), e);
        } catch (Exception e) {
            e.printStackTrace();
            _logger.debug(e, e);
            throw new ResourceUnavailableException(e);
        }
        // Add the session to the session list
        ResourceSession.addSession(session);
        
        return request;
    }



    public void bye(String sessionId) throws  RemoteException, InterruptedException {
        ResourceSession session = ResourceSession.getSession(sessionId);
        Map<String, ChannelResources> sessionChannels = session.getChannels();
        for(ChannelResources channel: sessionChannels.values()) {
            
           	//always close the mrcp channel (common to resources)
            _mrcpServer.closeChannel(channel.getChannelId());
            
            //then do resource specific cleanup
            //TODO: remove instanceof if statements (and casting) and add specific code to resources class (abstract method)
            //issue is that each resource needs a reference to a different pool so a common cleanup method will be hard
            //maybe just pass in an interface to "this" to the cleanup method that can get access to pools.
            if (channel instanceof TransmitterResources) {
            	TransmitterResources r = (TransmitterResources) channel;
                r.getRtpssc().shutdown();
                _portPairPool.returnPort(r.getPort());

            } else {
            	_logger.warn("Unsupported channel resource of type: "+channel.toString());
            }
   
        }
        ResourceSession.removeSession(session);
    }
    
    public static void main(String[] args) throws Exception {

        CommandLineParser parser = new GnuParser();
        Options options = getOptions();
        CommandLine line = parser.parse(options, args, true);
        args = line.getArgs();
        
        if (args.length != 2 || line.hasOption("help")) {
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp("TransmitterResource [options] <cairo-config-URL> <resource-name>", options);
            return;
        }

        URL configURL = CairoUtil.argToURL(args[0]);
        String resourceName = args[1];

        CairoConfig config = new CairoConfig(configURL);
        TransmitterConfig resourceConfig = config.getTransmitterConfig(resourceName);

        StringBuilder rmiUrl = new StringBuilder("rmi://");
        if (line.hasOption(RSERVERHOST_OPTION)) {
            rmiUrl.append(line.getOptionValue(RSERVERHOST_OPTION));
        } else {
            rmiUrl.append(CairoUtil.getLocalHost().getHostName());
        }
        rmiUrl.append('/').append(ResourceRegistry.NAME);

        _logger.info("looking up: " + rmiUrl);
        ResourceRegistry resourceRegistry = (ResourceRegistry) Naming.lookup(rmiUrl.toString());

        TransmitterResource impl = new TransmitterResource(resourceConfig);

        _logger.info("binding transmitter resource...");
        resourceRegistry.register(impl, RESOURCE_TYPE);

        _logger.info("Resource bound and waiting...");

    }



}
