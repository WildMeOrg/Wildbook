<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN" "DTD/xhtml1-transitional.dtd">
<html xmlns="http://www.w3.org/1999/xhtml" xml:lang="en" lang="en">

<head>
  <title>Manta Matcher Search Results</title>
  <link href="../css/mma.css" rel="stylesheet" type="text/css"/>
</head>

<body>

<p class="mma-version">${version}, ${datetime?string("dd MMM yyyy, hh:mm:ss")}</p>

<p class="mma-query"><span class="mma-bold">Query Image:</span> ${results[0].name!"Unknown"?html}</p>
<div>
  <a href="${results[0].link!"#"?html}" target="_blank"><img src="${results[0].linkEH}" width="*" height="200"/></a>
</div>
<p class="mma-postQuery">... Completed, displaying top matches: (click on an image to access the encounter record)</p>

<table id="mma-confidence">
<tr>
  <td>Overall confidence of results <span class="mma-small">(0:worst, 1:best)</span>: <span class="mma-bold">${results[0].confidence?string("0.######")}</span></td>
</tr>
</table>

<table id="mma-results">
<tr>
  <th>Rank</td>
  <th>Similarity<br/><span class="mma-small">(0:worst, 1:best)</span></td>
  <th>Filename</td>
  <th>Matched image<br/><span class="mma-small">(open in a new window)</span></td>
  <th>Query image<br/><span class="mma-small">(open in a new window)</span></td>
</tr>
<#list results[0].matches as item>
<tr>
  <td>${item.rank}</td>
  <td>${item.score?string("0.######")}</td>
  <td>${item.imgbase?html}</td>
  <td><a href="${item.link!"#"}" target="_blank"><img src="${item.linkEH}" width="*" height="200"/></a></td>
  <td><a href="${results[0].link!"#"}" target="_blank"><img src="${results[0].linkEH}" width="*" height="200"/></a></td>
</tr>
</#list>
</table>

<hr>
<p class="mma-copyright">Manta Matcher, Copyright: <a href="http://www.cl.cam.ac.uk/~cpt23/index.html">Chris Town</a> and N. Sethasathien, 2012</p>

</body>
</html>
