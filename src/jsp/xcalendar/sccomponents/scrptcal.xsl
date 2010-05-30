<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" 
xmlns:msxsl="urn:schemas-microsoft-com:xslt"
xmlns:cndoc="http://www.scriptcalendar.com">

	<xsl:variable name="varCellWidth" select="calendar/properties/@cellWidth" />
	<xsl:variable name="varCellHeight" select="calendar/properties/@cellHeight" />
	<xsl:variable name="varDeadCellBehavior" select="calendar/properties/@deadCellBehavior" />
	<xsl:variable name="varDisplayWeekNumber" select="calendar/properties/@displayWeekNumber" />


	<xsl:output method="xml" indent="yes" omit-xml-declaration="yes"/>
	<!--<xsl:output method="html" indent="no"/>-->

	<xsl:template match="/">
		<xsl:apply-templates select="calendar" />
	</xsl:template>

	<!--
	************************************************************
	***  Misc templates
	************************************************************
	-->
	<xsl:template name="tonull">
		<xsl:param name="value"/>
		
		<xsl:choose>
			<xsl:when test="string-length($value)=0">null</xsl:when>
			<xsl:otherwise><xsl:value-of select="$value"/></xsl:otherwise>
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
	***  Calendar templates
	************************************************************
	-->
	<xsl:template match="calendar">
		<table><tr><td>

	
		<div id="scCalendarElement" name="scCalendarElement" class="scCalendar">
			<table width="{@width}" border="{@border}" cellpadding="{@cellpadding}" cellspacing="{@cellspacing}">
		
			<tr id="scSelectorRow" name="scSelectorRow">
				<xsl:for-each select="selectors/selector[@uniqueid='prev']">
					<td id="scSelectorRow1" name="scSelectorRow1" align="center" valign="middle" colspan="{@colspan}">
						<div name="scHeaderElement" id="scHeaderElement" class="scHeader" onclick="{@onclick}" onmouseover="{@onmouseover}">
							<span class="scSelector"><xsl:value-of select="."/></span>
						</div>
					</td>
				</xsl:for-each>
				
				<xsl:for-each select="selectors/selector[@uniqueid='date']">
					<td id="scSelectorRow2" name="scSelectorRow2" align="center" valign="middle" colspan="{@colspan}">
						<div name="scHeaderElement" id="scHeaderElement" class="scHeader" onclick="{@onclick}" onmouseover="{@onmouseover}">
							<xsl:value-of select="."/>
						</div>
					</td>
				</xsl:for-each>
		
				<xsl:for-each select="selectors/selector[@uniqueid='next']">
					<td id="scSelectorRow3" name="scSelectorRow3" align="center" valign="middle" colspan="{@colspan}">
						<div name="scHeaderElement" id="scHeaderElement" class="scHeader" onclick="{@onclick}" onmouseover="{@onmouseover}">
							<span class="scSelector"><xsl:value-of select="."/></span>
						</div>
					</td>
				</xsl:for-each>
			</tr>
		
			<tr>
				<xsl:for-each select="weekdayheaders/weekdayheader">
					<td align="center">
						<div name="scHeaderElement" id="scHeaderElement" class="scHeader">
							<xsl:value-of select="."/>
						</div>
					</td>
				</xsl:for-each>
			</tr>
			
			<xsl:apply-templates select="weeks/week" />
			</table>
		</div>
		</td></tr></table>
	</xsl:template>	

	<!--
	************************************************************
	***  Week templates
	************************************************************
	-->
	<xsl:template match="week">
		<tr>
			<xsl:if test="$varDisplayWeekNumber=-1">
				<td valign="middle">
					<div name="scWeekNumberElement" id="scWeekNumberElement" class="scWeekNumber">
						<xsl:value-of select="weekdays/weekday[position()=1]/@weekofyear"/>
					</div>
				</td>
			</xsl:if>
			<xsl:apply-templates select="weekdays/weekday" />
		</tr>
	</xsl:template>

	<!--
	************************************************************
	***  Weekday templates
	************************************************************
	-->
	<xsl:template match="weekday">
		<td valign="top" width="{@width}" height="{@height}" >
			<div name="scWeekdayElement" id="scWeekdayElement" class="{@class}">

				<!-- add handlers -->
				<xsl:if test="string-length(@onmousedown)!=0">
					<xsl:attribute name="onMouseDown"><xsl:value-of select="@onmousedown"/></xsl:attribute>
				</xsl:if>	
				<xsl:if test="string-length(@onmouseout)!=0">
					<xsl:attribute name="onMouseOut"><xsl:value-of select="@onmouseout"/></xsl:attribute>
				</xsl:if>	
				<xsl:if test="string-length(@onmouseover)!=0">
					<xsl:attribute name="onMouseOver"><xsl:value-of select="@onmouseover"/></xsl:attribute>
				</xsl:if>	
				<xsl:if test="string-length(@onmouseup)!=0">
					<xsl:attribute name="onMouseUp"><xsl:value-of select="@onmouseup"/></xsl:attribute>
				</xsl:if>	
			
				<xsl:for-each select="number">
					<div class="{@class}">
						<xsl:value-of select="." />
					</div>
				</xsl:for-each>
				<xsl:apply-templates select="event"/>
			</div>
		</td>
	</xsl:template>

	<!--
	************************************************************
	***  Event templates
	************************************************************
	-->
	<xsl:template match="event">
		<div name="scEventElement" id="scEventElement" class="{@class}">
			<xsl:attribute name="title"><xsl:value-of select="tooltip"/></xsl:attribute>
			<xsl:if test="string-length(script)!=0">
				<xsl:attribute name="onClick">javacript:<xsl:value-of select="script"/></xsl:attribute>
			</xsl:if>

			<xsl:variable name="hyperlink">
				<xsl:choose>
				<xsl:when test="string-length(popuplink)!=0">
				</xsl:when>
				<xsl:when test="string-length(script)!=0">
					javascript:<xsl:value-of select="script"/>
				</xsl:when>
				<xsl:otherwise></xsl:otherwise>
				</xsl:choose>
			</xsl:variable>

			<xsl:choose>
			<xsl:when test="string-length(popuplink)!=0">
				<a href="javascript:var win=window.open('{popuplink}','popup', '');">
					<xsl:value-of select="text"/>
				</a>
			</xsl:when>
			<xsl:otherwise>
					<xsl:value-of select="text"/>
			</xsl:otherwise>
			</xsl:choose>
		</div>
	</xsl:template>
</xsl:stylesheet>
