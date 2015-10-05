package fap.core.series;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import fap.core.data.DataPointSerie;

/**
 * One serie of data which is to be processes.
 * @author Aleksa Todorovic, Zoltan Geller
 * @version 30.07.2010.
 */
public class Serie {

	/**
	 * The label(class) of the serie.
	 */
	private double label;

	/**
	 * Input data serie which is bound to this serie.
	 */
	private DataPointSerie data;
	
	/**
	 * Collection of representations for this serie.
	 */
	private Map<Class<? extends SerieRepresentation>, SerieRepresentation> representations;

	/**
	 * Default constructor.
	 */
	public Serie() {}
	
	/**
	 * Constructor
	 * @param data input data for this serie
	 */
	public Serie(DataPointSerie data) {
		this.data = data;
		this.representations = new HashMap<Class<? extends SerieRepresentation>, SerieRepresentation>();
	}

	/**
	 * Constructor
	 * @param data input data for this serie
	 * @param label label of this serie
	 */
	public Serie(DataPointSerie data, double label) {
		this.data = data;
		this.label = label;
		this.representations = new HashMap<Class<? extends SerieRepresentation>, SerieRepresentation>();
	}
	
	/**
	 * Sets the input data bound to this serie.
	 * @param data the input data for this serie
	 */
	public void setData(DataPointSerie data) {
		this.data = data;
	}
	
	/**
	 * Access input data bound to this serie.
	 * @return input data for this serie
	 */
	public DataPointSerie getData() {
		return data;
	}
	
	/**
	 * Returns the size of the time serie.
	 * @return the size of the serie
	 */
	public int getLength() {
		return data.getPointsCount();
	}

	/**
	 * Adds new representation of this serie. Overwrites previous representation of the same representation class.
	 * @param repr representation to add
	 */
	public void addRepr(SerieRepresentation repr) {
		representations.put(repr.getClass(), repr);
	}
	
	/**
	 * Removes specific representation
	 * @param <T> representation type to remove 
	 * @param reprClass class of representation type to remove
	 */
	public <T extends SerieRepresentation> void removeRepr(Class<T> reprClass) {
		representations.remove(reprClass);
	}
	
	/**
	 * Access specific representation of serie.
	 * @param <T> representation type
	 * @param reprClass class of representation type
	 * @return representation of specific type, or null if one doesn't exist
	 */
	@SuppressWarnings("unchecked")
	public <T extends SerieRepresentation> T getRepr(Class<T> reprClass) {
		return (T) representations.get(reprClass);
	}

	/**
	 * Access all representations of serie.
	 * @return all representations of serie
	 */
	public Collection<SerieRepresentation> getAllReprs() {
		return representations.values();
	}
	
	/**
	 * Returns the label of the serie.
	 * @return label label of the serie
	 */
	public double getLabel() 
	{ 
		return label; 
	}
	
	/**
	 * Sets the label of the serie.
	 * @param label the new label index
	 */
	public void setLabel(double label)
	{
		this.label = label;
	}
	
	/**
	 * Returns the mean.
	 * @return mean
	 */
	public double getMean()
	{
		double mean = 0;
		int count = data.getPointsCount();
		for (int i=0; i<count; i++)
			mean += data.getPoint(i).getY();
		return mean/count;
	}
	
	/**
	 * Returns the standard deviation.
	 * @return standard deviation
	 */
	public double getStDev()
	{
		double mean = getMean();
		double stdev = 0;
		int count = data.getPointsCount();
		for (int i=0; i<count; i++)
		{
			double y = data.getPoint(i).getY();
			stdev += (mean-y)*(mean-y);
		}
		return Math.sqrt(stdev/count);
	}

}
