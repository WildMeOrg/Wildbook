<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN" "DTD/xhtml1-transitional.dtd">
<html xmlns="http://www.w3.org/1999/xhtml" xml:lang="en" lang="en">

<head>
  <title>MantaMatcher: algorithm search results</title>
  <link href="../css/mma.css" rel="stylesheet" type="text/css"/>
</head>

<body>

<div id="mma-queryImage">
  <a href="${results[0].link!"#"}" target="_blank"><img src="${results[0].linkEH}" class="mma-queryImg"/></a>
  <p>${results[0].name!"Unknown"?html}</p>
</div>

<div id="mma-desc">
  <table>
    <tr>
      <th>Date of scan:</th>
      <td class="mma-date">${datetime?string("dd/MM/yyyy hh:mm:ss")}</td>
    </tr>
    <tr>
      <th>Version:</th>
      <td class="mma-version">${version}</td>
    </tr>
    <tr>
      <th>Match count:</th>
      <td class="mma-count">${results[0].matches?size}</td>
    </tr>
    <tr>
      <th>Confidence:<br/><span class="mma-small">(0:worst, 1:best)</span></th>
      <td class="mma-confidence">${results[0].confidence?string("0.######")}</td>
    </tr>
  </table>
</div>

<div id="mma-results">
  <table id="mma-resultsTable">
    <tr>
      <th>Rank</td>
      <th>Similarity<br/><span class="mma-small">(0:worst, 1:best)</span></td>
      <th>Match details</td>
      <th>Matched image<br/><span class="mma-small">(opens in a new window)</span></td>
      <th>Query image</td>
    </tr>
<#if results[0].matches?has_content>
  <#list results[0].matches as item>
    <tr>
      <td class="rank">${item.rank}</td>
      <td class="similarity">${item.score?string("0.######")}</td>
      <td class="filename">
        <table id="mma-resultDetailsTable">
          <tr><th>Individual ID:</th><td>${item.individualID!""}</td></tr>
          <tr><th>Encounter&nbsp;date:</th><td>${item.encounterDate!""}</td></tr>
          <tr><th>Pigmentation:</th><td>${item.pigmentation!""}</td></tr>
        </table>
      </td>
      <td class="matchedImage"><a href="${item.link!"#"}" target="_blank"><img src="${item.linkEH}" class="mma-matchImg"/></a></td>
      <td class="queryImage"><a href="${results[0].link!"#"}"><img src="${results[0].linkEH}" class="mma-queryImg"/></a></td>
    </tr>
  </#list>
<#else>
    <tr>
      <td class="noMatches" colspan="3">No matches were found by the MantaMatcher algorithm</td>
    </tr>
</#if>
  </table>
</div>

<div id="mma-footer">
  <p id="mma-copyright">Manta Matcher, Copyright: <a href="http://www.cl.cam.ac.uk/~cpt23/index.html">Chris Town</a> and N. Sethasathien, 2012</p>
</div>

</body>
</html>
