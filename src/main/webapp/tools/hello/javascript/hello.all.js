/*! hellojs v1.14.1 | (c) 2012-2017 Andrew Dodson | MIT https://adodson.com/hello.js/LICENSE */
<<<<<<< HEAD
Object.create||(Object.create=function(){function e(){}return function(t){if(1!=arguments.length)throw new Error("Object.create implementation only accepts one parameter.");return e.prototype=t,new e}}()),Object.keys||(Object.keys=function(e,t,n){n=[];for(t in e)n.hasOwnProperty.call(e,t)&&n.push(t);return n}),Array.prototype.indexOf||(Array.prototype.indexOf=function(e){for(var t=0;t<this.length;t++)if(this[t]===e)return t;return-1}),Array.prototype.forEach||(Array.prototype.forEach=function(e){if(void 0===this||null===this)throw new TypeError;var t=Object(this),n=t.length>>>0;if("function"!=typeof e)throw new TypeError;for(var o=arguments.length>=2?arguments[1]:void 0,i=0;n>i;i++)i in t&&e.call(o,t[i],i,t);return this}),Array.prototype.filter||(Array.prototype.filter=function(e,t){var n=[];return this.forEach(function(o,i,r){e.call(t||void 0,o,i,r)&&n.push(o)}),n}),Array.prototype.map||(Array.prototype.map=function(e,t){var n=[];return this.forEach(function(o,i,r){n.push(e.call(t||void 0,o,i,r))}),n}),Array.isArray||(Array.isArray=function(e){return"[object Array]"===Object.prototype.toString.call(e)}),"object"!=typeof window||"object"!=typeof window.location||window.location.assign||(window.location.assign=function(e){window.location=e}),Function.prototype.bind||(Function.prototype.bind=function(e){function t(){}if("function"!=typeof this)throw new TypeError("Function.prototype.bind - what is trying to be bound is not callable");var n=[].slice,o=n.call(arguments,1),i=this,r=function(){return i.apply(this instanceof t?this:e||window,o.concat(n.call(arguments)))};return t.prototype=this.prototype,r.prototype=new t,r});var hello=function(e){return hello.use(e)};hello.utils={extend:function(e){return Array.prototype.slice.call(arguments,1).forEach(function(t){if(Array.isArray(e)&&Array.isArray(t))Array.prototype.push.apply(e,t);else if(e&&(e instanceof Object||"object"==typeof e)&&t&&(t instanceof Object||"object"==typeof t)&&e!==t)for(var n in t)e[n]=hello.utils.extend(e[n],t[n]);else Array.isArray(t)&&(t=t.slice(0)),e=t}),e}},hello.utils.extend(hello,{settings:{redirect_uri:window.location.href.split("#")[0],response_type:"token",display:"popup",state:"",oauth_proxy:"https://auth-server.herokuapp.com/proxy",timeout:2e4,popup:{resizable:1,scrollbars:1,width:500,height:550},scope:["basic"],scope_map:{basic:""},default_service:null,force:null,page_uri:window.location.href},services:{},use:function(e){var t=Object.create(this);return t.settings=Object.create(this.settings),e&&(t.settings.default_service=e),t.utils.Event.call(t),t},init:function(e,t){var n=this.utils;if(!e)return this.services;for(var o in e)e.hasOwnProperty(o)&&"object"!=typeof e[o]&&(e[o]={id:e[o]});return n.extend(this.services,e),t&&(n.extend(this.settings,t),"redirect_uri"in t&&(this.settings.redirect_uri=n.url(t.redirect_uri).href)),this},login:function(){function e(e,t){hello.emit(e,t)}function t(e){return e}function n(e){return!!e}var o,i=this,r=i.utils,a=r.error,s=r.Promise(),l=r.args({network:"s",options:"o",callback:"f"},arguments),u=r.diffKey(l.options,i.settings),c=l.options=r.merge(i.settings,l.options||{});if(c.popup=r.merge(i.settings.popup,l.options.popup||{}),l.network=l.network||i.settings.default_service,s.proxy.then(l.callback,l.callback),s.proxy.then(e.bind(this,"auth.login auth"),e.bind(this,"auth.failed auth")),"string"!=typeof l.network||!(l.network in i.services))return s.reject(a("invalid_network","The provided network was not recognized"));var d=i.services[l.network],f=r.globalEvent(function(e){var t;t=e?JSON.parse(e):a("cancelled","The authentication was not completed"),t.error?s.reject(t):(r.store(t.network,t),s.fulfill({network:t.network,authResponse:t}))}),p=r.url(c.redirect_uri).href,m=d.oauth.response_type||c.response_type;/\bcode\b/.test(m)&&!d.oauth.grant&&(m=m.replace(/\bcode\b/,"token")),l.qs=r.merge(u,{client_id:encodeURIComponent(d.id),response_type:encodeURIComponent(m),redirect_uri:encodeURIComponent(p),state:{client_id:d.id,network:l.network,display:c.display,callback:f,state:c.state,redirect_uri:p}});var h=r.store(l.network),g=/[,\s]+/,v=i.settings.scope?[i.settings.scope.toString()]:[],y=r.merge(i.settings.scope_map,d.scope||{});if(c.scope&&v.push(c.scope.toString()),h&&"scope"in h&&h.scope instanceof String&&v.push(h.scope),v=v.join(",").split(g),v=r.unique(v).filter(n),l.qs.state.scope=v.join(","),v=v.map(function(e){return e in y?y[e]:e}),v=v.join(",").split(g),v=r.unique(v).filter(n),l.qs.scope=v.join(d.scope_delim||","),c.force===!1&&h&&"access_token"in h&&h.access_token&&"expires"in h&&h.expires>(new Date).getTime()/1e3){var w=r.diff((h.scope||"").split(g),(l.qs.state.scope||"").split(g));if(0===w.length)return s.fulfill({unchanged:!0,network:l.network,authResponse:h}),s}if("page"===c.display&&c.page_uri&&(l.qs.state.page_uri=r.url(c.page_uri).href),"login"in d&&"function"==typeof d.login&&d.login(l),(!/\btoken\b/.test(m)||parseInt(d.oauth.version,10)<2||"none"===c.display&&d.oauth.grant&&h&&h.refresh_token)&&(l.qs.state.oauth=d.oauth,l.qs.state.oauth_proxy=c.oauth_proxy),l.qs.state=encodeURIComponent(JSON.stringify(l.qs.state)),1===parseInt(d.oauth.version,10)?o=r.qs(c.oauth_proxy,l.qs,t):"none"===c.display&&d.oauth.grant&&h&&h.refresh_token?(l.qs.refresh_token=h.refresh_token,o=r.qs(c.oauth_proxy,l.qs,t)):o=r.qs(d.oauth.auth,l.qs,t),e("auth.init",l),"none"===c.display)r.iframe(o,p);else if("popup"===c.display)var _=r.popup(o,p,c.popup),b=setInterval(function(){if((!_||_.closed)&&(clearInterval(b),!s.state)){var e=a("cancelled","Login has been cancelled");_||(e=a("blocked","Popup was blocked")),e.network=l.network,s.reject(e)}},100);else window.location=o;return s.proxy},logout:function(){function e(e,t){hello.emit(e,t)}var t=this,n=t.utils,o=n.error,i=n.Promise(),r=n.args({name:"s",options:"o",callback:"f"},arguments);if(r.options=r.options||{},i.proxy.then(r.callback,r.callback),i.proxy.then(e.bind(this,"auth.logout auth"),e.bind(this,"error")),r.name=r.name||this.settings.default_service,r.authResponse=n.store(r.name),!r.name||r.name in t.services)if(r.name&&r.authResponse){var a=function(e){n.store(r.name,null),i.fulfill(hello.utils.merge({network:r.name},e||{}))},s={};if(r.options.force){var l=t.services[r.name].logout;if(l)if("function"==typeof l&&(l=l(a,r)),"string"==typeof l)n.iframe(l),s.force=null,s.message="Logout success on providers site was indeterminate";else if(void 0===l)return i.proxy}a(s)}else i.reject(o("invalid_session","There was no session to remove"));else i.reject(o("invalid_network","The network was unrecognized"));return i.proxy},getAuthResponse:function(e){return e=e||this.settings.default_service,e&&e in this.services?this.utils.store(e)||null:null},events:{}}),hello.utils.extend(hello.utils,{error:function(e,t){return{error:{code:e,message:t}}},qs:function(e,t,n){if(t){n=n||encodeURIComponent;for(var o in t){var i="([\\?\\&])"+o+"=[^\\&]*",r=new RegExp(i);e.match(r)&&(e=e.replace(r,"$1"+o+"="+n(t[o])),delete t[o])}}return this.isEmpty(t)?e:e+(e.indexOf("?")>-1?"&":"?")+this.param(t,n)},param:function(e,t){var n,o,i={};if("string"==typeof e){if(t=t||decodeURIComponent,o=e.replace(/^[\#\?]/,"").match(/([^=\/\&]+)=([^\&]+)/g))for(var r=0;r<o.length;r++)n=o[r].match(/([^=]+)=(.*)/),i[n[1]]=t(n[2]);return i}t=t||encodeURIComponent;var a=e;i=[];for(var s in a)a.hasOwnProperty(s)&&a.hasOwnProperty(s)&&i.push([s,"?"===a[s]?"?":t(a[s])].join("="));return i.join("&")},store:function(){function e(){var e={};try{e=JSON.parse(n.getItem("hello"))||{}}catch(t){}return e}function t(e){n.setItem("hello",JSON.stringify(e))}for(var n,o=["localStorage","sessionStorage"],i=-1,r="test";o[++i];)try{n=window[o[i]],n.setItem(r+i,i),n.removeItem(r+i);break}catch(a){n=null}if(!n){var s=null;n={getItem:function(e){e+="=";for(var t=document.cookie.split(";"),n=0;n<t.length;n++){var o=t[n].replace(/(^\s+|\s+$)/,"");if(o&&0===o.indexOf(e))return o.substr(e.length)}return s},setItem:function(e,t){s=t,document.cookie=e+"="+t}},s=n.getItem("hello")}return function(n,o,i){var r=e();if(n&&void 0===o)return r[n]||null;if(n&&null===o)try{delete r[n]}catch(a){r[n]=null}else{if(!n)return r;r[n]=o}return t(r),r||null}}(),append:function(e,t,n){var o="string"==typeof e?document.createElement(e):e;if("object"==typeof t)if("tagName"in t)n=t;else for(var i in t)if(t.hasOwnProperty(i))if("object"==typeof t[i])for(var r in t[i])t[i].hasOwnProperty(r)&&(o[i][r]=t[i][r]);else"html"===i?o.innerHTML=t[i]:/^on/.test(i)?o[i]=t[i]:o.setAttribute(i,t[i]);return"body"===n?!function a(){document.body?document.body.appendChild(o):setTimeout(a,16)}():"object"==typeof n?n.appendChild(o):"string"==typeof n&&document.getElementsByTagName(n)[0].appendChild(o),o},iframe:function(e){this.append("iframe",{src:e,style:{position:"absolute",left:"-1000px",bottom:0,height:"1px",width:"1px"}},"body")},merge:function(){var e=Array.prototype.slice.call(arguments);return e.unshift({}),this.extend.apply(null,e)},args:function(e,t){var n={},o=0,i=null,r=null;for(r in e)if(e.hasOwnProperty(r))break;if(1===t.length&&"object"==typeof t[0]&&"o!"!=e[r])for(r in t[0])if(e.hasOwnProperty(r)&&r in e)return t[0];for(r in e)if(e.hasOwnProperty(r))if(i=typeof t[o],"function"==typeof e[r]&&e[r].test(t[o])||"string"==typeof e[r]&&(e[r].indexOf("s")>-1&&"string"===i||e[r].indexOf("o")>-1&&"object"===i||e[r].indexOf("i")>-1&&"number"===i||e[r].indexOf("a")>-1&&"object"===i||e[r].indexOf("f")>-1&&"function"===i))n[r]=t[o++];else if("string"==typeof e[r]&&e[r].indexOf("!")>-1)return!1;return n},url:function(e){if(e){if(window.URL&&URL instanceof Function&&0!==URL.length)return new URL(e,window.location);var t=document.createElement("a");return t.href=e,t.cloneNode(!1)}return window.location},diff:function(e,t){return t.filter(function(t){return-1===e.indexOf(t)})},diffKey:function(e,t){if(e||!t){var n={};for(var o in e)o in t||(n[o]=e[o]);return n}return e},unique:function(e){return Array.isArray(e)?e.filter(function(t,n){return e.indexOf(t)===n}):[]},isEmpty:function(e){if(!e)return!0;if(Array.isArray(e))return!e.length;if("object"==typeof e)for(var t in e)if(e.hasOwnProperty(t))return!1;return!0},Promise:function(){var e=0,t=1,n=2,o=function(t){return this instanceof o?(this.id="Thenable/1.0.6",this.state=e,this.fulfillValue=void 0,this.rejectReason=void 0,this.onFulfilled=[],this.onRejected=[],this.proxy={then:this.then.bind(this)},void("function"==typeof t&&t.call(this,this.fulfill.bind(this),this.reject.bind(this)))):new o(t)};o.prototype={fulfill:function(e){return i(this,t,"fulfillValue",e)},reject:function(e){return i(this,n,"rejectReason",e)},then:function(e,t){var n=this,i=new o;return n.onFulfilled.push(s(e,i,"fulfill")),n.onRejected.push(s(t,i,"reject")),r(n),i.proxy}};var i=function(t,n,o,i){return t.state===e&&(t.state=n,t[o]=i,r(t)),t},r=function(e){e.state===t?a(e,"onFulfilled",e.fulfillValue):e.state===n&&a(e,"onRejected",e.rejectReason)},a=function(e,t,n){if(0!==e[t].length){var o=e[t];e[t]=[];var i=function(){for(var e=0;e<o.length;e++)o[e](n)};"object"==typeof process&&"function"==typeof process.nextTick?process.nextTick(i):"function"==typeof setImmediate?setImmediate(i):setTimeout(i,0)}},s=function(e,t,n){return function(o){if("function"!=typeof e)t[n].call(t,o);else{var i;try{i=e(o)}catch(r){return void t.reject(r)}l(t,i)}}},l=function(e,t){if(e===t||e.proxy===t)return void e.reject(new TypeError("cannot resolve promise with itself"));var n;if("object"==typeof t&&null!==t||"function"==typeof t)try{n=t.then}catch(o){return void e.reject(o)}if("function"!=typeof n)e.fulfill(t);else{var i=!1;try{n.call(t,function(n){i||(i=!0,n===t?e.reject(new TypeError("circular thenable chain")):l(e,n))},function(t){i||(i=!0,e.reject(t))})}catch(o){i||e.reject(o)}}};return o}(),Event:function(){var e=/[\s\,]+/;return this.parent={events:this.events,findEvents:this.findEvents,parent:this.parent,utils:this.utils},this.events={},this.on=function(t,n){if(n&&"function"==typeof n)for(var o=t.split(e),i=0;i<o.length;i++)this.events[o[i]]=[n].concat(this.events[o[i]]||[]);return this},this.off=function(e,t){return this.findEvents(e,function(e,n){t&&this.events[e][n]!==t||(this.events[e][n]=null)}),this},this.emit=function(e){var t=Array.prototype.slice.call(arguments,1);t.push(e);for(var n=function(n,o){t[t.length-1]="*"===n?e:n,this.events[n][o].apply(this,t)},o=this;o&&o.findEvents;)o.findEvents(e+",*",n),o=o.parent;return this},this.emitAfter=function(){var e=this,t=arguments;return setTimeout(function(){e.emit.apply(e,t)},0),this},this.findEvents=function(t,n){var o=t.split(e);for(var i in this.events)if(this.events.hasOwnProperty(i)&&o.indexOf(i)>-1)for(var r=0;r<this.events[i].length;r++)this.events[i][r]&&n.call(this,i,r)},this},globalEvent:function(e,t){return t=t||"_hellojs_"+parseInt(1e12*Math.random(),10).toString(36),window[t]=function(){try{e.apply(this,arguments)&&delete window[t]}catch(n){console.error(n)}},t},popup:function(e,t,n){var o=document.documentElement;if(n.height){var i=void 0!==window.screenTop?window.screenTop:screen.top,r=screen.height||window.innerHeight||o.clientHeight;n.top=parseInt((r-n.height)/2,10)+i}if(n.width){var a=void 0!==window.screenLeft?window.screenLeft:screen.left,s=screen.width||window.innerWidth||o.clientWidth;n.left=parseInt((s-n.width)/2,10)+a}var l=[];Object.keys(n).forEach(function(e){var t=n[e];l.push(e+(null!==t?"="+t:""))}),-1!==navigator.userAgent.indexOf("Safari")&&-1===navigator.userAgent.indexOf("Chrome")&&(e=t+"#oauth_redirect="+encodeURIComponent(encodeURIComponent(e)));var u=window.open(e,"_blank",l.join(","));return u&&u.focus&&u.focus(),u},responseHandler:function(e,t){function n(e,t,n){var r=e.callback,s=e.network;if(a.store(s,e),!("display"in e&&"page"===e.display)){if(n&&r&&r in n){try{delete e.callback}catch(l){}a.store(s,e);var u=JSON.stringify(e);try{o(n,r)(u)}catch(l){}}i()}}function o(e,t){return 0!==t.indexOf("_hellojs_")?function(){throw"Could not execute callback "+t}:e[t]}function i(){if(e.frameElement)t.document.body.removeChild(e.frameElement);else{try{e.close()}catch(n){}e.addEventListener&&e.addEventListener("load",function(){e.close()})}}var r,a=this,s=e.location;if(r=a.param(s.search),r&&r.state&&(r.code||r.oauth_token)){var l=JSON.parse(r.state);r.redirect_uri=l.redirect_uri||s.href.replace(/[\?\#].*$/,"");var u=l.oauth_proxy+"?"+a.param(r);return void s.assign(u)}if(r=a.merge(a.param(s.search||""),a.param(s.hash||"")),r&&"state"in r){try{var c=JSON.parse(r.state);a.extend(r,c)}catch(d){console.error("Could not decode state parameter")}if("access_token"in r&&r.access_token&&r.network)r.expires_in&&0!==parseInt(r.expires_in,10)||(r.expires_in=0),r.expires_in=parseInt(r.expires_in,10),r.expires=(new Date).getTime()/1e3+(r.expires_in||31536e3),n(r,e,t);else if("error"in r&&r.error&&r.network)r.error={code:r.error,message:r.error_message||r.error_description},n(r,e,t);else if(r.callback&&r.callback in t){var f="result"in r&&r.result?JSON.parse(r.result):!1;o(t,r.callback)(f),i()}r.page_uri&&s.assign(r.page_uri)}else if("oauth_redirect"in r)return void s.assign(decodeURIComponent(r.oauth_redirect))}}),hello.utils.Event.call(hello),function(e){var t={},n={};e.on("auth.login, auth.logout",function(n){n&&"object"==typeof n&&n.network&&(t[n.network]=e.utils.store(n.network)||{})}),function o(){var i=(new Date).getTime()/1e3,r=function(t){e.emit("auth."+t,{network:a,authResponse:s})};for(var a in e.services)if(e.services.hasOwnProperty(a)){if(!e.services[a].id)continue;var s=e.utils.store(a)||{},l=e.services[a],u=t[a]||{};if(s&&"callback"in s){var c=s.callback;try{delete s.callback}catch(d){}e.utils.store(a,s);try{window[c](s)}catch(d){}}if(s&&"expires"in s&&s.expires<i){var f=l.refresh||s.refresh_token;!f||a in n&&!(n[a]<i)?f||a in n||(r("expired"),n[a]=!0):(e.emit("notice",a+" has expired trying to resignin"),e.login(a,{display:"none",force:!1}),n[a]=i+600);continue}if(u.access_token===s.access_token&&u.expires===s.expires)continue;!s.access_token&&u.access_token?r("logout"):s.access_token&&!u.access_token?r("login"):s.expires!==u.expires&&r("update"),t[a]=s,a in n&&delete n[a]}setTimeout(o,1e3)}()}(hello),hello.api=function(){function e(e){e=e.replace(/\@\{([a-z\_\-]+)(\|.*?)?\}/gi,function(e,t,n){var a=n?n.replace(/^\|/,""):"";return t in r.query?(a=r.query[t],delete r.query[t]):r.data&&t in r.data?(a=r.data[t],delete r.data[t]):n||i.reject(o("missing_attribute","The attribute "+t+" is missing from the request")),a}),e.match(/^https?:\/\//)||(e=u.base+e),r.url=e,n.request(r,function(e,t){if(!r.formatResponse)return void(("object"==typeof t?t.statusCode>=400:"object"==typeof e&&"error"in e)?i.reject(e):i.fulfill(e));if(e===!0?e={success:!0}:e||(e={}),"delete"===r.method&&(e=!e||n.isEmpty(e)?{success:!0}:e),u.wrap&&(r.path in u.wrap||"default"in u.wrap)){var o=r.path in u.wrap?r.path:"default",a=((new Date).getTime(),u.wrap[o](e,t,r));a&&(e=a)}e&&"paging"in e&&e.paging.next&&("?"===e.paging.next[0]?e.paging.next=r.path+e.paging.next:e.paging.next+="#"+r.path),!e||"error"in e?i.reject(e):i.fulfill(e)})}var t=this,n=t.utils,o=n.error,i=n.Promise(),r=n.args({path:"s!",query:"o",method:"s",data:"o",timeout:"i",callback:"f"},arguments);r.method=(r.method||"get").toLowerCase(),r.headers=r.headers||{},r.query=r.query||{},"get"!==r.method&&"delete"!==r.method||(n.extend(r.query,r.data),r.data={});var a=r.data=r.data||{};if(i.then(r.callback,r.callback),!r.path)return i.reject(o("invalid_path","Missing the path parameter from the request"));r.path=r.path.replace(/^\/+/,"");var s=(r.path.split(/[\/\:]/,2)||[])[0].toLowerCase();if(s in t.services){r.network=s;var l=new RegExp("^"+s+":?/?");r.path=r.path.replace(l,"")}r.network=t.settings.default_service=r.network||t.settings.default_service;var u=t.services[r.network];if(!u)return i.reject(o("invalid_network","Could not match the service requested: "+r.network));if(r.method in u&&r.path in u[r.method]&&u[r.method][r.path]===!1)return i.reject(o("invalid_path","The provided path is not available on the selected network"));r.oauth_proxy||(r.oauth_proxy=t.settings.oauth_proxy),"proxy"in r||(r.proxy=r.oauth_proxy&&u.oauth&&1===parseInt(u.oauth.version,10)),"timeout"in r||(r.timeout=t.settings.timeout),"formatResponse"in r||(r.formatResponse=!0),r.authResponse=t.getAuthResponse(r.network),r.authResponse&&r.authResponse.access_token&&(r.query.access_token=r.authResponse.access_token);var c,d=r.path;r.options=n.clone(r.query),r.data=n.clone(a);var f=u[{"delete":"del"}[r.method]||r.method]||{};if("get"===r.method){var p=d.split(/[\?#]/)[1];p&&(n.extend(r.query,n.param(p)),d=d.replace(/\?.*?(#|$)/,"$1"))}return(c=d.match(/#(.+)/,""))?(d=d.split("#")[0],r.path=c[1]):d in f?(r.path=d,d=f[d]):"default"in f&&(d=f["default"]),r.redirect_uri=t.settings.redirect_uri,r.xhr=u.xhr,r.jsonp=u.jsonp,r.form=u.form,"function"==typeof d?d(r,e):e(d),i.proxy},hello.utils.extend(hello.utils,{request:function(e,t){function n(e,t){var n;e.authResponse&&e.authResponse.oauth&&1===parseInt(e.authResponse.oauth.version,10)&&(n=e.query.access_token,delete e.query.access_token,e.proxy=!0),!e.data||"get"!==e.method&&"delete"!==e.method||(o.extend(e.query,e.data),e.data=null);var i=o.qs(e.url,e.query);e.proxy&&(i=o.qs(e.oauth_proxy,{path:i,access_token:n||"",then:e.proxy_response_type||("get"===e.method.toLowerCase()?"redirect":"proxy"),method:e.method.toLowerCase(),suppress_response_codes:!0})),t(i)}var o=this,i=o.error;o.isEmpty(e.data)||"FileList"in window||!o.hasBinary(e.data)||(e.xhr=!1,e.jsonp=!1);var r=this.request_cors(function(){return void 0===e.xhr||e.xhr&&("function"!=typeof e.xhr||e.xhr(e,e.query))});if(r)return void n(e,function(n){var i=o.xhr(e.method,n,e.headers,e.data,t);i.onprogress=e.onprogress||null,i.upload&&e.onuploadprogress&&(i.upload.onprogress=e.onuploadprogress)});var a=e.query;if(e.query=o.clone(e.query),e.callbackID=o.globalEvent(),e.jsonp!==!1){if(e.query.callback=e.callbackID,"function"==typeof e.jsonp&&e.jsonp(e,e.query),"get"===e.method)return void n(e,function(n){o.jsonp(n,t,e.callbackID,e.timeout)});e.query=a}if(e.form!==!1){e.query.redirect_uri=e.redirect_uri,e.query.state=JSON.stringify({callback:e.callbackID});var s;if("function"==typeof e.form&&(s=e.form(e,e.query)),"post"===e.method&&s!==!1)return void n(e,function(n){o.post(n,e.data,s,t,e.callbackID,e.timeout)})}t(i("invalid_request","There was no mechanism for handling this request"))},request_cors:function(e){return"withCredentials"in new XMLHttpRequest&&e()},domInstance:function(e,t){var n="HTML"+(e||"").replace(/^[a-z]/,function(e){return e.toUpperCase()})+"Element";return t?window[n]?t instanceof window[n]:window.Element?t instanceof window.Element&&(!e||t.tagName&&t.tagName.toLowerCase()===e):!(t instanceof Object||t instanceof Array||t instanceof String||t instanceof Number)&&t.tagName&&t.tagName.toLowerCase()===e:!1},clone:function(e){if(null===e||"object"!=typeof e||e instanceof Date||"nodeName"in e||this.isBinary(e)||"function"==typeof FormData&&e instanceof FormData)return e;if(Array.isArray(e))return e.map(this.clone.bind(this));var t={};for(var n in e)t[n]=this.clone(e[n]);return t},xhr:function(e,t,n,o,i){function r(e){for(var t,n={},o=/([a-z\-]+):\s?(.*);?/gi;t=o.exec(e);)n[t[1]]=t[2];return n}var a=new XMLHttpRequest,s=this.error,l=!1;"blob"===e&&(l=e,e="GET"),e=e.toUpperCase(),a.onload=function(t){var n=a.response;try{n=JSON.parse(a.responseText)}catch(o){401===a.status&&(n=s("access_denied",a.statusText))}var l=r(a.getAllResponseHeaders());l.statusCode=a.status,i(n||("GET"===e?s("empty_response","Could not get resource"):{}),l)},a.onerror=function(e){var t=a.responseText;try{t=JSON.parse(a.responseText)}catch(n){}i(t||s("access_denied","Could not get resource"))};var u;if("GET"===e||"DELETE"===e)o=null;else if(o&&"string"!=typeof o&&!(o instanceof FormData)&&!(o instanceof File)&&!(o instanceof Blob)){var c=new FormData;for(u in o)o.hasOwnProperty(u)&&(o[u]instanceof HTMLInputElement?"files"in o[u]&&o[u].files.length>0&&c.append(u,o[u].files[0]):o[u]instanceof Blob?c.append(u,o[u],o.name):c.append(u,o[u]));o=c}if(a.open(e,t,!0),l&&("responseType"in a?a.responseType=l:a.overrideMimeType("text/plain; charset=x-user-defined")),n)for(u in n)a.setRequestHeader(u,n[u]);return a.send(o),a},jsonp:function(e,t,n,o){var i,r=this,a=r.error,s=0,l=document.getElementsByTagName("head")[0],u=a("server_error","server_error"),c=function(){s++||window.setTimeout(function(){t(u),l.removeChild(d)},0)};n=r.globalEvent(function(e){return u=e,!0},n),e=e.replace(new RegExp("=\\?(&|$)"),"="+n+"$1");var d=r.append("script",{id:n,name:n,src:e,async:!0,onload:c,onerror:c,onreadystatechange:function(){/loaded|complete/i.test(this.readyState)&&c()}});window.navigator.userAgent.toLowerCase().indexOf("opera")>-1&&(i=r.append("script",{text:"document.getElementById('"+n+"').onerror();"}),d.async=!1),o&&window.setTimeout(function(){u=a("timeout","timeout"),c()},o),l.appendChild(d),i&&l.appendChild(i)},post:function(e,t,n,o,i,r){var a,s=this,l=s.error,u=document,c=null,d=[],f=0,p=null,m=0,h=function(e){m++||o(e)};s.globalEvent(h,i);var g;try{g=u.createElement('<iframe name="'+i+'">')}catch(v){g=u.createElement("iframe")}if(g.name=i,g.id=i,g.style.display="none",n&&n.callbackonload&&(g.onload=function(){h({response:"posted",message:"Content was posted"})}),r&&setTimeout(function(){h(l("timeout","The post operation timed out"))},r),u.body.appendChild(g),s.domInstance("form",t)){for(c=t.form,f=0;f<c.elements.length;f++)c.elements[f]!==t&&c.elements[f].setAttribute("disabled",!0);t=c}if(s.domInstance("form",t))for(c=t,f=0;f<c.elements.length;f++)c.elements[f].disabled||"file"!==c.elements[f].type||(c.encoding=c.enctype="multipart/form-data",c.elements[f].setAttribute("name","file"));else{for(p in t)t.hasOwnProperty(p)&&s.domInstance("input",t[p])&&"file"===t[p].type&&(c=t[p].form,c.encoding=c.enctype="multipart/form-data");c||(c=u.createElement("form"),u.body.appendChild(c),a=c);var y;for(p in t)if(t.hasOwnProperty(p)){var w=s.domInstance("input",t[p])||s.domInstance("textArea",t[p])||s.domInstance("select",t[p]);if(w&&t[p].form===c)w&&t[p].name!==p&&(t[p].setAttribute("name",p),t[p].name=p);else{var _=c.elements[p];if(y)for(_ instanceof NodeList||(_=[_]),f=0;f<_.length;f++)_[f].parentNode.removeChild(_[f]);y=u.createElement("input"),y.setAttribute("type","hidden"),y.setAttribute("name",p),w?y.value=t[p].value:s.domInstance(null,t[p])?y.value=t[p].innerHTML||t[p].innerText:y.value=t[p],c.appendChild(y)}}for(f=0;f<c.elements.length;f++)y=c.elements[f],y.name in t||y.getAttribute("disabled")===!0||(y.setAttribute("disabled",!0),d.push(y))}c.setAttribute("method","POST"),c.setAttribute("target",i),c.target=i,c.setAttribute("action",e),setTimeout(function(){c.submit(),setTimeout(function(){try{a&&a.parentNode.removeChild(a)}catch(e){try{console.error("HelloJS: could not remove iframe")}catch(t){}}for(var n=0;n<d.length;n++)d[n]&&(d[n].setAttribute("disabled",!1),d[n].disabled=!1)},0)},100)},hasBinary:function(e){for(var t in e)if(e.hasOwnProperty(t)&&this.isBinary(e[t]))return!0;return!1},isBinary:function(e){return e instanceof Object&&(this.domInstance("input",e)&&"file"===e.type||"FileList"in window&&e instanceof window.FileList||"File"in window&&e instanceof window.File||"Blob"in window&&e instanceof window.Blob)},toBlob:function(e){var t=/^data\:([^;,]+(\;charset=[^;,]+)?)(\;base64)?,/i,n=e.match(t);if(!n)return e;for(var o=atob(e.replace(t,"")),i=[],r=0;r<o.length;r++)i.push(o.charCodeAt(r));return new Blob([new Uint8Array(i)],{type:n[1]})}}),function(e){var t=e.api,n=e.utils;n.extend(n,{dataToJSON:function(e){var t=this,n=window,o=e.data;if(t.domInstance("form",o)?o=t.nodeListToJSON(o.elements):"NodeList"in n&&o instanceof NodeList?o=t.nodeListToJSON(o):t.domInstance("input",o)&&(o=t.nodeListToJSON([o])),("File"in n&&o instanceof n.File||"Blob"in n&&o instanceof n.Blob||"FileList"in n&&o instanceof n.FileList)&&(o={file:o}),!("FormData"in n&&o instanceof n.FormData))for(var i in o)if(o.hasOwnProperty(i))if("FileList"in n&&o[i]instanceof n.FileList)1===o[i].length&&(o[i]=o[i][0]);else{if(t.domInstance("input",o[i])&&"file"===o[i].type)continue;t.domInstance("input",o[i])||t.domInstance("select",o[i])||t.domInstance("textArea",o[i])?o[i]=o[i].value:t.domInstance(null,o[i])&&(o[i]=o[i].innerHTML||o[i].innerText)}return e.data=o,o},nodeListToJSON:function(e){for(var t={},n=0;n<e.length;n++){var o=e[n];!o.disabled&&o.name&&("file"===o.type?t[o.name]=o:t[o.name]=o.value||o.innerHTML)}return t}}),e.api=function(){var e=n.args({path:"s!",method:"s",data:"o",timeout:"i",callback:"f"},arguments);return e.data&&n.dataToJSON(e),t.call(this,e)}}(hello),hello.utils.responseHandler(window,window.opener||window.parent),"object"==typeof chrome&&"object"==typeof chrome.identity&&chrome.identity.launchWebAuthFlow&&!function(){function e(t,n){var o={closed:!1};return chrome.identity.launchWebAuthFlow({url:t,interactive:n},function(t){if(void 0===t)return void(o.closed=!0);var n=hello.utils.url(t),i={location:{assign:function(t){e(t,!1)},search:n.search,hash:n.hash,href:n.href},close:function(){}};hello.utils.responseHandler(i,window)}),o}hello.utils.popup=function(t){return e(t,!0)},hello.utils.iframe=function(t){e(t,!1)},hello.utils.request_cors=function(e){return e(),!0};var t={};chrome.storage.local.get("hello",function(e){t=e.hello||{}}),hello.utils.store=function(e,n){return 0===arguments.length?t:1===arguments.length?t[e]||null:n?(t[e]=n,chrome.storage.local.set({hello:t}),n):null===n?(delete t[e],chrome.storage.local.set({hello:t}),null):void 0}}(),function(){if(/^file:\/{3}[^\/]/.test(window.location.href)&&window.cordova){hello.utils.iframe=function(e,t){hello.utils.popup(e,t,{hidden:"yes"})};var e=hello.utils.popup;hello.utils.popup=function(t,n,o){var i=e.call(this,t,n,o);try{if(i&&i.addEventListener){var r=hello.utils.url(n),a=r.origin||r.protocol+"//"+r.hostname;i.addEventListener("loadstart",function(e){var t=e.url;if(0===t.indexOf(a)){var n=hello.utils.url(t),o={location:{assign:function(e){i.executeScript({code:'window.location.href = "'+e+';"'})},search:n.search,hash:n.hash,href:n.href},close:function(){if(i.close){i.close();try{i.closed=!0}catch(e){}}}};hello.utils.responseHandler(o,window)}})}}catch(s){}return i}}}(),function(e){function t(e){e&&"error"in e&&(e.error={code:"server_error",message:e.error.message||e.error})}function n(t,n,o){if(!("object"!=typeof t||"undefined"!=typeof Blob&&t instanceof Blob||"undefined"!=typeof ArrayBuffer&&t instanceof ArrayBuffer||"error"in t)){var i=("app_folder"!==t.root?t.root:"")+t.path.replace(/\&/g,"%26");i=i.replace(/^\//,""),t.thumb_exists&&(t.thumbnail=o.oauth_proxy+"?path="+encodeURIComponent("https://api-content.dropbox.com/1/thumbnails/auto/"+i+"?format=jpeg&size=m")+"&access_token="+o.options.access_token),t.type=t.is_dir?"folder":t.mime_type,t.name=t.path.replace(/.*\//g,""),t.is_dir?t.files=i.replace(/^\//,""):(t.downloadLink=e.settings.oauth_proxy+"?path="+encodeURIComponent("https://api-content.dropbox.com/1/files/auto/"+i)+"&access_token="+o.options.access_token,t.file="https://api-content.dropbox.com/1/files/auto/"+i),t.id||(t.id=t.path.replace(/^\//,""))}}function o(e){return function(t,n){delete t.query.limit,n(e)}}var i={version:"1.0",auth:"https://www.dropbox.com/1/oauth/authorize",request:"https://api.dropbox.com/1/oauth/request_token",token:"https://api.dropbox.com/1/oauth/access_token"},r={version:2,auth:"https://www.dropbox.com/1/oauth2/authorize",grant:"https://api.dropbox.com/1/oauth2/token"};e.init({dropbox:{name:"Dropbox",oauth:r,login:function(t){t.qs.scope="";var n=decodeURIComponent(t.qs.redirect_uri);0===n.indexOf("http:")&&0!==n.indexOf("http://localhost/")?e.services.dropbox.oauth=i:e.services.dropbox.oauth=r,t.options.popup.width=1e3,t.options.popup.height=1e3},base:"https://api.dropbox.com/1/",root:"sandbox",get:{me:"account/info","me/files":o("metadata/auto/@{parent|}"),"me/folder":o("metadata/auto/@{id}"),"me/folders":o("metadata/auto/"),"default":function(e,t){e.path.match("https://api-content.dropbox.com/1/files/")&&(e.method="blob"),t(e.path)}},post:{"me/files":function(t,n){var o=t.data.parent,i=t.data.name;t.data={file:t.data.file},"string"==typeof t.data.file&&(t.data.file=e.utils.toBlob(t.data.file)),n("https://api-content.dropbox.com/1/files_put/auto/"+o+"/"+i)},"me/folders":function(t,n){var o=t.data.name;t.data={},n("fileops/create_folder?root=@{root|sandbox}&"+e.utils.param({path:o}))}},del:{"me/files":"fileops/delete?root=@{root|sandbox}&path=@{id}","me/folder":"fileops/delete?root=@{root|sandbox}&path=@{id}"},wrap:{me:function(e){if(t(e),!e.uid)return e;e.name=e.display_name;var n=e.name.split(" ");return e.first_name=n.shift(),e.last_name=n.join(" "),e.id=e.uid,delete e.uid,delete e.display_name,e},"default":function(e,o,i){return t(e),e.is_dir&&e.contents&&(e.data=e.contents,delete e.contents,e.data.forEach(function(t){t.root=e.root,n(t,o,i)})),n(e,o,i),e.is_deleted&&(e.success=!0),e}},xhr:function(e){if(e.data&&e.data.file){var t=e.data.file;t&&(t.files?e.data=t.files[0]:e.data=t)}return"delete"===e.method&&(e.method="post"),!0},form:function(e,t){delete t.state,delete t.redirect_uri}}})}(hello),function(e){function t(e){return e.id&&(e.thumbnail=e.picture="https://graph.facebook.com/"+e.id+"/picture"),e}function n(e){return"data"in e&&e.data.forEach(t),e}function o(e,t,n){if("boolean"==typeof e&&(e={success:e}),e&&"data"in e){var o=n.query.access_token;if(!(e.data instanceof Array)){var r=e.data;delete e.data,e.data=[r]}e.data.forEach(function(e){e.picture&&(e.thumbnail=e.picture),e.pictures=(e.images||[]).sort(function(e,t){return e.width-t.width}),e.cover_photo&&e.cover_photo.id&&(e.thumbnail=i+e.cover_photo.id+"/picture?access_token="+o),"album"===e.type&&(e.files=e.photos=i+e.id+"/photos"),e.can_upload&&(e.upload_location=i+e.id+"/photos")})}return e}e.init({facebook:{name:"Facebook",oauth:{version:2,auth:"https://www.facebook.com/dialog/oauth/",grant:"https://graph.facebook.com/oauth/access_token"
},scope:{basic:"public_profile",email:"email",share:"user_posts",birthday:"user_birthday",events:"user_events",photos:"user_photos",videos:"user_videos",friends:"user_friends",files:"user_photos,user_videos",publish_files:"user_photos,user_videos,publish_actions",publish:"publish_actions",offline_access:""},refresh:!1,login:function(e){e.options.force&&(e.qs.auth_type="reauthenticate"),e.qs.display=e.options.display||"popup"},logout:function(t,n){var o=e.utils.globalEvent(t),i=encodeURIComponent(e.settings.redirect_uri+"?"+e.utils.param({callback:o,result:JSON.stringify({force:!0}),state:"{}"})),r=(n.authResponse||{}).access_token;return e.utils.iframe("https://www.facebook.com/logout.php?next="+i+"&access_token="+r),r?void 0:!1},base:"https://graph.facebook.com/v2.7/",get:{me:"me?fields=email,first_name,last_name,name,timezone,verified","me/friends":"me/friends","me/following":"me/friends","me/followers":"me/friends","me/share":"me/feed","me/like":"me/likes","me/files":"me/albums","me/albums":"me/albums?fields=cover_photo,name","me/album":"@{id}/photos?fields=picture","me/photos":"me/photos","me/photo":"@{id}","friend/albums":"@{id}/albums","friend/photos":"@{id}/photos"},post:{"me/share":"me/feed","me/photo":"@{id}"},wrap:{me:t,"me/friends":n,"me/following":n,"me/followers":n,"me/albums":o,"me/photos":o,"me/files":o,"default":o},xhr:function(t,n){return"get"!==t.method&&"post"!==t.method||(n.suppress_response_codes=!0),"post"===t.method&&t.data&&"string"==typeof t.data.file&&(t.data.file=e.utils.toBlob(t.data.file)),!0},jsonp:function(t,n){var o=t.method;"get"===o||e.utils.hasBinary(t.data)?"delete"===t.method&&(n.method="delete",t.method="post"):(t.data.method=o,t.method="get")},form:function(e){return{callbackonload:!0}}}});var i="https://graph.facebook.com/"}(hello),function(e){function t(t,n,o){var i=(o?"":"flickr:")+"?method="+t+"&api_key="+e.services.flickr.id+"&format=json";for(var r in n)n.hasOwnProperty(r)&&(i+="&"+r+"="+n[r]);return i}function n(t){var n=e.getAuthResponse("flickr");t(n&&n.user_nsid?n.user_nsid:null)}function o(e,o){return o||(o={}),function(i,r){n(function(n){o.user_id=n,r(t(e,o,!0))})}}function i(e,t){var n="https://www.flickr.com/images/buddyicon.gif";return e.nsid&&e.iconserver&&e.iconfarm&&(n="https://farm"+e.iconfarm+".staticflickr.com/"+e.iconserver+"/buddyicons/"+e.nsid+(t?"_"+t:"")+".jpg"),n}function r(e,t,n,o,i){return i=i?"_"+i:"","https://farm"+t+".staticflickr.com/"+n+"/"+e+"_"+o+i+".jpg"}function a(e){e&&e.stat&&"ok"!=e.stat.toLowerCase()&&(e.error={code:"invalid_request",message:e.message})}function s(e){if(e.photoset||e.photos){var t="photoset"in e?"photoset":"photos";e=u(e,t),d(e),e.data=e.photo,delete e.photo;for(var n=0;n<e.data.length;n++){var o=e.data[n];o.name=o.title,o.picture=r(o.id,o.farm,o.server,o.secret,""),o.pictures=l(o.id,o.farm,o.server,o.secret),o.source=r(o.id,o.farm,o.server,o.secret,"b"),o.thumbnail=r(o.id,o.farm,o.server,o.secret,"m")}}return e}function l(e,t,n,o){var i=2048,a=[{id:"t",max:100},{id:"m",max:240},{id:"n",max:320},{id:"",max:500},{id:"z",max:640},{id:"c",max:800},{id:"b",max:1024},{id:"h",max:1600},{id:"k",max:2048},{id:"o",max:i}];return a.map(function(i){return{source:r(e,t,n,o,i.id),width:i.max,height:i.max}})}function u(e,t){return t in e?e=e[t]:"error"in e||(e.error={code:"invalid_request",message:e.message||"Failed to get data from Flickr"}),e}function c(e){if(a(e),e.contacts){e=u(e,"contacts"),d(e),e.data=e.contact,delete e.contact;for(var t=0;t<e.data.length;t++){var n=e.data[t];n.id=n.nsid,n.name=n.realname||n.username,n.thumbnail=i(n,"m")}}return e}function d(e){e.page&&e.pages&&e.page!==e.pages&&(e.paging={next:"?page="+ ++e.page})}e.init({flickr:{name:"Flickr",oauth:{version:"1.0a",auth:"https://www.flickr.com/services/oauth/authorize?perms=read",request:"https://www.flickr.com/services/oauth/request_token",token:"https://www.flickr.com/services/oauth/access_token"},base:"https://api.flickr.com/services/rest",get:{me:o("flickr.people.getInfo"),"me/friends":o("flickr.contacts.getList",{per_page:"@{limit|50}"}),"me/following":o("flickr.contacts.getList",{per_page:"@{limit|50}"}),"me/followers":o("flickr.contacts.getList",{per_page:"@{limit|50}"}),"me/albums":o("flickr.photosets.getList",{per_page:"@{limit|50}"}),"me/album":o("flickr.photosets.getPhotos",{photoset_id:"@{id}"}),"me/photos":o("flickr.people.getPhotos",{per_page:"@{limit|50}"})},wrap:{me:function(e){if(a(e),e=u(e,"person"),e.id){if(e.realname){e.name=e.realname._content;var t=e.name.split(" ");e.first_name=t.shift(),e.last_name=t.join(" ")}e.thumbnail=i(e,"l"),e.picture=i(e,"l")}return e},"me/friends":c,"me/followers":c,"me/following":c,"me/albums":function(e){return a(e),e=u(e,"photosets"),d(e),e.photoset&&(e.data=e.photoset,e.data.forEach(function(e){e.name=e.title._content,e.photos="https://api.flickr.com/services/rest"+t("flickr.photosets.getPhotos",{photoset_id:e.id},!0)}),delete e.photoset),e},"me/photos":function(e){return a(e),s(e)},"default":function(e){return a(e),s(e)}},xhr:!1,jsonp:function(e,t){"get"==e.method&&(delete t.callback,t.jsoncallback=e.callbackID)}}})}(hello),function(e){function t(e){!e.meta||400!==e.meta.code&&401!==e.meta.code||(e.error={code:"access_denied",message:e.meta.errorDetail})}function n(e){e&&e.id&&(e.thumbnail=e.photo.prefix+"100x100"+e.photo.suffix,e.name=e.firstName+" "+e.lastName,e.first_name=e.firstName,e.last_name=e.lastName,e.contact&&e.contact.email&&(e.email=e.contact.email))}function o(e,t){var n=t.access_token;return delete t.access_token,t.oauth_token=n,t.v=20121125,!0}e.init({foursquare:{name:"Foursquare",oauth:{version:2,auth:"https://foursquare.com/oauth2/authenticate",grant:"https://foursquare.com/oauth2/access_token"},refresh:!0,base:"https://api.foursquare.com/v2/",get:{me:"users/self","me/friends":"users/self/friends","me/followers":"users/self/friends","me/following":"users/self/friends"},wrap:{me:function(e){return t(e),e&&e.response&&(e=e.response.user,n(e)),e},"default":function(e){return t(e),e&&"response"in e&&"friends"in e.response&&"items"in e.response.friends&&(e.data=e.response.friends.items,e.data.forEach(n),delete e.response),e}},xhr:o,jsonp:o}})}(hello),function(e){function t(e,t){var n=t?t.statusCode:e&&"meta"in e&&"status"in e.meta&&e.meta.status;401!==n&&403!==n||(e.error={code:"access_denied",message:e.message||(e.data?e.data.message:"Could not get response")},delete e.message)}function n(e){e.id&&(e.thumbnail=e.picture=e.avatar_url,e.name=e.login)}function o(e,t,n){if(e.data&&e.data.length&&t&&t.Link){var o=t.Link.match(/<(.*?)>;\s*rel=\"next\"/);o&&(e.paging={next:o[1]})}}e.init({github:{name:"GitHub",oauth:{version:2,auth:"https://github.com/login/oauth/authorize",grant:"https://github.com/login/oauth/access_token",response_type:"code"},scope:{email:"user:email"},base:"https://api.github.com/",get:{me:"user","me/friends":"user/following?per_page=@{limit|100}","me/following":"user/following?per_page=@{limit|100}","me/followers":"user/followers?per_page=@{limit|100}","me/like":"user/starred?per_page=@{limit|100}"},wrap:{me:function(e,o){return t(e,o),n(e),e},"default":function(e,i,r){return t(e,i),Array.isArray(e)&&(e={data:e}),e.data&&(o(e,i,r),e.data.forEach(n)),e}},xhr:function(e){return"get"!==e.method&&e.data&&(e.headers=e.headers||{},e.headers["Content-Type"]="application/json","object"==typeof e.data&&(e.data=JSON.stringify(e.data))),!0}}})}(hello),function(e){function t(e){return parseInt(e,10)}function n(e){return c(e),e.data=e.items,delete e.items,e}function o(e){return e.error?void 0:(e.name||(e.name=e.title||e.message),e.picture||(e.picture=e.thumbnailLink),e.thumbnail||(e.thumbnail=e.thumbnailLink),"application/vnd.google-apps.folder"===e.mimeType&&(e.type="folder",e.files="https://www.googleapis.com/drive/v2/files?q=%22"+e.id+"%22+in+parents"),e)}function i(e){return{source:e.url,width:e.width,height:e.height}}function r(e){e.data=e.feed.entry.map(u),delete e.feed}function a(e){if(c(e),"feed"in e&&"entry"in e.feed)e.data=e.feed.entry.map(u),delete e.feed;else{if("entry"in e)return u(e.entry);"items"in e?(e.data=e.items.map(o),delete e.items):o(e)}return e}function s(e){e.name=e.displayName||e.name,e.picture=e.picture||(e.image?e.image.url:null),e.thumbnail=e.picture}function l(e,t,n){c(e);if("feed"in e&&"entry"in e.feed){for(var o=n.query.access_token,i=0;i<e.feed.entry.length;i++){var r=e.feed.entry[i];if(r.id=r.id.$t,r.name=r.title.$t,delete r.title,r.gd$email&&(r.email=r.gd$email&&r.gd$email.length>0?r.gd$email[0].address:null,r.emails=r.gd$email,delete r.gd$email),r.updated&&(r.updated=r.updated.$t),r.link){var a=r.link.length>0?r.link[0].href:null;a&&r.link[0].gd$etag&&(a+=(a.indexOf("?")>-1?"&":"?")+"access_token="+o,r.picture=a,r.thumbnail=a),delete r.link}r.category&&delete r.category}e.data=e.feed.entry,delete e.feed}return e}function u(e){var t,n=e.media$group,o=n.media$content.length?n.media$content[0]:{},r=n.media$content||[],a=n.media$thumbnail||[],s=r.concat(a).map(i).sort(function(e,t){return e.width-t.width}),l=0,u={id:e.id.$t,name:e.title.$t,description:e.summary.$t,updated_time:e.updated.$t,created_time:e.published.$t,picture:o?o.url:null,pictures:s,images:[],thumbnail:o?o.url:null,width:o.width,height:o.height};if("link"in e)for(l=0;l<e.link.length;l++){var c=e.link[l];if(c.rel.match(/\#feed$/)){u.upload_location=u.files=u.photos=c.href;break}}if("category"in e&&e.category.length)for(t=e.category,l=0;l<t.length;l++)t[l].scheme&&t[l].scheme.match(/\#kind$/)&&(u.type=t[l].term.replace(/^.*?\#/,""));return"media$thumbnail"in n&&n.media$thumbnail.length&&(t=n.media$thumbnail,u.thumbnail=t[0].url,u.images=t.map(i)),t=n.media$content,t&&t.length&&u.images.push(i(t[0])),u}function c(e){if("feed"in e&&e.feed.openSearch$itemsPerPage){var n=t(e.feed.openSearch$itemsPerPage.$t),o=t(e.feed.openSearch$startIndex.$t),i=t(e.feed.openSearch$totalResults.$t);i>o+n&&(e.paging={next:"?start="+(o+n)})}else"nextPageToken"in e&&(e.paging={next:"?pageToken="+e.nextPageToken})}function d(){function e(e){var n=new FileReader;n.onload=function(n){t(btoa(n.target.result),e.type+r+"Content-Transfer-Encoding: base64")},n.readAsBinaryString(e)}function t(e,t){n.push(r+"Content-Type: "+t+r+r+e),i--,s()}var n=[],o=(1e10*Math.random()).toString(32),i=0,r="\r\n",a=r+"--"+o,s=function(){},l=/^data\:([^;,]+(\;charset=[^;,]+)?)(\;base64)?,/i;this.append=function(n,o){"string"!=typeof n&&"length"in Object(n)||(n=[n]);for(var a=0;a<n.length;a++){i++;var s=n[a];if("undefined"!=typeof File&&s instanceof File||"undefined"!=typeof Blob&&s instanceof Blob)e(s);else if("string"==typeof s&&s.match(l)){var u=s.match(l);t(s.replace(l,""),u[1]+r+"Content-Transfer-Encoding: base64")}else t(s,o)}},this.onready=function(e){(s=function(){0===i&&(n.unshift(""),n.push("--"),e(n.join(a),o),n=[])})()}}function f(e,t){var n={};e.data&&"undefined"!=typeof HTMLInputElement&&e.data instanceof HTMLInputElement&&(e.data={file:e.data}),!e.data.name&&Object(Object(e.data.file).files).length&&"post"===e.method&&(e.data.name=e.data.file.files[0].name),"post"===e.method?e.data={title:e.data.name,parents:[{id:e.data.parent||"root"}],file:e.data.file}:(n=e.data,e.data={},n.parent&&(e.data.parents=[{id:e.data.parent||"root"}]),n.file&&(e.data.file=n.file),n.name&&(e.data.title=n.name));var o;if("file"in e.data&&(o=e.data.file,delete e.data.file,"object"==typeof o&&"files"in o&&(o=o.files),!o||!o.length))return void t({error:{code:"request_invalid",message:"There were no files attached with this request to upload"}});var i=new d;i.append(JSON.stringify(e.data),"application/json"),o&&i.append(o),i.onready(function(o,i){e.headers["content-type"]='multipart/related; boundary="'+i+'"',e.data=o,t("upload/drive/v2/files"+(n.id?"/"+n.id:"")+"?uploadType=multipart")})}function p(e){if("object"==typeof e.data)try{e.data=JSON.stringify(e.data),e.headers["content-type"]="application/json"}catch(t){}}var m="https://www.google.com/m8/feeds/contacts/default/full?v=3.0&alt=json&max-results=@{limit|1000}&start-index=@{start|1}";e.init({google:{name:"Google Plus",oauth:{version:2,auth:"https://accounts.google.com/o/oauth2/auth",grant:"https://accounts.google.com/o/oauth2/token"},scope:{basic:"https://www.googleapis.com/auth/plus.me profile",email:"email",birthday:"",events:"",photos:"https://picasaweb.google.com/data/",videos:"http://gdata.youtube.com",friends:"https://www.google.com/m8/feeds, https://www.googleapis.com/auth/plus.login",files:"https://www.googleapis.com/auth/drive.readonly",publish:"",publish_files:"https://www.googleapis.com/auth/drive",share:"",create_event:"",offline_access:""},scope_delim:" ",login:function(e){"code"===e.qs.response_type&&(e.qs.access_type="offline"),e.options.force&&(e.qs.approval_prompt="force")},base:"https://www.googleapis.com/",get:{me:"plus/v1/people/me","me/friends":"plus/v1/people/me/people/visible?maxResults=@{limit|100}","me/following":m,"me/followers":m,"me/contacts":m,"me/share":"plus/v1/people/me/activities/public?maxResults=@{limit|100}","me/feed":"plus/v1/people/me/activities/public?maxResults=@{limit|100}","me/albums":"https://picasaweb.google.com/data/feed/api/user/default?alt=json&max-results=@{limit|100}&start-index=@{start|1}","me/album":function(e,t){var n=e.query.id;delete e.query.id,t(n.replace("/entry/","/feed/"))},"me/photos":"https://picasaweb.google.com/data/feed/api/user/default?alt=json&kind=photo&max-results=@{limit|100}&start-index=@{start|1}","me/file":"drive/v2/files/@{id}","me/files":"drive/v2/files?q=%22@{parent|root}%22+in+parents+and+trashed=false&maxResults=@{limit|100}","me/folders":"drive/v2/files?q=%22@{id|root}%22+in+parents+and+mimeType+=+%22application/vnd.google-apps.folder%22+and+trashed=false&maxResults=@{limit|100}","me/folder":"drive/v2/files?q=%22@{id|root}%22+in+parents+and+trashed=false&maxResults=@{limit|100}"},post:{"me/files":f,"me/folders":function(e,t){e.data={title:e.data.name,parents:[{id:e.data.parent||"root"}],mimeType:"application/vnd.google-apps.folder"},t("drive/v2/files")}},put:{"me/files":f},del:{"me/files":"drive/v2/files/@{id}","me/folder":"drive/v2/files/@{id}"},patch:{"me/file":"drive/v2/files/@{id}"},wrap:{me:function(e){return e.id&&(e.last_name=e.family_name||(e.name?e.name.familyName:null),e.first_name=e.given_name||(e.name?e.name.givenName:null),e.emails&&e.emails.length&&(e.email=e.emails[0].value),s(e)),e},"me/friends":function(e){return e.items&&(c(e),e.data=e.items,e.data.forEach(s),delete e.items),e},"me/contacts":l,"me/followers":l,"me/following":l,"me/share":n,"me/feed":n,"me/albums":a,"me/photos":r,"default":a},xhr:function(t){return"post"===t.method||"put"===t.method?p(t):"patch"===t.method&&(e.utils.extend(t.query,t.data),t.data=null),!0},form:!1}})}(hello),function(e){function t(e){return{source:e.url,width:e.width,height:e.height}}function n(e){return"string"==typeof e?{error:{code:"invalid_request",message:e}}:(e&&"meta"in e&&"error_type"in e.meta&&(e.error={code:e.meta.error_type,message:e.meta.error_message}),e)}function o(e){return r(e),e&&"data"in e&&e.data.forEach(i),e}function i(e){e.id&&(e.thumbnail=e.profile_picture,e.name=e.full_name||e.username)}function r(e){"pagination"in e&&(e.paging={next:e.pagination.next_url},delete e.pagination)}e.init({instagram:{name:"Instagram",oauth:{version:2,auth:"https://instagram.com/oauth/authorize/",grant:"https://api.instagram.com/oauth/access_token"},refresh:!0,scope:{basic:"basic",photos:"",friends:"relationships",publish:"likes comments",email:"",share:"",publish_files:"",files:"",videos:"",offline_access:""},scope_delim:" ",base:"https://api.instagram.com/v1/",get:{me:"users/self","me/feed":"users/self/feed?count=@{limit|100}","me/photos":"users/self/media/recent?min_id=0&count=@{limit|100}","me/friends":"users/self/follows?count=@{limit|100}","me/following":"users/self/follows?count=@{limit|100}","me/followers":"users/self/followed-by?count=@{limit|100}","friend/photos":"users/@{id}/media/recent?min_id=0&count=@{limit|100}"},post:{"me/like":function(e,t){var n=e.data.id;e.data={},t("media/"+n+"/likes")}},del:{"me/like":"media/@{id}/likes"},wrap:{me:function(e){return n(e),"data"in e&&(e.id=e.data.id,e.thumbnail=e.data.profile_picture,e.name=e.data.full_name||e.data.username),e},"me/friends":o,"me/following":o,"me/followers":o,"me/photos":function(e){return n(e),r(e),"data"in e&&(e.data=e.data.filter(function(e){return"image"===e.type}),e.data.forEach(function(e){e.name=e.caption?e.caption.text:null,e.thumbnail=e.images.thumbnail.url,e.picture=e.images.standard_resolution.url,e.pictures=Object.keys(e.images).map(function(n){var o=e.images[n];return t(o)}).sort(function(e,t){return e.width-t.width})})),e},"default":function(e){return e=n(e),r(e),e}},xhr:function(e,t){var n=e.method,o="get"!==n;return o&&("post"!==n&&"put"!==n||!e.query.access_token||(e.data.access_token=e.query.access_token,delete e.query.access_token),e.proxy=o),o},form:!1}})}(hello),function(e){function t(e,t){var n,i;return e&&"Message"in e&&(i=e.Message,delete e.Message,"ErrorCode"in e?(n=e.ErrorCode,delete e.ErrorCode):n=o(t),e.error={code:n,message:i,details:e}),e}function n(e,t){var n=t.access_token;return delete t.access_token,e.headers.Authorization="Bearer "+n,"get"!==e.method&&e.data&&(e.headers["Content-Type"]="application/json","object"==typeof e.data&&(e.data=JSON.stringify(e.data))),"put"===e.method&&(e.method="patch"),!0}function o(e){switch(e.statusCode){case 400:return"invalid_request";case 403:return"stale_token";case 401:return"invalid_token";case 500:return"server_error";default:return"server_error"}}e.init({joinme:{name:"join.me",oauth:{version:2,auth:"https://secure.join.me/api/public/v1/auth/oauth2",grant:"https://secure.join.me/api/public/v1/auth/oauth2"},refresh:!1,scope:{basic:"user_info",user:"user_info",scheduler:"scheduler",start:"start_meeting",email:"",friends:"",share:"",publish:"",photos:"",publish_files:"",files:"",videos:"",offline_access:""},scope_delim:" ",login:function(e){e.options.popup.width=400,e.options.popup.height=700},base:"https://api.join.me/v1/",get:{me:"user",meetings:"meetings","meetings/info":"meetings/@{id}"},post:{"meetings/start/adhoc":function(e,t){t("meetings/start")},"meetings/start/scheduled":function(e,t){var n=e.data.meetingId;e.data={},t("meetings/"+n+"/start")},"meetings/schedule":function(e,t){t("meetings")}},patch:{"meetings/update":function(e,t){t("meetings/"+e.data.meetingId)}},del:{"meetings/delete":"meetings/@{id}"},wrap:{me:function(e,n){return t(e,n),e.email?(e.name=e.fullName,e.first_name=e.name.split(" ")[0],e.last_name=e.name.split(" ")[1],e.id=e.email,e):e},"default":function(e,n){return t(e,n),e}},xhr:n}})}(hello),function(e){function t(e){e&&"errorCode"in e&&(e.error={code:e.status,message:e.message})}function n(e){return e.error?void 0:(e.first_name=e.firstName,e.last_name=e.lastName,e.name=e.formattedName||e.first_name+" "+e.last_name,e.thumbnail=e.pictureUrl,e.email=e.emailAddress,e)}function o(e){return t(e),i(e),e.values&&(e.data=e.values.map(n),delete e.values),e}function i(e){"_count"in e&&"_start"in e&&e._count+e._start<e._total&&(e.paging={next:"?start="+(e._start+e._count)+"&count="+e._count})}function r(e,t){"{}"===JSON.stringify(e)&&200===t.statusCode&&(e.success=!0)}function a(e){e.access_token&&(e.oauth2_access_token=e.access_token,delete e.access_token)}function s(e,t){e.headers["x-li-format"]="json";var n=e.data.id;e.data=("delete"!==e.method).toString(),e.method="put",t("people/~/network/updates/key="+n+"/is-liked")}e.init({linkedin:{oauth:{version:2,response_type:"code",auth:"https://www.linkedin.com/uas/oauth2/authorization",grant:"https://www.linkedin.com/uas/oauth2/accessToken"},refresh:!0,scope:{basic:"r_basicprofile",email:"r_emailaddress",files:"",friends:"",photos:"",publish:"w_share",publish_files:"w_share",share:"",videos:"",offline_access:""},scope_delim:" ",base:"https://api.linkedin.com/v1/",get:{me:"people/~:(picture-url,first-name,last-name,id,formatted-name,email-address)","me/share":"people/~/network/updates?count=@{limit|250}"},post:{"me/share":function(e,t){var n={visibility:{code:"anyone"}};e.data.id?n.attribution={share:{id:e.data.id}}:(n.comment=e.data.message,e.data.picture&&e.data.link&&(n.content={"submitted-url":e.data.link,"submitted-image-url":e.data.picture})),e.data=JSON.stringify(n),t("people/~/shares?format=json")},"me/like":s},del:{"me/like":s},wrap:{me:function(e){return t(e),n(e),e},"me/friends":o,"me/following":o,"me/followers":o,"me/share":function(e){return t(e),i(e),e.values&&(e.data=e.values.map(n),e.data.forEach(function(e){e.message=e.headline}),delete e.values),e},"default":function(e,n){t(e),r(e,n),i(e)}},jsonp:function(e,t){a(t),"get"===e.method&&(t.format="jsonp",t["error-callback"]=e.callbackID)},xhr:function(e,t){return"get"!==e.method?(a(t),e.headers["Content-Type"]="application/json",e.headers["x-li-format"]="json",e.proxy=!0,!0):!1}}})}(hello),function(e){function t(e,t){var n=t.access_token;return delete t.access_token,t.oauth_token=n,t["_status_code_map[302]"]=200,!0}function n(e){return e.id&&(e.picture=e.avatar_url,e.thumbnail=e.avatar_url,e.name=e.username||e.full_name),e}function o(e){"next_href"in e&&(e.paging={next:e.next_href})}e.init({soundcloud:{name:"SoundCloud",oauth:{version:2,auth:"https://soundcloud.com/connect",grant:"https://soundcloud.com/oauth2/token"},base:"https://api.soundcloud.com/",get:{me:"me.json","me/friends":"me/followings.json","me/followers":"me/followers.json","me/following":"me/followings.json","default":function(e,t){t(e.path+".json")}},wrap:{me:function(e){return n(e),e},"default":function(e){return Array.isArray(e)&&(e={data:e.map(n)}),o(e),e}},xhr:t,jsonp:t}})}(hello),function(e){function t(e){if(e.id){if(e.name){var t=e.name.split(" ");e.first_name=t.shift(),e.last_name=t.join(" ")}e.thumbnail=e.profile_image_url_https||e.profile_image_url}return e}function n(e){return o(e),i(e),e.users&&(e.data=e.users.map(t),delete e.users),e}function o(e){if(e.errors){var t=e.errors[0];e.error={code:"request_failed",message:t.message}}}function i(e){"next_cursor_str"in e&&(e.paging={next:"?cursor="+e.next_cursor_str})}function r(e){return Array.isArray(e)?{data:e}:e}var a="https://api.twitter.com/";e.init({twitter:{oauth:{version:"1.0a",auth:a+"oauth/authenticate",request:a+"oauth/request_token",token:a+"oauth/access_token"},login:function(e){var t="?force_login=true";this.oauth.auth=this.oauth.auth.replace(t,"")+(e.options.force?t:"")},base:a+"1.1/",get:{me:"account/verify_credentials.json","me/friends":"friends/list.json?count=@{limit|200}","me/following":"friends/list.json?count=@{limit|200}","me/followers":"followers/list.json?count=@{limit|200}","me/share":"statuses/user_timeline.json?count=@{limit|200}","me/like":"favorites/list.json?count=@{limit|200}"},post:{"me/share":function(t,n){var o=t.data;t.data=null;var i=[];o.message&&(i.push(o.message),delete o.message),o.link&&(i.push(o.link),delete o.link),o.picture&&(i.push(o.picture),delete o.picture),i.length&&(o.status=i.join(" ")),o.file?(o["media[]"]=o.file,delete o.file,t.data=o,n("statuses/update_with_media.json")):"id"in o?n("statuses/retweet/"+o.id+".json"):(e.utils.extend(t.query,o),n("statuses/update.json?include_entities=1"))},"me/like":function(e,t){var n=e.data.id;e.data=null,t("favorites/create.json?id="+n)}},del:{"me/like":function(){p.method="post";var e=p.data.id;p.data=null,callback("favorites/destroy.json?id="+e)}},wrap:{me:function(e){return o(e),t(e),e},"me/friends":n,"me/followers":n,"me/following":n,"me/share":function(e){return o(e),i(e),!e.error&&"length"in e?{data:e}:e},"default":function(e){return e=r(e),i(e),e}},xhr:function(e){return"get"!==e.method}}})}(hello),function(e){function t(e,t){return null!==e&&"response"in e&&null!==e.response&&e.response.length&&(e=e.response[0],e.id=e.uid,e.thumbnail=e.picture=e.photo_max,e.name=e.first_name+" "+e.last_name,t.authResponse&&null!==t.authResponse.email&&(e.email=t.authResponse.email)),e}function n(e){if(e.error){var t=e.error;e.error={code:t.error_code,message:t.error_msg}}}e.init({vk:{name:"Vk",oauth:{version:2,auth:"https://oauth.vk.com/authorize",grant:"https://oauth.vk.com/access_token"},scope:{email:"email",friends:"friends",photos:"photos",videos:"video",share:"share",offline_access:"offline"},refresh:!0,login:function(e){e.qs.display=window.navigator&&window.navigator.userAgent&&/ipad|phone|phone|android/.test(window.navigator.userAgent.toLowerCase())?"mobile":"popup"},base:"https://api.vk.com/method/",get:{me:function(e,t){e.query.fields="id,first_name,last_name,photo_max",t("users.get")}},wrap:{me:function(e,o,i){return n(e),t(e,i)}},xhr:!1,jsonp:!0,form:!1}})}(hello),function(e){function t(e){return"data"in e&&e.data.forEach(function(e){e.picture&&(e.thumbnail=e.picture),e.images&&(e.pictures=e.images.map(n).sort(function(e,t){return e.width-t.width}))}),e}function n(e){return{width:e.width,height:e.height,source:e.source}}function o(e){return"data"in e&&e.data.forEach(function(e){e.photos=e.files="https://apis.live.net/v5.0/"+e.id+"/photos"}),e}function i(e,t,n){if(e.id){var o=n.query.access_token;if(e.emails&&(e.email=e.emails.preferred),e.is_friend!==!1){var i=e.user_id||e.id;e.thumbnail=e.picture="https://apis.live.net/v5.0/"+i+"/picture?access_token="+o}}return e}function r(e,t,n){return"data"in e&&e.data.forEach(function(e){i(e,t,n)}),e}e.init({windows:{name:"Windows live",oauth:{version:2,auth:"https://login.live.com/oauth20_authorize.srf",grant:"https://login.live.com/oauth20_token.srf"},refresh:!0,logout:function(){return"http://login.live.com/oauth20_logout.srf?ts="+(new Date).getTime()},scope:{basic:"wl.signin,wl.basic",email:"wl.emails",birthday:"wl.birthday",events:"wl.calendars",photos:"wl.photos",videos:"wl.photos",friends:"wl.contacts_emails",files:"wl.skydrive",publish:"wl.share",publish_files:"wl.skydrive_update",share:"wl.share",create_event:"wl.calendars_update,wl.events_create",offline_access:"wl.offline_access"},base:"https://apis.live.net/v5.0/",get:{me:"me","me/friends":"me/friends","me/following":"me/contacts","me/followers":"me/friends","me/contacts":"me/contacts","me/albums":"me/albums","me/album":"@{id}/files","me/photo":"@{id}","me/files":"@{parent|me/skydrive}/files","me/folders":"@{id|me/skydrive}/files","me/folder":"@{id|me/skydrive}/files"},post:{"me/albums":"me/albums","me/album":"@{id}/files/","me/folders":"@{id|me/skydrive/}","me/files":"@{parent|me/skydrive}/files"},del:{"me/album":"@{id}","me/photo":"@{id}","me/folder":"@{id}","me/files":"@{id}"},wrap:{me:i,"me/friends":r,"me/contacts":r,"me/followers":r,"me/following":r,"me/albums":o,"me/photos":t,"default":t},xhr:function(t){return"get"===t.method||"delete"===t.method||e.utils.hasBinary(t.data)||("string"==typeof t.data.file?t.data.file=e.utils.toBlob(t.data.file):(t.data=JSON.stringify(t.data),t.headers={"Content-Type":"application/json"})),!0},jsonp:function(t){"get"===t.method||e.utils.hasBinary(t.data)||(t.data.method=t.method,t.method="get")}}})}(hello),function(e){function t(e){e&&"meta"in e&&"error_type"in e.meta&&(e.error={code:e.meta.error_type,message:e.meta.error_message})}function n(e){if(t(e),e.query&&e.query.results&&e.query.results.profile){e=e.query.results.profile,e.id=e.guid,e.last_name=e.familyName,e.first_name=e.givenName||e.nickname;var n=[];e.first_name&&n.push(e.first_name),e.last_name&&n.push(e.last_name),e.name=n.join(" "),e.email=e.emails&&e.emails[0]?e.emails[0].handle:null,e.thumbnail=e.image?e.image.imageUrl:null}return e}function o(e,n,o){t(e),r(e,n,o);return e.query&&e.query.results&&e.query.results.contact&&(e.data=e.query.results.contact,delete e.query,Array.isArray(e.data)||(e.data=[e.data]),e.data.forEach(i)),e}function i(e){e.id=null,!e.fields||e.fields instanceof Array||(e.fields=[e.fields]),(e.fields||[]).forEach(function(t){"email"===t.type&&(e.email=t.value),"name"===t.type&&(e.first_name=t.value.givenName,e.last_name=t.value.familyName,e.name=t.value.givenName+" "+t.value.familyName),"yahooid"===t.type&&(e.id=t.value)})}function r(e,t,n){return e.query&&e.query.count&&n.options&&(e.paging={next:"?start="+(e.query.count+(+n.options.start||1))}),e}function a(e){return"https://query.yahooapis.com/v1/yql?q="+(e+" limit @{limit|100} offset @{start|0}").replace(/\s/g,"%20")+"&format=json"}e.init({yahoo:{oauth:{version:"1.0a",auth:"https://api.login.yahoo.com/oauth/v2/request_auth",request:"https://api.login.yahoo.com/oauth/v2/get_request_token",token:"https://api.login.yahoo.com/oauth/v2/get_token"},login:function(e){e.options.popup.width=560;try{delete e.qs.state.scope}catch(t){}},base:"https://social.yahooapis.com/v1/",get:{me:a("select * from social.profile(0) where guid=me"),"me/friends":a("select * from social.contacts(0) where guid=me"),"me/following":a("select * from social.contacts(0) where guid=me")},wrap:{me:n,"me/friends":o,"me/following":o,"default":r}}})}(hello),"function"==typeof define&&define.amd&&define(function(){return hello}),"object"==typeof module&&module.exports&&(module.exports=hello);
=======
// ES5 Object.create
if (!Object.create) {

	// Shim, Object create
	// A shim for Object.create(), it adds a prototype to a new object
	Object.create = (function() {

		function F() {}

		return function(o) {

			if (arguments.length != 1) {
				throw new Error('Object.create implementation only accepts one parameter.');
			}

			F.prototype = o;
			return new F();
		};

	})();

}

// ES5 Object.keys
if (!Object.keys) {
	Object.keys = function(o, k, r) {
		r = [];
		for (k in o) {
			if (r.hasOwnProperty.call(o, k))
				r.push(k);
		}

		return r;
	};
}

// ES5 [].indexOf
if (!Array.prototype.indexOf) {
	Array.prototype.indexOf = function(s) {

		for (var j = 0; j < this.length; j++) {
			if (this[j] === s) {
				return j;
			}
		}

		return -1;
	};
}

// ES5 [].forEach
if (!Array.prototype.forEach) {
	Array.prototype.forEach = function(fun/*, thisArg*/) {

		if (this === void 0 || this === null) {
			throw new TypeError();
		}

		var t = Object(this);
		var len = t.length >>> 0;
		if (typeof fun !== 'function') {
			throw new TypeError();
		}

		var thisArg = arguments.length >= 2 ? arguments[1] : void 0;
		for (var i = 0; i < len; i++) {
			if (i in t) {
				fun.call(thisArg, t[i], i, t);
			}
		}

		return this;
	};
}

// ES5 [].filter
if (!Array.prototype.filter) {
	Array.prototype.filter = function(fun, thisArg) {

		var a = [];
		this.forEach(function(val, i, t) {
			if (fun.call(thisArg || void 0, val, i, t)) {
				a.push(val);
			}
		});

		return a;
	};
}

// Production steps of ECMA-262, Edition 5, 15.4.4.19
// Reference: http://es5.github.io/#x15.4.4.19
if (!Array.prototype.map) {

	Array.prototype.map = function(fun, thisArg) {

		var a = [];
		this.forEach(function(val, i, t) {
			a.push(fun.call(thisArg || void 0, val, i, t));
		});

		return a;
	};
}

// ES5 isArray
if (!Array.isArray) {

	// Function Array.isArray
	Array.isArray = function(o) {
		return Object.prototype.toString.call(o) === '[object Array]';
	};

}

// Test for location.assign
if (typeof window === 'object' && typeof window.location === 'object' && !window.location.assign) {

	window.location.assign = function(url) {
		window.location = url;
	};

}

// Test for Function.bind
if (!Function.prototype.bind) {

	// MDN
	// Polyfill IE8, does not support native Function.bind
	Function.prototype.bind = function(b) {

		if (typeof this !== 'function') {
			throw new TypeError('Function.prototype.bind - what is trying to be bound is not callable');
		}

		function C() {}

		var a = [].slice;
		var f = a.call(arguments, 1);
		var _this = this;
		var D = function() {
			return _this.apply(this instanceof C ? this : b || window, f.concat(a.call(arguments)));
		};

		C.prototype = this.prototype;
		D.prototype = new C();

		return D;
	};

}

/**
 * @hello.js
 *
 * HelloJS is a client side Javascript SDK for making OAuth2 logins and subsequent REST calls.
 *
 * @author Andrew Dodson
 * @website https://adodson.com/hello.js/
 *
 * @copyright Andrew Dodson, 2012 - 2015
 * @license MIT: You are free to use and modify this code for any use, on the condition that this copyright notice remains.
 */

var hello = function(name) {
	return hello.use(name);
};

hello.utils = {

	// Extend the first object with the properties and methods of the second
	extend: function(r /*, a[, b[, ...]] */) {

		// Get the arguments as an array but ommit the initial item
		Array.prototype.slice.call(arguments, 1).forEach(function(a) {
			if (Array.isArray(r) && Array.isArray(a)) {
				Array.prototype.push.apply(r, a);
			}
			else if (r && (r instanceof Object || typeof r === 'object') && a && (a instanceof Object || typeof a === 'object') && r !== a) {
				for (var x in a) {
					r[x] = hello.utils.extend(r[x], a[x]);
				}
			}
			else {

				if (Array.isArray(a)) {
					// Clone it
					a = a.slice(0);
				}

				r = a;
			}
		});

		return r;
	}
};

// Core library
hello.utils.extend(hello, {

	settings: {

		// OAuth2 authentication defaults
		redirect_uri: window.location.href.split('#')[0],
		response_type: 'token',
		display: 'popup',
		state: '',

		// OAuth1 shim
		// The path to the OAuth1 server for signing user requests
		// Want to recreate your own? Checkout https://github.com/MrSwitch/node-oauth-shim
		oauth_proxy: 'https://auth-server.herokuapp.com/proxy',

		// API timeout in milliseconds
		timeout: 20000,

		// Popup Options
		popup: {
			resizable: 1,
			scrollbars: 1,
			width: 500,
			height: 550
		},

		// Default scope
		// Many services require atleast a profile scope,
		// HelloJS automatially includes the value of provider.scope_map.basic
		// If that's not required it can be removed via hello.settings.scope.length = 0;
		scope: ['basic'],

		// Scope Maps
		// This is the default module scope, these are the defaults which each service is mapped too.
		// By including them here it prevents the scope from being applied accidentally
		scope_map: {
			basic: ''
		},

		// Default service / network
		default_service: null,

		// Force authentication
		// When hello.login is fired.
		// (null): ignore current session expiry and continue with login
		// (true): ignore current session expiry and continue with login, ask for user to reauthenticate
		// (false): if the current session looks good for the request scopes return the current session.
		force: null,

		// Page URL
		// When 'display=page' this property defines where the users page should end up after redirect_uri
		// Ths could be problematic if the redirect_uri is indeed the final place,
		// Typically this circumvents the problem of the redirect_url being a dumb relay page.
		page_uri: window.location.href
	},

	// Service configuration objects
	services: {},

	// Use
	// Define a new instance of the HelloJS library with a default service
	use: function(service) {

		// Create self, which inherits from its parent
		var self = Object.create(this);

		// Inherit the prototype from its parent
		self.settings = Object.create(this.settings);

		// Define the default service
		if (service) {
			self.settings.default_service = service;
		}

		// Create an instance of Events
		self.utils.Event.call(self);

		return self;
	},

	// Initialize
	// Define the client_ids for the endpoint services
	// @param object o, contains a key value pair, service => clientId
	// @param object opts, contains a key value pair of options used for defining the authentication defaults
	// @param number timeout, timeout in seconds
	init: function(services, options) {

		var utils = this.utils;

		if (!services) {
			return this.services;
		}

		// Define provider credentials
		// Reformat the ID field
		for (var x in services) {if (services.hasOwnProperty(x)) {
			if (typeof (services[x]) !== 'object') {
				services[x] = {id: services[x]};
			}
		}}

		// Merge services if there already exists some
		utils.extend(this.services, services);

		// Update the default settings with this one.
		if (options) {
			utils.extend(this.settings, options);

			// Do this immediatly incase the browser changes the current path.
			if ('redirect_uri' in options) {
				this.settings.redirect_uri = utils.url(options.redirect_uri).href;
			}
		}

		return this;
	},

	// Login
	// Using the endpoint
	// @param network stringify       name to connect to
	// @param options object    (optional)  {display mode, is either none|popup(default)|page, scope: email,birthday,publish, .. }
	// @param callback  function  (optional)  fired on signin
	login: function() {

		// Create an object which inherits its parent as the prototype and constructs a new event chain.
		var _this = this;
		var utils = _this.utils;
		var error = utils.error;
		var promise = utils.Promise();

		// Get parameters
		var p = utils.args({network: 's', options: 'o', callback: 'f'}, arguments);

		// Local vars
		var url;

		// Get all the custom options and store to be appended to the querystring
		var qs = utils.diffKey(p.options, _this.settings);

		// Merge/override options with app defaults
		var opts = p.options = utils.merge(_this.settings, p.options || {});

		// Merge/override options with app defaults
		opts.popup = utils.merge(_this.settings.popup, p.options.popup || {});

		// Network
		p.network = p.network || _this.settings.default_service;

		// Bind callback to both reject and fulfill states
		promise.proxy.then(p.callback, p.callback);

		// Trigger an event on the global listener
		function emit(s, value) {
			hello.emit(s, value);
		}

		promise.proxy.then(emit.bind(this, 'auth.login auth'), emit.bind(this, 'auth.failed auth'));

		// Is our service valid?
		if (typeof (p.network) !== 'string' || !(p.network in _this.services)) {
			// Trigger the default login.
			// Ahh we dont have one.
			return promise.reject(error('invalid_network', 'The provided network was not recognized'));
		}

		var provider = _this.services[p.network];

		// Create a global listener to capture events triggered out of scope
		var callbackId = utils.globalEvent(function(str) {

			// The responseHandler returns a string, lets save this locally
			var obj;

			if (str) {
				obj = JSON.parse(str);
			}
			else {
				obj = error('cancelled', 'The authentication was not completed');
			}

			// Handle these response using the local
			// Trigger on the parent
			if (!obj.error) {

				// Save on the parent window the new credentials
				// This fixes an IE10 bug i think... atleast it does for me.
				utils.store(obj.network, obj);

				// Fulfill a successful login
				promise.fulfill({
					network: obj.network,
					authResponse: obj
				});
			}
			else {
				// Reject a successful login
				promise.reject(obj);
			}
		});

		var redirectUri = utils.url(opts.redirect_uri).href;

		// May be a space-delimited list of multiple, complementary types
		var responseType = provider.oauth.response_type || opts.response_type;

		// Fallback to token if the module hasn't defined a grant url
		if (/\bcode\b/.test(responseType) && !provider.oauth.grant) {
			responseType = responseType.replace(/\bcode\b/, 'token');
		}

		// Query string parameters, we may pass our own arguments to form the querystring
		p.qs = utils.merge(qs, {
			client_id: encodeURIComponent(provider.id),
			response_type: encodeURIComponent(responseType),
			redirect_uri: encodeURIComponent(redirectUri),
			state: {
				client_id: provider.id,
				network: p.network,
				display: opts.display,
				callback: callbackId,
				state: opts.state,
				redirect_uri: redirectUri
			}
		});

		// Get current session for merging scopes, and for quick auth response
		var session = utils.store(p.network);

		// Scopes (authentication permisions)
		// Ensure this is a string - IE has a problem moving Arrays between windows
		// Append the setup scope
		var SCOPE_SPLIT = /[,\s]+/;

		// Include default scope settings (cloned).
		var scope = _this.settings.scope ? [_this.settings.scope.toString()] : [];

		// Extend the providers scope list with the default
		var scopeMap = utils.merge(_this.settings.scope_map, provider.scope || {});

		// Add user defined scopes...
		if (opts.scope) {
			scope.push(opts.scope.toString());
		}

		// Append scopes from a previous session.
		// This helps keep app credentials constant,
		// Avoiding having to keep tabs on what scopes are authorized
		if (session && 'scope' in session && session.scope instanceof String) {
			scope.push(session.scope);
		}

		// Join and Split again
		scope = scope.join(',').split(SCOPE_SPLIT);

		// Format remove duplicates and empty values
		scope = utils.unique(scope).filter(filterEmpty);

		// Save the the scopes to the state with the names that they were requested with.
		p.qs.state.scope = scope.join(',');

		// Map scopes to the providers naming convention
		scope = scope.map(function(item) {
			// Does this have a mapping?
			return (item in scopeMap) ? scopeMap[item] : item;
		});

		// Stringify and Arrayify so that double mapped scopes are given the chance to be formatted
		scope = scope.join(',').split(SCOPE_SPLIT);

		// Again...
		// Format remove duplicates and empty values
		scope = utils.unique(scope).filter(filterEmpty);

		// Join with the expected scope delimiter into a string
		p.qs.scope = scope.join(provider.scope_delim || ',');

		// Is the user already signed in with the appropriate scopes, valid access_token?
		if (opts.force === false) {

			if (session && 'access_token' in session && session.access_token && 'expires' in session && session.expires > ((new Date()).getTime() / 1e3)) {
				// What is different about the scopes in the session vs the scopes in the new login?
				var diff = utils.diff((session.scope || '').split(SCOPE_SPLIT), (p.qs.state.scope || '').split(SCOPE_SPLIT));
				if (diff.length === 0) {

					// OK trigger the callback
					promise.fulfill({
						unchanged: true,
						network: p.network,
						authResponse: session
					});

					// Nothing has changed
					return promise;
				}
			}
		}

		// Page URL
		if (opts.display === 'page' && opts.page_uri) {
			// Add a page location, place to endup after session has authenticated
			p.qs.state.page_uri = utils.url(opts.page_uri).href;
		}

		// Bespoke
		// Override login querystrings from auth_options
		if ('login' in provider && typeof (provider.login) === 'function') {
			// Format the paramaters according to the providers formatting function
			provider.login(p);
		}

		// Add OAuth to state
		// Where the service is going to take advantage of the oauth_proxy
		if (!/\btoken\b/.test(responseType) ||
		parseInt(provider.oauth.version, 10) < 2 ||
		(opts.display === 'none' && provider.oauth.grant && session && session.refresh_token)) {

			// Add the oauth endpoints
			p.qs.state.oauth = provider.oauth;

			// Add the proxy url
			p.qs.state.oauth_proxy = opts.oauth_proxy;

		}

		// Convert state to a string
		p.qs.state = encodeURIComponent(JSON.stringify(p.qs.state));

		// URL
		if (parseInt(provider.oauth.version, 10) === 1) {

			// Turn the request to the OAuth Proxy for 3-legged auth
			url = utils.qs(opts.oauth_proxy, p.qs, encodeFunction);
		}

		// Refresh token
		else if (opts.display === 'none' && provider.oauth.grant && session && session.refresh_token) {

			// Add the refresh_token to the request
			p.qs.refresh_token = session.refresh_token;

			// Define the request path
			url = utils.qs(opts.oauth_proxy, p.qs, encodeFunction);
		}
		else {
			url = utils.qs(provider.oauth.auth, p.qs, encodeFunction);
		}

		// Broadcast this event as an auth:init
		emit('auth.init', p);

		// Execute
		// Trigger how we want self displayed
		if (opts.display === 'none') {
			// Sign-in in the background, iframe
			utils.iframe(url, redirectUri);
		}

		// Triggering popup?
		else if (opts.display === 'popup') {

			var popup = utils.popup(url, redirectUri, opts.popup);

			var timer = setInterval(function() {
				if (!popup || popup.closed) {
					clearInterval(timer);
					if (!promise.state) {

						var response = error('cancelled', 'Login has been cancelled');

						if (!popup) {
							response = error('blocked', 'Popup was blocked');
						}

						response.network = p.network;

						promise.reject(response);
					}
				}
			}, 100);
		}

		else {
			window.location = url;
		}

		return promise.proxy;

		function encodeFunction(s) {return s;}

		function filterEmpty(s) {return !!s;}
	},

	// Remove any data associated with a given service
	// @param string name of the service
	// @param function callback
	logout: function() {

		var _this = this;
		var utils = _this.utils;
		var error = utils.error;

		// Create a new promise
		var promise = utils.Promise();

		var p = utils.args({name:'s', options: 'o', callback: 'f'}, arguments);

		p.options = p.options || {};

		// Add callback to events
		promise.proxy.then(p.callback, p.callback);

		// Trigger an event on the global listener
		function emit(s, value) {
			hello.emit(s, value);
		}

		promise.proxy.then(emit.bind(this, 'auth.logout auth'), emit.bind(this, 'error'));

		// Network
		p.name = p.name || this.settings.default_service;
		p.authResponse = utils.store(p.name);

		if (p.name && !(p.name in _this.services)) {

			promise.reject(error('invalid_network', 'The network was unrecognized'));

		}
		else if (p.name && p.authResponse) {

			// Define the callback
			var callback = function(opts) {

				// Remove from the store
				utils.store(p.name, null);

				// Emit events by default
				promise.fulfill(hello.utils.merge({network:p.name}, opts || {}));
			};

			// Run an async operation to remove the users session
			var _opts = {};
			if (p.options.force) {
				var logout = _this.services[p.name].logout;
				if (logout) {
					// Convert logout to URL string,
					// If no string is returned, then this function will handle the logout async style
					if (typeof (logout) === 'function') {
						logout = logout(callback, p);
					}

					// If logout is a string then assume URL and open in iframe.
					if (typeof (logout) === 'string') {
						utils.iframe(logout);
						_opts.force = null;
						_opts.message = 'Logout success on providers site was indeterminate';
					}
					else if (logout === undefined) {
						// The callback function will handle the response.
						return promise.proxy;
					}
				}
			}

			// Remove local credentials
			callback(_opts);
		}
		else {
			promise.reject(error('invalid_session', 'There was no session to remove'));
		}

		return promise.proxy;
	},

	// Returns all the sessions that are subscribed too
	// @param string optional, name of the service to get information about.
	getAuthResponse: function(service) {

		// If the service doesn't exist
		service = service || this.settings.default_service;

		if (!service || !(service in this.services)) {
			return null;
		}

		return this.utils.store(service) || null;
	},

	// Events: placeholder for the events
	events: {}
});

// Core utilities
hello.utils.extend(hello.utils, {

	// Error
	error: function(code, message) {
		return {
			error: {
				code: code,
				message: message
			}
		};
	},

	// Append the querystring to a url
	// @param string url
	// @param object parameters
	qs: function(url, params, formatFunction) {

		if (params) {

			// Set default formatting function
			formatFunction = formatFunction || encodeURIComponent;

			// Override the items in the URL which already exist
			for (var x in params) {
				var str = '([\\?\\&])' + x + '=[^\\&]*';
				var reg = new RegExp(str);
				if (url.match(reg)) {
					url = url.replace(reg, '$1' + x + '=' + formatFunction(params[x]));
					delete params[x];
				}
			}
		}

		if (!this.isEmpty(params)) {
			return url + (url.indexOf('?') > -1 ? '&' : '?') + this.param(params, formatFunction);
		}

		return url;
	},

	// Param
	// Explode/encode the parameters of an URL string/object
	// @param string s, string to decode
	param: function(s, formatFunction) {
		var b;
		var a = {};
		var m;

		if (typeof (s) === 'string') {

			formatFunction = formatFunction || decodeURIComponent;

			m = s.replace(/^[\#\?]/, '').match(/([^=\/\&]+)=([^\&]+)/g);
			if (m) {
				for (var i = 0; i < m.length; i++) {
					b = m[i].match(/([^=]+)=(.*)/);
					a[b[1]] = formatFunction(b[2]);
				}
			}

			return a;
		}
		else {

			formatFunction = formatFunction || encodeURIComponent;

			var o = s;

			a = [];

			for (var x in o) {if (o.hasOwnProperty(x)) {
				if (o.hasOwnProperty(x)) {
					a.push([x, o[x] === '?' ? '?' : formatFunction(o[x])].join('='));
				}
			}}

			return a.join('&');
		}
	},

	// Local storage facade
	store: (function() {

		var a = ['localStorage', 'sessionStorage'];
		var i = -1;
		var prefix = 'test';

		// Set LocalStorage
		var localStorage;

		while (a[++i]) {
			try {
				// In Chrome with cookies blocked, calling localStorage throws an error
				localStorage = window[a[i]];
				localStorage.setItem(prefix + i, i);
				localStorage.removeItem(prefix + i);
				break;
			}
			catch (e) {
				localStorage = null;
			}
		}

		if (!localStorage) {

			var cache = null;

			localStorage = {
				getItem: function(prop) {
					prop = prop + '=';
					var m = document.cookie.split(';');
					for (var i = 0; i < m.length; i++) {
						var _m = m[i].replace(/(^\s+|\s+$)/, '');
						if (_m && _m.indexOf(prop) === 0) {
							return _m.substr(prop.length);
						}
					}

					return cache;
				},

				setItem: function(prop, value) {
					cache = value;
					document.cookie = prop + '=' + value;
				}
			};

			// Fill the cache up
			cache = localStorage.getItem('hello');
		}

		function get() {
			var json = {};
			try {
				json = JSON.parse(localStorage.getItem('hello')) || {};
			}
			catch (e) {}

			return json;
		}

		function set(json) {
			localStorage.setItem('hello', JSON.stringify(json));
		}

		// Check if the browser support local storage
		return function(name, value, days) {

			// Local storage
			var json = get();

			if (name && value === undefined) {
				return json[name] || null;
			}
			else if (name && value === null) {
				try {
					delete json[name];
				}
				catch (e) {
					json[name] = null;
				}
			}
			else if (name) {
				json[name] = value;
			}
			else {
				return json;
			}

			set(json);

			return json || null;
		};

	})(),

	// Create and Append new DOM elements
	// @param node string
	// @param attr object literal
	// @param dom/string
	append: function(node, attr, target) {

		var n = typeof (node) === 'string' ? document.createElement(node) : node;

		if (typeof (attr) === 'object') {
			if ('tagName' in attr) {
				target = attr;
			}
			else {
				for (var x in attr) {if (attr.hasOwnProperty(x)) {
					if (typeof (attr[x]) === 'object') {
						for (var y in attr[x]) {if (attr[x].hasOwnProperty(y)) {
							n[x][y] = attr[x][y];
						}}
					}
					else if (x === 'html') {
						n.innerHTML = attr[x];
					}

					// IE doesn't like us setting methods with setAttribute
					else if (!/^on/.test(x)) {
						n.setAttribute(x, attr[x]);
					}
					else {
						n[x] = attr[x];
					}
				}}
			}
		}

		if (target === 'body') {
			(function self() {
				if (document.body) {
					document.body.appendChild(n);
				}
				else {
					setTimeout(self, 16);
				}
			})();
		}
		else if (typeof (target) === 'object') {
			target.appendChild(n);
		}
		else if (typeof (target) === 'string') {
			document.getElementsByTagName(target)[0].appendChild(n);
		}

		return n;
	},

	// An easy way to create a hidden iframe
	// @param string src
	iframe: function(src) {
		this.append('iframe', {src: src, style: {position:'absolute', left: '-1000px', bottom: 0, height: '1px', width: '1px'}}, 'body');
	},

	// Recursive merge two objects into one, second parameter overides the first
	// @param a array
	merge: function(/* Args: a, b, c, .. n */) {
		var args = Array.prototype.slice.call(arguments);
		args.unshift({});
		return this.extend.apply(null, args);
	},

	// Makes it easier to assign parameters, where some are optional
	// @param o object
	// @param a arguments
	args: function(o, args) {

		var p = {};
		var i = 0;
		var t = null;
		var x = null;

		// 'x' is the first key in the list of object parameters
		for (x in o) {if (o.hasOwnProperty(x)) {
			break;
		}}

		// Passing in hash object of arguments?
		// Where the first argument can't be an object
		if ((args.length === 1) && (typeof (args[0]) === 'object') && o[x] != 'o!') {

			// Could this object still belong to a property?
			// Check the object keys if they match any of the property keys
			for (x in args[0]) {if (o.hasOwnProperty(x)) {
				// Does this key exist in the property list?
				if (x in o) {
					// Yes this key does exist so its most likely this function has been invoked with an object parameter
					// Return first argument as the hash of all arguments
					return args[0];
				}
			}}
		}

		// Else loop through and account for the missing ones.
		for (x in o) {if (o.hasOwnProperty(x)) {

			t = typeof (args[i]);

			if ((typeof (o[x]) === 'function' && o[x].test(args[i])) || (typeof (o[x]) === 'string' && (
			(o[x].indexOf('s') > -1 && t === 'string') ||
			(o[x].indexOf('o') > -1 && t === 'object') ||
			(o[x].indexOf('i') > -1 && t === 'number') ||
			(o[x].indexOf('a') > -1 && t === 'object') ||
			(o[x].indexOf('f') > -1 && t === 'function')
			))
			) {
				p[x] = args[i++];
			}

			else if (typeof (o[x]) === 'string' && o[x].indexOf('!') > -1) {
				return false;
			}
		}}

		return p;
	},

	// Returns a URL instance
	url: function(path) {

		// If the path is empty
		if (!path) {
			return window.location;
		}

		// Chrome and FireFox support new URL() to extract URL objects
		else if (window.URL && URL instanceof Function && URL.length !== 0) {
			return new URL(path, window.location);
		}

		// Ugly shim, it works!
		else {
			var a = document.createElement('a');
			a.href = path;
			return a.cloneNode(false);
		}
	},

	diff: function(a, b) {
		return b.filter(function(item) {
			return a.indexOf(item) === -1;
		});
	},

	// Get the different hash of properties unique to `a`, and not in `b`
	diffKey: function(a, b) {
		if (a || !b) {
			var r = {};
			for (var x in a) {
				// Does the property not exist?
				if (!(x in b)) {
					r[x] = a[x];
				}
			}

			return r;
		}

		return a;
	},

	// Unique
	// Remove duplicate and null values from an array
	// @param a array
	unique: function(a) {
		if (!Array.isArray(a)) { return []; }

		return a.filter(function(item, index) {
			// Is this the first location of item
			return a.indexOf(item) === index;
		});
	},

	isEmpty: function(obj) {

		// Scalar
		if (!obj)
			return true;

		// Array
		if (Array.isArray(obj)) {
			return !obj.length;
		}
		else if (typeof (obj) === 'object') {
			// Object
			for (var key in obj) {
				if (obj.hasOwnProperty(key)) {
					return false;
				}
			}
		}

		return true;
	},

	//jscs:disable

	/*!
	 **  Thenable -- Embeddable Minimum Strictly-Compliant Promises/A+ 1.1.1 Thenable
	 **  Copyright (c) 2013-2014 Ralf S. Engelschall <http://engelschall.com>
	 **  Licensed under The MIT License <http://opensource.org/licenses/MIT>
	 **  Source-Code distributed on <http://github.com/rse/thenable>
	 */
	Promise: (function(){
		/*  promise states [Promises/A+ 2.1]  */
		var STATE_PENDING   = 0;                                         /*  [Promises/A+ 2.1.1]  */
		var STATE_FULFILLED = 1;                                         /*  [Promises/A+ 2.1.2]  */
		var STATE_REJECTED  = 2;                                         /*  [Promises/A+ 2.1.3]  */

		/*  promise object constructor  */
		var api = function (executor) {
			/*  optionally support non-constructor/plain-function call  */
			if (!(this instanceof api))
				return new api(executor);

			/*  initialize object  */
			this.id           = "Thenable/1.0.6";
			this.state        = STATE_PENDING; /*  initial state  */
			this.fulfillValue = undefined;     /*  initial value  */     /*  [Promises/A+ 1.3, 2.1.2.2]  */
			this.rejectReason = undefined;     /*  initial reason */     /*  [Promises/A+ 1.5, 2.1.3.2]  */
			this.onFulfilled  = [];            /*  initial handlers  */
			this.onRejected   = [];            /*  initial handlers  */

			/*  provide optional information-hiding proxy  */
			this.proxy = {
				then: this.then.bind(this)
			};

			/*  support optional executor function  */
			if (typeof executor === "function")
				executor.call(this, this.fulfill.bind(this), this.reject.bind(this));
		};

		/*  promise API methods  */
		api.prototype = {
			/*  promise resolving methods  */
			fulfill: function (value) { return deliver(this, STATE_FULFILLED, "fulfillValue", value); },
			reject:  function (value) { return deliver(this, STATE_REJECTED,  "rejectReason", value); },

			/*  "The then Method" [Promises/A+ 1.1, 1.2, 2.2]  */
			then: function (onFulfilled, onRejected) {
				var curr = this;
				var next = new api();                                    /*  [Promises/A+ 2.2.7]  */
				curr.onFulfilled.push(
					resolver(onFulfilled, next, "fulfill"));             /*  [Promises/A+ 2.2.2/2.2.6]  */
				curr.onRejected.push(
					resolver(onRejected,  next, "reject" ));             /*  [Promises/A+ 2.2.3/2.2.6]  */
				execute(curr);
				return next.proxy;                                       /*  [Promises/A+ 2.2.7, 3.3]  */
			}
		};

		/*  deliver an action  */
		var deliver = function (curr, state, name, value) {
			if (curr.state === STATE_PENDING) {
				curr.state = state;                                      /*  [Promises/A+ 2.1.2.1, 2.1.3.1]  */
				curr[name] = value;                                      /*  [Promises/A+ 2.1.2.2, 2.1.3.2]  */
				execute(curr);
			}
			return curr;
		};

		/*  execute all handlers  */
		var execute = function (curr) {
			if (curr.state === STATE_FULFILLED)
				execute_handlers(curr, "onFulfilled", curr.fulfillValue);
			else if (curr.state === STATE_REJECTED)
				execute_handlers(curr, "onRejected",  curr.rejectReason);
		};

		/*  execute particular set of handlers  */
		var execute_handlers = function (curr, name, value) {
			/* global process: true */
			/* global setImmediate: true */
			/* global setTimeout: true */

			/*  short-circuit processing  */
			if (curr[name].length === 0)
				return;

			/*  iterate over all handlers, exactly once  */
			var handlers = curr[name];
			curr[name] = [];                                             /*  [Promises/A+ 2.2.2.3, 2.2.3.3]  */
			var func = function () {
				for (var i = 0; i < handlers.length; i++)
					handlers[i](value);                                  /*  [Promises/A+ 2.2.5]  */
			};

			/*  execute procedure asynchronously  */                     /*  [Promises/A+ 2.2.4, 3.1]  */
			if (typeof process === "object" && typeof process.nextTick === "function")
				process.nextTick(func);
			else if (typeof setImmediate === "function")
				setImmediate(func);
			else
				setTimeout(func, 0);
		};

		/*  generate a resolver function  */
		var resolver = function (cb, next, method) {
			return function (value) {
				if (typeof cb !== "function")                            /*  [Promises/A+ 2.2.1, 2.2.7.3, 2.2.7.4]  */
					next[method].call(next, value);                      /*  [Promises/A+ 2.2.7.3, 2.2.7.4]  */
				else {
					var result;
					try { result = cb(value); }                          /*  [Promises/A+ 2.2.2.1, 2.2.3.1, 2.2.5, 3.2]  */
					catch (e) {
						next.reject(e);                                  /*  [Promises/A+ 2.2.7.2]  */
						return;
					}
					resolve(next, result);                               /*  [Promises/A+ 2.2.7.1]  */
				}
			};
		};

		/*  "Promise Resolution Procedure"  */                           /*  [Promises/A+ 2.3]  */
		var resolve = function (promise, x) {
			/*  sanity check arguments  */                               /*  [Promises/A+ 2.3.1]  */
			if (promise === x || promise.proxy === x) {
				promise.reject(new TypeError("cannot resolve promise with itself"));
				return;
			}

			/*  surgically check for a "then" method
				(mainly to just call the "getter" of "then" only once)  */
			var then;
			if ((typeof x === "object" && x !== null) || typeof x === "function") {
				try { then = x.then; }                                   /*  [Promises/A+ 2.3.3.1, 3.5]  */
				catch (e) {
					promise.reject(e);                                   /*  [Promises/A+ 2.3.3.2]  */
					return;
				}
			}

			/*  handle own Thenables    [Promises/A+ 2.3.2]
				and similar "thenables" [Promises/A+ 2.3.3]  */
			if (typeof then === "function") {
				var resolved = false;
				try {
					/*  call retrieved "then" method */                  /*  [Promises/A+ 2.3.3.3]  */
					then.call(x,
						/*  resolvePromise  */                           /*  [Promises/A+ 2.3.3.3.1]  */
						function (y) {
							if (resolved) return; resolved = true;       /*  [Promises/A+ 2.3.3.3.3]  */
							if (y === x)                                 /*  [Promises/A+ 3.6]  */
								promise.reject(new TypeError("circular thenable chain"));
							else
								resolve(promise, y);
						},

						/*  rejectPromise  */                            /*  [Promises/A+ 2.3.3.3.2]  */
						function (r) {
							if (resolved) return; resolved = true;       /*  [Promises/A+ 2.3.3.3.3]  */
							promise.reject(r);
						}
					);
				}
				catch (e) {
					if (!resolved)                                       /*  [Promises/A+ 2.3.3.3.3]  */
						promise.reject(e);                               /*  [Promises/A+ 2.3.3.3.4]  */
				}
				return;
			}

			/*  handle other values  */
			promise.fulfill(x);                                          /*  [Promises/A+ 2.3.4, 2.3.3.4]  */
		};

		/*  export API  */
		return api;
	})(),

	//jscs:enable

	// Event
	// A contructor superclass for adding event menthods, on, off, emit.
	Event: function() {

		var separator = /[\s\,]+/;

		// If this doesn't support getPrototype then we can't get prototype.events of the parent
		// So lets get the current instance events, and add those to a parent property
		this.parent = {
			events: this.events,
			findEvents: this.findEvents,
			parent: this.parent,
			utils: this.utils
		};

		this.events = {};

		// On, subscribe to events
		// @param evt   string
		// @param callback  function
		this.on = function(evt, callback) {

			if (callback && typeof (callback) === 'function') {
				var a = evt.split(separator);
				for (var i = 0; i < a.length; i++) {

					// Has this event already been fired on this instance?
					this.events[a[i]] = [callback].concat(this.events[a[i]] || []);
				}
			}

			return this;
		};

		// Off, unsubscribe to events
		// @param evt   string
		// @param callback  function
		this.off = function(evt, callback) {

			this.findEvents(evt, function(name, index) {
				if (!callback || this.events[name][index] === callback) {
					this.events[name][index] = null;
				}
			});

			return this;
		};

		// Emit
		// Triggers any subscribed events
		this.emit = function(evt /*, data, ... */) {

			// Get arguments as an Array, knock off the first one
			var args = Array.prototype.slice.call(arguments, 1);
			args.push(evt);

			// Handler
			var handler = function(name, index) {

				// Replace the last property with the event name
				args[args.length - 1] = (name === '*' ? evt : name);

				// Trigger
				this.events[name][index].apply(this, args);
			};

			// Find the callbacks which match the condition and call
			var _this = this;
			while (_this && _this.findEvents) {

				// Find events which match
				_this.findEvents(evt + ',*', handler);
				_this = _this.parent;
			}

			return this;
		};

		//
		// Easy functions
		this.emitAfter = function() {
			var _this = this;
			var args = arguments;
			setTimeout(function() {
				_this.emit.apply(_this, args);
			}, 0);

			return this;
		};

		this.findEvents = function(evt, callback) {

			var a = evt.split(separator);

			for (var name in this.events) {if (this.events.hasOwnProperty(name)) {

				if (a.indexOf(name) > -1) {

					for (var i = 0; i < this.events[name].length; i++) {

						// Does the event handler exist?
						if (this.events[name][i]) {
							// Emit on the local instance of this
							callback.call(this, name, i);
						}
					}
				}
			}}
		};

		return this;
	},

	// Global Events
	// Attach the callback to the window object
	// Return its unique reference
	globalEvent: function(callback, guid) {
		// If the guid has not been supplied then create a new one.
		guid = guid || '_hellojs_' + parseInt(Math.random() * 1e12, 10).toString(36);

		// Define the callback function
		window[guid] = function() {
			// Trigger the callback
			try {
				if (callback.apply(this, arguments)) {
					delete window[guid];
				}
			}
			catch (e) {
				console.error(e);
			}
		};

		return guid;
	},

	// Trigger a clientside popup
	// This has been augmented to support PhoneGap
	popup: function(url, redirectUri, options) {

		var documentElement = document.documentElement;

		// Multi Screen Popup Positioning (http://stackoverflow.com/a/16861050)
		// Credit: http://www.xtf.dk/2011/08/center-new-popup-window-even-on.html
		// Fixes dual-screen position                         Most browsers      Firefox

		if (options.height) {
			var dualScreenTop = window.screenTop !== undefined ? window.screenTop : screen.top;
			var height = screen.height || window.innerHeight || documentElement.clientHeight;
			options.top = parseInt((height - options.height) / 2, 10) + dualScreenTop;
		}

		if (options.width) {
			var dualScreenLeft = window.screenLeft !== undefined ? window.screenLeft : screen.left;
			var width = screen.width || window.innerWidth || documentElement.clientWidth;
			options.left = parseInt((width - options.width) / 2, 10) + dualScreenLeft;
		}

		// Convert options into an array
		var optionsArray = [];
		Object.keys(options).forEach(function(name) {
			var value = options[name];
			optionsArray.push(name + (value !== null ? '=' + value : ''));
		});

		// Call the open() function with the initial path
		//
		// OAuth redirect, fixes URI fragments from being lost in Safari
		// (URI Fragments within 302 Location URI are lost over HTTPS)
		// Loading the redirect.html before triggering the OAuth Flow seems to fix it.
		//
		// Firefox  decodes URL fragments when calling location.hash.
		//  - This is bad if the value contains break points which are escaped
		//  - Hence the url must be encoded twice as it contains breakpoints.
		if (navigator.userAgent.indexOf('Safari') !== -1 && navigator.userAgent.indexOf('Chrome') === -1) {
			url = redirectUri + '#oauth_redirect=' + encodeURIComponent(encodeURIComponent(url));
		}

		var popup = window.open(
			url,
			'_blank',
			optionsArray.join(',')
		);

		if (popup && popup.focus) {
			popup.focus();
		}

		return popup;
	},

	// OAuth and API response handler
	responseHandler: function(window, parent) {

		var _this = this;
		var p;
		var location = window.location;

		// Is this an auth relay message which needs to call the proxy?
		p = _this.param(location.search);

		// OAuth2 or OAuth1 server response?
		if (p && p.state && (p.code || p.oauth_token)) {

			var state = JSON.parse(p.state);

			// Add this path as the redirect_uri
			p.redirect_uri = state.redirect_uri || location.href.replace(/[\?\#].*$/, '');

			// Redirect to the host
			var path = state.oauth_proxy + '?' + _this.param(p);

			location.assign(path);

			return;
		}

		// Save session, from redirected authentication
		// #access_token has come in?
		//
		// FACEBOOK is returning auth errors within as a query_string... thats a stickler for consistency.
		// SoundCloud is the state in the querystring and the token in the hashtag, so we'll mix the two together

		p = _this.merge(_this.param(location.search || ''), _this.param(location.hash || ''));

		// If p.state
		if (p && 'state' in p) {

			// Remove any addition information
			// E.g. p.state = 'facebook.page';
			try {
				var a = JSON.parse(p.state);
				_this.extend(p, a);
			}
			catch (e) {
				console.error('Could not decode state parameter');
			}

			// Access_token?
			if (('access_token' in p && p.access_token) && p.network) {

				if (!p.expires_in || parseInt(p.expires_in, 10) === 0) {
					// If p.expires_in is unset, set to 0
					p.expires_in = 0;
				}

				p.expires_in = parseInt(p.expires_in, 10);
				p.expires = ((new Date()).getTime() / 1e3) + (p.expires_in || (60 * 60 * 24 * 365));

				// Lets use the "state" to assign it to one of our networks
				authCallback(p, window, parent);
			}

			// Error=?
			// &error_description=?
			// &state=?
			else if (('error' in p && p.error) && p.network) {

				p.error = {
					code: p.error,
					message: p.error_message || p.error_description
				};

				// Let the state handler handle it
				authCallback(p, window, parent);
			}

			// API call, or a cancelled login
			// Result is serialized JSON string
			else if (p.callback && p.callback in parent) {

				// Trigger a function in the parent
				var res = 'result' in p && p.result ? JSON.parse(p.result) : false;

				// Trigger the callback on the parent
				callback(parent, p.callback)(res);
				closeWindow();
			}

			// If this page is still open
			if (p.page_uri) {
				location.assign(p.page_uri);
			}
		}

		// OAuth redirect, fixes URI fragments from being lost in Safari
		// (URI Fragments within 302 Location URI are lost over HTTPS)
		// Loading the redirect.html before triggering the OAuth Flow seems to fix it.
		else if ('oauth_redirect' in p) {

			location.assign(decodeURIComponent(p.oauth_redirect));
			return;
		}

		// Trigger a callback to authenticate
		function authCallback(obj, window, parent) {

			var cb = obj.callback;
			var network = obj.network;

			// Trigger the callback on the parent
			_this.store(network, obj);

			// If this is a page request it has no parent or opener window to handle callbacks
			if (('display' in obj) && obj.display === 'page') {
				return;
			}

			// Remove from session object
			if (parent && cb && cb in parent) {

				try {
					delete obj.callback;
				}
				catch (e) {}

				// Update store
				_this.store(network, obj);

				// Call the globalEvent function on the parent
				// It's safer to pass back a string to the parent,
				// Rather than an object/array (better for IE8)
				var str = JSON.stringify(obj);

				try {
					callback(parent, cb)(str);
				}
				catch (e) {
					// Error thrown whilst executing parent callback
				}
			}

			closeWindow();
		}

		function callback(parent, callbackID) {
			if (callbackID.indexOf('_hellojs_') !== 0) {
				return function() {
					throw 'Could not execute callback ' + callbackID;
				};
			}

			return parent[callbackID];
		}

		function closeWindow() {

			if (window.frameElement) {
				// Inside an iframe, remove from parent
				parent.document.body.removeChild(window.frameElement);
			}
			else {
				// Close this current window
				try {
					window.close();
				}
				catch (e) {}

				// IOS bug wont let us close a popup if still loading
				if (window.addEventListener) {
					window.addEventListener('load', function() {
						window.close();
					});
				}
			}

		}
	}
});

// Events
// Extend the hello object with its own event instance
hello.utils.Event.call(hello);

///////////////////////////////////
// Monitoring session state
// Check for session changes
///////////////////////////////////

(function(hello) {

	// Monitor for a change in state and fire
	var oldSessions = {};

	// Hash of expired tokens
	var expired = {};

	// Listen to other triggers to Auth events, use these to update this
	hello.on('auth.login, auth.logout', function(auth) {
		if (auth && typeof (auth) === 'object' && auth.network) {
			oldSessions[auth.network] = hello.utils.store(auth.network) || {};
		}
	});

	(function self() {

		var CURRENT_TIME = ((new Date()).getTime() / 1e3);
		var emit = function(eventName) {
			hello.emit('auth.' + eventName, {
				network: name,
				authResponse: session
			});
		};

		// Loop through the services
		for (var name in hello.services) {if (hello.services.hasOwnProperty(name)) {

			if (!hello.services[name].id) {
				// We haven't attached an ID so dont listen.
				continue;
			}

			// Get session
			var session = hello.utils.store(name) || {};
			var provider = hello.services[name];
			var oldSess = oldSessions[name] || {};

			// Listen for globalEvents that did not get triggered from the child
			if (session && 'callback' in session) {

				// To do remove from session object...
				var cb = session.callback;
				try {
					delete session.callback;
				}
				catch (e) {}

				// Update store
				// Removing the callback
				hello.utils.store(name, session);

				// Emit global events
				try {
					window[cb](session);
				}
				catch (e) {}
			}

			// Refresh token
			if (session && ('expires' in session) && session.expires < CURRENT_TIME) {

				// If auto refresh is possible
				// Either the browser supports
				var refresh = provider.refresh || session.refresh_token;

				// Has the refresh been run recently?
				if (refresh && (!(name in expired) || expired[name] < CURRENT_TIME)) {
					// Try to resignin
					hello.emit('notice', name + ' has expired trying to resignin');
					hello.login(name, {display: 'none', force: false});

					// Update expired, every 10 minutes
					expired[name] = CURRENT_TIME + 600;
				}

				// Does this provider not support refresh
				else if (!refresh && !(name in expired)) {
					// Label the event
					emit('expired');
					expired[name] = true;
				}

				// If session has expired then we dont want to store its value until it can be established that its been updated
				continue;
			}

			// Has session changed?
			else if (oldSess.access_token === session.access_token &&
			oldSess.expires === session.expires) {
				continue;
			}

			// Access_token has been removed
			else if (!session.access_token && oldSess.access_token) {
				emit('logout');
			}

			// Access_token has been created
			else if (session.access_token && !oldSess.access_token) {
				emit('login');
			}

			// Access_token has been updated
			else if (session.expires !== oldSess.expires) {
				emit('update');
			}

			// Updated stored session
			oldSessions[name] = session;

			// Remove the expired flags
			if (name in expired) {
				delete expired[name];
			}
		}}

		// Check error events
		setTimeout(self, 1000);
	})();

})(hello);

// EOF CORE lib
//////////////////////////////////

/////////////////////////////////////////
// API
// @param path    string
// @param query   object (optional)
// @param method  string (optional)
// @param data    object (optional)
// @param timeout integer (optional)
// @param callback  function (optional)

hello.api = function() {

	// Shorthand
	var _this = this;
	var utils = _this.utils;
	var error = utils.error;

	// Construct a new Promise object
	var promise = utils.Promise();

	// Arguments
	var p = utils.args({path: 's!', query: 'o', method: 's', data: 'o', timeout: 'i', callback: 'f'}, arguments);

	// Method
	p.method = (p.method || 'get').toLowerCase();

	// Headers
	p.headers = p.headers || {};

	// Query
	p.query = p.query || {};

	// If get, put all parameters into query
	if (p.method === 'get' || p.method === 'delete') {
		utils.extend(p.query, p.data);
		p.data = {};
	}

	var data = p.data = p.data || {};

	// Completed event callback
	promise.then(p.callback, p.callback);

	// Remove the network from path, e.g. facebook:/me/friends
	// Results in { network : facebook, path : me/friends }
	if (!p.path) {
		return promise.reject(error('invalid_path', 'Missing the path parameter from the request'));
	}

	p.path = p.path.replace(/^\/+/, '');
	var a = (p.path.split(/[\/\:]/, 2) || [])[0].toLowerCase();

	if (a in _this.services) {
		p.network = a;
		var reg = new RegExp('^' + a + ':?\/?');
		p.path = p.path.replace(reg, '');
	}

	// Network & Provider
	// Define the network that this request is made for
	p.network = _this.settings.default_service = p.network || _this.settings.default_service;
	var o = _this.services[p.network];

	// INVALID
	// Is there no service by the given network name?
	if (!o) {
		return promise.reject(error('invalid_network', 'Could not match the service requested: ' + p.network));
	}

	// PATH
	// As long as the path isn't flagged as unavaiable, e.g. path == false

	if (!(!(p.method in o) || !(p.path in o[p.method]) || o[p.method][p.path] !== false)) {
		return promise.reject(error('invalid_path', 'The provided path is not available on the selected network'));
	}

	// PROXY
	// OAuth1 calls always need a proxy

	if (!p.oauth_proxy) {
		p.oauth_proxy = _this.settings.oauth_proxy;
	}

	if (!('proxy' in p)) {
		p.proxy = p.oauth_proxy && o.oauth && parseInt(o.oauth.version, 10) === 1;
	}

	// TIMEOUT
	// Adopt timeout from global settings by default

	if (!('timeout' in p)) {
		p.timeout = _this.settings.timeout;
	}

	// Format response
	// Whether to run the raw response through post processing.
	if (!('formatResponse' in p)) {
		p.formatResponse = true;
	}

	// Get the current session
	// Append the access_token to the query
	p.authResponse = _this.getAuthResponse(p.network);
	if (p.authResponse && p.authResponse.access_token) {
		p.query.access_token = p.authResponse.access_token;
	}

	var url = p.path;
	var m;

	// Store the query as options
	// This is used to populate the request object before the data is augmented by the prewrap handlers.
	p.options = utils.clone(p.query);

	// Clone the data object
	// Prevent this script overwriting the data of the incoming object.
	// Ensure that everytime we run an iteration the callbacks haven't removed some data
	p.data = utils.clone(data);

	// URL Mapping
	// Is there a map for the given URL?
	var actions = o[{'delete': 'del'}[p.method] || p.method] || {};

	// Extrapolate the QueryString
	// Provide a clean path
	// Move the querystring into the data
	if (p.method === 'get') {

		var query = url.split(/[\?#]/)[1];
		if (query) {
			utils.extend(p.query, utils.param(query));

			// Remove the query part from the URL
			url = url.replace(/\?.*?(#|$)/, '$1');
		}
	}

	// Is the hash fragment defined
	if ((m = url.match(/#(.+)/, ''))) {
		url = url.split('#')[0];
		p.path = m[1];
	}
	else if (url in actions) {
		p.path = url;
		url = actions[url];
	}
	else if ('default' in actions) {
		url = actions['default'];
	}

	// Redirect Handler
	// This defines for the Form+Iframe+Hash hack where to return the results too.
	p.redirect_uri = _this.settings.redirect_uri;

	// Define FormatHandler
	// The request can be procesed in a multitude of ways
	// Here's the options - depending on the browser and endpoint
	p.xhr = o.xhr;
	p.jsonp = o.jsonp;
	p.form = o.form;

	// Make request
	if (typeof (url) === 'function') {
		// Does self have its own callback?
		url(p, getPath);
	}
	else {
		// Else the URL is a string
		getPath(url);
	}

	return promise.proxy;

	// If url needs a base
	// Wrap everything in
	function getPath(url) {

		// Format the string if it needs it
		url = url.replace(/\@\{([a-z\_\-]+)(\|.*?)?\}/gi, function(m, key, defaults) {
			var val = defaults ? defaults.replace(/^\|/, '') : '';
			if (key in p.query) {
				val = p.query[key];
				delete p.query[key];
			}
			else if (p.data && key in p.data) {
				val = p.data[key];
				delete p.data[key];
			}
			else if (!defaults) {
				promise.reject(error('missing_attribute', 'The attribute ' + key + ' is missing from the request'));
			}

			return val;
		});

		// Add base
		if (!url.match(/^https?:\/\//)) {
			url = o.base + url;
		}

		// Define the request URL
		p.url = url;

		// Make the HTTP request with the curated request object
		// CALLBACK HANDLER
		// @ response object
		// @ statusCode integer if available
		utils.request(p, function(r, headers) {

			// Is this a raw response?
			if (!p.formatResponse) {
				// Bad request? error statusCode or otherwise contains an error response vis JSONP?
				if (typeof headers === 'object' ? (headers.statusCode >= 400) : (typeof r === 'object' && 'error' in r)) {
					promise.reject(r);
				}
				else {
					promise.fulfill(r);
				}

				return;
			}

			// Should this be an object
			if (r === true) {
				r = {success:true};
			}
			else if (!r) {
				r = {};
			}

			// The delete callback needs a better response
			if (p.method === 'delete') {
				r = (!r || utils.isEmpty(r)) ? {success:true} : r;
			}

			// FORMAT RESPONSE?
			// Does self request have a corresponding formatter
			if (o.wrap && ((p.path in o.wrap) || ('default' in o.wrap))) {
				var wrap = (p.path in o.wrap ? p.path : 'default');
				var time = (new Date()).getTime();

				// FORMAT RESPONSE
				var b = o.wrap[wrap](r, headers, p);

				// Has the response been utterly overwritten?
				// Typically self augments the existing object.. but for those rare occassions
				if (b) {
					r = b;
				}
			}

			// Is there a next_page defined in the response?
			if (r && 'paging' in r && r.paging.next) {

				// Add the relative path if it is missing from the paging/next path
				if (r.paging.next[0] === '?') {
					r.paging.next = p.path + r.paging.next;
				}

				// The relative path has been defined, lets markup the handler in the HashFragment
				else {
					r.paging.next += '#' + p.path;
				}
			}

			// Dispatch to listeners
			// Emit events which pertain to the formatted response
			if (!r || 'error' in r) {
				promise.reject(r);
			}
			else {
				promise.fulfill(r);
			}
		});
	}
};

// API utilities
hello.utils.extend(hello.utils, {

	// Make an HTTP request
	request: function(p, callback) {

		var _this = this;
		var error = _this.error;

		// This has to go through a POST request
		if (!_this.isEmpty(p.data) && !('FileList' in window) && _this.hasBinary(p.data)) {

			// Disable XHR and JSONP
			p.xhr = false;
			p.jsonp = false;
		}

		// Check if the browser and service support CORS
		var cors = this.request_cors(function() {
			// If it does then run this...
			return ((p.xhr === undefined) || (p.xhr && (typeof (p.xhr) !== 'function' || p.xhr(p, p.query))));
		});

		if (cors) {

			formatUrl(p, function(url) {

				var x = _this.xhr(p.method, url, p.headers, p.data, callback);
				x.onprogress = p.onprogress || null;

				// Windows Phone does not support xhr.upload, see #74
				// Feature detect
				if (x.upload && p.onuploadprogress) {
					x.upload.onprogress = p.onuploadprogress;
				}

			});

			return;
		}

		// Clone the query object
		// Each request modifies the query object and needs to be tared after each one.
		var _query = p.query;

		p.query = _this.clone(p.query);

		// Assign a new callbackID
		p.callbackID = _this.globalEvent();

		// JSONP
		if (p.jsonp !== false) {

			// Clone the query object
			p.query.callback = p.callbackID;

			// If the JSONP is a function then run it
			if (typeof (p.jsonp) === 'function') {
				p.jsonp(p, p.query);
			}

			// Lets use JSONP if the method is 'get'
			if (p.method === 'get') {

				formatUrl(p, function(url) {
					_this.jsonp(url, callback, p.callbackID, p.timeout);
				});

				return;
			}
			else {
				// It's not compatible reset query
				p.query = _query;
			}

		}

		// Otherwise we're on to the old school, iframe hacks and JSONP
		if (p.form !== false) {

			// Add some additional query parameters to the URL
			// We're pretty stuffed if the endpoint doesn't like these
			p.query.redirect_uri = p.redirect_uri;
			p.query.state = JSON.stringify({callback:p.callbackID});

			var opts;

			if (typeof (p.form) === 'function') {

				// Format the request
				opts = p.form(p, p.query);
			}

			if (p.method === 'post' && opts !== false) {

				formatUrl(p, function(url) {
					_this.post(url, p.data, opts, callback, p.callbackID, p.timeout);
				});

				return;
			}
		}

		// None of the methods were successful throw an error
		callback(error('invalid_request', 'There was no mechanism for handling this request'));

		return;

		// Format URL
		// Constructs the request URL, optionally wraps the URL through a call to a proxy server
		// Returns the formatted URL
		function formatUrl(p, callback) {

			// Are we signing the request?
			var sign;

			// OAuth1
			// Remove the token from the query before signing
			if (p.authResponse && p.authResponse.oauth && parseInt(p.authResponse.oauth.version, 10) === 1) {

				// OAUTH SIGNING PROXY
				sign = p.query.access_token;

				// Remove the access_token
				delete p.query.access_token;

				// Enfore use of Proxy
				p.proxy = true;
			}

			// POST body to querystring
			if (p.data && (p.method === 'get' || p.method === 'delete')) {
				// Attach the p.data to the querystring.
				_this.extend(p.query, p.data);
				p.data = null;
			}

			// Construct the path
			var path = _this.qs(p.url, p.query);

			// Proxy the request through a server
			// Used for signing OAuth1
			// And circumventing services without Access-Control Headers
			if (p.proxy) {
				// Use the proxy as a path
				path = _this.qs(p.oauth_proxy, {
					path: path,
					access_token: sign || '',

					// This will prompt the request to be signed as though it is OAuth1
					then: p.proxy_response_type || (p.method.toLowerCase() === 'get' ? 'redirect' : 'proxy'),
					method: p.method.toLowerCase(),
					suppress_response_codes: true
				});
			}

			callback(path);
		}
	},

	// Test whether the browser supports the CORS response
	request_cors: function(callback) {
		return 'withCredentials' in new XMLHttpRequest() && callback();
	},

	// Return the type of DOM object
	domInstance: function(type, data) {
		var test = 'HTML' + (type || '').replace(
			/^[a-z]/,
			function(m) {
				return m.toUpperCase();
			}

		) + 'Element';

		if (!data) {
			return false;
		}

		if (window[test]) {
			return data instanceof window[test];
		}
		else if (window.Element) {
			return data instanceof window.Element && (!type || (data.tagName && data.tagName.toLowerCase() === type));
		}
		else {
			return (!(data instanceof Object || data instanceof Array || data instanceof String || data instanceof Number) && data.tagName && data.tagName.toLowerCase() === type);
		}
	},

	// Create a clone of an object
	clone: function(obj) {
		// Does not clone DOM elements, nor Binary data, e.g. Blobs, Filelists
		if (obj === null || typeof (obj) !== 'object' || obj instanceof Date || 'nodeName' in obj || this.isBinary(obj) || (typeof FormData === 'function' && obj instanceof FormData)) {
			return obj;
		}

		if (Array.isArray(obj)) {
			// Clone each item in the array
			return obj.map(this.clone.bind(this));
		}

		// But does clone everything else.
		var clone = {};
		for (var x in obj) {
			clone[x] = this.clone(obj[x]);
		}

		return clone;
	},

	// XHR: uses CORS to make requests
	xhr: function(method, url, headers, data, callback) {

		var r = new XMLHttpRequest();
		var error = this.error;

		// Binary?
		var binary = false;
		if (method === 'blob') {
			binary = method;
			method = 'GET';
		}

		method = method.toUpperCase();

		// Xhr.responseType 'json' is not supported in any of the vendors yet.
		r.onload = function(e) {
			var json = r.response;
			try {
				json = JSON.parse(r.responseText);
			}
			catch (_e) {
				if (r.status === 401) {
					json = error('access_denied', r.statusText);
				}
			}

			var headers = headersToJSON(r.getAllResponseHeaders());
			headers.statusCode = r.status;

			callback(json || (method === 'GET' ? error('empty_response', 'Could not get resource') : {}), headers);
		};

		r.onerror = function(e) {
			var json = r.responseText;
			try {
				json = JSON.parse(r.responseText);
			}
			catch (_e) {}

			callback(json || error('access_denied', 'Could not get resource'));
		};

		var x;

		// Should we add the query to the URL?
		if (method === 'GET' || method === 'DELETE') {
			data = null;
		}
		else if (data && typeof (data) !== 'string' && !(data instanceof FormData) && !(data instanceof File) && !(data instanceof Blob)) {
			// Loop through and add formData
			var f = new FormData();
			for (x in data) if (data.hasOwnProperty(x)) {
				if (data[x] instanceof HTMLInputElement) {
					if ('files' in data[x] && data[x].files.length > 0) {
						f.append(x, data[x].files[0]);
					}
				}
				else if (data[x] instanceof Blob) {
					f.append(x, data[x], data.name);
				}
				else {
					f.append(x, data[x]);
				}
			}

			data = f;
		}

		// Open the path, async
		r.open(method, url, true);

		if (binary) {
			if ('responseType' in r) {
				r.responseType = binary;
			}
			else {
				r.overrideMimeType('text/plain; charset=x-user-defined');
			}
		}

		// Set any bespoke headers
		if (headers) {
			for (x in headers) {
				r.setRequestHeader(x, headers[x]);
			}
		}

		r.send(data);

		return r;

		// Headers are returned as a string
		function headersToJSON(s) {
			var r = {};
			var reg = /([a-z\-]+):\s?(.*);?/gi;
			var m;
			while ((m = reg.exec(s))) {
				r[m[1]] = m[2];
			}

			return r;
		}
	},

	// JSONP
	// Injects a script tag into the DOM to be executed and appends a callback function to the window object
	// @param string/function pathFunc either a string of the URL or a callback function pathFunc(querystringhash, continueFunc);
	// @param function callback a function to call on completion;
	jsonp: function(url, callback, callbackID, timeout) {

		var _this = this;
		var error = _this.error;

		// Change the name of the callback
		var bool = 0;
		var head = document.getElementsByTagName('head')[0];
		var operaFix;
		var result = error('server_error', 'server_error');
		var cb = function() {
			if (!(bool++)) {
				window.setTimeout(function() {
					callback(result);
					head.removeChild(script);
				}, 0);
			}

		};

		// Add callback to the window object
		callbackID = _this.globalEvent(function(json) {
			result = json;
			return true;

			// Mark callback as done
		}, callbackID);

		// The URL is a function for some cases and as such
		// Determine its value with a callback containing the new parameters of this function.
		url = url.replace(new RegExp('=\\?(&|$)'), '=' + callbackID + '$1');

		// Build script tag
		var script = _this.append('script', {
			id: callbackID,
			name: callbackID,
			src: url,
			async: true,
			onload: cb,
			onerror: cb,
			onreadystatechange: function() {
				if (/loaded|complete/i.test(this.readyState)) {
					cb();
				}
			}
		});

		// Opera fix error
		// Problem: If an error occurs with script loading Opera fails to trigger the script.onerror handler we specified
		//
		// Fix:
		// By setting the request to synchronous we can trigger the error handler when all else fails.
		// This action will be ignored if we've already called the callback handler "cb" with a successful onload event
		if (window.navigator.userAgent.toLowerCase().indexOf('opera') > -1) {
			operaFix = _this.append('script', {
				text: 'document.getElementById(\'' + callbackID + '\').onerror();'
			});
			script.async = false;
		}

		// Add timeout
		if (timeout) {
			window.setTimeout(function() {
				result = error('timeout', 'timeout');
				cb();
			}, timeout);
		}

		// TODO: add fix for IE,
		// However: unable recreate the bug of firing off the onreadystatechange before the script content has been executed and the value of "result" has been defined.
		// Inject script tag into the head element
		head.appendChild(script);

		// Append Opera Fix to run after our script
		if (operaFix) {
			head.appendChild(operaFix);
		}
	},

	// Post
	// Send information to a remote location using the post mechanism
	// @param string uri path
	// @param object data, key value data to send
	// @param function callback, function to execute in response
	post: function(url, data, options, callback, callbackID, timeout) {

		var _this = this;
		var error = _this.error;
		var doc = document;

		// This hack needs a form
		var form = null;
		var reenableAfterSubmit = [];
		var newform;
		var i = 0;
		var x = null;
		var bool = 0;
		var cb = function(r) {
			if (!(bool++)) {
				callback(r);
			}
		};

		// What is the name of the callback to contain
		// We'll also use this to name the iframe
		_this.globalEvent(cb, callbackID);

		// Build the iframe window
		var win;
		try {
			// IE7 hack, only lets us define the name here, not later.
			win = doc.createElement('<iframe name="' + callbackID + '">');
		}
		catch (e) {
			win = doc.createElement('iframe');
		}

		win.name = callbackID;
		win.id = callbackID;
		win.style.display = 'none';

		// Override callback mechanism. Triggger a response onload/onerror
		if (options && options.callbackonload) {
			// Onload is being fired twice
			win.onload = function() {
				cb({
					response: 'posted',
					message: 'Content was posted'
				});
			};
		}

		if (timeout) {
			setTimeout(function() {
				cb(error('timeout', 'The post operation timed out'));
			}, timeout);
		}

		doc.body.appendChild(win);

		// If we are just posting a single item
		if (_this.domInstance('form', data)) {
			// Get the parent form
			form = data.form;

			// Loop through and disable all of its siblings
			for (i = 0; i < form.elements.length; i++) {
				if (form.elements[i] !== data) {
					form.elements[i].setAttribute('disabled', true);
				}
			}

			// Move the focus to the form
			data = form;
		}

		// Posting a form
		if (_this.domInstance('form', data)) {
			// This is a form element
			form = data;

			// Does this form need to be a multipart form?
			for (i = 0; i < form.elements.length; i++) {
				if (!form.elements[i].disabled && form.elements[i].type === 'file') {
					form.encoding = form.enctype = 'multipart/form-data';
					form.elements[i].setAttribute('name', 'file');
				}
			}
		}
		else {
			// Its not a form element,
			// Therefore it must be a JSON object of Key=>Value or Key=>Element
			// If anyone of those values are a input type=file we shall shall insert its siblings into the form for which it belongs.
			for (x in data) if (data.hasOwnProperty(x)) {
				// Is this an input Element?
				if (_this.domInstance('input', data[x]) && data[x].type === 'file') {
					form = data[x].form;
					form.encoding = form.enctype = 'multipart/form-data';
				}
			}

			// Do If there is no defined form element, lets create one.
			if (!form) {
				// Build form
				form = doc.createElement('form');
				doc.body.appendChild(form);
				newform = form;
			}

			var input;

			// Add elements to the form if they dont exist
			for (x in data) if (data.hasOwnProperty(x)) {

				// Is this an element?
				var el = (_this.domInstance('input', data[x]) || _this.domInstance('textArea', data[x]) || _this.domInstance('select', data[x]));

				// Is this not an input element, or one that exists outside the form.
				if (!el || data[x].form !== form) {

					// Does an element have the same name?
					var inputs = form.elements[x];
					if (input) {
						// Remove it.
						if (!(inputs instanceof NodeList)) {
							inputs = [inputs];
						}

						for (i = 0; i < inputs.length; i++) {
							inputs[i].parentNode.removeChild(inputs[i]);
						}

					}

					// Create an input element
					input = doc.createElement('input');
					input.setAttribute('type', 'hidden');
					input.setAttribute('name', x);

					// Does it have a value attribute?
					if (el) {
						input.value = data[x].value;
					}
					else if (_this.domInstance(null, data[x])) {
						input.value = data[x].innerHTML || data[x].innerText;
					}
					else {
						input.value = data[x];
					}

					form.appendChild(input);
				}

				// It is an element, which exists within the form, but the name is wrong
				else if (el && data[x].name !== x) {
					data[x].setAttribute('name', x);
					data[x].name = x;
				}
			}

			// Disable elements from within the form if they weren't specified
			for (i = 0; i < form.elements.length; i++) {

				input = form.elements[i];

				// Does the same name and value exist in the parent
				if (!(input.name in data) && input.getAttribute('disabled') !== true) {
					// Disable
					input.setAttribute('disabled', true);

					// Add re-enable to callback
					reenableAfterSubmit.push(input);
				}
			}
		}

		// Set the target of the form
		form.setAttribute('method', 'POST');
		form.setAttribute('target', callbackID);
		form.target = callbackID;

		// Update the form URL
		form.setAttribute('action', url);

		// Submit the form
		// Some reason this needs to be offset from the current window execution
		setTimeout(function() {
			form.submit();

			setTimeout(function() {
				try {
					// Remove the iframe from the page.
					//win.parentNode.removeChild(win);
					// Remove the form
					if (newform) {
						newform.parentNode.removeChild(newform);
					}
				}
				catch (e) {
					try {
						console.error('HelloJS: could not remove iframe');
					}
					catch (ee) {}
				}

				// Reenable the disabled form
				for (var i = 0; i < reenableAfterSubmit.length; i++) {
					if (reenableAfterSubmit[i]) {
						reenableAfterSubmit[i].setAttribute('disabled', false);
						reenableAfterSubmit[i].disabled = false;
					}
				}
			}, 0);
		}, 100);
	},

	// Some of the providers require that only multipart is used with non-binary forms.
	// This function checks whether the form contains binary data
	hasBinary: function(data) {
		for (var x in data) if (data.hasOwnProperty(x)) {
			if (this.isBinary(data[x])) {
				return true;
			}
		}

		return false;
	},

	// Determines if a variable Either Is or like a FormInput has the value of a Blob

	isBinary: function(data) {

		return data instanceof Object && (
		(this.domInstance('input', data) && data.type === 'file') ||
		('FileList' in window && data instanceof window.FileList) ||
		('File' in window && data instanceof window.File) ||
		('Blob' in window && data instanceof window.Blob));

	},

	// Convert Data-URI to Blob string
	toBlob: function(dataURI) {
		var reg = /^data\:([^;,]+(\;charset=[^;,]+)?)(\;base64)?,/i;
		var m = dataURI.match(reg);
		if (!m) {
			return dataURI;
		}

		var binary = atob(dataURI.replace(reg, ''));
		var array = [];
		for (var i = 0; i < binary.length; i++) {
			array.push(binary.charCodeAt(i));
		}

		return new Blob([new Uint8Array(array)], {type: m[1]});
	}

});

// EXTRA: Convert FormElement to JSON for POSTing
// Wrappers to add additional functionality to existing functions
(function(hello) {

	// Copy original function
	var api = hello.api;
	var utils = hello.utils;

	utils.extend(utils, {

		// DataToJSON
		// This takes a FormElement|NodeList|InputElement|MixedObjects and convers the data object to JSON.
		dataToJSON: function(p) {

			var _this = this;
			var w = window;
			var data = p.data;

			// Is data a form object
			if (_this.domInstance('form', data)) {
				data = _this.nodeListToJSON(data.elements);
			}
			else if ('NodeList' in w && data instanceof NodeList) {
				data = _this.nodeListToJSON(data);
			}
			else if (_this.domInstance('input', data)) {
				data = _this.nodeListToJSON([data]);
			}

			// Is data a blob, File, FileList?
			if (('File' in w && data instanceof w.File) ||
				('Blob' in w && data instanceof w.Blob) ||
				('FileList' in w && data instanceof w.FileList)) {
				data = {file: data};
			}

			// Loop through data if it's not form data it must now be a JSON object
			if (!('FormData' in w && data instanceof w.FormData)) {

				for (var x in data) if (data.hasOwnProperty(x)) {

					if ('FileList' in w && data[x] instanceof w.FileList) {
						if (data[x].length === 1) {
							data[x] = data[x][0];
						}
					}
					else if (_this.domInstance('input', data[x]) && data[x].type === 'file') {
						continue;
					}
					else if (_this.domInstance('input', data[x]) ||
						_this.domInstance('select', data[x]) ||
						_this.domInstance('textArea', data[x])) {
						data[x] = data[x].value;
					}
					else if (_this.domInstance(null, data[x])) {
						data[x] = data[x].innerHTML || data[x].innerText;
					}
				}
			}

			p.data = data;
			return data;
		},

		// NodeListToJSON
		// Given a list of elements extrapolate their values and return as a json object
		nodeListToJSON: function(nodelist) {

			var json = {};

			// Create a data string
			for (var i = 0; i < nodelist.length; i++) {

				var input = nodelist[i];

				// If the name of the input is empty or diabled, dont add it.
				if (input.disabled || !input.name) {
					continue;
				}

				// Is this a file, does the browser not support 'files' and 'FormData'?
				if (input.type === 'file') {
					json[input.name] = input;
				}
				else {
					json[input.name] = input.value || input.innerHTML;
				}
			}

			return json;
		}
	});

	// Replace it
	hello.api = function() {

		// Get arguments
		var p = utils.args({path: 's!', method: 's', data:'o', timeout: 'i', callback: 'f'}, arguments);

		// Change for into a data object
		if (p.data) {
			utils.dataToJSON(p);
		}

		return api.call(this, p);
	};

})(hello);

/////////////////////////////////////
//
// Save any access token that is in the current page URL
// Handle any response solicited through iframe hash tag following an API request
//
/////////////////////////////////////

hello.utils.responseHandler(window, window.opener || window.parent);

// Script to support ChromeApps
// This overides the hello.utils.popup method to support chrome.identity.launchWebAuthFlow
// See https://developer.chrome.com/apps/app_identity#non

// Is this a chrome app?

if (typeof chrome === 'object' && typeof chrome.identity === 'object' && chrome.identity.launchWebAuthFlow) {

	(function() {

		// Swap the popup method
		hello.utils.popup = function(url) {

			return _open(url, true);

		};

		// Swap the hidden iframe method
		hello.utils.iframe = function(url) {

			_open(url, false);

		};

		// Swap the request_cors method
		hello.utils.request_cors = function(callback) {

			callback();

			// Always run as CORS

			return true;
		};

		// Swap the storage method
		var _cache = {};
		chrome.storage.local.get('hello', function(r) {
			// Update the cache
			_cache = r.hello || {};
		});

		hello.utils.store = function(name, value) {

			// Get all
			if (arguments.length === 0) {
				return _cache;
			}

			// Get
			if (arguments.length === 1) {
				return _cache[name] || null;
			}

			// Set
			if (value) {
				_cache[name] = value;
				chrome.storage.local.set({hello: _cache});
				return value;
			}

			// Delete
			if (value === null) {
				delete _cache[name];
				chrome.storage.local.set({hello: _cache});
				return null;
			}
		};

		// Open function
		function _open(url, interactive) {

			// Launch
			var ref = {
				closed: false
			};

			// Launch the webAuthFlow
			chrome.identity.launchWebAuthFlow({
				url: url,
				interactive: interactive
			}, function(responseUrl) {

				// Did the user cancel this prematurely
				if (responseUrl === undefined) {
					ref.closed = true;
					return;
				}

				// Split appart the URL
				var a = hello.utils.url(responseUrl);

				// The location can be augmented in to a location object like so...
				// We dont have window operations on the popup so lets create some
				var _popup = {
					location: {

						// Change the location of the popup
						assign: function(url) {

							// If there is a secondary reassign
							// In the case of OAuth1
							// Trigger this in non-interactive mode.
							_open(url, false);
						},

						search: a.search,
						hash: a.hash,
						href: a.href
					},
					close: function() {}
				};

				// Then this URL contains information which HelloJS must process
				// URL string
				// Window - any action such as window relocation goes here
				// Opener - the parent window which opened this, aka this script

				hello.utils.responseHandler(_popup, window);
			});

			// Return the reference
			return ref;
		}

	})();
}

// Phonegap override for hello.phonegap.js
(function() {

	// Is this a phonegap implementation?
	if (!(/^file:\/{3}[^\/]/.test(window.location.href) && window.cordova)) {
		// Cordova is not included.
		return;
	}

	// Augment the hidden iframe method
	hello.utils.iframe = function(url, redirectUri) {
		hello.utils.popup(url, redirectUri, {hidden: 'yes'});
	};

	// Augment the popup
	var utilPopup = hello.utils.popup;

	// Replace popup
	hello.utils.popup = function(url, redirectUri, options) {

		// Run the standard
		var popup = utilPopup.call(this, url, redirectUri, options);

		// Create a function for reopening the popup, and assigning events to the new popup object
		// PhoneGap support
		// Add an event listener to listen to the change in the popup windows URL
		// This must appear before popup.focus();
		try {
			if (popup && popup.addEventListener) {

				// Get the origin of the redirect URI

				var a = hello.utils.url(redirectUri);
				var redirectUriOrigin = a.origin || (a.protocol + '//' + a.hostname);

				// Listen to changes in the InAppBrowser window

				popup.addEventListener('loadstart', function(e) {

					var url = e.url;

					// Is this the path, as given by the redirectUri?
					// Check the new URL agains the redirectUriOrigin.
					// According to #63 a user could click 'cancel' in some dialog boxes ....
					// The popup redirects to another page with the same origin, yet we still wish it to close.

					if (url.indexOf(redirectUriOrigin) !== 0) {
						return;
					}

					// Split appart the URL
					var a = hello.utils.url(url);

					// We dont have window operations on the popup so lets create some
					// The location can be augmented in to a location object like so...

					var _popup = {
						location: {
							// Change the location of the popup
							assign: function(location) {

								// Unfourtunatly an app is may not change the location of a InAppBrowser window.
								// So to shim this, just open a new one.
								popup.executeScript({code: 'window.location.href = "' + location + ';"'});
							},

							search: a.search,
							hash: a.hash,
							href: a.href
						},
						close: function() {
							if (popup.close) {
								popup.close();
								try {
									popup.closed = true;
								}
								catch (_e) {}
							}
						}
					};

					// Then this URL contains information which HelloJS must process
					// URL string
					// Window - any action such as window relocation goes here
					// Opener - the parent window which opened this, aka this script

					hello.utils.responseHandler(_popup, window);

				});
			}
		}
		catch (e) {}

		return popup;
	};

})();

(function(hello) {

	// OAuth1
	var OAuth1Settings = {
		version: '1.0',
		auth: 'https://www.dropbox.com/1/oauth/authorize',
		request: 'https://api.dropbox.com/1/oauth/request_token',
		token: 'https://api.dropbox.com/1/oauth/access_token'
	};

	// OAuth2 Settings
	var OAuth2Settings = {
		version: 2,
		auth: 'https://www.dropbox.com/1/oauth2/authorize',
		grant: 'https://api.dropbox.com/1/oauth2/token'
	};

	// Initiate the Dropbox module
	hello.init({

		dropbox: {

			name: 'Dropbox',

			oauth: OAuth2Settings,

			login: function(p) {
				// OAuth2 non-standard adjustments
				p.qs.scope = '';

				// Should this be run as OAuth1?
				// If the redirect_uri is is HTTP (non-secure) then its required to revert to the OAuth1 endpoints
				var redirect = decodeURIComponent(p.qs.redirect_uri);
				if (redirect.indexOf('http:') === 0 && redirect.indexOf('http://localhost/') !== 0) {

					// Override the dropbox OAuth settings.
					hello.services.dropbox.oauth = OAuth1Settings;
				}
				else {
					// Override the dropbox OAuth settings.
					hello.services.dropbox.oauth = OAuth2Settings;
				}

				// The dropbox login window is a different size
				p.options.popup.width = 1000;
				p.options.popup.height = 1000;
			},

			/*
				Dropbox does not allow insecure HTTP URI's in the redirect_uri field
				...otherwise I'd love to use OAuth2

				Follow request https://forums.dropbox.com/topic.php?id=106505

				p.qs.response_type = 'code';
				oauth: {
					version: 2,
					auth: 'https://www.dropbox.com/1/oauth2/authorize',
					grant: 'https://api.dropbox.com/1/oauth2/token'
				}
			*/

			// API Base URL
			base: 'https://api.dropbox.com/1/',

			// Bespoke setting: this is states whether to use the custom environment of Dropbox or to use their own environment
			// Because it's notoriously difficult for Dropbox too provide access from other webservices, this defaults to Sandbox
			root: 'sandbox',

			// Map GET requests
			get: {
				me: 'account/info',

				// Https://www.dropbox.com/developers/core/docs#metadata
				'me/files': req('metadata/auto/@{parent|}'),
				'me/folder': req('metadata/auto/@{id}'),
				'me/folders': req('metadata/auto/'),

				'default': function(p, callback) {
					if (p.path.match('https://api-content.dropbox.com/1/files/')) {
						// This is a file, return binary data
						p.method = 'blob';
					}

					callback(p.path);
				}
			},

			post: {
				'me/files': function(p, callback) {

					var path = p.data.parent;
					var fileName = p.data.name;

					p.data = {
						file: p.data.file
					};

					// Does this have a data-uri to upload as a file?
					if (typeof (p.data.file) === 'string') {
						p.data.file = hello.utils.toBlob(p.data.file);
					}

					callback('https://api-content.dropbox.com/1/files_put/auto/' + path + '/' + fileName);
				},

				'me/folders': function(p, callback) {

					var name = p.data.name;
					p.data = {};

					callback('fileops/create_folder?root=@{root|sandbox}&' + hello.utils.param({
						path: name
					}));
				}
			},

			// Map DELETE requests
			del: {
				'me/files': 'fileops/delete?root=@{root|sandbox}&path=@{id}',
				'me/folder': 'fileops/delete?root=@{root|sandbox}&path=@{id}'
			},

			wrap: {
				me: function(o) {
					formatError(o);
					if (!o.uid) {
						return o;
					}

					o.name = o.display_name;
					var m = o.name.split(' ');
					o.first_name = m.shift();
					o.last_name = m.join(' ');
					o.id = o.uid;
					delete o.uid;
					delete o.display_name;
					return o;
				},

				'default': function(o, headers, req) {
					formatError(o);
					if (o.is_dir && o.contents) {
						o.data = o.contents;
						delete o.contents;

						o.data.forEach(function(item) {
							item.root = o.root;
							formatFile(item, headers, req);
						});
					}

					formatFile(o, headers, req);

					if (o.is_deleted) {
						o.success = true;
					}

					return o;
				}
			},

			// Doesn't return the CORS headers
			xhr: function(p) {

				// The proxy supports allow-cross-origin-resource
				// Alas that's the only thing we're using.
				if (p.data && p.data.file) {
					var file = p.data.file;
					if (file) {
						if (file.files) {
							p.data = file.files[0];
						}
						else {
							p.data = file;
						}
					}
				}

				if (p.method === 'delete') {
					p.method = 'post';
				}

				return true;
			},

			form: function(p, qs) {
				delete qs.state;
				delete qs.redirect_uri;
			}
		}
	});

	function formatError(o) {
		if (o && 'error' in o) {
			o.error = {
				code: 'server_error',
				message: o.error.message || o.error
			};
		}
	}

	function formatFile(o, headers, req) {

		if (typeof o !== 'object' ||
			(typeof Blob !== 'undefined' && o instanceof Blob) ||
			(typeof ArrayBuffer !== 'undefined' && o instanceof ArrayBuffer)) {
			// This is a file, let it through unformatted
			return;
		}

		if ('error' in o) {
			return;
		}

		var path = (o.root !== 'app_folder' ? o.root : '') + o.path.replace(/\&/g, '%26');
		path = path.replace(/^\//, '');
		if (o.thumb_exists) {
			o.thumbnail = req.oauth_proxy + '?path=' +
			encodeURIComponent('https://api-content.dropbox.com/1/thumbnails/auto/' + path + '?format=jpeg&size=m') + '&access_token=' + req.options.access_token;
		}

		o.type = (o.is_dir ? 'folder' : o.mime_type);
		o.name = o.path.replace(/.*\//g, '');
		if (o.is_dir) {
			o.files = path.replace(/^\//, '');
		}
		else {
			o.downloadLink = hello.settings.oauth_proxy + '?path=' +
			encodeURIComponent('https://api-content.dropbox.com/1/files/auto/' + path) + '&access_token=' + req.options.access_token;
			o.file = 'https://api-content.dropbox.com/1/files/auto/' + path;
		}

		if (!o.id) {
			o.id = o.path.replace(/^\//, '');
		}

		// O.media = 'https://api-content.dropbox.com/1/files/' + path;
	}

	function req(str) {
		return function(p, cb) {
			delete p.query.limit;
			cb(str);
		};
	}

})(hello);

(function(hello) {

	hello.init({

		facebook: {

			name: 'Facebook',

			// SEE https://developers.facebook.com/docs/facebook-login/manually-build-a-login-flow/v2.1
			oauth: {
				version: 2,
				auth: 'https://www.facebook.com/dialog/oauth/',
				grant: 'https://graph.facebook.com/oauth/access_token'
			},

			// Authorization scopes
			scope: {
				basic: 'public_profile',
				email: 'email',
				share: 'user_posts',
				birthday: 'user_birthday',
				events: 'user_events',
				photos: 'user_photos',
				videos: 'user_videos',
				friends: 'user_friends',
				files: 'user_photos,user_videos',
				publish_files: 'user_photos,user_videos,publish_actions',
				publish: 'publish_actions',

				// Deprecated in v2.0
				// Create_event	: 'create_event',

				offline_access: ''
			},

			// Refresh the access_token
			refresh: false,

			login: function(p) {

				// Reauthenticate
				// https://developers.facebook.com/docs/facebook-login/reauthentication
				if (p.options.force) {
					p.qs.auth_type = 'reauthenticate';
				}

				// Set the display value
				p.qs.display = p.options.display || 'popup';
			},

			logout: function(callback, options) {
				// Assign callback to a global handler
				var callbackID = hello.utils.globalEvent(callback);
				var redirect = encodeURIComponent(hello.settings.redirect_uri + '?' + hello.utils.param({callback:callbackID, result: JSON.stringify({force:true}), state: '{}'}));
				var token = (options.authResponse || {}).access_token;
				hello.utils.iframe('https://www.facebook.com/logout.php?next=' + redirect + '&access_token=' + token);

				// Possible responses:
				// String URL	- hello.logout should handle the logout
				// Undefined	- this function will handle the callback
				// True - throw a success, this callback isn't handling the callback
				// False - throw a error
				if (!token) {
					// If there isn't a token, the above wont return a response, so lets trigger a response
					return false;
				}
			},

			// API Base URL
			base: 'https://graph.facebook.com/v2.7/',

			// Map GET requests
			get: {
				me: 'me?fields=email,first_name,last_name,name,timezone,verified',
				'me/friends': 'me/friends',
				'me/following': 'me/friends',
				'me/followers': 'me/friends',
				'me/share': 'me/feed',
				'me/like': 'me/likes',
				'me/files': 'me/albums',
				'me/albums': 'me/albums?fields=cover_photo,name',
				'me/album': '@{id}/photos?fields=picture',
				'me/photos': 'me/photos',
				'me/photo': '@{id}',
				'friend/albums': '@{id}/albums',
				'friend/photos': '@{id}/photos'

				// Pagination
				// Https://developers.facebook.com/docs/reference/api/pagination/
			},

			// Map POST requests
			post: {
				'me/share': 'me/feed',
				'me/photo': '@{id}'

				// Https://developers.facebook.com/docs/graph-api/reference/v2.2/object/likes/
			},

			wrap: {
				me: formatUser,
				'me/friends': formatFriends,
				'me/following': formatFriends,
				'me/followers': formatFriends,
				'me/albums': format,
				'me/photos': format,
				'me/files': format,
				'default': format
			},

			// Special requirements for handling XHR
			xhr: function(p, qs) {
				if (p.method === 'get' || p.method === 'post') {
					qs.suppress_response_codes = true;
				}

				// Is this a post with a data-uri?
				if (p.method === 'post' && p.data && typeof (p.data.file) === 'string') {
					// Convert the Data-URI to a Blob
					p.data.file = hello.utils.toBlob(p.data.file);
				}

				return true;
			},

			// Special requirements for handling JSONP fallback
			jsonp: function(p, qs) {
				var m = p.method;
				if (m !== 'get' && !hello.utils.hasBinary(p.data)) {
					p.data.method = m;
					p.method = 'get';
				}
				else if (p.method === 'delete') {
					qs.method = 'delete';
					p.method = 'post';
				}
			},

			// Special requirements for iframe form hack
			form: function(p) {
				return {
					// Fire the callback onload
					callbackonload: true
				};
			}
		}
	});

	var base = 'https://graph.facebook.com/';

	function formatUser(o) {
		if (o.id) {
			o.thumbnail = o.picture = 'https://graph.facebook.com/' + o.id + '/picture';
		}

		return o;
	}

	function formatFriends(o) {
		if ('data' in o) {
			o.data.forEach(formatUser);
		}

		return o;
	}

	function format(o, headers, req) {
		if (typeof o === 'boolean') {
			o = {success: o};
		}

		if (o && 'data' in o) {
			var token = req.query.access_token;

			if (!(o.data instanceof Array)) {
				var data = o.data;
				delete o.data;
				o.data = [data];
			}

			o.data.forEach(function(d) {

				if (d.picture) {
					d.thumbnail = d.picture;
				}

				d.pictures = (d.images || [])
					.sort(function(a, b) {
						return a.width - b.width;
					});

				if (d.cover_photo && d.cover_photo.id) {
					d.thumbnail = base + d.cover_photo.id + '/picture?access_token=' + token;
				}

				if (d.type === 'album') {
					d.files = d.photos = base + d.id + '/photos';
				}

				if (d.can_upload) {
					d.upload_location = base + d.id + '/photos';
				}
			});
		}

		return o;
	}

})(hello);

(function(hello) {

	hello.init({

		flickr: {

			name: 'Flickr',

			// Ensure that you define an oauth_proxy
			oauth: {
				version: '1.0a',
				auth: 'https://www.flickr.com/services/oauth/authorize?perms=read',
				request: 'https://www.flickr.com/services/oauth/request_token',
				token: 'https://www.flickr.com/services/oauth/access_token'
			},

			// API base URL
			base: 'https://api.flickr.com/services/rest',

			// Map GET resquests
			get: {
				me: sign('flickr.people.getInfo'),
				'me/friends': sign('flickr.contacts.getList', {per_page:'@{limit|50}'}),
				'me/following': sign('flickr.contacts.getList', {per_page:'@{limit|50}'}),
				'me/followers': sign('flickr.contacts.getList', {per_page:'@{limit|50}'}),
				'me/albums': sign('flickr.photosets.getList', {per_page:'@{limit|50}'}),
				'me/album': sign('flickr.photosets.getPhotos', {photoset_id: '@{id}'}),
				'me/photos': sign('flickr.people.getPhotos', {per_page:'@{limit|50}'})
			},

			wrap: {
				me: function(o) {
					formatError(o);
					o = checkResponse(o, 'person');
					if (o.id) {
						if (o.realname) {
							o.name = o.realname._content;
							var m = o.name.split(' ');
							o.first_name = m.shift();
							o.last_name = m.join(' ');
						}

						o.thumbnail = getBuddyIcon(o, 'l');
						o.picture = getBuddyIcon(o, 'l');
					}

					return o;
				},

				'me/friends': formatFriends,
				'me/followers': formatFriends,
				'me/following': formatFriends,
				'me/albums': function(o) {
					formatError(o);
					o = checkResponse(o, 'photosets');
					paging(o);
					if (o.photoset) {
						o.data = o.photoset;
						o.data.forEach(function(item) {
							item.name = item.title._content;
							item.photos = 'https://api.flickr.com/services/rest' + getApiUrl('flickr.photosets.getPhotos', {photoset_id: item.id}, true);
						});

						delete o.photoset;
					}

					return o;
				},

				'me/photos': function(o) {
					formatError(o);
					return formatPhotos(o);
				},

				'default': function(o) {
					formatError(o);
					return formatPhotos(o);
				}
			},

			xhr: false,

			jsonp: function(p, qs) {
				if (p.method == 'get') {
					delete qs.callback;
					qs.jsoncallback = p.callbackID;
				}
			}
		}
	});

	function getApiUrl(method, extraParams, skipNetwork) {
		var url = ((skipNetwork) ? '' : 'flickr:') +
			'?method=' + method +
			'&api_key=' + hello.services.flickr.id +
			'&format=json';
		for (var param in extraParams) {
			if (extraParams.hasOwnProperty(param)) {
				url += '&' + param + '=' + extraParams[param];
			}
		}

		return url;
	}

	// This is not exactly neat but avoid to call
	// The method 'flickr.test.login' for each api call

	function withUser(cb) {
		var auth = hello.getAuthResponse('flickr');
		cb(auth && auth.user_nsid ? auth.user_nsid : null);
	}

	function sign(url, params) {
		if (!params) {
			params = {};
		}

		return function(p, callback) {
			withUser(function(userId) {
				params.user_id = userId;
				callback(getApiUrl(url, params, true));
			});
		};
	}

	function getBuddyIcon(profile, size) {
		var url = 'https://www.flickr.com/images/buddyicon.gif';
		if (profile.nsid && profile.iconserver && profile.iconfarm) {
			url = 'https://farm' + profile.iconfarm + '.staticflickr.com/' +
				profile.iconserver + '/' +
				'buddyicons/' + profile.nsid +
				((size) ? '_' + size : '') + '.jpg';
		}

		return url;
	}

	// See: https://www.flickr.com/services/api/misc.urls.html
	function createPhotoUrl(id, farm, server, secret, size) {
		size = (size) ? '_' + size : '';
		return 'https://farm' + farm + '.staticflickr.com/' + server + '/' + id + '_' + secret + size + '.jpg';
	}

	function formatUser(o) {
	}

	function formatError(o) {
		if (o && o.stat && o.stat.toLowerCase() != 'ok') {
			o.error = {
				code: 'invalid_request',
				message: o.message
			};
		}
	}

	function formatPhotos(o) {
		if (o.photoset || o.photos) {
			var set = ('photoset' in o) ? 'photoset' : 'photos';
			o = checkResponse(o, set);
			paging(o);
			o.data = o.photo;
			delete o.photo;
			for (var i = 0; i < o.data.length; i++) {
				var photo = o.data[i];
				photo.name = photo.title;
				photo.picture = createPhotoUrl(photo.id, photo.farm, photo.server, photo.secret, '');
				photo.pictures = createPictures(photo.id, photo.farm, photo.server, photo.secret);
				photo.source = createPhotoUrl(photo.id, photo.farm, photo.server, photo.secret, 'b');
				photo.thumbnail = createPhotoUrl(photo.id, photo.farm, photo.server, photo.secret, 'm');
			}
		}

		return o;
	}

	// See: https://www.flickr.com/services/api/misc.urls.html
	function createPictures(id, farm, server, secret) {

		var NO_LIMIT = 2048;
		var sizes = [
			{id: 't', max: 100},
			{id: 'm', max: 240},
			{id: 'n', max: 320},
			{id: '', max: 500},
			{id: 'z', max: 640},
			{id: 'c', max: 800},
			{id: 'b', max: 1024},
			{id: 'h', max: 1600},
			{id: 'k', max: 2048},
			{id: 'o', max: NO_LIMIT}
		];

		return sizes.map(function(size) {
			return {
				source: createPhotoUrl(id, farm, server, secret, size.id),

				// Note: this is a guess that's almost certain to be wrong (unless square source)
				width: size.max,
				height: size.max
			};
		});
	}

	function checkResponse(o, key) {

		if (key in o) {
			o = o[key];
		}
		else if (!('error' in o)) {
			o.error = {
				code: 'invalid_request',
				message: o.message || 'Failed to get data from Flickr'
			};
		}

		return o;
	}

	function formatFriends(o) {
		formatError(o);
		if (o.contacts) {
			o = checkResponse(o, 'contacts');
			paging(o);
			o.data = o.contact;
			delete o.contact;
			for (var i = 0; i < o.data.length; i++) {
				var item = o.data[i];
				item.id = item.nsid;
				item.name = item.realname || item.username;
				item.thumbnail = getBuddyIcon(item, 'm');
			}
		}

		return o;
	}

	function paging(res) {
		if (res.page && res.pages && res.page !== res.pages) {
			res.paging = {
				next: '?page=' + (++res.page)
			};
		}
	}

})(hello);

(function(hello) {

	hello.init({

		foursquare: {

			name: 'Foursquare',

			oauth: {
				// See: https://developer.foursquare.com/overview/auth
				version: 2,
				auth: 'https://foursquare.com/oauth2/authenticate',
				grant: 'https://foursquare.com/oauth2/access_token'
			},

			// Refresh the access_token once expired
			refresh: true,

			base: 'https://api.foursquare.com/v2/',

			get: {
				me: 'users/self',
				'me/friends': 'users/self/friends',
				'me/followers': 'users/self/friends',
				'me/following': 'users/self/friends'
			},

			wrap: {
				me: function(o) {
					formatError(o);
					if (o && o.response) {
						o = o.response.user;
						formatUser(o);
					}

					return o;
				},

				'default': function(o) {
					formatError(o);

					// Format friends
					if (o && 'response' in o && 'friends' in o.response && 'items' in o.response.friends) {
						o.data = o.response.friends.items;
						o.data.forEach(formatUser);
						delete o.response;
					}

					return o;
				}
			},

			xhr: formatRequest,
			jsonp: formatRequest
		}
	});

	function formatError(o) {
		if (o.meta && (o.meta.code === 400 || o.meta.code === 401)) {
			o.error = {
				code: 'access_denied',
				message: o.meta.errorDetail
			};
		}
	}

	function formatUser(o) {
		if (o && o.id) {
			o.thumbnail = o.photo.prefix + '100x100' + o.photo.suffix;
			o.name = o.firstName + ' ' + o.lastName;
			o.first_name = o.firstName;
			o.last_name = o.lastName;
			if (o.contact) {
				if (o.contact.email) {
					o.email = o.contact.email;
				}
			}
		}
	}

	function formatRequest(p, qs) {
		var token = qs.access_token;
		delete qs.access_token;
		qs.oauth_token = token;
		qs.v = 20121125;
		return true;
	}

})(hello);

(function(hello) {

	hello.init({

		github: {

			name: 'GitHub',

			oauth: {
				version: 2,
				auth: 'https://github.com/login/oauth/authorize',
				grant: 'https://github.com/login/oauth/access_token',
				response_type: 'code'
			},

			scope: {
				email: 'user:email'
			},

			base: 'https://api.github.com/',

			get: {
				me: 'user',
				'me/friends': 'user/following?per_page=@{limit|100}',
				'me/following': 'user/following?per_page=@{limit|100}',
				'me/followers': 'user/followers?per_page=@{limit|100}',
				'me/like': 'user/starred?per_page=@{limit|100}'
			},

			wrap: {
				me: function(o, headers) {

					formatError(o, headers);
					formatUser(o);

					return o;
				},

				'default': function(o, headers, req) {

					formatError(o, headers);

					if (Array.isArray(o)) {
						o = {data:o};
					}

					if (o.data) {
						paging(o, headers, req);
						o.data.forEach(formatUser);
					}

					return o;
				}
			},

			xhr: function(p) {

				if (p.method !== 'get' && p.data) {

					// Serialize payload as JSON
					p.headers = p.headers || {};
					p.headers['Content-Type'] = 'application/json';
					if (typeof (p.data) === 'object') {
						p.data = JSON.stringify(p.data);
					}
				}

				return true;
			}
		}
	});

	function formatError(o, headers) {
		var code = headers ? headers.statusCode : (o && 'meta' in o && 'status' in o.meta && o.meta.status);
		if ((code === 401 || code === 403)) {
			o.error = {
				code: 'access_denied',
				message: o.message || (o.data ? o.data.message : 'Could not get response')
			};
			delete o.message;
		}
	}

	function formatUser(o) {
		if (o.id) {
			o.thumbnail = o.picture = o.avatar_url;
			o.name = o.login;
		}
	}

	function paging(res, headers, req) {
		if (res.data && res.data.length && headers && headers.Link) {
			var next = headers.Link.match(/<(.*?)>;\s*rel=\"next\"/);
			if (next) {
				res.paging = {
					next: next[1]
				};
			}
		}
	}

})(hello);

(function(hello) {

	var contactsUrl = 'https://www.google.com/m8/feeds/contacts/default/full?v=3.0&alt=json&max-results=@{limit|1000}&start-index=@{start|1}';

	hello.init({

		google: {

			name: 'Google Plus',

			// See: http://code.google.com/apis/accounts/docs/OAuth2UserAgent.html
			oauth: {
				version: 2,
				auth: 'https://accounts.google.com/o/oauth2/auth',
				grant: 'https://accounts.google.com/o/oauth2/token'
			},

			// Authorization scopes
			scope: {
				basic: 'https://www.googleapis.com/auth/plus.me profile',
				email: 'email',
				birthday: '',
				events: '',
				photos: 'https://picasaweb.google.com/data/',
				videos: 'http://gdata.youtube.com',
				friends: 'https://www.google.com/m8/feeds, https://www.googleapis.com/auth/plus.login',
				files: 'https://www.googleapis.com/auth/drive.readonly',
				publish: '',
				publish_files: 'https://www.googleapis.com/auth/drive',
				share: '',
				create_event: '',
				offline_access: ''
			},

			scope_delim: ' ',

			login: function(p) {

				if (p.qs.response_type === 'code') {

					// Let's set this to an offline access to return a refresh_token
					p.qs.access_type = 'offline';
				}

				// Reauthenticate
				// https://developers.google.com/identity/protocols/
				if (p.options.force) {
					p.qs.approval_prompt = 'force';
				}
			},

			// API base URI
			base: 'https://www.googleapis.com/',

			// Map GET requests
			get: {
				me: 'plus/v1/people/me',

				// Deprecated Sept 1, 2014
				//'me': 'oauth2/v1/userinfo?alt=json',

				// See: https://developers.google.com/+/api/latest/people/list
				'me/friends': 'plus/v1/people/me/people/visible?maxResults=@{limit|100}',
				'me/following': contactsUrl,
				'me/followers': contactsUrl,
				'me/contacts': contactsUrl,
				'me/share': 'plus/v1/people/me/activities/public?maxResults=@{limit|100}',
				'me/feed': 'plus/v1/people/me/activities/public?maxResults=@{limit|100}',
				'me/albums': 'https://picasaweb.google.com/data/feed/api/user/default?alt=json&max-results=@{limit|100}&start-index=@{start|1}',
				'me/album': function(p, callback) {
					var key = p.query.id;
					delete p.query.id;
					callback(key.replace('/entry/', '/feed/'));
				},

				'me/photos': 'https://picasaweb.google.com/data/feed/api/user/default?alt=json&kind=photo&max-results=@{limit|100}&start-index=@{start|1}',

				// See: https://developers.google.com/drive/v2/reference/files/list
				'me/file': 'drive/v2/files/@{id}',
				'me/files': 'drive/v2/files?q=%22@{parent|root}%22+in+parents+and+trashed=false&maxResults=@{limit|100}',

				// See: https://developers.google.com/drive/v2/reference/files/list
				'me/folders': 'drive/v2/files?q=%22@{id|root}%22+in+parents+and+mimeType+=+%22application/vnd.google-apps.folder%22+and+trashed=false&maxResults=@{limit|100}',

				// See: https://developers.google.com/drive/v2/reference/files/list
				'me/folder': 'drive/v2/files?q=%22@{id|root}%22+in+parents+and+trashed=false&maxResults=@{limit|100}'
			},

			// Map POST requests
			post: {

				// Google Drive
				'me/files': uploadDrive,
				'me/folders': function(p, callback) {
					p.data = {
						title: p.data.name,
						parents: [{id: p.data.parent || 'root'}],
						mimeType: 'application/vnd.google-apps.folder'
					};
					callback('drive/v2/files');
				}
			},

			// Map PUT requests
			put: {
				'me/files': uploadDrive
			},

			// Map DELETE requests
			del: {
				'me/files': 'drive/v2/files/@{id}',
				'me/folder': 'drive/v2/files/@{id}'
			},

			// Map PATCH requests
			patch: {
				'me/file': 'drive/v2/files/@{id}'
			},

			wrap: {
				me: function(o) {
					if (o.id) {
						o.last_name = o.family_name || (o.name ? o.name.familyName : null);
						o.first_name = o.given_name || (o.name ? o.name.givenName : null);

						if (o.emails && o.emails.length) {
							o.email = o.emails[0].value;
						}

						formatPerson(o);
					}

					return o;
				},

				'me/friends': function(o) {
					if (o.items) {
						paging(o);
						o.data = o.items;
						o.data.forEach(formatPerson);
						delete o.items;
					}

					return o;
				},

				'me/contacts': formatFriends,
				'me/followers': formatFriends,
				'me/following': formatFriends,
				'me/share': formatFeed,
				'me/feed': formatFeed,
				'me/albums': gEntry,
				'me/photos': formatPhotos,
				'default': gEntry
			},

			xhr: function(p) {

				if (p.method === 'post' || p.method === 'put') {
					toJSON(p);
				}
				else if (p.method === 'patch') {
					hello.utils.extend(p.query, p.data);
					p.data = null;
				}

				return true;
			},

			// Don't even try submitting via form.
			// This means no POST operations in <=IE9
			form: false
		}
	});

	function toInt(s) {
		return parseInt(s, 10);
	}

	function formatFeed(o) {
		paging(o);
		o.data = o.items;
		delete o.items;
		return o;
	}

	// Format: ensure each record contains a name, id etc.
	function formatItem(o) {
		if (o.error) {
			return;
		}

		if (!o.name) {
			o.name = o.title || o.message;
		}

		if (!o.picture) {
			o.picture = o.thumbnailLink;
		}

		if (!o.thumbnail) {
			o.thumbnail = o.thumbnailLink;
		}

		if (o.mimeType === 'application/vnd.google-apps.folder') {
			o.type = 'folder';
			o.files = 'https://www.googleapis.com/drive/v2/files?q=%22' + o.id + '%22+in+parents';
		}

		return o;
	}

	function formatImage(image) {
		return {
			source: image.url,
			width: image.width,
			height: image.height
		};
	}

	function formatPhotos(o) {
		o.data = o.feed.entry.map(formatEntry);
		delete o.feed;
	}

	// Google has a horrible JSON API
	function gEntry(o) {
		paging(o);

		if ('feed' in o && 'entry' in o.feed) {
			o.data = o.feed.entry.map(formatEntry);
			delete o.feed;
		}

		// Old style: Picasa, etc.
		else if ('entry' in o) {
			return formatEntry(o.entry);
		}

		// New style: Google Drive & Plus
		else if ('items' in o) {
			o.data = o.items.map(formatItem);
			delete o.items;
		}
		else {
			formatItem(o);
		}

		return o;
	}

	function formatPerson(o) {
		o.name = o.displayName || o.name;
		o.picture = o.picture || (o.image ? o.image.url : null);
		o.thumbnail = o.picture;
	}

	function formatFriends(o, headers, req) {
		paging(o);
		var r = [];
		if ('feed' in o && 'entry' in o.feed) {
			var token = req.query.access_token;
			for (var i = 0; i < o.feed.entry.length; i++) {
				var a = o.feed.entry[i];

				a.id	= a.id.$t;
				a.name	= a.title.$t;
				delete a.title;
				if (a.gd$email) {
					a.email	= (a.gd$email && a.gd$email.length > 0) ? a.gd$email[0].address : null;
					a.emails = a.gd$email;
					delete a.gd$email;
				}

				if (a.updated) {
					a.updated = a.updated.$t;
				}

				if (a.link) {

					var pic = (a.link.length > 0) ? a.link[0].href : null;
					if (pic && a.link[0].gd$etag) {
						pic += (pic.indexOf('?') > -1 ? '&' : '?') + 'access_token=' + token;
						a.picture = pic;
						a.thumbnail = pic;
					}

					delete a.link;
				}

				if (a.category) {
					delete a.category;
				}
			}

			o.data = o.feed.entry;
			delete o.feed;
		}

		return o;
	}

	function formatEntry(a) {

		var group = a.media$group;
		var photo = group.media$content.length ? group.media$content[0] : {};
		var mediaContent = group.media$content || [];
		var mediaThumbnail = group.media$thumbnail || [];

		var pictures = mediaContent
			.concat(mediaThumbnail)
			.map(formatImage)
			.sort(function(a, b) {
				return a.width - b.width;
			});

		var i = 0;
		var _a;
		var p = {
			id: a.id.$t,
			name: a.title.$t,
			description: a.summary.$t,
			updated_time: a.updated.$t,
			created_time: a.published.$t,
			picture: photo ? photo.url : null,
			pictures: pictures,
			images: [],
			thumbnail: photo ? photo.url : null,
			width: photo.width,
			height: photo.height
		};

		// Get feed/children
		if ('link' in a) {
			for (i = 0; i < a.link.length; i++) {
				var d = a.link[i];
				if (d.rel.match(/\#feed$/)) {
					p.upload_location = p.files = p.photos = d.href;
					break;
				}
			}
		}

		// Get images of different scales
		if ('category' in a && a.category.length) {
			_a = a.category;
			for (i = 0; i < _a.length; i++) {
				if (_a[i].scheme && _a[i].scheme.match(/\#kind$/)) {
					p.type = _a[i].term.replace(/^.*?\#/, '');
				}
			}
		}

		// Get images of different scales
		if ('media$thumbnail' in group && group.media$thumbnail.length) {
			_a = group.media$thumbnail;
			p.thumbnail = _a[0].url;
			p.images = _a.map(formatImage);
		}

		_a = group.media$content;

		if (_a && _a.length) {
			p.images.push(formatImage(_a[0]));
		}

		return p;
	}

	function paging(res) {

		// Contacts V2
		if ('feed' in res && res.feed.openSearch$itemsPerPage) {
			var limit = toInt(res.feed.openSearch$itemsPerPage.$t);
			var start = toInt(res.feed.openSearch$startIndex.$t);
			var total = toInt(res.feed.openSearch$totalResults.$t);

			if ((start + limit) < total) {
				res.paging = {
					next: '?start=' + (start + limit)
				};
			}
		}
		else if ('nextPageToken' in res) {
			res.paging = {
				next: '?pageToken=' + res.nextPageToken
			};
		}
	}

	// Construct a multipart message
	function Multipart() {

		// Internal body
		var body = [];
		var boundary = (Math.random() * 1e10).toString(32);
		var counter = 0;
		var lineBreak = '\r\n';
		var delim = lineBreak + '--' + boundary;
		var ready = function() {};

		var dataUri = /^data\:([^;,]+(\;charset=[^;,]+)?)(\;base64)?,/i;

		// Add file
		function addFile(item) {
			var fr = new FileReader();
			fr.onload = function(e) {
				addContent(btoa(e.target.result), item.type + lineBreak + 'Content-Transfer-Encoding: base64');
			};

			fr.readAsBinaryString(item);
		}

		// Add content
		function addContent(content, type) {
			body.push(lineBreak + 'Content-Type: ' + type + lineBreak + lineBreak + content);
			counter--;
			ready();
		}

		// Add new things to the object
		this.append = function(content, type) {

			// Does the content have an array
			if (typeof (content) === 'string' || !('length' in Object(content))) {
				// Converti to multiples
				content = [content];
			}

			for (var i = 0; i < content.length; i++) {

				counter++;

				var item = content[i];

				// Is this a file?
				// Files can be either Blobs or File types
				if (
					(typeof (File) !== 'undefined' && item instanceof File) ||
					(typeof (Blob) !== 'undefined' && item instanceof Blob)
				) {
					// Read the file in
					addFile(item);
				}

				// Data-URI?
				// Data:[<mime type>][;charset=<charset>][;base64],<encoded data>
				// /^data\:([^;,]+(\;charset=[^;,]+)?)(\;base64)?,/i
				else if (typeof (item) === 'string' && item.match(dataUri)) {
					var m = item.match(dataUri);
					addContent(item.replace(dataUri, ''), m[1] + lineBreak + 'Content-Transfer-Encoding: base64');
				}

				// Regular string
				else {
					addContent(item, type);
				}
			}
		};

		this.onready = function(fn) {
			ready = function() {
				if (counter === 0) {
					// Trigger ready
					body.unshift('');
					body.push('--');
					fn(body.join(delim), boundary);
					body = [];
				}
			};

			ready();
		};
	}

	// Upload to Drive
	// If this is PUT then only augment the file uploaded
	// PUT https://developers.google.com/drive/v2/reference/files/update
	// POST https://developers.google.com/drive/manage-uploads
	function uploadDrive(p, callback) {

		var data = {};

		// Test for DOM element
		if (p.data &&
			(typeof (HTMLInputElement) !== 'undefined' && p.data instanceof HTMLInputElement)
		) {
			p.data = {file: p.data};
		}

		if (!p.data.name && Object(Object(p.data.file).files).length && p.method === 'post') {
			p.data.name = p.data.file.files[0].name;
		}

		if (p.method === 'post') {
			p.data = {
				title: p.data.name,
				parents: [{id: p.data.parent || 'root'}],
				file: p.data.file
			};
		}
		else {

			// Make a reference
			data = p.data;
			p.data = {};

			// Add the parts to change as required
			if (data.parent) {
				p.data.parents = [{id: p.data.parent || 'root'}];
			}

			if (data.file) {
				p.data.file = data.file;
			}

			if (data.name) {
				p.data.title = data.name;
			}
		}

		// Extract the file, if it exists from the data object
		// If the File is an INPUT element lets just concern ourselves with the NodeList
		var file;
		if ('file' in p.data) {
			file = p.data.file;
			delete p.data.file;

			if (typeof (file) === 'object' && 'files' in file) {
				// Assign the NodeList
				file = file.files;
			}

			if (!file || !file.length) {
				callback({
					error: {
						code: 'request_invalid',
						message: 'There were no files attached with this request to upload'
					}
				});
				return;
			}
		}

		// Set type p.data.mimeType = Object(file[0]).type || 'application/octet-stream';

		// Construct a multipart message
		var parts = new Multipart();
		parts.append(JSON.stringify(p.data), 'application/json');

		// Read the file into a  base64 string... yep a hassle, i know
		// FormData doesn't let us assign our own Multipart headers and HTTP Content-Type
		// Alas GoogleApi need these in a particular format
		if (file) {
			parts.append(file);
		}

		parts.onready(function(body, boundary) {

			p.headers['content-type'] = 'multipart/related; boundary="' + boundary + '"';
			p.data = body;

			callback('upload/drive/v2/files' + (data.id ? '/' + data.id : '') + '?uploadType=multipart');
		});

	}

	function toJSON(p) {
		if (typeof (p.data) === 'object') {
			// Convert the POST into a javascript object
			try {
				p.data = JSON.stringify(p.data);
				p.headers['content-type'] = 'application/json';
			}
			catch (e) {}
		}
	}

})(hello);

(function(hello) {

	hello.init({

		instagram: {

			name: 'Instagram',

			oauth: {
				// See: http://instagram.com/developer/authentication/
				version: 2,
				auth: 'https://instagram.com/oauth/authorize/',
				grant: 'https://api.instagram.com/oauth/access_token'
			},

			// Refresh the access_token once expired
			refresh: true,

			scope: {
				basic: 'basic',
				photos: '',
				friends: 'relationships',
				publish: 'likes comments',
				email: '',
				share: '',
				publish_files: '',
				files: '',
				videos: '',
				offline_access: ''
			},

			scope_delim: ' ',

			base: 'https://api.instagram.com/v1/',

			get: {
				me: 'users/self',
				'me/feed': 'users/self/feed?count=@{limit|100}',
				'me/photos': 'users/self/media/recent?min_id=0&count=@{limit|100}',
				'me/friends': 'users/self/follows?count=@{limit|100}',
				'me/following': 'users/self/follows?count=@{limit|100}',
				'me/followers': 'users/self/followed-by?count=@{limit|100}',
				'friend/photos': 'users/@{id}/media/recent?min_id=0&count=@{limit|100}'
			},

			post: {
				'me/like': function(p, callback) {
					var id = p.data.id;
					p.data = {};
					callback('media/' + id + '/likes');
				}
			},

			del: {
				'me/like': 'media/@{id}/likes'
			},

			wrap: {
				me: function(o) {

					formatError(o);

					if ('data' in o) {
						o.id = o.data.id;
						o.thumbnail = o.data.profile_picture;
						o.name = o.data.full_name || o.data.username;
					}

					return o;
				},

				'me/friends': formatFriends,
				'me/following': formatFriends,
				'me/followers': formatFriends,
				'me/photos': function(o) {

					formatError(o);
					paging(o);

					if ('data' in o) {
						o.data = o.data.filter(function(d) {
							return d.type === 'image';
						});

						o.data.forEach(function(d) {
							d.name = d.caption ? d.caption.text : null;
							d.thumbnail = d.images.thumbnail.url;
							d.picture = d.images.standard_resolution.url;
							d.pictures = Object.keys(d.images)
								.map(function(key) {
									var image = d.images[key];
									return formatImage(image);
								})
								.sort(function(a, b) {
									return a.width - b.width;
								});
						});
					}

					return o;
				},

				'default': function(o) {
					o = formatError(o);
					paging(o);
					return o;
				}
			},

			// Instagram does not return any CORS Headers
			// So besides JSONP we're stuck with proxy
			xhr: function(p, qs) {

				var method = p.method;
				var proxy = method !== 'get';

				if (proxy) {

					if ((method === 'post' || method === 'put') && p.query.access_token) {
						p.data.access_token = p.query.access_token;
						delete p.query.access_token;
					}

					// No access control headers
					// Use the proxy instead
					p.proxy = proxy;
				}

				return proxy;
			},

			// No form
			form: false
		}
	});

	function formatImage(image) {
		return {
			source: image.url,
			width: image.width,
			height: image.height
		};
	}

	function formatError(o) {
		if (typeof o === 'string') {
			return {
				error: {
					code: 'invalid_request',
					message: o
				}
			};
		}

		if (o && 'meta' in o && 'error_type' in o.meta) {
			o.error = {
				code: o.meta.error_type,
				message: o.meta.error_message
			};
		}

		return o;
	}

	function formatFriends(o) {
		paging(o);
		if (o && 'data' in o) {
			o.data.forEach(formatFriend);
		}

		return o;
	}

	function formatFriend(o) {
		if (o.id) {
			o.thumbnail = o.profile_picture;
			o.name = o.full_name || o.username;
		}
	}

	// See: http://instagram.com/developer/endpoints/
	function paging(res) {
		if ('pagination' in res) {
			res.paging = {
				next: res.pagination.next_url
			};
			delete res.pagination;
		}
	}

})(hello);

(function(hello) {

	hello.init({

		joinme: {

			name: 'join.me',

			oauth: {
				version: 2,
				auth: 'https://secure.join.me/api/public/v1/auth/oauth2',
				grant: 'https://secure.join.me/api/public/v1/auth/oauth2'
			},

			refresh: false,

			scope: {
				basic: 'user_info',
				user: 'user_info',
				scheduler: 'scheduler',
				start: 'start_meeting',
				email: '',
				friends: '',
				share: '',
				publish: '',
				photos: '',
				publish_files: '',
				files: '',
				videos: '',
				offline_access: ''
			},

			scope_delim: ' ',

			login: function(p) {
				p.options.popup.width = 400;
				p.options.popup.height = 700;
			},

			base: 'https://api.join.me/v1/',

			get: {
				me: 'user',
				meetings: 'meetings',
				'meetings/info': 'meetings/@{id}'
			},

			post: {
				'meetings/start/adhoc': function(p, callback) {
					callback('meetings/start');
				},

				'meetings/start/scheduled': function(p, callback) {
					var meetingId = p.data.meetingId;
					p.data = {};
					callback('meetings/' + meetingId + '/start');
				},

				'meetings/schedule': function(p, callback) {
					callback('meetings');
				}
			},

			patch: {
				'meetings/update': function(p, callback) {
					callback('meetings/' + p.data.meetingId);
				}
			},

			del: {
				'meetings/delete': 'meetings/@{id}'
			},

			wrap: {
				me: function(o, headers) {
					formatError(o, headers);

					if (!o.email) {
						return o;
					}

					o.name = o.fullName;
					o.first_name = o.name.split(' ')[0];
					o.last_name = o.name.split(' ')[1];
					o.id = o.email;

					return o;
				},

				'default': function(o, headers) {
					formatError(o, headers);

					return o;
				}
			},

			xhr: formatRequest

		}
	});

	function formatError(o, headers) {
		var errorCode;
		var message;
		var details;

		if (o && ('Message' in o)) {
			message = o.Message;
			delete o.Message;

			if ('ErrorCode' in o) {
				errorCode = o.ErrorCode;
				delete o.ErrorCode;
			}
			else {
				errorCode = getErrorCode(headers);
			}

			o.error = {
				code: errorCode,
				message: message,
				details: o
			};
		}

		return o;
	}

	function formatRequest(p, qs) {
		// Move the access token from the request body to the request header
		var token = qs.access_token;
		delete qs.access_token;
		p.headers.Authorization = 'Bearer ' + token;

		// Format non-get requests to indicate json body
		if (p.method !== 'get' && p.data) {
			p.headers['Content-Type'] = 'application/json';
			if (typeof (p.data) === 'object') {
				p.data = JSON.stringify(p.data);
			}
		}

		if (p.method === 'put') {
			p.method = 'patch';
		}

		return true;
	}

	function getErrorCode(headers) {
		switch (headers.statusCode) {
			case 400:
				return 'invalid_request';
			case 403:
				return 'stale_token';
			case 401:
				return 'invalid_token';
			case 500:
				return 'server_error';
			default:
				return 'server_error';
		}
	}

}(hello));

(function(hello) {

	hello.init({

		linkedin: {

			oauth: {
				version: 2,
				response_type: 'code',
				auth: 'https://www.linkedin.com/uas/oauth2/authorization',
				grant: 'https://www.linkedin.com/uas/oauth2/accessToken'
			},

			// Refresh the access_token once expired
			refresh: true,

			scope: {
				basic: 'r_basicprofile',
				email: 'r_emailaddress',
				files: '',
				friends: '',
				photos: '',
				publish: 'w_share',
				publish_files: 'w_share',
				share: '',
				videos: '',
				offline_access: ''
			},
			scope_delim: ' ',

			base: 'https://api.linkedin.com/v1/',

			get: {
				me: 'people/~:(picture-url,first-name,last-name,id,formatted-name,email-address)',

				// See: http://developer.linkedin.com/documents/get-network-updates-and-statistics-api
				'me/share': 'people/~/network/updates?count=@{limit|250}'
			},

			post: {

				// See: https://developer.linkedin.com/documents/api-requests-json
				'me/share': function(p, callback) {
					var data = {
						visibility: {
							code: 'anyone'
						}
					};

					if (p.data.id) {

						data.attribution = {
							share: {
								id: p.data.id
							}
						};

					}
					else {
						data.comment = p.data.message;
						if (p.data.picture && p.data.link) {
							data.content = {
								'submitted-url': p.data.link,
								'submitted-image-url': p.data.picture
							};
						}
					}

					p.data = JSON.stringify(data);

					callback('people/~/shares?format=json');
				},

				'me/like': like
			},

			del:{
				'me/like': like
			},

			wrap: {
				me: function(o) {
					formatError(o);
					formatUser(o);
					return o;
				},

				'me/friends': formatFriends,
				'me/following': formatFriends,
				'me/followers': formatFriends,
				'me/share': function(o) {
					formatError(o);
					paging(o);
					if (o.values) {
						o.data = o.values.map(formatUser);
						o.data.forEach(function(item) {
							item.message = item.headline;
						});

						delete o.values;
					}

					return o;
				},

				'default': function(o, headers) {
					formatError(o);
					empty(o, headers);
					paging(o);
				}
			},

			jsonp: function(p, qs) {
				formatQuery(qs);
				if (p.method === 'get') {
					qs.format = 'jsonp';
					qs['error-callback'] = p.callbackID;
				}
			},

			xhr: function(p, qs) {
				if (p.method !== 'get') {
					formatQuery(qs);
					p.headers['Content-Type'] = 'application/json';

					// Note: x-li-format ensures error responses are not returned in XML
					p.headers['x-li-format'] = 'json';
					p.proxy = true;
					return true;
				}

				return false;
			}
		}
	});

	function formatError(o) {
		if (o && 'errorCode' in o) {
			o.error = {
				code: o.status,
				message: o.message
			};
		}
	}

	function formatUser(o) {
		if (o.error) {
			return;
		}

		o.first_name = o.firstName;
		o.last_name = o.lastName;
		o.name = o.formattedName || (o.first_name + ' ' + o.last_name);
		o.thumbnail = o.pictureUrl;
		o.email = o.emailAddress;
		return o;
	}

	function formatFriends(o) {
		formatError(o);
		paging(o);
		if (o.values) {
			o.data = o.values.map(formatUser);
			delete o.values;
		}

		return o;
	}

	function paging(res) {
		if ('_count' in res && '_start' in res && (res._count + res._start) < res._total) {
			res.paging = {
				next: '?start=' + (res._start + res._count) + '&count=' + res._count
			};
		}
	}

	function empty(o, headers) {
		if (JSON.stringify(o) === '{}' && headers.statusCode === 200) {
			o.success = true;
		}
	}

	function formatQuery(qs) {
		// LinkedIn signs requests with the parameter 'oauth2_access_token'
		// ... yeah another one who thinks they should be different!
		if (qs.access_token) {
			qs.oauth2_access_token = qs.access_token;
			delete qs.access_token;
		}
	}

	function like(p, callback) {
		p.headers['x-li-format'] = 'json';
		var id = p.data.id;
		p.data = (p.method !== 'delete').toString();
		p.method = 'put';
		callback('people/~/network/updates/key=' + id + '/is-liked');
	}

})(hello);

// See: https://developers.soundcloud.com/docs/api/reference
(function(hello) {

	hello.init({

		soundcloud: {
			name: 'SoundCloud',

			oauth: {
				version: 2,
				auth: 'https://soundcloud.com/connect',
				grant: 'https://soundcloud.com/oauth2/token'
			},

			// Request path translated
			base: 'https://api.soundcloud.com/',
			get: {
				me: 'me.json',

				// Http://developers.soundcloud.com/docs/api/reference#me
				'me/friends': 'me/followings.json',
				'me/followers': 'me/followers.json',
				'me/following': 'me/followings.json',

				// See: http://developers.soundcloud.com/docs/api/reference#activities
				'default': function(p, callback) {

					// Include '.json at the end of each request'
					callback(p.path + '.json');
				}
			},

			// Response handlers
			wrap: {
				me: function(o) {
					formatUser(o);
					return o;
				},

				'default': function(o) {
					if (Array.isArray(o)) {
						o = {
							data: o.map(formatUser)
						};
					}

					paging(o);
					return o;
				}
			},

			xhr: formatRequest,
			jsonp: formatRequest
		}
	});

	function formatRequest(p, qs) {
		// Alter the querystring
		var token = qs.access_token;
		delete qs.access_token;
		qs.oauth_token = token;
		qs['_status_code_map[302]'] = 200;
		return true;
	}

	function formatUser(o) {
		if (o.id) {
			o.picture = o.avatar_url;
			o.thumbnail = o.avatar_url;
			o.name = o.username || o.full_name;
		}

		return o;
	}

	// See: http://developers.soundcloud.com/docs/api/reference#activities
	function paging(res) {
		if ('next_href' in res) {
			res.paging = {
				next: res.next_href
			};
		}
	}

})(hello);

(function(hello) {

	var base = 'https://api.twitter.com/';

	hello.init({

		twitter: {

			// Ensure that you define an oauth_proxy
			oauth: {
				version: '1.0a',
				auth: base + 'oauth/authenticate',
				request: base + 'oauth/request_token',
				token: base + 'oauth/access_token'
			},

			login: function(p) {
				// Reauthenticate
				// https://dev.twitter.com/oauth/reference/get/oauth/authenticate
				var prefix = '?force_login=true';
				this.oauth.auth = this.oauth.auth.replace(prefix, '') + (p.options.force ? prefix : '');
			},

			base: base + '1.1/',

			get: {
				me: 'account/verify_credentials.json',
				'me/friends': 'friends/list.json?count=@{limit|200}',
				'me/following': 'friends/list.json?count=@{limit|200}',
				'me/followers': 'followers/list.json?count=@{limit|200}',

				// Https://dev.twitter.com/docs/api/1.1/get/statuses/user_timeline
				'me/share': 'statuses/user_timeline.json?count=@{limit|200}',

				// Https://dev.twitter.com/rest/reference/get/favorites/list
				'me/like': 'favorites/list.json?count=@{limit|200}'
			},

			post: {
				'me/share': function(p, callback) {

					var data = p.data;
					p.data = null;

					var status = [];

					// Change message to status
					if (data.message) {
						status.push(data.message);
						delete data.message;
					}

					// If link is given
					if (data.link) {
						status.push(data.link);
						delete data.link;
					}

					if (data.picture) {
						status.push(data.picture);
						delete data.picture;
					}

					// Compound all the components
					if (status.length) {
						data.status = status.join(' ');
					}

					// Tweet media
					if (data.file) {
						data['media[]'] = data.file;
						delete data.file;
						p.data = data;
						callback('statuses/update_with_media.json');
					}

					// Retweet?
					else if ('id' in data) {
						callback('statuses/retweet/' + data.id + '.json');
					}

					// Tweet
					else {
						// Assign the post body to the query parameters
						hello.utils.extend(p.query, data);
						callback('statuses/update.json?include_entities=1');
					}
				},

				// See: https://dev.twitter.com/rest/reference/post/favorites/create
				'me/like': function(p, callback) {
					var id = p.data.id;
					p.data = null;
					callback('favorites/create.json?id=' + id);
				}
			},

			del: {

				// See: https://dev.twitter.com/rest/reference/post/favorites/destroy
				'me/like': function() {
					p.method = 'post';
					var id = p.data.id;
					p.data = null;
					callback('favorites/destroy.json?id=' + id);
				}
			},

			wrap: {
				me: function(res) {
					formatError(res);
					formatUser(res);
					return res;
				},

				'me/friends': formatFriends,
				'me/followers': formatFriends,
				'me/following': formatFriends,

				'me/share': function(res) {
					formatError(res);
					paging(res);
					if (!res.error && 'length' in res) {
						return {data: res};
					}

					return res;
				},

				'default': function(res) {
					res = arrayToDataResponse(res);
					paging(res);
					return res;
				}
			},
			xhr: function(p) {

				// Rely on the proxy for non-GET requests.
				return (p.method !== 'get');
			}
		}
	});

	function formatUser(o) {
		if (o.id) {
			if (o.name) {
				var m = o.name.split(' ');
				o.first_name = m.shift();
				o.last_name = m.join(' ');
			}

			// See: https://dev.twitter.com/overview/general/user-profile-images-and-banners
			o.thumbnail = o.profile_image_url_https || o.profile_image_url;
		}

		return o;
	}

	function formatFriends(o) {
		formatError(o);
		paging(o);
		if (o.users) {
			o.data = o.users.map(formatUser);
			delete o.users;
		}

		return o;
	}

	function formatError(o) {
		if (o.errors) {
			var e = o.errors[0];
			o.error = {
				code: 'request_failed',
				message: e.message
			};
		}
	}

	// Take a cursor and add it to the path
	function paging(res) {
		// Does the response include a 'next_cursor_string'
		if ('next_cursor_str' in res) {
			// See: https://dev.twitter.com/docs/misc/cursoring
			res.paging = {
				next: '?cursor=' + res.next_cursor_str
			};
		}
	}

	function arrayToDataResponse(res) {
		return Array.isArray(res) ? {data: res} : res;
	}

	/**
	// The documentation says to define user in the request
	// Although its not actually required.

	var user_id;

	function withUserId(callback){
		if(user_id){
			callback(user_id);
		}
		else{
			hello.api('twitter:/me', function(o){
				user_id = o.id;
				callback(o.id);
			});
		}
	}

	function sign(url){
		return function(p, callback){
			withUserId(function(user_id){
				callback(url+'?user_id='+user_id);
			});
		};
	}
	*/

})(hello);

// Vkontakte (vk.com)
(function(hello) {

	hello.init({

		vk: {
			name: 'Vk',

			// See https://vk.com/dev/oauth_dialog
			oauth: {
				version: 2,
				auth: 'https://oauth.vk.com/authorize',
				grant: 'https://oauth.vk.com/access_token'
			},

			// Authorization scopes
			// See https://vk.com/dev/permissions
			scope: {
				email: 'email',
				friends: 'friends',
				photos: 'photos',
				videos: 'video',
				share: 'share',
				offline_access: 'offline'
			},

			// Refresh the access_token
			refresh: true,

			login: function(p) {
				p.qs.display = window.navigator &&
					window.navigator.userAgent &&
					/ipad|phone|phone|android/.test(window.navigator.userAgent.toLowerCase()) ? 'mobile' : 'popup';
			},

			// API Base URL
			base: 'https://api.vk.com/method/',

			// Map GET requests
			get: {
				me: function(p, callback) {
					p.query.fields = 'id,first_name,last_name,photo_max';
					callback('users.get');
				}
			},

			wrap: {
				me: function(res, headers, req) {
					formatError(res);
					return formatUser(res, req);
				}
			},

			// No XHR
			xhr: false,

			// All requests should be JSONP as of missing CORS headers in https://api.vk.com/method/*
			jsonp: true,

			// No form
			form: false
		}
	});

	function formatUser(o, req) {

		if (o !== null && 'response' in o && o.response !== null && o.response.length) {
			o = o.response[0];
			o.id = o.uid;
			o.thumbnail = o.picture = o.photo_max;
			o.name = o.first_name + ' ' + o.last_name;

			if (req.authResponse && req.authResponse.email !== null)
				o.email = req.authResponse.email;
		}

		return o;
	}

	function formatError(o) {

		if (o.error) {
			var e = o.error;
			o.error = {
				code: e.error_code,
				message: e.error_msg
			};
		}
	}

})(hello);

(function(hello) {

	hello.init({
		windows: {
			name: 'Windows live',

			// REF: http://msdn.microsoft.com/en-us/library/hh243641.aspx
			oauth: {
				version: 2,
				auth: 'https://login.live.com/oauth20_authorize.srf',
				grant: 'https://login.live.com/oauth20_token.srf'
			},

			// Refresh the access_token once expired
			refresh: true,

			logout: function() {
				return 'http://login.live.com/oauth20_logout.srf?ts=' + (new Date()).getTime();
			},

			// Authorization scopes
			scope: {
				basic: 'wl.signin,wl.basic',
				email: 'wl.emails',
				birthday: 'wl.birthday',
				events: 'wl.calendars',
				photos: 'wl.photos',
				videos: 'wl.photos',
				friends: 'wl.contacts_emails',
				files: 'wl.skydrive',
				publish: 'wl.share',
				publish_files: 'wl.skydrive_update',
				share: 'wl.share',
				create_event: 'wl.calendars_update,wl.events_create',
				offline_access: 'wl.offline_access'
			},

			// API base URL
			base: 'https://apis.live.net/v5.0/',

			// Map GET requests
			get: {

				// Friends
				me: 'me',
				'me/friends': 'me/friends',
				'me/following': 'me/contacts',
				'me/followers': 'me/friends',
				'me/contacts': 'me/contacts',

				'me/albums': 'me/albums',

				// Include the data[id] in the path
				'me/album': '@{id}/files',
				'me/photo': '@{id}',

				// Files
				'me/files': '@{parent|me/skydrive}/files',
				'me/folders': '@{id|me/skydrive}/files',
				'me/folder': '@{id|me/skydrive}/files'
			},

			// Map POST requests
			post: {
				'me/albums': 'me/albums',
				'me/album': '@{id}/files/',

				'me/folders': '@{id|me/skydrive/}',
				'me/files': '@{parent|me/skydrive}/files'
			},

			// Map DELETE requests
			del: {
				// Include the data[id] in the path
				'me/album': '@{id}',
				'me/photo': '@{id}',
				'me/folder': '@{id}',
				'me/files': '@{id}'
			},

			wrap: {
				me: formatUser,

				'me/friends': formatFriends,
				'me/contacts': formatFriends,
				'me/followers': formatFriends,
				'me/following': formatFriends,
				'me/albums': formatAlbums,
				'me/photos': formatDefault,
				'default': formatDefault
			},

			xhr: function(p) {
				if (p.method !== 'get' && p.method !== 'delete' && !hello.utils.hasBinary(p.data)) {

					// Does this have a data-uri to upload as a file?
					if (typeof (p.data.file) === 'string') {
						p.data.file = hello.utils.toBlob(p.data.file);
					}
					else {
						p.data = JSON.stringify(p.data);
						p.headers = {
							'Content-Type': 'application/json'
						};
					}
				}

				return true;
			},

			jsonp: function(p) {
				if (p.method !== 'get' && !hello.utils.hasBinary(p.data)) {
					p.data.method = p.method;
					p.method = 'get';
				}
			}
		}
	});

	function formatDefault(o) {
		if ('data' in o) {
			o.data.forEach(function(d) {
				if (d.picture) {
					d.thumbnail = d.picture;
				}

				if (d.images) {
					d.pictures = d.images
						.map(formatImage)
						.sort(function(a, b) {
							return a.width - b.width;
						});
				}
			});
		}

		return o;
	}

	function formatImage(image) {
		return {
			width: image.width,
			height: image.height,
			source: image.source
		};
	}

	function formatAlbums(o) {
		if ('data' in o) {
			o.data.forEach(function(d) {
				d.photos = d.files = 'https://apis.live.net/v5.0/' + d.id + '/photos';
			});
		}

		return o;
	}

	function formatUser(o, headers, req) {
		if (o.id) {
			var token = req.query.access_token;
			if (o.emails) {
				o.email = o.emails.preferred;
			}

			// If this is not an non-network friend
			if (o.is_friend !== false) {
				// Use the id of the user_id if available
				var id = (o.user_id || o.id);
				o.thumbnail = o.picture = 'https://apis.live.net/v5.0/' + id + '/picture?access_token=' + token;
			}
		}

		return o;
	}

	function formatFriends(o, headers, req) {
		if ('data' in o) {
			o.data.forEach(function(d) {
				formatUser(d, headers, req);
			});
		}

		return o;
	}

})(hello);

(function(hello) {

	hello.init({

		yahoo: {

			// Ensure that you define an oauth_proxy
			oauth: {
				version: '1.0a',
				auth: 'https://api.login.yahoo.com/oauth/v2/request_auth',
				request: 'https://api.login.yahoo.com/oauth/v2/get_request_token',
				token: 'https://api.login.yahoo.com/oauth/v2/get_token'
			},

			// Login handler
			login: function(p) {
				// Change the default popup window to be at least 560
				// Yahoo does dynamically change it on the fly for the signin screen (only, what if your already signed in)
				p.options.popup.width = 560;

				// Yahoo throws an parameter error if for whatever reason the state.scope contains a comma, so lets remove scope
				try {delete p.qs.state.scope;}
				catch (e) {}
			},

			base: 'https://social.yahooapis.com/v1/',

			get: {
				me: yql('select * from social.profile(0) where guid=me'),
				'me/friends': yql('select * from social.contacts(0) where guid=me'),
				'me/following': yql('select * from social.contacts(0) where guid=me')
			},
			wrap: {
				me: formatUser,

				// Can't get IDs
				// It might be better to loop through the social.relationship table with has unique IDs of users.
				'me/friends': formatFriends,
				'me/following': formatFriends,
				'default': paging
			}
		}
	});

	/*
		// Auto-refresh fix: bug in Yahoo can't get this to work with node-oauth-shim
		login : function(o){
			// Is the user already logged in
			var auth = hello('yahoo').getAuthResponse();

			// Is this a refresh token?
			if(o.options.display==='none'&&auth&&auth.access_token&&auth.refresh_token){
				// Add the old token and the refresh token, including path to the query
				// See http://developer.yahoo.com/oauth/guide/oauth-refreshaccesstoken.html
				o.qs.access_token = auth.access_token;
				o.qs.refresh_token = auth.refresh_token;
				o.qs.token_url = 'https://api.login.yahoo.com/oauth/v2/get_token';
			}
		},
	*/

	function formatError(o) {
		if (o && 'meta' in o && 'error_type' in o.meta) {
			o.error = {
				code: o.meta.error_type,
				message: o.meta.error_message
			};
		}
	}

	function formatUser(o) {

		formatError(o);
		if (o.query && o.query.results && o.query.results.profile) {
			o = o.query.results.profile;
			o.id = o.guid;
			o.last_name = o.familyName;
			o.first_name = o.givenName || o.nickname;
			var a = [];
			if (o.first_name) {
				a.push(o.first_name);
			}

			if (o.last_name) {
				a.push(o.last_name);
			}

			o.name = a.join(' ');
			o.email = (o.emails && o.emails[0]) ? o.emails[0].handle : null;
			o.thumbnail = o.image ? o.image.imageUrl : null;
		}

		return o;
	}

	function formatFriends(o, headers, request) {
		formatError(o);
		paging(o, headers, request);
		var contact;
		var field;
		if (o.query && o.query.results && o.query.results.contact) {
			o.data = o.query.results.contact;
			delete o.query;

			if (!Array.isArray(o.data)) {
				o.data = [o.data];
			}

			o.data.forEach(formatFriend);
		}

		return o;
	}

	function formatFriend(contact) {
		contact.id = null;

		// #362: Reports of responses returning a single item, rather than an Array of items.
		// Format the contact.fields to be an array.
		if (contact.fields && !(contact.fields instanceof Array)) {
			contact.fields = [contact.fields];
		}

		(contact.fields || []).forEach(function(field) {
			if (field.type === 'email') {
				contact.email = field.value;
			}

			if (field.type === 'name') {
				contact.first_name = field.value.givenName;
				contact.last_name = field.value.familyName;
				contact.name = field.value.givenName + ' ' + field.value.familyName;
			}

			if (field.type === 'yahooid') {
				contact.id = field.value;
			}
		});
	}

	function paging(res, headers, request) {

		// See: http://developer.yahoo.com/yql/guide/paging.html#local_limits
		if (res.query && res.query.count && request.options) {
			res.paging = {
				next: '?start=' + (res.query.count + (+request.options.start || 1))
			};
		}

		return res;
	}

	function yql(q) {
		return 'https://query.yahooapis.com/v1/yql?q=' + (q + ' limit @{limit|100} offset @{start|0}').replace(/\s/g, '%20') + '&format=json';
	}

})(hello);

// Register as anonymous AMD module
if (typeof define === 'function' && define.amd) {
	define(function() {
		return hello;
	});
}

// CommonJS module for browserify
if (typeof module === 'object' && module.exports) {
	module.exports = hello;
}
>>>>>>> 6ddec32f0023b4e30f37ce4f110420a1df285572
