package org.ecocean.grid;


public class GridManagerFactory {

	private static GridManager gm;
	
	public synchronized static GridManager getGridManager() {

		try{
			if (gm==null) {
				
				gm=new GridManager();

			}
			return gm;
			}
		catch (Exception jdo){
			jdo.printStackTrace();
			System.out.println("I couldn't instantiate a gridManager.");
			return null;
			}
		}
	
}
