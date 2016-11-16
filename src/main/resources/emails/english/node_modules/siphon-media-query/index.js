'use strict';

var parse = require('css-parse');
var stringify = require('css-stringify');

/**
 * Parses a string of CSS, returning only CSS inside of media queries.
 * @param {string} input - CSS to parse.
 * @param {string} query [] - Specific media query to extract. If ommitted, all media query CSS will be extracted.
 * @returns {string} Matching CSS.
 */
module.exports = function(input, query) {
  var output = [];
  var rules = parse(input).stylesheet.rules;
  var all = query ? false : true;

  // Iterate through every rule found in the CSS
  for (var i in rules) {
    var rule = rules[i];

    // Only add the rule to the list if it's a @media rule, and if it's the matching rule. Or, add it if no specific media query was specified
    if (rule.type === 'media' && (rule.media === query || all)) {
      output.push(rule);
    }
  }

  // Turn the CSS rule tree back into a proper stylesheet
  return stringify({
    type: 'stylesheet',
    stylesheet: { rules: output }
  });
}
