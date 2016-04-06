
var imageEnhancer = {
    applyTo: function(selector, opt) {
        jQuery(selector).each(function(i, el) {
            imageEnhancer.apply(el, opt);
        });
    },

    apply: function(el, opt) {
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

        //.menu is a sort of shortcut
        if ((typeof opt.menu != 'undefined') && Array.isArray(opt.menu)) {
            imageEnhancer.initMenu(wrapper);
        }

        for (var i = 0 ; i < opt.init.length ; i++) {
            opt.init[i](wrapper, opt);
        }
        if (opt.debug) imageEnhancer.debugInitFunction(wrapper, opt);

        //now we store opt on the actual dom element
        wrapper[0].enhancer = { opt: opt, imgEl: jel };
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

    initMenu: function(el) {
console.warn('menu -> %o', el);
        el.append('<div class="image-enhancer-menu" />');
        var m = el.find('.image-enhancer-menu');
        m.css('height', m.css('width'));
        m.on('click', function(ev) {
            imageEnhancer.clickMenu(ev);
            ev.stopPropagation();
        });
    },

    clickMenu: function(ev) {
        console.debug('menu click! %o', ev);
        if (!ev || !ev.target || !ev.target.parentElement) {
            console.warn('could not find parentElement on %o', ev.target);
            return;
        }
        if (ev.target.parentElement.menuOpened) return imageEnhancer.closeMenu(ev.target.parentElement, ev);
        imageEnhancer.openMenu(ev.target.parentElement, ev);
    },

    openMenu: function(el, ev) {
        if (!el.enhancer || !el.enhancer.opt || !el.enhancer.imgEl) return;
        el.menuOpened = true;
        var mh = '<div class="image-enhancer-menu-open">';
        for (var i = 0 ; i < el.enhancer.opt.menu.length ; i++) {
            mh += '<div class="menu-item" data-i="' + i + '">' + el.enhancer.opt.menu[i][0] + '</div>';
        }
        if (el.enhancer.opt.debug) {
            mh += '<div class="menu-item" data-i="-1"><i>test item (debug == true)</i></div>';
        }
        mh += '</div>';
        jQuery(el).append(mh);
        jQuery(el).find('.menu-item').on('click', function(ev) {
            imageEnhancer.clickMenuItem(ev);
        });
    },

    closeMenu: function(el, ev) {
        el.menuOpened = false;
        jQuery(el).find('.image-enhancer-menu-open').remove();
    },

    clickMenuItem: function(ev) {
        var i = ev.currentTarget.getAttribute('data-i');
        var enh = ev.currentTarget.parentElement.parentElement.enhancer;
console.log('i=%o; ev: %o, enhancer: %o', i, ev, enh);
        ev.stopPropagation();
        imageEnhancer.closeMenu(ev.currentTarget.parentElement.parentElement);
        if (i < 0) return imageEnhancer.debugMenuItem(enh);
        //ev.target.parentElement.parentElement.enhancer.opt.menu[i][1](ev.target.parentElement.parentElement.enhancer);
        enh.opt.menu[i][1](enh);
    },


    popup: function(h) {
        jQuery('.image-enhancer-popup').remove();
        jQuery('body').append('<div class="image-enhancer-popup"><div class="popup-close">x</div><div class="popup-content"></div></div>');
        jQuery('.image-enhancer-popup .popup-close').on('click', function(ev) {
            ev.stopPropagation();
            jQuery('.image-enhancer-popup').remove();
        });
        var p = jQuery('.image-enhancer-popup .popup-content');
        if (h) p.html(h);
        return p;
    },

    debugInitFunction: function(wel, opt) {
        console.log('>>>> init on %o with opt=%o', wel, opt);
    },

    debugMenuItem: function(enh) {
        imageEnhancer.popup('<p><b>test item:</b> see js console for details</p>');
        console.debug('DEBUG TEST %o', enh);
    },

    debugEvent: function(ev) {
        console.info('imageEnhancer.debugEvent -> %o', ev);
        console.info('enhancer -> %o', ev.currentTarget.enhancer);
        return true;
    }
};



jQuery(document).ready(function() {
    imageEnhancer.applyTo('figure img', {
        debug: true,
        menu: [
            ['boring alert thing', function(enh) { console.info(enh); alert('ok!');}]
        ]
    });
});



