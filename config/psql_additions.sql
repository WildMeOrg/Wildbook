

--until i figure out how to do compound pk in datanucleus  :(
ALTER TABLE "IBEISIAIDENTIFICATIONMATCHINGSTATE" ADD CONSTRAINT ibeismatchingstateids UNIQUE("ANNID1", "ANNID2");
