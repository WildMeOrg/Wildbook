
//TODO "remember" previous settings (e.g. scale) in case they were not default
function zoomToFeature(imgEl, feat) {
    if ((imgEl == null) || (typeof feat != 'object')) return;
    if (!feat.parameters || (typeof feat.parameters != 'object')) return;
    var elWidth = imgEl.width;
    var imgWidth = imgEl.naturalWidth;
    console.log('elWidth=%o imgWidth=%o; ft.params => %o', elWidth, imgWidth, feat.parameters);
    if (imgWidth < 1) return;
    var ratio = elWidth / imgWidth;
    var scale = imgWidth / feat.parameters.width;
    console.log('ratio => %o; scale => %o', ratio, scale);
    imgEl.style.transformOrigin = '0 0';
    imgEl.style.left = -(feat.parameters.x * scale * ratio) + 'px';
    imgEl.style.top = -(feat.parameters.y * scale * ratio) + 'px';
    imgEl.style.transform = 'scale(' + scale + ')';
console.info(imgEl.style);
}

function unzoomFeature(imgEl) {
    imgEl.style.transform = 'scale(1.0)';
    imgEl.style.left = 0;
    imgEl.style.top = 0;
}
