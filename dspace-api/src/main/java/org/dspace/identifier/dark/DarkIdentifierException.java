/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.identifier.dark;

import org.dspace.identifier.IdentifierException;

/**
 * Exception used by dARK identifier components.
 */
public class DarkIdentifierException extends IdentifierException {

    public static final int CODE_NOT_SET = 0;
    public static final int DARK_DOES_NOT_EXIST = 1;
    public static final int DARK_ALREADY_EXISTS = 2;
    public static final int FOREIGN_DARK = 3;
    public static final int BAD_ANSWER = 4;
    public static final int BAD_REQUEST = 5;
    public static final int AUTHENTICATION_ERROR = 6;
    public static final int INTERNAL_ERROR = 7;
    public static final int CONVERSION_ERROR = 8;
    public static final int MISMATCH = 9;
    public static final int UNRECOGNIZED = 10;
    public static final int UNAUTHORIZED_METADATA_MANIPULATION = 11;
    public static final int DARK_IS_TOMBSTONED = 12;

    private final int code;

    public DarkIdentifierException() {
        super();
        this.code = CODE_NOT_SET;
    }

    public DarkIdentifierException(String message) {
        super(message);
        this.code = CODE_NOT_SET;
    }

    public DarkIdentifierException(String message, int code) {
        super(message);
        this.code = code;
    }

    public DarkIdentifierException(String message, Throwable cause) {
        super(message, cause);
        this.code = CODE_NOT_SET;
    }

    public DarkIdentifierException(String message, Throwable cause, int code) {
        super(message, cause);
        this.code = code;
    }

    public DarkIdentifierException(Throwable cause) {
        super(cause);
        this.code = CODE_NOT_SET;
    }

    public int getCode() {
        return code;
    }
}
