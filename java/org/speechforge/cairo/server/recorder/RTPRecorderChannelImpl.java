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
import org.speechforge.cairo.rtp.server.RTPStreamReplicator.ProcessorReplicatorPair;
import org.speechforge.cairo.rtp.server.sphinx.SourceAudioFormat;
import org.speechforge.cairo.rtp.server.RTPStreamReplicator;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;

import javax.media.DataSink;
import javax.media.Manager;
import javax.media.MediaLocator;
import javax.media.Processor;
import javax.media.datasink.DataSinkEvent;
import javax.media.datasink.DataSinkListener;
import javax.media.datasink.EndOfStreamEvent;
import javax.media.protocol.ContentDescriptor;
import javax.media.protocol.DataSource;
import javax.media.protocol.FileTypeDescriptor;

import org.apache.commons.lang.Validate;
import org.apache.log4j.Logger;

/**
 * Manages recording requests against an RTP audio stream being received.
 *
 * @author Niels Godfredsen {@literal <}<a href="mailto:ngodfredsen@users.sourceforge.net">ngodfredsen@users.sourceforge.net</a>{@literal >}
 */
public class RTPRecorderChannelImpl implements DataSinkListener, RTPRecorderChannel {

    private static Logger _logger = Logger.getLogger(RTPRecorderChannelImpl.class);

    private static final ContentDescriptor CONTENT_DESCRIPTOR_WAVE =
        new FileTypeDescriptor(FileTypeDescriptor.WAVE);

    private File _recordingDir;
    private RTPStreamReplicator _replicator;

    private Processor _processor;
    private File _destination;

	private ProcessorReplicatorPair _pair;

    /**
     * TODOC
     * @param channelID unique id of the recorder channel
     * @param baseRecordingDir base directory to save recorded files to
     * @param replicator 
     * @throws IllegalArgumentException if the File specified is not a directory
     */
    public RTPRecorderChannelImpl(String channelID, File baseRecordingDir, RTPStreamReplicator replicator) throws IllegalArgumentException {
        Validate.isTrue(baseRecordingDir.isDirectory(), "baseRecordingDir parameter was not a directory: ", baseRecordingDir);
        _recordingDir = new File(baseRecordingDir, channelID);
        if (!_recordingDir.mkdir()) {
            throw new IllegalArgumentException("Specified directory not valid: " + _recordingDir.getAbsolutePath());
        }

        Validate.notNull(replicator, "Null replicator!");
        _replicator = replicator;
    }

    /**
     * Starts recording the current RTP stream to an audio file
     * 
     * @param startInputTimers whether to start input timers or wait for a future command to start input timers
     * @return the location of the recorded file
     * @throws IOException
     * @throws IllegalStateException
     */
    
    public synchronized File startRecording(boolean startInputTimers) throws IOException, IllegalStateException {
        if (_processor != null) {
            throw new IllegalStateException("Recording already in progress!");
        }
        
        _pair  = _replicator.createRealizedProcessor(CONTENT_DESCRIPTOR_RAW, 10000,SourceAudioFormat.PREFERRED_MEDIA_FORMATS); // TODO: specify audio format
        _processor = _pair.getProc();

        DataSource dataSource = _processor.getDataOutput();
        if (dataSource == null) {
            throw new IOException("Processor.getDataOutput() returned null!");
        }

        _destination = new File(_recordingDir, new StringBuilder().append(System.currentTimeMillis()).append(".wav").toString());

        try {
            DataSink dataSink = Manager.createDataSink(dataSource, new MediaLocator(_destination.toURL()));
            dataSink.addDataSinkListener(this);
            if (_logger.isDebugEnabled()) {
                _logger.debug("contentType=" + dataSink.getContentType());
            }
            dataSink.open();
            _logger.debug("opened datasink...");
            _processor.start();
            dataSink.start();
            _logger.debug("started processor...");
            _logger.debug("started datasink...");
        } catch (javax.media.NoDataSinkException e){
            throw (IOException) new IOException(e.getMessage()).initCause(e);
        } catch (MalformedURLException e){
            throw (IOException) new IOException(e.getMessage()).initCause(e);
        }

        return _destination;

    }
    
    /**
     * Stops recording the current RTP stream and closes the audio file
     * 
     * @return the location of the recorded file
     * @throws IllegalStateException if recording is not yet in progress
     */
    public synchronized File stopRecording() throws IllegalStateException {
        if (_processor == null) {
            throw new IllegalStateException("Recording not in progress!");
        }
        _logger.debug("Closing processor...");
        _processor.close();
        _logger.debug("Processor closed.");
        _processor = null;
        _replicator.removeReplicant( _pair.getPbds());
        
        // TODO: wait for EndOfStreamEvent

        return _destination;
    }
    
    /**
     * Starts the input timers which trigger no-input-timeout if speech has not started after the specified time.
     * @param noInputTimeout the amount of time to wait, in milliseconds, before triggering a no-input-timeout. 
     * @return {@code true} if input timers were started or {@code false} if speech has already started.
     * @throws IllegalStateException if recognition is not in progress or if the input timers have already been started.
     */
    public synchronized boolean startInputTimers(long noInputTimeout) throws IllegalStateException {
        return true;
    }

    /* (non-Javadoc)
     * @see javax.media.datasink.DataSinkListener#dataSinkUpdate(javax.media.datasink.DataSinkEvent)
     */
    public void dataSinkUpdate(DataSinkEvent event) {
        if (_logger.isDebugEnabled()) {
            _logger.debug("DataSinkEvent received: " + event);
        }

        if (event instanceof EndOfStreamEvent) {
            event.getSourceDataSink().close();
            _logger.debug("closed datasink...");
        }
    }

    @Deprecated
    private class TestThread extends Thread {
        
        @Override
        public void run() {
            try {
                Thread.sleep(1000);
                _logger.debug("TestThread: start recording...");
                startRecording(false);
                Thread.sleep(3000);
                _logger.debug("TestThread: stop recording...");
                stopRecording();
                Thread.sleep(1000);
                _logger.debug("TestThread: start recording...");
                startRecording(false);
                Thread.sleep(2000);
                _logger.debug("TestThread: stop recording...");
                stopRecording();
                _logger.debug("TestThread: complete.");
            } catch (Exception e) {
                _logger.warn(e, e);
            }
        }
        
    }

	public File startRecording(RecorderListener listener, long noInputTimeout, String uri)
            throws IOException, IllegalStateException, ResourceUnavailableException {
	    // TODO Auto-generated method stub
	    return null;
    }

	public void closeProcessor() {
	    // TODO Auto-generated method stub
	    
    }



}
