package org.speechforge.cairo.server.recog.sphinx;

import java.net.URL;

import org.apache.commons.pool.ObjectPool;
import org.apache.commons.pool.PoolableObjectFactory;
import org.apache.commons.pool.impl.GenericObjectPool;
import org.apache.log4j.Logger;
import org.speechforge.cairo.util.pool.ObjectPoolUtil;
import org.speechforge.cairo.util.pool.PoolableObject;

public class SphinxRecEngineFactoryJsgf extends SphinxRecEngineFactory {
	
	private static Logger _logger = Logger.getLogger(SphinxRecEngineFactoryJsgf.class);
	
	public SphinxRecEngineFactoryJsgf(URL sphinxConfigURL) {
		super(sphinxConfigURL);
		// TODO Auto-generated constructor stub
	}

	public PoolableObject makeObject() throws Exception {
        return new SphinxRecEngineJSGF(super._cm, super.id++);
    }
	
	public static ObjectPool createObjectPool(URL sphinxConfigURL, int instances) 
			throws InstantiationException {	        
	    if (_logger.isDebugEnabled()) {
	        _logger.debug("creating new rec engine pool... instances: " + instances);
	    }
	
	    PoolableObjectFactory factory = new SphinxRecEngineFactoryJsgf(sphinxConfigURL);
	    GenericObjectPool.Config config = ObjectPoolUtil.getGenericObjectPoolConfig(instances);
	
	    ObjectPool objectPool = new GenericObjectPool(factory, config);
	    initPool(objectPool);
	    return objectPool;
	}
	
}
