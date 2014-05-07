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
package org.speechforge.cairo.server;

import org.speechforge.cairo.exception.UnsupportedHeaderException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;
import org.mrcp4j.MrcpRequestState;
import org.mrcp4j.message.MrcpResponse;
import org.mrcp4j.message.header.IllegalValueException;
import org.mrcp4j.message.header.MrcpHeader;
import org.mrcp4j.message.header.MrcpHeaderName;
import org.mrcp4j.message.request.MrcpRequest;
import org.mrcp4j.message.request.MrcpRequestFactory.UnimplementedRequest;
import org.mrcp4j.server.MrcpSession;
import org.mrcp4j.server.provider.GenericRequestHandler;

/**
 * Abstract base class for specific MRCPv2 channel implementations.
 * 
 * @author Niels Godfredsen {@literal <}<a href="mailto:ngodfredsen@users.sourceforge.net">ngodfredsen@users.sourceforge.net</a>{@literal >}
 */
public abstract class MrcpGenericChannel implements GenericRequestHandler {

    private static Logger _logger = Logger.getLogger(MrcpGenericChannel.class);

    private Map<String, Object> _params = new HashMap<String, Object>();

    /* (non-Javadoc)
     * @see org.mrcp4j.server.provider.GenericRequestHandler#setParams(org.mrcp4j.message.request.MrcpRequestFactory.UnimplementedRequest, org.mrcp4j.server.MrcpSession)
     */
    public MrcpResponse setParams(UnimplementedRequest request, MrcpSession session) {
        short statusCode = -1;
        boolean allSupported = true;
        List<MrcpHeader> badHeaders = new ArrayList<MrcpHeader>();
        Map<String, Object> validParams = new HashMap<String, Object>();
        for (MrcpHeader header : request.getHeaders()) {
            try {
                //String name = header.getName();
                MrcpHeaderName headerName = header.getHeaderName();
                if (headerName == null) {
                    throw new UnsupportedHeaderException("Unknown header name.");
                } else if (headerName.isGeneric()) {
                    validateGenericHeader(header);
                    validParams.put(headerName.toString(), header.getValueObject());
                } else {
                    if (validateParam(header)) {
                        validParams.put(headerName.toString(), header.getValueObject());
                    } else {
                        allSupported = false;
                    }
                }
            } catch (UnsupportedHeaderException e) {
                _logger.debug(e, e);
                if (statusCode != MrcpResponse.STATUS_ILLEGAL_VALUE_FOR_HEADER) {  // STATUS_ILLEGAL_VALUE_FOR_HEADER takes precedence
                    statusCode = MrcpResponse.STATUS_UNSUPPORTED_HEADER;
                }
                badHeaders.add(header);
            } catch (IllegalValueException e) {
                _logger.debug(e, e);
                statusCode = MrcpResponse.STATUS_ILLEGAL_VALUE_FOR_HEADER;
                badHeaders.add(header);
            }
        }

        if (statusCode < 0) { // all headers passed validation
            statusCode = allSupported ? MrcpResponse.STATUS_SUCCESS : MrcpResponse.STATUS_SUCCESS_SOME_OPTIONAL_HEADERS_IGNORED;
            _params.putAll(validParams);
            /*for (MrcpHeader header : request.getHeaders()) {
                String name = header.getNameString();
                if (!MrcpHeaderName.CHANNEL_IDENTIFIER.equals(header.getHeaderName())) {
                    try {
                        _params.put(name, header.getValueObject());
                    } catch (IllegalValueException e) {
                        statusCode = MrcpResponse.STATUS_ILLEGAL_VALUE_FOR_HEADER;
                        badHeaders.add(header);
                    }
                }
            }*/
        }

        MrcpResponse response = session.createResponse(statusCode, MrcpRequestState.COMPLETE);
        for (MrcpHeader header : badHeaders) {
            response.addHeader(header);
        }
        return response;
    }

    /**
     * @param header
     */
    private void validateGenericHeader(MrcpHeader header) {
        // TODO Auto-generated method stub
        
    }

    /* (non-Javadoc)
     * @see org.mrcp4j.server.provider.GenericRequestHandler#getParams(org.mrcp4j.message.request.MrcpRequestFactory.UnimplementedRequest, org.mrcp4j.server.MrcpSession)
     */
    public MrcpResponse getParams(UnimplementedRequest request, MrcpSession session) {
        // TODO Auto-generated method stub
        return null;
    }
    
    /**
     * Subclasses of this class should implement this method to check that the specified
     * header field-name and field-value is valid for the specific resource type implemented.
     * @param header header to be validated.
     * @return false if support for the header is optional and the resource chooses to ignore it.
     * @throws UnsupportedHeaderException if the parameter is unsupported for the resource type implemented.
     * @throws IllegalValueException if the parameter has an illegal value.
     */
    protected abstract boolean validateParam(MrcpHeader header) throws UnsupportedHeaderException, IllegalValueException;

    /**
     * @param name the name of a parameter to be set.
     * @param value the value of the parameter to be set.
     * /
    protected final void setParam(String name, String value) {
        _params.put(name, value);
    }*/

    /**
     * Determines the value for a parameter using the following precedence: value in current request,
     * value from previous SET-PARAMS request, default value.
     * @param name the header name of the parameter for which the value is desired.
     * @param request current request message which may or may not contain the desired header value.
     * @param defaultValue the default value to be used if the parameter is not in the current request nor has been set by a previous SET-PARAMS request. 
     * @return the value of the parameter from the current request, from a previous SET-PARAMS request, or the default value in that order of precedence.
     * @throws IllegalValueException if an illegal value is specified in the current request.
     */
    protected final Object getParam(MrcpHeaderName name, MrcpRequest request, Object defaultValue) throws IllegalValueException {
        Object param = null;

        MrcpHeader header = request.getHeader(name);
        if (header != null) {
            param = header.getValueObject();
        }

        if (param == null) {
            param = getParam(name);
        }

        if (param == null) {
            param = defaultValue;
        }
        
        return param;

    }
    
    /**
     * @param name the header name of the parameter for which the value is desired.
     * @return the value of the parameter or null if no value has been set.
     */
    protected final Object getParam(MrcpHeaderName name) {
        return getParam(name.toString());
    }

    /**
     * @param name the name of the parameter for which the value is desired.
     * @return the value of the parameter or null if no value has been set.
     */
    protected final Object getParam(String name) {
        return _params.get(name);
    }

}
