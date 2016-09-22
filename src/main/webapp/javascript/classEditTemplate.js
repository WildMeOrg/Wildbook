var classEditTemplate = {};

classEditTemplate.defaultFieldsPerSubtable = 8;
classEditTemplate.deleteSheet = false;

classEditTemplate.checkIfOldFormValue = function(changedElem) {
  return ($(changedElem).attr("name").startsWith("oldValue-"));
}

classEditTemplate.checkIfOldFormValue$ = function($changedElem) {
  return (changedElem.attr("name").startsWith("oldValue-"));
}

classEditTemplate.markFormFieldNotOld = function(changedElem) {
  if (classEditTemplate.checkIfOldFormValue(changedElem)) {
    $(changedElem).attr("name",$(changedElem).attr("name").substring(9));
    $(changedElem).closest('tr').addClass("changed-row");
  }
}

classEditTemplate.markFormFieldOld$ = function($inputElem) {
  if (!classEditTemplate.checkIfOldFormValue($inputElem)){
    $inputElem.attr("name","oldValue-"+$inputElem.attr("name"));
  }
}

classEditTemplate.extractDateTimeFromVal = function(valString) {
  quoteSplit = valString.split('"');
  console.log("Here's the list (length="+quoteSplit.length+"): "+quoteSplit);
  return "hello";

}

classEditTemplate.updateMillisFromDisplayDateTime = function(displayElem) {
}

// following functions were built on nest.jsp
classEditTemplate.deleteSheet = false;

classEditTemplate.checkBeforeDeletion = function() {
  if (classEditTemplate.deleteSheet == true) {
    return confirm('Are you sure you want to delete this data sheet?');
  }
  return true;
}

classEditTemplate.markDeleteSheet = function() {
  classEditTemplate.deleteSheet = true;
}

classEditTemplate.extractIntFromString = function(str) {
  var strOnlyDigits = str.match(/\d+/)[0];
  return parseInt(strOnlyDigits);
}

classEditTemplate.createEggWeightFromTemplate = function(eggRowTemplateElem, eggNo, dataSheetNum) {
  var eggWeightRowElem = eggRowTemplateElem.clone();
  eggWeightRowElem.find('td.value input').attr('name', 'nes-dp-new:ds'+dataSheetNum+'-eggWeight'+eggNo);
  eggWeightRowElem.find('td.value input').val('');
  eggWeightRowElem.find('td.unit-label').html('g');
  eggWeightRowElem.find('td.fieldName').html('egg '+eggNo+' weight');
  return eggWeightRowElem;
}

classEditTemplate.createEggWeightFromTemplate2 = function(eggRowTemplateElem, eggNo, dataSheetNum) {
  return classEditTemplate.modifiedCopyRowEl(
    eggRowTemplateElem.clone(),
    'nes-dp-new:ds'+dataSheetNum+'-eggDiam'+eggNo,
    '',
    'g',
    'egg '+eggNo+' weight'
  );
}




classEditTemplate.createEggDiamFromTemplate = function(eggRowTemplateElem, eggNo, dataSheetNum) {
  var eggDiamRowElem = eggRowTemplateElem.clone();
  eggDiamRowElem.find('td.value input').attr('name', 'nes-dp-new:ds'+dataSheetNum+'-eggDiam'+eggNo);
  eggDiamRowElem.find('td.value input').val('');
  eggDiamRowElem.find('td.unit-label').html('cm');
  eggDiamRowElem.find('td.fieldName').html('egg '+eggNo+' diam.');
  return eggDiamRowElem;
}


classEditTemplate.modifiedCopyRowEl = function(rowEl, name, value, units, fieldName) {
  var copyEl = rowEl.clone();
  classEditTemplate.modifyRowElem(copyEl, name, value, units, fieldName);
  return copyEl;
}

classEditTemplate.modifyRowElem = function(rowEl, name, value, units, fieldName) {

  if (name) rowEl.find('td.value input').attr('name', name);
  if (value) {
    rowEl.find('td.value input').val(value);
  } else {
    rowEl.find('td.value input').val('');
  }
  if (units) rowEl.find('td.unit-label').html(units);
  if (fieldName) rowEl.find('td.fieldName').html('egg '+eggNo+' diam.');

}

classEditTemplate.updateSubtableIfNeeded = function(subtableElem) {
  if (classEditTemplate.isFull(subtableElem)) {
    console.log('Subtable is full, updating!');
    return classEditTemplate.makeNewSubtable(subtableElem);
  }
  else return subtableElem;
}

classEditTemplate.isFull = function(subtableElem) {
  var numRows = $(subtableElem).find('tr').length;
  return (numRows >= classEditTemplate.defaultFieldsPerSubtable);
}

classEditTemplate.makeNewSubtable = function(subtableElem) {
  var superColumn = subtableElem.closest('div.col');
  superColumn.addClass("makeNewSubtabled");
  var superRow = superColumn.closest('div.row');
  var newColumn = superColumn.clone(); // bc each subtable has its own bootstrap column
  newColumn.find('tr').remove(); // empty the cloned table
  console.log('about to append!');
  superColumn.after(newColumn);
  superRow.addClass("makeNewSubtabled");
  console.log('just appended '+newColumn.toString()+' onto '+superRow.toString());
  return newColumn.find('table.nest-field-table');
}




$(document).ready(function() {

  var changedFields = {};

  $("td.undo-container div.undo-button").click(function(event) {
    event.stopPropagation();
    var changedRow = $(this).closest('tr.changed-row');
    changedRow.removeClass('changed-row');
    var original = changedRow.attr('data-original-value');
    var correspondingInput = changedRow.find('td input');
    correspondingInput.val(original);
    classEditTemplate.markFormFieldOld$(correspondingInput);
  });

  $("td.undo-container div.select-undo-button").click(function(event) {
    console.log("select-undo-button!");
    event.stopPropagation();
    var changedRow = $(this).closest('tr.changed-row');
    changedRow.removeClass('changed-row');
    var original = changedRow.attr('data-original-value');
    var correspondingSelect = changedRow.find('td select');
    var optionWithOrigVal = findOptionWithOrigVal(correspondingSelect, original);
    console.log(optionWithOrigVal);
    optionWithOrigVal.prop('selected', true);
    console.log(optionWithOrigVal);
    classEditTemplate.markFormFieldOld$(correspondingSelect);
  });

  var findOptionWithOrigVal = function(selectElem, origVal) {
    return selectElem.find('option[value="' + origVal + '"]');
  }


	$('#classEditTemplateForm input, #classEditTemplateForm select').change(function() {
    console.log("classEdit")
    classEditTemplate.markFormFieldNotOld(this);
    var name = $(this).attr("name");

    //if (name.substring(0,8)=='display-'))

    var strVal = $(this).val();
    console.log('Change handler called on elem' + name + " to new val " + strVal);
    changedFields[name] = strVal;
		$('.submit').addClass('changes-made');
		$('.submit .note').html('changes made. please save.');
    console.log('Change detected on form. ChangedFields = '+JSON.stringify(changedFields));
	});

  $( ".datepicker.display" ).datetimepicker({
    changeMonth: true,
    changeYear: true,
    dateFormat: 'yy-mm-dd',
    maxDate: '+1d',
    controlType: 'select',
    alwaysSetTime: false,
    altFieldTimeOnly: true
  });

  $(".datepicker").datetimepicker({
    dateFormat: '@'
  })



  $( ".datepicker.display" ).each(function() {
    var thisName = $(this).attr("name");
    thisName = thisName.substring(8);
    var altField = "#"+thisName;
    var altFormat = "@";
    $(this).datetimepicker({
      altField: altField,
      altFormat: altFormat
    })
  })

  $( ".datepicker.millis").each(function() {
    var valStr = $(this).val();
    console.log("  valStr = "+valStr);
    //var valueStr = $(this).value();
    //console.log("  valueStr = "+valueStr);

    if(valStr!="") {
      console.log("found a val: "+valStr);
    }
  })

  $( ".datepicker" ).datetimepicker( $.timepicker.regional[ "<%=langCode %>" ] );

  $('#classEditTemplateForm input.datepicker.display').change(function() {
    console.log("The datepicker has changed.");
    var str = $(this).val();
    console.log("  "+str);
    var datetime = new Date(str);
    var millis = datetime.getTime();
    console.log("  millis = "+millis);
    var millisInput = $(this).siblings(".millis");

    console.log("  original millisInput.val() = "+JSON.stringify(millisInput));
    millisInput.val(millis);
    console.log("  second millisInput.val() = "+millisInput.val());
    classEditTemplate.markFormFieldNotOld(millisInput);
    //$(this).val(datetime.getTime());
  });




});
