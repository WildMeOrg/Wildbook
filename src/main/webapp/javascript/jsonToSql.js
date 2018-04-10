/**
 * lightweight module for translating JSON data queries in MongoDB style into sql queries.
 * The json queries are generated clientside while interacting with the UI, and the sql
 * queries are used directly to get what we need from the database.
 *
 * generally, components are copied from 2do2go team's excellent json-sql node library (MIT License).
 * The goal here is to extract the minimum functionality that we need from json-sql.
 */

var jsonToSql = {};

/*
jsonToSql.buildComparisonOperator = function(field, operator, value) {
  	return [field, operator, value].join(' ');
  };

jsonToSql.buildBooleanOperator = function(field, operator, value) {
  	return buildComparisonOperator(field, 'is' + (value ? '' : ' not'), operator);
  };
*/

jsonToSql.utils = {
  buildComparisonOperator: function(field, operator, value) {
    return [field, operator, value].join(' ');
  },
  buildBooleanOperator: function(field, operator, value) {
    return buildComparisonOperator(field, 'is' + (value ? '' : ' not'), operator);
  },
  buildLogicalOperator: function(operator, values) {
    if (!values.length) return '';
    var result = values.join(' ' + operator + ' ');
    if (values.length > 1) result = '(' + result + ')';
    return result;
  }
}

var op = '$eq';
jsonToSql.operators.comparison[op].fn(field,value);


jsonToSql.operators = {
  comparison : {
    '$eq': {
      inversedOperator: '$ne',
      fn: function(field, value) {
        return jsonToSql.utils.buildComparisonOperator(field, '=', value);
      }
    },
    '$ne': {
      inversedOperator: '$eq',
      fn: function(field, value) {
        return jsonToSql.utils.buildComparisonOperator(field, '!=', value);
      }
    },
    '$gt': {
      inversedOperator: '$lte',
      fn: function(field, value) {
        return jsonToSql.utils.buildComparisonOperator(field, '>', value);
      }
    },
    '$lt': {
      inversedOperator: '$gte',
      fn: function(field, value) {
        return jsonToSql.utils.buildComparisonOperator(field, '<', value);
      }
    },
    '$gte': {
      inversedOperator: '$lt',
      fn: function(field, value) {
        return jsonToSql.utils.buildComparisonOperator(field, '>=', value);
      }
    },
    '$lte': {
      inversedOperator: '$gt',
      fn: function(field, value) {
        return jsonToSql.utils.buildComparisonOperator(field, '<=', value);
      }
    }
    // we can add more operations as needed, but these are the minimum
    // see json-sql.lib/dialects/base/operators/comparison.js
  },
  logical : {
    '$and' : {
      fn: function(values) {
        return jsonToSql.utils.buildLogicalOperator('and', values);
      }
    },
    '$or' : {
      fn: function(values) {
        return jsonToSql.utils.buildLogicalOperator('or', values);
      }
    }
  }
}

jsonToSql.convert = function(jsonQuery) {
  return jsonQuery
};

jsonToSql.sample = {sex:"male",
 livingStatus:"dead",
 occurenceID:"test"
};

jsonToSql.test = function() {
  var tFunc = jsonToSql.operators.comparison.$eq.fn;
  console.log('tFunc type = '+typeof(tFunc));
  var output = tFunc("first","second");
  console.log("tFunc(first,second) = "+output);
}

$(window).load(function() {
  console.log('Testing!');
  jsonToSql.test();
});
