package edu.cmu.sphinx.frontend.endpoint;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Logger;
import edu.cmu.sphinx.frontend.BaseDataProcessor;
import edu.cmu.sphinx.frontend.Data;
import edu.cmu.sphinx.frontend.DataProcessingException;
import edu.cmu.sphinx.frontend.DoubleData;
import edu.cmu.sphinx.util.LogMath;
import edu.cmu.sphinx.util.props.PropertyException;
import edu.cmu.sphinx.util.props.PropertySheet;
import edu.cmu.sphinx.util.props.S4Boolean;
import edu.cmu.sphinx.util.props.S4Double;
import edu.cmu.sphinx.util.props.S4Integer;

/**
 * 
 * Implements a level tracking endpointer invented by Bent Schmidt Nielsen.
 * 
 * Modified to:
 * <ol> 
 * <li> Compare SNR to a configurable value rather than to compare the 
 * absolute difference between the RMS signal and the estimated background 
 * noise.
 * <li>  Estamates the background noise differently.  First rms value is
 * the initial estimate.  All rms values that are declared out of speech are used to
 * adjust the background noise estimate thereafter (does not reset like previous
 * algorithm).
 * </ol>  
 * 
 * <p>For second point.  In the original classifier module, the noise estimate was calculated as 
 * <br>
 * B(i) = c*(F(i) -B(i-1)) + B(i-1) if F(i) < B(i-1) 
 * <br>
 * B(i) = F(i) otherwise.  
 * <br> 
 * where F(i) is the rms value of the ith frame and B(i) is the estmated background noise of the ith frame
 * <br> 
 * This cuases the noise estimate to steadily increase 
 * while in speech and occasionally cut-off end of utterenaces.  Another side effect was that the 
 * noise estimate can drop to a very low level when the F(i) was randomly low and thus causing subsequent 
 * false positives.
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
public class SnrSpeechClassifier extends BaseDataProcessor {

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
    
    /** The SphinxProperty specifying the adjustment. */
    @S4Boolean(defaultValue = false)
    public static final String PROP_SNR_THRESHOLD = "useSnrThreshold";
 
    protected Logger logger;
  
    private double snr;                 //signal to noise ratio
    private boolean snrThresholdFlag;   //if true comapare snr against threshold.  If false compare (signal-noise) against threshold
    private boolean reset  = true;      //flag used to set the inital value of background noise to the next level value
    private boolean debug;
    private double averageNumber = 1;
    private double adjustment;
    private double level;               // average signal level
    private double background = 0;      // background signal level
    private double oldbackground = 0;
    private double minSignal;           // minimum valid signal level
    private double threshold;
    private float frameLengthSec;
    List outputQueue = new LinkedList();
    
    
    
    private long zeroCrossing;
    private double lastValFromPreviousFrame = 0.0;
    private boolean flag =true;
    private BufferedWriter out;
    

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
        snrThresholdFlag = ps.getBoolean(PROP_SNR_THRESHOLD);
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
        if (debug) {
            try {
                out = new BufferedWriter(new FileWriter("c:/temp/"+System.currentTimeMillis()));
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
            //        out.close();
        }
        
    }


    /**
     * Resets this LevelTracker to a starting state.
     */
    private void reset() {
        System.out.println("SpeechClassifierPatch.reset()");
        level = 0;
        background = 10;
        reset = true;          
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
            
            //keep track of zero crossings (to be used for better endpointing -- experimental)
            if (i>0) {
                if (((samples[i] < 0) && (samples[i-1] >0)) || ((samples[i] > 0) && (samples[i-1] <0))) {
                    zeroCrossing++;
                }
            } else if (i ==0) {
                if (((samples[i] < 0) && (lastValFromPreviousFrame >0)) || ((samples[i] > 0) && (lastValFromPreviousFrame <0))) {
                    zeroCrossing++;
                }
            }
        }

        lastValFromPreviousFrame = samples[samples.length-1];
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

        if (reset) {
            background = current;
            reset = false;
         }
        
        //TODO:  Research adding zero crossings to the algorithm
        boolean isSpeech = false;
        if (current >= minSignal) {
            level = ((level*averageNumber) + current)/(averageNumber + 1);
            if (snrThresholdFlag) {
               snr = level/background;
               isSpeech = snr > threshold;
            } else {
              isSpeech = (level - background > threshold);
            }
            
            // update the noise estimate if not in speech.
            // TODO:  Can get stuck in state "In speech" if noise estimate is very low.  
            // And then becuase of this check it never update the nosie estimate
            if (!isSpeech)
                background += (current - background) * adjustment;     
        }
        
        SpeechClassifiedData labeledAudio
            = new SpeechClassifiedData(audio, isSpeech);
        
        if (debug) {
        
            //For debugging. writes the rms level, background noise estimate and zero crossings to a file.
            //it may be useful to be plot this.
            try {
                long inSpeech;
                if (labeledAudio.isSpeech()) {
                    inSpeech = 20;
                } else {
                    inSpeech = 0;
                }
                out.write(inSpeech + "  "+ level  + "  "+  background + "  "+  snr + "  "+ zeroCrossing);
                out.newLine();
                zeroCrossing = 0;
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
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
