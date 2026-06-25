package org.sola.common;

/**
 * Exception used to indicate MD5 mismatch
 */
public class SOLAMD5Exception extends SOLAException {
    public SOLAMD5Exception(String messageCode) {
        super(messageCode);
    }
    
    public SOLAMD5Exception(String messageCode, Object[] messageParameters) { //NOSONAR
        super(messageCode, messageParameters);
    }

    public SOLAMD5Exception(String messageCode, Throwable cause) {
        super(messageCode, cause);
    }

    public SOLAMD5Exception(String messageCode, Object[] messageParameters, Throwable cause) { //NOSONAR
        super(messageCode, messageParameters, cause);
    }
}
