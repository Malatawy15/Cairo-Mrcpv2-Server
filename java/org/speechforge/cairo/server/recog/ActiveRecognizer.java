package org.speechforge.cairo.server.recog;

import java.io.IOException;
import java.io.UnsupportedEncodingException;

import javax.media.protocol.PushBufferDataSource;
import javax.speech.recognition.GrammarException;

import org.apache.commons.pool.ObjectPool;
import org.apache.log4j.Logger;
import org.speechforge.cairo.server.recog.sphinx.SphinxRecEngine;
import org.speechforge.cairo.server.recog.sphinx.SphinxRecEngineJSGF;

public class ActiveRecognizer {

	static Logger _logger = Logger.getLogger(ActiveRecognizer.class);

	private ObjectPool _recPool;
	private Object _recEngine;
	private String _appType;

	public ActiveRecognizer(ObjectPool recPool, Object recEngine, String appType) {
		_recPool = recPool;
		_recEngine = recEngine;
		_appType = appType;
	}

	public void startRecognition(PushBufferDataSource dataSource,
			RecogListener recogListener) throws UnsupportedEncodingException {
		if (_appType.equals("application/jsgf")) {
			((SphinxRecEngineJSGF) _recEngine).startRecognition(dataSource,
					recogListener);
			((SphinxRecEngineJSGF) _recEngine).startRecogThread();
		}
	}

	public void loadLM(GrammarLocation grammarLocation)
			throws GrammarException, IOException {
		if (_appType.equals("application/jsgf")) {
			((SphinxRecEngineJSGF) _recEngine).load(grammarLocation);
		}
	}
	
	public void deallocateLM(){
		if (_appType.equals("application/jsgf")) {
			//((SphinxRecEngineJSGF) _recEngine).deallocateJSGF();
		}
	}

	public void setHotword(boolean hotword) {
		if (_appType.equals("application/jsgf")) {
			((SphinxRecEngineJSGF) _recEngine).setHotword(hotword);
		}
	}

	public void returnRecEngine() throws Exception {
		deallocateLM();
		_recPool.returnObject(_recEngine);
	}

}
