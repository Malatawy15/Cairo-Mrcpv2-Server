package org.speechforge.cairo.server.recorder.sphinx;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;

import javax.media.protocol.PushBufferDataSource;
import javax.media.protocol.PushBufferStream;
import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;

import org.apache.log4j.Logger;
import org.speechforge.cairo.jmf.ProcessorStarter;
import org.speechforge.cairo.rtp.server.SpeechEventListener;
import org.speechforge.cairo.rtp.server.sphinx.RawAudioProcessor;
import org.speechforge.cairo.rtp.server.sphinx.RawAudioTransferHandler;
import org.speechforge.cairo.rtp.server.sphinx.SpeechDataMonitor;
import org.speechforge.cairo.rtp.server.sphinx.SpeechDataRecorder;
import org.speechforge.cairo.server.recog.RecogListener;
import org.speechforge.cairo.server.recog.sphinx.SphinxRecEngineFactory;
import org.speechforge.cairo.server.recorder.RecorderListener;
import org.speechforge.cairo.util.pool.AbstractPoolableObject;
import org.speechforge.cairo.util.pool.PoolableObject;


import edu.cmu.sphinx.frontend.BaseDataProcessor;
import edu.cmu.sphinx.frontend.Data;
import edu.cmu.sphinx.frontend.DataEndSignal;
import edu.cmu.sphinx.frontend.DataStartSignal;
import edu.cmu.sphinx.frontend.DoubleData;
import edu.cmu.sphinx.frontend.FloatData;
import edu.cmu.sphinx.frontend.FrontEnd;
import edu.cmu.sphinx.frontend.endpoint.SpeechEndSignal;
import edu.cmu.sphinx.frontend.endpoint.SpeechStartSignal;
import edu.cmu.sphinx.jsapi.JSGFGrammar;
import edu.cmu.sphinx.recognizer.Recognizer;
import edu.cmu.sphinx.util.props.ConfigurationManager;

public class SphinxRecorder extends AbstractPoolableObject implements SpeechEventListener {
    private static Logger _logger = Logger.getLogger(SphinxRecorder.class);
    
	private FrontEnd _fe;
	private int _id;
    private RawAudioProcessor _rawAudioProcessor;
    private RawAudioTransferHandler _rawAudioTransferHandler;
    private RecorderListener _recorderListener;
    private SpeechDataStreamer streamer ;
    private String _uri;
    
	public SphinxRecorder(ConfigurationManager cm, int id) throws InstantiationException {

    	_logger.info("Creating Recorder # "+id);
    	_id = id;
        _fe = (FrontEnd) cm.lookup("frontEnd"+id);
		_fe.initialize();
        


        Object primaryInput = cm.lookup("primaryInput"+id);
        if (primaryInput instanceof RawAudioProcessor) {
            _rawAudioProcessor = (RawAudioProcessor) primaryInput;
        } else {
            String className = (primaryInput == null) ? null : primaryInput.getClass().getName();
            throw new InstantiationException("Unsupported primary input type: " + className);
        }
    }

	
    public synchronized void stopProcessing() {
        _logger.debug("SphinxRecordingEngine  #"+_id +"stopping processing...");
        if (_rawAudioTransferHandler != null) {
            _rawAudioTransferHandler.stopProcessing();
            _rawAudioTransferHandler = null;
        }
        // TODO: should wait to set this until after run thread completes (i.e. recognizer is cleared)
    }
	
	public void activate() throws Exception {
		_logger.debug("SphinxRecordingEngine #"+_id +" activating...");

	}



	public void passivate() throws Exception {
        _logger.debug("SphinxRecorderEngine #"+_id +"passivating...");
        stopProcessing();
        _recorderListener = null;

	}


	public void speechEnded() {
        stopProcessing();
        RecorderListener recorderListener = null;
        synchronized (this) {
        	recorderListener = _recorderListener; 
        }

        if (recorderListener == null) {
            _logger.debug("speechStarted(): recorderListener is null!");
        } else {
        	recorderListener.recordingComplete(_uri);
        }
    }

	public void speechStarted() {

        RecorderListener recorderListener = null;
        synchronized (this) {
        	recorderListener = _recorderListener; 
        }

        if (recorderListener == null) {
            _logger.debug("speechStarted(): recorderListener is null!");
        } else {
        	recorderListener.speechStarted();
        }

    }
	
	public void startRecording(PushBufferDataSource dataSource, RecorderListener listener, String uri) throws UnsupportedEncodingException {
		
		_uri = uri;
		
        _logger.info("SphinxRecEngine  #"+_id +"starting  recognition...");
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

        _recorderListener = listener;
        
        streamer  = new SpeechDataStreamer();
		streamer.setSpeechEventListener(this);
		try {
	        streamer.startRecordingThread();
        } catch (IOException e) {
	        // TODO Auto-generated catch block
	        e.printStackTrace();
        }
		
	}
	
	
	class SpeechDataStreamer  extends Thread{


	    //private BaseDataProcessor frontEnd;
		//private OutputStream out;
	    //private ObjectOutputStream dout;
	    
	    
	    private ByteArrayOutputStream baos;
	    private DataOutputStream dos;

	    private boolean isInSpeech;
	    
	    private SpeechEventListener _speechEventListener = null;
	    
		/**
	     * TODOC
	     */
	    public SpeechDataStreamer() {
	        super();
	        // TODO Auto-generated constructor stub
	    }

	    public void setSpeechEventListener(SpeechEventListener speechEventListener) {
	        _speechEventListener = speechEventListener;
	    }
	    
	    private void broadcastSpeechStartSignal() {
	        if (_speechEventListener != null) {
	            _speechEventListener.speechStarted();
	        }
	    }

	    private void broadcastSpeechEndSignal() {
	        if (_speechEventListener != null) {
	            _speechEventListener.speechEnded();
	        }
	    }
	    
	    
	    private void showSignals(Data data) {
	        if (data instanceof SpeechStartSignal) {
		        _logger.info("SpeechStartSignal encountered!");
	        } else if (data instanceof SpeechEndSignal) {
		        _logger.info("SpeechEndSignal encountered!");
	        } else if (data instanceof DataStartSignal) {
	            _logger.info("DataStartSignal encountered!");
	            infoDataStartSignal((DataStartSignal) data);
	        } else if (data instanceof DataEndSignal) {
	            _logger.info("DataEndSignal encountered!");
	        }

	    }

	    private void infoDataStartSignal(DataStartSignal dataStartSignal) {
	        Map<String, Object> dataProps = dataStartSignal.getProps();
	        if (dataProps.containsKey(DataStartSignal.SPEECH_TAGGED_FEATURE_STREAM))
	           _logger.debug("SPEECH TAG FEATURE STREAM: "+dataProps.get(DataStartSignal.SPEECH_TAGGED_FEATURE_STREAM));
	    }
	    
   
	    
	    private void showData(Data data) {

	        if (data instanceof DoubleData) {
	        	DoubleData dd = (DoubleData) data;
	        	double[] d = dd.getValues();

	        	_logger.debug(dd.toString());
	        	_logger.debug("Sending " + d.length + " values.  "+d[0]+ " "+d[d.length-1]);
	        } else if (data instanceof FloatData) {
	        	FloatData fd = (FloatData) data;
	        	_logger.debug("FloatData: " + fd.getSampleRate() + "Hz, first sample #: " +
	                    fd.getFirstSampleNumber() + ", collect time: " + fd.getCollectTime());
	        	float[] d = fd.getValues();
	        	_logger.debug("Sending " + d.length + " values.");
	        	//for (float val: d) {
	        	//	_logger.info(val);
	        	//}
	        }
	    }
	    
	    /**
	     * Writes the data to a file.  The data should correspond to the utterance (post endpointing)
	     * so it is the same that gets fed to recoginizer.  File names is a sequences number
	     * that gets incremented for each utterance.
	     * 
	     * @param data the data
	     */
	    private void stopRecordingData(Data data) {
	        
	        //location of audio file (where it will be written)
	        //String dumpFilePath = "c:/temp/";

	        
	        //audio format parameters
	        int bitsPerSample = 16;
	        int sampleRate = 8000;
	        boolean isBigEndian = true;
	        boolean isSigned = true;

	        
	        //create an audio format object (java sound api)
	        AudioFormat wavFormat = new AudioFormat(sampleRate, bitsPerSample, 1, isSigned, isBigEndian);
	        AudioFileFormat.Type outputType = getTargetType("wav");


	        
	        _logger.debug("created audio Format Object "+wavFormat.toString());
	        _logger.debug("filename:" + _uri);

	        byte[] abAudioData = baos.toByteArray();
	        ByteArrayInputStream bais = new ByteArrayInputStream(abAudioData);
	        AudioInputStream ais = new AudioInputStream(bais, wavFormat, abAudioData.length / wavFormat.getFrameSize());

	        URI outuri = null;
            try {
	            outuri = new URI(_uri);
            } catch (URISyntaxException e1) {
	            // TODO Auto-generated catch block
	            e1.printStackTrace();
            }
	        File outWavFile = new File(outuri);

	        if (AudioSystem.isFileTypeSupported(outputType, ais)) {
	            try {
	                AudioSystem.write(ais, outputType, outWavFile);
	            } catch (IOException e) {
	                e.printStackTrace();
	            }
	        } else {
	           System.out.println("output type not supported..."); 
	        }

	        /*Player player;
	        try {
	            MediaLocator source = new MediaLocator(outWavFile.toURL());
	            DataSource dataSource = Manager.createDataSource(source);
	            player = Manager.createRealizedPlayer(dataSource);
	            player.start();
	            player.close();
	            player.deallocate();
	            player = null;
	        } catch (Exception e) {
	            _logger.warn("Could not create player for new stream!", e);
	        }
	        _logger.debug("Starting player..."); */

	        
	        isInSpeech = false;
	    }
	    


	    public void startRecordingThread() throws IOException {
	        start();
	    }


	    
	    public void run() {

	    	_logger.debug("starting recorder thread");
	    	boolean moreData = true;
	    	while (moreData) {

	    		Data data = _fe.getData();
	            if (data instanceof SpeechStartSignal) {
	                isInSpeech = true;
	                baos = new ByteArrayOutputStream();
	                dos = new DataOutputStream(baos);
	                broadcastSpeechStartSignal();
	            } else if (data instanceof SpeechEndSignal) {
	                broadcastSpeechEndSignal();
	                stopRecordingData(data);
	            //} else if (data instanceof DataStartSignal) {
	            //} else if (data instanceof DataEndSignal) {
	            }


	    		if (data != null) {
			        //if inspeech we want to write to a file.  
			        // convert to double and call write method
			        if ((isInSpeech) && (data instanceof DoubleData || data instanceof FloatData)) {
			            DoubleData dd = data instanceof DoubleData ? (DoubleData) data : FloatData2DoubleData((FloatData) data);
			            double[] values = dd.getValues();
			            for (double value : values) {
			                try {
			                    dos.writeShort(new Short((short) value));
			                } catch (IOException e) {
			                    e.printStackTrace();
			                }
			            }
			        }
	    		} else {
	    			_logger.info("Null data");
	    			moreData=false;
	    		}
	            
	    		showSignals(data);
	    		showData(data);
	    		//_logger.debug("SDS: "+data);
	 

	    		

	    	}
	    	_logger.info("dropped out of the get data loop in the recording thread");
	    }
	}
	

    
    /**
     * Converts DoubleData object to FloatDatas.
     * 
     * @param data the data
     * 
     * @return the double data
     */
    public  static DoubleData FloatData2DoubleData(FloatData data) {
        int numSamples = data.getValues().length;

        double[] doubleData = new double[numSamples];
        float[] values = data.getValues();
        for (int i = 0; i < values.length; i++) {
            doubleData[i] = values[i];
        }
        return new DoubleData(doubleData, data.getSampleRate(), data.getCollectTime(), data.getFirstSampleNumber());
    } 
    
    /**
     * Gets the target type.
     * 
     * @param extension the extension
     * 
     * @return the target type
     */
    private static AudioFileFormat.Type getTargetType(String extension) {
        AudioFileFormat.Type[] typesSupported = AudioSystem.getAudioFileTypes();

        for (AudioFileFormat.Type aTypesSupported : typesSupported) {
            if (aTypesSupported.getExtension().equals(extension)) {
                return aTypesSupported;
            }
        }
        return null;
    }
}
