package org.sola.common;

/**
 * Exception used to indicate missing data to save
 */
public class SOLANoDataException extends SOLAException {
    public SOLANoDataException(String messageCode) {
        super(messageCode);
    }
    
    public SOLANoDataException(String messageCode, Object[] messageParameters) { //NOSONAR
        super(messageCode, messageParameters);
    }

    public SOLANoDataException(String messageCode, Throwable cause) {
        super(messageCode, cause);
    }

    public SOLANoDataException(String messageCode, Object[] messageParameters, Throwable cause) { //NOSONAR
        super(messageCode, messageParameters, cause);
    }
}
