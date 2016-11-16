var through = require('through2')
var Parser = require('xml2js').Parser

module.exports = function (options) {
  var parser = new Parser(options)

  return through.obj(function (chunk, enc, callback) {
    parser.parseString(chunk.toString(), callback)
  })
}
