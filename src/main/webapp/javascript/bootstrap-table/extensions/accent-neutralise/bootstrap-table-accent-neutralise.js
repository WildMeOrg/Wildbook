(function (global, factory) {
  if (typeof define === "function" && define.amd) {
    define([], factory);
  } else if (typeof exports !== "undefined") {
    factory();
  } else {
    var mod = {
      exports: {}
    };
    factory();
    global.bootstrapTableAccentNeutralise = mod.exports;
  }
})(this, function () {
  'use strict';

  var _slicedToArray = function () {
    function sliceIterator(arr, i) {
      var _arr = [];
      var _n = true;
      var _d = false;
      var _e = undefined;

      try {
        for (var _i = arr[Symbol.iterator](), _s; !(_n = (_s = _i.next()).done); _n = true) {
          _arr.push(_s.value);

          if (i && _arr.length === i) break;
        }
      } catch (err) {
        _d = true;
        _e = err;
      } finally {
        try {
          if (!_n && _i["return"]) _i["return"]();
        } finally {
          if (_d) throw _e;
        }
      }

      return _arr;
    }

    return function (arr, i) {
      if (Array.isArray(arr)) {
        return arr;
      } else if (Symbol.iterator in Object(arr)) {
        return sliceIterator(arr, i);
      } else {
        throw new TypeError("Invalid attempt to destructure non-iterable instance");
      }
    };
  }();

  function _classCallCheck(instance, Constructor) {
    if (!(instance instanceof Constructor)) {
      throw new TypeError("Cannot call a class as a function");
    }
  }

  var _createClass = function () {
    function defineProperties(target, props) {
      for (var i = 0; i < props.length; i++) {
        var descriptor = props[i];
        descriptor.enumerable = descriptor.enumerable || false;
        descriptor.configurable = true;
        if ("value" in descriptor) descriptor.writable = true;
        Object.defineProperty(target, descriptor.key, descriptor);
      }
    }

    return function (Constructor, protoProps, staticProps) {
      if (protoProps) defineProperties(Constructor.prototype, protoProps);
      if (staticProps) defineProperties(Constructor, staticProps);
      return Constructor;
    };
  }();

  function _possibleConstructorReturn(self, call) {
    if (!self) {
      throw new ReferenceError("this hasn't been initialised - super() hasn't been called");
    }

    return call && (typeof call === "object" || typeof call === "function") ? call : self;
  }

  var _get = function get(object, property, receiver) {
    if (object === null) object = Function.prototype;
    var desc = Object.getOwnPropertyDescriptor(object, property);

    if (desc === undefined) {
      var parent = Object.getPrototypeOf(object);

      if (parent === null) {
        return undefined;
      } else {
        return get(parent, property, receiver);
      }
    } else if ("value" in desc) {
      return desc.value;
    } else {
      var getter = desc.get;

      if (getter === undefined) {
        return undefined;
      }

      return getter.call(receiver);
    }
  };

  function _inherits(subClass, superClass) {
    if (typeof superClass !== "function" && superClass !== null) {
      throw new TypeError("Super expression must either be null or a function, not " + typeof superClass);
    }

    subClass.prototype = Object.create(superClass && superClass.prototype, {
      constructor: {
        value: subClass,
        enumerable: false,
        writable: true,
        configurable: true
      }
    });
    if (superClass) Object.setPrototypeOf ? Object.setPrototypeOf(subClass, superClass) : subClass.__proto__ = superClass;
  }

  /**
   * @author: Dennis Hernández
   * @webSite: http://djhvscf.github.io/Blog
   * @update: zhixin wen <wenzhixin2010@gmail.com>
   */

  !function ($) {
    var diacriticsMap = {};
    var defaultAccentsDiacritics = [{ base: 'A', letters: 'A\u24B6\uFF21\xC0\xC1\xC2\u1EA6\u1EA4\u1EAA\u1EA8\xC3\u0100\u0102\u1EB0\u1EAE\u1EB4\u1EB2\u0226\u01E0\xC4\u01DE\u1EA2\xC5\u01FA\u01CD\u0200\u0202\u1EA0\u1EAC\u1EB6\u1E00\u0104\u023A\u2C6F' }, { base: 'AA', letters: '\uA732' }, { base: 'AE', letters: '\xC6\u01FC\u01E2' }, { base: 'AO', letters: '\uA734' }, { base: 'AU', letters: '\uA736' }, { base: 'AV', letters: '\uA738\uA73A' }, { base: 'AY', letters: '\uA73C' }, { base: 'B', letters: 'B\u24B7\uFF22\u1E02\u1E04\u1E06\u0243\u0182\u0181' }, { base: 'C', letters: 'C\u24B8\uFF23\u0106\u0108\u010A\u010C\xC7\u1E08\u0187\u023B\uA73E' }, { base: 'D', letters: 'D\u24B9\uFF24\u1E0A\u010E\u1E0C\u1E10\u1E12\u1E0E\u0110\u018B\u018A\u0189\uA779' }, { base: 'DZ', letters: '\u01F1\u01C4' }, { base: 'Dz', letters: '\u01F2\u01C5' }, { base: 'E', letters: 'E\u24BA\uFF25\xC8\xC9\xCA\u1EC0\u1EBE\u1EC4\u1EC2\u1EBC\u0112\u1E14\u1E16\u0114\u0116\xCB\u1EBA\u011A\u0204\u0206\u1EB8\u1EC6\u0228\u1E1C\u0118\u1E18\u1E1A\u0190\u018E' }, { base: 'F', letters: 'F\u24BB\uFF26\u1E1E\u0191\uA77B' }, { base: 'G', letters: 'G\u24BC\uFF27\u01F4\u011C\u1E20\u011E\u0120\u01E6\u0122\u01E4\u0193\uA7A0\uA77D\uA77E' }, { base: 'H', letters: 'H\u24BD\uFF28\u0124\u1E22\u1E26\u021E\u1E24\u1E28\u1E2A\u0126\u2C67\u2C75\uA78D' }, { base: 'I', letters: 'I\u24BE\uFF29\xCC\xCD\xCE\u0128\u012A\u012C\u0130\xCF\u1E2E\u1EC8\u01CF\u0208\u020A\u1ECA\u012E\u1E2C\u0197' }, { base: 'J', letters: 'J\u24BF\uFF2A\u0134\u0248' }, { base: 'K', letters: 'K\u24C0\uFF2B\u1E30\u01E8\u1E32\u0136\u1E34\u0198\u2C69\uA740\uA742\uA744\uA7A2' }, { base: 'L', letters: 'L\u24C1\uFF2C\u013F\u0139\u013D\u1E36\u1E38\u013B\u1E3C\u1E3A\u0141\u023D\u2C62\u2C60\uA748\uA746\uA780' }, { base: 'LJ', letters: '\u01C7' }, { base: 'Lj', letters: '\u01C8' }, { base: 'M', letters: 'M\u24C2\uFF2D\u1E3E\u1E40\u1E42\u2C6E\u019C' }, { base: 'N', letters: 'N\u24C3\uFF2E\u01F8\u0143\xD1\u1E44\u0147\u1E46\u0145\u1E4A\u1E48\u0220\u019D\uA790\uA7A4' }, { base: 'NJ', letters: '\u01CA' }, { base: 'Nj', letters: '\u01CB' }, { base: 'O', letters: 'O\u24C4\uFF2F\xD2\xD3\xD4\u1ED2\u1ED0\u1ED6\u1ED4\xD5\u1E4C\u022C\u1E4E\u014C\u1E50\u1E52\u014E\u022E\u0230\xD6\u022A\u1ECE\u0150\u01D1\u020C\u020E\u01A0\u1EDC\u1EDA\u1EE0\u1EDE\u1EE2\u1ECC\u1ED8\u01EA\u01EC\xD8\u01FE\u0186\u019F\uA74A\uA74C' }, { base: 'OI', letters: '\u01A2' }, { base: 'OO', letters: '\uA74E' }, { base: 'OU', letters: '\u0222' }, { base: 'OE', letters: '\x8C\u0152' }, { base: 'oe', letters: '\x9C\u0153' }, { base: 'P', letters: 'P\u24C5\uFF30\u1E54\u1E56\u01A4\u2C63\uA750\uA752\uA754' }, { base: 'Q', letters: 'Q\u24C6\uFF31\uA756\uA758\u024A' }, { base: 'R', letters: 'R\u24C7\uFF32\u0154\u1E58\u0158\u0210\u0212\u1E5A\u1E5C\u0156\u1E5E\u024C\u2C64\uA75A\uA7A6\uA782' }, { base: 'S', letters: 'S\u24C8\uFF33\u1E9E\u015A\u1E64\u015C\u1E60\u0160\u1E66\u1E62\u1E68\u0218\u015E\u2C7E\uA7A8\uA784' }, { base: 'T', letters: 'T\u24C9\uFF34\u1E6A\u0164\u1E6C\u021A\u0162\u1E70\u1E6E\u0166\u01AC\u01AE\u023E\uA786' }, { base: 'TZ', letters: '\uA728' }, { base: 'U', letters: 'U\u24CA\uFF35\xD9\xDA\xDB\u0168\u1E78\u016A\u1E7A\u016C\xDC\u01DB\u01D7\u01D5\u01D9\u1EE6\u016E\u0170\u01D3\u0214\u0216\u01AF\u1EEA\u1EE8\u1EEE\u1EEC\u1EF0\u1EE4\u1E72\u0172\u1E76\u1E74\u0244' }, { base: 'V', letters: 'V\u24CB\uFF36\u1E7C\u1E7E\u01B2\uA75E\u0245' }, { base: 'VY', letters: '\uA760' }, { base: 'W', letters: 'W\u24CC\uFF37\u1E80\u1E82\u0174\u1E86\u1E84\u1E88\u2C72' }, { base: 'X', letters: 'X\u24CD\uFF38\u1E8A\u1E8C' }, { base: 'Y', letters: 'Y\u24CE\uFF39\u1EF2\xDD\u0176\u1EF8\u0232\u1E8E\u0178\u1EF6\u1EF4\u01B3\u024E\u1EFE' }, { base: 'Z', letters: 'Z\u24CF\uFF3A\u0179\u1E90\u017B\u017D\u1E92\u1E94\u01B5\u0224\u2C7F\u2C6B\uA762' }, { base: 'a', letters: 'a\u24D0\uFF41\u1E9A\xE0\xE1\xE2\u1EA7\u1EA5\u1EAB\u1EA9\xE3\u0101\u0103\u1EB1\u1EAF\u1EB5\u1EB3\u0227\u01E1\xE4\u01DF\u1EA3\xE5\u01FB\u01CE\u0201\u0203\u1EA1\u1EAD\u1EB7\u1E01\u0105\u2C65\u0250' }, { base: 'aa', letters: '\uA733' }, { base: 'ae', letters: '\xE6\u01FD\u01E3' }, { base: 'ao', letters: '\uA735' }, { base: 'au', letters: '\uA737' }, { base: 'av', letters: '\uA739\uA73B' }, { base: 'ay', letters: '\uA73D' }, { base: 'b', letters: 'b\u24D1\uFF42\u1E03\u1E05\u1E07\u0180\u0183\u0253' }, { base: 'c', letters: 'c\u24D2\uFF43\u0107\u0109\u010B\u010D\xE7\u1E09\u0188\u023C\uA73F\u2184' }, { base: 'd', letters: 'd\u24D3\uFF44\u1E0B\u010F\u1E0D\u1E11\u1E13\u1E0F\u0111\u018C\u0256\u0257\uA77A' }, { base: 'dz', letters: '\u01F3\u01C6' }, { base: 'e', letters: 'e\u24D4\uFF45\xE8\xE9\xEA\u1EC1\u1EBF\u1EC5\u1EC3\u1EBD\u0113\u1E15\u1E17\u0115\u0117\xEB\u1EBB\u011B\u0205\u0207\u1EB9\u1EC7\u0229\u1E1D\u0119\u1E19\u1E1B\u0247\u025B\u01DD' }, { base: 'f', letters: 'f\u24D5\uFF46\u1E1F\u0192\uA77C' }, { base: 'g', letters: 'g\u24D6\uFF47\u01F5\u011D\u1E21\u011F\u0121\u01E7\u0123\u01E5\u0260\uA7A1\u1D79\uA77F' }, { base: 'h', letters: 'h\u24D7\uFF48\u0125\u1E23\u1E27\u021F\u1E25\u1E29\u1E2B\u1E96\u0127\u2C68\u2C76\u0265' }, { base: 'hv', letters: '\u0195' }, { base: 'i', letters: 'i\u24D8\uFF49\xEC\xED\xEE\u0129\u012B\u012D\xEF\u1E2F\u1EC9\u01D0\u0209\u020B\u1ECB\u012F\u1E2D\u0268\u0131' }, { base: 'j', letters: 'j\u24D9\uFF4A\u0135\u01F0\u0249' }, { base: 'k', letters: 'k\u24DA\uFF4B\u1E31\u01E9\u1E33\u0137\u1E35\u0199\u2C6A\uA741\uA743\uA745\uA7A3' }, { base: 'l', letters: 'l\u24DB\uFF4C\u0140\u013A\u013E\u1E37\u1E39\u013C\u1E3D\u1E3B\u017F\u0142\u019A\u026B\u2C61\uA749\uA781\uA747' }, { base: 'lj', letters: '\u01C9' }, { base: 'm', letters: 'm\u24DC\uFF4D\u1E3F\u1E41\u1E43\u0271\u026F' }, { base: 'n', letters: 'n\u24DD\uFF4E\u01F9\u0144\xF1\u1E45\u0148\u1E47\u0146\u1E4B\u1E49\u019E\u0272\u0149\uA791\uA7A5' }, { base: 'nj', letters: '\u01CC' }, { base: 'o', letters: 'o\u24DE\uFF4F\xF2\xF3\xF4\u1ED3\u1ED1\u1ED7\u1ED5\xF5\u1E4D\u022D\u1E4F\u014D\u1E51\u1E53\u014F\u022F\u0231\xF6\u022B\u1ECF\u0151\u01D2\u020D\u020F\u01A1\u1EDD\u1EDB\u1EE1\u1EDF\u1EE3\u1ECD\u1ED9\u01EB\u01ED\xF8\u01FF\u0254\uA74B\uA74D\u0275' }, { base: 'oi', letters: '\u01A3' }, { base: 'ou', letters: '\u0223' }, { base: 'oo', letters: '\uA74F' }, { base: 'p', letters: 'p\u24DF\uFF50\u1E55\u1E57\u01A5\u1D7D\uA751\uA753\uA755' }, { base: 'q', letters: 'q\u24E0\uFF51\u024B\uA757\uA759' }, { base: 'r', letters: 'r\u24E1\uFF52\u0155\u1E59\u0159\u0211\u0213\u1E5B\u1E5D\u0157\u1E5F\u024D\u027D\uA75B\uA7A7\uA783' }, { base: 's', letters: 's\u24E2\uFF53\xDF\u015B\u1E65\u015D\u1E61\u0161\u1E67\u1E63\u1E69\u0219\u015F\u023F\uA7A9\uA785\u1E9B' }, { base: 't', letters: 't\u24E3\uFF54\u1E6B\u1E97\u0165\u1E6D\u021B\u0163\u1E71\u1E6F\u0167\u01AD\u0288\u2C66\uA787' }, { base: 'tz', letters: '\uA729' }, { base: 'u', letters: 'u\u24E4\uFF55\xF9\xFA\xFB\u0169\u1E79\u016B\u1E7B\u016D\xFC\u01DC\u01D8\u01D6\u01DA\u1EE7\u016F\u0171\u01D4\u0215\u0217\u01B0\u1EEB\u1EE9\u1EEF\u1EED\u1EF1\u1EE5\u1E73\u0173\u1E77\u1E75\u0289' }, { base: 'v', letters: 'v\u24E5\uFF56\u1E7D\u1E7F\u028B\uA75F\u028C' }, { base: 'vy', letters: '\uA761' }, { base: 'w', letters: 'w\u24E6\uFF57\u1E81\u1E83\u0175\u1E87\u1E85\u1E98\u1E89\u2C73' }, { base: 'x', letters: 'x\u24E7\uFF58\u1E8B\u1E8D' }, { base: 'y', letters: 'y\u24E8\uFF59\u1EF3\xFD\u0177\u1EF9\u0233\u1E8F\xFF\u1EF7\u1E99\u1EF5\u01B4\u024F\u1EFF' }, { base: 'z', letters: 'z\u24E9\uFF5A\u017A\u1E91\u017C\u017E\u1E93\u1E95\u01B6\u0225\u0240\u2C6C\uA763' }];

    var initNeutraliser = function initNeutraliser() {
      for (var _iterator = defaultAccentsDiacritics, _isArray = Array.isArray(_iterator), _i = 0, _iterator = _isArray ? _iterator : _iterator[Symbol.iterator]();;) {
        var _ref;

        if (_isArray) {
          if (_i >= _iterator.length) break;
          _ref = _iterator[_i++];
        } else {
          _i = _iterator.next();
          if (_i.done) break;
          _ref = _i.value;
        }

        var diacritic = _ref;

        var letters = diacritic.letters;
        for (var i = 0; i < letters.length; i++) {
          diacriticsMap[letters[i]] = diacritic.base;
        }
      }
    };

    /* eslint-disable no-control-regex */
    var removeDiacritics = function removeDiacritics(str) {
      return str.replace(/[^\u0000-\u007E]/g, function (a) {
        return diacriticsMap[a] || a;
      });
    };

    $.extend($.fn.bootstrapTable.defaults, {
      searchAccentNeutralise: false
    });

    $.BootstrapTable = function (_$$BootstrapTable) {
      _inherits(_class, _$$BootstrapTable);

      function _class() {
        _classCallCheck(this, _class);

        return _possibleConstructorReturn(this, (_class.__proto__ || Object.getPrototypeOf(_class)).apply(this, arguments));
      }

      _createClass(_class, [{
        key: 'init',
        value: function init() {
          if (this.options.searchAccentNeutralise) {
            initNeutraliser();
          }
          _get(_class.prototype.__proto__ || Object.getPrototypeOf(_class.prototype), 'init', this).call(this);
        }
      }, {
        key: 'initSearch',
        value: function initSearch() {
          var _this2 = this;

          if (this.options.sidePagination !== 'server') {
            var s = this.searchText && this.searchText.toLowerCase();
            var f = $.isEmptyObject(this.filterColumns) ? null : this.filterColumns;

            // Check filter
            this.data = f ? this.options.data.filter(function (item, i) {
              for (var key in f) {
                if (item[key] !== f[key]) {
                  return false;
                }
              }
              return true;
            }) : this.options.data;

            this.data = s ? this.options.data.filter(function (item, i) {
              for (var _iterator2 = function (target) {
                return Object.keys(target).map(function (key) {
                  return [key, target[key]];
                });
              }(item), _isArray2 = Array.isArray(_iterator2), _i2 = 0, _iterator2 = _isArray2 ? _iterator2 : _iterator2[Symbol.iterator]();;) {
                var _ref2;

                if (_isArray2) {
                  if (_i2 >= _iterator2.length) break;
                  _ref2 = _iterator2[_i2++];
                } else {
                  _i2 = _iterator2.next();
                  if (_i2.done) break;
                  _ref2 = _i2.value;
                }

                var _ref3 = _ref2,
                    _ref4 = _slicedToArray(_ref3, 2),
                    key = _ref4[0],
                    value = _ref4[1];

                key = $.isNumeric(key) ? parseInt(key, 10) : key;
                var column = _this2.columns[_this2.fieldsColumnsIndex[key]];
                var j = _this2.header.fields.indexOf(key);

                if (column && column.searchFormatter) {
                  value = $.fn.bootstrapTable.utils.calculateObjectValue(column, _this2.header.formatters[j], [value, item, i], value);
                }

                var index = _this2.header.fields.indexOf(key);
                if (index !== -1 && _this2.header.searchables[index] && typeof value === 'string') {
                  if (_this2.options.searchAccentNeutralise) {
                    value = removeDiacritics(value);
                    s = removeDiacritics(s);
                  }
                  if (_this2.options.strictSearch) {
                    if (('' + value).toLowerCase() === s) {
                      return true;
                    }
                  } else {
                    if (('' + value).toLowerCase().indexOf(s) !== -1) {
                      return true;
                    }
                  }
                }
              }
              return false;
            }) : this.data;
          }
        }
      }]);

      return _class;
    }($.BootstrapTable);
  }(jQuery);
});