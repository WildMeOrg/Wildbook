package fap.input;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

import fap.core.data.DataPoint;
import fap.core.data.DataPointSerie;
import fap.core.data.DataPointSerieArray;
import fap.core.input.CSVDataPointFactory;
import fap.core.series.Serie;
import fap.core.series.SerieList;


public class SerieFileReader {
	
	private String fname;
	private char separator;
	
	public SerieFileReader(String fname, char separator)
	{
		this.fname = fname;
		this.separator = separator;
	}
	
	public String getFName()
	{
		return fname;
	}
	
	public char getSeparator()
	{
		return separator;
	}
	
	public SerieList<Serie> load() throws IOException
	{
		SerieList<Serie> list = new SerieList<Serie>();
		BufferedReader input = new BufferedReader(new FileReader(new File(fname)));
		try {
			String line = input.readLine();
			while ( line != null )
			{
				CSVDataPointFactory dpf = new CSVDataPointFactory(line,separator,false);
				DataPointSerie dps = new DataPointSerieArray();
				double label=0;
				boolean notEmpty=false;
				// label
				if (dpf.hasNextPoint())
				{
					DataPoint dp = dpf.nextPoint();
					label = dp.getY();
					notEmpty=true;
				}
				// data
				while (dpf.hasNextPoint())
				{
					DataPoint dp = dpf.nextPoint();
					dps.addPoint(dp);
				}
				if (notEmpty)
				{
					Serie ls = new Serie(dps,label);
					list.add(ls);
				}
				line = input.readLine();
			}
		}
		finally
		{
			input.close();
		}
		return list;
	}

}
