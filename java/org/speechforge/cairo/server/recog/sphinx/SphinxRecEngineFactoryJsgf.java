package org.speechforge.cairo.server.recog.sphinx;

import java.net.URL;

import org.speechforge.cairo.util.pool.PoolableObject;

public class SphinxRecEngineFactoryJsgf extends SphinxRecEngineFactory {
	public SphinxRecEngineFactoryJsgf(URL sphinxConfigURL) {
		super(sphinxConfigURL);
		// TODO Auto-generated constructor stub
	}

	public PoolableObject makeObject() throws Exception {
        return new SphinxRecEngineJSGF(super._cm, super.id++);
    }
}
