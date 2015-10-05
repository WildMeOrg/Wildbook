package fap.core.exceptions;

/**
 * General fap runtime exception.
 * @author Zoltan Geller
 * @version 18.07.2010.
 */
public class CoreRuntimeException extends RuntimeException {

	private static final long serialVersionUID = 1L;
	
	public CoreRuntimeException() {
		super();
	}
	
	public CoreRuntimeException(String msg) {
		super(msg);
	}

}
