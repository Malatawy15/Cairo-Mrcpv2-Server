/*
 * Copyright 1999-2002 Carnegie Mellon University.  
 * Portions Copyright 2002 Sun Microsystems, Inc.  
 * Portions Copyright 2002 Mitsubishi Electric Research Laboratories.
 * All Rights Reserved.  Use is subject to license terms.
 * 
 * See the file "license.terms" for information on usage and
 * redistribution of this file, and for a DISCLAIMER OF ALL 
 * WARRANTIES.
 *
 */

/**
 * 
 * NOTE:
 * 
 * This file provides a patch to fix a bug in edu.cmu.sphinx.frontend.endpoint.SpeechClassifier.
 * Please see http://sourceforge.net/tracker/index.php?func=detail&aid=1501329&group_id=1904&atid=101904
 * for details of the bug.
 * 
 * Since the code contained herein is verbatim to the original, with the exception of a misspelled
 * method that has been fixed, the code retains the original CMU, et al. copyright notice. 
 * 
 */

package edu.cmu.sphinx.frontend.endpoint;

import java.util.LinkedList;
import java.util.List;

import java.util.logging.Level;
import java.util.logging.Logger;

import edu.cmu.sphinx.frontend.BaseDataProcessor;
import edu.cmu.sphinx.frontend.Data;
import edu.cmu.sphinx.frontend.DataProcessingException;
import edu.cmu.sphinx.frontend.DoubleData;
import edu.cmu.sphinx.util.LogMath;
import edu.cmu.sphinx.util.props.PropertyException;
import edu.cmu.sphinx.util.props.PropertySheet;
import edu.cmu.sphinx.util.props.S4Double;
import edu.cmu.sphinx.util.props.S4Integer;


/**
 * 
 * Provides a patch to fix a bug in {@code edu.cmu.sphinx.frontend.endpoint.SpeechClassifier}.
 *
 * <p>Please see <a href="http://sourceforge.net/tracker/index.php?func=detail&aid=1501329&group_id=1904&atid=101904"
 * target="_blank">http://sourceforge.net/tracker/index.php?func=detail&aid=1501329&group_id=1904&atid=101904</a>
 * for details of the bug.<hr>
 *
 * Implements a level tracking endpointer invented by Bent Schmidt Nielsen.
 *
 * <p>This endpointer is composed of three main steps.
 * <ol>
 * <li>classification of audio into speech and non-speech
 * <li>inserting SPEECH_START and SPEECH_END signals around speech
 * <li>removing non-speech regions
 * </ol>
 *
 * <p>The first step, classification of audio into speech and non-speech,
 * uses Bent Schmidt Nielsen's algorithm. Each time audio comes in, 
 * the average signal level and the background noise level are updated,
 * using the signal level of the current audio. If the average signal
 * level is greater than the background noise level by a certain
 * threshold value (configurable), then the current audio is marked
 * as speech. Otherwise, it is marked as non-speech.
 *
 * <p>The second and third step of this endpointer are documented in the
 * classes {@link SpeechMarker SpeechMarker} and 
 * {@link NonSpeechDataFilter NonSpeechDataFilter}.
 *
 * @see SpeechMarker
 *
 */
public class SpeechClassifierPatch extends BaseDataProcessor {

    /** The SphinxProperty specifying the endpointing frame length in milliseconds. */
    @S4Integer(defaultValue = 10)
    public static final String PROP_FRAME_LENGTH_MS = "frameLengthInMs";

    /** The SphinxProperty specifying the minimum signal level used to update the background signal level. */
    @S4Double(defaultValue = 0)
    public static final String PROP_MIN_SIGNAL = "minSignal";


    /**
     * The SphinxProperty specifying the threshold. If the current signal level is greater than the background level by
     * this threshold, then the current signal is marked as speech. Therefore, a lower threshold will make the
     * endpointer more sensitive, that is, mark more audio as speech. A higher threshold will make the endpointer less
     * sensitive, that is, mark less audio as speech.
     */
    @S4Double(defaultValue = 10)
    public static final String PROP_THRESHOLD = "threshold";

    /** The SphinxProperty specifying the adjustment. */
    @S4Double(defaultValue = 0.003)
    public static final String PROP_ADJUSTMENT = "adjustment";
    

    protected Logger logger;

    private boolean debug;
    private double averageNumber = 1;
    private double adjustment;
    private double level;               // average signal level
    private double background = 0;          // background signal level
    private double minSignal;           // minimum valid signal level
    private double threshold;
    private float frameLengthSec;
    List outputQueue = new LinkedList();
    
    /*
     * (non-Javadoc)
     * 
     * @see edu.cmu.sphinx.util.props.Configurable#newProperties(edu.cmu.sphinx.util.props.PropertySheet)
     */
    public void newProperties(PropertySheet ps) throws PropertyException {
    	
    	
        super.newProperties(ps);
        int frameLengthMs = ps.getInt(PROP_FRAME_LENGTH_MS);
        frameLengthSec = ((float) frameLengthMs) / 1000.f;

        adjustment = ps.getDouble(PROP_ADJUSTMENT);
        threshold = ps.getDouble(PROP_THRESHOLD);
        minSignal = ps.getDouble(PROP_MIN_SIGNAL);
        logger = ps.getLogger();

        initialize();
    	

    }

    /**
     * Initializes this LevelTracker endpointer 
     * and DataProcessor predecessor.
     *
     */
    public void initialize() {
        super.initialize();
        reset();
    }


    /**
     * Resets this LevelTracker to a starting state.
     */
    private void reset() {
        System.out.println("SpeechClassifierPatch.reset()");
        level = 0;
        background = 100;
    }

    /**
     * Returns the logarithm base 10 of the root mean square of the
     * given samples.
     *
     * @param samples the samples
     *
     * @return the calculated log root mean square in log 10
     */
    private double logRootMeanSquare(double[] samples) {
        assert samples.length > 0;
        double sumOfSquares = 0.0f;
        for (int i = 0; i < samples.length; i++) {
            double sample = samples[i];
            sumOfSquares += sample * sample;
        }
        double rootMeanSquare = Math.sqrt
            ((double)sumOfSquares/samples.length);
        rootMeanSquare = Math.max(rootMeanSquare, 1);
        return (LogMath.log10((float)rootMeanSquare) * 20);
    }
    
    /**
     * Classifies the given audio frame as speech or not, and updates
     * the endpointing parameters.
     *
     * @param audio the audio frame
     */
    private void classify(DoubleData audio) {
        double current = logRootMeanSquare(audio.getValues());
        // System.out.println("current: " + current);
        if (logger.isLoggable(Level.FINEST)) {
            System.out.println("Before -- Bkg: " + background + ", level: " + level +
                               ", current: " + current);
        }
        boolean isSpeech = false;
        if (current >= minSignal) {
            level = ((level*averageNumber) + current)/(averageNumber + 1);
            if (current < background) {
                background = current;
            } else {
                background += (current - background) * adjustment;
            }
            if (level < background) {
                level = background;
            }
            isSpeech = (level - background > threshold);
        }
        SpeechClassifiedData labeledAudio
            = new SpeechClassifiedData(audio, isSpeech);
        if (logger.isLoggable(Level.FINEST)) {
            String speech = "";
            if (labeledAudio.isSpeech()) {
                speech = "*";
            }
            System.out.println("Bkg: " + background + ", level: " + level +
                               ", current: " + current + " " + speech);
        }
        outputQueue.add(labeledAudio);
    }
    
    /**
     * Returns the next Data object.
     *
     * @return the next Data object, or null if none available
     *
     * @throws DataProcessingException if a data processing error occurs
     */
    public Data getData() throws DataProcessingException {
        if (outputQueue.size() == 0) {
            Data audio = getPredecessor().getData();
            if (audio != null) {
                if (audio instanceof DoubleData) {
                    DoubleData data = (DoubleData) audio;
                    if (data.getValues().length > 
                        ((int)(frameLengthSec * data.getSampleRate()))) {
                        throw new Error
                            ("Length of data frame is " + 
                             data.getValues().length + 
                             " samples, but the expected frame is <= " +
                             (frameLengthSec * data.getSampleRate()));
                    }
                    classify(data);
                } else {
                    outputQueue.add(audio);
                }
            }
        }
        if (outputQueue.size() > 0) {
            Data audio = (Data) outputQueue.remove(0);
            return audio;
        } else {
            return null;
        }
    }
}
