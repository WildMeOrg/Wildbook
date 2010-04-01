//Starfield - http://www.btinternet.com/~kurt.grigg/javascript
if  ((document.getElementById) && 
window.addEventListener || window.attachEvent){

(function(){

//Configure here.

var numberOfStars = 40;

var starSpeed = 0.9; 

var inTheFace = 5;

var setTimeOutSpeed = 30;

//End config. 

var h,y,cy,cx,sy,sx,ref,field,oy1,oy2,ox1,ox2,iy1,iy2,ix1,ix2;
var d = document;
var domWw = (typeof window.innerWidth == "number");
var domSy = (typeof window.pageYOffset == "number");
var pi1 = 180/3.14;
var pi2 = 3.14/180;
var y = [];
var x = [];
var strs = [];
var gro = [];
var dim = [];
var dfc = [];
var vel = [];
var dir = [];
var acc = [];
var dtor = [];
var xy2 = [];
var idx = document.getElementsByTagName('div').length;
var zip = [];
var pix = "px";

//Floor or round anything possible for Netscape.
//Slashes CPU strain in Opera too.

for (i = 0; i < numberOfStars; i++){
document.write('<div id="stars'+(idx+i)+'"'
+' style="position:absolute;top:0px;left:0px;'
+'width:1px;height:1px;background-color:#ffffff;'
+'font-size:0px;"><\/div>');
}

if (domWw) ref = window;
else{ 
 if (d.documentElement && 
  typeof d.documentElement.clientWidth == "number" && 
  d.documentElement.clientWidth != 0)
  ref = d.documentElement;
 else{ 
  if (d.body && 
  typeof d.body.clientWidth == "number")
  ref = d.body;
 }
}

function win(){
var mozBar = ((domWw) && 
ref.innerWidth != d.documentElement.offsetWidth)?20:0;
h = (domWw)?ref.innerHeight:ref.clientHeight; 
w = (domWw)?ref.innerWidth - mozBar:ref.clientWidth;
cy = Math.floor(h/2);
cx = Math.floor(w/2);

oy1 = (75 * h / 100);
oy2 = (oy1 / 2);
ox1 = (75 * w / 100);
ox2 = (ox1 / 2);

iy1 = (18 * h / 100);
iy2 = (iy1 / 2);
ix1 = (18 * w / 100);
ix2 = (ix1 / 2); 

field = (h > w)?h:w;
}

function rst(s){
var cyx;
sy = (domSy)?ref.pageYOffset:ref.scrollTop;
sx = (domSy)?ref.pageXOffset:ref.scrollLeft;
acc[s] = 0;
dim[s] = 1;
xy2[s] = 0;
cyx = Math.round(Math.random() * 2);
if (cyx == 0){
y[s] = (cy - iy2) + Math.floor(Math.random() * iy1);
x[s] = (cx - ix2) + Math.floor(Math.random() * ix1);
}
else{
y[s] = (cy - oy2) + Math.floor(Math.random() * oy1);
x[s] = (cx - ox2) + Math.floor(Math.random() * ox1);
}
dy = y[s] - cy;
dx = x[s] - cx;
dir[s] = Math.atan2(dy,dx) * pi1;
dfc[s] = Math.sqrt(dy*dy + dx*dx) ;
zip[s] = 10 * (dfc[s] + inTheFace) / 100;
vel[s] = starSpeed * dfc[s] / 100;
dtor[s] = (field - dfc[s]); 
if (dtor[s] < 1) dtor[s] = 1;
gro[s] = 0.003 * dtor[s] / 100;
}

function animate(){
for (i = 0; i < numberOfStars; i++){
y[i] += vel[i] * Math.sin(dir[i] * pi2);
x[i] += vel[i] * Math.cos(dir[i] * pi2);
acc[i] = (vel[i] / (dfc[i] + (vel[i] * zip[i])) * vel[i]);
vel[i] += (acc[i]);
dim[i] += gro[i] + acc[i] / zip[i];
xy2[i] = dim[i] / 2;
if (y[i] < 0 + xy2[i] || 
x[i] < 0 + xy2[i] || 
y[i] > h - xy2[i] || 
x[i] > w - xy2[i]){
 rst(i);
}
strs[i].top = (y[i] - xy2[i]) + sy + pix;
strs[i].left = (x[i] - xy2[i]) + sx + pix;
strs[i].width = (strs[i].height = (Math.round(dim[i])) + pix);
}
setTimeout(animate,setTimeOutSpeed);
}

function init(){
win();
for (i = 0; i < numberOfStars; i++){
 strs[i] = document.getElementById("stars"+(idx+i)).style;
 rst(i);
}
animate();
}

if (window.addEventListener){
 window.addEventListener("resize",win,false);
 window.addEventListener("load",init,false);
}  
else if (window.attachEvent){
 window.attachEvent("onresize",win);
 window.attachEvent("onload",init);
} 
})();
}//End.