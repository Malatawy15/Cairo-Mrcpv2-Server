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

import static org.speechforge.cairo.jmf.JMFUtil.CONTENT_DESCRIPTOR_RAW;

import org.speechforge.cairo.exception.ResourceUnavailableException;
import org.speechforge.cairo.rtp.server.RTPStreamReplicator.ProcessorReplicatorPair;
import org.speechforge.cairo.rtp.server.sphinx.SourceAudioFormat;
import org.speechforge.cairo.server.recog.sphinx.SphinxRecEngine;
import org.speechforge.cairo.rtp.server.RTPStreamReplicator;
import org.speechforge.cairo.jmf.ProcessorStarter;

import java.io.IOException;
import java.util.Timer;
import java.util.TimerTask;

import javax.media.Processor;
import javax.media.protocol.PushBufferDataSource;
import javax.speech.recognition.GrammarException;

import org.apache.commons.lang.Validate;
import org.apache.commons.pool.ObjectPool;
import org.apache.log4j.Logger;

/**
 * Handles recognition requests against an incoming RTP audio stream.
 *
 * @author Niels Godfredsen {@literal <}<a href="mailto:ngodfredsen@users.sourceforge.net">ngodfredsen@users.sourceforge.net</a>{@literal >}
 */
public class RTPRecogChannel {

    public static final short WAITING_FOR_SPEECH = 0;
    public static final short SPEECH_IN_PROGRESS = 1;
    public static final short COMPLETE = 2;

    static Logger _logger = Logger.getLogger(RTPRecogChannel.class);

    private /*static*/ Timer _timer = new Timer();

    private ObjectPool _recEnginePool;
    private RTPStreamReplicator _replicator;

    RecogListener _recogListener;
    SphinxRecEngine _recEngine = null;
    TimerTask _noInputTimeoutTask;

    private Processor _processor;

    volatile short _state = COMPLETE;
	private ProcessorReplicatorPair _pair;

    /**
     * TODOC
     * @param recEnginePool 
     * @param replicator 
     */
    public RTPRecogChannel(ObjectPool recEnginePool, RTPStreamReplicator replicator) {
        Validate.notNull(recEnginePool, "Null recEnginePool!");
        Validate.notNull(replicator, "Null replicator!");

        _recEnginePool = recEnginePool;
        _replicator = replicator;
    }

    /**
     * TODOC
     * @param listener
     * @param grammarLocation
     * @param noInputTimeout
     * @throws IllegalStateException
     * @throws IOException
     * @throws ResourceUnavailableException
     * @throws GrammarException
     */
    public synchronized void recognize(RecogListener listener, GrammarLocation grammarLocation, long noInputTimeout, boolean hotword)
      throws IllegalStateException, IOException, ResourceUnavailableException, GrammarException {

        if (_processor != null) {
            throw new IllegalStateException("Recognition already in progress!");
            // TODO: cancel or queue request instead (depending upon value of 'cancel-if-queue' header)
        }
        _logger.debug("OK, processor was null");


        _pair  = _replicator.createRealizedProcessor(CONTENT_DESCRIPTOR_RAW, 10000,SourceAudioFormat.PREFERRED_MEDIA_FORMATS); // TODO: specify audio format
        _processor = _pair.getProc();
        _logger.debug("OK, created new realized processor");
        
        PushBufferDataSource dataSource = (PushBufferDataSource) _processor.getDataOutput();
        _logger.debug("OK, created new datasource "+dataSource);
        
        if (dataSource == null) {
            throw new IOException("Processor.getDataOutput() returned null!");
        }

        try {
            _logger.debug("Borrowing recognition engine from object pool...");
            _recEngine = (SphinxRecEngine) _recEnginePool.borrowObject();
        } catch (Exception e) {
        	e.printStackTrace();
            _logger.debug(e, e);
            closeProcessor();
            throw new ResourceUnavailableException("All rec engines are in use!", e);
            // TODO: wait for availability...?
        }

        _logger.debug("Recognize command with a listener: "+listener);
        _recogListener = new Listener(listener);
        
        try {
            _logger.debug("Loading grammar...");
            _recEngine.loadJSGF(grammarLocation);
            _recEngine.setHotword(hotword);
            _logger.debug("Starting recognition...");
            _state = WAITING_FOR_SPEECH;
            _recEngine.startRecognition(dataSource, _recogListener);

            _processor.addControllerListener(new ProcessorStarter());
            _processor.start();

            _recEngine.startRecogThread();

            if (noInputTimeout > 0) {
                startInputTimers(noInputTimeout);
            }

        } catch (GrammarException e) {
            closeProcessor();
            throw e;
        } catch (IOException e) {
            closeProcessor();
            throw e;
        }
    }

    /**
     * Starts the input timers which trigger no-input-timeout if speech has not started after the specified time.
     * @param noInputTimeout the amount of time to wait, in milliseconds, before triggering a no-input-timeout. 
     * @return {@code true} if input timers were started or {@code false} if speech has already started.
     * @throws IllegalStateException if recognition is not in progress or if the input timers have already been started.
     */
    public synchronized boolean startInputTimers(long noInputTimeout) throws IllegalStateException {
        if (noInputTimeout <= 0) {
            throw new IllegalArgumentException("Illegal value for no-input-timeout: " + noInputTimeout);
        }
        if (_processor == null) {
            throw new IllegalStateException("Recognition not in progress!");
        }
        if (_noInputTimeoutTask != null) {
            throw new IllegalStateException("InputTimer already started!");
        }

        boolean startInputTimers = (_state == WAITING_FOR_SPEECH); 
        if (startInputTimers) {
            _noInputTimeoutTask = new NoInputTimeoutTask();
            _timer.schedule(_noInputTimeoutTask, noInputTimeout);
        }

        return startInputTimers;
    }

    public synchronized void closeProcessor() {
        if (_processor != null) {
          _logger.debug("Closing processor...");
            _processor.close();
            _processor = null;
            _replicator.removeReplicant( _pair.getPbds());
        }
        if (_recEngine != null) {
            _logger.debug("Returning recengine to pool...");
            try {
                _recEnginePool.returnObject(_recEngine);
            } catch (Exception e) {
                // TODO Auto-generated catch block
                _logger.debug(e, e);
            }
            _recEngine = null;
        } else {
            _logger.warn("No recengine to return to pool!");
        }
    }

    private class NoInputTimeoutTask extends TimerTask {

        /* (non-Javadoc)
         * @see java.util.TimerTask#run()
         */
        @Override
        public void run() {
            synchronized (RTPRecogChannel.this) {
                _noInputTimeoutTask = null;
                if (_state == WAITING_FOR_SPEECH) {
                    _state = COMPLETE;
                    closeProcessor();
                    if (_recogListener != null) {
                        _recogListener.noInputTimeout();
                    }
                }
            }
        }
        
    }

    private class Listener extends RecogListenerDecorator {

        /**
         * TODOC
         * @param recogListener
         */
        public Listener(RecogListener recogListener) {
            super(recogListener);
        }

        /* (non-Javadoc)
         * @see org.speechforge.cairo.server.recog.RecogListener#speechStarted()
         */
        @Override
        public void speechStarted() {
            _logger.debug("speechStarted()");

            synchronized (RTPRecogChannel.this) {
                if (_state == WAITING_FOR_SPEECH) {
                    _state = SPEECH_IN_PROGRESS;
                }
                if (_noInputTimeoutTask != null) {
                    _noInputTimeoutTask.cancel();
                    _noInputTimeoutTask = null;
                }
            }
            super.speechStarted();
        }

        /* (non-Javadoc)
         * @see org.speechforge.cairo.server.recog.RecogListener#recognitionComplete()
         */
        @Override
        public void recognitionComplete(RecognitionResult result) {
            _logger.debug("recognitionComplete()");

            boolean doit = false;
            synchronized (RTPRecogChannel.this) {
                if (_state == SPEECH_IN_PROGRESS) {
                    _state = COMPLETE;
                    doit = true;
                    closeProcessor();
                }
            }

            if (doit) {
                super.recognitionComplete(result);
            }
        }

//        public void noInputTimeout() {
//            boolean doit = false;
//            synchronized (RTPRecogChannel.this) {
//                doit = _speechStarted;
//                _speechStarted = false;
//            }
//            try {
//                _recEnginePool.returnObject(_recEngine);
//            } catch (Exception e) {
//                // TODO Auto-generated catch block
//                _logger.debug(e, e);
//            }
//            super.noInputTimeout();
//        }

    }
}
