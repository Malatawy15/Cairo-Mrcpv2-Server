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

/**
 * Defines methods required for an object to be pooled using {@link org.speechforge.cairo.util.pool.AbstractPoolableObjectFactory}.
 *
 * @author Niels Godfredsen {@literal <}<a href="mailto:ngodfredsen@users.sourceforge.net">ngodfredsen@users.sourceforge.net</a>{@literal >}
 * @see org.speechforge.cairo.util.pool.AbstractPoolableObjectFactory
 * @see org.speechforge.cairo.util.pool.AbstractPoolableObject
 */
public interface PoolableObject {

    /**
     * Reinitialize instance to be returned by the pool.
     * @throws Exception 
     */
    void activate() throws Exception;

    /**
     * Uninitialize instance to be returned to the pool.
     * @throws Exception 
     */
    void passivate() throws Exception;

    /**
     * Ensures that the instance is safe to be returned by the pool.  Returns false if this object should be destroyed.
     * @return {@code false} if this instance is not valid and should be dropped from the pool, {@code true} otherwise.
     */
    boolean validate();

    /**
     * Destroys an instance no longer needed by the pool.
     * @throws Exception 
     */
    void destroy() throws Exception;


}
