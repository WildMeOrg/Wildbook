package fap.core.preprocessing;

import fap.core.series.Serie;

public interface PreprocessingTransformation {

	public Serie performTransformation(Serie serie);
	
}
