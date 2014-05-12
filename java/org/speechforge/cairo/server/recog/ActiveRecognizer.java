package org.speechforge.cairo.server.recog;

import java.io.IOException;
import java.io.UnsupportedEncodingException;

import javax.media.protocol.PushBufferDataSource;
import javax.speech.recognition.GrammarException;

import org.apache.commons.pool.ObjectPool;
import org.apache.log4j.Logger;
import org.speechforge.cairo.server.recog.sphinx.SphinxRecEngine;

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
		((SphinxRecEngine) _recEngine).startRecognition(dataSource,
				recogListener);
		((SphinxRecEngine) _recEngine).startRecogThread();
	}

	public void loadLM(GrammarLocation grammarLocation)
			throws GrammarException, IOException {
		if (_appType == "application/jsgf") {
			((SphinxRecEngine) _recEngine).loadJSGF(grammarLocation);
		}
	}

	public void setHotword(boolean hotword) {
		((SphinxRecEngine) _recEngine).setHotword(hotword);
	}

	public void returnRecEngine() throws Exception {
		_recPool.returnObject(_recEngine);
	}

}
