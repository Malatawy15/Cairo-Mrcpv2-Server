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

import static org.speechforge.cairo.jmf.JMFUtil.CONTENT_DESCRIPTOR_RAW;

import org.speechforge.cairo.exception.ResourceUnavailableException;
import org.speechforge.cairo.jmf.ProcessorStarter;
import org.speechforge.cairo.rtp.server.RTPStreamReplicator.ProcessorReplicatorPair;
import org.speechforge.cairo.rtp.server.sphinx.SourceAudioFormat;
import org.speechforge.cairo.rtp.server.RTPStreamReplicator;
import org.speechforge.cairo.server.recorder.sphinx.SphinxRecorder;
import java.io.File;
import java.io.IOException;
import java.util.Timer;
import java.util.TimerTask;
import javax.media.Processor;
import javax.media.protocol.ContentDescriptor;
import javax.media.protocol.FileTypeDescriptor;
import javax.media.protocol.PushBufferDataSource;
import org.apache.commons.lang.Validate;
import org.apache.commons.pool.ObjectPool;
import org.apache.log4j.Logger;

/**
 * Manages recording requests against an RTP audio stream being received.
 *
 * @author Spencer Lord {@literal <}<a href="mailto:salord@users.sourceforge.net">salord@users.sourceforge.net</a>{@literal >}
 */
public class RTPRecorderChannelS4Impl implements RTPRecorderChannel  {

    private static Logger _logger = Logger.getLogger(RTPRecorderChannelS4Impl.class);

    private static final ContentDescriptor CONTENT_DESCRIPTOR_WAVE =
        new FileTypeDescriptor(FileTypeDescriptor.WAVE);
    
    
    public static final short WAITING_FOR_SPEECH = 0;
    public static final short SPEECH_IN_PROGRESS = 1;
    public static final short COMPLETE = 2;
    volatile short _state = COMPLETE;
    
    TimerTask _noInputTimeoutTask;
    private /*static*/ Timer _timer = new Timer();


    private ObjectPool _recorderEnginePool;
    SphinxRecorder _recorderEngine = null;
    private File _recordingDir;
    private RTPStreamReplicator _replicator;

    private Processor _processor;
    private File _destination;
    private ContentDescriptor _cd;
    
    private ObjectPool _recorderPool;
    
    RecorderListener _recorderListener;

	private ProcessorReplicatorPair _pair;

    /**
     * TODOC
     * @param channelID unique id of the recorder channel
     * @param baseRecordingDir base directory to save recorded files to
     * @param replicator 
     * @throws IllegalArgumentException if the File specified is not a directory
     */
    public RTPRecorderChannelS4Impl(ObjectPool recorderPool, File recordingDir, RTPStreamReplicator replicator, ContentDescriptor cd) throws IllegalArgumentException {

    	if (cd == null) {
    		_cd = CONTENT_DESCRIPTOR_WAVE;
    	} else {
    		_cd = cd;
    	}
    		
    	
    	//Validate.isTrue(recordingDir.isDirectory(), "baseRecordingDir parameter was not a directory: ", recordingDir);
        _recordingDir = recordingDir;
        
        Validate.notNull(recorderPool, "Null recorderPool!");
        _recorderPool = recorderPool;

        
        if (!_recordingDir.mkdir()) {
            throw new IllegalArgumentException("Specified directory not valid: " + _recordingDir.getAbsolutePath());
        }

        Validate.notNull(replicator, "Null replicator!");
        _replicator = replicator;
    }


    /* (non-Javadoc)
     * @see org.speechforge.cairo.server.recorder.RTPRe#startRecording(org.speechforge.cairo.server.recorder.RecorderListener, long, java.lang.String)
     */
    public synchronized File startRecording(RecorderListener listener, long noInputTimeout, String uri) throws IOException, IllegalStateException, ResourceUnavailableException {
    	
    	
        if (_processor != null) {
            throw new IllegalStateException("Recording already in progress!");
            // TODO: cancel or queue request instead (depending upon value of 'cancel-if-queue' header)
        }

        _recorderListener = new Listener(listener);

        // TODO: specify audio format
        _pair  = _replicator.createRealizedProcessor(CONTENT_DESCRIPTOR_RAW, 10000,SourceAudioFormat.PREFERRED_MEDIA_FORMATS); // TODO: specify audio format
        _processor = _pair.getProc();


        PushBufferDataSource dataSource = (PushBufferDataSource) _processor.getDataOutput();
        if (dataSource == null) {
            throw new IOException("Processor.getDataOutput() returned null!");
        }

        
        try {
            _logger.debug("Borrowing recognition engine from object pool...");
            _recorderEngine = (SphinxRecorder) _recorderPool.borrowObject();
        } catch (Exception e) {
            _logger.debug(e, e);
            closeProcessor();
            throw new ResourceUnavailableException("All rec engines are in use!", e);
            // TODO: wait for availability...?
        }
        
        

        if (uri == null) {
            uri = _recordingDir.getAbsolutePath() + getNextFreeIndex(_recordingDir.getAbsolutePath()) + ".wav";
        }
        
        _logger.debug("Starting recognition...");
        _state = WAITING_FOR_SPEECH;
        _recorderEngine.startRecording(dataSource, _recorderListener,uri);

        _processor.addControllerListener(new ProcessorStarter());
        _processor.start();
        
        if (noInputTimeout > 0) {
            startInputTimers(noInputTimeout);
        }
		return _destination;
        
    }
    

    /* (non-Javadoc)
     * @see org.speechforge.cairo.server.recorder.RTPRe#stopRecording()
     */
    public synchronized File stopRecording() throws IllegalStateException {
		return _destination;

    }
    
    /* (non-Javadoc)
     * @see org.speechforge.cairo.server.recorder.RTPRe#closeProcessor()
     */
    public synchronized void closeProcessor() {
        if (_processor != null) {
          _logger.debug("Closing processor...");
            _processor.close();
            _processor = null;
            _replicator.removeReplicant( _pair.getPbds());
        }
        if (_recorderEngine != null) {
            _logger.debug("Returning recengine to pool...");
            try {
                _recorderEnginePool.returnObject(_recorderEngine);
            } catch (Exception e) {
                // TODO Auto-generated catch block
                _logger.debug(e, e);
            }
            _recorderEngine = null;
        } else {
            _logger.warn("No recengine to return to pool!");
        }
    }
    

    
    /* (non-Javadoc)
     * @see org.speechforge.cairo.server.recorder.RTPRe#startInputTimers(long)
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
    

    private class NoInputTimeoutTask extends TimerTask {

        /* (non-Javadoc)
         * @see java.util.TimerTask#run()
         */
        @Override
        public void run() {
            synchronized (RTPRecorderChannelS4Impl.this) {
                _noInputTimeoutTask = null;
                if (_state == WAITING_FOR_SPEECH) {
                    _state = COMPLETE;
                    closeProcessor();
                    if (_recorderListener != null) {
                        _recorderListener.noInputTimeout();
                    }
                }
            }
        }
        
    }
  
    private class Listener extends RecorderListenerDecorator {

        /**
         * TODOC
         * @param recogListener
         */
        public Listener(RecorderListener recorderListener) {
            super(recorderListener);
        }

        /* (non-Javadoc)
         * @see org.speechforge.cairo.server.recog.RecogListener#speechStarted()
         */
        @Override
        public void speechStarted() {
            _logger.info("speechStarted()");

            synchronized (RTPRecorderChannelS4Impl.this) {
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
        public void recordingComplete(String uri) {
            _logger.info("recordingComplete()");

            synchronized (RTPRecorderChannelS4Impl.this) {
                if (_state == SPEECH_IN_PROGRESS) {
                    _state = COMPLETE;
                }
            }
            super.recordingComplete(uri);
        }

        public void noInputTimeout() {
            _logger.info("noInputTimeout()");
            synchronized (RTPRecorderChannelS4Impl.this) {
                if (_state == WAITING_FOR_SPEECH) {
                    _state = COMPLETE;
                }
            }
           super.noInputTimeout();
        }

    }

    
    /**
     * Gets the next free index (a unique number for the next file name)
     * 
     * @param outPattern the out pattern
     * 
     * @return the next free index
     */
    private static int getNextFreeIndex(String outPattern) {
        int fileIndex = 0;
        while (new File(outPattern + fileIndex + ".wav").isFile())
            fileIndex++;
        return fileIndex;

    }
}
