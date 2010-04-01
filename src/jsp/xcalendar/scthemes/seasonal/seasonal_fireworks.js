/**
  * You may use this code for free on any web page provided that 
  * these comment lines and the following credit remain in the code.
  * Cross Browser Fireworks from http://www.javascript-fx.com
  */
/*************************************************/

if (g_intMonth==7) {

if(!window.JSFX) JSFX=new Object();

if(!JSFX.createLayer)
{/*** Include Library Code ***/

var ns4 = document.layers;
var ie4 = document.all;
JSFX.objNo=0;

JSFX.getObjId = function(){return "JSFX_obj" + JSFX.objNo++;};

JSFX.createLayer = function(theHtml)
{
	var layerId = JSFX.getObjId();

	document.write(ns4 ? "<LAYER  NAME='"+layerId+"'>"+theHtml+"</LAYER>" : 
				   "<DIV id='"+layerId+"' style='position:absolute;'>"+theHtml+"</DIV>" );

	var el = 	document.getElementById	? document.getElementById(layerId) :
			document.all 		? document.all[layerId] :
							  document.layers[layerId];

	if(ns4)
		el.style=el;

	return el;
}
JSFX.fxLayer = function(theHtml)
{
	if(theHtml == null) return;
	this.el = JSFX.createLayer(theHtml);
}
var proto = JSFX.fxLayer.prototype

proto.moveTo     = function(x,y){this.el.style.left = x;this.el.style.top=y;}
proto.setBgColor = function(color) { this.el.style.backgroundColor = color; } 
proto.clip       = function(x1,y1, x2,y2){ this.el.style.clip="rect("+y1+" "+x2+" "+y2+" "+x1+")"; }
if(ns4){
	proto.clip = function(x1,y1, x2,y2){
		this.el.style.clip.top	 =y1;this.el.style.clip.left	=x1;
		this.el.style.clip.bottom=y2;this.el.style.clip.right	=x2;
	}
	proto.setBgColor=function(color) { this.el.bgColor = color; }
}
if(window.opera)
	proto.setBgColor = function(color) { this.el.style.color = color==null?'transparent':color; }

if(window.innerWidth)
{
	gX=function(){return innerWidth;};
	gY=function(){return innerHeight;};
}
else
{
	gX=function(){return document.body.clientWidth;};
	gY=function(){return document.body.clientHeight;};
}

/*** Example extend class ***/
JSFX.fxLayer2 = function(theHtml)
{
	this.superC = JSFX.fxLayer;
	this.superC(theHtml + "C");
}
JSFX.fxLayer2.prototype = new JSFX.fxLayer;
}/*** End Library Code ***/

/*************************************************/

/*** Class Firework extends FxLayer ***/
JSFX.Firework = function(fwImages)
{
	window[ this.id = JSFX.getObjId() ] = this;
	this.imgId = "i" + this.id;
	this.fwImages  = fwImages;
	this.numImages = fwImages.length;
	this.superC = JSFX.fxLayer;
	this.superC("<img src='"+fwImages[0].src+"' name='"+this.imgId+"'>");

	this.img = document.layers ? this.el.document.images[0] : document.images[this.imgId];
	this.step = 0;
	this.timerId = -1;
	this.x = 0;
	this.y = 0;
	this.dx = 0;
	this.dy = 0;
	this.ay = 0.2;
	this.state = "OFF";
}
JSFX.Firework.prototype = new JSFX.fxLayer;

JSFX.Firework.prototype.getMaxDy = function()
{
	var ydiff = gY() - 130;
	var dy    = 1;
	var dist  = 0;
	var ay    = this.ay;
	while(dist<ydiff)
	{
		dist += dy;
		dy+=ay;
	}
	return -dy;
}
JSFX.Firework.prototype.setFrame = function()
{
//	this.img.src=this.fwName+"/"+this.step+".gif";
	this.img.src=this.fwImages[ this.step ].src;
}
JSFX.Firework.prototype.animate = function()
{

	if(this.state=="OFF")
	{
		
		this.step = 0;
		this.x = -200;
		this.y = -200;
		this.moveTo(this.x, this.y);
		this.setFrame();
		if(Math.random() > .95)
		{
			this.x = (gX()-200)*Math.random();
			this.y = (gY()-200)*Math.random();
			this.moveTo(this.x, this.y);
			this.state = "EXPLODE";
		}
	}
	else if(this.state == "EXPLODE")
	{
		this.step++;
		if(this.step < this.numImages)
			this.setFrame();
		else
			this.state="OFF";
	}
}
/*** END Class Firework***/

/*** Class FireworkDisplay extends Object ***/
JSFX.FireworkDisplay = function(n, fwImages, numImages)
{
	window[ this.id = JSFX.getObjId() ] = this;
	this.timerId = -1;
	this.fireworks = new Array();
	this.imgArray = new Array();
	this.loadCount=0;
	this.loadImages(fwImages, numImages);

	for(var i=0 ; i<n ; i++)
		this.fireworks[this.fireworks.length] = new JSFX.Firework(this.imgArray);
}
JSFX.FireworkDisplay.prototype.loadImages = function(fwName, numImages)
{
	var strIndex;

	for(var i=0 ; i<numImages ; i++)
	{
		this.imgArray[i] = new Image();
		this.imgArray[i].obj = this;
		this.imgArray[i].onload = window[this.id].imageLoaded;
		this.imgArray[i].src = fwName +"/" + i + ".gif";
	}
}
JSFX.FireworkDisplay.prototype.imageLoaded = function()
{
	this.obj.loadCount++;
}

JSFX.FireworkDisplay.prototype.animate = function()
{
status = this.loadCount;
	if(this.loadCount < this.imgArray.length)
		return;

	for(var i=0 ; i<this.fireworks.length ; i++)
		this.fireworks[i].animate();
}
JSFX.FireworkDisplay.prototype.start = function()
{
	if(this.timerId == -1)
	{
		this.state = "OFF";
		this.timerId = setInterval("window."+this.id+".animate()", 40);
	}

}
JSFX.FireworkDisplay.prototype.stop = function()
{
	if(this.timerId != -1)
	{
		clearInterval(this.timerId);
		this.timerId = -1;
		for(var i=0 ; i<this.fireworks.length ; i++)
		{
			this.fireworks[i].moveTo(-100, -100);
			this.fireworks[i].step = 0;;
			this.fireworks[i].state = "OFF";
		}	
	}
}
/*** END Class FireworkDisplay***/

JSFX.FWStart = function()
{
	if(JSFX.FWLoad)JSFX.FWLoad();
	myFW.start();
}
myFW = new JSFX.FireworkDisplay(20, "fireworks", 27);
JSFX.FWLoad=window.onload;
window.onload=JSFX.FWStart;

}