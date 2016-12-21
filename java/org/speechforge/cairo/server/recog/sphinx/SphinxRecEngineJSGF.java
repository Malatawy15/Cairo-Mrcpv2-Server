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


import org.speechforge.cairo.rtp.server.sphinx.RawAudioProcessor;
import org.speechforge.cairo.rtp.server.sphinx.SpeechDataMonitor;
import org.speechforge.cairo.server.recog.GrammarLocation;
import org.speechforge.cairo.server.recog.RecogListener;
import org.speechforge.cairo.server.recog.RecognitionResult;
import java.io.IOException;
import javax.speech.recognition.GrammarException;
import javax.speech.recognition.RuleGrammar;
import javax.speech.recognition.RuleParse;

import edu.cmu.sphinx.jsapi.JSGFGrammar;
import edu.cmu.sphinx.recognizer.Recognizer;
import edu.cmu.sphinx.result.Result;
import edu.cmu.sphinx.util.props.ConfigurationManager;
import edu.cmu.sphinx.util.props.PropertyException;

/**
 * Provides a poolable recognition engine that takes raw audio data as input.
 *
 * @author Niels Godfredsen {@literal <}<a
 * href="mailto:ngodfredsen@users.sourceforge.net">ngodfredsen@users.sourceforge.net</a>{@literal >}
 */
public class SphinxRecEngineJSGF extends SphinxRecEngine {

    private boolean hotword = false;
    protected final JSGFGrammar _jsgfGrammar;

    public SphinxRecEngineJSGF(ConfigurationManager cm, int id, int origin)
            throws IOException, PropertyException, InstantiationException {

        super(cm, id, origin);
        _logger.info("Creating JSGF Engine # " + id + " . Clone of # " + origin);

        _recognizer = (Recognizer) cm.lookup("recognizer" + origin);
        _recognizer.allocate();

        _jsgfGrammar = (JSGFGrammar) cm.lookup("grammar");

        SpeechDataMonitor speechDataMonitor = (SpeechDataMonitor) cm.lookup("speechDataMonitor" + origin);
        if (speechDataMonitor != null) {
            speechDataMonitor.setSpeechEventListener(this);
        }

        Object primaryInput = cm.lookup("primaryInput" + origin);
        if (primaryInput instanceof RawAudioProcessor) {
            _rawAudioProcessor = (RawAudioProcessor) primaryInput;
        } else {
            String className = (primaryInput == null) ? null : primaryInput.getClass().getName();
            throw new InstantiationException("Unsupported primary input type: " + className);
        }
    }
    
    public SphinxRecEngineJSGF(ConfigurationManager cm, int id)
            throws IOException, PropertyException, InstantiationException {

        super(cm, id);
        _logger.info("Creating JSGF Engine # " + id);

        _recognizer = (Recognizer) cm.lookup("recognizer" + id);
        _recognizer.allocate();

        _jsgfGrammar = (JSGFGrammar) cm.lookup("grammar");

        SpeechDataMonitor speechDataMonitor = (SpeechDataMonitor) cm.lookup("speechDataMonitor" + id);
        if (speechDataMonitor != null) {
            speechDataMonitor.setSpeechEventListener(this);
        }

        Object primaryInput = cm.lookup("primaryInput" + id);
        if (primaryInput instanceof RawAudioProcessor) {
            _rawAudioProcessor = (RawAudioProcessor) primaryInput;
        } else {
            String className = (primaryInput == null) ? null : primaryInput.getClass().getName();
            throw new InstantiationException("Unsupported primary input type: " + className);
        }
    }

    @Override
    public synchronized void load(GrammarLocation grammarLocation) throws IOException, GrammarException {

        _logger.debug("loadJSGF file: " + grammarLocation.getFilename());
        _jsgfGrammar.setBaseURL(grammarLocation.getBaseURL());
        _jsgfGrammar.loadJSGF(grammarLocation.getGrammarName());
        _logger.debug("load (JSGF file): completed successfully.");

    }

    /**
     * TODOC
     *
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

    @Override
    public void startRecogThread() {
        new RecogThread().start();
    }

    private RecognitionResult waitForResult(boolean hotword) {
        Result result = null;

        _logger.debug("The hotword flag is: " + hotword);
        //if hotword mode, run recognize until a match occurs
        if (hotword) {
            _logger.debug("recognize with hotword mode");
            RecognitionResult rr = new RecognitionResult();
            boolean inGrammarResult = false;
            while (!inGrammarResult) {
                result = _recognizer.recognize();

                if (result == null) {
                    _logger.debug("result is null");
                } else {
                    _logger.debug("result is:" + result.toString());
                }
                rr.setNewResult(result, _jsgfGrammar.getRuleGrammar());
                _logger.debug("Rec result: " + rr.toString());
                _logger.debug("text:" + rr.getText() + " matches:" + rr.getRuleMatches() + " oog flag:" + rr.isOutOfGrammar());
                if ((!rr.getRuleMatches().isEmpty()) && (!rr.isOutOfGrammar())) {
                    inGrammarResult = true;
                }
            }

            //if not hotword, just run recognize once
        } else {
            _logger.debug("recognize without hotword mode enabled");
            while(result==null || result.getBestFinalResultNoFiller() == null || result.getBestFinalResultNoFiller().trim().isEmpty())
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

    ///////////////////////////////////////////////////////////////////////////
    // inner classes
    ///////////////////////////////////////////////////////////////////////////
    protected class RecogThread extends Thread {

        /* (non-Javadoc)
         * @see java.lang.Runnable#run()
         */
        @Override
        public void run() {
            _logger.debug("RecogThread waiting for result...");

            RecognitionResult result = null;
            result = SphinxRecEngineJSGF.this.waitForResult(hotword);

            if (_logger.isDebugEnabled()) {
                StringBuilder sb = new StringBuilder();
                sb.append("\n**************************************************************");
                sb.append("\nRecogThread got result: ").append(result);
                sb.append("\nUtterance" + result.getRawResult().getBestToken().getWordUnitPath());

                sb.append("\n**************************************************************");
                _logger.debug(sb);
            }

            RecogListener recogListener = null;
            synchronized (SphinxRecEngineJSGF.this) {
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
