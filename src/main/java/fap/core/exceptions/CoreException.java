package fap.core.exceptions;

/**
 * General fap exception.
 * @author Zoltan Geller
 * @version 18.07.2010.
 */
public class CoreException extends Exception {

	private static final long serialVersionUID = 1L;
	
	public CoreException(String msg) {
		super(msg);
	}

	public CoreException() {
		super();
	}
}
