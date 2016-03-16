/**
 * lightweight module for translating JSON data queries in MongoDB style into sql queries.
 * The json queries are generated clientside while interacting with the UI, and the sql
 * queries are used directly to get what we need from the database.
 *
 * generally, components are copied from 2do2go team's excellent json-sql node library (MIT License).
 * The goal here is to extract the minimum functionality that we need from json-sql.
 */

var jsonToSql = {};

jsonToSql.convert = function(jsonQuery) {

  var buildComparisonOperator = function(field, operator, value) {
  	return [field, operator, value].join(' ');
  };

  var buildBooleanOperator = function(field, operator, value) {
  	return buildComparisonOperator(field, 'is' + (value ? '' : ' not'), operator);
  };

  var comparisonOperators = {
    '$eq': {
  		inversedOperator: '$ne',
  		fn: function(field, value) {
  			return buildComparisonOperator(field, '=', value);
  		}
    },
    '$ne': {
  		inversedOperator: '$eq',
  		fn: function(field, value) {
  			return buildComparisonOperator(field, '!=', value);
  		}
  	},
    '$gt': {
  		inversedOperator: '$lte',
  		fn: function(field, value) {
  			return buildComparisonOperator(field, '>', value);
  		}
  	},
    '$lt': {
  		inversedOperator: '$gte',
  		fn: function(field, value) {
  			return buildComparisonOperator(field, '<', value);
  		}
  	},
    '$gte': {
      inversedOperator: '$lt',
      fn: function(field, value) {
        return buildComparisonOperator(field, '>=', value);
      }
    },
    '$lte': {
      inversedOperator: '$gt',
      fn: function(field, value) {
        return buildComparisonOperator(field, '<=', value);
      }
    }
    // we can add more operations as needed, but these are the minimum
    // see json-sql.lib/dialects/base/operators/comparison.js
  }

  var buildLogicalOperator = function(operator, values) {
  	if (!values.length) return '';
  	var result = values.join(' ' + operator + ' ');
  	if (values.length > 1) result = '(' + result + ')';
  	return result;
  }

  var logicalOperators = {
    '$and' : {
  		fn: function(values) {
  			return buildLogicalOperator('and', values);
  		}
  	},
    '$or' : {
  		fn: function(values) {
  			return buildLogicalOperator('or', values);
  		}
  	}
  }

  return jsonQuery
};

jsonToSql.sample = {sex:"male",
livingStatus:"dead",
occurenceID:"test"
};
