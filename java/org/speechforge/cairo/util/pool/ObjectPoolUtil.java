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
package org.speechforge.cairo.util.pool;

import org.apache.commons.pool.impl.GenericObjectPool;

/**
 * Provides utility methods for working with the Jakarta {@code commons-pool} library.
 *
 * @author Niels Godfredsen {@literal <}<a href="mailto:ngodfredsen@users.sourceforge.net">ngodfredsen@users.sourceforge.net</a>{@literal >}
 */
public class ObjectPoolUtil {

    /**
     * Make default constructor private to prevent instantiation.
     */
    private ObjectPoolUtil() {
        super();
    }

    /**
     * TODOC
     * @param maxActive
     * @return
     */
    public static GenericObjectPool.Config getGenericObjectPoolConfig(int maxActive) {
        GenericObjectPool.Config config = new GenericObjectPool.Config();

        config.maxActive                        = maxActive;
        config.maxIdle                          = -1;
        config.maxWait                          = 200;
        config.minEvictableIdleTimeMillis       = -1;
        config.minIdle                          = config.maxActive;
        config.numTestsPerEvictionRun           = -1;
        //config.softMinEvictableIdleTimeMillis   = -1;
        config.testOnBorrow                     = false;
        config.testOnReturn                     = false;
        config.testWhileIdle                    = false;
        config.timeBetweenEvictionRunsMillis    = -1;
        config.whenExhaustedAction              = GenericObjectPool.WHEN_EXHAUSTED_FAIL;

        return config;
    }

}
