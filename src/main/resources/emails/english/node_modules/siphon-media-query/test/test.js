var assert = require('assert');
var fs = require('fs');
var mq = require('..');
var parse = require('css-parse');

var INPUT_FILE = 'test/fixtures/input.css';

describe('Siphon Media Query', function() {
  var input = '';
  var inputTree = {};

  before(function(done) {
    fs.readFile(INPUT_FILE, function(err, data) {
      input = data.toString();
      inputTree = parse(input);
      done();
    })
  })

  it('extracts CSS from all media queries', function(done) {
    var output = parse(mq(input));
    var expected;

    fs.readFile('test/fixtures/expected-all.css', function(err, data) {
      expected = parse(data.toString());
      assert.deepEqual(output, expected);
      done();
    });
  });

  it('extracts CSS from a specific media query', function(done) {
    var output = parse(mq(input, 'screen and (min-width: 600px)'));
    var expected;

    fs.readFile('test/fixtures/expected-one.css', function(err, data) {
      expected = parse(data.toString());
      assert.deepEqual(output, expected);
      done();
    });
  });
});
