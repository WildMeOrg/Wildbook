function exportTableToExcelUri(tableEl, sheetName) {
    if (!sheetName) sheetName = 'Worksheet';
    var uri = 'data:application/vnd.ms-excel;base64,';
    var content = '<html xmlns:o="urn:schemas-microsoft-com:office:office" xmlns:x="urn:schemas-microsoft-com:office:excel" xmlns="http://www.w3.org/TR/REC-html40"><meta http-equiv="content-type" content="application/vnd.ms-excel; charset=UTF-8"/><head><!--[if gte mso 9]><xml><x:ExcelWorkbook><x:ExcelWorksheets><x:ExcelWorksheet><x:Name>' + sheetName + '</x:Name><x:WorksheetOptions><x:DisplayGridlines/></x:WorksheetOptions></x:ExcelWorksheet></x:ExcelWorksheets></x:ExcelWorkbook></xml><![endif]--></head><body><table>' + tableEl.innerHTML + '</table></body></html>';

    uri += base64(content);
    return uri;
}

function base64(s) {
    return window.btoa(unescape(encodeURIComponent(s)));
}
