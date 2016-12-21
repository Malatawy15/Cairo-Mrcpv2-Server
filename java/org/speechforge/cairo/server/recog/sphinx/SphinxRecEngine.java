package org.speechforge.cairo.server.recog.sphinx;

import edu.cmu.sphinx.recognizer.Recognizer;
import edu.cmu.sphinx.util.props.ConfigurationManager;
import java.awt.Toolkit;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import javax.media.protocol.PushBufferDataSource;
import javax.media.protocol.PushBufferStream;
import javax.speech.recognition.GrammarException;
import org.apache.log4j.Logger;
import org.speechforge.cairo.rtp.server.SpeechEventListener;
import org.speechforge.cairo.rtp.server.sphinx.RawAudioProcessor;
import org.speechforge.cairo.rtp.server.sphinx.RawAudioTransferHandler;
import org.speechforge.cairo.server.recog.GrammarLocation;
import org.speechforge.cairo.server.recog.RecogListener;
import org.speechforge.cairo.util.pool.AbstractPoolableObject;

/**
 *
 * @author Markus Gutbrod
 */
public abstract class SphinxRecEngine extends AbstractPoolableObject implements SpeechEventListener {
    protected static final Logger _logger = Logger.getLogger(SphinxRecEngine.class);
    protected final int _id;
    protected final int _origin; 

    protected static final Toolkit _toolkit = _logger.isTraceEnabled()? Toolkit.getDefaultToolkit() : null;
    protected Recognizer _recognizer;
    protected RawAudioProcessor _rawAudioProcessor;

    protected RawAudioTransferHandler _rawAudioTransferHandler;
    RecogListener _recogListener;
    
    public SphinxRecEngine(ConfigurationManager cm, int id, int origin) {
        _id = id;
        _origin = origin;
    }
    
    public SphinxRecEngine(ConfigurationManager cm, int id) {
        _id = id;
        _origin = id;
    }

    /* (non-Javadoc)
     * @see org.speechforge.cairo.util.pool.PoolableObject#activate()
     */
    @Override
    public synchronized void activate() {
        _logger.debug("SphinxRecEngine #" + _id + " activating...");
    }

    /* (non-Javadoc)
     * @see org.speechforge.cairo.util.pool.PoolableObject#passivate()
     */
    @Override
    public synchronized void passivate() {
        _logger.debug("SphinxRecEngine #" + _id + "passivating...");
        stopProcessing();
        _recogListener = null;
    }

    /**
     * TODOC
     * @param dataSource
     * @param listener
     * @throws UnsupportedEncodingException
     */
    public synchronized void startRecognition(PushBufferDataSource dataSource, RecogListener listener) throws UnsupportedEncodingException {
        _logger.debug("SphinxRecEngine  #" + _id + " (Clone of #"+ _origin +") starting  recognition...");
        if (_rawAudioTransferHandler != null) {
            throw new IllegalStateException("Recognition already in progress!");
        }
        PushBufferStream[] streams = dataSource.getStreams();
        if (streams.length != 1) {
            throw new IllegalArgumentException("Rec engine can handle only single stream datasources, # of streams: " + streams);
        }
        if (_logger.isDebugEnabled()) {
            _logger.debug("Starting recognition on stream format: " + streams[0].getFormat());
        }
        try {
            _rawAudioTransferHandler = new RawAudioTransferHandler(_rawAudioProcessor);
            _rawAudioTransferHandler.startProcessing(streams[0]);
        } catch (UnsupportedEncodingException e) {
            _rawAudioTransferHandler = null;
            throw e;
        }
        _recogListener = listener;
    }

    // TODO: rename method
    public abstract void startRecogThread();
    
    /**
     * TODOC
     */
    public synchronized void stopProcessing() {
        _logger.debug("SphinxRecEngine  #"+_id +"stopping processing...");
        if (_rawAudioTransferHandler != null) {
            _rawAudioTransferHandler.stopProcessing();
            _rawAudioTransferHandler = null;
        }
        // TODO: should wait to set this until after run thread completes (i.e. recognizer is cleared)
    }

    /* (non-Javadoc)
     * @see org.speechforge.cairo.server.recog.SpeechEventListener#speechStarted()
     */
    public void speechStarted() {
        if (_toolkit != null) {
            _toolkit.beep();
        }
        RecogListener recogListener = null;
        synchronized (this) {
            recogListener = _recogListener;
        }
        if (recogListener == null) {
            _logger.debug("speechStarted(): _recogListener is null!");
        } else {
            recogListener.speechStarted();
        }
    }

    /* (non-Javadoc)
     * @see org.speechforge.cairo.server.recog.SpeechEventListener#speechEnded()
     */
    public void speechEnded() {
        if (_toolkit != null) {
            _toolkit.beep();
        }
    }
    
    /**
     * TODOC
     * @param grammarLocation
     * @throws IOException
     * @throws GrammarException
     */
    public abstract void load(GrammarLocation grammarLocation) throws IOException, GrammarException;
    
}
