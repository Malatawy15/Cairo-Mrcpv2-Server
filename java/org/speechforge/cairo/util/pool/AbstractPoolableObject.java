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

import org.apache.log4j.Logger;

/**
 * Provides default implementations for methods defined by {@link org.speechforge.cairo.util.pool.PoolableObject}.
 *
 * @author Niels Godfredsen {@literal <}<a href="mailto:ngodfredsen@users.sourceforge.net">ngodfredsen@users.sourceforge.net</a>{@literal >}
 */
public abstract class AbstractPoolableObject implements PoolableObject {

    private static Logger _logger = Logger.getLogger(AbstractPoolableObject.class);

    /* (non-Javadoc)
     * @see org.speechforge.cairo.util.pool.PoolableObject#activate()
     */
    public void activate() throws Exception {
        _logger.debug("activate()");
        return;
    }

    /* (non-Javadoc)
     * @see org.speechforge.cairo.util.pool.PoolableObject#passivate()
     */
    public void passivate() throws Exception {
        _logger.debug("passivate()");
        return;
    }

    /* (non-Javadoc)
     * @see org.speechforge.cairo.util.pool.PoolableObject#validate()
     */
    public boolean validate() {
        _logger.debug("validate(): returning true");
        return true;
    }

    /* (non-Javadoc)
     * @see org.speechforge.cairo.util.pool.PoolableObject#destroy()
     */
    public void destroy() throws Exception {
        _logger.debug("destroy()");
        return;
    }

}
