//

var imageEnhancer = {
    applyTo: function(selector, data, opt) {
        jQuery(selector).each(function(i, el) {
            imageEnhancer.apply(el, data, opt);
        });
    },

    apply: function(el, data, opt) {
        var jel = jQuery(el);
        var parEl = jel.parent();  //TODO what if there is none... oops???
        if (parEl.prop('tagName') == 'A') parEl = parEl.parent();
console.info('imageEnhancer.apply to %o with opt %o (parEl=%o)', el, opt, parEl);
        if (typeof opt != 'object') opt = {};

        if (parEl.css('position') == 'static') parEl.css('position', 'relative');

        parEl.append('<div class="image-enhancer-wrapper' + (opt.debug ? ' image-enhancer-debug' : '') + '" />');
        imageEnhancer.wrapperSizeSetFromImg(parEl);
        var wrapper = parEl.find('.image-enhancer-wrapper');

        //all we care about is attaching the right events to the wrapper
        if (!opt.events) opt.events = {};
        if (opt.debug && !opt.events.mouseover) opt.events.mouseover = function(ev) { return imageEnhancer.debugEvent(ev); };
        for (var e in opt.events) {
console.info('assigning event %s', e);
            wrapper.bind(e, opt.events[e]);
        }

        //init is a series of functions run on "startup", passed wrapper element and opt hash
        if (!opt.init) opt.init = [];
        if (typeof opt.init == 'function') opt.init = [ opt.init ];  //force singleton to an array
        if (opt.debug) opt.init.push(imageEnhancer.debugInitFunction);
        for (var i = 0 ; i < opt.init.length ; i++) {
            opt.init[i](wrapper, opt);
        }

        //now we store opt on the actual dom element
        wrapper[0].enhancerOpt = opt;
    },

    wrapperSizeSetFromImg: function(el) {
        var img = el.find('img');
        var w = el.find('.image-enhancer-wrapper');
        if (!img.length || !w.length) return;
//console.warn('img => %o', img);
//console.warn('%d x %d', img.width(), img.height());
        w.css('width', img.width());
        w.css('height', img.height());
    },

    debugInitFunction: function(wel, opt) {
        console.log('init on %o with opt=%o', wel, opt);
    },

    debugEvent: function(ev) {
        console.info('imageEnhancer.debugEvent -> %o', ev);
        console.info('opt -> %o', ev.target.enhancerOpt);
        return true;
    }
};
