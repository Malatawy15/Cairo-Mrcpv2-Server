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
import org.speechforge.cairo.server.config.ReceiverConfig;
import org.speechforge.cairo.server.recog.MrcpRecogChannel;
import org.speechforge.cairo.server.recog.RTPRecogChannel;
import org.speechforge.cairo.server.recog.sphinx.SphinxRecEngineFactory;
import org.speechforge.cairo.server.recorder.MrcpRecorderChannel;
import org.speechforge.cairo.server.recorder.RTPRecorderChannel;
import org.speechforge.cairo.server.recorder.RTPRecorderChannelImpl;
import org.speechforge.cairo.server.recorder.RTPRecorderChannelS4Impl;
import org.speechforge.cairo.server.recorder.sphinx.SphinxRecorderFactory;
import org.speechforge.cairo.server.resource.session.ChannelResources;
import org.speechforge.cairo.server.resource.session.RecognizerResources;
import org.speechforge.cairo.server.resource.session.RecorderResources;
import org.speechforge.cairo.rtp.server.RTPStreamReplicator;
import org.speechforge.cairo.rtp.server.RTPStreamReplicatorFactory;
import org.speechforge.cairo.util.CairoUtil;
import org.speechforge.cairo.rtp.AudioFormats;
import org.speechforge.cairo.sip.ResourceUnavailableException;
import org.speechforge.cairo.sip.SdpMessage;
import org.speechforge.cairo.sip.SipSession;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URL;
import java.rmi.Naming;
import java.rmi.RemoteException;
import java.util.List;
import java.util.Map;
import java.util.Vector;

import javax.media.protocol.ContentDescriptor;
import javax.media.protocol.FileTypeDescriptor;
import javax.sdp.MediaDescription;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.lang.Validate;
import org.apache.commons.pool.ObjectPool;
import org.apache.log4j.Logger;
import org.mrcp4j.MrcpResourceType;
import org.mrcp4j.server.MrcpServerSocket;

/**
 * Implements a {@link org.speechforge.cairo.server.resource.Resource} for handling MRCPv2 requests
 * that require processing of audio data streamed to the resource.
 *
 * @author Niels Godfredsen {@literal <}<a href="mailto:ngodfredsen@users.sourceforge.net">ngodfredsen@users.sourceforge.net</a>{@literal >}
 */
public class ReceiverResource extends ResourceImpl {

    private static Logger _logger = Logger.getLogger(ReceiverResource.class);

    public static final Resource.Type RESOURCE_TYPE = Resource.Type.RECEIVER;

    private MrcpServerSocket _mrcpServer;
    private ObjectPool _replicatorPool;
    private ObjectPool _recEnginePool;
    private ObjectPool _recorderEnginePool;
        

    private File _baseRecordingDir;
    private File _baseGrammarDir;

    public ReceiverResource(ReceiverConfig config)
      throws IOException, RemoteException, InstantiationException {
        super(RESOURCE_TYPE);
        _baseRecordingDir = config.getBaseRecordingDir();
        _baseGrammarDir = config.getBaseGrammarDir();
        _mrcpServer = new MrcpServerSocket(config.getMrcpPort());
        
        InetAddress localIpAddress = CairoUtil.getLocalHost();
        //InetAddress localIpAddress = InetAddress.getByName(config.getIpAddress());
        //if (localIpAddress == null) {
        //	localIpAddress = CairoUtil.getLocalHost();
        //}
        _replicatorPool = RTPStreamReplicatorFactory.createObjectPool(
                config.getRtpBasePort(), config.getMaxConnects(), localIpAddress);
        _recEnginePool = SphinxRecEngineFactory.createObjectPool(
                config.getSphinxConfigURL(), config.getEngines());
        _recorderEnginePool = SphinxRecorderFactory.createObjectPool(
                config.getSphinxRecorderConfigURL(), config.getRecorderEngines());
    }

    /* (non-Javadoc)
     * @see org.speechforge.cairo.server.resource.Resource#invite(org.speechforge.cairo.server.resource.ResourceMessage)
     */
    public SdpMessage invite(SdpMessage request, String sessionId) throws ResourceUnavailableException {
        _logger.debug("Resource received invite() request.");

        // Create a resource session object
        // TODO: Check if there is already a session (ie. This is a re-invite)        
        ResourceSession session = ResourceSession.createResourceSession(sessionId);
        
        // get the map that holds list of the channels and the resources used for each channel
        // the key is the dialogID
        Map<String, ChannelResources> sessionChannels = session.getChannels();
        
        
        try {
            List<MediaDescription> channels = request.getMrcpReceiverChannels();
            List<MediaDescription> c2 = request.getMrcpRecorderChannels();
            channels.addAll(c2);
            Vector formatsInRequest = null;
            if (channels.size() > 0) {

                for(MediaDescription md: channels) {
                    String channelID = md.getAttribute(SdpMessage.SDP_CHANNEL_ATTR_NAME);
                    String rt =  md.getAttribute(SdpMessage.SDP_RESOURCE_ATTR_NAME);
                    MrcpResourceType resourceType = null;
                    _logger.debug("Resource Type: " +rt);
                    if (rt.equalsIgnoreCase("speechrecog")) {
                        resourceType = MrcpResourceType.SPEECHRECOG;
                    } else if (rt.equalsIgnoreCase("speechsynth")) {
                        resourceType = MrcpResourceType.SPEECHSYNTH;
                    } else if (rt.equalsIgnoreCase("recorder")) {
                        resourceType = MrcpResourceType.RECORDER;
                    }


                    List<MediaDescription> rtpmd = null;
                    RTPStreamReplicator replicator = null;
                    AudioFormats af = null;
                    switch (resourceType) {
                    case  SPEECHRECOG:
                        rtpmd = request.getAudioChansForThisControlChan(md);
                        //TODO: Check if audio format is supported.  If not resource not available exception should be shown.
                        //      maybe this could be part of the up-front validation
                        formatsInRequest = rtpmd.get(0).getMedia().getMediaFormats(true); 
                        af  = AudioFormats.constructWithSdpVector(formatsInRequest);
                        //formatsInRequest = AudioFormats.filterOutUnSupportedFormatsInOffer(formatsInRequest);
 
                        //if null, borrow a replicator.  else we will use the same one fo the other channel in this request
                        //TODO: Is this correct in all cases?  Could there be seperate rtp channels for recording and recognizing? 
                        if (replicator == null )
                           replicator =  (RTPStreamReplicator) _replicatorPool.borrowObject();
                        if (rtpmd.size() > 0) {
                            //TODO: What if there is more than 1 media channels?

                            rtpmd.get(0).getMedia().setMediaPort(replicator.getPort());
                        } else {
                            //TODO:  handle no media channel in the request corresponding to the mrcp channel (sip error)
                        }

                        RTPRecogChannel recog = new RTPRecogChannel(_recEnginePool, replicator);
                        _mrcpServer.openChannel(channelID, new MrcpRecogChannel(channelID, recog, _baseGrammarDir));
                        md.getMedia().setMediaPort(_mrcpServer.getPort());
                        rtpmd.get(0).getMedia().setMediaFormats(af.filterOutUnSupportedFormatsInOffer());
                        
                        // Create a channel resources object and put it in the channel map (which is in the session).  
                        // These resources must be returned to the pool when the channel is closed.  In the case of a 
                        // transmitter, the resource is the RTP Replicator in the rtpReplicatorPool
                        // TODO:  The channels should cleanup after themselves (retrun resource to pools)
                        //        instead of having to keep track of the resoruces in the session.
                        ChannelResources cr = new RecognizerResources();
                        cr = new RecognizerResources();
                        cr.setChannelId(channelID);
                        ((RecognizerResources)cr).setReplicator(replicator);
                        ((RecognizerResources)cr).setRecog(recog);
                        sessionChannels.put(channelID, cr);
                        break;

                     case RECORDER:
                    	 _logger.info("processing recorder request...");
                         rtpmd = request.getAudioChansForThisControlChan(md);
                         //TODO: Check if audio format is supported.  If not resource not available exception should be shown.
                         //      maybe this could be part of the up-front validation
                         formatsInRequest = rtpmd.get(0).getMedia().getMediaFormats(true); 
                         af  = AudioFormats.constructWithSdpVector(formatsInRequest);
                         //formatsInRequest = AudioFormats.filterOutUnSupportedFormatsInOffer(formatsInRequest);
  

                         //if null, borrow a replicator.  else we will use the same one fo the other channel in this request
                         //TODO: Is this correct in all cases?  Could there be seperate rtp channels for recording and recognizing? 
                         if (replicator == null )
                             replicator =  (RTPStreamReplicator) _replicatorPool.borrowObject();
                         if (rtpmd.size() > 0) {
                             //TODO: What if there is more than 1 media channels?

                             rtpmd.get(0).getMedia().setMediaPort(replicator.getPort());
                         } else {
                             //TODO:  handle no media channel in the request corresponding to the mrcp channel (sip error)
                         }                  	 
                    	 
                     	//Validate.isTrue(_baseRecordingDir.isDirectory(), "baseRecordingDir parameter was not a directory: ", _baseRecordingDir);
                        File recordingDir = new File(_baseRecordingDir, channelID);
                        
                        //TODO: get this from the mrcp header
                        ContentDescriptor cd = new FileTypeDescriptor(FileTypeDescriptor.WAVE);

                        RTPRecorderChannel recorder = new RTPRecorderChannelS4Impl(_recorderEnginePool, recordingDir, replicator, cd);
                        _mrcpServer.openChannel(channelID, new MrcpRecorderChannel(recorder));
                        md.getMedia().setMediaPort(_mrcpServer.getPort());
                        rtpmd.get(0).getMedia().setMediaFormats(af.filterOutUnSupportedFormatsInOffer());   
                        _logger.info(_mrcpServer.getPort());
  
                        // Create a channel resources object and put it in the channel map (which is in the session).  
                        // These resources must be returned to the pool when the channel is closed.  In the case of a 
                        // transmitter, the resource is the RTP Replicator in the rtpReplicatorPool
                        // TODO:  The channels should cleanup after themselves (retrun resource to pools)
                        //        instead of having to keep track of the resoruces in the session.
                        ChannelResources recRes = new RecorderResources();
                        recRes.setChannelId(channelID);
                        ((RecorderResources)recRes).setRecorderReplicator(replicator);
                        ((RecorderResources)recRes).setRecorder(recorder);
                        sessionChannels.put(channelID, recRes);
                        
                        break;

                    default:
                        throw new ResourceUnavailableException("Unsupported resource type: " + resourceType);
                    }

                }
            }
        } catch (ResourceUnavailableException e) {
            _logger.debug(e, e);
            throw e;
        } catch (Exception e) {
            _logger.debug(e, e);
            throw new ResourceUnavailableException(e);
        }
        // Add the session to the session list
        ResourceSession.addSession(session);
        return request;
    }

    public void bye(String sessionId) throws  RemoteException {      
        ResourceSession session = ResourceSession.getSession(sessionId);
        Map<String, ChannelResources> sessionChannels = session.getChannels();
        for(ChannelResources channel: sessionChannels.values()) {
        	//always close the mrcp channel (common to resources)
            _mrcpServer.closeChannel(channel.getChannelId());
            
            //then do resource specific cleanup
            //TODO: remove instanceof if statements (and casting) and add specific code to resources class (abstract method)
            //issue is that each resource needs a reference to a different pool so a common cleanup method will be hard
            //maybe just pass in an interface to "this" to the cleanup method that can get access to pools.
            if (channel instanceof RecognizerResources) {
            	RecognizerResources r = (RecognizerResources) channel;
	            //r.getRecog().closeProcessor();
            	r.getReplicator().shutdown();
	            try {
	                _replicatorPool.returnObject(r.getReplicator());
	            } catch (Exception e) {
	                _logger.debug(e, e);
	                throw new RemoteException(e.getMessage(), e);
	            }
            } else if (channel instanceof RecorderResources) {
            	RecorderResources r = (RecorderResources) channel;
	            //r.getRecorder().closeProcessor();
	            try {
	                _replicatorPool.returnObject(r.getRecorderReplicator());
	            } catch (Exception e) {
	                _logger.debug(e, e);
	                throw new RemoteException(e.getMessage(), e);
	            }
            } else {
            	_logger.warn("Unsupported channle resource of type "+channel.toString());
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
            formatter.printHelp("ReceiverResource [options] <cairo-config-URL> <resource-name>", options);
            return;
        }

        URL configURL = CairoUtil.argToURL(args[0]);
        String resourceName = args[1];

        CairoConfig config = new CairoConfig(configURL);
        ReceiverConfig resourceConfig = config.getReceiverConfig(resourceName);

        StringBuilder rmiUrl = new StringBuilder("rmi://");
        if (line.hasOption(RSERVERHOST_OPTION)) {
            rmiUrl.append(line.getOptionValue(RSERVERHOST_OPTION));
        } else {
            rmiUrl.append(CairoUtil.getLocalHost().getHostName());
        }
        rmiUrl.append('/').append(ResourceRegistry.NAME);

        _logger.info("looking up: " + rmiUrl);
        ResourceRegistry resourceRegistry = (ResourceRegistry) Naming.lookup(rmiUrl.toString());

        ReceiverResource impl = new ReceiverResource(resourceConfig);

        _logger.info("binding receiver resource...");
        resourceRegistry.register(impl, RESOURCE_TYPE);

        _logger.info("Resource bound and waiting...");

    }
}
