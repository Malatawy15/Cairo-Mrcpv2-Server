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
package org.speechforge.cairo.server.recorder;

import org.speechforge.cairo.exception.ResourceUnavailableException;
import org.speechforge.cairo.exception.UnsupportedHeaderException;
import org.speechforge.cairo.server.MrcpGenericChannel;
import org.speechforge.cairo.server.recog.RecognitionResult;
import org.speechforge.cairo.rtp.server.RTPStreamReplicator;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.EnumSet;
import java.util.concurrent.TimeoutException;

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
import org.mrcp4j.message.request.RecordRequest;
import org.mrcp4j.message.request.StartInputTimersRequest;
import org.mrcp4j.message.request.StopRequest;
import org.mrcp4j.server.MrcpServerSocket;
import org.mrcp4j.server.MrcpSession;
import org.mrcp4j.server.provider.RecorderRequestHandler;

/**
 * Handles MRCPv2 recorder requests by delegating to a dedicated {@link org.speechforge.cairo.server.recorder.RTPRecorderChannelImpl}.
 *
 * @author Spencer Lord {@literal <}<a href="mailto:salord@users.sourceforge.net">salord@users.sourceforge.net</a>{@literal >}
 */
public class MrcpRecorderChannel extends MrcpGenericChannel implements RecorderRequestHandler {

	private static final Long LONG_MINUS_ONE = new Long(-1);


    private static Logger _logger = Logger.getLogger(MrcpRecorderChannel.class);

    private RTPRecorderChannel _recorderChannel;

    public static Long DEFAULT_NO_INPUT_TIMEOUT = new Long(10000);
    public static Boolean DEFAULT_START_INPUT_TIMERS = Boolean.TRUE;

    static short IDLE = 0;
    static short RECORDING = 1;
    static short RECORDED = 2;
    
    /*volatile*/ short _state = IDLE;

    public MrcpRecorderChannel(RTPRecorderChannel recorderChannel) {
        _recorderChannel = recorderChannel;
    }

    /* (non-Javadoc)
     * @see org.mrcp4j.server.provider.RecorderRequestHandler#record(org.mrcp4j.message.request.RecordRequest, org.mrcp4j.server.MrcpSession)
     */
    public synchronized MrcpResponse record(RecordRequest request, MrcpSession session) {
        MrcpRequestState requestState = MrcpRequestState.COMPLETE;
        short statusCode = -1;
        if (_state == RECORDING) {
            // TODO: cancel or queue request instead (depending upon value of 'cancel-if-queue' header)
            statusCode = MrcpResponse.STATUS_METHOD_NOT_VALID_IN_STATE;
        } else {
            try {
                Boolean startInputTimers = (Boolean) getParam(MrcpHeaderName.START_INPUT_TIMERS, request, DEFAULT_START_INPUT_TIMERS);
                Long noInputTimeout = (startInputTimers.booleanValue()) ?
                        (Long) getParam(MrcpHeaderName.NO_INPUT_TIMEOUT, request, DEFAULT_NO_INPUT_TIMEOUT) : LONG_MINUS_ONE;
            	
            	//todo: What other headers do i need?  timeouts, sensitivities, ...
            	MrcpHeader recordUri = request.getHeader(MrcpHeaderName.RECORD_URI);
            	String uri = null;
            	if (recordUri != null) {
                	uri = recordUri.getValueString();
            	}

            	_logger.info("Starting recording with uri: "+ uri);
                _recorderChannel.startRecording(new Listener(session),  noInputTimeout.longValue(), uri);
                statusCode = MrcpResponse.STATUS_SUCCESS;
                requestState = MrcpRequestState.IN_PROGRESS;
                _state = RECORDING;
            } catch (IllegalStateException e){
                _logger.debug(e, e);
                statusCode = MrcpResponse.STATUS_METHOD_NOT_VALID_IN_STATE;
            } catch (IOException e){
                _logger.debug(e, e);
                statusCode = MrcpResponse.STATUS_SERVER_INTERNAL_ERROR;
            } catch (IllegalValueException e) {
	            // TODO Auto-generated catch block
	            e.printStackTrace();
            } catch (ResourceUnavailableException e) {
	            // TODO Auto-generated catch block
	            e.printStackTrace();
            }
        }
        // TODO: cache event acceptor if request is not complete
        return session.createResponse(statusCode, requestState);
    }

    /* (non-Javadoc)
     * @see org.mrcp4j.server.provider.RecorderRequestHandler#stop(org.mrcp4j.message.request.StopRequest, org.mrcp4j.server.MrcpSession)
     */
    public synchronized MrcpResponse stop(StopRequest request, MrcpSession session) {
        MrcpRequestState requestState = MrcpRequestState.COMPLETE;
        short statusCode = -1;
        if (_state == RECORDING) {
            try {
                _recorderChannel.stopRecording();
                statusCode = MrcpResponse.STATUS_SUCCESS;
                //requestState = MrcpRequestState.IN_PROGRESS;
                _state = RECORDED;
            } catch (IllegalStateException e){
                statusCode = MrcpResponse.STATUS_METHOD_NOT_VALID_IN_STATE;
            }
        } else {
            statusCode = MrcpResponse.STATUS_METHOD_NOT_VALID_IN_STATE;
        }
        return session.createResponse(statusCode, requestState);
    }

    /* (non-Javadoc)
     * @see org.mrcp4j.server.provider.RecorderRequestHandler#startInputTimers(org.mrcp4j.message.request.StartInputTimersRequest, org.mrcp4j.server.MrcpSession)
     */

    public synchronized MrcpResponse startInputTimers(StartInputTimersRequest request, MrcpSession session) {
        MrcpResponse response = null;

        try {
            Long noInputTimeout = (Long) getParam(MrcpHeaderName.NO_INPUT_TIMEOUT, request, DEFAULT_NO_INPUT_TIMEOUT);
            _recorderChannel.startInputTimers(noInputTimeout.longValue());
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
        if (!headerName.isApplicableTo(MrcpResourceType.RECORDER)) {
            throw new UnsupportedHeaderException();
        }
        if (MrcpHeaderName.ENROLLMENT_HEADER_NAMES.contains(headerName)) {
            throw new UnsupportedHeaderException();
        }
        return FULLY_SUPPORTED_HEADERS.contains(header);
    }
    
    
    private class Listener implements RecorderListener {

        private MrcpSession _session;

        /**
         * TODOC
         * @param session
         */
        public Listener(MrcpSession session) {
            _session = session;
        }


        /* (non-Javadoc)
         * @see org.speechforge.cairo.server.recog.RecogListener#speechStarted()
         */
        public void speechStarted() {
        	_logger.info("speech started event");
            short state;
            synchronized (MrcpRecorderChannel.this) {
                state = _state;
            }
        	_logger.debug("and past the synchronized block");
            if (state == RECORDING) try {
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
        	_logger.info("no input timeout event...");
            short state;
            synchronized (MrcpRecorderChannel.this) {
                state = _state;
                _state = IDLE;
            }
            if (state == RECORDING) try {
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

		public void recordingComplete(String uri) {
	        // TODO Auto-generated method stub
        	_logger.info("speech complete event...");
            synchronized (MrcpRecorderChannel.this) {
                _state = RECORDED;
            }
        	_logger.debug("...and past the synchronized block");
            try {
                MrcpEvent event = _session.createEvent(
                        MrcpEventName.RECORD_COMPLETE,
                        MrcpRequestState.COMPLETE
                );

                CompletionCause completionCause = new CompletionCause((short) 0, "success");
                event.addHeader(MrcpHeaderName.COMPLETION_CAUSE.constructHeader(completionCause));
       
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
    

    public static void main(String[] args) throws Exception {
        // We need three parameters to receive and record RTP transmissions
        // For example,
        //   java MrcpRecorderChannel "C:\\work\\cvs\\onomatopia\\cairo\\output\\prompts" 32416 42050

        String channelID = "32AECB23433801@recorder";
        
        if (args.length < 3) {
            printUsage();
        }

        int mrcpPort = -1;
        try {
            mrcpPort = Integer.parseInt(args[1]);
        } catch (Exception e){
            _logger.debug(e, e);
        }
        if (mrcpPort < 0) {
            printUsage();
        }

        int rtpPort = -1;
        try {
            rtpPort = Integer.parseInt(args[2]);
        } catch (Exception e){
            _logger.debug(e, e);
        }
        if (rtpPort < 0) {
            printUsage();
        }

        File dir = new File(args[0]);

        _logger.info("Starting up RTPStreamReplicator...");
        RTPStreamReplicator replicator = new RTPStreamReplicator(rtpPort);

        _logger.info("Starting up MrcpServerSocket...");
        MrcpServerSocket serverSocket = new MrcpServerSocket(mrcpPort);
        RTPRecorderChannelImpl recorder = new RTPRecorderChannelImpl(channelID, dir, replicator);
        serverSocket.openChannel(channelID, new MrcpRecorderChannel(recorder));

        _logger.info("MRCP recorder resource listening on port " + mrcpPort);

        _logger.info("Hit <enter> to shutdown...");
        BufferedReader consoleReader = new BufferedReader(new InputStreamReader(System.in));
        String cmd = consoleReader.readLine();
        Thread.sleep(90000);
        _logger.info("Shutting down...");
        replicator.shutdown();
    }

    static void printUsage() {
        System.err.println("Usage: MrcpRecorderChannel <recordDir> <mrcpPort> <rtpPort>");
        System.err.println("     <recordDir>: directory to place recordings of RTP transmissions");
        System.err.println("     <mrcpPort>: port to listen for MRCP messages");
        System.err.println("     <rtpPort>: port to listen for RTP transmissions");
        System.exit(0);
    }

}