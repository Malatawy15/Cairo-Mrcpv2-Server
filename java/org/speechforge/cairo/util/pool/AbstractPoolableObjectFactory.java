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

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

import org.apache.commons.pool.ObjectPool;
import org.apache.commons.pool.PoolableObjectFactory;
import org.apache.log4j.Logger;

/**
 * Generic implementation of {@code org.apache.commons.pool.PoolableObjectFactory} which constructs
 * {@link org.speechforge.cairo.util.pool.PoolableObject} instances and delegates all object pool
 * life-cycle methods to the pooled objects themselves.
 *
 * @author Niels Godfredsen {@literal <}<a href="mailto:ngodfredsen@users.sourceforge.net">ngodfredsen@users.sourceforge.net</a>{@literal >}
 * @see org.speechforge.cairo.util.pool.PoolableObject
 */
public abstract class AbstractPoolableObjectFactory implements PoolableObjectFactory {

    private static Logger _logger = Logger.getLogger(AbstractPoolableObjectFactory.class);


    /* (non-Javadoc)
     * @see org.apache.commons.pool.PoolableObjectFactory#makeObject()
     */
    public abstract PoolableObject makeObject() throws Exception;

    /* (non-Javadoc)
     * @see org.apache.commons.pool.PoolableObjectFactory#activateObject(java.lang.Object)
     */
    public void activateObject(Object object) throws Exception {
        PoolableObject poolableObject = (PoolableObject) object;
        poolableObject.activate();
    }

    /* (non-Javadoc)
     * @see org.apache.commons.pool.PoolableObjectFactory#passivateObject(java.lang.Object)
     */
    public void passivateObject(Object object) throws Exception {
        PoolableObject poolableObject = (PoolableObject) object;
        poolableObject.passivate();
    }

    /* (non-Javadoc)
     * @see org.apache.commons.pool.PoolableObjectFactory#validateObject(java.lang.Object)
     */
    public boolean validateObject(Object object) {
        PoolableObject poolableObject = (PoolableObject) object;
        return poolableObject.validate();
    }

    /* (non-Javadoc)
     * @see org.apache.commons.pool.PoolableObjectFactory#destroyObject(java.lang.Object)
     */
    public void destroyObject(Object object) throws Exception {
        PoolableObject poolableObject = (PoolableObject) object;
        poolableObject.destroy();
    }

    /**
     * Initializes an {@link org.apache.commons.pool.ObjectPool} by borrowing each object from the
     * pool (thereby triggering activation) and then returning all the objects back to the pool. 
     * @param pool the object pool to be initialized.
     * @throws InstantiationException if borrowing (or returning) an object from the pool triggers an exception.
     */
    public static void initPool(ObjectPool pool) throws InstantiationException {
        try {
            List<Object> objects = new ArrayList<Object>();
            while (true) try {
                objects.add(pool.borrowObject());
            } catch (NoSuchElementException e){
                // ignore, max active reached
                break;
            }
            for (Object obj : objects) {
                pool.returnObject(obj);
            }
        } catch (Exception e) {
            try {
                pool.close();
            } catch (Exception e1) {
                _logger.warn("Encounter expception while attempting to close object pool!", e1);
            }
            throw (InstantiationException) new InstantiationException(e.getMessage()).initCause(e);
        }

    }

}
