<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN" "DTD/xhtml1-transitional.dtd">
<html xmlns="http://www.w3.org/1999/xhtml" xml:lang="en" lang="en">
<head>
<title>Manta Matcher Search Results</title>
<meta name="Author" content="Manta Matcher, Copyright: Dr C.P. Town and N. Sethasathien, 2012"></meta>
</head>
<body>
${version}, ${datetime?string("dd MMM yyyy, hh:mm:ss")}
<font size="4">
<p><b>Query Image:</b> ${results[0].name!"Unknown"?html}</p>
<p><a href="${results[0].link!"#"?html}" target="_blank"><img src="${results[0].linkEH}" width="*" height="200"/></a></p>
<p>... Completed, displaying top matches: (click on an image to access the encounter record)</p>
<p>
<table border='1' cellpadding='5' cellspacing='0' bgcolor='#FFFFD0'>
<tr><td>Overall confidence of results <small>(0:worst, 1:best)</small>: <b>${results[0].confidence?string("0.######")}</b></td></tr>
</table>
</p><p>
<table width='100%' border='1' cellpadding='5' cellspacing='0' bgcolor='#E0E0F0'>
<tr>
<td valign="middle" align="center" width="7%"><b>Rank</b></td>
<td valign="middle" align="center" width="8%"><b>Similarity</b> <small>(0:worst, 1:best)</small></td>
<td valign="middle" align="center" width="15%"><b>Filename</b></td>
<td valign="middle" align="center" width="35%"><b>Matched image</b> <small>(open in a new window)</small></td>
<td valign="middle" align="center" width="35%"><b>Query image</b> <small>(open in a new window)</small></td>
</tr>
<#list results[0].matches as item>
<tr>
<td valign="middle" align="center" width="7%">${item.rank}</td>
<td valign="middle" align="center" width="8%">${item.score?string("0.######")}</td>
<td valign="middle" align="center" width="15%">${item.imgbase?html}</td>
<td valign="middle" align="center" width="35%"><a href="${item.link!"#"}" target="_blank"><img src="${item.linkEH}" width="*" height="200"/></a></td>
<td valign="middle" align="center" width="35%"><a href="${results[0].link!"#"}" target="_blank"><img src="${results[0].linkEH}" width="*" height="200"/></a></td>
</tr>
</#list>
</table>
</p>
<hr><address>
Manta Matcher, Copyright: <a href="http://www.cl.cam.ac.uk/~cpt23/index.html">Chris Town</a> and N. Sethasathien, 2012</address></hr>
</font>
</body>
</html>
