# siphon-media-query

Extract media query-specific CSS from a stylesheet. Used by the [Foundation for Emails](http://foundation.zurb.com/emails) web inliner to separate general CSS from media query-specific CSS. Inspired by [media-query-extractor](https://www.npmjs.com/package/media-query-extractor), the main difference being this library works as a pure API.

## Installation

```bash
npm install siphon-media-query --save
```

## Usage

The parse function takes in a CSS string and gives you back a CSS string.

To extract all media queries:

```js
var parse = require('siphon-media-query');

var input = `
  .foo { color: red; }

  @media { .bar { color: dodgerblue; } }
`;

parse(input); // => @media { .bar { color: dodgerblue; } }
```

To extract only CSS from a specific media query:

```js
var input = `
  @media (min-width: 400px) {
    .foo { color: red; }
  }

  @media (min-width: 800px) {
    .bar { color: dodgerblue; }
  }
`;

parse(input, '(min-width: 800px)');
// =>
// @media (min-width: 800px) {
//   .bar { color: dodgerblue; }
// }
```

## Local Development

```bash
git clone https://github.com/zurb/siphon-media-query
cd siphon-media-query
npm install
npm test
```
