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
package org.speechforge.cairo.server.tts;

import org.speechforge.cairo.exception.UnsupportedHeaderException;
import org.speechforge.cairo.server.MrcpGenericChannel;
import org.speechforge.cairo.server.resource.TransmitterResource;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.concurrent.TimeoutException;

import javax.media.rtp.InvalidSessionAddressException;

import org.apache.commons.pool.ObjectPool;
import org.apache.log4j.Logger;
import org.mrcp4j.MrcpEventName;
import org.mrcp4j.MrcpRequestState;
import org.mrcp4j.message.MrcpEvent;
import org.mrcp4j.message.MrcpResponse;
import org.mrcp4j.message.header.CompletionCause;
import org.mrcp4j.message.header.IllegalValueException;
import org.mrcp4j.message.header.MrcpHeader;
import org.mrcp4j.message.header.MrcpHeaderName;
import org.mrcp4j.message.request.StopRequest;
import org.mrcp4j.message.request.MrcpRequestFactory.UnimplementedRequest;
import org.mrcp4j.server.MrcpSession;
import org.mrcp4j.server.provider.SpeechSynthRequestHandler;


/**
 * Handles MRCPv2 speech synthesis requests by delegating to a dedicated {@link org.speechforge.cairo.server.tts.RTPSpeechSynthChannel}.
 *
 * @author Niels Godfredsen {@literal <}<a href="mailto:ngodfredsen@users.sourceforge.net">ngodfredsen@users.sourceforge.net</a>{@literal >}
 */
public class MrcpSpeechSynthChannel extends MrcpGenericChannel implements SpeechSynthRequestHandler {

    private static Logger _logger = Logger.getLogger(MrcpSpeechSynthChannel.class);

//    private static short IDLE = 0;
//    private static short SPEAKING = 1;
//    private static short PAUSED = 2;
//
//    volatile short _state = IDLE;

    private ObjectPool _promptGeneratorPool;
    private RTPSpeechSynthChannel _rtpChannel;
    private File _promptDir;
    private String _speechSynthesizer;

    /**
     * TODOC
     * @param channelID 
     * @param basePromptDir 
     * @param rtpChannel 
     * @param promptGeneratorPool 
     * @throws IllegalArgumentException 
     */
    public MrcpSpeechSynthChannel(String channelID, RTPSpeechSynthChannel rtpChannel, File basePromptDir, ObjectPool promptGeneratorPool, String speechSynthesizer)
      throws IllegalArgumentException {

        if (basePromptDir == null || !basePromptDir.isDirectory()) {
            throw new IllegalArgumentException("Base prompt directory file specified does not exist or is not a directory: " + basePromptDir);
        }

        _promptDir = new File(basePromptDir, channelID);
        if (!_promptDir.mkdir()) {
            throw new RuntimeException("Could not make prompt directory: " + _promptDir.getAbsolutePath());
        }

        _rtpChannel = rtpChannel;
        _promptGeneratorPool = promptGeneratorPool;

	if(speechSynthesizer == null)
	    _speechSynthesizer = "Mary";
	else
	    _speechSynthesizer = speechSynthesizer;
    }

    /* (non-Javadoc)
     * @see org.mrcp4j.server.provider.SpeechSynthRequestHandler#speak(org.mrcp4j.message.request.MrcpRequestFactory.UnimplementedRequest, org.mrcp4j.server.MrcpSession)
     */
    public synchronized MrcpResponse speak(UnimplementedRequest request, MrcpSession session) {
        MrcpRequestState requestState = MrcpRequestState.COMPLETE;
        short statusCode = -1;

        _logger.debug(request.getContent());
        if (request.hasContent()) {
            String contentType = request.getContentType();
            if (contentType.equalsIgnoreCase("text/plain")) {
                String text = request.getContent();
                try {
                    File promptFile = generatePrompt(text);
                    int state = _rtpChannel.queuePrompt(promptFile, new Listener(session));
                    requestState = (state == RTPSpeechSynthChannel.IDLE) ? MrcpRequestState.IN_PROGRESS : MrcpRequestState.PENDING;
                    statusCode = MrcpResponse.STATUS_SUCCESS;
                } catch (RuntimeException e) {
                    _logger.debug(e, e);
                    statusCode = MrcpResponse.STATUS_SERVER_INTERNAL_ERROR;
                } catch (InvalidSessionAddressException e) {
                    _logger.debug(e, e);
                    statusCode = MrcpResponse.STATUS_OPERATION_FAILED;
                } catch (IOException e) {
                    _logger.debug(e, e);
                    statusCode = MrcpResponse.STATUS_OPERATION_FAILED;
                }
            } else if (contentType.equalsIgnoreCase("text/uri-list")) {
                String text = request.getContent();
                String[] uris = text.split("\\r");
                _logger.debug(text);
                //TODO: Handle multiple URI's in a URI list
                //should there be just one listener for the last prompt?  for now limiting to one.
                if (uris.length > 1) {
                   _logger.warn("Multiple URIs not supported yet.  Just playing the first URI.");
                }
                //for (int i=0; i<uris.length;i++) {
                for (int i=0; i<1;i++) {
                    try {

                        URL url = new URL(uris[i]);
                        URLConnection uc = url.openConnection();
                        _logger.debug(uris[i]+"  "+uc.getContentType());
                        
                        if (uc.getContentType().equals("text/plain")) {                        
                           BufferedReader in = new BufferedReader(
                                                new InputStreamReader(
                                                uc.getInputStream()));
                           
                           //TODO: Make this more efficient
                           String inputLine;
                           String promptString = new String();
                           while ((inputLine = in.readLine()) != null) {
                               promptString = promptString +inputLine;
                           }
                           in.close();
                           
                           try {
                              File promptFile = generatePrompt(promptString);
                              int state = _rtpChannel.queuePrompt(promptFile, new Listener(session));
                              requestState = (state == RTPSpeechSynthChannel.IDLE) ? MrcpRequestState.IN_PROGRESS : MrcpRequestState.PENDING;
                              statusCode = MrcpResponse.STATUS_SUCCESS;
                           } catch (RuntimeException e) {
                               _logger.debug(e, e);
                               statusCode = MrcpResponse.STATUS_SERVER_INTERNAL_ERROR;
                               break;
                           } catch (InvalidSessionAddressException e) {
                               _logger.debug(e, e);
                               statusCode = MrcpResponse.STATUS_OPERATION_FAILED;
                               break;
                           } catch (IOException e) {
                               _logger.debug(e, e);
                               statusCode = MrcpResponse.STATUS_OPERATION_FAILED;
                               break;
                           }
                           
                        } else if ((uc.getContentType().equals("audio/x-wav")) ||
                                   (uc.getContentType().equals("audio/basic"))){
                            try {
                                File promptFile;
                                //if file protocol url -- no need to copy it to the server else copy it
                                if (url.getProtocol().equals("file")) {
                                   promptFile = new File(url.getFile());
                                } else {
                                   promptFile = copyPrompt(url);
                                }
                                int state = _rtpChannel.queuePrompt(promptFile, new Listener(session));
                                requestState = (state == RTPSpeechSynthChannel.IDLE) ? MrcpRequestState.IN_PROGRESS : MrcpRequestState.PENDING;
                                statusCode = MrcpResponse.STATUS_SUCCESS;
                             } catch (RuntimeException e) {
                                 _logger.debug(e, e);
                                 statusCode = MrcpResponse.STATUS_SERVER_INTERNAL_ERROR;
                                 break;
                             } catch (InvalidSessionAddressException e) {
                                 _logger.debug(e, e);
                                 statusCode = MrcpResponse.STATUS_OPERATION_FAILED;
                                 break;
                             } catch (IOException e) {
                                 _logger.debug(e, e);
                                 statusCode = MrcpResponse.STATUS_OPERATION_FAILED;
                                 break;
                             }
                        } else {
                            _logger.warn("Unsupported content type for in the speak request: "+ uc.getContentType());
                        }
                  
                        
                    } catch (MalformedURLException e) {
                        _logger.debug(e, e);
                        statusCode = MrcpResponse.STATUS_OPERATION_FAILED;
                    } catch (IOException e) {
                        _logger.debug(e, e);
                        statusCode = MrcpResponse.STATUS_OPERATION_FAILED;
                    }
                }
            } else {
                statusCode = MrcpResponse.STATUS_UNSUPPORTED_HEADER_VALUE;
            }
        } else {
            statusCode = MrcpResponse.STATUS_MANDATORY_HEADER_MISSING;
        }

        return session.createResponse(statusCode, requestState);
    }

    /* (non-Javadoc)
     * @see org.mrcp4j.server.provider.SpeechSynthRequestHandler#stop(org.mrcp4j.message.request.StopRequest, org.mrcp4j.server.MrcpSession)
     */
    public synchronized MrcpResponse stop(StopRequest request, MrcpSession session) {
        MrcpRequestState requestState = MrcpRequestState.COMPLETE;
        short statusCode = -1;
        _rtpChannel.stopPlayback();
        statusCode = MrcpResponse.STATUS_SUCCESS;

        //TODO: set Active-Request-Id-List header

        return session.createResponse(statusCode, requestState);
    }

    /* (non-Javadoc)
     * @see org.mrcp4j.server.provider.SpeechSynthRequestHandler#pause(org.mrcp4j.message.request.MrcpRequestFactory.UnimplementedRequest, org.mrcp4j.server.MrcpSession)
     */
    public synchronized MrcpResponse pause(UnimplementedRequest request, MrcpSession session) {
        // TODO Auto-generated method stub
        return null;
    }

    /* (non-Javadoc)
     * @see org.mrcp4j.server.provider.SpeechSynthRequestHandler#resume(org.mrcp4j.message.request.MrcpRequestFactory.UnimplementedRequest, org.mrcp4j.server.MrcpSession)
     */
    public synchronized MrcpResponse resume(UnimplementedRequest request, MrcpSession session) {
        // TODO Auto-generated method stub
        return null;
    }

    /* (non-Javadoc)
     * @see org.mrcp4j.server.provider.SpeechSynthRequestHandler#bargeInOccurred(org.mrcp4j.message.request.MrcpRequestFactory.UnimplementedRequest, org.mrcp4j.server.MrcpSession)
     */
    public synchronized MrcpResponse bargeInOccurred(UnimplementedRequest request, MrcpSession session) {
        MrcpRequestState requestState = MrcpRequestState.COMPLETE;
        short statusCode = -1;
        _rtpChannel.stopPlayback();
        statusCode = MrcpResponse.STATUS_SUCCESS;

        //TODO: set Active-Request-Id-List header

        return session.createResponse(statusCode, requestState);
    }

    /* (non-Javadoc)
     * @see org.mrcp4j.server.provider.SpeechSynthRequestHandler#control(org.mrcp4j.message.request.MrcpRequestFactory.UnimplementedRequest, org.mrcp4j.server.MrcpSession)
     */
    public synchronized MrcpResponse control(UnimplementedRequest request, MrcpSession session) {
        // TODO Auto-generated method stub
        return null;
    }

    /* (non-Javadoc)
     * @see org.mrcp4j.server.provider.SpeechSynthRequestHandler#defineLexicon(org.mrcp4j.message.request.MrcpRequestFactory.UnimplementedRequest, org.mrcp4j.server.MrcpSession)
     */
    public synchronized MrcpResponse defineLexicon(UnimplementedRequest request, MrcpSession session) {
        // TODO Auto-generated method stub
        return null;
    }

    /* (non-Javadoc)
     * @see org.speechforge.cairo.server.MrcpGenericChannel#validateParam(org.mrcp4j.message.header.MrcpHeader)
     */
    @SuppressWarnings("unused")
    @Override
    protected boolean validateParam(MrcpHeader header) throws UnsupportedHeaderException, IllegalValueException {
        throw new UnsupportedHeaderException();
    }

    private File copyPrompt(URL url) throws IOException {
        
        if (_promptDir == null || !_promptDir.isDirectory()) {
            throw new IllegalArgumentException("Directory file specified does not exist or is not a directory: " + _promptDir);
        }

        String promptName = Long.toString(System.currentTimeMillis());
        File promptFile = new File(_promptDir, promptName);
        
        InputStream is = url.openStream();
        FileOutputStream fos = new FileOutputStream(promptFile);
    
        // Transfer bytes from in to out
        byte[] buf = new byte[1024];
        int len;
        while ((len = is.read(buf)) > 0) {
            fos.write(buf, 0, len);
        }
        is.close();
        fos.close();
        return promptFile;
    }
    
    private File generatePrompt(String text) throws IllegalArgumentException, IOException {
	_logger.info(">>>>>>>>>>>>>>>>>>>_speechSynthesizer:"+ _speechSynthesizer + "<<<<<<<<<<<<<<<<<<<<<<<<<<<<");
	if(_speechSynthesizer.equals("Festival")) return generateFestivalPrompt(text);
	else return generateMaryPrompt(text);
	
    }

    private File generateFestivalPrompt(String text) throws IllegalArgumentException, IOException {
        FestivalPromptGenerator promptGenerator = null;
	_logger.info(">>>>>>>>>>>>>>>>>>> in festivalPrompt<<<<<<<<<<<<<<<<<<<<<<<<<<<<");
        // borrow prompt generator
        try {
            promptGenerator = (FestivalPromptGenerator) _promptGeneratorPool.borrowObject();
        } catch (Exception e) {
            // TODO Auto-generated catch block
            throw new RuntimeException(e);
        }

        // generate prompt
        File promptFile = promptGenerator.generatePrompt(text, _promptDir);

        // return prompt generator
        try {
            _promptGeneratorPool.returnObject(promptGenerator);
        } catch (Exception e) {
            // TODO Auto-generated catch block
            _logger.debug(e, e);
        }

        return promptFile;
    }

    private File generateMaryPrompt(String text) throws IllegalArgumentException, IOException {
        MaryPromptGenerator promptGenerator = null;

        // borrow prompt generator
        try {
            promptGenerator = (MaryPromptGenerator) _promptGeneratorPool.borrowObject();
        } catch (Exception e) {
            // TODO Auto-generated catch block
            throw new RuntimeException(e);
        }

        // generate prompt
        File promptFile = promptGenerator.generatePrompt(text, _promptDir);

        // return prompt generator
        try {
            _promptGeneratorPool.returnObject(promptGenerator);
        } catch (Exception e) {
            // TODO Auto-generated catch block
            _logger.debug(e, e);
        }

        return promptFile;
    }

    private class Listener implements PromptPlayListener {

        private MrcpSession _session;

        /**
         * TODOC
         * @param session
         */
        public Listener(MrcpSession session) {
            _session = session;
        }

        /* (non-Javadoc)
         * @see org.speechforge.cairo.server.tts.PromptPlayListener#playCompleted()
         */
        public void playCompleted() {
            try {
                //TODO: check state before posting event
                MrcpEvent event = _session.createEvent(
                        MrcpEventName.SPEAK_COMPLETE,
                        MrcpRequestState.COMPLETE
                );
                CompletionCause completionCause = new CompletionCause((short) 0, "normal");
                MrcpHeader completionCauseHeader = MrcpHeaderName.COMPLETION_CAUSE.constructHeader(completionCause);
                event.addHeader(completionCauseHeader);
                _session.postEvent(event);
            } catch (IllegalStateException e) {
                // TODO Auto-generated catch block
                _logger.debug(e, e);
            } catch (TimeoutException e) {
                // TODO Auto-generated catch block
                _logger.debug(e, e);
            }
        }

        /* (non-Javadoc)
         * @see org.speechforge.cairo.server.tts.PromptPlayListener#playInterrupted()
         */
        public void playInterrupted() {
            // ignore
        }

        /* (non-Javadoc)
         * @see org.speechforge.cairo.server.tts.PromptPlayListener#playFailed(java.lang.Exception)
         */
        public void playFailed(Exception cause) {
            try {
                //TODO: check state before posting event
                MrcpEvent event = _session.createEvent(
                        MrcpEventName.SPEAK_COMPLETE,
                        MrcpRequestState.COMPLETE
                );
                CompletionCause completionCause = new CompletionCause((short) 4, "error");
                MrcpHeader completionCauseHeader = MrcpHeaderName.COMPLETION_CAUSE.constructHeader(completionCause);
                MrcpHeader completionReasonHeader = MrcpHeaderName.COMPLETION_REASON.constructHeader(cause.getMessage());
                event.addHeader(completionCauseHeader);
                event.addHeader(completionReasonHeader);
                _session.postEvent(event);
            } catch (IllegalStateException e) {
                // TODO Auto-generated catch block
                _logger.debug(e, e);
            } catch (TimeoutException e) {
                // TODO Auto-generated catch block
                _logger.debug(e, e);
            }
        }
        
    }
}
