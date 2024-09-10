import globals from "globals";
import pluginJs from "@eslint/js";
import pluginReactConfig from "eslint-plugin-react/configs/recommended.js";
import babelParser from "@babel/eslint-parser";
import reactHooks from "eslint-plugin-react-hooks";

export default [
  pluginJs.configs.recommended,
  pluginReactConfig,
  {
    files: ["**/*.{js,mjs,cjs,jsx}"],     
    plugins: {
      "react-hooks": reactHooks,  
    },
    languageOptions: {
      globals: globals.browser,
      parser: babelParser,
    },
    settings: {
      react: {
        version: "detect"
      }
    },  
      
    rules: {
      "semi": 2,
      "react/prop-types": 0,
      "jsx-no-bind": 0,
      "react/jsx-no-bind": 0,
      "react/jsx-filename-extension": 0,
      "react/jsx-props-no-spreading": 0,
      "react/destructuring-assignment": 0,
      "react/forbid-prop-types": 0,
      "react/jsx-wrap-multilines": 0,
      "react/style-prop-object": [2, { "allow": ["FormattedNumber"] }],
      "import/prefer-default-export": 0,
      "function-paren-newline": 0,
      "react/function-component-definition": 0,
      "func-names": 0,
      "no-underscore-dangle": 0,
      "no-console": 1,
      "prefer-destructuring": 0,
      "operator-linebreak": 0,
      "indent": 0,
      "camelcase": 0,
      "space-before-function-paren": 0,
      "implicit-arrow-linebreak": 0,
      "nonblock-statement-body-position": 0,
      "react/prefer-stateless-function": 0,
      "curly": 0,
      "object-curly-newline": 0,
      "prefer-template": 0,
      "arrow-parens": 0,
      "no-param-reassign": 0,
      "no-mixed-operators": 0,
      "no-else-return": 0,
      
    },
  },
  {
    files: ['frontend/babel.config.js', 'frontend/jest.config.js'],  // Specify the config files
    languageOptions: {
      globals: {
        require: "readonly",  // Define Node.js globals
        module: "readonly",
        __dirname: "readonly",  // Other common Node.js globals if necessary
        process: "readonly",
        jest: "readonly",
        test: "readonly",
        expect: "readonly",
      },
    },
    // env: {
    //   node: true,  // Enable Node.js environment
    // },
  },
  {
    files: ["**/__tests__/**/*.{js,jsx}", "**/*.test.{js,jsx}"],
    languageOptions: {
      globals: {
        jest: "readonly",    // Define Jest-specific globals
        test: "readonly",
        expect: "readonly",
      },
    },
    rules: {
      "react/react-in-jsx-scope": 0,  // Disable React in scope for JSX if using React 17+
    }
  }
];