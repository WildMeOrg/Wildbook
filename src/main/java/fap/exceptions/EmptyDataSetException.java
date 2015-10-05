package fap.exceptions;

import fap.core.exceptions.CoreRuntimeException;

/**
 * Empty training set exception
 * @author Zoltan Gelelr
 * @version 14.07.2010.
 */
public class EmptyDataSetException extends CoreRuntimeException {

	private static final long serialVersionUID = 1L;
	
	public EmptyDataSetException(String msg) {
		super(msg);
	}
	
	public EmptyDataSetException() {
		super();
	}
	
}
