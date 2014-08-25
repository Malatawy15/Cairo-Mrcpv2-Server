package org.speechforge.cairo.server.recog.sphinx;

import edu.cmu.sphinx.linguist.language.ngram.LanguageModel;
import edu.cmu.sphinx.linguist.language.ngram.SimpleNGramModel;
import edu.cmu.sphinx.linguist.lextree.LexTreeLinguist;
import edu.cmu.sphinx.recognizer.Recognizer;
import edu.cmu.sphinx.result.Result;
import edu.cmu.sphinx.util.props.ConfigurationManager;
import edu.cmu.sphinx.util.props.PropertySheet;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.speech.recognition.GrammarException;
import org.speechforge.cairo.rtp.server.sphinx.RawAudioProcessor;
import org.speechforge.cairo.rtp.server.sphinx.SpeechDataMonitor;
import org.speechforge.cairo.server.recog.GrammarLocation;
import org.speechforge.cairo.server.recog.InvalidRecognitionResultException;
import org.speechforge.cairo.server.recog.RecogListener;
import org.speechforge.cairo.server.recog.RecognitionResult;
import static org.speechforge.cairo.server.recog.sphinx.SphinxRecEngine._logger;

/**
 *
 * @author Markus Gutbrod
 * @author Stephan Schuenemann
 */
public class SphinxRecEngineARPA extends SphinxRecEngine {

    protected final SimpleNGramModel _nGramModel;
    protected final LexTreeLinguist _linguist;
    private PropertySheet _ps;

    public SphinxRecEngineARPA(ConfigurationManager cm, int id, int origin) throws InstantiationException {
        super(cm, id, origin);
        _logger.info("Creating ARPA Engine # " + id + " . Clone of # " + origin);

        _recognizer = (Recognizer) cm.lookup("recognizer"+origin);
        _logger.debug("ARPA Engine # " + id + ": Recognizer # " + origin + " loaded");

        _nGramModel = (SimpleNGramModel) cm.lookup("trigramModel"+origin);
        _linguist = (LexTreeLinguist) cm.lookup("lexTreeLinguist"+origin);
        _logger.debug("ARPA Engine # " + id + ": trigramModel # " + origin + " loaded");
        _ps = cm.getPropertySheet("trigramModel"+origin);
        
        _recognizer.allocate();

        SpeechDataMonitor speechDataMonitor = (SpeechDataMonitor) cm.lookup("speechDataMonitor"+origin);
        if (speechDataMonitor != null) {
            speechDataMonitor.setSpeechEventListener(this);
        } else {
            _logger.debug("speechDataMonitor couldn't be set up!");
        }

        Object primaryInput = cm.lookup("primaryInput"+origin);
        if (primaryInput instanceof RawAudioProcessor) {
            _rawAudioProcessor = (RawAudioProcessor) primaryInput;
        } else {
            String className = (primaryInput == null) ? null : primaryInput.getClass().getName();
            throw new InstantiationException("Unsupported primary input type: " + className);
        }
    }
    
    public SphinxRecEngineARPA(ConfigurationManager cm, int id) throws InstantiationException {
        super(cm, id);
        _logger.info("Creating ARPA Engine # " + id);

        _recognizer = (Recognizer) cm.lookup("recognizer"+id);
        _logger.debug("ARPA Engine # " + id + ": Recognizer loaded");

        _nGramModel = (SimpleNGramModel) cm.lookup("trigramModel"+id);
        _linguist = (LexTreeLinguist) cm.lookup("lexTreeLinguist"+id);
        _logger.debug("ARPA Engine # " + id + ": trigramModel loaded");
        _ps = cm.getPropertySheet("trigramModel"+id);
        
        _recognizer.allocate();

        SpeechDataMonitor speechDataMonitor = (SpeechDataMonitor) cm.lookup("speechDataMonitor"+id);
        if (speechDataMonitor != null) {
            speechDataMonitor.setSpeechEventListener(this);
        } else {
            _logger.debug("speechDataMonitor couldn't be set up!");
        }

        Object primaryInput = cm.lookup("primaryInput"+id);
        if (primaryInput instanceof RawAudioProcessor) {
            _rawAudioProcessor = (RawAudioProcessor) primaryInput;
        } else {
            String className = (primaryInput == null) ? null : primaryInput.getClass().getName();
            throw new InstantiationException("Unsupported primary input type: " + className);
        }
    }

    @Override
    public void load(GrammarLocation grammarLocation) throws IOException, GrammarException {

        String file = grammarLocation.getBaseURL().toString() + "/" + grammarLocation.getFilename();
        _logger.info("load ARPA language model: " + file);
        System.out.println("load ARPA language model: " + file);
        //_nGramModel.deallocate();
        /*if (_recognizer.getState() == Recognizer.State.ALLOCATED) {
            _recognizer.deallocate();
        }*/
        
        _linguist.deallocate();
        _nGramModel.deallocate();
        _ps.setString(LanguageModel.PROP_LOCATION, file);
        _nGramModel.newProperties(_ps);
        _nGramModel.allocate();
        _linguist.allocate();
        
        _logger.debug("load(): ARPA loading completed successfully.");
    }

    @Override
    public void startRecogThread() {
        _logger.info("RecogThread startup");
        new RecogThread().start();
    }

    private RecognitionResult waitForResult() {
        Result result = null;
        _logger.info("waitForResult() started");
        //_nGramModel.dump();
        do {
            _logger.info("recognition started");
            result = _recognizer.recognize();
        } while (result != null && result.getBestResultNoFiller().trim().isEmpty());
        _logger.info("recognizer in waitForResult() completed its job");
        stopProcessing();
        if (result != null) {
            _logger.info("waitForResult(): result of recognition is " + result.getBestResultNoFiller());

            /*Result result2clear = _recognizer.recognize();
             if (result2clear != null) {
             _logger.debug("waitForResult(): result2clear not null!");
             }*/
        } else {
            _logger.info("waitForResult(): got null result from recognizer!");
            return null;
        }
        RecognitionResult recognitionResult = null;
        try {
            recognitionResult = RecognitionResult.constructResultFromString(result.getBestResultNoFiller());
            //return new RecognitionResult(result, _jsgfGrammar.getRuleGrammar());
            //return null;
        } catch (InvalidRecognitionResultException ex) {
            Logger.getLogger(SphinxRecEngineARPA.class.getName()).log(Level.SEVERE, null, ex);
        }
        return recognitionResult;
    }

    ///////////////////////////////////////////////////////////////////////////
    // inner classes
    ///////////////////////////////////////////////////////////////////////////
    protected class RecogThread extends Thread {

        /* (non-Javadoc)
         * @see java.lang.Runnable#run()
         */
        @Override
        public void run() {
            _logger.debug("RecogThread for ARPA waiting for result...");

            // loop the recognition until the programm exits.
            /*while (true) {
             System.out.println("Start speaking. Press Ctrl-C to quit.\n");

             Result result = null;
             try {
             result = _recognizer.recognize();
             } catch (Exception e) {
             _logger.debug(e.toString());
             }

             if (result != null) {
             String resultText = result.getBestResultNoFiller();
             System.out.println("You said: " + resultText + '\n');
             } else {
             System.out.println("I can't hear what you said.\n");
             }
             }*/
            RecognitionResult result = SphinxRecEngineARPA.this.waitForResult();

            if (_logger.isDebugEnabled()) {
                StringBuilder sb = new StringBuilder();
                sb.append("\n**************************************************************");
                sb.append("\nARPA RecogThread got result: ").append(result.getText());
                //sb.append("\nUtterance").append(result.getRawResult().getBestToken().getWordUnitPath());

                sb.append("\n**************************************************************");
                _logger.debug(sb);
            }

            RecogListener recogListener = null;
            synchronized (SphinxRecEngineARPA.this) {
                recogListener = _recogListener;
            }

            if (recogListener == null) {
                _logger.debug("RecogThread.run(): _recogListener is null!");
            } else {
                recogListener.recognitionComplete(result);
            }
        }
    }

}
