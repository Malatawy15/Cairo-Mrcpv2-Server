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
package org.speechforge.cairo.server.recog.sphinx;

import static org.speechforge.cairo.rtp.server.sphinx.SourceAudioFormat.PREFERRED_MEDIA_FORMATS;
import static org.speechforge.cairo.jmf.JMFUtil.CONTENT_DESCRIPTOR_RAW;
import static org.speechforge.cairo.jmf.JMFUtil.MICROPHONE;

import org.speechforge.cairo.rtp.server.sphinx.RawAudioProcessor;
import org.speechforge.cairo.rtp.server.sphinx.RawAudioTransferHandler;
import org.speechforge.cairo.rtp.server.sphinx.SpeechDataMonitor;
import org.speechforge.cairo.server.recog.GrammarLocation;
import org.speechforge.cairo.server.recog.RecogListener;
import org.speechforge.cairo.server.recog.RecogListenerDecorator;
import org.speechforge.cairo.server.recog.RecognitionResult;
import org.speechforge.cairo.rtp.server.SpeechEventListener;
import org.speechforge.cairo.rtp.server.PBDSReplicator;
import org.speechforge.cairo.jmf.ProcessorStarter;
import org.speechforge.cairo.util.pool.AbstractPoolableObject;

import java.awt.Toolkit;
import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;

import javax.media.CannotRealizeException;
import javax.media.Manager;
import javax.media.NoDataSourceException;
import javax.media.NoProcessorException;
import javax.media.Processor;
import javax.media.ProcessorModel;
import javax.media.protocol.DataSource;
import javax.media.protocol.PushBufferDataSource;
import javax.media.protocol.PushBufferStream;
import javax.speech.recognition.GrammarException;
import javax.speech.recognition.RuleGrammar;
import javax.speech.recognition.RuleParse;

import edu.cmu.sphinx.util.LogMath;
import edu.cmu.sphinx.linguist.language.ngram.SimpleNGramModel;
import edu.cmu.sphinx.linguist.dictionary.*; 
import edu.cmu.sphinx.jsapi.JSGFGrammar;
import edu.cmu.sphinx.recognizer.Recognizer;
import edu.cmu.sphinx.result.Result;
import edu.cmu.sphinx.util.props.ConfigurationManager;
import edu.cmu.sphinx.util.props.PropertyException;

import org.apache.log4j.Logger;

/**
 * Provides a poolable recognition engine that takes raw audio data as input.
 *
 * @author Niels Godfredsen {@literal <}<a href="mailto:ngodfredsen@users.sourceforge.net">ngodfredsen@users.sourceforge.net</a>{@literal >}
 */
public class SphinxRecEngine extends AbstractPoolableObject implements SpeechEventListener {

    private static Logger _logger = Logger.getLogger(SphinxRecEngine.class);
    private static Toolkit _toolkit = _logger.isTraceEnabled()? Toolkit.getDefaultToolkit() : null;

    private int _id;
    private Recognizer _recognizer;
    private JSGFGrammar _jsgfGrammar;
    private RawAudioProcessor _rawAudioProcessor;

    private RawAudioTransferHandler _rawAudioTransferHandler;
    RecogListener _recogListener;
    
    private boolean hotword = false;

    public SphinxRecEngine(ConfigurationManager cm, int id)
      throws IOException, PropertyException, InstantiationException {

    	_logger.info("Creating Engine # "+id);
    	_id = id;
        _recognizer = (Recognizer) cm.lookup("recognizer"+id);
        _recognizer.allocate();
	_logger.info("lookup JSGFGrammar");
        _jsgfGrammar = (JSGFGrammar) cm.lookup("grammar");

//	FastDictionary _fast_dictionary = (FastDictionary) cm.lookup("dictionary");
//	_logger.info("lookup dictionary");

//	String format = "arpa";
//	LogMath _logMath = new LogMath(10, false);
//	URL url = new URL("http://it-tjr7.dhbw-stuttgart.de/patrick/train1.arpa");
//	float weigth = 1;
//	int maxDepth = 5;
	
//	SimpleNGramModel simple_lm = new SimpleNGramModel(format, url, _fast_dictionary, weigth, _logMath, maxDepth);
//	simple_lm.allocate();
//	_logger.info("names :" + cm.getComponentNames());
//	if ((SimpleNGramModel) cm.lookup("lm") == null)
//	{
//		cm.addConfigurable(simple_lm, "lm");
//	}


	
        SpeechDataMonitor speechDataMonitor = (SpeechDataMonitor) cm.lookup("speechDataMonitor"+id);
        if (speechDataMonitor != null) {
            speechDataMonitor.setSpeechEventListener(this);
        }

        Object primaryInput = cm.lookup("primaryInput"+id);
        if (primaryInput instanceof RawAudioProcessor) {
            _rawAudioProcessor = (RawAudioProcessor) primaryInput;
        } else {
            String className = (primaryInput == null) ? null : primaryInput.getClass().getName();
            throw new InstantiationException("Unsupported primary input type: " + className);
        }
    }

    /* (non-Javadoc)
     * @see org.speechforge.cairo.util.pool.PoolableObject#activate()
     */
    @Override
    public synchronized void activate() {
        _logger.debug("SphinxRecEngine #"+_id +" activating...");
    }

    /* (non-Javadoc)
     * @see org.speechforge.cairo.util.pool.PoolableObject#passivate()
     */
    @Override
    public synchronized void passivate() {
        _logger.debug("SphinxRecEngine #"+_id +"passivating...");
        stopProcessing();
        _recogListener = null;
    }

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

    /**
     * TODOC
     * @param grammarLocation
     * @throws IOException
     * @throws GrammarException
     */
    public synchronized void loadJSGF(GrammarLocation grammarLocation) throws IOException, GrammarException {
    	
    	_logger.info("Allocating grammar");
    	_jsgfGrammar.allocate();
    	_logger.info("Loading grammar");
        _jsgfGrammar.setBaseURL(grammarLocation.getBaseURL());
        _jsgfGrammar.loadJSGF(grammarLocation.getGrammarName());
       _logger.debug("loadJSGF(): completed successfully.");
//	_jsgfGrammar.deallocate();
    }
    
    public synchronized void deallocateJSGF(){
    	_jsgfGrammar.deallocate();
    }

    /**
     * TODOC
     * @param text
     * @param ruleName
     * @return
     * @throws GrammarException
     */
    public synchronized RuleParse parse(String text, String ruleName) throws GrammarException {
        if (_rawAudioTransferHandler != null) {
            throw new IllegalStateException("Recognition already in progress!");
        }
        
        RuleGrammar ruleGrammar = _jsgfGrammar.getRuleGrammar();
        return ruleGrammar.parse(text, ruleName);
    }

    /**
     * TODOC
     * @param dataSource
     * @param listener 
     * @throws UnsupportedEncodingException 
     */
    public synchronized void startRecognition(PushBufferDataSource dataSource, RecogListener listener)
      throws UnsupportedEncodingException {

        _logger.debug("SphinxRecEngine  #"+_id +"starting  recognition...");
        if (_rawAudioTransferHandler != null) {
            throw new IllegalStateException("Recognition already in progress!");
        }

        PushBufferStream[] streams = dataSource.getStreams();
        if (streams.length != 1) {
            throw new IllegalArgumentException(
                "Rec engine can handle only single stream datasources, # of streams: " + streams);
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
    public void startRecogThread() {
        new RecogThread().start();
    }

    private RecognitionResult waitForResult(boolean hotword) {
        Result result = null;
        
        _logger.debug("The hotword flag is: "+hotword);
        //if hotword mode, run recognize until a match occurs
        if (hotword) {
            RecognitionResult rr = new RecognitionResult();
            boolean inGrammarResult = false;
            while (!inGrammarResult) {
                 result = _recognizer.recognize();

                 if (result == null) {
                     _logger.debug("result is null");
                 } else {
                     _logger.debug("result is:"+result.toString());
                 }
                 rr.setNewResult(result, _jsgfGrammar.getRuleGrammar());
                 _logger.debug("Rec result: "+rr.toString());
                 _logger.debug("text:"+rr.getText()+" matches:"+rr.getRuleMatches()+" oog flag:"+rr.isOutOfGrammar());
                 if( (!rr.getRuleMatches().isEmpty()) && (!rr.isOutOfGrammar())) {
                     inGrammarResult = true;
                 }
            }
         
        //if not hotword, just run recognize once
        } else {
             result = _recognizer.recognize();
        }
        stopProcessing();
        if (result != null) {
            Result result2clear = _recognizer.recognize();
            if (result2clear != null) {
                _logger.debug("waitForResult(): result2clear not null!");
            }
        } else {
            _logger.info("waitForResult(): got null result from recognizer!");
            return null;
        }
        return new RecognitionResult(result, _jsgfGrammar.getRuleGrammar());

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

    ///////////////////////////////////////////////////////////////////////////
    // inner classes
    ///////////////////////////////////////////////////////////////////////////

    private class RecogThread extends Thread {
        
        /* (non-Javadoc)
         * @see java.lang.Runnable#run()
         */
        @Override
        public void run() {
            _logger.debug("RecogThread waiting for result...");

            RecognitionResult result = SphinxRecEngine.this.waitForResult(hotword);

            if (_logger.isDebugEnabled()) {
                StringBuilder sb = new StringBuilder();
                sb.append("\n**************************************************************");
                sb.append("\nRecogThread got result: ").append(result);
                sb.append("\nUtterance"+result.getRawResult().getBestToken().getWordUnitPath());

                sb.append("\n**************************************************************");
                _logger.debug(sb);
            }
            
            RecogListener recogListener = null;
            synchronized (SphinxRecEngine.this) {
                recogListener = _recogListener;
            }

            if (recogListener == null) {
                _logger.debug("RecogThread.run(): _recogListener is null!");
            } else {
                recogListener.recognitionComplete(result);
            }
        }
    }

    /**
     * Provides a client for testing {@link org.speechforge.cairo.server.recog.sphinx.SphinxRecEngine}
     * in standalone mode using the microphone for input.
     */
    public static class Test extends RecogListenerDecorator {

        private SphinxRecEngine _engine;
        private RecognitionResult _result;
        private PBDSReplicator _replicator;

        public Test(SphinxRecEngine engine)
          throws NoProcessorException, NoDataSourceException, CannotRealizeException, IOException {
            super(null);
            _engine = engine;
            _replicator = createMicrophoneReplicator();
        }

        /* (non-Javadoc)
         * @see org.speechforge.cairo.server.recog.RecogListener#recognitionComplete(org.speechforge.cairo.server.recog.RecognitionResult)
         */
        @Override
        public synchronized void recognitionComplete(RecognitionResult result) {
            _result = result;
            this.notify();
        }

        public RecognitionResult doRecognize() throws IOException, NoProcessorException, CannotRealizeException,
                InterruptedException {

            _result = null;
            _engine.activate();

            Processor processor = createReplicatedProcessor();
            processor.addControllerListener(new ProcessorStarter());

            PushBufferDataSource pbds = (PushBufferDataSource) processor.getDataOutput();
            _engine.startRecognition(pbds, this);
            processor.start();
            _logger.debug("Performing recognition...");
            _engine.startRecogThread();

            // wait for result
            RecognitionResult result = null;
            synchronized (this) {
                while (_result == null) {
                    this.wait(1000);
                }
                result = _result;
                _result = null;
            }

            _engine.passivate();

            return result;
        }

        private Processor createReplicatedProcessor() throws IOException,
                IllegalStateException, NoProcessorException,
                CannotRealizeException {
            
            ProcessorModel pm = new ProcessorModel(
                    _replicator.replicate(),
                    PREFERRED_MEDIA_FORMATS,
                    CONTENT_DESCRIPTOR_RAW
            );
            
            _logger.debug("Creating realized processor...");
            Processor processor = Manager.createRealizedProcessor(pm);
            _logger.debug("Processor realized.");
            
            return processor;
        }

        private static Processor createMicrophoneProcessor()
          throws NoDataSourceException, IOException, NoProcessorException, CannotRealizeException {

            DataSource dataSource = Manager.createDataSource(MICROPHONE);
            ProcessorModel pm = new ProcessorModel(dataSource,
                    PREFERRED_MEDIA_FORMATS, CONTENT_DESCRIPTOR_RAW);
            Processor processor = Manager.createRealizedProcessor(pm);
            return processor;
        }

        private static PBDSReplicator createMicrophoneReplicator()
          throws NoProcessorException, NoDataSourceException, CannotRealizeException, IOException {
            Processor processor = createMicrophoneProcessor();
            processor.addControllerListener(new ProcessorStarter());
            PushBufferDataSource pbds = (PushBufferDataSource) processor.getDataOutput();
            PBDSReplicator replicator = new PBDSReplicator(pbds);
            processor.start();
            return replicator;
        }
        
        public static void main(String[] args) throws Exception {
            URL url;
            if (args.length > 0) {
                url = new File(args[0]).toURL();
            } else {
                url = SphinxRecEngine.class.getResource("/config/sphinx-config.xml");
            }
            
            if (url == null) {
                throw new RuntimeException("Sphinx config file not found!");
            }

            _logger.info("Loading...");
            ConfigurationManager cm = new ConfigurationManager(url);
            SphinxRecEngine engine = new SphinxRecEngine(cm,1);

            if (_logger.isDebugEnabled()) {
                for (int i=0; i < 12; i++) {
                    _logger.debug(engine._jsgfGrammar.getRandomSentence());
                }
            }

            Test test = new Test(engine);
            

            RecognitionResult result;
            while (true) {
                result = test.doRecognize();
            }

//            RuleParse ruleParse = engine.parse("", "main");


            //System.exit(0);
        }

    }

    /**
     * @return the hotword
     */
    public boolean isHotword() {
        return hotword;
    }

    /**
     * @param hotword the hotword to set
     */
    public void setHotword(boolean hotword) {
        this.hotword = hotword;
    }

}
