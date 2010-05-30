<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" 
xmlns:msxsl="urn:schemas-microsoft-com:xslt"
xmlns:cndoc="http://www.scriptcalendar.com">


	<xsl:variable name="varDebug" select="//events/@debugmode" />

	<xsl:template match="/">
		<xsl:choose>
		<xsl:when test="events/@mode='editor' ">
			<xsl:apply-templates select="events" mode="editor"/>
		</xsl:when>
		<xsl:when test="events/@mode='js' ">
			<xsl:apply-templates select="events" mode="js"/>
		</xsl:when>
		</xsl:choose>
	</xsl:template>

	<!--
	************************************************************
	***  Misc templates
	************************************************************
	-->
	<xsl:template name="tonull">
		<xsl:param name="value"/>
		<xsl:param name="datatype"/>
		<xsl:param name="shownull"/>

		<xsl:choose>
		<xsl:when test="string-length($value)=0">
			<xsl:choose>
			<xsl:when test="$shownull=0"></xsl:when>
			<xsl:when test="$shownull=1">null</xsl:when>
			</xsl:choose>
		</xsl:when>
		<xsl:otherwise>
			<xsl:choose>
			<xsl:when test="$datatype='integer'"><xsl:value-of select="$value"/></xsl:when>
			<xsl:when test="$datatype='string'">"<xsl:value-of select="$value"/>"</xsl:when>
			</xsl:choose>
		</xsl:otherwise>
		</xsl:choose>
	</xsl:template>

	<xsl:template name="pad">
		<xsl:param name="data"/>
		
		<xsl:choose>
		<xsl:when test="string-length($data)=0">00</xsl:when>
		<xsl:when test="string-length($data)=1"><xsl:value-of select="concat('0', $data)"/></xsl:when>
		<xsl:otherwise><xsl:value-of select="$data"/></xsl:otherwise>
		</xsl:choose>
	</xsl:template>
	
	<xsl:template name="setmaxlength">
		<xsl:param name="data" />
		<xsl:param name="maxlen" />

		<xsl:choose>
		<xsl:when test="string-length($data) &gt; $maxlen"><xsl:value-of select="substring($data, 1, $maxlen)"/></xsl:when>
		<xsl:otherwise><xsl:value-of select="$data"/></xsl:otherwise>
		</xsl:choose>
	</xsl:template>

	<!--
	************************************************************
	***  Editor templates
	************************************************************
	-->
	<xsl:template match="events" mode="editor">
		

	   	<table border="0" cellpadding="5" cellspacing="2">
			<tr>
				<xsl:if test="$varDebug='-1'">
					<th class="tabletext" bgcolor="99ccff">index</th>
				</xsl:if>
				<th class="tabletext" bgcolor="99ccff">m</th>
				<th class="tabletext" bgcolor="99ccff">d</th>
				<th class="tabletext" bgcolor="99ccff">y</th>
				<th class="tabletext" bgcolor="99ccff">text</th>
				<th class="tabletext" bgcolor="99ccff">popuplink</th>
				<th class="tabletext" bgcolor="99ccff">style</th>
				<th class="tabletext" bgcolor="99ccff">tooltip</th>
				<th class="tabletext" bgcolor="99ccff">script</th>
				<th class="tabletext" bgcolor="99ccff">filter</th>
			</tr>

			<xsl:apply-templates select="event" mode="editor">
				<xsl:sort select="@sortvalue" data-type="number" order="ascending"/>
			</xsl:apply-templates>
		</table>
	</xsl:template>

	<xsl:template match="event" mode="editor">
		<xsl:variable name="maxlen" select="30"/>
		<xsl:variable name="strtext"><xsl:call-template name="setmaxlength"><xsl:with-param name="data" select="text"/><xsl:with-param name="maxlen" select="$maxlen"/></xsl:call-template></xsl:variable>
		<xsl:variable name="strpopuplink"><xsl:call-template name="setmaxlength"><xsl:with-param name="data" select="popuplink"/><xsl:with-param name="maxlen" select="$maxlen"/></xsl:call-template></xsl:variable>
		<xsl:variable name="strstyle"><xsl:call-template name="setmaxlength"><xsl:with-param name="data" select="style"/><xsl:with-param name="maxlen" select="$maxlen"/></xsl:call-template></xsl:variable>
		<xsl:variable name="strtooltip"><xsl:call-template name="setmaxlength"><xsl:with-param name="data" select="tooltip"/><xsl:with-param name="maxlen" select="$maxlen"/></xsl:call-template></xsl:variable>
		<xsl:variable name="strscript"><xsl:call-template name="setmaxlength"><xsl:with-param name="data" select="script"/><xsl:with-param name="maxlen" select="$maxlen"/></xsl:call-template></xsl:variable>
		<xsl:variable name="strfilter"><xsl:call-template name="setmaxlength"><xsl:with-param name="data" select="filter"/><xsl:with-param name="maxlen" select="$maxlen"/></xsl:call-template></xsl:variable>
				
		<tr id="{@index}" name="{@index}" onClick="row_click(this);" bgcolor="#ffffff" >
			<xsl:if test="$varDebug='-1'">
				<td nowrap="true" valign="top" class="tableText" bgcolor="#e0e0e0" ><xsl:value-of select="@index"/></td>
			</xsl:if>
			
			<td nowrap="true" valign="top" class="tableText"><xsl:value-of select="month"/></td>
			<td nowrap="true" valign="top" class="tableText"><xsl:value-of select="day"/></td>
			<td nowrap="true" valign="top" class="tableText"><xsl:value-of select="year"/></td>
			<td nowrap="true" valign="top" class="tableText"><xsl:value-of select="$strtext"/></td>
			<td nowrap="true" valign="top" class="tableText"><xsl:value-of select="$strpopuplink"/></td>
			<td nowrap="true" valign="top" class="tableText"><xsl:value-of select="$strstyle"/></td>
			<td nowrap="true" valign="top" class="tableText"><xsl:value-of select="$strtooltip"/></td>
			<td nowrap="true" valign="top" class="tableText"><xsl:value-of select="$strscript"/></td>
			<td nowrap="true" valign="top" class="tableText"><xsl:value-of select="$strfilter"/></td>
		</tr>
	</xsl:template>

	<!--
	************************************************************
	***  Convert to js templates
	************************************************************
	-->
	<xsl:template match="events" mode="js">

// ********* ********* ********* ********* ********* ********* ********* ********* *********
// Define Events
// call the fscEvent function
// 
// #  PARMS		DATA TYPE	DESCRIPTION
// 1  m			number		2 digit month (1=jan, 2=feb, 3=mar,... 12=dec)
// 2  d			number		2 digit day
// 3  y			number		4 digit year
// 4  text		date		HTML event text
// 5  link		string		URL for popup window
// 6  style		string		CSS class for the event (in-line style is invalid)
// 7  tooltip		string		text for hover over tooltip
// 8  script		string		javascript to execute during onMouseDown
// 9  filter		string		keyword to allow users to filter events
// ********* ********* ********* ********* ********* ********* ********* ********* *********

		<xsl:apply-templates select="event" mode="js">
			<xsl:sort select="@sortvalue" data-type="number" order="ascending"/>
		</xsl:apply-templates>
	</xsl:template>

	<xsl:template match="event" mode="js">
		<xsl:variable name="vDelimiter">, </xsl:variable>
		<xsl:variable name="vMonth">
			<xsl:call-template name="tonull">
				<xsl:with-param name="value" select="month"/>
				<xsl:with-param name="datatype">integer</xsl:with-param>
				<xsl:with-param name="shownull">1</xsl:with-param>
			</xsl:call-template>
		</xsl:variable>
		<xsl:variable name="vDay">
			<xsl:call-template name="tonull">
				<xsl:with-param name="value" select="day"/>
				<xsl:with-param name="datatype">integer</xsl:with-param>
				<xsl:with-param name="shownull">1</xsl:with-param>
			</xsl:call-template>
		</xsl:variable>			
		<xsl:variable name="vYear">
			<xsl:call-template name="tonull">
				<xsl:with-param name="value" select="year"/>
				<xsl:with-param name="datatype">integer</xsl:with-param>
				<xsl:with-param name="shownull">1</xsl:with-param>
			</xsl:call-template>
		</xsl:variable>			
		<xsl:variable name="vText">
			<xsl:call-template name="tonull">
				<xsl:with-param name="value" select="text"/>
				<xsl:with-param name="datatype">string</xsl:with-param>
				<xsl:with-param name="shownull">1</xsl:with-param>
			</xsl:call-template>
		</xsl:variable>			
		<xsl:variable name="vPopup">
			<xsl:call-template name="tonull">
				<xsl:with-param name="value" select="popuplink"/>
				<xsl:with-param name="datatype">string</xsl:with-param>
				<xsl:with-param name="shownull">1</xsl:with-param>
			</xsl:call-template>
		</xsl:variable>			
		<xsl:variable name="vStyle">
			<xsl:call-template name="tonull">
				<xsl:with-param name="value" select="style"/>
				<xsl:with-param name="datatype">string</xsl:with-param>
				<xsl:with-param name="shownull">1</xsl:with-param>
			</xsl:call-template>
		</xsl:variable>	
		<xsl:variable name="vTooltip">
			<xsl:call-template name="tonull">
				<xsl:with-param name="value" select="tooltip"/>
				<xsl:with-param name="datatype">string</xsl:with-param>
				<xsl:with-param name="shownull">1</xsl:with-param>
			</xsl:call-template>
		</xsl:variable>	
		<xsl:variable name="vScript">
			<xsl:call-template name="tonull">
				<xsl:with-param name="value" select="script"/>
				<xsl:with-param name="datatype">string</xsl:with-param>
				<xsl:with-param name="shownull">1</xsl:with-param>
			</xsl:call-template>
		</xsl:variable>	
		<xsl:variable name="vFilter">
			<xsl:call-template name="tonull">
				<xsl:with-param name="value" select="filter"/>
				<xsl:with-param name="datatype">string</xsl:with-param>
				<xsl:with-param name="shownull">1</xsl:with-param>
			</xsl:call-template>
		</xsl:variable>	
fscEvent( <xsl:value-of select="concat( $vMonth, $vDelimiter, $vDay, $vDelimiter, $vYear, $vDelimiter, $vText, $vDelimiter, $vPopup, $vDelimiter, $vStyle, $vDelimiter, $vTooltip, $vDelimiter, $vScript, $vDelimiter, $vFilter )" /> );
	</xsl:template>
</xsl:stylesheet>
