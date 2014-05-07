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
package org.speechforge.cairo.server.recog;

import org.speechforge.cairo.exception.UnsupportedHeaderException;
import org.speechforge.cairo.server.MrcpGenericChannel;
import org.speechforge.cairo.exception.ResourceUnavailableException;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.EnumSet;
import java.util.concurrent.TimeoutException;

import javax.speech.recognition.GrammarException;

import org.apache.log4j.Logger;
import org.mrcp4j.MrcpEventName;
import org.mrcp4j.MrcpRequestState;
import org.mrcp4j.MrcpResourceType;
import org.mrcp4j.message.MrcpEvent;
import org.mrcp4j.message.MrcpResponse;
import org.mrcp4j.message.header.CompletionCause;
import org.mrcp4j.message.header.IllegalValueException;
import org.mrcp4j.message.header.MrcpHeader;
import org.mrcp4j.message.header.MrcpHeaderName;
import org.mrcp4j.message.request.StartInputTimersRequest;
import org.mrcp4j.message.request.StopRequest;
import org.mrcp4j.message.request.MrcpRequestFactory.UnimplementedRequest;
import org.mrcp4j.server.MrcpSession;
import org.mrcp4j.server.provider.RecogOnlyRequestHandler;

/**
 * Handles MRCPv2 recognition requests by delegating to a dedicated {@link org.speechforge.cairo.server.recog.RTPRecogChannel}.
 *
 * @author Niels Godfredsen {@literal <}<a href="mailto:ngodfredsen@users.sourceforge.net">ngodfredsen@users.sourceforge.net</a>{@literal >}
 */
public class MrcpRecogChannel extends MrcpGenericChannel implements RecogOnlyRequestHandler {

    private static final Long LONG_MINUS_ONE = new Long(-1);

    static Logger _logger = Logger.getLogger(MrcpRecogChannel.class);

    public static Long DEFAULT_NO_INPUT_TIMEOUT = new Long(10000);
    public static Boolean DEFAULT_START_INPUT_TIMERS = Boolean.TRUE;

    static short IDLE = 0;
    static short RECOGNIZING = 1;
    static short RECOGNIZED = 2;

    private RTPRecogChannel _rtpChannel;
    /*volatile*/ short _state = IDLE;
    //private String _channelID;
    private GrammarManager _grammarManager;

    /**
     * TODOC
     * @param channelID 
     * @param rtpChannel 
     * @param baseGrammarDir 
     */
    public MrcpRecogChannel(String channelID, RTPRecogChannel rtpChannel, File baseGrammarDir) {
        //_channelID = channelID;
        _rtpChannel = rtpChannel;
        _grammarManager = new GrammarManager(channelID, baseGrammarDir);
    }

    /* (non-Javadoc)
     * @see org.mrcp4j.server.provider.RecogOnlyRequestHandler#defineGrammar(org.mrcp4j.message.request.MrcpRequestFactory.UnimplementedRequest, org.mrcp4j.server.MrcpSession)
     */
    public synchronized MrcpResponse defineGrammar(UnimplementedRequest request, MrcpSession session) {
        // TODO Auto-generated method stub
        return null;
    }

    /* (non-Javadoc)
     * @see org.mrcp4j.server.provider.RecogOnlyRequestHandler#recognize(org.mrcp4j.message.request.MrcpRequestFactory.UnimplementedRequest, org.mrcp4j.server.MrcpSession)
     */
    public synchronized MrcpResponse recognize(UnimplementedRequest request, MrcpSession session) {

        MrcpRequestState requestState = MrcpRequestState.COMPLETE;
        MrcpHeader completionCauseHeader = null;
        MrcpHeader completionReasonHeader = null;
        short statusCode = -1;

        _logger.debug(request.toString());
        if (_state == RECOGNIZING) {
            // TODO: cancel or queue request instead (depending upon value of 'cancel-if-queue' header)
            statusCode = MrcpResponse.STATUS_METHOD_NOT_VALID_IN_STATE;
        } else {
            GrammarLocation grammarLocation = null;
            if (request.hasContent()) {
                String contentType = request.getContentType();
                if (contentType.equalsIgnoreCase("application/jsgf")) {
                	_logger.debug("processing jsgf");
                    // save grammar to file
                    MrcpHeader contentIdHeader = request.getHeader(MrcpHeaderName.CONTENT_ID);
                    String grammarID = (contentIdHeader == null) ? null : contentIdHeader.getValueString();
                    try {
                        grammarLocation = _grammarManager.saveGrammar(grammarID, request.getContent());
                    } catch (IOException e) {
                        _logger.debug(e, e);
                        statusCode = MrcpResponse.STATUS_SERVER_INTERNAL_ERROR;
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
                            
                            //TODO:  Should replace this check content type and not always assume it is JSGF 
                            //       (But using the URI-LIST as a work around for large grammars not supported in mrcp4j 
                            //        and in some cases the uri does not hav a content type (file uri's)).
                            //if ((uc.getContentType().equals("text/plain")) ||                //TODO: Remove this should not assume text/plain is jsgf
                            //    (uc.getContentType().equals("application/jsgf"))){
                               BufferedReader in = new BufferedReader(
                                                    new InputStreamReader(
                                                    uc.getInputStream()));
                               
                               //TODO: Make this more efficient
                               String inputLine;
                               String grammarText = new String();
                               while ((inputLine = in.readLine()) != null) {
                                   grammarText = grammarText +inputLine+"\n";
                               }
                               in.close();
                              _logger.debug(grammarText);
                               
                               // save grammar to file
                               MrcpHeader contentIdHeader = request.getHeader(MrcpHeaderName.CONTENT_ID);
                               String grammarID = (contentIdHeader == null) ? null : contentIdHeader.getValueString();
                               try {
                                   grammarLocation = _grammarManager.saveGrammar(grammarID, grammarText);
                               } catch (IOException e) {
                                   _logger.debug(e, e);
                                   statusCode = MrcpResponse.STATUS_SERVER_INTERNAL_ERROR;
                               }
                           
                            //} else {
                            //    _logger.warn("Unsupported content type for in the recognize request: "+ uc.getContentType());
                            //}
                      
                            
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
            }
        	_logger.debug("Before doing the work, status code is "+statusCode);
            if (statusCode < 0) { // status not yet set
                try {
                    Boolean startInputTimers = (Boolean) getParam(MrcpHeaderName.START_INPUT_TIMERS, request, DEFAULT_START_INPUT_TIMERS);
                    Long noInputTimeout = (startInputTimers.booleanValue()) ?
                            (Long) getParam(MrcpHeaderName.NO_INPUT_TIMEOUT, request, DEFAULT_NO_INPUT_TIMEOUT) : LONG_MINUS_ONE;
                    //TODO: get the hotword mode from mrcp message        
                    boolean hotword = false;
                    String hw = (String) getParam(MrcpHeaderName.RECOGNITION_MODE, request, "normal");
                    if (hw.equals("hotword")) {
                       hotword = true;
                    }
                    _logger.debug("Recognition Mode is : "+hw+ "  So hotword flag is now: "+hotword);
                    _logger.debug("No input timeout value is "+noInputTimeout.longValue());
                    _rtpChannel.recognize(new Listener(session), grammarLocation, noInputTimeout.longValue(), hotword);
                    statusCode = MrcpResponse.STATUS_SUCCESS;
                    requestState = MrcpRequestState.IN_PROGRESS;
                    _state = RECOGNIZING;
                } catch (IllegalStateException e) {
                    _logger.debug(e, e);
                    statusCode = MrcpResponse.STATUS_METHOD_NOT_VALID_IN_STATE;
                    // TODO: cancel or queue request instead (depending upon value of 'cancel-if-queue' header)
                } catch (IOException e) {
                    _logger.debug(e, e);
                    statusCode = MrcpResponse.STATUS_SERVER_INTERNAL_ERROR;
                    CompletionCause completionCause = new CompletionCause((short) 6, "recognizer-error");
                    completionCauseHeader = MrcpHeaderName.COMPLETION_CAUSE.constructHeader(completionCause);
                    completionReasonHeader = MrcpHeaderName.COMPLETION_REASON.constructHeader(e.getMessage());
                } catch (ResourceUnavailableException e) {
                    _logger.debug(e, e);
                    statusCode = MrcpResponse.STATUS_SERVER_INTERNAL_ERROR;
                    CompletionCause completionCause = new CompletionCause((short) 6, "recognizer-error");
                    completionCauseHeader = MrcpHeaderName.COMPLETION_CAUSE.constructHeader(completionCause);
                    completionReasonHeader = MrcpHeaderName.COMPLETION_REASON.constructHeader(e.getMessage());
                } catch (GrammarException e) {
                    _logger.debug(e, e);
                    statusCode = MrcpResponse.STATUS_OPERATION_FAILED;
                    CompletionCause completionCause = new CompletionCause((short) 4, "grammar-load-failure");
                    completionCauseHeader = MrcpHeaderName.COMPLETION_CAUSE.constructHeader(completionCause);
                    completionReasonHeader = MrcpHeaderName.COMPLETION_REASON.constructHeader(e.getMessage());
                } catch (IllegalValueException e) {
                    _logger.debug(e, e);
                    statusCode = MrcpResponse.STATUS_ILLEGAL_VALUE_FOR_HEADER;
                    // TODO: add completion cause header
                    // TODO: add bad value headers
                }
            }
        }

        MrcpResponse response = session.createResponse(statusCode, requestState);
        response.addHeader(completionCauseHeader);
        response.addHeader(completionReasonHeader);
        return response;
    }

    /* (non-Javadoc)
     * @see org.mrcp4j.server.provider.RecogOnlyRequestHandler#interpret(org.mrcp4j.message.request.MrcpRequestFactory.UnimplementedRequest, org.mrcp4j.server.MrcpSession)
     */
    public synchronized MrcpResponse interpret(UnimplementedRequest request, MrcpSession session) {
        // TODO Auto-generated method stub
        return null;
    }

    /* (non-Javadoc)
     * @see org.mrcp4j.server.provider.RecogOnlyRequestHandler#getResult(org.mrcp4j.message.request.MrcpRequestFactory.UnimplementedRequest, org.mrcp4j.server.MrcpSession)
     */
    public synchronized MrcpResponse getResult(UnimplementedRequest request, MrcpSession session) {
        // TODO Auto-generated method stub
        return null;
    }

    /* (non-Javadoc)
     * @see org.mrcp4j.server.provider.RecogOnlyRequestHandler#startInputTimers(org.mrcp4j.message.request.StartInputTimersRequest, org.mrcp4j.server.MrcpSession)
     */
    public synchronized MrcpResponse startInputTimers(StartInputTimersRequest request, MrcpSession session) {
        MrcpResponse response = null;

        try {
            Long noInputTimeout = (Long) getParam(MrcpHeaderName.NO_INPUT_TIMEOUT, request, DEFAULT_NO_INPUT_TIMEOUT);
            _rtpChannel.startInputTimers(noInputTimeout.longValue());
            response = session.createResponse(MrcpResponse.STATUS_SUCCESS, MrcpRequestState.COMPLETE);
        } catch (IllegalStateException e) {
            _logger.debug(e, e);
            response = session.createResponse(MrcpResponse.STATUS_METHOD_NOT_VALID_IN_STATE, MrcpRequestState.COMPLETE);
        } catch (IllegalValueException e) {
            _logger.debug(e, e);
            response = session.createResponse(MrcpResponse.STATUS_ILLEGAL_VALUE_FOR_HEADER, MrcpRequestState.COMPLETE);
            response.addHeader(request.getHeader(MrcpHeaderName.NO_INPUT_TIMEOUT));  // TODO: get header name from exception?
        }

        return response;
    }

    /* (non-Javadoc)
     * @see org.mrcp4j.server.provider.RecogOnlyRequestHandler#stop(org.mrcp4j.message.request.StopRequest, org.mrcp4j.server.MrcpSession)
     */
    public synchronized MrcpResponse stop(StopRequest request, MrcpSession session) {
        
        _logger.debug("Stop recognition called, mrcp channel state: "+_state+" rtp channel state: "+_rtpChannel._state);



        
        if (_state == IDLE) {
            //Nothing to cancel
            _logger.warn("Stopping recognition, but nothing to cancel.  Mrcp channel state is IDLE");
        } else if (_state == RECOGNIZED) {
            //Nothing to cancel
            _logger.warn("Stopping recognition, but nothing to cancel.  Mrcp channel state is RECOGNIZED");            
        } else if (_state == RECOGNIZING) {
            _logger.info("Stopping recognition.  Mrcp channel state is RECOGNIZING and rtp channel state is is "+_rtpChannel._state);
            if (_rtpChannel._state == RTPRecogChannel.WAITING_FOR_SPEECH) {
                if (_rtpChannel._noInputTimeoutTask != null) {
                   _rtpChannel._noInputTimeoutTask.cancel();
                   _logger.info("Stopping recognition, canceled no input timer");
                }
                _rtpChannel.closeProcessor();
                //TODO: Add  active-request-id-list header containing the request-id of the RECOGNIZE request that was terminated.
            } else if (_rtpChannel._state == RTPRecogChannel.SPEECH_IN_PROGRESS) {
                _rtpChannel.closeProcessor();
                //TODO: Add  active-request-id-list header containing the request-id of the RECOGNIZE request that was terminated.
            } else if (_rtpChannel._state == RTPRecogChannel.COMPLETE) {
                _logger.warn("Stopping recognition, but nothing to cancel.  Mrcp channel state is recognizing, but rtp chan state is complete");
            } else {
                _logger.warn("Stopping recognition, but invalid rtp channel state: "+_rtpChannel._state);
            }
            
        } else {
            _logger.warn("Stopping recognition, but invalid mrcp channel state: "+_state);
        }
        
        //change the state to IDLE
        _state = IDLE;
        
        MrcpResponse response = null;
        response = session.createResponse(MrcpResponse.STATUS_SUCCESS, MrcpRequestState.COMPLETE);

        return response;
    }
    
    private class Listener implements RecogListener {

        private MrcpSession _session;

        /**
         * TODOC
         * @param session
         */
        public Listener(MrcpSession session) {
            _session = session;
        }

        /* (non-Javadoc)
         * @see org.speechforge.cairo.server.recog.RecogListener#recognitionComplete()
         */
        public void recognitionComplete(RecognitionResult result) {
        	_logger.debug("speech complete event...");
            synchronized (MrcpRecogChannel.this) {
                _state = RECOGNIZED;
            }
        	_logger.debug("...and past the synchronized block");
            try {
                MrcpEvent event = _session.createEvent(
                        MrcpEventName.RECOGNITION_COMPLETE,
                        MrcpRequestState.COMPLETE
                );
                String content = result.toString();
                if (content == null || content.trim().length() < 1) {
                    CompletionCause completionCause = new CompletionCause((short) 1, "no-match");
                    event.addHeader(MrcpHeaderName.COMPLETION_CAUSE.constructHeader(completionCause));
                } else {
                    CompletionCause completionCause = new CompletionCause((short) 0, "success");
                    event.addHeader(MrcpHeaderName.COMPLETION_CAUSE.constructHeader(completionCause));
                    event.setContent("text/plain", null, content);
                }
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
         * @see org.speechforge.cairo.server.recog.RecogListener#speechStarted()
         */
        public void speechStarted() {
        	_logger.debug("speech started event");
            short state;
            synchronized (MrcpRecogChannel.this) {
                state = _state;
            }
        	_logger.debug("and past the synchronized block");
            if (state == RECOGNIZING) try {
                MrcpEvent event = _session.createEvent(
                        MrcpEventName.START_OF_INPUT,
                        MrcpRequestState.IN_PROGRESS
                );
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
         * @see org.speechforge.cairo.server.recog.RecogListener#noInputTimeout()
         */
        public void noInputTimeout() {
            short state;
            synchronized (MrcpRecogChannel.this) {
                state = _state;
                _state = IDLE;
            }
            if (state == RECOGNIZING) try {
                MrcpEvent event = _session.createEvent(
                        MrcpEventName.RECOGNITION_COMPLETE,
                        MrcpRequestState.COMPLETE
                );
                CompletionCause completionCause = new CompletionCause((short) 2, "no-input-timeout");
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
        
    }

    // TODO: define which headers are supported fully
    private static EnumSet FULLY_SUPPORTED_HEADERS  = EnumSet.of(MrcpHeaderName.START_INPUT_TIMERS);

    /* (non-Javadoc)
     * @see org.speechforge.cairo.server.MrcpGenericChannel#validateParam(org.mrcp4j.message.header.MrcpHeader)
     */
    @Override
    protected boolean validateParam(MrcpHeader header) throws UnsupportedHeaderException, IllegalValueException {
        header.getValueObject();
        MrcpHeaderName headerName = header.getHeaderName();
        if (headerName == null) {
            throw new UnsupportedHeaderException();
        }
        if (!headerName.isApplicableTo(MrcpResourceType.SPEECHRECOG)) {
            throw new UnsupportedHeaderException();
        }
        if (MrcpHeaderName.ENROLLMENT_HEADER_NAMES.contains(headerName)) {
            throw new UnsupportedHeaderException();
        }
        return FULLY_SUPPORTED_HEADERS.contains(header);
    }

}
