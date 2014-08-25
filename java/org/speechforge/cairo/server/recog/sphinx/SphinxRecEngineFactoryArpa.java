package org.speechforge.cairo.server.recog.sphinx;

import java.net.URL;

import org.speechforge.cairo.util.pool.PoolableObject;

public class SphinxRecEngineFactoryArpa extends SphinxRecEngineFactory {
	
    public SphinxRecEngineFactoryArpa(URL sphinxConfigURL) {
		super(sphinxConfigURL);
		// TODO Auto-generated constructor stub
	}

	public PoolableObject makeObject() throws Exception {
        return new SphinxRecEngineARPA(super._cm, super.id++);
    }
    
}
