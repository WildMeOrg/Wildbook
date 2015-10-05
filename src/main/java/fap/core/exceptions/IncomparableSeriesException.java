package fap.core.exceptions;


/**
 * IncomparableException (two timer series are incomparable with the given SimilarityComparator)
 * @author Zoltan Geller
 * @version 18.07.2010.
 */
public class IncomparableSeriesException extends CoreRuntimeException {

	private static final long serialVersionUID = 1L;
	
	public IncomparableSeriesException(String msg) {
		super(msg);
	}
	
	public IncomparableSeriesException() {
		super();
	}

}
