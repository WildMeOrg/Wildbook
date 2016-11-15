var Transform = require('stream').Transform
  , util = require('util')

module.exports = function(nodeName) {
  return new XmlNodes(nodeName)
}

function XmlNodes(nodeName) {
  this.nodeName = nodeName
  this.soFar = ''
  Transform.call(this)
}

util.inherits(XmlNodes, Transform)

XmlNodes.prototype._transform = function(chunk, encoding, done) {
  var nodes

  this.soFar += String(chunk)
  nodes = this.getNodes()

  for (var i = 0; i < nodes.length; i++) {
    this.push(nodes[i])
  }

  done()
}

XmlNodes.prototype.getNodes = function(nodes) {
  nodes = nodes || []

  var openingIndex = this.getOpeningIndex(this.soFar)

  if (openingIndex === -1) return nodes

  var str = this.soFar.slice(openingIndex)
    , nestedCount = this.getNestedCount(str)
    , closingIndex = this.getClosingIndex(str, nestedCount)

  if (closingIndex === -1) return nodes

  nodes.push(str.slice(0, closingIndex))
  this.soFar = str.slice(closingIndex)
  return this.getNodes(nodes)
}

XmlNodes.prototype.getNestedCount = function(str) {
  var openingIndex = this.getOpeningIndex(str)
    , firstClosingIndex = str.indexOf('</'+this.nodeName+'>')
    , currentIndex = openingIndex + 1
    , count = 0

  if (!firstClosingIndex) return false

  while (currentIndex < firstClosingIndex) {
    currentIndex = this.getOpeningIndex(str, currentIndex + 1)

    if (currentIndex === -1) break
    if (currentIndex < firstClosingIndex) count++
  }

  return count
}

XmlNodes.prototype.getOpeningIndex = function(str, i) {
  var withoutAttr = str.indexOf('<'+this.nodeName+'>', i)
    , withAttr = str.indexOf('<'+this.nodeName+' ', i)

  if (withoutAttr > -1 && withAttr === -1) return withoutAttr
  if (withAttr > -1 && withoutAttr === -1) return withAttr
  if (withAttr === -1 && withoutAttr === -1) return -1

  return withAttr > withoutAttr ? withAttr : withoutAttr
}

XmlNodes.prototype.getClosingIndex = function(str, nestedCount) {
  var isSelfClosing = /^\<[^/\>]+(?=\/\>)/.test(str)
  if (isSelfClosing) return str.indexOf('/>') + 2

  var currentIndex = str.indexOf('</'+this.nodeName+'>')
    , currentCount = 0

  while (currentCount !== nestedCount) {
    currentIndex = str.indexOf('</'+this.nodeName+'>', currentIndex + 1)

    if (currentIndex === -1) break
    currentCount++
  }

  if (currentIndex === -1) return currentIndex

  return currentIndex + this.nodeName.length + 3
}
