package org.speechforge.cairo.server.recog;

import java.io.UnsupportedEncodingException;
import java.util.HashMap;

import javax.media.protocol.PushBufferDataSource;

import org.apache.commons.pool.ObjectPool;
import org.apache.log4j.Logger;
import org.speechforge.cairo.server.config.ReceiverConfig;
import org.speechforge.cairo.server.recog.sphinx.SphinxRecEngineFactory;

public class RecogInterface {

	static Logger _logger = Logger.getLogger(RecogInterface.class);

	private HashMap<String, ObjectPool> _recPools;
	private ActiveRecognizer _activeRecog;
	private ReceiverConfig _config;

	public RecogInterface(ReceiverConfig config) {
		_recPools = new HashMap<String, ObjectPool>();
		_config = config;
	}

	public void startRecognition(PushBufferDataSource dataSource,
			RecogListener recogListener) throws UnsupportedEncodingException {
		_activeRecog.startRecognition(dataSource, recogListener);
	}

	public void startSphinxGrammar() {

	}

	public void activateRecEngine(String appType,
			GrammarLocation grammarLocation, boolean hotword) throws Exception {
		if (_recPools.containsKey(appType) == false) {
			try {
				_recPools.put(
						appType,
						SphinxRecEngineFactory.createObjectPool(
								_config.getSphinxConfigURL(),
								_config.getEngines()));
			} catch (InstantiationException e) {
				e.printStackTrace();
			}
		}
		ObjectPool recPool = _recPools.get(appType);
		_activeRecog = new ActiveRecognizer(recPool, recPool.borrowObject(),
				appType);
		_logger.debug("Loading grammar...");
		_activeRecog.loadLM(grammarLocation);
		_activeRecog.setHotword(hotword);
	}

	public void returnRecEngine() {
		if (_activeRecog != null) {
			_logger.debug("Returning recengine to pool...");
			try {
				_activeRecog.returnRecEngine();
			} catch (Exception e) {
				_logger.debug(e, e);
			}
			_activeRecog = null;
		} else {
			_logger.warn("No recengine to return to pool!");
		}
	}

}