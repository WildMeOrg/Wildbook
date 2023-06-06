
<h1><a href="https://www.wildbook.org"><img style="float: left;" align="middle" width="225px" height="225px" src="https://raw.githubusercontent.com/WildbookOrg/Wildbook/master/src/main/webapp/cust/mantamatcher/img/wildbook_logo.png"></a>Wildbook</h1>

Wildbook&reg; is an open source software framework to support mark-recapture, molecular ecology, and social ecology studies. The biological and statistical communities already support a number of excellent tools, such as Program MARK,GenAlEx, and SOCPROG for use in analyzing wildlife data. Wildbook is a complementary software application that:

-provides a scalable and collaborative platform for intelligent wildlife data storage and management, including advanced, consolidated searching

-provides an easy-to-use software suite of functionality that can be extended to meet the needs of wildlife projects, especially where individual identification is used

-provides an API to support the easy export of data to cross-disciplinary analysis applications (e.g., GenePop ) and other software (e.g., Google Earth)

-provides a platform that supports the exposure of data in biodiversity databases (e.g., GBIF and OBIS)

-provides a platform for animal biometrics that supports easy data access and facilitates matching application deployment for multiple species

<h2>Wildbook IA - Formerly IBEIS</h2>

<img width="125px" height="*" align="left" src="src/main/webapp/images/wild-me-logo-only-100-100.png" /> Wildbook is the data management layer for the <a href="https://github.com/WildbookOrg/wildbook-ia">Wildbook IA (WBIA)</a>. The WBIA project is the successor to the Image-Based Ecological Information System (IBEIS) computer vision research platform, which pulls data from Wildbook servers to detect features in images and identify individual animals. WBIA brings massive-scale computer vision to wildlife research for the first time. 
<br />
<h2>Support</h2>

Please see <a href="https://docs.wildme.org">Wildbook.org</a> for documentation. 

Need direct help?

Wild Me (wildme.org) engineering staff provide support for Wildbook. You can contact us at: support@wildme.org

We provide support during regular office hours on Mondays and Tuesdays.

Support resources include:
<ul>
<li><a href="http://docs.wildme.org">Wildbook Wiki - User Manual, Field Guide, & Documentation</a></li>
<li><a href="https://community.wildme.org">Community & Developer Support</a></li>
<li><a href="https://www.wildme.org/services">Wild Me Professional Services</a></li>
</ul>

<h2>Want to contribute code?</h2>
<h3>Variable naming conventions</h3>
<ul>
  <li>Camel case</li>
  <li>Please don’t use single-letter variable names (no matter how temporary you think the code is)</li>
  <li>Avoid comments; code should be clear enough to speak for itself in almost all cases</li>
  <li>Code for clarity rather than for efficiency (one-liners are cool, but not at the expense of future obfuscation)</li>
</ul>

<h3>Overall outline of code framework</h3>
Spell out how .jsp files relate to servlet files relate to java files, etc. Someone new to the codebase should be able to orient themselves based on your notes.

<h3>Java/jsp style</h3>
Initialize variables and type signatures at the abstract/interface level when possible.

Instead of:

```
ArrayList encounters = new ArrayList<Encounter>();
...
public int getMax(ArrayList<int> numbers) {
```

Try:

```
List encounters = new ArrayList<Encounter>();
...
public int getMax(Collection<int> numbers) {
```

First of all, it’s easier to read and more intuitive for a function to take a Map or List than a HashMap or ArrayList.

The List interface defines how we want that variable to behave, and whether it’s an ArrayList or LinkedList is incidental. Keeping the variable and method signatures abstract means we can change the implementation later (eg swapping ArrayList->LinkedList) without changing the rest of our code.
https://stackoverflow.com/questions/2279030/type-list-vs-type-arraylist-in-java

Related: when writing utility methods, making the input type as abstract as possible makes the method versatile. See Util.asSortedList in Wildbook: since the input is an abstract Collection, it can accept a List, Set, PriorityQueue, or Vector as input, and return a sorted List.

Runtime (not style): Use Sets (not Lists or arrays) if you’re only keeping track of collection membership / item uniqueness. 

Instead of:

```
    	List<MarkedIndividual> uniqueIndividuals = new ArrayList<MarkedIndividual>();
    	for(Encounter currentEncounter: encounters){
		MarkedIndividual currentInd = enc.getIndividual();
		if !(uniqueIndividuals.contains(currentInd) {
			uniqueIndividuals.add(currentInd);
			doStuff();
```
      			
Try:

```
Set<MarkedIndividual> uniqueIndividuals = new HashSet<MarkedIndividual>();	
    	for(Encounter currentEncounter: encounters){
		MarkedIndividual currentInd = enc.getIndividual();
		if !(uniqueIndividuals.contains(currentInd) {
			uniqueIndividuals.add(currentInd);
			doStuff();
```

The reason is a little deep in the data types. Sets are defined as unordered collections of unique elements; and Lists/arrays are ordered collections with no bearing on element-uniqueness. If the order of a collection doesn’t matter and you’re just checking membership, you’ll have faster runtime using a Set.

Sets implement contains, add, and remove methods much faster than lists [contains is O(log(n)) vs O(n) runtime]. A list has to iterate through the entire list every time it runs contains (it checks each item once at a time) while a set (especially a HashSet) keeps track of an item index for quick lookup.


Use for-each loops aka “enhanced for loops” to make loops more concise and readable.

Instead of:

```
for (int i=0; i<encounters.length(); i++) {
	Encounter enc = encounters.get(i)
	doStuff();
```

try:

```
for (Encounter enc: encounters) {
	doStuff();
```

Note that in both cases you might want to check if `encounters == null` if relevant, but you rarely need to check if `encounters.length()>0` because the for-loops take care of that.

Also note that if you want access to the `i` variable for logging or otherwise, the classic for-loop is best.


`Util.stringExists` is shorthand for a common string check:

Instead of:

```
	if (str!=Null && !str.equals("")) {
		doStuff();
```
 
Try:

```
	if (Util.stringExists(str)) {
		doStuff();
```

This method also checks for the strings “none” and “unknown” which have given us trouble in displays in the past.

<h2>History</h2>
Wildbook started as a collaborative software platform for globally-coordinated whale shark (Rhincodon typus ) research as deployed in the Wildbook for Whale Sharks (now part of http://www.sharkbook.ai). After many requests to use our software outside of whale shark research, it is now an open source, community-maintained standard for mark-recapture studies.


<p><img style="float: right;" align="middle" src="src/main/webapp/images/wild-me-logo-only-100-100.png"> Wildbook is a registered trademark of Wild Me, a 501(c)(3) non-profit organization.</p> https://www.wildme.org
