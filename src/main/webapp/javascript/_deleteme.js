//

var imageEnhancer = {
    applyTo: function(selector, data, opt) {
        jQuery(selector).each(function(i, el) {
            imageEnhancer.apply(el, data, opt);
        });
    },

    apply: function(el, data, opt) {  //jquery element
console.info('imageEnhancer.apply to %o with opt %o', el, opt);
    }
};

